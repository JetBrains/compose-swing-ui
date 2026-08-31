package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Stable
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.layout.checkOnePlacement
import org.jetbrains.compose.swing.modifier.layout.removeLayoutConstraint
import org.jetbrains.compose.swing.modifier.layout.removeSlot
import org.jetbrains.compose.swing.node.DeclaredSlot
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.Component

/**
 * An ordered, immutable collection of styling/behavior [NodeElement]s applied to a Swing component -
 * the Swing analogue of `androidx.compose.ui.Modifier`.
 *
 * Build a chain by calling the builder extensions off the [companion][SwingModifier.Companion]
 * (`SwingModifier.foreground(c).border(b)`, see the sibling `*Modifiers.kt` files) and pass it to a
 * component's `modifier` parameter. The empty modifier ([SwingModifier] itself, the companion)
 * applies nothing and is the parameter default.
 *
 * Implement [NodeElement] to wrap any Swing property or listener the library does not ship a builder
 * for. See `docs/CUSTOM-COMPONENTS.md`.
 *
 * An [NodeElement] declares the component type it targets via [NodeElement.targetType]. A modifier targeting
 * a type the node is not (e.g. `border`, a `JComponent` property, on a bare `java.awt.Component`)
 * fails with a clear error naming the element and the required vs. actual type.
 *
 * Conditional composition works the way it does in Compose: `if (selected) it.background(blue)
 * else it` adds the background when selected and removes it (restoring the value the component had
 * before the modifier first touched that property) when not.
 *
 * The modifier is immutable and safe to share, hoist, and reuse as a theme token. Building the chain
 * inline in the composable body is the intended style and needs no `remember`: a chain declaring what
 * the one last applied to a component declares is skipped, and each element is judged on its own, so a
 * property whose declared value has not changed is not written again. See [NodeElement] for what an
 * element that did change costs.
 *
 * A modifier is applied *to* a node, and a node *holds* the modifier state that outlives one
 * apply pass, so `modifier` and `node` are one boundary read from two sides, not two layers - a
 * declaration belongs with the type it is an operation on.
 */
@Stable
public interface SwingModifier {
    /**
     * Accumulates a value across the chain's elements in declaration (application) order. Rarely
     * needed directly.
     *
     * @param initial the value handed to the first element of the chain.
     * @param operation combines the value accumulated so far with one element and yields the value the
     *   next element is handed.
     * @return the accumulated value; [initial] where the chain has no elements.
     */
    public fun <R> foldIn(
        initial: R,
        operation: (R, NodeElement<*, *>) -> R,
    ): R

    /**
     * Returns a modifier that applies this chain and then [other]. For two non-[additive][NodeElement.additive]
     * elements sharing a [NodeElement.key], the later one wins; two [additive][NodeElement.additive] elements
     * each keep their own slot and both stay installed.
     *
     * @return the two chains joined; this chain itself where [other] is the empty modifier.
     */
    public infix fun then(other: SwingModifier): SwingModifier =
        if (other === SwingModifier) this else CombinedSwingModifier(this, other)

    /**
     * The stateful counterpart of an [NodeElement], created once per slot and kept across recompositions.
     *
     * The applier injects the already-typed target [component], calls [onAttach] once, then calls the
     * owning [NodeElement]'s [update][NodeElement.update] to push the latest data onto the node's fields - so a
     * listener installed in [onAttach] is live before the first `update` lands, and every field it reads
     * needs an initial value that stands until then. [onDetach] runs symmetrically when the element leaves
     * the chain or the node is released/reused, to restore a captured original or remove an installed
     * listener.
     *
     * Subclass this to back a custom [NodeElement]: capture the property's original in a field in
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
     * builder for (see `docs/CUSTOM-COMPONENTS.md`). See [targetType] for how to declare the component
     * type the element targets; the node's [Node.component] arrives already typed [T].
     *
     * The element is immutable and throwaway: every pass builds a fresh one carrying the values declared
     * then. The [Node] is the long-lived side - [create]d once per slot, kept for as long as an element of
     * this type occupies it, and owning the mutable state (the captured original, the installed listener)
     * with its setup and teardown in [Node.onAttach]/[Node.onDetach]. An element unequal to the one its
     * slot holds costs one [update] call and nothing else: the node is not recreated and a listener it
     * installed is not reattached. So a callback written inline as a lambda is the intended style and needs
     * no `remember` - push it onto the node in [update] and have the node read it when the event fires.
     *
     * An element is one of two kinds, selected by [additive]: a **property** element (the default),
     * right for a value like `background` or `border`, or a **subscription** element, right for a
     * listener like `onHover`. See [additive] and [key] for how each kind is matched across
     * recompositions.
     *
     * [equals] and [hashCode] are abstract, so every element states its own equality: the slot skips an
     * incoming element equal to the one it holds, and equality is therefore the contract deciding when
     * [update] runs at all. Compare a value structurally - a `data class` says that in one word - and
     * compare anything the node *registers* (a listener, a callback, a binding, a slot attachment) with
     * `===`, since such a field may carry an `equals` of its own under which two instances the node must
     * tell apart compare equal, leaving the node holding the one the composition replaced. An element
     * that carries nothing, and one whose write has to be redone whatever the declaration says, are
     * equal only to themselves - `this === other`. A freshly built instance is then unequal to the one
     * the slot holds and every pass applies it; an element declared as an `object` hands the slot the
     * same instance each pass, so it is applied once.
     */
    public abstract class NodeElement<T : Component, N : Node<T>> : SwingModifier {
        /**
         * The component type this element targets. The node's [Node.component] arrives already typed
         * [T]; a node that is not a [T] is rejected at apply with a clear error. Use the most general
         * type the element needs: `Component::class.java` for a universal property,
         * `JComponent::class.java` for a `JComponent`-only one, a concrete widget class for a
         * widget-specific listener.
         */
        public abstract val targetType: Class<T>

        /**
         * Identifies the property this element owns. Defaults to the element's runtime class, so each
         * element type is its own identity; override only when distinct instances of the same type must
         * be independent slots (e.g. a client property keyed by its property key). Ignored when
         * [additive] is `true` (additive elements are matched by position, not by key).
         */
        public open val key: Any get() = javaClass

        /**
         * Whether this element accumulates rather than replaces. `false` (the default) makes it a
         * keyed, last-wins **property** slot - correct for a value like a color or a border. `true`
         * makes it a positional **subscription** slot - correct for a listener, so two applications
         * of the same builder both install and both fire instead of one replacing the other.
         */
        public open val additive: Boolean get() = false

        /**
         * What this element is called, for a message about it and for a tool showing the chain a
         * component carries. Defaults to the element's class name, which serves an element declared as
         * its own class; an element built by a shared builder shares that class with every other
         * property built the same way, and names the property it writes instead.
         *
         * Display only. [key] is what tells one slot from another, so two elements sharing a name still
         * occupy their own slots and neither replaces the other.
         */
        public open val name: String get() = javaClass.simpleName

        /**
         * What this element declares, under the name each value is declared by - the argument of a
         * single-valued property under the property's own [name], and one entry per argument for an
         * element carrying several. Empty for an element that carries nothing.
         *
         * A tool reads this to show what a component was built with. It is read on demand and never
         * during an apply, so an element assembles it when asked rather than holding it.
         */
        public open val declaredValues: Map<String, Any?> get() = emptyMap()

        /** Creates the stateful node. Called once per slot, when the element first enters the chain. */
        public abstract fun create(): N

        /**
         * Pushes this element's latest data onto [node]. Called on add, and on a chain change that hands
         * this slot an element unequal to the one it holds.
         *
         * @param node the node [create] returned for this slot, already attached and past
         *   [Node.onAttach], so [Node.component] is readable from here.
         */
        public abstract fun update(node: N)

        abstract override fun equals(other: Any?): Boolean

        abstract override fun hashCode(): Int

        /**
         * Whether the slot this element occupies - [node] is the one it holds - can keep it for [next],
         * the element the pass being applied declares in its place, having first written onto [node]
         * whatever [next] declares that is read live rather than applied.
         *
         * Equality is the whole answer for an element that carries only values, and that is what this
         * does. An element that hands its node something read at event time rather than written onto it
         * overrides this to write the newer one there, and answers `true` for a slot whose registration
         * is unchanged; the walk that asked then leaves the node and the listener it installed alone.
         *
         * What is written belongs to [node] and to no other, so an element handed to two slots - a
         * hoisted chain reaching two components - carries nothing either of them can reach the other by.
         */
        internal open fun adopt(
            node: N,
            next: NodeElement<*, *>,
        ): Boolean = this == next

        final override fun <R> foldIn(
            initial: R,
            operation: (R, NodeElement<*, *>) -> R,
        ): R = operation(initial, this)
    }

    /** The empty modifier and the entry point for building chains. */
    public companion object : SwingModifier {
        override fun <R> foldIn(
            initial: R,
            operation: (R, NodeElement<*, *>) -> R,
        ): R = initial

        override infix fun then(other: SwingModifier): SwingModifier = other
    }
}

/**
 * Internal cons-cell joining two modifiers.
 *
 * Two cells are equal when both halves are, so a chain rebuilt from equal parts equals the chain
 * built on the previous composition and the whole apply is skipped.
 */
internal class CombinedSwingModifier(
    internal val outer: SwingModifier,
    internal val inner: SwingModifier,
) : SwingModifier {
    override fun <R> foldIn(
        initial: R,
        operation: (R, SwingModifier.NodeElement<*, *>) -> R,
    ): R = inner.foldIn(outer.foldIn(initial, operation), operation)

    override fun equals(other: Any?): Boolean =
        other is CombinedSwingModifier && outer == other.outer && inner == other.inner

    override fun hashCode(): Int = outer.hashCode() + 31 * inner.hashCode()
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
    private val elementType: Class<out SwingModifier.NodeElement<T, N>>,
    private var element: SwingModifier.NodeElement<T, N>,
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
    fun canRebind(element: SwingModifier.NodeElement<*, *>): Boolean = elementType.isInstance(element)

    /**
     * Rebinds the slot to a (possibly new) [element] instance, then refreshes it against [target].
     * An element the one the slot already carries [adopts][SwingModifier.NodeElement.adopt] declares
     * the same data, so the slot keeps the one it holds and writes nothing. Anything else rebinds: a
     * fresh element instance carrying new data (or new callbacks) is pushed onto the node via
     * [SwingModifier.NodeElement.update], which keeps a node-installed listener's callbacks current
     * without reattaching.
     *
     * Only call when [canRebind] holds: the slot's node statically knows its own type, so a
     * [canRebind]-checked [element] is applied through it without an unchecked cast.
     */
    fun rebindAndRefresh(
        element: SwingModifier.NodeElement<*, *>,
        target: Component,
    ) {
        if (adopt(element)) return
        this.element = elementType.cast(element)
        refresh(target)
    }

    /**
     * Whether this slot can keep the element it holds for [incoming], the element the pass being applied
     * declares in its place - having handed the node whatever [incoming] carries live. See
     * [SwingModifier.NodeElement.adopt].
     *
     * Where it can, [incoming] becomes the element the slot holds. Nothing is written - it declares what
     * the one held did - but the element the last diff installed is released, and with it everything the
     * callbacks it carries capture.
     */
    fun adopt(incoming: SwingModifier.NodeElement<*, *>): Boolean {
        if (!element.adopt(node, incoming)) return false
        element = elementType.cast(incoming)
        return true
    }

    /** Re-narrows the target and pushes the element currently occupying this slot onto the node. */
    private fun refresh(target: Component): Unit = refreshElement(element, target, node)
}

/**
 * The diff state for one node's modifier chain: the elements applied last, and the buffers the chain
 * being applied is partitioned into.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
public class SwingModifierState internal constructor() {
    internal val records: LinkedHashMap<Any, ElementRecord<*, *>> = LinkedHashMap()
    internal val additiveRecords: ArrayList<ElementRecord<*, *>> = ArrayList()

    /**
     * The chain last declared for this node, and what the next pass compares its own declaration
     * against. Every pass replaces it with the chain it declared, whether the slots diffed that
     * declaration or adopted it, so what stands here is what the composition declared last - which is
     * what [org.jetbrains.compose.swing.node.SwingComponentNode.modifier] answers with.
     *
     * Adopting decides what reaches the component, not what is held: a chain the slots adopt writes
     * nothing, while this and each slot's own element still take the incoming declaration, releasing
     * everything the elements they replace captured.
     */
    internal var applied: SwingModifier = SwingModifier

    /**
     * The keyed (last-wins) elements of the chain being applied, by [SwingModifier.NodeElement.key]. Held on
     * the node so a pass reuses the previous pass's storage rather than allocating its own;
     * [applyModifierDiff] empties it before walking the chain, so it only ever holds elements the chain
     * currently being applied declares.
     */
    internal val incomingKeyed: LinkedHashMap<Any, SwingModifier.NodeElement<*, *>> = LinkedHashMap()

    /** The additive (subscription) elements of the chain being applied, in declaration order. */
    internal val incomingAdditive: ArrayList<SwingModifier.NodeElement<*, *>> = ArrayList()
}

/**
 * Applies [modifier] to this node, diffing against the chain applied on the previous composition:
 * new elements are applied, persisting elements re-applied, and elements that disappeared are
 * [detached][SwingModifier.Node.onDetach] (restoring the value the component had before the modifier
 * first touched that property).
 *
 * Runs after the component's own `set`s, so a modifier can override component defaults. A chain
 * declaring what the one applied last declares is skipped whole - a listener callback the pass rebuilt
 * reaches the node that reads it without counting as a change - and in a chain that did change, every
 * element that did not is skipped with it.
 *
 * A chain carrying a placement - [org.jetbrains.compose.swing.modifier.layout.layoutConstraint] or a host
 * slot - declares where the node is attached in its parent, and this is the channel through which that
 * placement reaches the node: it is written onto the node here, before the applier attaches the
 * component. A chain declaring both kinds of placement is refused here, since a parent holds a child by
 * one of the two.
 *
 * @param modifier the chain to declare on the node; [SwingModifier] itself declares nothing, which
 *   detaches every element the previous pass installed.
 */
@PublishedApi
internal fun SwingNodeUpdater<out Component>.applyModifier(modifier: SwingModifier): Unit =
    updater.set(modifier) { applyDeclaredModifier(it) }

/**
 * Diffs [modifier] onto this node unless the chain applied last declares the same thing, which is what
 * [adoptDeclaration] answers - and, for the part of a chain that is read live rather than applied, is
 * what hands the pass's own over.
 *
 * The two questions are not the same one asked twice. A chain reaching here is one the caller declared
 * anew - a callback rebuilt for this pass is enough to make it that - and what this asks is whether
 * anything the node applied has to change for it.
 *
 * A node with no modifier state yet has applied no chain, so its first declaration always diffs; a node
 * whose state was reset - released, reused, parked - is in that same position and rebuilds from scratch.
 */
private fun SwingNodeHolder<Component>.applyDeclaredModifier(modifier: SwingModifier) {
    val state = modifierState
    if (state != null && adoptDeclaration(state.applied, modifier, state.additiveRecords, 0) != DIVERGED) {
        state.applied = modifier
        return
    }
    applyModifierDiff(modifier)
}

/** What the walk answers from where two chains part ways, so no slot past that point is asked. */
private const val DIVERGED = -1

/**
 * Walks [applied] and [next] in lockstep, asking the slot behind each element of [applied] to adopt the
 * element [next] declares in its place, and answers how many additive slots were matched - or [DIVERGED]
 * as soon as the two chains differ, since past that point an element is no longer paired with its own
 * slot. The diff that follows is what pairs the rest, by key and by position, adopting as it goes.
 *
 * [matched] indexes [records], which hold the additive slots in the order the chain declares them: this
 * walk takes the same path through a chain as [SwingModifier.foldIn], so a prefix of the path it matched
 * is a prefix of those slots.
 *
 * The two walks agreeing is what this fast path is worth, not what makes it safe. A walk taking another
 * path than the fold reaches a slot holding a registration its element does not match, [DIVERGED] is
 * answered, and the diff behind it pairs everything correctly - so the chain still ends the pass on what
 * the composition declares, at the cost of the diff this path exists to skip. Nothing observable breaks,
 * which is why a disagreement would have to be found by reading rather than by a failing test.
 */
private fun adoptDeclaration(
    applied: SwingModifier,
    next: SwingModifier,
    records: ArrayList<ElementRecord<*, *>>,
    matched: Int,
): Int =
    when {
        applied is CombinedSwingModifier && next is CombinedSwingModifier -> {
            val outer = adoptDeclaration(applied.outer, next.outer, records, matched)
            if (outer == DIVERGED) DIVERGED else adoptDeclaration(applied.inner, next.inner, records, outer)
        }

        applied is SwingModifier.NodeElement<*, *> && next is SwingModifier.NodeElement<*, *> -> {
            adoptElement(applied, next, records, matched)
        }

        else -> {
            if (applied == next) matched else DIVERGED
        }
    }

/** Asks the slot [applied] occupies - [records] at [matched], where it is an additive one - for [next]. */
private fun adoptElement(
    applied: SwingModifier.NodeElement<*, *>,
    next: SwingModifier.NodeElement<*, *>,
    records: ArrayList<ElementRecord<*, *>>,
    matched: Int,
): Int {
    // A keyed element owns a slot as well, but nothing keyed carries anything read live, so equality is
    // the whole of what its slot has to be asked and the keyed records are never indexed here.
    if (!applied.additive) return if (applied == next) matched else DIVERGED
    val record = records.getOrNull(matched)
    return if (record != null && record.adopt(next)) matched + 1 else DIVERGED
}

@VisibleForTesting
internal fun SwingNodeHolder<Component>.applyModifierDiff(modifier: SwingModifier) {
    val target = component
    val state = modifierState ?: SwingModifierState().also { modifierState = it }

    // Walk the chain once, partitioning into keyed property elements (last-wins by key) and additive
    // subscription elements (each its own slot, matched by position). The state itself is the fold's
    // accumulator, so the walk carries the destination buffers instead of capturing them.
    state.incomingKeyed.clear()
    state.incomingAdditive.clear()
    modifier.foldIn(state) { chainState, element ->
        if (element.additive) {
            chainState.incomingAdditive.add(element)
        } else {
            chainState.incomingKeyed[element.key] = element
        }
        chainState
    }

    // A placement says where the node is attached rather than what its component looks like, so it is
    // taken off the partitioned chain instead of being diffed into a slot - and taking it off is what
    // keeps it away from the element diff, whose nodes it has none of. It is keyed like any other
    // property element, so the walk above has already resolved last-wins for each of the two kinds, and
    // a chain declaring one of each is refused before either is written.
    //
    // Writing it here puts it on the node before the applier reads it: an inserted node runs its update
    // changes between the applier's top-down and bottom-up passes, and the bottom-up pass is the one
    // that attaches the component. A chain declaring no placement leaves nothing to take off and resets
    // the node to none; applyConstraint gates on equality, so an unchanged constraint writes nothing.
    // The declared host region is recorded rather than filled: the applier alone installs a component
    // into a region, and it moves one whose chain declares a region other than the one it is in.
    val slot = state.incomingKeyed.removeSlot()
    val constraint = state.incomingKeyed.removeLayoutConstraint()
    checkOnePlacement(slot, constraint)
    declaredSlot = slot?.let { DeclaredSlot(it.attachment, it.regionName) }
    applyConstraint(constraint)

    diffKeyedElements(target, state.records, state.incomingKeyed)
    diffAdditiveElements(target, state.additiveRecords, state.incomingAdditive)

    // Recorded once the slots hold it, so a diff that fails partway leaves the chain it did not finish
    // applying to be diffed again rather than adopted.
    state.applied = modifier
}

/**
 * Creates a node for [element], injects the [checkedTarget] component, runs [SwingModifier.Node.onAttach],
 * then pushes the element's data with [SwingModifier.NodeElement.update] - the first-install order. Returns
 * an [ElementRecord] whose [ElementRecord.rebindAndRefresh] re-runs the element's `update` against the
 * same node.
 *
 * Typing [N] here keeps `create()`/`update()` together with no cast: the returned record holds the
 * concrete node, so a later recomposition pushes fresh data without re-narrowing the node's type.
 */
private fun <T : Component, N : SwingModifier.Node<T>> attachElement(
    element: SwingModifier.NodeElement<T, N>,
    raw: Component,
): ElementRecord<T, N> {
    val typed = checkedTarget(element, raw)
    val node = element.create()
    check(node.attachedComponent == null) {
        "A SwingModifier.Node instance may not be attached to multiple components simultaneously"
    }
    node.attachedComponent = typed
    node.onAttach()
    element.update(node)
    // element.javaClass is typed Class<out NodeElement<T, N>>: capturing it lets a later rebind
    // Class.cast-check a new element of the same type onto this node without an unchecked cast.
    return ElementRecord(node, element.javaClass, element)
}

/**
 * Re-checks the target type (the component is stable, but re-narrowing keeps the error path identical
 * to first apply) and pushes the element's latest data onto its node via [SwingModifier.NodeElement.update].
 */
private fun <T : Component, N : SwingModifier.Node<T>> refreshElement(
    element: SwingModifier.NodeElement<T, N>,
    raw: Component,
    node: N,
) {
    checkedTarget(element, raw)
    element.update(node)
}

/**
 * Narrows the node to an element's target type. A node that is not the required type is rejected with
 * a clear message naming the element ([SwingModifier.NodeElement.name]) and the required vs. actual type.
 */
private fun <T : Component> checkedTarget(
    element: SwingModifier.NodeElement<T, *>,
    raw: Component,
): T {
    val targetType = element.targetType
    if (!targetType.isInstance(raw)) {
        error(
            "Modifier element ${element.name} requires a ${targetType.name} target, " +
                "but the component is a ${raw.javaClass.name}",
        )
    }
    return targetType.cast(raw)
}

/** Diffs the keyed (last-wins) property elements: detach departed keys, then add/refresh the rest. */
private fun diffKeyedElements(
    target: Component,
    records: LinkedHashMap<Any, ElementRecord<*, *>>,
    incoming: LinkedHashMap<Any, SwingModifier.NodeElement<*, *>>,
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
    incoming: ArrayList<SwingModifier.NodeElement<*, *>>,
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
 * before the modifier touched it. Invoked by [SwingNodeHolder] on release, reuse and deactivate, so a
 * node's modifier state never carries over to content it no longer drives.
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
    state.incomingKeyed.clear()
    state.incomingAdditive.clear()
    modifierState = null
}
