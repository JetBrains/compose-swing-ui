package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.util.set
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JInternalFrame
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * `JComponent.setContent` accepts any component, a root-pane container such as a [JInternalFrame]
 * included. Such a container forwards `add` to its content pane, so the composition's children live on
 * the content pane and every structural change - the last of them being the dispose - has to address
 * them there. What the container holds itself is its own affair, the root pane and whatever else the
 * installed look and feel gave it, and the composition neither adds to it nor takes from it.
 *
 * The frame here is stamped with the test's recomposer, which is how a window publishes its own, so this
 * exercises the real self-first resolution off-screen and needs no display.
 */
class RootPaneContainerContentHostTest {
    private fun hostFrame(test: TestRecomposer): JInternalFrame = JInternalFrame("host").apply {
        bounds = Rectangle(0, 0, SIZE, SIZE)
        this[COMPOSITION_KEY] = test.recomposer
    }

    @Test
    fun aRootPaneContainerHostsTheCompositionOnItsContentPane() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            val host = hostFrame(test)
            val ownChildren = host.components.toList()
            var text by mutableStateOf("first")
            handles += host.setContent { Label(text = text) }
            test.awaitIdle()

            assertEquals(
                ownChildren,
                host.components.toList(),
                "the host itself should hold nothing beyond the children it came with",
            )
            assertEquals("first", labelText(host), "the content should be composed onto the host's content pane")

            text = "second"
            test.awaitIdle()
            assertEquals("second", labelText(host), "the hosted content should recompose in place")
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    @Test
    fun disposingTheCompositionRemovesItsChildrenAndLeavesTheRootPaneInstalled() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            val host = hostFrame(test)
            val ownChildren = host.components.toList()
            val handle = host.setContent { Label(text = "body") }
            handles += handle
            test.awaitIdle()

            val contentPane = host.contentPane
            assertEquals(1, contentPane.componentCount, "the composed child should be on the content pane")

            handle.dispose()

            assertEquals(
                ownChildren,
                host.components.toList(),
                "disposing the composition must leave the host with the children it came with",
            )
            assertSame(contentPane, host.contentPane, "the host should keep the content pane it had")
            assertEquals(
                0,
                contentPane.componentCount,
                "disposing the composition must remove the children it put on the content pane",
            )
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    /**
     * The text of the single composed label. Read off the content pane rather than the frame, because a
     * frame's own title pane carries a label of its own.
     */
    private fun labelText(frame: JInternalFrame): String {
        val labels = mutableListOf<JLabel>()

        fun visit(component: Container) {
            for (child in component.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(frame.contentPane)
        return labels.single().text
    }

    private companion object {
        const val SIZE: Int = 200
    }
}
