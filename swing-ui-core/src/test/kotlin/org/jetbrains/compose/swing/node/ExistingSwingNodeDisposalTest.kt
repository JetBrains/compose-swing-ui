// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExistingSwingNodeDisposalTest {
    @Test
    fun disposingRootContentLeavesTheClaimedComponentInItsParent() = runComposeSwingTest {
        var mounted by mutableStateOf(true)
        setContent {
            val on = mounted
            val parent = rememberCompositionContext()
            SwingNode(
                factory = { OwningHost(JLabel("owned")) },
                update = {
                    set(parent to on) { (context, active) ->
                        mount(context.takeIf { active }) {
                            ExistingSwingNode<OwningHost, _, JLabel>(claim = { part as JLabel })
                        }
                    }
                },
                onRelease = { release() },
            )
        }
        awaitIdle()
        val host = onNodeOfType<OwningHost>()

        host.assertProperty(true, "the parent should hold the claimed component") { part.parent === this }
        mounted = false
        awaitIdle()

        host.assertProperty(
            true,
            "disposing the content should leave the claimed component in its parent",
        ) { part.parent === this }
        host.assertProperty(1, "the parent should keep its component") { componentCount }
    }

    @Test
    fun disposingRootContentRemovesItsCreatedSiblingAndKeepsParentChildren() = runComposeSwingTest {
        var mounted by mutableStateOf(true)
        setContent {
            val on = mounted
            val parent = rememberCompositionContext()
            SwingNode(
                factory = { OwningHost(JLabel("claimed"), JLabel("unclaimed")) },
                update = {
                    set(parent to on) { (context, active) ->
                        mount(context.takeIf { active }) {
                            ExistingSwingNode<OwningHost, _, JLabel>(claim = { part as JLabel })
                            Label("composed")
                        }
                    }
                },
                onRelease = { release() },
            )
        }
        awaitIdle()
        val host = onNodeOfType<OwningHost>()

        val hostComponent = host.fetch()
        val created = onNodeWithText("composed").assertExists().fetch()
        assertSame(hostComponent, created.parent, "the composition should add its child to the root")
        val unclaimed = onNodeWithText("unclaimed").fetch()
        mounted = false
        awaitIdle()

        host.assertProperty(
            true,
            "the claimed component should stay in its parent",
        ) { part.parent === this }
        host.assertProperty(
            true,
            "the parent's other component should stay in its parent",
        ) { unclaimed.parent === this }
        assertNull(created.parent, "the composition-created sibling should leave the root")
        host.assertProperty(2, "only the parent's components should remain") { componentCount }
    }

    @Test
    fun disposingRootContentClearsComposedChildrenFromItsClaimedContainer() = runComposeSwingTest {
        var mounted by mutableStateOf(true)
        setContent {
            val on = mounted
            val parent = rememberCompositionContext()
            SwingNode(
                factory = { OwningHost(JPanel().apply { add(JLabel("owned")) }) },
                update = {
                    set(parent to on) { (context, active) ->
                        mount(context.takeIf { active }) {
                            ExistingSwingNode<OwningHost, _, JPanel>(
                                claim = { part as JPanel },
                                content = { Label("composed") },
                            )
                        }
                    }
                },
                onRelease = { release() },
            )
        }
        awaitIdle()
        val host = onNodeOfType<OwningHost>()

        val composedChild =
            onNodeWithText("composed")
                .assertExists()
                .assertProperty(true, "the content should compose into the claimed container") {
                    (parent?.parent as? OwningHost)?.part === parent
                }.fetch()
        val ownerChild = onNodeWithText("owned").fetch()
        mounted = false
        awaitIdle()

        host.assertProperty(true, "the claimed container should stay in its parent") { part.parent === this }
        host.assertProperty(
            true,
            "the container should keep its own child",
        ) { ownerChild.parent === part }
        assertNull(composedChild.parent, "the child composed into the claimed container should be removed")
        host.assertProperty(
            1,
            "only the container's own child should remain",
        ) { (part as Container).componentCount }
    }

    private class OwningHost(
        vararg parts: Component,
    ) : JPanel() {
        val part: Component = parts.first()
        private var content: DisposableHandle? = null

        init {
            parts.forEach { add(it) }
        }

        fun mount(
            parent: CompositionContext?,
            content: @Composable () -> Unit,
        ) {
            this.content?.dispose()
            this.content = parent?.let { setContent(it, content) }
        }

        fun release() = mount(null) {}
    }
}
