@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import java.awt.Component

/**
 * Emits one menu node into the surrounding [MenuApplier] composition: [factory] builds the backing
 * Swing widget, [update] maps composition state onto it, and [content] supplies any nested menu items
 * (empty for a leaf such as a single item or separator).
 *
 * @param factory builds the backing Swing widget, once, on the pass that creates the node.
 * @param update typed update block; see [SwingNodeUpdater]. It runs on every pass and may make no
 *   composable calls of its own.
 * @param content the nested menu items. Defaults to none, which is what a leaf declares.
 */
@Composable
@SwingMenuComposable
public inline fun <reified T : Component> MenuNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit,
    content:
        @Composable @SwingMenuComposable
        () -> Unit = {},
) {
    ComposeNode<SwingNodeHolder<T>, MenuApplier>(
        factory = { SwingNodeHolder(factory()) },
        update = { SwingNodeUpdater(this).update() },
        content = content,
    )
}
