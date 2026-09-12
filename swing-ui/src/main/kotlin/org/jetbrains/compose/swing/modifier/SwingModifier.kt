@file:JvmMultifileClass
@file:JvmName("SwingModifierKt")

package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Stable
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.watchRestore
import org.jetbrains.compose.swing.core.watchWrite
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.modifier.layout.checkOnePlacement
import org.jetbrains.compose.swing.node.DeclaredSlot
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.Component

/**
 * An ordered, immutable collection of [Element]s applied to a Swing component - the Swing analogue of
 * `androidx.compose.ui.Modifier`. Most are a [NodeElement], which carries the node that writes onto the
 * component; the rest declare something the node holder reads itself, such as where the component is
 * attached in its parent.
 *
 * Build a modifier by calling the builder extensions off the [companion][SwingModifier.Companion]
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
 * The modifier is immutable and safe to share, hoist, and reuse as a theme token. Building the modifier
 * inline in the composable body is the intended style and needs no `remember`: a modifier declaring what
 * the one last applied to a component declares is skipped, and each element is judged on its own, so a
 * property whose declared value has not changed is not written again, until something declared before
 * it writes. See [NodeElement] for what an element that did change costs.
 *
 * A modifier is applied *to* a node, and a node *holds* the modifier state that outlives one
 * apply pass, so `modifier` and `node` are one boundary read from two sides, not two layers - a
 * declaration belongs with the type it is an operation on.
 */
@Stable
public interface SwingModifier {
    /**
     * Accumulates a value across the modifier's elements in declaration (application) order. Rarely
     * needed directly.
     *
     * @param initial the value handed to the first element of the modifier.
     * @param operation combines the value accumulated so far with one element and yields the value the
     *   next element is handed.
     * @return the accumulated value; [initial] where the modifier has no elements.
     */
    public fun <R> foldIn(
        initial: R,
        operation: (R, Element) -> R,
    ): R

    /**
     * One entry in a modifier.
     *
     * Most entries are a [NodeElement] and carry a [Node] that writes onto the component. An entry that
     * declares something the node holder itself reads - where the component is attached, what the
     * modifier's application is tied to - is an [Element] and no more, and this library ships every one
     * of those. Extend [NodeElement] to write a modifier of your own: applying an [Element] that is
     * neither is refused, since nothing would carry it out.
     */
    public interface Element : SwingModifier {
        override fun <R> foldIn(
            initial: R,
            operation: (R, Element) -> R,
        ): R = operation(initial, this)
    }

    /**
     * An entry that describes itself to a tool showing the modifier a component carries.
     *
     * Display only: nothing here decides what is written or which slot an entry takes.
     */
    public interface InspectableElement {
        /**
         * What this entry is called. Defaults to its class name, which serves an entry declared as its own
         * class; one built by a shared builder shares that class with every other entry built the same way,
         * and names the property it writes instead.
         */
        public val name: String get() = javaClass.simpleName

        /**
         * What this entry declares, under the name each value is declared by - the argument of a
         * single-valued property under the property's own [name], and one entry per argument for an entry
         * carrying several. Empty for one that carries nothing.
         */
        public val declaredValues: Map<String, Any?> get() = emptyMap()
    }

    /**
     * Returns a modifier that applies this one and then [other]. For two non-[additive][NodeElement.additive]
     * elements sharing a [NodeElement.key], the later one wins; two [additive][NodeElement.additive] elements
     * each keep their own slot and both stay installed.
     *
     * @return the two applied in order; this modifier itself where [other] is the empty modifier.
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
     * the modifier or the node is released/reused, to restore a captured original or remove an installed
     * listener.
     *
     * Subclass this to back a custom [NodeElement]: capture the property's original in a field in
     * [onAttach], write the new value in `update`, and restore it in [onDetach]. See
     * `docs/CUSTOM-COMPONENTS.md`.
     */
    public open class Node<T : Component> {
        internal var attachedComponent: T? = null

        /**
         * The state of the modifier this node's slot belongs to, injected with [attachedComponent] and
         * cleared with it. A node reaches what its whole modifier shares through this - what each property
         * the modifier writes stood at before it did, and nothing else yet.
         */
        internal var modifierState: SwingModifierState? = null

        /**
         * The typed target, valid from [onAttach] until [onDetach]. Reading it outside that window -
         * before the node is attached, or after it has been detached - fails.
         */
        public val component: T
            get() = checkNotNull(attachedComponent) { "Node is not attached" }

        /** Runs once, after the component is injected, to install listeners or capture originals. */
        public open fun onAttach() {}

        /**
         * Runs once, when the element leaves the modifier or the node is released/reused.
         *
         * Property nodes come apart in the reverse of the order the modifier declared them last, so a node
         * putting back what it read finds every node declared before it still standing. Subscription
         * nodes are held by position instead, and come apart after the property diff has restored what
         * left the modifier and applied what stands, so what one of them reads as it goes is what the
         * pass leaves behind.
         */
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
     * incoming element equal to the one it holds, unless it is a property element and a property
     * declared before it has already written on this pass - see [update] for that second occasion.
     * Compare a value structurally - a `data class` says that in one word - and compare anything the
     * node *registers* (a listener, a callback, a binding, a slot attachment) with
     * `===`, since such a field may carry an `equals` of its own under which two instances the node must
     * tell apart compare equal, leaving the node holding the one the composition replaced. An element
     * that carries nothing, and one whose write has to be redone whatever the declaration says, are
     * equal only to themselves - `this === other`. A freshly built instance is then unequal to the one
     * the slot holds and every pass applies it; an element declared as an `object` hands the slot the
     * same instance each pass, so it is applied once.
     */
    public abstract class NodeElement<T : Component, N : Node<T>> :
        Element,
        InspectableElement {
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
         * What detaching this element puts back, [RestorePolicy.EverythingWritten] by default. See
         * [RestorePolicy] for what each undertakes.
         */
        public open val restores: RestorePolicy get() = RestorePolicy.EverythingWritten

        /**
         * [key] is what tells one slot from another, so two elements sharing a name still occupy their own
         * slots and neither replaces the other.
         */
        override val name: String get() = javaClass.simpleName

        /**
         * The properties this element goes on holding while it stands: the one [name] names, and every
         * other its own write lands on. Defaults to [name] alone.
         *
         * A property named here is held whether or not a write of this element's moves it: a coarse
         * declaration re-writing the value already standing holds it the same as one that changes it.
         * That is what tells a watching check the properties no read around the write can reveal.
         */
        public open val heldProperties: Set<String> get() = setOf(name)

        /**
         * What this element declares, under the name each value is declared by. Read on demand and never
         * during an apply, so an element assembles it when asked rather than holding it.
         */
        override val declaredValues: Map<String, Any?> get() = emptyMap()

        /** Creates the stateful node. Called once per slot, when the element first enters the modifier. */
        public abstract fun create(): N

        /**
         * Pushes this element's latest data onto [node]. Called on add, on a modifier change that hands
         * this slot an element unequal to the one it holds, and - for a property element - on a pass
         * where a property declared before this one has already written.
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
         * hoisted modifier reaching two components - carries nothing either of them can reach the other by.
         */
        internal open fun adopt(
            node: N,
            next: NodeElement<*, *>,
        ): Boolean = this == next

        final override fun <R> foldIn(
            initial: R,
            operation: (R, Element) -> R,
        ): R = operation(initial, this)
    }

    /** The empty modifier and the entry point for building modifiers. */
    public companion object : SwingModifier {
        override fun <R> foldIn(
            initial: R,
            operation: (R, Element) -> R,
        ): R = initial

        override infix fun then(other: SwingModifier): SwingModifier = other
    }
}

/**
 * Internal cons-cell joining two modifiers.
 *
 * Two cells are equal when both halves are, so a modifier rebuilt from equal parts equals the modifier
 * built on the previous composition and the whole apply is skipped.
 */
internal class CombinedSwingModifier(
    internal val outer: SwingModifier,
    internal val inner: SwingModifier,
) : SwingModifier {
    override fun <R> foldIn(
        initial: R,
        operation: (R, SwingModifier.Element) -> R,
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
    fun detach(diagnostics: SwingCompositionDiagnostics?) {
        // onDetach runs while the target is still injected: a node restores its captured original
        // through component. The target is cleared afterwards, closing the attached window.
        val component = node.component
        diagnostics.watchRestore(component, node) { node.onDetach() }
        node.attachedComponent = null
        node.modifierState = null
    }

    /**
     * Whether [element] is of the kind this slot's node was created for, i.e. whether
     * [rebindAndRefresh] can apply it through the existing node. A diff hands a slot an element of a
     * different kind when a conditional modifier chain changes shape; the slot cannot host it, so the caller
     * [detach]es this record and attaches the element fresh instead.
     */
    fun canRebind(element: SwingModifier.NodeElement<*, *>): Boolean = elementType.isInstance(element)

    /**
     * Rebinds the slot to a (possibly new) [element] instance and writes it against [target], unless
     * the one the slot already carries [adopts][SwingModifier.NodeElement.adopt] it - an element
     * declaring the same data, so the slot keeps what it holds and writes nothing. Anything else
     * rebinds: a fresh element carrying new data or new callbacks is pushed onto the node via
     * [SwingModifier.NodeElement.update], which keeps a node-installed listener's callbacks current
     * without reattaching.
     *
     * Only call when [canRebind] holds: the slot's node statically knows its own type, so a
     * [canRebind]-checked [element] is applied through it without an unchecked cast.
     *
     * @return whether the declaration was written.
     */
    fun rebindAndRefresh(
        element: SwingModifier.NodeElement<*, *>,
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
    ): Boolean {
        if (adopt(element)) return false
        rebindAndWrite(element, target, diagnostics)
        return true
    }

    /**
     * Rebinds the slot to [element] and writes it against [target] whatever the slot already carries.
     * Only call when [canRebind] holds.
     */
    fun rebindAndWrite(
        element: SwingModifier.NodeElement<*, *>,
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
    ) {
        this.element = elementType.cast(element)
        refresh(target, diagnostics)
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
    private fun refresh(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
    ) {
        val element = element
        diagnostics.watchWrite(target, node, element) { refreshElement(element, target, node) }
    }
}

/**
 * The diff state for one node's modifier: the elements applied last, and the buffers the modifier
 * being applied is partitioned into.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
public class SwingModifierState internal constructor() {
    internal val records: LinkedHashMap<Any, ElementRecord<*, *>> = LinkedHashMap()
    internal val additiveRecords: ArrayList<ElementRecord<*, *>> = ArrayList()

    /**
     * The modifier last declared for this node, and what the next pass compares its own declaration
     * against. Every pass replaces it with the modifier it declared, whether the slots diffed that
     * declaration or adopted it, so what stands here is what the composition declared last - which is
     * what [org.jetbrains.compose.swing.node.SwingComponentNode.modifier] answers with.
     *
     * Adopting decides what reaches the component, not what is held: a modifier the slots adopt writes
     * nothing, while this and each slot's own element still take the incoming declaration, releasing
     * everything the elements they replace captured.
     */
    internal var applied: SwingModifier = SwingModifier

    private var propertyCaptures: PropertyCaptures? = null

    /**
     * What each property this modifier writes stood at before it did; see [PropertyCaptures]. Built by the
     * first node that asks, so a modifier declaring no property holds none.
     */
    internal fun captures(): PropertyCaptures = propertyCaptures ?: PropertyCaptures().also { propertyCaptures = it }

    /**
     * The keys the modifier applied last tied its application to, `null` where it declared none - which
     * is what tells a modifier declaring no key apart from one declaring `null`.
     *
     * The next pass has to compare its own keys against these, and the modifier itself is what carries
     * them, so this is the list [applied] declares. It is written before the diff and [applied] after
     * it: a teardown that throws partway leaves the keys that reached it standing, and the pass that
     * follows tears down again rather than diffing onto slots the last one abandoned. See
     * [org.jetbrains.compose.swing.modifier.key].
     */
    internal var declaredKeys: List<Any?>? = null

    /**
     * The keys of [records]'s slots in the order the modifier applied last declared them, which is not
     * the order [records] holds them in. [diffKeyedElements] reads it to see which slots the modifier
     * being applied has moved.
     */
    internal val declaredKeyOrder: ArrayList<Any> = ArrayList()

    /**
     * Hands [action] the key of every standing property slot in the reverse of the order the modifier
     * declared them last, which is the order the slots are taken apart in.
     *
     * That is not the reverse of the order [records] holds the slots in: a slot keeps the place it
     * attached at, so one that left the modifier and was declared again stands where it re-attached rather
     * than where the modifier declares it. A key [declaredKeyOrder] does not reach is one a pass that threw
     * installed without recording it; it is the most recently attached, so it comes apart first.
     *
     * [action] may drop the slot it is handed from [records]. It must leave [declaredKeyOrder] alone.
     */
    internal inline fun forEachStandingKeyInUnwindOrder(action: (Any) -> Unit) {
        var declared = 0
        for (key in declaredKeyOrder) {
            if (key in records) declared++
        }
        if (declared < records.size) {
            // records answers no backwards walk, so these keys are gathered to be handed over in reverse.
            val undeclared = ArrayList<Any>(records.size - declared)
            for (key in records.keys) {
                if (key !in declaredKeyOrder) undeclared.add(key)
            }
            for (index in undeclared.indices.reversed()) action(undeclared[index])
        }
        for (index in declaredKeyOrder.indices.reversed()) {
            val key = declaredKeyOrder[index]
            if (key in records) action(key)
        }
    }

    /**
     * The first slot the modifier being applied declares in another place among the slots that stand than
     * the modifier applied last declared it in, or `null` where every one of them stands where it stood.
     *
     * Two slots that swap places settle their overlap the other way round while both elements stay
     * equal, so the slots from the first moved one on are written again rather than adopted. A slot
     * [declaredKeyOrder] does not reach is one attached by a pass that threw before recording it; where
     * it stood is unknown, so it counts as moved.
     *
     * Both orders are read past the slots that do not stand: the incoming keyed elements name the slots yet to
     * attach, and [declaredKeyOrder] the ones that have left.
     */
    internal fun firstMovedKey(incoming: ModifierPartition): Any? {
        var stoodIndex = 0
        for (key in incoming.keyed.keys) {
            if (key !in records) continue
            while (stoodIndex < declaredKeyOrder.size && declaredKeyOrder[stoodIndex] !in records) stoodIndex++
            val stoodKey = declaredKeyOrder.getOrNull(stoodIndex)
            stoodIndex++
            if (key != stoodKey) return key
        }
        return null
    }

    /**
     * Takes every standing slot apart and drops it: the property slots in the reverse of the order the
     * modifier declared them last, then the subscription slots from the end - the order [applyModifierDiff]
     * unwinds a departed slot of each kind in.
     *
     * What the modifier captured is left in place. Every hold on it is taken by a slot and given up when that slot
     * detaches, so a modifier with no slots left holds no captured value, and a slot attaching afterwards
     * captures what the component stands at then.
     */
    internal fun unwind(diagnostics: SwingCompositionDiagnostics?) {
        forEachStandingKeyInUnwindOrder { key -> records.getValue(key).detach(diagnostics) }
        for (index in additiveRecords.indices.reversed()) {
            additiveRecords[index].detach(diagnostics)
        }
        records.clear()
        additiveRecords.clear()
        declaredKeyOrder.clear()
    }
}

/**
 * Applies [modifier] to this node, diffing against the modifier applied on the previous composition:
 * new elements are applied, persisting elements re-applied, and elements that disappeared are
 * [detached][SwingModifier.Node.onDetach] (restoring the value the component had before the modifier
 * first touched that property).
 *
 * Runs after the component's own `set`s, so a modifier can override component defaults. A modifier
 * declaring what the one applied last declares is skipped whole - a listener callback the pass rebuilt
 * reaches the node that reads it without counting as a change - and in a modifier that did change, every
 * element ahead of the first write is skipped with it.
 *
 * A modifier carrying a placement - [org.jetbrains.compose.swing.modifier.layout.layoutConstraint] or a host
 * slot - declares where the node is attached in its parent, and this is the channel through which that
 * placement reaches the node: it is written onto the node here, before the applier attaches the
 * component. A modifier declaring both kinds of placement is refused here, since a parent holds a child by
 * one of the two.
 *
 * @param modifier the modifier to declare on the node; [SwingModifier] itself declares nothing, which
 *   detaches every element the previous pass installed.
 */
@PublishedApi
internal fun SwingNodeUpdater<out Component>.applyModifier(modifier: SwingModifier): Unit =
    updater.set(modifier) { applyDeclaredModifier(it) }

/**
 * Diffs [modifier] onto this node unless the modifier applied last declares the same thing, which is what
 * [adoptDeclaration] answers - and, for the part of a modifier that is read live rather than applied, is
 * what hands the pass's own over.
 *
 * The two questions are not the same one asked twice. A modifier reaching here is one the caller declared
 * anew - a callback rebuilt for this pass is enough to make it that - and what this asks is whether
 * anything the node applied has to change for it.
 *
 * A node with no modifier state yet has applied no modifier, so its first declaration always diffs; a node
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

/** What the walk answers from where two modifiers part ways, so no slot past that point is asked. */
private const val DIVERGED = -1

/**
 * Walks [applied] and [next] in lockstep, asking the slot behind each element of [applied] to adopt the
 * element [next] declares in its place, and answers how many additive slots were matched - or [DIVERGED]
 * as soon as the two modifiers differ, since past that point an element is no longer paired with its own
 * slot. The diff that follows is what pairs the rest, by key and by position, adopting as it goes.
 *
 * [matched] indexes [records], which hold the additive slots in the order the modifier declares them: this
 * walk takes the same path through a modifier as [SwingModifier.foldIn], so a prefix of the path it matched
 * is a prefix of those slots.
 *
 * The two walks agreeing is what this fast path is worth, not what makes it safe. A walk taking another
 * path than the fold reaches a slot holding a registration its element does not match, [DIVERGED] is
 * answered, and the diff behind it pairs everything correctly - so the modifier still ends the pass on what
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
    // Read off the owner rather than held: the node is attached to its composition before its update
    // block runs, so the owner answers for every write this pass makes.
    val diagnostics = owner?.diagnostics

    // Walk the modifier once, routing each entry to whatever reads it. The partition is the fold's
    // accumulator, so the walk carries its destinations rather than capturing them.
    val incoming = ModifierPartition()
    modifier.foldIn(incoming) { partition, element ->
        partition.take(element)
        partition
    }

    // A placement says where the node is attached rather than what its component looks like, so the walk
    // routes it here rather than into a slot, which is what keeps it away from the element diff it has no
    // node for. The walk has resolved parent declarations, folded their parent data, and retained the
    // non-data declarations a measuring parent interprets. A modifier declaring a slot with any parent
    // declaration is refused here.
    //
    // Writing it here puts it on the node before the applier reads it: an inserted node runs its update
    // changes between the applier's top-down and bottom-up passes, and the bottom-up pass is the one
    // that attaches the component. A modifier declaring no placement leaves nothing to take off and resets
    // the node to none; applyComponentLayout gates on equality, so an unchanged declaration writes nothing.
    // The declared host region is recorded rather than filled: the applier alone installs a component
    // into a region, and it moves one whose modifier declares a region other than the one it is in.
    val slot = incoming.slot
    val parentDeclarations = incoming.parentDeclarations
    val parentData = incoming.parentData
    val parentProtocol = incoming.parentProtocol
    val parentLayoutElements = incoming.parentLayoutElements
    val declaredKeys = incoming.keys
    checkOnePlacement(slot, parentDeclarations.filterIsInstance<ParentLayoutElement>())
    declaration.checkParentElementsAccepted(parentDeclarations)
    declaredSlot = slot?.let { DeclaredSlot(it.parentProtocol, it.attachment, it.regionName) }
    declaration.applyComponentLayout(parentData, parentProtocol, parentLayoutElements)

    // A modifier tied to other keys than the ones applied last is applied from scratch rather than
    // diffed: every standing slot comes apart, putting back what it captured, and the diff below finds
    // no slot to keep and attaches the whole modifier afresh. See [key] for what that is worth.
    if (state.declaredKeys != declaredKeys) state.unwind(diagnostics)
    state.declaredKeys = declaredKeys

    diffKeyedElements(target, state, incoming, diagnostics)
    diffAdditiveElements(target, state, incoming, diagnostics)

    // Recorded once the slots hold it, so a diff that fails partway leaves the modifier it did not finish
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
    state: SwingModifierState,
    diagnostics: SwingCompositionDiagnostics?,
): ElementRecord<T, N> {
    val typed = checkedTarget(element, raw)
    val node = element.create()
    check(node.attachedComponent == null) {
        "A SwingModifier.Node instance may not be attached to multiple components simultaneously"
    }
    node.attachedComponent = typed
    node.modifierState = state
    // A slot's install is one write however the node splits it: some capture an original in onAttach and
    // write the declaration in update, others write in onAttach and have nothing to add.
    diagnostics.watchWrite(typed, node, element) {
        node.onAttach()
        element.update(node)
    }
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
    state: SwingModifierState,
    partition: ModifierPartition,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val records = state.records
    val incoming = partition.keyed
    val declaredKeyOrder = state.declaredKeyOrder

    // Detach + drop elements whose key left the modifier, last declared first: a slot restoring through an
    // object a later slot declared needs that object still standing.
    var wrote = false
    state.forEachStandingKeyInUnwindOrder { key ->
        if (key !in incoming) {
            records.remove(key)?.detach(diagnostics)
            wrote = true
        }
    }

    // Apply (add or refresh) the current modifier. A persisting slot keeps its node and refreshes it via
    // update(), which keeps a node-installed listener's callbacks current without reattaching.
    //
    // A slot writes what its own declaration says, and a coarser slot's write covers a finer one's
    // property - a whole geometry over one axis of it. The modifier settles that by order: the later
    // declaration wins. So once anything has written on this pass, every slot after it writes again
    // rather than being adopted, or the earlier write would stand over a later declaration that had
    // nothing to say for itself. A slot that left counts as a write, since its restore put back what
    // the component carried before it.
    val moved = state.firstMovedKey(partition)

    for ((key, element) in incoming) {
        if (key == moved) wrote = true
        val record = records[key]
        when {
            record == null -> {
                records[key] = attachElement(element, target, state, diagnostics)
                wrote = true
            }

            // One key can be declared through two kinds of element - a property declared with a
            // restore on one pass and without it on the next is the shape that does. The slot's node
            // was built for one of them and cannot host the other, so the slot restores and is built
            // again. The restore runs first, so the arriving element captures what the component
            // carried before this key declared anything rather than what the departing write left.
            !record.canRebind(element) -> {
                record.detach(diagnostics)
                records[key] = attachElement(element, target, state, diagnostics)
                wrote = true
            }

            wrote -> {
                record.rebindAndWrite(element, target, diagnostics)
            }

            else -> {
                wrote = record.rebindAndRefresh(element, target, diagnostics)
            }
        }
    }

    declaredKeyOrder.clear()
    declaredKeyOrder.addAll(incoming.keys)
}

/**
 * Diffs the additive (subscription) elements by position: a position present last time but gone now is
 * detached and removed; a persisting position keeps its node and is refreshed via update(); a new
 * trailing position is created and attached. A conditional modifier chain changing shape can hand a persisting
 * position an element of a different kind; the slot's node cannot host it, so the slot is swapped
 * wholesale - the new element attaches fresh and the old node then detaches, removing its listener.
 */
private fun diffAdditiveElements(
    target: Component,
    state: SwingModifierState,
    partition: ModifierPartition,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val records = state.additiveRecords
    val incoming = partition.additive

    // Detach + drop trailing positions that left the modifier, from the end.
    while (records.size > incoming.size) {
        records.removeAt(records.size - 1).detach(diagnostics)
    }

    // Apply (add or refresh) each position. A persisting position of the same kind refreshes via
    // update(), keeping a node-installed listener's callbacks current without reattaching.
    for (index in incoming.indices) {
        val element = incoming[index]
        val record = records.getOrNull(index)
        when {
            record == null -> {
                records.add(attachElement(element, target, state, diagnostics))
            }

            record.canRebind(element) -> {
                record.rebindAndRefresh(element, target, diagnostics)
            }

            else -> {
                // The replacement attaches first: an element the component is not the target of is
                // refused here, and the slot is left holding the node it has rather than a detached one
                // every later teardown would fail on.
                records[index] = attachElement(element, target, state, diagnostics)
                record.detach(diagnostics)
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
    state.unwind(owner?.diagnostics)
    modifierState = null
}
