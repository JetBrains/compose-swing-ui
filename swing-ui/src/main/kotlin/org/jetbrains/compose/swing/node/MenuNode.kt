@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.Component

/**
 * Emits one menu node into the surrounding [MenuApplier] composition: [factory] builds the backing
 * Swing widget, [update] maps composition state onto it, and [content] supplies any nested menu items
 * (empty for a leaf such as a single item or separator).
 *
 * @param factory builds the backing Swing widget, once, on the pass that creates the node.
 * @param modifier the [SwingModifier] applied to the widget, after [update] has run, so a chain can
 *   override what the widget's own state declared. Forward the chain the enclosing composable took;
 *   defaults to [SwingModifier], the empty chain.
 * @param update typed update block; see [SwingNodeUpdater]. It runs on every pass and may make no
 *   composable calls of its own. Defaults to declaring nothing.
 * @param content the nested menu items. Defaults to none, which is what a leaf declares.
 */
@Composable
@SwingMenuComposable
public inline fun <reified T : Component> MenuNode(
    noinline factory: () -> T,
    modifier: SwingModifier = SwingModifier,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    content:
        @Composable @SwingMenuComposable
        () -> Unit = {},
) {
    ComposeNode<SwingNodeHolder<T>, MenuApplier>(
        factory = { SwingNodeHolder(factory()) },
        update = {
            val updater = SwingNodeUpdater(this)
            updater.update()
            updater.applyModifier(modifier)
        },
        content = content,
    )
}
