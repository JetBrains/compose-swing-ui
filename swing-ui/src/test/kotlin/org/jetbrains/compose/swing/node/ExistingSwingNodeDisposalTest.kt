package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExistingSwingNodeDisposalTest {
    @Test
    fun disposingRootContentLeavesTheClaimedComponentInItsParent() = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        val borrowed = JLabel("owned")
        val host = JPanel().apply { add(borrowed) }
        val handle =
            host.setContent(context) {
                ExistingSwingNode<JPanel, JLabel>(claim = { borrowed })
            }

        try {
            assertSame(host, borrowed.parent, "the parent should hold the claimed component")
        } finally {
            handle.dispose()
        }

        assertSame(host, borrowed.parent, "disposing the content should leave the claimed component in its parent")
        assertEquals(1, host.componentCount, "the parent should keep its component")
    }

    @Test
    fun disposingRootContentRemovesItsCreatedSiblingAndKeepsParentChildren() = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        val borrowed = JLabel("claimed")
        val unclaimed = JLabel("unclaimed")
        val created = JLabel("composed")
        val host =
            JPanel().apply {
                add(borrowed)
                add(unclaimed)
            }
        val handle =
            host.setContent(context) {
                ExistingSwingNode<JPanel, JLabel>(claim = { borrowed })
                SwingNode(factory = { created })
            }

        try {
            assertSame(host, created.parent, "the composition should add its child to the root")
        } finally {
            handle.dispose()
        }

        assertSame(host, borrowed.parent, "the claimed component should stay in its parent")
        assertSame(host, unclaimed.parent, "the parent's other component should stay in its parent")
        assertNull(created.parent, "the composition-created sibling should leave the root")
        assertEquals(2, host.componentCount, "only the parent's components should remain")
    }

    @Test
    fun disposingRootContentClearsComposedChildrenFromItsClaimedContainer() = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        val ownerChild = JLabel("owned")
        val composedChild = JLabel("composed")
        val borrowed = JPanel().apply { add(ownerChild) }
        val host = JPanel().apply { add(borrowed) }
        val handle =
            host.setContent(context) {
                ExistingSwingNode<JPanel, JPanel>(
                    claim = { borrowed },
                    content = { SwingNode(factory = { composedChild }) },
                )
            }

        try {
            assertSame(borrowed, composedChild.parent, "the content should compose into the claimed container")
        } finally {
            handle.dispose()
        }

        assertSame(host, borrowed.parent, "the claimed container should stay in its parent")
        assertSame(borrowed, ownerChild.parent, "the container should keep its own child")
        assertNull(composedChild.parent, "the child composed into the claimed container should be removed")
        assertEquals(1, borrowed.componentCount, "only the container's own child should remain")
    }
}
