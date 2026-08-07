@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.SwingComposable
import java.awt.Component

/**
 * Declares a leaf or container Swing node in the composition.
 *
 * This is the primary entry point for defining a **custom component**: wrap any Swing [Component] by
 * passing a [factory] that creates it and an [update] block that maps composition state onto it.
 * Every built-in wrapper (`Button`, `TextField`, `Slider`, ...) is built on top of this function - see
 * `docs/CUSTOM-COMPONENTS.md`.
 *
 * Where this node sits in its parent is declared on the node's own modifier chain: a layout constraint
 * the parent's layout manager understands (e.g. a `BorderLayout` region), or a slot of a host that
 * reaches its children through dedicated setters (e.g. a `JScrollPane` region). That placement reaches
 * the node through [org.jetbrains.compose.swing.modifier.applyModifier], so a component whose [update]
 * never applies a modifier cannot be placed at all.
 *
 * The node is recyclable: when it is conditionally shown/hidden across recompositions (e.g. a
 * [androidx.compose.runtime.ReusableContentHost] parked and reactivated, or structurally-identical
 * content replacing it in the same slot) the runtime reuses the existing backing [Component] for the
 * new content from a clean baseline rather than allocating a fresh one. Code outside the node that has
 * to reach the backing component therefore takes it from the node on every pass instead of holding on
 * to the instance it once saw: only the component the node currently holds is the one the composition
 * is driving.
 *
 * When [hostsSubcompositions] is `true`, a `setContent` call on a descendant Swing component joins
 * this composition, sharing its scope and [androidx.compose.runtime.CompositionLocal]s. The component
 * must be a [javax.swing.JComponent] or this throws [IllegalStateException]. Defaults to `false`.
 *
 * @param factory builds the backing Swing component.
 * @param update typed update block; see [SwingNodeUpdater]. Install listeners through the modifier
 *   mechanism - see [org.jetbrains.compose.swing.modifier.listener].
 * @param onRelease optional teardown run when the node leaves the composition for good.
 * @param hostsSubcompositions when `true`, a descendant component's `setContent` joins this
 *   composition. Defaults to `false`.
 * @param childPlacement how children composed under this node are held; see [ChildPlacement]. Defaults
 *   to [ChildPlacement.Indexed].
 */
@Composable
@SwingComposable
public inline fun <reified T : Component> SwingNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    noinline onRelease: (T.() -> Unit)? = null,
    hostsSubcompositions: Boolean = false,
    childPlacement: ChildPlacement = ChildPlacement.Indexed,
) {
    val parentContext = if (hostsSubcompositions) rememberCompositionContext() else null
    ReusableComposeNode<SwingNodeHolder<T>, SwingApplier>(
        factory = { SwingNodeHolder(factory()) },
        update = {
            set(childPlacement) { this.childPlacement = it }
            set(parentContext) { hostSubcompositions(it) }
            SwingNodeUpdater(this).update()
            set(onRelease) { release ->
                releaseBlock =
                    if (release != null) {
                        { component.release() }
                    } else {
                        null
                    }
            }
        },
    )
}

/**
 * Container variant of [SwingNode] that hosts composable [content] as children.
 *
 * Use this overload when your custom Swing component is a [java.awt.Container] that should host
 * further composables. See `docs/CUSTOM-COMPONENTS.md`.
 *
 * The children [content] emits are exactly the ones the current composition declares. A container that
 * lets callers declare its children through a scope therefore builds and fills that scope afresh on
 * every pass: a child the caller stops declaring (behind an `if`, say) then drops out of [content] and
 * is removed, where a remembered, mutated scope would go on emitting the stale declaration.
 *
 * Identity within [content] is positional: the state a child remembers belongs to the position it is
 * emitted at rather than to the declaration made there, so reordering declarations leaves that state
 * where it was unless [androidx.compose.runtime.key] gives each child an identity of its own.
 *
 * [childPlacement] states how the component holds those children: added to it by index, which is what a
 * container with a layout manager does and what this defaults to, or each in a named region the component
 * reaches through a setter of its own, the way a `JScrollPane` shows one component per region. Under a
 * region-holding placement every child names the region it fills, on its own modifier chain and usually
 * through a builder the container's scope offers; under the indexed placement no child may name one. A
 * child that does not match the placement is refused as it arrives, naming this component and the
 * builders that would place it.
 *
 * The component's own child array holds the indexed children in the order [content] emits them, whatever
 * layout constraint each of them declares, so a layout manager of your own can read `getComponents` as
 * the structure this composition declared and derive from all of it at measure or layout time.
 *
 * [hostsSubcompositions] behaves as in the leaf overload. Defaults to `false`.
 */
@Composable
@SwingComposable
public inline fun <reified T : Component> SwingNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    noinline onRelease: (T.() -> Unit)? = null,
    hostsSubcompositions: Boolean = false,
    childPlacement: ChildPlacement = ChildPlacement.Indexed,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    val parentContext = if (hostsSubcompositions) rememberCompositionContext() else null
    ReusableComposeNode<SwingNodeHolder<T>, SwingApplier>(
        factory = { SwingNodeHolder(factory()) },
        update = {
            set(childPlacement) { this.childPlacement = it }
            set(parentContext) { hostSubcompositions(it) }
            SwingNodeUpdater(this).update()
            set(onRelease) { release ->
                releaseBlock =
                    if (release != null) {
                        { component.release() }
                    } else {
                        null
                    }
            }
        },
        content = { content() },
    )
}
