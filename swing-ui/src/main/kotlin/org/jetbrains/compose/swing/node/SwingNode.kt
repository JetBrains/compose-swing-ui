@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
 * A slot-based parent can dictate this node's placement (e.g. a `BorderLayout` region declared
 * through a `BorderPanel` slot) or install it into a host that reaches children through dedicated
 * setters (e.g. a `JScrollPane` region), without the child knowing its container. A container of your
 * own declares the placement of the children it composes with [SwingConstraint].
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
 */
@Composable
@SwingComposable
public inline fun <reified T : Component> SwingNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    noinline onRelease: (T.() -> Unit)? = null,
    hostsSubcompositions: Boolean = false,
) {
    val constraint = LocalSwingConstraint.current
    val slotAttachment = LocalSlotAttachment.current
    val parentContext = if (hostsSubcompositions) rememberCompositionContext() else null
    ReusableComposeNode<SwingNodeHolder<T>, SwingApplier>(
        // The slot attachment is read in the factory, not the update block, because the applier needs
        // it at insert time (insertBottomUp installs the component through it) - before the node's
        // update changes run.
        factory = { SwingNodeHolder(factory()).also { it.slotAttachment = slotAttachment } },
        update = {
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
            // The placement the parent provides, applied on change: the component is re-registered
            // with its parent's layout manager only when the constraint this composition asks for
            // differs from the one the manager holds.
            set(constraint) { applyConstraint(it) }
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
 * [hostsSubcompositions] behaves as in the leaf overload. Defaults to `false`.
 */
@Composable
@SwingComposable
public inline fun <reified T : Component> SwingNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    noinline onRelease: (T.() -> Unit)? = null,
    hostsSubcompositions: Boolean = false,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    val constraint = LocalSwingConstraint.current
    val slotAttachment = LocalSlotAttachment.current
    val parentContext = if (hostsSubcompositions) rememberCompositionContext() else null
    ReusableComposeNode<SwingNodeHolder<T>, SwingApplier>(
        // The slot attachment is read in the factory, not the update block, because the applier needs
        // it at insert time (insertBottomUp installs the component through it) - before the node's
        // update changes run.
        factory = { SwingNodeHolder(factory()).also { it.slotAttachment = slotAttachment } },
        update = {
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
            // The placement the parent provides, applied on change: the component is re-registered
            // with its parent's layout manager only when the constraint this composition asks for
            // differs from the one the manager holds.
            set(constraint) { applyConstraint(it) }
        },
        // This node has consumed any incoming layout constraint and slot attachment for its OWN
        // placement; its children must not re-consume them (they belong inside this component, placed
        // by its own layout manager and attached the ordinary way). Reset both locals to their default
        // for the subtree, so a child gets the default (null) constraint unless this container itself
        // re-provides one per region (as BorderPanel does under this null baseline). Without resetting
        // the constraint, a constrained-layout child (e.g. a GridBagLayout panel placed in a
        // BorderPanel region) would inherit the parent region's BorderLayout constraint string and the
        // applier would reject it (GridBagLayout.addLayoutComponent demands a GridBagConstraints).
        content = {
            CompositionLocalProvider(
                LocalSwingConstraint provides null,
                LocalSlotAttachment provides null,
            ) {
                content()
            }
        },
    )
}
