/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.CombinedModifier in AndroidX's ui; see this module's
 * META-INF/NOTICE for the synced version. CombinedSwingModifier's fold and equality contract,
 * and the Modifier.Node attach/detach/reset lifecycle checks, are upstream's.
 */

@file:JvmMultifileClass
@file:JvmName("SwingModifierKt")

package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.core.runEveryStep
import org.jetbrains.compose.swing.core.watchRestore
import org.jetbrains.compose.swing.core.watchWrite
import org.jetbrains.compose.swing.defaults.LocalComponentDefaults
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.requireInheritableIsNotAdditive
import org.jetbrains.compose.swing.modifier.layout.checkOnePlacement
import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.DeclaredSlot
import org.jetbrains.compose.swing.node.SwingCompositionOwner
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.checkParentElementsAccepted
import org.jetbrains.compose.swing.node.finishStyleReconciliation
import org.jetbrains.compose.swing.node.forEachStyle
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.observesLocals
import org.jetbrains.compose.swing.node.parentReads
import org.jetbrains.compose.swing.node.requireOwner
import org.jetbrains.compose.swing.node.runReportingUncaught
import org.jetbrains.compose.swing.style.StyleNode
import org.jetbrains.compose.swing.style.UnstyledComponent
import org.jetbrains.compose.swing.style.rewriteSlotsFrom
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIn
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Component

/**
 * An ordered, immutable collection of [Element]s applied to a Swing component - the Swing analogue of
 * `androidx.compose.ui.Modifier`. Most are a [NodeElement], which carries the node that writes onto the
 * component; the rest declare something the node holder reads itself, such as where the component is
 * attached in its parent.
 *
 * Call builder extensions on the [companion][SwingModifier.Companion] and pass the result to a
 * component's `modifier` parameter. In a `Panel(PanelLayout.Border())`,
 * `SwingModifier.north().onFocus(onGained = showHint)` places the child at the top and shows a hint
 * when it gains focus. The empty modifier ([SwingModifier] itself) is the parameter default.
 *
 * Implement [NodeElement] to wrap any Swing property or listener the library does not ship a builder
 * for. See `docs/MODIFIERS.md`.
 *
 * An [NodeElement] declares the component type it targets via [NodeElement.targetType]. A modifier targeting
 * a type the node is not (e.g. `contentAreaFilled`, an `AbstractButton` property, on a bare `java.awt.Component`)
 * fails with a clear error naming the element and the required vs. actual type.
 *
 * Use `if (showHints) it.onHover(onEnter = showHint) else it` to attach a hover listener only
 * while hints are enabled. A property element removed from the modifier restores its captured
 * value by default.
 *
 * The modifier is immutable; build it in place in the composable body. It needs no `remember`: a modifier
 * declaring what the one last applied to a component declares is skipped, and each element is judged on
 * its own, so a property whose declared value has not changed is not written again, until something
 * declared before it writes. See [NodeElement] for what an element that did change costs.
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
         * Whether an element update or composition-local refresh automatically invalidates the consumer
         * of this node's state.
         * A parent-layout node invalidates its measuring parent; a component node invalidates its component's
         * [DeclaredNodesListener]. `true` by default.
         *
         * A node returning `false` requests the invalidation its update or local refresh needs itself, such as
         * repainting its component. Structural changes still notify consumers regardless of this value.
         */
        public open val shouldAutoInvalidate: Boolean get() = true

        /**
         * Whether a composition-local map replacing the one this node's component was declared under refreshes this
         * node; see [CompositionLocalConsumerModifierNode].
         */
        internal open val refreshesOnLocalMap: Boolean get() = this is CompositionLocalConsumerModifierNode

        /** Whether this node or a record it holds is refreshed by such a map. */
        internal open val holdsLocalConsumers: Boolean get() = refreshesOnLocalMap

        /** Whether this node holds attached layout nodes, which detach with it and which a listener was handed. */
        internal open val holdsLayoutNodes: Boolean get() = false

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

        /**
         * Runs once every node of the chain is attached. Runs again only after [onDetach].
         * A component that moves into another composition detaches its nodes and attaches them again there; a move
         * within one composition runs neither.
         *
         * This callback can run before the current composition changes finish applying. Read composition locals in
         * the element's `update` or layout callbacks to follow those changes; see
         * [CompositionLocalConsumerModifierNode].
         *
         * A throw propagates to the caller applying the modifier and fails the pass; the node is detached first.
         * A throw from [onDetach] during that detach is suppressed on it, not reported.
         */
        public open fun onAttach() {}

        /**
         * Runs when the entry leaves the modifier or the node is released, deactivated or reused, while every
         * node of its chain is still attached.
         * It also runs when the component moves into another composition.
         *
         * A throw is reported to the thread's uncaught-exception handler and the node detaches, as if this had
         * returned.
         */
        public open fun onDetach() {}

        /**
         * Runs while the node is attached, right before the composition reuses its component for different
         * content or deactivates it. Drop state tied to the old content; [onDetach] follows.
         *
         * A throw is reported to the thread's uncaught-exception handler, as if this had returned.
         */
        public open fun onReset() {}

        /**
         * Visits the nodes of the modifier this node is attached in that hold a place in it, in the order the
         * modifier declares them: every [ComponentNode] of an [additive][NodeElement.additive] element, and
         * every [ParentLayoutNode][org.jetbrains.compose.swing.layout.ParentLayoutNode], including the ones the
         * component's style declares, which come last. This node is among them if it is one of those. Visits
         * nothing while this node is not attached.
         *
         * @param block called with each node, in declaration order.
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

        /**
         * Runs [onDetach], reporting a throw to the uncaught-exception handler unless
         * [reportCallbackFailure] is false.
         */
        internal fun runDetachLifecycle(reportCallbackFailure: Boolean = true) {
            check(isAttached) { "The modifier node is detached multiple times" }
            if (reportCallbackFailure) runReportingUncaught { onDetach() } else onDetach()
        }

        internal fun reset() {
            check(isAttached) { "reset() called on a node that is not attached" }
            runReportingUncaught { onReset() }
        }

        /** Marks the node detached once [runDetachLifecycle] has run, cancelling [coroutineScope]. */
        internal fun detach() {
            check(isAttached) { "Cannot detach a node that is not attached" }
            try {
                scope?.cancel(ModifierNodeDetachedCancellationException())
            } finally {
                holder?.owner?.let { owner ->
                    owner.cancelAfterValidation(this)
                    owner.snapshotObserver.clear(this)
                }
                scope = null
                holder = null
            }
        }
    }

    /**
     * The stateful counterpart of an [NodeElement], created once per slot and kept across recompositions.
     *
     * [onAttach] runs each time the node attaches, and the owning [NodeElement]'s [update][NodeElement.update]
     * follows it to push the latest data onto the node's fields. So a listener installed in [onAttach] is installed
     * before that `update` lands, and every field it reads needs an initial value to read until then.
     * [onDetach] runs when the element leaves the modifier, the node is released, deactivated or reused, or its
     * component moves into another composition, to restore a captured original or remove an installed listener.
     * A reused or deactivated node gets [onReset] first.
     *
     * Property nodes come apart in the reverse of the order the modifier declared them last, so a node
     * putting back what it read finds every node declared before it still attached. Subscription nodes are
     * held by position instead, and come apart after the property diff has restored what left the modifier
     * and applied what remains installed, so what one of them reads as it goes is what the pass leaves behind.
     *
     * Subclass this to back a custom [NodeElement]: capture the property's original in a field in
     * [onAttach], write the new value in `update`, and restore it in [onDetach]. See
     * `docs/MODIFIERS.md`.
     */
    public open class ComponentNode<T : Component> : Node() {
        /** Component nodes write their own component directly and opt into listener handoff where needed. */
        public override val shouldAutoInvalidate: Boolean get() = false

        /**
         * The diagnostics a record of this node runs under, given those the composition has. A node that owns records,
         * layout records included, returns none: each property record is watched on its own, and the node attaches and
         * detaches its layout records itself, so its record runs without the diagnostics the chain passes.
         */
        internal open fun diagnosticsFor(given: SwingCompositionDiagnostics?): SwingCompositionDiagnostics? = given

        /**
         * The typed target, valid from [onAttach] until [onDetach]. Reading it outside that window -
         * before the node is attached, or after it has been detached - fails.
         */
        public val component: T
            get() {
                val holder = checkNotNull(holder) { "Node is not attached" }
                // ComponentNodeRecord.mark attaches this node only to a holder whose component is a T.
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
     * A **property** element (the default) writes a component property. A **subscription** element
     * registers a listener, as `onFocus` does. See [additive] and [key] for how each kind is
     * matched across recompositions.
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
         * Whether this element is permitted to cascade as an inherited component default.
         *
         * `false` by default. Only non-additive property elements intended to apply across a heterogeneous
         * subtree opt in to `true`. Behavior, listeners, focus management and button-group membership remain
         * `false`. A layout declaration cascades through
         * [ParentLayoutElement.inheritable][org.jetbrains.compose.swing.layout.ParentLayoutElement.inheritable].
         */
        @Deprecated(
            "Provide a nested style: style = { provideNestedStyle(AllComponents) { ... } }. " +
                "For a parent-layout declaration on each child, wrap the children in VirtualSwingNode(modifier).",
        )
        public open val inheritable: Boolean get() = false

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
         * The properties this element goes on holding while it is installed: the one [name] names, and every
         * other its own write lands on. Defaults to [name] alone.
         *
         * A property named here is held whether or not a write of this element's moves it: a coarse
         * declaration re-writing the value already installed holds it the same as one that changes it.
         * That is what tells a watching check the properties no read around the write can reveal.
         */
        public open val heldProperties: Set<String> get() = setOf(name)

        /**
         * What this element declares, under the name each value is declared by. Read on demand and never
         * during an apply, so an element assembles it when asked rather than holding it.
         */
        override val declaredValues: Map<String, Any?> get() = emptyMap()

        /** Whether this declaration can write any of the named properties. */
        internal open fun affectsProperties(names: Set<String>): Boolean = heldProperties.any { it in names }

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

        /**
         * [update]s [node], where [mode] determines whether earlier declarations require rewriting,
         * and returns whether the write automatically invalidates its consumer. The default returns
         * [node]'s [shouldAutoInvalidate][SwingModifier.Node.shouldAutoInvalidate] value. An element whose node
         * owns records writes each of them again instead of reusing equal elements under [WriteMode.RewriteEqual].
         *
         * @param node the node [create] returned for this element, already attached.
         * @param mode whether equal declarations may be reused or must be written again.
         */
        internal open fun update(
            node: N,
            mode: WriteMode,
        ): Boolean {
            update(node)
            return node.shouldAutoInvalidate
        }

        /**
         * Validates [previous], the element this one replaces in the same partition under its key, or `null` where
         * none precedes it. Throws where the two cannot share a component. Accepts any predecessor by default.
         *
         * @param previous the element this one replaces, or `null` where none precedes it.
         */
        internal open fun checkPreviousDeclaration(previous: SwingModifier.Element?) = Unit

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
         * What is written belongs to [node] and to no other, so an element handed to two slots carries
         * nothing either of them can reach the other by.
         */
        internal open fun reuse(
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
internal open class CombinedSwingModifier(
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

/** Retains a modifier slot's node and declaration until permanent removal. */
internal open class RetainedNodeRecord<N : SwingModifier.Node, E : SwingModifier.Element>(
    /** The retained node, cleared after permanent removal. */
    private var retainedNode: N?,
    /** The retained declaration, cleared after permanent removal. */
    private var retainedElement: E?,
) {
    /** Whether the retained node still occupies an attached lifecycle window. */
    val isAttached: Boolean get() = retainedNode?.isAttached == true

    /** The node retained by a standing record. */
    val node: N get() = checkNotNull(retainedNode)

    /** The declaration retained by a standing record. */
    var element: E
        get() = checkNotNull(retainedElement)
        protected set(value) {
            retainedElement = value
        }

    /** Releases the removed record's node and declaration. */
    fun clear() {
        retainedNode = null
        retainedElement = null
    }
}

/**
 * Mutable per-slot state held by the node holder across recompositions: one slot's node and the element
 * currently occupying it.
 *
 * The node hosts only elements of the exact class of the one it holds, as AndroidX reuses a node only for an
 * element of the same class; [canUpdate] checks that, so an update's narrowing is a verified [Class.cast].
 */
internal abstract class NodeRecord<N : SwingModifier.Node, E : SwingModifier.Element>(
    node: N?,
    element: E?,
    /** The key of the keyed record, or null for a positional record. */
    val key: Any?,
    /** The node declaring this record, or null for the outer modifier. */
    val declaringNode: SwingModifier.Node?,
) : RetainedNodeRecord<N, E>(node, element) {
    /** Whether a successful reconciliation committed this record's position. */
    var isCommitted: Boolean = false
        private set

    /** Whether this record is standing: it has not been withdrawn from its store. */
    var isStanding: Boolean = true
        private set

    /** Marks this record withdrawn from its store. */
    fun withdraw() {
        isStanding = false
    }

    /** Marks this record standing, and its position committed where it is keyed. */
    fun commit() {
        isStanding = true
        if (key != null) isCommitted = true
    }

    /** The existing node family used for matching and lifecycle ordering. */
    abstract val nodeType: ModifierNodeType<*>

    /** Properties this record releases when it leaves its declaring node. */
    open val heldProperties: Set<String> get() = emptySet()

    /**
     * Reports the consumers affected by an update of this record.
     *
     * @param holder the holder whose consumers are affected.
     * @param invalidates whether the update automatically invalidates its consumer.
     */
    abstract fun updateChanges(
        holder: SwingNodeHolder<*>,
        invalidates: Boolean,
    ): ModifierChanges

    /** Adds this record's parent-layout contribution to [records], if any. */
    internal open fun contributeParentLayout(records: MutableList<ParentLayoutNodeRecord>) {}

    /** Whether this record is standing for [declaringNode] and, unless [nodeType] is null, in that family. */
    fun isStandingFor(
        nodeType: ModifierNodeType<*>?,
        declaringNode: SwingModifier.Node?,
    ): Boolean = isStanding && this.declaringNode === declaringNode && (nodeType == null || this.nodeType === nodeType)

    /**
     * Inserts this record into [state], runs the node's [SwingModifier.Node.onAttach], then pushes the element onto
     * it: the first install. The record is installed while `onAttach` runs, so
     * [SwingModifier.Node.visitDeclaredNodes] finds the node from there; [state] takes it out again if `onAttach`
     * throws, and the node itself - marked attached before this ran - is detached with it: its `onDetach` runs, its
     * `coroutineScope` cancels, and its observed reads clear, so it is not left attached with no record holding it.
     * A node whose `onAttach` succeeded and whose first `update` then throws stays installed instead, for the next
     * pass to hand the same node its declaration again. Returns whether that `update` automatically invalidates its
     * consumer.
     *
     * @param state owns the record and withdraws it if the node's `onAttach` throws.
     * @param diagnostics where a failing callback is reported, or `null` for none.
     * @param replacing the record this one replaces, which is put back if `onAttach` throws, or `null` for none.
     */
    open fun runAttachLifecycle(
        state: SwingModifierState,
        diagnostics: SwingCompositionDiagnostics?,
        replacing: NodeRecord<*, *>? = null,
    ): Boolean {
        state.insert(this, replacing)
        // Re-throws every exception after withdrawing the record and detaching the node; catching Throwable ensures
        // errors during attach are unwound rather than leaving a half-attached node in the chain.
        try {
            node.runAttachLifecycle()
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Throwable,
        ) {
            if (replacing != null) checkNotNull(state.records).insert(replacing, this)
            state.records?.withdraw(this)
            // The caller gets the attach failure, so a throw from the rollback is suppressed on it, not reported.
            collectFailure(failure) { detach(diagnostics, reportCallbackFailure = false) }
            clear()
            throw failure
        }
        return refresh(checkNotNull(node.holder).component, null, WriteMode.ReuseEqual)
    }

    /**
     * Runs the node's [SwingModifier.Node.onDetach]; a throw is reported unless [reportCallbackFailure] is false.
     *
     * @param diagnostics where a failing callback is reported, or `null` for none.
     * @param reportCallbackFailure whether a throw from `onDetach` is reported instead of propagating.
     */
    open fun runDetachLifecycle(
        diagnostics: SwingCompositionDiagnostics?,
        reportCallbackFailure: Boolean = true,
    ): Unit = node.runDetachLifecycle(reportCallbackFailure)

    /**
     * Runs the node's [SwingModifier.Node.onDetach] and marks the node detached, even when the first fails. The first
     * failure propagates, with the later one suppressed on it. A throw from `onDetach` is reported unless
     * [reportCallbackFailure] is false, in which case it propagates.
     *
     * @param diagnostics where a failing callback is reported, or `null` for none.
     * @param reportCallbackFailure whether a throw from `onDetach` is reported instead of propagating.
     */
    open fun detach(
        diagnostics: SwingCompositionDiagnostics?,
        reportCallbackFailure: Boolean = true,
    ): Unit = runEveryStep({ runDetachLifecycle(diagnostics, reportCallbackFailure) }, node::detach)

    /**
     * Updates the record to [element] and writes it against [target] whatever the record already carries.
     * Only call when [canUpdate] holds. [mode] controls reuse and rewriting; see [refresh].
     *
     * @param element the declaration the record holds from now on.
     * @param target the component the declaration is written against.
     * @param diagnostics where a failing write is reported, or `null` for none.
     * @param mode whether equal declarations may be reused or must be written again.
     */
    fun updateTo(
        element: SwingModifier.Element,
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
        mode: WriteMode,
    ): Boolean {
        this.element = this.element.javaClass.cast(element)
        return refresh(target, diagnostics, mode)
    }

    /**
     * Whether this record can keep the element it holds for [incoming], the element the pass being applied
     * declares in its place - having handed the node whatever [incoming] carries as read, not applied. See
     * [SwingModifier.NodeElement.reuse].
     *
     * Where it can, [incoming] becomes the element the record holds. Nothing is written - it declares what
     * the one held did - but the element the last diff installed is released, and with it everything the
     * callbacks it carries capture.
     *
     * @param incoming the declaration the pass being applied declares in this record's place.
     */
    abstract fun reuse(incoming: SwingModifier.Element): Boolean

    /**
     * Pushes the retained declaration onto the node under [mode]'s ordering policy.
     *
     * @param target the component the declaration is written against.
     * @param diagnostics where a failing write is reported, or `null` for none.
     * @param mode whether equal declarations may be reused or must be written again.
     */
    abstract fun refresh(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
        mode: WriteMode,
    ): Boolean

    companion object {
        /**
         * Whether [record]'s node was created for the exact class of [element].
         *
         * @param record the record whose node is checked.
         * @param element the declaration the record would hold.
         */
        fun canUpdate(
            record: NodeRecord<*, *>,
            element: SwingModifier.Element,
        ): Boolean = element.javaClass === record.element.javaClass

        /**
         * Creates and marks attached the node of [element], an entry of [ModifierPartition.positional].
         *
         * @param element the declaration the new record holds.
         * @param holder the holder whose component the node targets.
         */
        fun mark(
            element: SwingModifier.Element,
            holder: SwingNodeHolder<*>,
        ): NodeRecord<*, *> =
            when (element) {
                is SwingModifier.NodeElement<*, *> -> ComponentNodeRecord.mark(element, holder)
                else -> ParentLayoutNodeRecord.mark(element as ParentLayoutNodeElement<*>, holder)
            }
    }
}

/**
 * The record of a [SwingModifier.NodeElement], whose node writes onto the component: the target is injected before
 * the node attaches and checked again on every write, and the composition's diagnostics watch each write and
 * restore.
 */
internal class ComponentNodeRecord<T : Component, N : SwingModifier.ComponentNode<T>>(
    node: N,
    element: SwingModifier.NodeElement<T, N>,
    /** The keyed identity, or null for a positional modifier record. */
    key: Any? = null,
    /** The declaring node, or null for the outer modifier. */
    declaringNode: SwingModifier.Node? = null,
) : NodeRecord<N, SwingModifier.NodeElement<T, N>>(node, element, key, declaringNode),
    ModifierInfo {
    override val component: Component get() = node.component

    override val modifier: SwingModifier.NodeElement<*, *> get() = element

    /** The family used by the shared store for this record. */
    override val nodeType: ModifierNodeType<*> get() = ComponentNodeType

    override val heldProperties: Set<String> get() = element.heldProperties

    override fun updateChanges(
        holder: SwingNodeHolder<*>,
        invalidates: Boolean,
    ): ModifierChanges = if (invalidates) ModifierChanges.ListenerNeedsUpdatedNodes else ModifierChanges.None

    override fun runAttachLifecycle(
        state: SwingModifierState,
        diagnostics: SwingCompositionDiagnostics?,
        replacing: NodeRecord<*, *>?,
    ): Boolean {
        var invalidates = false
        // A record's install is one write however the node splits it: some capture an original in onAttach and
        // write the declaration in update, others write in onAttach and have nothing to add.
        node.diagnosticsFor(diagnostics).watchWrite(this) {
            invalidates = super.runAttachLifecycle(state, diagnostics, replacing)
        }
        return invalidates
    }

    /** Runs the node's [SwingModifier.Node.onDetach], while the target is still injected. */
    override fun runDetachLifecycle(
        diagnostics: SwingCompositionDiagnostics?,
        reportCallbackFailure: Boolean,
    ): Unit =
        // A node restores its captured original through component.
        node.diagnosticsFor(diagnostics).watchRestore(this) {
            node.runDetachLifecycle(reportCallbackFailure)
        }

    /**
     * Binds a kept node to [element] before its attach lifecycle and update run in the new composition.
     *
     * @param element the element the node is bound to.
     * @param holder the holder whose component the node targets.
     * @param diagnostics where a failing callback is reported, or `null` for none.
     * @param state owns the record and withdraws it if the node's `onAttach` throws.
     */
    fun reattach(
        element: SwingModifier.NodeElement<*, *>,
        holder: SwingNodeHolder<*>,
        diagnostics: SwingCompositionDiagnostics?,
        state: SwingModifierState,
    ): Boolean {
        this.element = this.element.javaClass.cast(element)
        checkedTarget(this.element, holder.component)
        node.attach(holder)
        return runAttachLifecycle(state, diagnostics)
    }

    // Only a component element is paired with a component record.
    override fun reuse(incoming: SwingModifier.Element): Boolean {
        if (!canUpdate(this, incoming) ||
            !element.reuse(node, incoming as SwingModifier.NodeElement<*, *>)
        ) {
            return false
        }
        element = element.javaClass.cast(incoming)
        return true
    }

    /**
     * Re-narrows the target and pushes the element this record currently holds onto the node.
     *
     * @param target the component the declaration is written against.
     * @param diagnostics where a failing write is reported, or `null` for none.
     * @param mode whether equal declarations may be reused or must be written again.
     */
    override fun refresh(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
        mode: WriteMode,
    ): Boolean {
        val element = element
        val node = node
        var invalidates = false
        node.diagnosticsFor(diagnostics).watchWrite(this) {
            // Re-checks the target type: the component is stable, but re-narrowing keeps the error path
            // identical to first apply.
            checkedTarget(element, target)
            if (node is CompositionLocalConsumerModifierNode) {
                node.observeReads(OnLocalReadChanged) { invalidates = element.update(node, mode) }
            } else {
                invalidates = element.update(node, mode)
            }
        }
        return invalidates
    }

    companion object {
        /**
         * Checks the component of [holder] with [checkedTarget], creates a node for [element] and marks it
         * attached in the modifier of [holder], without running its `onAttach`.
         *
         * Typing [N] here keeps `create()`/`update()` together with no cast: the returned record holds the
         * concrete node, so a later recomposition pushes fresh data without re-narrowing the node's type.
         *
         * @param element the declaration the new record holds.
         * @param holder the holder whose component the node targets.
         * @param key the key of the record, or `null` for a positional record.
         * @param declaringNode the node declaring the record, or `null` for the outer modifier.
         */
        fun <T : Component, N : SwingModifier.ComponentNode<T>> mark(
            element: SwingModifier.NodeElement<T, N>,
            holder: SwingNodeHolder<*>,
            key: Any? = null,
            declaringNode: SwingModifier.Node? = null,
        ): ComponentNodeRecord<T, N> {
            checkedTarget(element, holder.component)
            val node = element.create()
            node.attach(holder)
            return ComponentNodeRecord(node, element, key, declaringNode)
        }
    }
}

/** The record of a [ParentLayoutNodeElement], whose node the parent's layout manager reads. */
internal class ParentLayoutNodeRecord(
    node: ParentLayoutNode,
    element: ParentLayoutNodeElement<*>,
    /** The keyed identity, or null for a positional modifier record. */
    key: Any? = null,
    /** The declaring node, or null for the outer modifier. */
    declaringNode: SwingModifier.Node? = null,
) : NodeRecord<ParentLayoutNode, ParentLayoutNodeElement<*>>(node, element, key, declaringNode) {
    /** The family used by the shared store for this record. */
    override val nodeType: ModifierNodeType<*> get() = ParentLayoutNodeType

    override fun updateChanges(
        holder: SwingNodeHolder<*>,
        invalidates: Boolean,
    ): ModifierChanges =
        (if (invalidates) ModifierChanges.ListenerNeedsUpdatedNodes else ModifierChanges.None) +
            (if (holder.declaration.parentReads(node)) ModifierChanges.ParentLayoutUpdated else ModifierChanges.None)

    override fun contributeParentLayout(records: MutableList<ParentLayoutNodeRecord>) {
        records.add(this)
    }

    override fun reuse(incoming: SwingModifier.Element): Boolean {
        if (!canUpdate(this, incoming) || element != incoming) return false
        element = element.javaClass.cast(incoming)
        return true
    }

    /**
     * Detaches the node, then [withdraws][org.jetbrains.compose.swing.node.ParentDeclaration.withdrawLayoutNodes] it
     * from what the measuring parent of its component last received, even when the detach fails.
     */
    override fun detach(
        diagnostics: SwingCompositionDiagnostics?,
        reportCallbackFailure: Boolean,
    ) {
        val declaration = node.holder?.declaration
        runEveryStep(
            { super.detach(diagnostics, reportCallbackFailure) },
            { declaration?.withdrawLayoutNodes()?.revalidate() },
        )
    }

    @Suppress("UNCHECKED_CAST") // The record's node was created by an element of this element's class.
    override fun refresh(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
        mode: WriteMode,
    ): Boolean {
        val element = element
        (element as ParentLayoutNodeElement<ParentLayoutNode>).update(node)
        checkDeclaration(node, element)
        return node.shouldAutoInvalidate
    }

    companion object {
        /**
         * Creates the node for [element] and marks it attached in the modifier of [holder], without its `onAttach`.
         *
         * @param element the declaration the new record holds.
         * @param holder the holder the node attaches to.
         * @param key the key of the record, or `null` for a positional record.
         * @param declaringNode the node declaring the record, or `null` for the outer modifier.
         */
        fun mark(
            element: ParentLayoutNodeElement<*>,
            holder: SwingNodeHolder<*>,
            key: Any? = null,
            declaringNode: SwingModifier.Node? = null,
        ): ParentLayoutNodeRecord {
            val node = element.create()
            checkDeclaration(node, element)
            node.attach(holder)
            return ParentLayoutNodeRecord(node, element, key, declaringNode)
        }

        /**
         * Refuses a [node] declaring another `parentProtocol` or `inheritable` than [element], the element that
         * created or updated it, and an [element] [both inheritable and additive][requireInheritableIsNotAdditive].
         */
        private fun checkDeclaration(
            node: ParentLayoutNode,
            element: ParentLayoutNodeElement<*>,
        ) {
            check(node.parentProtocol === element.parentProtocol && node.inheritable == element.inheritable) {
                "${node.javaClass.name} must declare the parentProtocol and inheritable its element " +
                    "${element.javaClass.name} declares."
            }
            element.requireInheritableIsNotAdditive()
        }
    }
}

/** Owns the ordered records of a modifier and the records declared by their nodes. */
internal class NodeRecords : AbstractList<NodeRecord<*, *>>() {
    /** Committed positions followed by additions from an unfinished reconciliation. */
    private val records = ArrayList<NodeRecord<*, *>>(1)

    /** The occupied positions, including empty committed positions. */
    override val size: Int get() = records.size

    /**
     * How many positional records of the outer modifier the store holds. A positional record is never committed,
     * so it leaves the store when it is withdrawn and every one held is standing.
     */
    var positionalCount: Int = 0
        private set

    /**
     * Reads an occupied physical position, including a withdrawn committed record.
     *
     * @param index the physical position to read.
     */
    override fun get(index: Int): NodeRecord<*, *> = records[index]

    /**
     * The standing records of [nodeType] that [declaringNode] declares, copied so that a caller can remove them
     * while it visits them.
     *
     * @param nodeType the family of the records.
     * @param declaringNode the node declaring the records.
     */
    fun standing(
        nodeType: ModifierNodeType<*>,
        declaringNode: SwingModifier.Node?,
    ): List<NodeRecord<*, *>> {
        val standing = ArrayList<NodeRecord<*, *>>()
        fastForEach { if (it.isStandingFor(nodeType, declaringNode)) standing.add(it) }
        return standing
    }

    /**
     * Finds a standing keyed record, or the positional one at [index] when [key] is null, among the records of
     * [nodeType].
     *
     * @param nodeType the family the record belongs to.
     * @param declaringNode the node declaring the record, or `null` for the outer modifier.
     * @param key the key of the record, or `null` for a positional record.
     * @param index which positional record to find; ignored when [key] is not null.
     */
    fun <R : NodeRecord<*, *>> get(
        nodeType: ModifierNodeType<R>,
        declaringNode: SwingModifier.Node?,
        key: Any?,
        index: Int = 0,
    ): R? {
        var position = 0
        records.fastForEach { record ->
            if (record.isStandingFor(nodeType, declaringNode) && record.key == key) {
                if (key != null || position++ == index) return nodeType.typed(record)
            }
        }
        return null
    }

    /**
     * Publishes a record before attachment, keeping a replaced or committed position.
     *
     * @param record the record to publish.
     * @param from the record whose position [record] takes, or `null` for none.
     */
    fun insert(
        record: NodeRecord<*, *>,
        from: NodeRecord<*, *>? = null,
    ) {
        if (!record.isStanding) record.commit()
        val index =
            records.indexOfFirst { standing ->
                val sameKey = record.key != null && record.key == standing.key
                val sameDeclaringNode =
                    record.nodeType === standing.nodeType && record.declaringNode === standing.declaringNode
                standing === from || (sameKey && sameDeclaringNode)
            }
        if (index >= 0) {
            val standing = records[index]
            if (standing.isCommitted) record.commit()
            if (standing.isOuterPositional) positionalCount--
            if (record.isOuterPositional) positionalCount++
            records[index] = record
            return
        }
        if (record.isOuterPositional) positionalCount++
        records.add(record)
    }

    private val NodeRecord<*, *>.isOuterPositional: Boolean get() = key == null && declaringNode == null

    /**
     * Withdraws a failed attachment without running its rollback callbacks.
     *
     * @param record the record to withdraw.
     */
    fun withdraw(record: NodeRecord<*, *>) {
        record.withdraw()
        if (record.isCommitted) return
        val index = records.indexOfFirst { it === record }
        if (index >= 0) removeAt(index)
    }

    /**
     * Drops detached records of one declaring node and family after their lifecycle completes.
     *
     * @param nodeType the family of the records dropped.
     * @param declaringNode the node declaring the records, or `null` for the outer modifier.
     */
    fun clear(
        nodeType: ModifierNodeType<*>,
        declaringNode: SwingModifier.Node?,
    ) {
        val dropped = records.filter { it.nodeType === nodeType && it.declaringNode === declaringNode }
        dropped.asReversed().fastForEach { record ->
            record.clear()
            removeAt(records.indexOfFirst { it === record })
        }
    }

    /** Drops all records after modifier teardown. */
    fun clear() {
        records.clear()
        positionalCount = 0
    }

    /** The physical order of declarations within this record store. */
    val order = DeclarationOrder()

    /** Ordering within this record store. */
    inner class DeclarationOrder {
        /**
         * Commits the keyed declaration order of the standing record of each of [elements], skipping elements
         * with none.
         *
         * @param declaringNode the node declaring the records, or `null` for the outer modifier.
         * @param elements the keyed declarations, in declared order.
         */
        fun commit(
            declaringNode: SwingModifier.Node?,
            elements: List<SwingModifier.NodeElement<*, *>>,
        ): Boolean = commit(declaringNode, elements, emptyList())

        /**
         * Commits the combined positional order of the standing records, one for each of [families], which states
         * the family of each declaration in order.
         *
         * @param families the family of each positional declaration, in declared order.
         */
        fun commitPositional(families: List<ModifierNodeType<*>>): Boolean = commit(null, null, families)

        /** Commits the keyed order of [keyed], or the positional order of [families] where [keyed] is `null`. */
        private fun commit(
            declaringNode: SwingModifier.Node?,
            keyed: List<SwingModifier.NodeElement<*, *>>?,
            families: List<ModifierNodeType<*>>,
        ): Boolean {
            if (matchesOrder(declaringNode, keyed, families)) return false
            val ordered = ArrayList<NodeRecord<*, *>>((keyed?.size ?: 0) + families.size)
            forEachInOrder(declaringNode, keyed, families) { ordered.add(it) }
            return replace(declaringNode, if (keyed == null) null else ComponentNodeType, ordered)
        }

        /**
         * Commits [ordered] among the records of one declaring node.
         *
         * @param declaringNode the node declaring the records, or `null` for the outer modifier.
         * @param nodeType the family of the keyed records ordered, or `null` for the positional records.
         * @param ordered the records in their new order.
         */
        fun replace(
            declaringNode: SwingModifier.Node?,
            nodeType: ModifierNodeType<*>?,
            ordered: List<NodeRecord<*, *>>,
        ): Boolean {
            var moved = false
            var position = 0
            val surplus = ArrayList<Int>(0)
            records.fastForEachIndexed { index, record ->
                if (record.isOrderedFor(declaringNode, nodeType)) {
                    if (position >= ordered.size) {
                        surplus.add(index)
                    } else {
                        val next = ordered[position++]
                        if (record !== next) moved = true
                        records[index] = next
                        if (nodeType != null) next.commit()
                    }
                }
            }
            surplus.asReversed().fastForEach { removeAt(it) }
            return moved
        }

        private fun NodeRecord<*, *>.isOrderedFor(
            declaringNode: SwingModifier.Node?,
            nodeType: ModifierNodeType<*>?,
        ): Boolean =
            this.declaringNode === declaringNode &&
                if (nodeType == null) key == null else this.nodeType === nodeType && key != null

        /** Visits the standing records of the incoming declarations without committing their order. */
        private inline fun forEachInOrder(
            declaringNode: SwingModifier.Node?,
            keyed: List<SwingModifier.NodeElement<*, *>>?,
            families: List<ModifierNodeType<*>>,
            action: (NodeRecord<*, *>) -> Unit,
        ) {
            if (keyed != null) {
                keyed.fastForEach { element ->
                    val record = get(ComponentNodeType, declaringNode, element.key)
                    if (record != null) action(record)
                }
                return
            }
            var componentIndex = 0
            var layoutIndex = 0
            families.fastForEach { family ->
                if (family === ComponentNodeType) {
                    val component = nextPositionalIndex(componentIndex, ComponentNodeType, declaringNode)
                    componentIndex = component + 1
                    if (component < size) action(this@NodeRecords[component])
                } else {
                    val layout = nextPositionalIndex(layoutIndex, ParentLayoutNodeType, declaringNode)
                    layoutIndex = layout + 1
                    if (layout < size) action(this@NodeRecords[layout])
                }
            }
        }

        /** Commits matching keyed positions without building a permutation. */
        private fun matchesOrder(
            declaringNode: SwingModifier.Node?,
            keyed: List<SwingModifier.NodeElement<*, *>>?,
            families: List<ModifierNodeType<*>>,
        ): Boolean {
            val nodeType = if (keyed == null) null else ComponentNodeType
            var index = 0
            forEachInOrder(declaringNode, keyed, families) { next ->
                run {
                    records.fastForEachIn(index until size) {
                        if (it.isOrderedFor(declaringNode, nodeType)) return@run
                        index++
                    }
                }
                if (index == size || this@NodeRecords[index++] !== next) return false
            }
            var matches = true
            records.fastForEachIn(index until size) {
                if (it.isOrderedFor(declaringNode, nodeType)) matches = false
            }
            if (matches && nodeType != null) {
                records.fastForEach { record ->
                    if (record.isStandingFor(nodeType, declaringNode) && record.key != null) record.commit()
                }
            }
            return matches
        }
    }

    /** Removes one occupied position without retaining its references. */
    private fun removeAt(index: Int) {
        if (records[index].isOuterPositional) positionalCount--
        records.removeAt(index)
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
    /**
     * Whether the node's ancestors changed since its style slots were last checked against them: a declaration
     * takes a slot only while it can collect something under them.
     */
    internal var ancestorsChanged: Boolean = false

    /** The linked style declarations, kept separately from their descriptor entries. */
    internal var firstStyle: StyleNode? = null

    /** The shared store, allocated by the first node installation and absent while this modifier has no records. */
    internal var records: NodeRecords? = null
        private set

    /** How many positional records the modifier holds. */
    internal val positionalCount: Int get() = records?.positionalCount ?: 0

    /**
     * Publishes a node without allocating storage for an empty modifier.
     *
     * @param record the record to publish.
     * @param replacing the record [record] replaces, or `null` for none.
     */
    internal fun insert(
        record: NodeRecord<*, *>,
        replacing: NodeRecord<*, *>? = null,
    ) {
        val records = records ?: NodeRecords().also { records = it }
        records.insert(record, replacing)
    }

    /**
     * Records the declaration whose application completed.
     *
     * @param modifier the declaration that was applied.
     */
    internal fun adopt(modifier: SwingModifier) {
        applied = modifier
        hasApplied = true
    }

    /**
     * Puts back the declaration [startWrite] returned, for a write that applied nothing.
     *
     * @param held the declaration [startWrite] returned.
     */
    internal fun restore(held: SwingModifier) {
        applied = held
    }

    /** Whether a declaration has been applied: a state created only to hold a style has not. */
    private var hasApplied = false

    /** Whether a declaration applied or a failed one awaits its retry; a state holding only a style has neither. */
    internal val hasApplication: Boolean get() = hasApplied || !finished

    /** Whether a chain entering this state attaches whole: no node is installed and no declaration has applied. */
    internal val attachesWholeChain: Boolean get() = records == null && !hasApplied

    /**
     * Records application keys before diffing and reports whether they changed.
     *
     * @param keys the keys the modifier being applied ties its application to, or `null` where it declares none.
     */
    internal fun takeKeys(keys: List<Any?>?): Boolean {
        val changed = declaredKeys != keys
        declaredKeys = keys
        return changed
    }

    /**
     * The modifier last declared for this node, which the next pass compares its own declaration against. Every
     * pass that finishes replaces it with the modifier it declared, whether the walk wrote the slots in place or
     * the diff applied it. [org.jetbrains.compose.swing.node.SwingComponentNode.modifier] reads it through
     * [declared].
     *
     * Reuse decides what reaches the component, not what is held: a slot that reuses its element writes
     * nothing, but it still takes the new instance, releasing everything the replaced one captured.
     *
     * An [UnfinishedPass] from the start of a pass or a slot refresh until it returns. If one is left behind
     * because a write threw, it equals no declaration, so the next declaration is diffed and every slot is
     * written again.
     */
    private var applied: SwingModifier = SwingModifier

    /** Whether the last application completed without leaving an unfinished write. */
    internal val finished: Boolean get() = applied !is UnfinishedPass

    /** [applied], or [SwingModifier] where the last write threw. */
    internal val declared: SwingModifier get() = if (applied is UnfinishedPass) SwingModifier else applied

    /**
     * Whether the nodes last handed to a [DeclaredNodesListener] were any. Every pass that finishes hands over the
     * [records] it changed and the layout nodes the style declares, so outside a pass that threw the nodes in effect
     * are the ones last handed.
     */
    internal val handedNodes: Boolean get() = (applied as? UnfinishedPass)?.handedNodes ?: hasPositionalRecords

    /** Whether the modifier declares positional records or its style has attached layout nodes. */
    private val hasPositionalRecords: Boolean
        get() {
            if (positionalCount != 0) return true
            var holds = false
            forEachStyle { if (it.holdsLayoutNodes) holds = true }
            return holds
        }

    /**
     * Marks the records as holding no declaration until [applied] is written again, and returns what [applied] held.
     * A write that throws leaves the mark in place.
     */
    internal fun startWrite(): SwingModifier {
        val held = applied
        if (held !is UnfinishedPass) {
            applied = if (hasPositionalRecords) UnfinishedPass.Handed else UnfinishedPass.NoneHanded
        }
        return held
    }

    private var propertyCaptures: PropertyCaptures? = null

    /**
     * What each property this modifier writes held before it did; see [PropertyCaptures]. Built by the
     * first node that asks, so a modifier declaring no property holds none.
     */
    internal fun captures(): PropertyCaptures = propertyCaptures ?: PropertyCaptures().also { propertyCaptures = it }

    private var propertyRewrites: PropertyRewrites? = null

    /**
     * The order this modifier's `rewriteOn` declarations were written in; see [PropertyRewrites]. Built by the first
     * node that asks, so a modifier declaring no `rewriteOn` holds none.
     */
    internal fun rewrites(): PropertyRewrites = propertyRewrites ?: PropertyRewrites().also { propertyRewrites = it }

    /**
     * The keys the modifier applied last tied its application to, `null` where it declared none - which
     * is what tells a modifier declaring no key apart from one declaring `null`.
     *
     * The next pass has to compare its own keys against these, and the modifier itself is what carries
     * them, so this is the list [applied] declares. It is written before the diff and [applied] after
     * it: a teardown that throws partway leaves the keys that reached it installed, and the pass that
     * follows tears down again rather than diffing onto records the last one abandoned. See
     * [org.jetbrains.compose.swing.modifier.key].
     */
    private var declaredKeys: List<Any?>? = null

    /**
     * Every standing record in the order the records come apart: the property records in the reverse of the order
     * the modifier declared them last, then the additive component records from the end - the order
     * [applyModifierDiff] unwinds a departed record of each kind in - then the parent-layout records.
     */
    internal fun standingInUnwindOrder(): List<NodeRecord<*, *>> {
        val unwinding = ArrayList<NodeRecord<*, *>>()
        val records = records ?: return unwinding
        val reversed = records.asReversed()
        reversed.fastForEach { record ->
            if (record.isStandingFor(ComponentNodeType, null) && record.key != null) unwinding.add(record)
        }
        reversed.fastForEach { record ->
            if (record.isStandingFor(ComponentNodeType, null) && record.key == null) unwinding.add(record)
        }
        records.fastForEach { record ->
            if (record.isStandingFor(ParentLayoutNodeType, null)) unwinding.add(record)
        }
        return unwinding
    }

    /**
     * Takes every component record apart and drops it, in [standingInUnwindOrder]: every node runs
     * [SwingModifier.Node.onDetach], and only then is any node marked detached. The parent-layout records stay.
     *
     * What the modifier captured is left in place. Every capture of it is taken by a record and released when that
     * record detaches, so a modifier with no records left holds no captured value, and a record attaching afterwards
     * captures the value the component holds then.
     *
     * @param diagnostics where a failing callback is reported, or `null` for none.
     */
    internal fun unwindComponentNodes(diagnostics: SwingCompositionDiagnostics?): Boolean {
        val unwinding = standingInUnwindOrder().filter { it.nodeType === ComponentNodeType }
        val removed = unwinding.any { it.key == null }
        try {
            unwinding.detachAll(diagnostics)
        } finally {
            unwinding.fastForEach { record ->
                records?.clear(ComponentNodeType, record.node)
                records?.clear(ParentLayoutNodeType, record.node)
            }
            records?.clear(ComponentNodeType, null)
        }
        return removed
    }

    /**
     * Runs the `update` of the record holding [node] again, against [node]'s component, and hands the holder's
     * [DeclaredNodesListener] its nodes where the rewritten node automatically invalidates its consumer.
     *
     * @param node the node whose record is updated again.
     */
    internal fun refreshLocalConsumer(node: SwingModifier.ComponentNode<*>) {
        val holder = node.holder
        val diagnostics = holder?.owner?.diagnostics
        val handed = handedNodes
        val held = startWrite()
        var needsDeclaredNodes = false
        if (!rewritePropertySlotsFrom(
                node.component,
                diagnostics,
                onInvalidates = { needsDeclaredNodes = true },
            ) { it.node === node }
        ) {
            val rewritten = records?.fastFirstOrNull { it.key == null && it.node === node }
            if (rewritten != null) {
                if (rewritten.refresh(node.component, diagnostics, mode = WriteMode.ReuseEqual)) {
                    needsDeclaredNodes = true
                }
            } else {
                // A node a style's slot holds is stored in the modifier's records with that style as its declaring
                // node, so when no record above holds it, the lookup below rewrites each style's slots and only the
                // style holding it matches.
                forEachStyle(holder) { style ->
                    style.rewriteSlotsFrom(onInvalidates = { needsDeclaredNodes = true }) { record ->
                        record.node === node
                    }
                }
            }
        }
        applied = held
        if (holder != null && needsDeclaredNodes) {
            holder.notifyDeclaredNodes(chainChanged = true, handedNodes = handed)
        }
    }

    /**
     * Updates the first property record selected by [from] and every later property record in declared order.
     * Later declarations take precedence over earlier writes, as in [diffKeyedElements]. Returns whether
     * [from] selected a record.
     *
     * @param target the component the records write to.
     * @param diagnostics where a failing write is reported, or `null` for none.
     * @param onInvalidates called for each record whose update automatically invalidates its consumer.
     * @param except the node whose records are skipped, or `null` to skip none.
     * @param from selects the first record to update.
     */
    internal fun rewritePropertySlotsFrom(
        target: Component,
        diagnostics: SwingCompositionDiagnostics?,
        onInvalidates: () -> Unit = {},
        except: SwingModifier.Node? = null,
        from: (ComponentNodeRecord<*, *>) -> Boolean,
    ): Boolean =
        rewriteRecordsFrom(records, null, except, { record, mode ->
            if (record.refresh(target, diagnostics, mode)) onInvalidates()
        }, from)
}

/**
 * What [SwingModifierState.applied] holds while the slots are being written: equal to no declaration, carrying
 * whether the nodes last handed to a [DeclaredNodesListener] were any.
 */
internal class UnfinishedPass private constructor(
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
 * Runs after the component's own `set`s, so a modifier can override what they wrote. A modifier
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
 * Inherited component defaults in [localMap] are put ahead of [modifier], keyed apart from it, so a pass
 * where neither changed allocates nothing, and a change of an unrelated local re-diffs nothing.
 *
 * @param localMap the composition locals in scope where the node declaring [modifier] was composed.
 * @param modifier the modifier the node declares; [SwingModifier] clears its chain.
 */
@PublishedApi
internal fun SwingNodeUpdater<out Component>.applyModifier(
    localMap: CompositionLocalMap,
    modifier: SwingModifier,
) {
    // A change of the defaults alone re-diffs at the end of the batch, so a modifier changed on the same
    // pass has already applied them and the node is diffed once.
    updater.set(localMap[LocalComponentDefaults]) {
        if (modifierState?.hasApplication == true) requireOwner().updateBatch.holdForDefaultsRefresh(this)
    }
    updater.set(modifier) {
        applyDeclaredModifier(compositionLocalMap[LocalComponentDefaults].effectiveModifier(component.javaClass, it))
    }
}

/**
 * @param modifier the modifier to diff onto this node's chain.
 * @param needsDeclaredNodes whether a record written in place before this diff holds a node the holder's
 *   [DeclaredNodesListener] needs; the diff reuses that record and hands the nodes over.
 * @param diffLayoutChange the layout declaration change from nodes the linear diff already updated.
 */
internal fun SwingNodeHolder<Component>.applyModifierDiff(
    modifier: SwingModifier,
    needsDeclaredNodes: Boolean = false,
    diffLayoutChange: ModifierChanges = ModifierChanges.None,
) {
    // A holder with no installed chain marks it whole before any attachment callback runs.
    val attachesWholeChain = modifierState?.attachesWholeChain != false
    val state = modifierState ?: SwingModifierState().also { modifierState = it }
    // A holder whose last write threw has records holding no declaration: every record is written again, and the
    // nodes it attached are handed over.
    val mode = if (state.startWrite() !is UnfinishedPass) WriteMode.ReuseEqual else WriteMode.RewriteEqual
    val handedNodes = state.handedNodes
    val initialChange = (mode === WriteMode.RewriteEqual) or needsDeclaredNodes

    val incoming = partitionModifier(modifier, state)
    val multipleStyles = state.firstStyle?.nextStyle != null || incoming.styleCount > 1
    val previousStyleLayout = if (multipleStyles) declaration.parentLayoutElements else null

    val chainChanged =
        reconcileModifierChain(state, incoming, mode, attachesWholeChain, diffLayoutChange) || initialChange

    state.adopt(modifier)
    if (previousStyleLayout != null && previousStyleLayout != declaration.parentLayoutElements) declaration.reapply()
    notifyDeclaredNodes(chainChanged, handedNodes)
}

private fun SwingNodeHolder<Component>.reconcileModifierChain(
    state: SwingModifierState,
    incoming: ModifierPartition,
    mode: WriteMode,
    wholeChain: Boolean,
    diffLayoutChange: ModifierChanges,
): Boolean {
    // Read off the owner rather than held: the node is attached to its composition before its update
    // block runs, so the owner answers for every write this pass makes.
    val diagnostics = owner?.diagnostics
    val reconcilesStyles = state.firstStyle != null || incoming.styleCount != 0
    // A holder with no owner has no batch to publish the declaration on; a visit mid-pass walks its stored chain.
    val batch = owner?.updateBatch
    val outerState = batch?.diffingState
    val outerDeclaration = batch?.diffingDeclaration
    batch?.diffingState = state
    batch?.diffingDeclaration = null
    var marked: MarkedChain? = null
    var chainChanged = false
    var attachesWholeChain = wholeChain
    try {
        // A modifier tied to other keys than the ones applied last has its component nodes applied from
        // scratch rather than diffed: every component record comes apart, putting back what it captured, and the
        // chain below attaches them afresh. The layout nodes stay installed and are diffed. See [key] for what that is
        // worth.
        if (state.takeKeys(incoming.keys)) {
            var heldStyleLayout = false
            state.forEachStyle(this) { if (it.holdsLayoutNodes) heldStyleLayout = true }
            if (state.unwindComponentNodes(diagnostics) || heldStyleLayout) chainChanged = true
            attachesWholeChain = true
        }
        // Published only now, so a node the unwind above detaches visits the order last applied.
        batch?.diffingDeclaration = incoming.positional
        // Every node attaching whole is marked before the first onAttach runs; a node entering an installed chain
        // is marked and run on its own, by the diffs, which find the nodes a whole attach ran installed on their
        // own elements. The layout nodes go first, so the declaration the parent reads is published before any
        // component node runs - and, by a pass that throws partway, with the layout nodes installed then.
        val marksNodes = attachesWholeChain && (incoming.keyed.isNotEmpty() || incoming.positional.isNotEmpty())
        marked = if (marksNodes) MarkedChain.mark(this, state, incoming) else null
        if (applyLayoutNodes(state, incoming, marked, mode, diffLayoutChange)) chainChanged = true
        if (applyComponentSlots(state, incoming, marked, mode)) chainChanged = true
        if (state.records?.order?.commitPositional(incoming.positionalFamilies) == true) chainChanged = true
    } finally {
        // A node this whole attach marked but whose own onAttach never ran - because an earlier node's did and
        // threw - is attached with no record holding it, the same leak an onAttach that itself throws would leave;
        // see [MarkedChain.detachOrphaned].
        try {
            marked?.detachOrphaned(state)
        } finally {
            if (reconcilesStyles) state.finishStyleReconciliation()
            batch?.diffingState = outerState
            batch?.diffingDeclaration = outerDeclaration
        }
    }

    return chainChanged
}

/**
 * Folds [modifier] into a partition sized for the records of [state], checks its placement, and records the declared
 * slot on this holder.
 */
private fun SwingNodeHolder<Component>.partitionModifier(
    modifier: SwingModifier,
    state: SwingModifierState,
): ModifierPartition {
    // Walk the modifier once, routing each entry to whatever reads it. The partition is the fold's
    // accumulator, so the walk carries its destinations rather than capturing them. It is sized for the records
    // installed, which a modifier changing its values rather than its shape declares again.
    val installed = state.records?.size ?: 0
    val incoming = ModifierPartition(installed - state.positionalCount, state.positionalCount, holder = this)
    modifier.foldIn(incoming) { partition, element ->
        partition.take(element)
        partition
    }
    // A component that declares no style takes the unstyled declaration, so a style provided for AllComponents
    // reaches it too.
    if (incoming.styleCount == 0) incoming.take(UnstyledComponent)

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
    checkOnePlacement(slot, parentDeclarations)
    checkDeclaredPlacement(slot, parentDeclarations)
    declaration.checkParentElementsAccepted(parentDeclarations)
    declaredSlot = slot?.let { DeclaredSlot(it.parentProtocol, it.attachment, it.regionName) }
    return incoming
}

/**
 * Updates the layout nodes of [incoming] and publishes the declaration the parent reads, even if an update throws.
 * Returns whether a node joined, left or needs handing over.
 */
private fun SwingNodeHolder<Component>.applyLayoutNodes(
    state: SwingModifierState,
    incoming: ModifierPartition,
    marked: MarkedChain?,
    mode: WriteMode,
    diffLayoutChange: ModifierChanges,
): Boolean {
    val diagnostics = owner?.diagnostics
    // Publish the remaining layout nodes even if their update throws.
    var chainChanged = false
    var layoutChange = ModifierChanges.NodesAddedOrRemoved + ModifierChanges.ParentLayoutUpdated
    try {
        // Nodes a whole attach just updated hold their elements whatever the last pass left, and joined the chain.
        val attached = marked?.runLayoutLifecycles(state, diagnostics) == true
        var changes =
            updateModifierNodes(
                this,
                state,
                incoming,
                ParentLayoutNodeType,
                if (attached) WriteMode.ReuseEqual else mode,
            )
        if (attached) changes += ModifierChanges.NodesAddedOrRemoved
        layoutChange = changes
        chainChanged = changes.needsDeclaredNodes
    } finally {
        layoutChange += diffLayoutChange
        declaration.applyComponentLayout(incoming.resolved(), incoming.parentLayoutElements, layoutChange)
    }
    return chainChanged
}

/**
 * Runs the component nodes [marked] attaches, or diffs the keyed records of [partition] where none does, then diffs
 * the additive component records. Returns whether a record joined or left the chain, or was written with a new element
 * that automatically invalidates its consumer. A record standing
 * from before this pass reuses an equal element only where [mode] permits it.
 */
private fun SwingNodeHolder<*>.applyComponentSlots(
    state: SwingModifierState,
    partition: ModifierPartition,
    marked: MarkedChain?,
    mode: WriteMode,
): Boolean {
    val diagnostics = owner?.diagnostics
    val needsDeclaredNodes =
        if (marked == null) {
            diffKeyedElements(partition.keyed, mode)
        } else {
            marked.runComponentLifecycles(state, diagnostics)
        }
    // A whole attach leaves no component record standing from before this pass.
    val changes =
        updateModifierNodes(
            this,
            state,
            partition,
            ComponentNodeType,
            if (marked != null) {
                WriteMode.ReuseEqual
            } else {
                mode
            },
        )
    return needsDeclaredNodes || changes.needsDeclaredNodes
}

/**
 * The nodes of a chain [mark] or [markAttached] marked attached, whose `onAttach` has yet to run.
 *
 * A record is standing before its node's `onAttach` runs and is withdrawn again if `onAttach` throws, so a pass that
 * throws partway leaves the nodes whose `onAttach` did not run withdrawn, as the diff does. The keyed nodes are
 * installed in the order of [ModifierPartition.keyed].
 *
 * [mark] may itself leave a node marked attached whose `onAttach` never got a turn: one it created before an
 * element declared after it failed to be created, or one behind a family whose own `onAttach` threw before this
 * one's had a chance to run. Neither is standing in the records by the time the throw reaches the caller, so nothing
 * else would ever detach them - [mark] unwinds the first kind itself, and [detachOrphaned] the second.
 */
internal class MarkedChain(
    private val keyed: List<ComponentNodeRecord<*, *>>,
    private val components: List<ComponentNodeRecord<*, *>>,
    private val layoutNodes: List<ParentLayoutNodeRecord>,
) {
    /**
     * Runs each parent-layout node - its `onAttach` and its element's `update` - in turn. Returns whether any
     * joined the chain.
     */
    fun runLayoutLifecycles(
        state: SwingModifierState,
        diagnostics: SwingCompositionDiagnostics?,
    ): Boolean {
        layoutNodes.fastForEach { it.runAttachLifecycle(state, diagnostics) }
        return layoutNodes.isNotEmpty()
    }

    /**
     * Runs each component node in turn, keyed slots first, as the diff attaches them. Returns whether a keyed node's
     * update automatically invalidates its consumer or a component node joined the chain.
     */
    fun runComponentLifecycles(
        state: SwingModifierState,
        diagnostics: SwingCompositionDiagnostics?,
    ): Boolean {
        var autoInvalidates = false
        keyed.fastForEach { if (it.runAttachLifecycle(state, diagnostics)) autoInvalidates = true }
        val ordered = ArrayList<NodeRecord<*, *>>(keyed.size)
        keyed.fastForEach { if (it.isStanding) ordered.add(it) }
        state.records?.order?.replace(null, ComponentNodeType, ordered)
        components.fastForEach { if (it.runAttachLifecycle(state, diagnostics)) autoInvalidates = true }
        return autoInvalidates || components.isNotEmpty()
    }

    /** Marks every node attached to [holder], without running any `onAttach`. */
    fun markAttached(holder: SwingNodeHolder<*>) {
        keyed.fastForEach { it.node.attach(holder) }
        components.fastForEach { it.node.attach(holder) }
        layoutNodes.fastForEach { it.node.attach(holder) }
    }

    /**
     * Detaches every node this marked that never joined [state]: one whose `onAttach` never got a turn because an
     * earlier node's threw first. A node whose own `onAttach` ran - whether it went on to join [state] or threw
     * and was already detached by [NodeRecord.runAttachLifecycle] - is untouched here; only one still attached and
     * absent from [SwingModifierState.records] is reached, and it runs no
     * `onDetach`, since none of its own lifecycle ran either. Every orphan is detached even when detaching one
     * throws; the first failure propagates, with the later ones suppressed on it.
     */
    fun detachOrphaned(state: SwingModifierState) {
        val records = ArrayList<NodeRecord<*, *>>(keyed.size + components.size + layoutNodes.size)
        records += keyed
        records += components
        records += layoutNodes
        var failure: Throwable? = null
        records.fastForEach { record -> failure = collectFailure(failure) { record.detachIfOrphaned(state) } }
        failure?.let { throw it }
    }

    private fun NodeRecord<*, *>.detachIfOrphaned(state: SwingModifierState) {
        if (!isAttached) return
        var standing = false
        state.records?.fastForEach { if (it === this) standing = true }
        if (!standing) node.detach()
    }

    companion object {
        private fun markKeyed(
            holder: SwingNodeHolder<*>,
            partition: ModifierPartition,
            records: MutableList<ComponentNodeRecord<*, *>>?,
        ) {
            partition.keyed.fastForEach { element ->
                checkNotNull(records).add(ComponentNodeRecord.mark(element, holder, element.key))
            }
        }

        private fun markComponents(
            holder: SwingNodeHolder<*>,
            elements: List<SwingModifier.Element>,
            records: MutableList<ComponentNodeRecord<*, *>>?,
        ) {
            if (records == null) return
            elements.fastForEach { element ->
                if (element is SwingModifier.NodeElement<*, *>) records.add(ComponentNodeRecord.mark(element, holder))
            }
        }

        private fun markLayout(
            holder: SwingNodeHolder<*>,
            elements: List<SwingModifier.Element>,
            records: MutableList<ParentLayoutNodeRecord>?,
        ) {
            if (records == null) return
            elements.fastForEach { element ->
                if (element is ParentLayoutNodeElement<*>) records.add(ParentLayoutNodeRecord.mark(element, holder))
            }
        }

        /**
         * Creates and marks attached a node for every component element of [partition], and for every parent-layout
         * element where [state] holds no parent-layout record, for a holder attaching its chain whole, without
         * running any `onAttach`. A node created here is unwound the same way if creating a later element fails,
         * so a throw partway through never hands the caller a chain holding a leaked node.
         *
         * @param holder the holder whose component the nodes target.
         * @param state the modifier state whose parent-layout records decide whether layout nodes are created.
         * @param partition the elements to create nodes for.
         */
        fun mark(
            holder: SwingNodeHolder<*>,
            state: SwingModifierState,
            partition: ModifierPartition,
        ): MarkedChain {
            val keyedElements = partition.keyed
            keyedElements.fastForEach { checkedTarget(it, holder.component) }
            val elements = partition.positional
            var componentCount = 0
            elements.fastForEach {
                if (it is SwingModifier.NodeElement<*, *>) {
                    checkedTarget(it, holder.component)
                    componentCount++
                }
            }
            val layoutNodeCount = elements.size - componentCount
            // A key change leaves the parent-layout records standing, and the diff pairs them with their elements.
            val marksLayoutNodes = layoutNodeCount != 0 && state.positionalCount == 0
            val keyed = if (keyedElements.isEmpty()) null else ArrayList<ComponentNodeRecord<*, *>>(keyedElements.size)
            val components = if (componentCount == 0) null else ArrayList<ComponentNodeRecord<*, *>>(componentCount)
            val layoutNodes = if (marksLayoutNodes) ArrayList<ParentLayoutNodeRecord>(layoutNodeCount) else null
            // Re-throws every exception after detaching all nodes created during the mark pass, so any error
            // during node instantiation is unwound cleanly.
            try {
                markKeyed(holder, partition, keyed)
                markComponents(holder, elements, components)
                markLayout(holder, elements, layoutNodes)
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Throwable,
            ) {
                // Every node created above is attach()ed but has not run its onAttach, so unwinding it undoes
                // exactly that mark: no onDetach runs for a node whose onAttach never did.
                keyed?.fastForEach { it.node.detach() }
                components?.fastForEach { it.node.detach() }
                layoutNodes?.fastForEach { it.node.detach() }
                throw failure
            }
            return MarkedChain(keyed.orEmpty(), components.orEmpty(), layoutNodes.orEmpty())
        }
    }
}

/**
 * Detaches every node of the modifier, both the component nodes and the layout nodes its parent interprets,
 * and restores every modified property to the value captured before the modifier touched it. Invoked by
 * [SwingNodeHolder] on release, reuse and deactivate, so a node's modifier state never carries over to
 * content it no longer drives.
 *
 * The whole chain comes apart in phases: on reuse and deactivation ([reset] set) every node runs
 * [SwingModifier.Node.onReset], then every node runs [SwingModifier.Node.onDetach], and only then is any node
 * marked detached. A measuring parent that received a layout node is then declared to without them, with the parent
 * data it received and nothing merged or checked, and lays the component out anew unless it is being released. Each
 * of these steps is attempted even when an earlier one throws; the first failure reaches the caller, with the later
 * ones suppressed on it, once the holder carries no modifier state.
 *
 * @param reset whether the holder is being reused or deactivated rather than released.
 */
internal fun SwingNodeHolder<*>.resetModifierState(reset: Boolean = false) {
    val state = modifierState
    val diagnostics = owner?.diagnostics
    val handedNodes = state?.handedNodes == true
    val unwinding = state?.standingInUnwindOrder().orEmpty()
    try {
        runEveryStep(
            { if (reset) unwinding.fastForEach { it.node.reset() } },
            { unwinding.detachAll(diagnostics) },
            { declaration.withdrawLayoutNodes()?.let { if (reset) it.revalidate() } },
        )
    } finally {
        state?.records?.clear()
        notifyDeclaredNodes(chainChanged = true, handedNodes)
        modifierState = null
    }
}

/**
 * Cheap to throw: a cancellation reaching every coroutine a detached node's [SwingModifier.Node.coroutineScope]
 * ran costs no stack trace, matching AndroidX's `PlatformOptimizedCancellationException`.
 */
internal class ModifierNodeDetachedCancellationException : CancellationException("The modifier node was detached") {
    override fun fillInStackTrace(): Throwable = this
}
