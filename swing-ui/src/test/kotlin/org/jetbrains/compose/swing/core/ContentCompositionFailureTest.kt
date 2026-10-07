package org.jetbrains.compose.swing.core

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingComponentNodeListener
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.requestAfterValidation
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * What a nested content composition leaves behind when its root cannot be attached or its applier cannot be built
 * after its root attached.
 */
class ContentCompositionFailureTest {
    @Test
    fun aFailedApplierFactoryLeavesNothingOfTheRootItAttachedRunning() = runComposeSwingTest {
        val failure = IllegalStateException("The applier could not be built")
        val probe = AttachedProbe()

        assertConstructionFailureReachesCaller(probe, failure) { throw failure }
        assertEquals(listOf("attached", "detached"), probe.events, "the root is told it detached")
        probe.watched.intValue = 1
        awaitIdle()

        assertEquals(0, probe.changes, "a read recorded under the owner runs no callback after the failure")
        assertEquals(0, probe.actionsRun, "an action requested of the owner never runs after the failure")
    }

    @Test
    fun aRootThatThrowsAsItAttachesReportsTheFailureAndKeepsItsCompositionUsable() = runComposeSwingTest {
        val failure = object : IllegalStateException("The root failed on attach") {}
        val probe = AttachedProbe(failure)
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        awaitIdle()
        val root = CreatedNodeHolder(probe)
        val composition =
            SwingContentComposition.nested(parentContext, root) {
                org.jetbrains.compose.swing.node
                    .SwingApplier(root)
            }
        val text = mutableIntStateOf(0)
        val component = JPanel()
        composition.setContent {
            org.jetbrains.compose.swing.node.SwingNode(factory = { component }, update = {
                set(text.intValue) { name = it.toString() }
            })
        }
        val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
        assertSame(failure, thrown, "the root listener's original failure is reported")
        awaitIdle()
        probe.watched.intValue = 1
        text.intValue = 1
        awaitIdle()
        assertEquals(1, probe.changes, "the attached root's owner still observes reads")
        assertEquals(1, probe.actionsRun, "the attached root's owner still runs pending actions")
        assertEquals("1", component.name, "the root's composition still updates its content")
        assertEquals(listOf("attached"), probe.events, "the reported callback leaves the root attached")
        composition.dispose()
        assertEquals(listOf("attached", "detached"), probe.events, "dispose still detaches the root once")
    }

    private suspend fun ComposeSwingTest.assertConstructionFailureReachesCaller(
        probe: AttachedProbe,
        failure: Throwable,
        applierFactory: () -> AbstractApplier<SwingNodeHolder<*>>,
    ) {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        awaitIdle()

        val thrown =
            assertFailsWith<IllegalStateException> {
                SwingContentComposition.nested(parentContext, CreatedNodeHolder(probe)) { applierFactory() }
            }

        assertSame(failure, thrown, "the construction's own failure reaches the caller")
    }

    /**
     * A root that, once attached, records a read and requests an after-validation action of its owner under
     * [bystander], a node the failed construction's rollback of the root does not detach, then throws [failure]
     * where one is set.
     */
    private class AttachedProbe(
        private val failure: Throwable? = null,
    ) : JPanel(),
        SwingComponentNodeListener {
        val events = ArrayList<String>()
        val watched = mutableIntStateOf(0)
        var changes = 0
        var actionsRun = 0
        private val bystander = CreatedNodeHolder(JPanel())

        override fun onAttached(node: SwingComponentNode<*>) {
            events += "attached"
            val owner = checkNotNull(node.owner)
            bystander.attachedTo(owner)
            bystander.observeReads({ changes++ }) { watched.intValue }
            synchronized(bystander.component.treeLock) {
                bystander.requestAfterValidation { actionsRun++ }
            }
            failure?.let { throw it }
        }

        override fun onDetached(node: SwingComponentNode<*>) {
            events += "detached"
        }
    }
}
