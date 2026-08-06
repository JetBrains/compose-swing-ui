@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.ReusableComposeNode
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import java.awt.Component

/**
 * Emits one menu node into the surrounding [MenuApplier] composition: [factory] builds the backing
 * Swing widget, [update] maps composition state onto it, and [content] supplies any nested menu items
 * (empty for a leaf such as a single item or separator).
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
    ReusableComposeNode<SwingNodeHolder<T>, MenuApplier>(
        factory = { SwingNodeHolder(factory()) },
        update = { SwingNodeUpdater(this).update() },
        content = content,
    )
}
