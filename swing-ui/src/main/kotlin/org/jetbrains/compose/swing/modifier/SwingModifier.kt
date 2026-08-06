package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Stable
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.Component

/**
 * An ordered, immutable collection of styling/behavior [Element]s applied to a Swing component -
 * the Swing analogue of `androidx.compose.ui.Modifier`.
 *
 * Build a chain by calling the builder extensions off the [companion][SwingModifier.Companion]
 * (`SwingModifier.foreground(c).border(b)`, see the sibling `*Modifiers.kt` files) and pass it to a
 * component's `modifier` parameter. The empty modifier ([SwingModifier] itself, the companion)
 * applies nothing and is the parameter default.
 *
 * Implement [Element] to wrap any Swing property or listener the library does not ship a builder
 * for. See `docs/CUSTOM-COMPONENTS.md`.
 *
 * An [Element] declares the component type it targets via [Element.targetType]. A modifier targeting
 * a type the node is not (e.g. `border`, a `JComponent` property, on a bare `java.awt.Component`)
 * fails with a clear error naming the element and the required vs. actual type.
 *
 * Conditional composition works the way it does in Compose: `if (selected) it.background(blue)
 * else it` adds the background when selected and removes it (restoring the value the component had
 * before the modifier first touched that property) when not.
 *
 * The modifier is immutable and safe to share, hoist, and reuse as a theme token. A modifier hoisted
 * into a `remember { ... }` (or otherwise referentially stable) is applied once and skipped on every
 * recomposition where it is unchanged.
 */
@Stable
public interface SwingModifier {
    /**
     * Accumulates a value across the chain's elements in declaration (application) order. Rarely
     * needed directly.
     */
    public fun <R> foldIn(
        initial: R,
        operation: (R, Element<*, *>) -> R,
    ): R

    /**
     * Returns a modifier that applies this chain and then [other]. For two non-[additive][Element.additive]
     * elements sharing a [Element.key], the later one wins; two [additive][Element.additive] elements
     * each keep their own slot and both stay installed.
     */
    public infix fun then(other: SwingModifier): SwingModifier =
        if (other === SwingModifier) this else CombinedSwingModifier(this, other)

    /**
     * The stateful counterpart of an [Element], created once per slot and kept across recompositions.
     *
     * The applier injects the already-typed target [component], calls [onAttach] once, then calls the
     * owning [Element]'s [update][Element.update] to push the latest data onto the node's fields. A
     * listener installed in [onAttach] reads those fields, so refreshing them in `update` keeps
     * callbacks current with no reattach. [onDetach] runs symmetrically when the element leaves the
     * chain or the node is released/reused, to restore a captured original or remove an installed
     * listener.
     *
     * Subclass this to back a custom [Element]: capture the property's original in a field in
     * [onAttach], write the new value in `update`, and restore it in [onDetach]. See
     * `docs/CUSTOM-COMPONENTS.md`.
     */
    public open class Node<T : Component> {
        internal var attachedComponent: T? = null

        /**
         * The typed target, valid from [onAttach] until [onDetach]. Reading it outside that window -
         * before the node is attached, or after it has been detached - fails.
         */
        public val component: T
            get() = checkNotNull(attachedComponent) { "Node is not attached" }

        /** Runs once, after the component is injected, to install listeners or capture originals. */
        public open fun onAttach() {}

        /** Runs once, when the element leaves the chain or the node is released/reused. */
        public open fun onDetach() {}
    }

    /**
     * A single unit of a [SwingModifier] chain: one property write or one installed listener, targeting
     * a component of type [T] and backed by a stateful [Node] of type [N].
     *
     * Implement this to expose an arbitrary Swing property or listener the library does not ship a
     * builder for (see `docs/CUSTOM-COMPONENTS.md`). Declare [targetType] as the [Class] of the most
     * general component the element needs (`Component::class.java` for a property every component has,
     * `JComponent::class.java` for a `JComponent`-only property like `border`, a concrete widget class
     * for a widget-specific listener); the node's [Node.component] arrives already typed [T].
     *
     * The element is immutable data; it [create]s a [Node] once per slot and [update]s it with the
     * latest data on every chain change. The node owns the mutable state (the captured original, the
     * installed listener) and its setup/teardown via [Node.onAttach]/[Node.onDetach].
     *
     * An element is one of two kinds, selected by [additive]:
     * - A **property** element ([additive] `false`, the default) owns a single value identified by
     *   [key]: two elements with equal keys target the same property (last wins, and a key vanishing
     *   from the chain triggers [Node.onDetach]); two elements with different keys are independent.
     *   This is the right shape for appearance/layout writes (`background`, `border`, ...) where one
     *   value wins.
     * - A **subscription** element ([additive] `true`) is its own slot for every application, matched
     *   across recompositions by position rather than by [key]. This is the right shape for listeners,
     *   which are inherently additive in Swing: two `onHover {}` both install and both fire.
     */
    public interface Element<T : Component, N : Node<T>> : SwingModifier {
        /**
         * The component type this element targets. The node's [Node.component] arrives already typed
         * [T]; a node that is not a [T] is rejected at apply with a clear error. Use the most general
         * type the element needs: `Component::class.java` for a universal property,
         * `JComponent::class.java` for a `JComponent`-only one, a concrete widget class for a
         * widget-specific listener.
         */
        public val targetType: Class<T>

        /**
         * Identifies the property this element owns. Defaults to the element's runtime class, so each
         * element type is its own identity; override only when distinct instances of the same type must
         * be independent slots (e.g. a client property keyed by its property key). Ignored when
         * [additive] is `true` (additive elements are matched by position, not by key).
         */
        public val key: Any get() = javaClass

        /**
         * Whether this element accumulates rather than replaces. `false` (the default) makes it a
         * keyed, last-wins **property** slot - correct for a value like a color or a border. `true`
         * makes it a positional **subscription** slot - correct for a listener, so two applications
         * of the same builder both install and both fire instead of one replacing the other.
         */
        public val additive: Boolean get() = false

        /** Creates the stateful node. Called once per slot, when the element first enters the chain. */
        public fun create(): N

        /** Pushes this element's latest data onto [node]. Called on add and on every chain change. */
        public fun update(node: N)

        override fun <R> foldIn(
            initial: R,
            operation: (R, Element<*, *>) -> R,
        ): R = operation(initial, this)
    }

    /** The empty modifier and the entry point for building chains. */
    public companion object : SwingModifier {
        override fun <R> foldIn(
            initial: R,
            operation: (R, Element<*, *>) -> R,
        ): R = initial

        override infix fun then(other: SwingModifier): SwingModifier = other
    }
}

/** Internal cons-cell joining two modifiers. */
internal class CombinedSwingModifier(
    private val outer: SwingModifier,
    private val inner: SwingModifier,
) : SwingModifier {
    override fun <R> foldIn(
        initial: R,
        operation: (R, SwingModifier.Element<*, *>) -> R,
    ): R = inner.foldIn(outer.foldIn(initial, operation), operation)
}

/**
 * Mutable per-slot state held by the node holder across recompositions: one slot's node, the element
 * currently occupying it, and the type of element the node was created for.
 *
 * Constructed at the statically-typed apply site (see [attachElement]), where the element's [T] and
 * [N] are known, so it captures the concrete [node] together with the [elementType] that created it.
 * A later recomposition pushes a fresh element instance through [rebindAndRefresh] without the diff
 * path ever re-narrowing the node's type, and [canRebind] runtime-checks an incoming element against
 * [elementType], so that rebind's narrowing is a verified [Class.cast] rather than an unchecked cast.
 */
internal class ElementRecord<T : Component, N : SwingModifier.Node<T>>(
    private val node: N,
    private val elementType: Class<out SwingModifier.Element<T, N>>,
    private var element: SwingModifier.Element<T, N>,
) {
    /** Tears the slot's node down via [SwingModifier.Node.onDetach]. */
    fun detach() {
        // onDetach runs while the target is still injected: a node restores its captured original
        // through component. The target is cleared afterwards, closing the attached window.
        node.onDetach()
        node.attachedComponent = null
    }

    /**
     * Whether [element] is of the kind this slot's node was created for, i.e. whether
     * [rebindAndRefresh] can apply it through the existing node. A diff hands a slot an element of a
     * different kind when a conditional chain changes shape; the slot cannot host it, so the caller
     * [detach]es this record and attaches the element fresh instead.
     */
    fun canRebind(element: SwingModifier.Element<*, *>): Boolean = elementType.isInstance(element)

    /**
     * Rebinds the slot to a (possibly new) [element] instance, then refreshes it against [target].
     * A fresh element instance arrives each recomposition (its callbacks are new), so rebinding keeps
     * a node-installed listener's callbacks current via [SwingModifier.Element.update] without
     * reattaching. Only call when [canRebind] holds: the slot's node statically knows its own type,
     * so a [canRebind]-checked [element] is applied through it without an unchecked cast.
     */
    fun rebindAndRefresh(
        element: SwingModifier.Element<*, *>,
        target: Component,
    ) {
        this.element = elementType.cast(element)
        refresh(target)
    }

    /** Re-narrows the target and pushes the element currently occupying this slot onto the node. */
    private fun refresh(target: Component): Unit = refreshElement(element, target, node)
}

/**
 * The diff state for one node's modifier chain: the elements applied last.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
public class SwingModifierState internal constructor() {
    internal val records: LinkedHashMap<Any, ElementRecord<*, *>> = LinkedHashMap()
    internal val additiveRecords: ArrayList<ElementRecord<*, *>> = ArrayList()
}

/**
 * Applies [modifier] to this node, diffing against the chain applied on the previous composition:
 * new elements are applied, persisting elements re-applied, and elements that disappeared are
 * [detached][SwingModifier.Node.onDetach] (restoring the value the component had before the modifier
 * first touched that property).
 *
 * Call it as the last statement of a component's `update` block, after the component's own `set`s,
 * so a modifier can override component defaults. A hoisted (referentially stable) modifier is applied
 * once and skipped thereafter. Available on any node whose component is a [Component].
 */
public fun SwingNodeUpdater<out Component>.applyModifier(modifier: SwingModifier): Unit =
    updater.set(modifier) { applyModifierDiff(it) }

internal fun SwingNodeHolder<Component>.applyModifierDiff(modifier: SwingModifier) {
    val target = component
    val state = modifierState ?: SwingModifierState().also { modifierState = it }

    // Walk the chain once, partitioning into keyed property elements (last-wins by key) and additive
    // subscription elements (each its own slot, matched by position).
    val incomingKeyed = LinkedHashMap<Any, SwingModifier.Element<*, *>>()
    val incomingAdditive = ArrayList<SwingModifier.Element<*, *>>()
    modifier.foldIn(Unit) { _, element ->
        if (element.additive) incomingAdditive.add(element) else incomingKeyed[element.key] = element
    }

    diffKeyedElements(target, state.records, incomingKeyed)
    diffAdditiveElements(target, state.additiveRecords, incomingAdditive)
}

/**
 * Creates a node for [element], injects the [checkedTarget] component, runs [SwingModifier.Node.onAttach],
 * then pushes the element's data with [SwingModifier.Element.update] - the first-install order. Returns
 * an [ElementRecord] whose [ElementRecord.rebindAndRefresh] re-runs the element's `update` against the
 * same node.
 *
 * Typing [N] here keeps `create()`/`update()` together with no cast: the returned record holds the
 * concrete node, so a later recomposition pushes fresh data without re-narrowing the node's type.
 */
private fun <T : Component, N : SwingModifier.Node<T>> attachElement(
    element: SwingModifier.Element<T, N>,
    raw: Component,
): ElementRecord<T, N> {
    val typed = checkedTarget(element, raw)
    val node = element.create()
    node.attachedComponent = typed
    node.onAttach()
    element.update(node)
    // element.javaClass is typed Class<out Element<T, N>>: capturing it lets a later rebind
    // Class.cast-check a new element of the same type onto this node without an unchecked cast.
    return ElementRecord(node, element.javaClass, element)
}

/**
 * Re-checks the target type (the component is stable, but re-narrowing keeps the error path identical
 * to first apply) and pushes the element's latest data onto its node via [SwingModifier.Element.update].
 */
private fun <T : Component, N : SwingModifier.Node<T>> refreshElement(
    element: SwingModifier.Element<T, N>,
    raw: Component,
    node: N,
) {
    checkedTarget(element, raw)
    element.update(node)
}

/**
 * Narrows the node to an element's target type. A node that is not the required type is rejected with
 * a clear message naming the element ([SwingModifier.Element.key]) and the required vs. actual type.
 */
private fun <T : Component> checkedTarget(
    element: SwingModifier.Element<T, *>,
    raw: Component,
): T {
    val targetType = element.targetType
    if (!targetType.isInstance(raw)) {
        error(
            "Modifier element ${element.key} requires a ${targetType.name} target, " +
                "but the component is a ${raw.javaClass.name}",
        )
    }
    return targetType.cast(raw)
}

/** Diffs the keyed (last-wins) property elements: detach departed keys, then add/refresh the rest. */
private fun diffKeyedElements(
    target: Component,
    records: LinkedHashMap<Any, ElementRecord<*, *>>,
    incoming: LinkedHashMap<Any, SwingModifier.Element<*, *>>,
) {
    // Detach + drop elements whose key left the chain.
    val iterator = records.entries.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        if (entry.key !in incoming) {
            entry.value.detach()
            iterator.remove()
        }
    }

    // Apply (add or refresh) the current chain. A persisting slot keeps its node and refreshes it via
    // update(), which keeps a node-installed listener's callbacks current without reattaching.
    for ((key, element) in incoming) {
        val record = records[key]
        if (record == null) {
            records[key] = attachElement(element, target)
        } else {
            record.rebindAndRefresh(element, target)
        }
    }
}

/**
 * Diffs the additive (subscription) elements by position: a position present last time but gone now is
 * detached and removed; a persisting position keeps its node and is refreshed via update(); a new
 * trailing position is created and attached. A conditional chain changing shape can hand a persisting
 * position an element of a different kind; the slot's node cannot host it, so the slot is swapped
 * wholesale - the old node detaches (removing its listener) and the new element attaches fresh.
 */
private fun diffAdditiveElements(
    target: Component,
    records: ArrayList<ElementRecord<*, *>>,
    incoming: ArrayList<SwingModifier.Element<*, *>>,
) {
    // Detach + drop trailing positions that left the chain.
    while (records.size > incoming.size) {
        val record = records.removeAt(records.size - 1)
        record.detach()
    }

    // Apply (add or refresh) each position. A persisting position of the same kind refreshes via
    // update(), keeping a node-installed listener's callbacks current without reattaching.
    for (index in incoming.indices) {
        val element = incoming[index]
        val record = records.getOrNull(index)
        when {
            record == null -> {
                records.add(attachElement(element, target))
            }

            record.canRebind(element) -> {
                record.rebindAndRefresh(element, target)
            }

            else -> {
                record.detach()
                records[index] = attachElement(element, target)
            }
        }
    }
}

/**
 * Detaches every modifier-installed node and restores every modified property to the value captured
 * before the modifier touched it. Invoked by [SwingNodeHolder] on release/reuse/deactivate so a
 * recycled node starts clean.
 */
internal fun SwingNodeHolder<*>.resetModifierState() {
    val state = modifierState ?: return
    for (record in state.records.values) {
        record.detach()
    }
    for (record in state.additiveRecords) {
        record.detach()
    }
    state.records.clear()
    state.additiveRecords.clear()
    modifierState = null
}
