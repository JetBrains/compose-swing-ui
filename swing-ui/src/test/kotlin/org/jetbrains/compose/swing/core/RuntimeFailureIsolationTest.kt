package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.rememberCompositionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingComponentNodeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RuntimeFailureIsolationTest {
    @Test
    fun successfulFirstPassDoesNotDisposeItsOwner() = runSwingTest {
        val parent = Recomposer(Dispatchers.Swing)
        val panel = JPanel()
        var disposals = 0
        val label = JLabel("result")
        val content =
            panel.setContent(parent = parent) {
                SwingNode(factory = { label })
                DisposableEffect(Unit) { onDispose { disposals++ } }
            }
        try {
            assertEquals("result", label.text)
            assertSame(panel, label.parent)
            assertEquals(1, panel.componentCount)
            assertEquals(0, disposals)
        } finally {
            content.dispose()
            parent.cancel()
        }
        assertEquals(1, disposals)
    }

    @TestFactory
    fun aFailedFirstPassKeepsItsOwnFailureWhateverItsCleanupDoes(): List<DynamicTest> {
        val rethrown = IllegalStateException("first pass failed")
        return listOf(
            failsFirstPass("cleanup succeeds", IllegalStateException("first pass failed"), cleanup = null),
            failsFirstPass(
                "cleanup fails",
                IllegalStateException("first pass failed"),
                IllegalArgumentException("cleanup failed"),
            ),
            failsFirstPass("cleanup rethrows the first pass failure", rethrown, cleanup = rethrown),
            failsFirstPass(
                "cancellation stays cancellation",
                CancellationException("first pass cancelled"),
                IllegalStateException("cleanup failed"),
            ),
        )
    }

    @Test
    fun aRefusedChildKeepsItsFailureAndAFailingCleanupOfTheHalfAppliedCompositionIsReported() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        awaitIdle()
        val cleanup = IllegalArgumentException("cleanup failed")
        val panel = FailingDetachRoot(cleanup)
        val failure =
            assertFailsWith<IllegalStateException> {
                panel.setContent(parent = parentContext) { ScrollPane { Label("refused") } }
            }
        assertTrue("every child must declare which region it fills" in failure.message.orEmpty(), failure.message)
        assertTrue(failure.suppressed.isEmpty(), "a listener's failure is reported, not attached to the refusal")
        val reported = assertFailsWith<IllegalArgumentException> { awaitIdle() }
        assertSame(cleanup, reported, "the cleanup failure is reported")
    }

    /** A case named [name]: the first pass throws [original] and the container's listener removal throws [cleanup]. */
    private fun failsFirstPass(
        name: String,
        original: Throwable,
        cleanup: Throwable?,
    ): DynamicTest = DynamicTest.dynamicTest(name) {
        runSwingTest {
            val parent = Recomposer(Dispatchers.Swing)
            val panel = FailingRemovalPanel(listenerRemoval = cleanup)
            val listenersBefore = panel.hierarchyListeners.toList()
            try {
                val actual = assertFailsWith<Throwable> { panel.setContent(parent = parent) { throw original } }
                assertSame(original, actual)
                assertEquals(listOfNotNull(cleanup).filter { it !== original }, actual.suppressed.toList())
                assertEquals(1, panel.listenerRemovals)
                assertEquals(listenersBefore, panel.hierarchyListeners.toList())
                panel.listenerRemoval = null
                panel.setContent(parent = parent) { Label("replacement") }.dispose()
            } finally {
                parent.cancel()
            }
        }
    }

    private class FailingDetachRoot(
        private val failure: Throwable,
    ) : JPanel(),
        SwingComponentNodeListener {
        override fun onAttached(node: SwingComponentNode<*>) = Unit

        override fun onDetached(node: SwingComponentNode<*>): Unit = throw failure
    }
}
