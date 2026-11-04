@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import org.jetbrains.compose.swing.annotations.SwingComposable

/**
 * Emits [content] into the container this node is composed in, as if [content] were written there.
 *
 * A virtual node has no component. Its children are added to the nearest ancestor that has one, in composition
 * order among that ancestor's other children.
 *
 * A group is refused under a container that places children in named regions, including a composition root
 * with a single-content slot. An ordinary `setContent` root accepts groups among its indexed children.
 * An `ExistingSwingNode` directly inside a group is refused. A group cannot be used in menu content.
 *
 * @param content the composables whose components the container receives.
 */
@Composable
@SwingComposable
public inline fun VirtualSwingNode(
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    ComposeNode<VirtualNodeHolder, SwingApplier>(
        factory = ::VirtualNodeHolder,
        update = {},
        content = content,
    )
}
