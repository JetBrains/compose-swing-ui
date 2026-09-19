@file:JvmMultifileClass
@file:JvmName("SwingModifierKt")

package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.watchRestore
import org.jetbrains.compose.swing.core.watchWrite
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.modifier.layout.checkOnePlacement
import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.DeclaredSlot
import org.jetbrains.compose.swing.node.SwingCompositionOwner
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.observesLocals
import org.jetbrains.compose.swing.node.requireOwner
import org.jetbrains.compose.swing.util.fastForEach
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
 * for. See `docs/MODIFIERS.md`.
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
     * Most entries are a [NodeElement] and carry a [ComponentNode] that writes onto the component. An entry that
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
     * A long-lived node a modifier entry is backed by, mirroring `androidx.compose.ui.Modifier.Node`.
     *
     * A node is attached to its component's composition before [onAttach] runs, and detached after
     * [onDetach] returns. [coroutineScope] and the capabilities bound to this type, such as
     * [observeReads][org.jetbrains.compose.swing.node.observeReads], are available only in between.
     *
     * A chain attaches and detaches whole: while any node of the chain runs [onAttach] or [onDetach], every
     * node of the chain is attached, so a node may measure or paint its component whatever its place in the
     * chain. The chain is the component's whole modifier, with the nodes of both families; a `key` change
     * detaches and attaches again only the [ComponentNode]s.
     *
     * The library builds two families on it: [ComponentNode], which writes onto its component, and
     * [ParentLayoutNode][org.jetbrains.compose.swing.layout.ParentLayoutNode], which the parent's layout
     * manager interprets.
     */
    @Suppress("AbstractClassCanBeConcreteClass") // A node is always one of the library's families, never this base.
    public abstract class Node internal constructor() {
        private var scope: CoroutineScope? = null

        /** The node holder whose modifier this node is attached in, or `null` while it is not attached. */
        internal var holder: SwingNodeHolder<*>? = null
            private set

        /** Whether this node is attached to a composition. */
        public val isAttached: Boolean get() = holder != null

        /**
         * A scope that runs for as long as this node is attached, on the composition's own coroutine
         * context and frame clock. Created on first access and cancelled once [onDetach] returns.
         *
         * @throws IllegalStateException if this node is not attached.
         */
        public val coroutineScope: CoroutineScope
            get() {
                val context = requireOwner().coroutineContext
                return scope ?: CoroutineScope(context + Job(context[Job])).also { scope = it }
            }

        /** Runs once every node of the chain is attached. Runs again only after [onDetach]. */
        public open fun onAttach() {}

        /**
         * Runs when the entry leaves the modifier or the node is released, deactivated or reused, while every
         * node of its chain is still attached.
         */
        public open fun onDetach() {}

        /**
         * Runs while the node is attached, right before the composition reuses its component for different
         * content or deactivates it. Drop state tied to the old content; [onDetach] follows.
         */
        public open fun onReset() {}

        /**
         * Visits the nodes of the modifier this node is attached in that hold a place in it, in the order the
         * modifier declares them: every [ComponentNode] of an [additive][NodeElement.additive] element, and
         * every [ParentLayoutNode][org.jetbrains.compose.swing.layout.ParentLayoutNode]. This node is among
         * them if it is one of those. Visits nothing while this node is not attached.
         */
        public fun visitDeclaredNodes(block: (Node) -> Unit) {
            holder?.visitDeclaredNodes(block)
        }

        internal fun requireOwner(): SwingCompositionOwner =
            checkNotNull(holder?.owner) { "The modifier node is not attached" }

        /**
         * Marks the node attached in the modifier of [holder], and so to its composition, without running
         * [onAttach]; [runAttachLifecycle] follows.
         */
        internal fun attach(holder: SwingNodeHolder<*>) {
            check(!isAttached) { "A modifier node may not be attached to multiple components simultaneously" }
            // Fails for a holder no composition has attached, so an attached node always reaches its owner.
            holder.requireOwner()
            this.holder = holder
        }

        internal fun runAttachLifecycle() {
            check(isAttached) { "attach(holder) must run before runAttachLifecycle()" }
            onAttach()
        }

        internal fun runDetachLifecycle() {
            check(isAttached) { "The modifier node is detached multiple times" }
            onDetach()
        }

        internal fun reset() {
            check(isAttached) { "reset() called on a node that is not attached" }
            onReset()
        }

        /** Marks the node detached once [runDetachLifecycle] has run, cancelling [coroutineScope]. */
        internal fun detach() {
            check(isAttached) { "Cannot detach a node that is not attached" }
            holder?.owner?.snapshotObserver?.clear(this)
            scope?.cancel(ModifierNodeDetachedCancellationException())
            scope = null
            holder = null
        }
    }

    /**
     * The stateful counterpart of an [NodeElement], created once per slot and kept across recompositions.
     *
     * [onAttach] runs once, before the owning [NodeElement]'s first [update][NodeElement.update] pushes the
     * latest data onto the node's fields. So a listener installed in [onAttach] is live before the first
     * `update` lands, and every field it reads needs an initial value that stands until then. [onDetach]
     * runs when the element leaves the modifier or the node is released or reused, to restore a captured
     * original or remove an installed listener. A reused or deactivated node gets [onReset] first.
     *
     * Property nodes come apart in the reverse of the order the modifier declared them last, so a node
     * putting back what it read finds every node declared before it still standing. Subscription nodes are
     * held by position instead, and come apart after the property diff has restored what left the modifier
     * and applied what stands, so what one of them reads as it goes is what the pass leaves behind.
     *
     * Subclass this to back a custom [NodeElement]: capture the property's original in a field in
     * [onAttach], write the new value in `update`, and restore it in [onDetach]. See
     * `docs/MODIFIERS.md`.
     */
    public open class ComponentNode<T : Component> : Node() {
        /**
         * The typed target, valid from [onAttach] until [onDetach]. Reading it outside that window -
         * before the node is attached, or after it has been detached - fails.
         */
        public val component: T
            get() {
                val holder = checkNotNull(holder) { "Node is not attached" }
                // ElementRecord.mark attaches this node only to a holder whose component is a T.
                @Suppress("UNCHECKED_CAST")
                return holder.component as T
            }
    }

    /**
     * A single unit of a [SwingModifier] chain: one property write or one installed listener, targeting
     * a component of type [T] and backed by a stateful [ComponentNode] of type [N].
     *
     * Implement this to expose an arbitrary Swing property or listener the library does not ship a
     * builder for (see `docs/MODIFIERS.md`). See [targetType] for how to declare the component
     * type the element targets; the node's [ComponentNode.component] arrives already typed [T].
     *
     * The element is immutable and throwaway: every pass builds a fresh one carrying the values declared
     * then. The [ComponentNode] is the long-lived side - [create]d once per slot, kept for as long as an
     * element of this type occupies it, and owning the mutable state (the captured original, the installed
     * listener) with its setup and teardown in [ComponentNode.onAttach]/[ComponentNode.onDetach]. An element
     * unequal to the one its slot holds costs one [update] call and nothing else: the node is not recreated
     * and a listener it installed is not reattached. So a callback written inline as a lambda is the intended
     * style and needs no `remember` - push it onto the node in [update] and have the node read it when the
     * event fires.
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
    public abstract class NodeElement<T : Component, N : ComponentNode<T>> :
        Element,
        InspectableElement {
        /**
         * The component type this element targets. The node's [ComponentNode.component] arrives already typed
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
         *   [ComponentNode.onAttach], so [ComponentNode.component] is readable from here.
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
 * Mutable per-slot state held by the node holder across recompositions: one slot's node and the element
 * currently occupying it.
 *
 * The node hosts only elements of the exact class of the one it holds, as AndroidX reuses a node only for an
 * element of the same class; [canRebind] checks that, so a rebind's narrowing is a verified [Class.cast].
 */
internal abstract class NodeRecord<N : SwingModifier.Node, E : SwingModifier.Element>(
    /** The node this slot holds for as long as the slot stands. */
    val node: N,
    protected var element: E,
) {
    /**
     * Calls [record], runs the node's [SwingModifier.Node.onAttach], then pushes the element onto it: the first
     * install. The slot stands while `onAttach` runs, so [SwingModifier.Node.visitDeclaredNodes] finds the node from
     * there; [unrecord] takes it out again if `onAttach` throws, and the node itself - marked attached before this
     * ran - is detached with it: its `onDetach` runs, its `coroutineScope` cancels, and its observed reads clear,
     * so it does not stand attached with no slot holding it. A node whose `onAttach` succeeded and whose first
     * `update` then throws is left standing instead, for the next pass to hand the same node its declaration again.
     */
    open fun runAttachLifecycle(
        diagnostics: SwingCompositionDiagnostics?,
        record: () -> Unit,
        unrecord: () -> Unit,
    ) {
        record()
        // Re-throws every exception after unrecording the slot and detaching the node; catching Throwable ensures
        // errors during attach are unwound rather than leaving a half-attached node in the chain.
        try {
            node.runAttachLifecycle()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            unrecord()
            detach(diagnostics)
            throw failure
        }
        update(element)
    }

    /** [runAttachLifecycle] for a slot appended to [chain]. */
    fun runAttachLifecycle(
        diagnostics: SwingCompositionDiagnostics?,
        chain: ArrayList<NodeRecord<*, *>>,
    ): Unit = runAttachLifecycle(diagnostics, { chain += this }, { chain.removeAt(chain.lastIndex) })

    /** Runs the node's [SwingModifier.Node.onDetach]. */
    open fun runDetachLifecycle(diagnostics: SwingCompositionDiagnostics?): Unit = node.runDetachLifecycle()

    /** Marks the node detached, closing the attached window. */
    open fun markDetached(): Unit = node.detach()

    fun detach(diagnostics: SwingCompositionDiagnostics?) {
        runDetachLifecycle(diagnostics)
        markDetached()
    }

    /**
     * Whether [element] is of the class this slot's node was created for, i.e. whether a rebind can apply it
     * through the existing node. A diff hands a slot an element of a different class when a conditional modifier
     * chain changes shape; the slot cannot host it, so the caller [detach]es this record and attaches the
     * element fresh instead.
     */
    fun canRebind(element: SwingModifier.Element): Boolean = element.javaClass === this.element.javaClass

    /**
     * Rebinds the slot to [element] and writes it against [target] whatever the slot already carries.
     * Only call when [canRebind] holds.
     */
    fun rebindAndWrite(
        element: SwingModifier.Element,
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
    ) {
        this.element = this.element.javaClass.cast(element)
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
    fun adopt(incoming: SwingModifier.Element): Boolean {
        if (!canRebind(incoming) || !adopts(incoming)) return false
        element = element.javaClass.cast(incoming)
        return true
    }

    /** Whether the element this slot holds declares what [incoming] does. */
    protected abstract fun adopts(incoming: SwingModifier.Element): Boolean

    /** Pushes the element currently occupying this slot onto the node. */
    open fun refresh(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
    ): Unit = update(element)

    /** Runs [element]'s own `update` against the node. */
    protected abstract fun update(element: E)

    companion object {
        /** Creates and marks attached the node of [element], an entry of [ModifierPartition.chain]. */
        fun mark(
            element: SwingModifier.Element,
            holder: SwingNodeHolder<*>,
        ): NodeRecord<*, *> =
            when (element) {
                is SwingModifier.NodeElement<*, *> -> ElementRecord.mark(element, holder)
                else -> LayoutNodeRecord.mark(element as ParentLayoutNodeElement<*>, holder)
            }
    }
}

/**
 * The slot of a [SwingModifier.NodeElement], whose node writes onto the component: the target is injected before
 * the node attaches and checked again on every write, and the composition's diagnostics watch each write and
 * restore.
 */
internal class ElementRecord<T : Component, N : SwingModifier.ComponentNode<T>>(
    node: N,
    element: SwingModifier.NodeElement<T, N>,
) : NodeRecord<N, SwingModifier.NodeElement<T, N>>(node, element) {
    override fun runAttachLifecycle(
        diagnostics: SwingCompositionDiagnostics?,
        record: () -> Unit,
        unrecord: () -> Unit,
    ) {
        // A slot's install is one write however the node splits it: some capture an original in onAttach and
        // write the declaration in update, others write in onAttach and have nothing to add.
        diagnostics.watchWrite(node.component, node, element) {
            super.runAttachLifecycle(diagnostics, record, unrecord)
        }
    }

    /** Runs the node's [SwingModifier.Node.onDetach], while the target is still injected. */
    override fun runDetachLifecycle(diagnostics: SwingCompositionDiagnostics?): Unit =
        // A node restores its captured original through component.
        diagnostics.watchRestore(node.component, node) {
            node.runDetachLifecycle()
        }

    // Only a component element is paired with a component slot.
    override fun adopts(incoming: SwingModifier.Element): Boolean =
        element.adopt(node, incoming as SwingModifier.NodeElement<*, *>)

    /** Re-narrows the target and pushes the element currently occupying this slot onto the node. */
    override fun refresh(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
    ) {
        val element = element
        diagnostics.watchWrite(target, node, element) {
            // Re-checks the target type: the component is stable, but re-narrowing keeps the error path
            // identical to first apply.
            checkedTarget(element, target)
            update(element)
        }
    }

    /**
     * Pushes [element] onto the node. A node reading composition locals has the reads observed, so a
     * change of a local it read runs its `update` again.
     */
    override fun update(element: SwingModifier.NodeElement<T, N>) {
        if (node is CompositionLocalConsumerModifierNode) {
            node.observeReads(OnLocalReadChanged) { element.update(node) }
        } else {
            element.update(node)
        }
    }

    companion object {
        /**
         * Checks the component of [holder] with [checkedTarget], creates a node for [element] and marks it
         * attached in the modifier of [holder], without running its `onAttach`.
         *
         * Typing [N] here keeps `create()`/`update()` together with no cast: the returned record holds the
         * concrete node, so a later recomposition pushes fresh data without re-narrowing the node's type.
         */
        fun <T : Component, N : SwingModifier.ComponentNode<T>> mark(
            element: SwingModifier.NodeElement<T, N>,
            holder: SwingNodeHolder<*>,
        ): ElementRecord<T, N> {
            checkedTarget(element, holder.component)
            val node = element.create()
            node.attach(holder)
            return ElementRecord(node, element)
        }
    }
}

/** The slot of a [ParentLayoutNodeElement], whose node the parent's layout manager reads. */
internal class LayoutNodeRecord(
    node: ParentLayoutNode,
    element: ParentLayoutNodeElement<*>,
) : NodeRecord<ParentLayoutNode, ParentLayoutNodeElement<*>>(node, element) {
    override fun adopts(incoming: SwingModifier.Element): Boolean = element == incoming

    @Suppress("UNCHECKED_CAST") // The slot's node was created by an element of this element's class.
    override fun update(element: ParentLayoutNodeElement<*>) {
        (element as ParentLayoutNodeElement<ParentLayoutNode>).update(node)
    }

    companion object {
        /** Creates the node for [element] and marks it attached in the modifier of [holder], without its `onAttach`. */
        fun mark(
            element: ParentLayoutNodeElement<*>,
            holder: SwingNodeHolder<*>,
        ): LayoutNodeRecord {
            val node = element.create()
            node.attach(holder)
            return LayoutNodeRecord(node, element)
        }
    }
}

/**
 * The one callback every observed local read shares, so the observer keeps a single scope map for them. It
 * runs only while the node is attached, so the node's modifier state is still there.
 */
private val OnLocalReadChanged: (SwingModifier.ComponentNode<*>) -> Unit = { node ->
    node.holder?.modifierState?.refreshLocalConsumer(node)
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

    /**
     * The slots holding a place in the modifier, in the order the modifier declares them: the additive component
     * slots and the parent-layout slots. Each family is matched to its own elements of [ModifierPartition.chain]
     * by position among them.
     */
    internal val chain: ArrayList<NodeRecord<*, *>> = ArrayList()

    /**
     * The modifier last declared for this node, and what the next pass compares its own declaration
     * against. Every pass that finishes replaces it with the modifier it declared, whether the slots diffed that
     * declaration or adopted it, so what stands here is what the composition declared last - which is
     * what [org.jetbrains.compose.swing.node.SwingComponentNode.modifier] answers with, through [declared].
     *
     * Adopting decides what reaches the component, not what is held: a modifier the slots adopt writes
     * nothing, while this and each slot's own element still take the incoming declaration, releasing
     * everything the elements they replace captured.
     *
     * An [UnfinishedPass] from the start of a diff or a slot refresh until it returns. One left standing - the
     * write threw - equals no declaration, so the next declaration is diffed with every slot written again.
     */
    internal var applied: SwingModifier = SwingModifier

    /** [applied], or [SwingModifier] where the last write threw. */
    internal val declared: SwingModifier get() = if (applied is UnfinishedPass) SwingModifier else applied

    /**
     * Whether the nodes last handed to a [DeclaredNodesListener] were any. Every pass that finishes hands over the
     * [chain] it changed, so outside a pass that threw the chain standing is the one last handed.
     */
    internal val handedNodes: Boolean get() = (applied as? UnfinishedPass)?.handedNodes ?: chain.isNotEmpty()

    /**
     * Marks the slots as holding no declaration until [applied] is written again, and returns what [applied] held.
     * A write that throws leaves the mark standing.
     */
    internal fun startWrite(): SwingModifier {
        val held = applied
        if (held !is UnfinishedPass) applied = if (chain.isEmpty()) UnfinishedPass.NoneHanded else UnfinishedPass.Handed
        return held
    }

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
     * Every standing slot in the order the slots come apart: the property slots in the reverse of the order
     * the modifier declared them last, then the additive component slots from the end - the order
     * [applyModifierDiff] unwinds a departed slot of each kind in - then the parent-layout slots.
     */
    internal fun standingInUnwindOrder(): List<NodeRecord<*, *>> {
        val unwinding = ArrayList<NodeRecord<*, *>>(records.size + chain.size)
        forEachStandingKeyInUnwindOrder { key -> unwinding += records.getValue(key) }
        for (index in chain.indices.reversed()) {
            if (chain[index] is ElementRecord<*, *>) unwinding += chain[index]
        }
        chain.fastForEach { if (it is LayoutNodeRecord) unwinding += it }
        return unwinding
    }

    /**
     * Takes every component slot apart and drops it, in [standingInUnwindOrder]: every node runs
     * [SwingModifier.Node.onDetach], and only then is any node marked detached. The parent-layout slots stand.
     *
     * What the modifier captured is left in place. Every hold on it is taken by a slot and given up when that slot
     * detaches, so a modifier with no slots left holds no captured value, and a slot attaching afterwards
     * captures what the component stands at then.
     */
    internal fun unwindComponentNodes(diagnostics: SwingCompositionDiagnostics?): Boolean {
        var componentSlots = records.size
        chain.fastForEach { if (it is ElementRecord<*, *>) componentSlots++ }
        if (componentSlots == 0) {
            declaredKeyOrder.clear()
            return false
        }
        val unwinding = standingInUnwindOrder().filter { it is ElementRecord<*, *> }
        unwinding.fastForEach { it.runDetachLifecycle(diagnostics) }
        unwinding.fastForEach { it.markDetached() }
        records.clear()
        declaredKeyOrder.clear()
        return chain.removeAll { it is ElementRecord<*, *> }
    }

    /**
     * Puts the [chain]'s slots in the order [elements] - the [ModifierPartition.chain] the slots were diffed
     * against - declares them, each family keeping its own order, and returns whether any slot moved. Walks the chain
     * once where it stands in that order already, which is where no slot of either family entered, left or changed
     * places with one of the other.
     */
    internal fun orderChain(elements: List<SwingModifier.Element>): Boolean {
        var moved = false
        for (index in elements.indices) {
            val layout = elements[index] is ParentLayoutNodeElement<*>
            var from = index
            while ((chain[from] is LayoutNodeRecord) != layout) from++
            if (from != index) {
                chain.add(index, chain.removeAt(from))
                moved = true
            }
        }
        return moved
    }

    /** Drops every slot record, once each slot's node has detached. */
    internal fun clear() {
        records.clear()
        chain.clear()
        declaredKeyOrder.clear()
    }

    /** Hands [action] every slot whose node is a [CompositionLocalConsumerModifierNode]. */
    internal inline fun forEachLocalConsumer(action: (NodeRecord<*, *>) -> Unit) {
        for (record in records.values) {
            if (record.node is CompositionLocalConsumerModifierNode) action(record)
        }
        chain.fastForEach { record ->
            if (record.node is CompositionLocalConsumerModifierNode) action(record)
        }
    }

    /** Runs the `update` of the slot holding [node] again, against [node]'s component. */
    internal fun refreshLocalConsumer(node: SwingModifier.ComponentNode<*>) {
        val diagnostics = node.holder?.owner?.diagnostics
        val held = startWrite()
        if (!rewritePropertySlotsFrom(node.component, diagnostics) { it.node === node }) {
            chain.firstOrNull { it.node === node }?.refresh(node.component, diagnostics)
        }
        applied = held
    }

    /**
     * Runs the `update` of the first property slot [from] picks again, and then of every property slot the
     * modifier declared after it, in declared order, so a later declaration stands over the property that
     * slot writes, as [diffKeyedElements] keeps it once a slot has written. Returns whether [from] picked one.
     */
    internal fun rewritePropertySlotsFrom(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
        from: (ElementRecord<*, *>) -> Boolean,
    ): Boolean {
        var writing = false
        val rewrite = { record: ElementRecord<*, *> ->
            if (writing || from(record)) {
                writing = true
                record.refresh(target, diagnostics)
            }
        }
        declaredKeyOrder.fastForEach { key -> records[key]?.let(rewrite) }
        // A slot a pass that threw installed without recording its key attached last.
        for ((key, record) in records) {
            if (key !in declaredKeyOrder) rewrite(record)
        }
        return writing
    }
}

/**
 * What [SwingModifierState.applied] holds while the slots are being written: equal to no declaration, carrying
 * whether the nodes last handed to a [DeclaredNodesListener] were any.
 */
private class UnfinishedPass private constructor(
    val handedNodes: Boolean,
) : SwingModifier {
    override fun <R> foldIn(
        initial: R,
        operation: (R, SwingModifier.Element) -> R,
    ): R = initial

    companion object {
        val Handed = UnfinishedPass(handedNodes = true)
        val NoneHanded = UnfinishedPass(handedNodes = false)
    }
}

/**
 * Stores [map] - the whole set of [CompositionLocal][androidx.compose.runtime.CompositionLocal]s in
 * scope where this node was declared - on the node, so a capability that reads one of them does not need
 * a [SwingNode][org.jetbrains.compose.swing.node.SwingNode]/[MenuNode][org.jetbrains.compose.swing.node.MenuNode]
 * parameter of its own.
 *
 * Called before [applyModifier] in the same update block, so a node attached by that modifier reads the
 * current map. A map replacing the one a [CompositionLocalConsumerModifierNode] read holds this node for the end of
 * the batch, where [refreshLocalConsumers][org.jetbrains.compose.swing.node.refreshLocalConsumers] runs after every
 * node's update block and modifier diff, so the modifier writes last.
 */
@PublishedApi
internal fun SwingNodeUpdater<out Component>.applyCompositionLocalMap(map: CompositionLocalMap): Unit =
    updater.set(map) {
        compositionLocalMap = it
        if (observesLocals()) requireOwner().updateBatch.holdForLocalsRefresh(this)
    }

/**
 * Applies [modifier] to this node, diffing against the modifier applied on the previous composition:
 * new elements are applied, persisting elements re-applied, and elements that disappeared are
 * [detached][SwingModifier.ComponentNode.onDetach] (restoring the value the component had before the modifier
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
 * whose state was reset - released, reused, parked - is in that same position and rebuilds from scratch. A node
 * whose last diff threw partway diffs its next declaration too.
 */
internal fun SwingNodeHolder<Component>.applyDeclaredModifier(modifier: SwingModifier) {
    val state = modifierState
    if (state != null && adoptDeclaration(state.applied, modifier, state.chain, 0) != DIVERGED) {
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
 * [matched] indexes [records], the [chain][SwingModifierState.chain]: this walk takes the same path through a
 * modifier as [SwingModifier.foldIn], so a prefix of the path it matched is a prefix of its additive slots.
 * The parent-layout slots among them are passed over, and their elements compared by equality.
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
    records: ArrayList<NodeRecord<*, *>>,
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

/**
 * Asks the slot [applied] occupies - the first component slot of [records] from [matched], where it is an additive
 * one - for [next].
 */
private fun adoptElement(
    applied: SwingModifier.NodeElement<*, *>,
    next: SwingModifier.NodeElement<*, *>,
    records: ArrayList<NodeRecord<*, *>>,
    matched: Int,
): Int {
    // A keyed element owns a slot as well, but nothing keyed carries anything read live, so equality is
    // the whole of what its slot has to be asked and the keyed records are never indexed here.
    if (!applied.additive) return if (applied == next) matched else DIVERGED
    var index = matched
    while (records.getOrNull(index) is LayoutNodeRecord) index++
    val record = records.getOrNull(index)
    return if (record is ElementRecord<*, *> && record.adopt(next)) index + 1 else DIVERGED
}

@VisibleForTesting
internal fun SwingNodeHolder<Component>.applyModifierDiff(modifier: SwingModifier) {
    // A holder with no modifier state attaches its chain whole: on its first apply, and on the first after a
    // release, a reuse or a deactivation reset it.
    var attachesWholeChain = modifierState == null
    val state = modifierState ?: SwingModifierState().also { modifierState = it }
    // A holder whose last write threw has slots holding no declaration: every slot is written again, and the
    // nodes it attached are handed over.
    val adoptable = state.startWrite() !is UnfinishedPass
    val handedNodes = state.handedNodes
    var chainChanged = !adoptable
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
    val declaredKeys = incoming.keys
    checkOnePlacement(slot, parentDeclarations)
    declaration.checkParentElementsAccepted(parentDeclarations)
    declaredSlot = slot?.let { DeclaredSlot(it.parentProtocol, it.attachment, it.regionName) }

    // A modifier tied to other keys than the ones applied last has its component nodes applied from
    // scratch rather than diffed: every component slot comes apart, putting back what it captured, and the
    // chain below attaches them afresh. The layout nodes stand and are diffed. See [key] for what that is
    // worth.
    if (state.declaredKeys != declaredKeys) {
        if (state.unwindComponentNodes(diagnostics)) chainChanged = true
        attachesWholeChain = true
    }
    state.declaredKeys = declaredKeys
    // A holder with no owner has no batch to publish the declaration on; a visit mid-pass walks its stored chain.
    val batch = owner?.updateBatch
    val outerState = batch?.diffingState
    val outerDeclaration = batch?.diffingDeclaration
    batch?.diffingState = state
    batch?.diffingDeclaration = incoming.chain
    var marked: MarkedChain? = null
    try {
        // Every node attaching whole is marked before the first onAttach runs; a node entering a standing chain
        // is marked and run on its own, by the diffs, which find the nodes a whole attach ran standing on their
        // own elements. The layout nodes go first, so the declaration the parent reads is published before any
        // component node runs - and, by a pass that throws partway, with the layout nodes standing then.
        val marksNodes = attachesWholeChain && (incoming.keyed.isNotEmpty() || incoming.chain.isNotEmpty())
        marked = if (marksNodes) MarkedChain.mark(this, state, incoming) else null
        // Taken as joined where the layout diff throws, so the parent reads the layout nodes that stand.
        var layoutChange = SlotChange.JoinedOrLeft
        try {
            // Nodes a whole attach just updated hold their elements whatever the last pass left.
            val attached = marked?.runLayoutLifecycles(state, diagnostics) == true
            val diffed = diffChainSlots(this, state, incoming.chain, LayoutNodeFamily, adoptable || attached)
            layoutChange = if (attached) SlotChange.JoinedOrLeft else diffed
        } finally {
            declaration.applyComponentLayout(
                incoming.parentData,
                incoming.parentProtocol,
                incoming.parentLayoutElements,
                layoutChange != SlotChange.Unchanged,
            )
        }
        if (layoutChange == SlotChange.JoinedOrLeft) chainChanged = true
        if (applyComponentSlots(state, incoming, marked, adoptable)) chainChanged = true
        if (state.orderChain(incoming.chain)) chainChanged = true
    } finally {
        // A node this whole attach marked but whose own onAttach never ran - because an earlier node's did and
        // threw - is attached with no slot holding it, the same leak an onAttach that itself throws would leave;
        // see [MarkedChain.detachOrphaned].
        marked?.detachOrphaned(state)
        batch?.diffingState = outerState
        batch?.diffingDeclaration = outerDeclaration
    }

    state.applied = modifier
    notifyDeclaredNodes(chainChanged, handedNodes)
}

/**
 * Runs the component nodes [marked] attaches, or diffs the keyed slots of [partition] where none does, then diffs
 * the additive component slots. Returns whether a slot joined or left the chain. A slot standing from before this
 * pass adopts an equal element only where [adoptable].
 */
private fun SwingNodeHolder<*>.applyComponentSlots(
    state: SwingModifierState,
    partition: ModifierPartition,
    marked: MarkedChain?,
    adoptable: Boolean,
): Boolean {
    val diagnostics = owner?.diagnostics
    val attached =
        if (marked == null) {
            diffKeyedElements(this, state, partition, adoptable, diagnostics)
            false
        } else {
            marked.runComponentLifecycles(state, partition, diagnostics)
        }
    // A whole attach leaves no component slot standing from before this pass.
    val diffed = diffChainSlots(this, state, partition.chain, ComponentNodeFamily, adoptable || marked != null)
    return attached || diffed == SlotChange.JoinedOrLeft
}

/**
 * The nodes of a chain [mark] marked attached, whose `onAttach` has yet to run.
 *
 * A slot is recorded before its node's `onAttach` runs and dropped again if `onAttach` throws, so a pass that throws
 * partway leaves the nodes whose `onAttach` did not run unrecorded, as the diff does. The keyed nodes stand in the
 * order of [ModifierPartition.keyed].
 *
 * [mark] may itself leave a node marked attached whose `onAttach` never got a turn: one it created before an
 * element declared after it failed to be created, or one behind a family whose own `onAttach` threw before this
 * one's had a chance to run. Neither is recorded anywhere by the time the throw reaches the caller, so nothing
 * else would ever detach them - [mark] unwinds the first kind itself, and [detachOrphaned] the second.
 */
private class MarkedChain(
    private val keyed: List<ElementRecord<*, *>>,
    private val components: List<ElementRecord<*, *>>,
    private val layoutNodes: List<LayoutNodeRecord>,
) {
    /**
     * Runs each parent-layout node - its `onAttach` and its element's `update` - in turn. Returns whether any
     * joined the chain.
     */
    fun runLayoutLifecycles(
        state: SwingModifierState,
        diagnostics: SwingCompositionDiagnostics?,
    ): Boolean {
        layoutNodes.fastForEach { record -> record.runAttachLifecycle(diagnostics, state.chain) }
        return layoutNodes.isNotEmpty()
    }

    /**
     * Runs each component node in turn, keyed slots first, as the diff attaches them. Returns whether any joined the
     * chain.
     */
    fun runComponentLifecycles(
        state: SwingModifierState,
        partition: ModifierPartition,
        diagnostics: SwingCompositionDiagnostics?,
    ): Boolean {
        var index = 0
        for (key in partition.keyed.keys) {
            val record = keyed[index++]
            record.runAttachLifecycle(diagnostics, { state.records[key] = record }, { state.records.remove(key) })
        }
        state.declaredKeyOrder.clear()
        state.declaredKeyOrder.addAll(partition.keyed.keys)
        components.fastForEach { record -> record.runAttachLifecycle(diagnostics, state.chain) }
        return components.isNotEmpty()
    }

    /**
     * Detaches every node this marked that never joined [state]: one whose `onAttach` never got a turn because an
     * earlier node's threw first. A node whose own `onAttach` ran - whether it went on to join [state] or threw
     * and was already detached by [NodeRecord.runAttachLifecycle] - is untouched here; only one still attached and
     * standing in neither [SwingModifierState.chain] nor [SwingModifierState.records] is reached, and it runs no
     * `onDetach`, since none of its own lifecycle ran either.
     */
    fun detachOrphaned(state: SwingModifierState) {
        keyed.fastForEach { it.detachIfOrphaned(state) }
        components.fastForEach { it.detachIfOrphaned(state) }
        layoutNodes.fastForEach { it.detachIfOrphaned(state) }
    }

    private fun NodeRecord<*, *>.detachIfOrphaned(state: SwingModifierState) {
        if (node.isAttached && state.chain.none { it === this } && state.records.values.none { it === this }) {
            node.detach()
        }
    }

    companion object {
        /**
         * Creates and marks attached a node for every component element of [partition], and for every parent-layout
         * element where no parent-layout slot stands in [state], for a holder attaching its chain whole, without
         * running any `onAttach`. A node created here is unwound the same way if creating a later element of this
         * same call fails, so a throw partway through never hands the caller a chain holding a leaked node.
         */
        fun mark(
            holder: SwingNodeHolder<*>,
            state: SwingModifierState,
            partition: ModifierPartition,
        ): MarkedChain {
            val keyedElements = partition.keyed.values
            keyedElements.forEach { checkedTarget(it, holder.component) }
            val elements = partition.chain
            var componentCount = 0
            elements.fastForEach {
                if (it is SwingModifier.NodeElement<*, *>) {
                    checkedTarget(it, holder.component)
                    componentCount++
                }
            }
            val layoutNodeCount = elements.size - componentCount
            // A key change leaves the parent-layout slots standing, and the diff pairs them with their elements.
            val marksLayoutNodes = layoutNodeCount != 0 && state.chain.isEmpty()
            val keyed = ArrayList<ElementRecord<*, *>>(keyedElements.size)
            val components = ArrayList<ElementRecord<*, *>>(componentCount)
            val layoutNodes = ArrayList<LayoutNodeRecord>(if (marksLayoutNodes) layoutNodeCount else 0)
            // Re-throws every exception after detaching all nodes created during the mark pass, so any error
            // during node instantiation is unwound cleanly.
            try {
                keyedElements.forEach { keyed += ElementRecord.mark(it, holder) }
                if (componentCount != 0) {
                    elements.fastForEach {
                        if (it is SwingModifier.NodeElement<*, *>) components += ElementRecord.mark(it, holder)
                    }
                }
                if (marksLayoutNodes) {
                    elements.fastForEach {
                        if (it is ParentLayoutNodeElement<*>) layoutNodes += LayoutNodeRecord.mark(it, holder)
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                // Every node created above is attach()ed but has not run its onAttach, so unwinding it undoes
                // exactly that mark: no onDetach runs for a node whose onAttach never did.
                keyed.fastForEach { it.node.detach() }
                components.fastForEach { it.node.detach() }
                layoutNodes.fastForEach { it.node.detach() }
                throw failure
            }
            return MarkedChain(keyed, components, layoutNodes)
        }
    }
}

/**
 * Checks the component with [checkedTarget], creates a node for [element], attaches it in the modifier of [holder],
 * records its [ElementRecord] in [records] under [key], then runs its `onAttach` and pushes the element's data with
 * [SwingModifier.NodeElement.update] - the first-install order for a property slot entering a standing modifier.
 * The record's [ElementRecord.rebindAndWrite] re-runs the element's `update` against the same node.
 *
 * @param element the element entering the slot, which builds the node and pushes its data onto it.
 * @param holder the node holder whose component the element targets, narrowed to the element's target type
 *   before anything is written, and whose composition's diagnostics watch the write.
 * @param records the keyed slots, which hold the slot from before its `onAttach`, and drop it if `onAttach` throws,
 *   so a failing `update` leaves it to release.
 * @param key the key the slot is held under.
 */
private fun <T : Component, N : SwingModifier.ComponentNode<T>> attachElement(
    element: SwingModifier.NodeElement<T, N>,
    holder: SwingNodeHolder<*>,
    records: MutableMap<Any, ElementRecord<*, *>>,
    key: Any,
) {
    val record = ElementRecord.mark(element, holder)
    record.runAttachLifecycle(holder.owner?.diagnostics, { records[key] = record }, { records.remove(key) })
}

/**
 * Diffs the keyed (last-wins) property elements: detach departed keys, then add/refresh the rest. A slot adopts an
 * equal element only where [adoptable].
 */
private fun diffKeyedElements(
    holder: SwingNodeHolder<*>,
    state: SwingModifierState,
    partition: ModifierPartition,
    adoptable: Boolean,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val target = holder.component
    val records = state.records
    val incoming = partition.keyed
    val declaredKeyOrder = state.declaredKeyOrder

    // Detach + drop elements whose key left the modifier, last declared first: a slot restoring through an
    // object a later slot declared needs that object still standing.
    var wrote = !adoptable
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
                attachElement(element, holder, records, key)
                wrote = true
            }

            // One key can be declared through two kinds of element - a property declared with a
            // restore on one pass and without it on the next is the shape that does. The slot's node
            // was built for one of them and cannot host the other, so the slot restores and is built
            // again. The restore runs first, so the arriving element captures what the component
            // carried before this key declared anything rather than what the departing write left. The slot
            // is dropped as it comes apart, so an arriving element that is refused leaves no detached node behind.
            !record.canRebind(element) -> {
                records.remove(key)
                record.detach(diagnostics)
                attachElement(element, holder, records, key)
                wrote = true
            }

            wrote -> {
                record.rebindAndWrite(element, target, diagnostics)
            }

            !record.adopt(element) -> {
                record.rebindAndWrite(element, target, diagnostics)
                wrote = true
            }
        }
    }

    declaredKeyOrder.clear()
    declaredKeyOrder.addAll(incoming.keys)
}

/** The slots of one family of the [chain][SwingModifierState.chain], and the elements of the modifier they hold. */
private class ChainFamily(
    val elementType: Class<out SwingModifier.Element>,
    val slotType: Class<out NodeRecord<*, *>>,
)

private val ComponentNodeFamily = ChainFamily(SwingModifier.NodeElement::class.java, ElementRecord::class.java)

private val LayoutNodeFamily = ChainFamily(ParentLayoutNodeElement::class.java, LayoutNodeRecord::class.java)

/**
 * Diffs the [chain][SwingModifierState.chain]'s slots of [family] against its elements among [elements], by
 * position among them: the k-th slot of the family is handed the k-th element of it, so an element of the other
 * family entering or leaving moves no slot of this one. Slots past the family's last element detach first, from
 * the end; a persisting slot keeps its node and is refreshed via update(); a new slot is attached at the end of
 * the chain, for [SwingModifierState.orderChain] to put in place. A conditional modifier chain changing shape can
 * hand a slot an element of a different kind; the slot's node cannot host it, so the slot is swapped wholesale -
 * the new element attaches fresh and the old node then detaches, removing its listener. A slot adopts an equal
 * element only where [adoptable]. Returns the largest change it made to a slot.
 */
private fun diffChainSlots(
    holder: SwingNodeHolder<*>,
    state: SwingModifierState,
    elements: List<SwingModifier.Element>,
    family: ChainFamily,
    adoptable: Boolean,
): SlotChange {
    val target = holder.component
    val diagnostics = holder.owner?.diagnostics
    val records = state.chain
    var declared = 0
    elements.fastForEach { if (family.elementType.isInstance(it)) declared++ }
    var standing = 0
    records.fastForEach { if (family.slotType.isInstance(it)) standing++ }

    // Detach + drop the slots past the family's last element, from the end.
    var change = SlotChange.Unchanged
    var last = records.size
    while (standing > declared) {
        if (family.slotType.isInstance(records[--last])) {
            records.removeAt(last).detach(diagnostics)
            change = SlotChange.JoinedOrLeft
            standing--
        }
    }

    // Apply (add or refresh) each element. A persisting slot of the same kind refreshes via update(), keeping a
    // node-installed listener's callbacks current without reattaching.
    var index = 0
    elements.fastForEach { element ->
        if (!family.elementType.isInstance(element)) return@fastForEach
        while (index < records.size && !family.slotType.isInstance(records[index])) index++
        val record = records.getOrNull(index)
        when {
            record == null -> {
                val attaching = NodeRecord.mark(element, holder)
                attaching.runAttachLifecycle(diagnostics, records)
                change = SlotChange.JoinedOrLeft
            }

            record.canRebind(element) -> {
                if (!adoptable || !record.adopt(element)) {
                    record.rebindAndWrite(element, target, diagnostics)
                    change = maxOf(change, SlotChange.Written)
                }
            }

            else -> {
                // The replacement attaches first: an element the component is not the target of is
                // refused here, and the slot is left holding the node it has rather than a detached one
                // every later teardown would fail on. The slot takes its node back if onAttach throws.
                val replacement = NodeRecord.mark(element, holder)
                records[index] = replacement
                try {
                    replacement.runAttachLifecycle(diagnostics, {}, { records[index] = record })
                } finally {
                    if (records[index] === replacement) record.detach(diagnostics)
                }
                change = SlotChange.JoinedOrLeft
            }
        }
        index++
    }
    return change
}

/** What [diffChainSlots] did to the slots of one family, from least to most. */
private enum class SlotChange {
    /** Every slot adopted its element. */
    Unchanged,

    /** A slot was written with its element, and none joined or left the chain. */
    Written,

    /** A slot joined or left the chain. */
    JoinedOrLeft,
}

/**
 * Detaches every node of the modifier, both the component nodes and the layout nodes its parent interprets,
 * and restores every modified property to the value captured before the modifier touched it. Invoked by
 * [SwingNodeHolder] on release, reuse and deactivate, so a node's modifier state never carries over to
 * content it no longer drives.
 *
 * The whole chain comes apart in phases: on reuse and deactivation ([reset] set) every node runs
 * [SwingModifier.Node.onReset], then every node runs [SwingModifier.Node.onDetach], and only then is any node
 * marked detached.
 */
internal fun SwingNodeHolder<*>.resetModifierState(reset: Boolean = false) {
    val state = modifierState
    val diagnostics = owner?.diagnostics
    val handedNodes = state?.handedNodes == true
    val unwinding = state?.standingInUnwindOrder().orEmpty()
    if (reset) unwinding.fastForEach { it.node.reset() }
    unwinding.fastForEach { it.runDetachLifecycle(diagnostics) }
    unwinding.fastForEach { it.markDetached() }
    declaration.dropLayoutNodes()
    state?.clear()
    notifyDeclaredNodes(chainChanged = true, handedNodes)
    modifierState = null
}

/**
 * Cheap to throw: a cancellation reaching every coroutine a detached node's [SwingModifier.Node.coroutineScope]
 * ran costs no stack trace, matching AndroidX's `PlatformOptimizedCancellationException`.
 */
internal class ModifierNodeDetachedCancellationException : CancellationException("The modifier node was detached") {
    override fun fillInStackTrace(): Throwable = this
}
