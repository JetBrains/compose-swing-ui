@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.currentComposer
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyCompositionLocalMap
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.materialize
import java.awt.Component

/**
 * Configures a component its parent already built and keeps, such as a `JScrollPane`'s scroll bars or a
 * `JLayer`'s glass pane, the way [SwingNode] configures a component its own factory builds.
 *
 * The parent is the component of the node this one is composed directly under. [claim] runs on the parent
 * when the node is created, and returns the component this node configures; it need not be a direct Swing
 * child of the parent. A function reference infers both type parameters -
 * `ExistingSwingNode(claim = JScrollPane::getVerticalScrollBar)` - and a lambda needs them written:
 * `ExistingSwingNode<JScrollPane, JScrollBar>(claim = { verticalScrollBar })`.
 *
 * [claim] is read once, when the node is created, as [SwingNode] reads its `factory`: a later [claim] is
 * not called and changes nothing. The claim holds for the node's life. Relocated under another parent, such as
 * by `movableContentOf`, the node is refused. A part the parent replaces afterwards keeps nothing of this node's
 * declarations, so the code that replaces a part keys the node claiming it on that part -
 * `key(part) { ExistingSwingNode(...) }` - and a new node claims the new part. Replacing a claimed part from
 * outside that code is undefined behavior.
 *
 * The node never adds, removes or moves the claimed component. When the node leaves the composition,
 * every property [modifier] declared is given back and every listener it installed is removed; the
 * component stays where its parent holds it. What [update] wrote stays on the component.
 *
 * Within one composition, a component is claimed by at most one node at a time. A second node claiming it
 * is refused, including a node that replaces the first one under a changed `key`.
 *
 * Under a host that holds its children in regions, the node names the region its component stands in with
 * [org.jetbrains.compose.swing.modifier.layout.slot] without an attachment. Under an indexed
 * host it names nothing. That is the one placement the node declares: the parent placed the component, so
 * parent data such as `layoutConstraint` or a row's `weight` is refused.
 *
 * @param claim finds the component this node configures on its parent. It is refused when the parent is not
 *   a [P], or when it returns `null` or something that is not a [T], naming the parent, the expected types
 *   and what was found.
 * @param modifier the [SwingModifier] applied to the claimed component, after [update] has run.
 * @param update typed update block; see [SwingNodeUpdater].
 */
@Composable
@SwingComposable
public inline fun <reified P : Component, reified T : Component> ExistingSwingNode(
    noinline claim: @DisallowComposableCalls P.() -> T?,
    modifier: SwingModifier = SwingModifier,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
) {
    val materialized = currentComposer.materialize(modifier)
    val localMap = currentComposer.currentCompositionLocalMap
    ComposeNode<SwingNodeHolder<T>, SwingApplier>(
        factory = { ExistingNodeHolder(P::class.java, T::class.java, null, claim) },
        update = {
            val updater = SwingNodeUpdater(this)
            updater.applyCompositionLocalMap(localMap)
            updater.update()
            updater.applyModifier(localMap, materialized)
        },
    )
}

/**
 * Container variant of [ExistingSwingNode] that composes [content] into the claimed container.
 *
 * The claimed container keeps its own layout manager.
 *
 * When the node leaves the composition, the children [content] composed are removed from the container,
 * and what [update] wrote - the layout included - stays on it.
 *
 * @param claim finds the container this node configures on its parent; see the leaf overload.
 * @param modifier the [SwingModifier] applied to the claimed container, after [update] has run.
 * @param update typed update block; see [SwingNodeUpdater].
 * @param childPlacement how children composed under this node are held; see [ChildPlacement]. Defaults
 *   to [ChildPlacement.Indexed].
 * @param content the composables the claimed container holds as its children.
 */
@Composable
@SwingComposable
public inline fun <reified P : Component, reified T : Component> ExistingSwingNode(
    noinline claim: @DisallowComposableCalls P.() -> T?,
    modifier: SwingModifier = SwingModifier,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    childPlacement: ChildPlacement = ChildPlacement.Indexed,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    ExistingSwingNode<P, T>(declaringCall = null, claim, modifier, update, childPlacement, content)
}

/** [ExistingSwingNode] whose [declaringCall] names it in a refusal when no slot does. */
@Composable
@SwingComposable
@PublishedApi
internal inline fun <reified P : Component, reified T : Component> ExistingSwingNode(
    declaringCall: String?,
    noinline claim: @DisallowComposableCalls P.() -> Component?,
    modifier: SwingModifier,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit,
    childPlacement: ChildPlacement,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    val materialized = currentComposer.materialize(modifier)
    val localMap = currentComposer.currentCompositionLocalMap
    ComposeNode<SwingNodeHolder<T>, SwingApplier>(
        factory = { ExistingNodeHolder(P::class.java, T::class.java, declaringCall, claim) },
        update = {
            set(childPlacement) { this.childPlacement = it }
            val updater = SwingNodeUpdater(this)
            updater.applyCompositionLocalMap(localMap)
            updater.update()
            updater.applyModifier(localMap, materialized)
        },
        content = content,
    )
}
