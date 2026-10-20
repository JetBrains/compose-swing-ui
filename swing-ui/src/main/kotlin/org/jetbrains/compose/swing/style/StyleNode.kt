package org.jetbrains.compose.swing.style

import androidx.collection.ObjectList
import androidx.collection.emptyObjectList
import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.watchRestore
import org.jetbrains.compose.swing.core.watchWrite
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.modifier.ComponentNodeRecord
import org.jetbrains.compose.swing.modifier.ComponentNodeType
import org.jetbrains.compose.swing.modifier.NodeRecord
import org.jetbrains.compose.swing.modifier.ParentLayoutNodeRecord
import org.jetbrains.compose.swing.modifier.ParentLayoutNodeType
import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.PropertyRewrites
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.WriteMode
import org.jetbrains.compose.swing.modifier.attachElement
import org.jetbrains.compose.swing.modifier.diffKeyedElements
import org.jetbrains.compose.swing.modifier.handDeclaredNodes
import org.jetbrains.compose.swing.modifier.rewriteRecordsFrom
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.restyleBelow
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/** Creates and publishes a style layout slot before its first attachment callback. */
private fun attachLayoutNode(
    holder: SwingNodeHolder<*>,
    declaringNode: StyleNode,
    element: ParentLayoutNodeElement<*>,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val state = checkNotNull(holder.modifierState)
    val record = ParentLayoutNodeRecord.mark(element, holder, element.key, declaringNode)
    record.runAttachLifecycle(diagnostics, { state.insert(record) }, { state.records?.withdraw(record) })
}

/**
 * The style slot of a component: [kind]'s default style and then [style], run against [kind]'s scope built over
 * [state].
 */
internal class StyleElement<S : ComponentStyleScope, T>(
    val kind: SwingStyleKind<S, T>,
    val style: SwingStyle<S>,
    val state: T,
) : SwingModifier.NodeElement<Component, StyleNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    /** Each property the style writes is a slot of its own, held to its own policy. */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override val heldProperties: Set<String> get() = emptySet()

    override val name: String get() = "styleable"

    override val declaredValues: Map<String, Any?> get() = mapOf("style" to style, "kind" to kind, "state" to state)

    override fun create(): StyleNode = StyleNode(kind)

    override fun update(node: StyleNode) {
        node.resolve(this)
    }

    override fun update(
        node: StyleNode,
        mode: WriteMode,
    ): Boolean = node.resolve(this, mode)

    /**
     * Runs [kind]'s default style and then [style], and returns the scope they ran against: [reusable], the scope of
     * a run with this kind and state, or `null` where one is built over what [base] returns. [setOnlyNests] sets
     * the collector's nesting-default check and returns its previous value: it holds [kind]'s setting only while
     * the default runs, so the style runs unchecked, and the previous value returns afterwards.
     */
    inline fun execute(
        reusable: ComponentStyleScope?,
        setOnlyNests: (Boolean) -> Boolean,
        base: () -> NestedStyleProviderScope,
    ): ComponentStyleScope {
        // A reusable scope was built by a kind that builds only an S.
        @Suppress("UNCHECKED_CAST")
        val scope = (reusable as S?) ?: kind.scope(base(), state)
        val outer = setOnlyNests(kind.defaultOnlyNests)
        try {
            with(kind.default) { scope.applyStyle() }
            setOnlyNests(false)
            with(style) { scope.applyStyle() }
        } finally {
            setOnlyNests(outer)
        }
        return scope
    }

    /** Refuses a second styleable: a component takes one, so [previous] is a declaration it cannot share with. */
    override fun checkPreviousDeclaration(previous: SwingModifier.Element?) {
        require(previous == null) {
            "A component can have only one styleable. If the component takes a style parameter, " +
                "pass the style there; otherwise merge them into one styleable."
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is StyleElement<*, *> && kind === other.kind && style == other.style && state === other.state)

    override fun hashCode(): Int =
        31 * (31 * System.identityHashCode(kind) + style.hashCode()) + System.identityHashCode(state)
}

/**
 * Resolves a component's style and writes each property it sets through a property slot of its own, diffed the
 * way the keyed slots of a modifier are: an equal property is adopted, a changed one written, and one that leaves
 * the style restored. A parent-layout element the style sets is declared to the parent after the modifier's own,
 * a node-backed one through a layout slot of its own.
 *
 * A resolve with a style to run or a providing ancestor to read runs inside the node's observed reads, so a
 * snapshot state the style reads, including what a providing ancestor provides, resolves it again. A resolve that
 * can collect nothing builds and observes nothing.
 *
 * The node holds the nested blocks and style locals its last resolve provided to its descendants.
 */
internal class StyleNode(
    private var kind: SwingStyleKind<*, *>,
) : SwingModifier.ComponentNode<Component>() {
    private val properties = StyleableProperties()

    override fun diagnosticsFor(given: SwingCompositionDiagnostics?): SwingCompositionDiagnostics? = null

    /**
     * Whether the last resolve read a composition local through the style. A replaced local map refreshes only
     * such a node: a change of a dynamic local the node reads already resolves it again through its observed reads.
     */
    internal var readsLocal = false
        private set

    override val refreshesOnLocalMap: Boolean get() = readsLocal

    override val holdsLocalConsumers: Boolean
        get() =
            readsLocal ||
                holder?.modifierState?.records.let { records ->
                    var reads = false
                    records?.forEach(ComponentNodeType, this) { if (it.node.refreshesOnLocalMap) reads = true }
                    reads
                } ||
                layoutRefreshesOnLocalMap

    /** Whether something the last resolve read has changed since, so its collected declarations no longer stand. */
    private var stale = false

    /** Records that the style reads a composition local on the resolve running. */
    fun readLocal() {
        readsLocal = true
    }

    /** Whether the last resolve ran under observed reads, so a resolve that reads nothing clears them once. */
    private var observed = false

    /** Whether the last resolve provided a nested style key or set a style local. Read without observation. */
    internal val providesStyles: Boolean get() = provided !== ProvidedStyles.Empty

    /** The parent-layout values declared by the last layout reconciliation. */
    internal var declaredLayout: List<ParentLayoutElement> = emptyList()
        private set

    /** [declaredLayout] with node declarations replaced by the shared store's retained nodes. */
    internal val layoutElements: List<ParentLayoutElement>
        get() {
            if (declaredLayout.isEmpty()) return declaredLayout
            return declaredLayout.mapNotNull {
                if (it is ParentLayoutNodeElement<*>) {
                    holder
                        ?.modifierState
                        ?.records
                        ?.get(ParentLayoutNodeType, this, it.key)
                        ?.node as? ParentLayoutNode
                } else {
                    it
                }
            }
        }

    override val holdsLayoutNodes: Boolean
        get() {
            var attached = false
            holder?.modifierState?.records?.forEach(ParentLayoutNodeType, this) {
                if (it.node.isAttached) attached = true
            }
            return attached
        }

    /** Whether a layout slot's node reads composition locals. */
    internal val layoutRefreshesOnLocalMap: Boolean
        get() {
            var reads = false
            holder?.modifierState?.records?.forEach(ParentLayoutNodeType, this) {
                if (it.node.refreshesOnLocalMap) reads = true
            }
            return reads
        }

    /** The style element of the last resolve. */
    private var ownStyle: StyleElement<*, *>? = null

    /** The scope of [ownStyle]'s kind and state, built over the collector of the composition this node is in. */
    private var kindScope: ComponentStyleScope? = null

    /** The scope of the last resolve where [element] has its kind and state; `null` where one must be built. */
    fun reusableScope(element: StyleElement<*, *>): ComponentStyleScope? {
        val own = ownStyle ?: return null
        return if (own.kind === element.kind && own.state === element.state) kindScope else null
    }

    /**
     * What the last resolve provided to descendants, which they read through [publication] once it exists.
     */
    private var provided: ProvidedStyles = ProvidedStyles.Empty

    /**
     * The one value descendants observe, holding [provided]. Created by [ensureProvidedStyles] or by a resolve that
     * provides while the node has children, so a node no child can read holds none.
     */
    private var publication: MutableState<ProvidedStyles>? = null

    /** The local map is observed only by styles that read a local from this node's context. */
    private var localMapState: MutableState<CompositionLocalMap>? = null

    /**
     * The composition locals at this node, observed when a nested style reads them. The first read uses the
     * holder's current map; writes update the observed map only after that first read.
     */
    var localMap: CompositionLocalMap
        get() {
            val map =
                localMapState ?: mutableStateOf(checkNotNull(holder).compositionLocalMap).also { localMapState = it }
            return map.value
        }
        set(value) {
            localMapState?.value = value
        }

    /** Makes this node's published styles observable before a descendant first resolves. */
    fun ensureProvidedStyles() {
        providedStyles
    }

    /** The nested blocks and locals this node provides, read as a descendant resolves. */
    val providedStyles: ProvidedStyles
        get() = (publication ?: mutableStateOf(provided).also { publication = it }).value

    override fun onAttach() {
        val holder = checkNotNull(holder)
        requireKindTarget(kind, holder.component)
        // A style added to a node that has children already reaches them.
        if (holder.children.isNotEmpty()) {
            ensureProvidedStyles()
            holder.restyleBelow()
        }
    }

    /**
     * Applies [element] to this node's slots under [mode]. Where [element] is the one the last resolve ran, nothing it
     * read has changed since and no composition local was read, the collected declarations still stand, so the slots
     * are refreshed under [mode] without collecting again. Otherwise the style resolves through observed reads.
     * A resolve that throws leaves no element behind, so the next one collects again and writes every slot.
     */
    fun resolve(
        element: StyleElement<*, *>,
        mode: WriteMode = WriteMode.AdoptEqual,
    ): Boolean {
        val holder = checkNotNull(holder) { "Node is not attached" }
        // The node is reused across kinds, so the requirement is that of the element last applied, not of the first.
        kind = element.kind
        requireKindTarget(kind, holder.component)
        val owner = checkNotNull(holder.owner) { "Node is not attached to a composition" }
        if (!stale && !readsLocal && element == ownStyle) {
            properties.refresh(mode)
            var invalidates = false
            holder.modifierState?.records?.forEach(ComponentNodeType, this) {
                if (it.refresh(holder.component, owner.diagnostics, mode)) invalidates = true
            }
            return invalidates
        }
        val collector = owner.styleCollector
        readsLocal = false
        val collected =
            if (element.collectsNothingUnder(holder)) {
                // Nothing is collected, so no scope or observer is built and an earlier observer is dropped.
                if (observed) owner.snapshotObserver.clear(this)
                observed = false
                null
            } else {
                observed = true
                collectObserved(collector, element, holder)
            }
        // A collection that threw left the node stale, so the next resolve collects again.
        stale = false
        // Attached slots can adopt equal declarations when no earlier slot wrote.
        val updating = if (ownStyle != null) mode else WriteMode.RewriteEqual
        // A successful apply sets it again; an apply that throws leaves no element behind.
        ownStyle = null
        return try {
            // Only the style's own reads run it again; what a write reads while it lands on the component does not.
            Snapshot.withoutReadObservation {
                applyCollected(element, collected, updating, provided, properties)
            }
        } finally {
            collected?.release()
        }
    }

    /**
     * Provides [element], what it [provides] and the [scope] it ran against. A state is written only where it
     * changes, so an equal resolve resolves no descendant again.
     */
    internal fun updateProvidedStyles(
        element: StyleElement<*, *>,
        scope: ComponentStyleScope?,
        provides: ProvidedStyles,
    ) {
        ownStyle = element
        kindScope = scope
        val before = providesStyles
        provided = provides
        val state = publication
        if (state == null) {
            if (provides !== ProvidedStyles.Empty && holder?.children?.isNotEmpty() == true) {
                publication = mutableStateOf(provides)
            }
        } else if (state.value !== provides) {
            state.value = provides
        }
        val after = provides !== ProvidedStyles.Empty
        // Descendants that resolved before this node published anything have not observed it yet.
        if (before != after) holder?.restyleBelow()
    }

    override fun onReset() {
        val records = holder?.modifierState?.records ?: return
        records.forEach(ComponentNodeType, this) { it.node.reset() }
        records.forEach(ParentLayoutNodeType, this) { it.node.reset() }
    }

    /** Resolves this node's style against its current ancestors. */
    fun restyle() {
        stale = true
        if (isAttached) holder?.modifierState?.refreshLocalConsumer(this)
    }

    /**
     * Detaches every slot and clears what this node provided, even when a step throws. Property slots keep their
     * nodes across moves. The next resolve attaches the declared property slots again in declared order.
     * Layout declarations rebuild against the new parent on that resolve.
     * The first failure propagates, with the later ones suppressed on it.
     */
    override fun onDetach() {
        val diagnostics = holder?.owner?.diagnostics
        var failure = collectFailure(null) { properties.detach() }
        val records = holder?.modifierState?.records
        records?.forEachReversed(ComponentNodeType, this) {
            if (it.node.isAttached) failure = collectFailure(failure) { it.runDetachLifecycle(diagnostics) }
        }
        records?.forEachReversed(ComponentNodeType, this) {
            if (it.node.isAttached) failure = collectFailure(failure) { it.node.detach() }
        }
        val departingLayout = ArrayList<NodeRecord<*, *>>()
        records?.forEach(ParentLayoutNodeType, this) { if (it.node.isAttached) departingLayout.add(it) }
        departingLayout.fastForEach { failure = collectFailure(failure) { it.runDetachLifecycle(diagnostics) } }
        departingLayout.fastForEach { failure = collectFailure(failure) { it.node.detach() } }
        if (departingLayout.isNotEmpty()) failure = collectFailure(failure) { holder?.handDeclaredNodes() }
        if (declaredLayout.isNotEmpty()) {
            failure =
                collectFailure(failure) {
                    holder?.declaration?.restateStyleLayout(this) {
                        declaredLayout = emptyList()
                        false
                    }
                }
        }
        declaredLayout = emptyList()
        records?.clear(ParentLayoutNodeType, this)
        observed = false
        // Clears what this node provided, so every descendant that read it resolves again.
        failure =
            collectFailure(failure) {
                val provides = providesStyles
                ownStyle = null
                kindScope = null
                provided = ProvidedStyles.Empty
                publication?.value = ProvidedStyles.Empty
                if (provides) holder?.restyleBelow()
            }
        failure?.let { throw it }
    }

    /** Reconciles layout nodes while publishing the incoming declaration even after a failed update. */
    internal fun declareLayout(
        holder: SwingNodeHolder<*>,
        declared: ObjectList<ParentLayoutElement>,
        mode: WriteMode,
    ): Boolean {
        val elements =
            when (declared.size) {
                0 -> emptyList()
                1 -> listOf(declared[0])
                else -> Array(declared.size) { declared[it] }.asList()
            }
        holder.checkDeclaredPlacement(null, elements)
        holder.declaration.checkParentElementsAccepted(elements)
        val diagnostics = holder.owner?.diagnostics
        val state = checkNotNull(holder.modifierState)
        val previousNodes = layoutElements.filterIsInstance<ParentLayoutNode>()
        var nodesChanged = false
        holder.declaration.restateStyleLayout(this) {
            var written = false
            try {
                state.records?.forEach(ParentLayoutNodeType, this) { record ->
                    if (!declared.any { it.key == record.key && it is ParentLayoutNodeElement<*> }) {
                        state.records?.detachAndRemove(record, diagnostics)
                        nodesChanged = true
                    }
                }
                declared.forEach { element ->
                    if (element is ParentLayoutNodeElement<*>) {
                        val record = state.records?.get(ParentLayoutNodeType, this, element.key)
                        when {
                            record == null -> {
                                attachLayoutNode(holder, this, element, diagnostics)
                                nodesChanged = true
                            }

                            !record.canRebind(element) -> {
                                state.records?.detachAndRemove(record, diagnostics)
                                attachLayoutNode(holder, this, element, diagnostics)
                                nodesChanged = true
                            }

                            !mode.adopt(record, element) -> {
                                record.rebindAndWrite(
                                    element,
                                    holder.component,
                                    diagnostics,
                                    WriteMode.AdoptEqual,
                                )
                                if (holder.declaration.parentReads(record.node as ParentLayoutNode)) written = true
                                nodesChanged = true
                            }
                        }
                    }
                }
            } finally {
                declaredLayout = elements
            }
            nodesChanged = nodesChanged || previousNodes != layoutElements.filterIsInstance<ParentLayoutNode>()
            written
        }
        return nodesChanged
    }
}

/**
 * Runs the `update` of the first slot [from] picks again, and then of every slot declared after it, in declared
 * order, followed by modifier declarations after this style. Returns whether [from] picked a slot. A slot's node
 * reads its own composition locals, which the outer modifier's refresh does not reach.
 */
internal fun StyleNode.rewriteSlotsFrom(
    onInvalidates: () -> Unit = {},
    from: (ComponentNodeRecord<*, *>) -> Boolean,
): Boolean {
    val holder = holder
    val records = holder?.modifierState?.records
    if (holder == null || records == null) return false
    val diagnostics = holder.owner?.diagnostics
    val rewritten =
        rewriteRecordsFrom(records, this, except = null, { record, mode ->
            if (record.refresh(holder.component, diagnostics, mode)) onInvalidates()
        }, from)
    if (rewritten) {
        rewriteRecordsFrom(records, null, except = null, { record, mode ->
            if (record.node !== this && record.refresh(holder.component, diagnostics, mode)) onInvalidates()
        }) { it.node === this }
    }
    return rewritten
}

/** Collects [element] with [collector], recording the reads so a change of one resolves it again. */
private fun StyleNode.collectObserved(
    collector: StyleCollector,
    element: StyleElement<*, *>,
    holder: SwingNodeHolder<*>,
): StyleCollection {
    var collected: StyleCollection? = null
    // From the publisher above, so a provider never receives its own nested styles.
    observeReads(OnStyleReadChanged) { collected = collector.collect(this, element, holder.publisherAbove) }
    return checkNotNull(collected) { "The observed collect did not run" }
}

/** Whether resolving this element yields nothing: no style to run, a default that only nests, no key to apply. */
private fun StyleElement<*, *>.collectsNothingUnder(holder: SwingNodeHolder<*>): Boolean =
    style === SwingStyle && kind.readsNothingByDefault && holder.publisherAbove == null

/** The one callback every observed style resolve shares, so the observer keeps a single scope map for them. */
private val OnStyleReadChanged: (StyleNode) -> Unit = StyleNode::restyle

/** Runs [step], and returns [failure], or what [step] threw where [failure] is `null`; a later throw is suppressed. */
private inline fun collectFailure(
    failure: Throwable?,
    step: () -> Unit,
): Throwable? =
    try {
        step()
        failure
    } catch (
        // A teardown step runs whatever the step before it threw; the first failure is rethrown once all have run.
        @Suppress("TooGenericExceptionCaught") thrown: Throwable,
    ) {
        failure?.apply { if (thrown !== this) addSuppressed(thrown) } ?: thrown
    }

/** The nested blocks and style locals a node provides. Each key's blocks are observed independently. */
internal class ProvidedStyles private constructor(
    val nested: Map<NestedStyleKey<*>, MutableState<List<SwingStyle<ComponentStyleScope>>>>,
    val locals: Map<String, ProvidedLocal>,
) {
    companion object {
        /** Provides no nested styles or style locals. */
        val Empty = ProvidedStyles(emptyMap(), emptyMap())

        /**
         * Publishes [nested] and [locals], reusing [previous]'s state for retained keys and updating their styles.
         * Returns [previous] when its keys and locals are unchanged, or [Empty] when both inputs are empty.
         */
        fun of(
            nested: Map<NestedStyleKey<*>, List<SwingStyle<ComponentStyleScope>>>,
            locals: Map<String, ProvidedLocal>,
            previous: ProvidedStyles,
        ): ProvidedStyles {
            if (nested.isEmpty() && locals.isEmpty()) return Empty
            val sameKeys = previous.nested.keys == nested.keys
            val sameLocals = previous.locals == locals
            val blocks =
                if (sameKeys) {
                    previous.nested
                } else {
                    nested.mapValues { (key, styles) -> previous.nested[key] ?: mutableStateOf(styles.toList()) }
                }
            for ((key, styles) in nested) {
                val state = blocks.getValue(key)
                if (state.value != styles) state.value = styles.toList()
            }
            return if (sameKeys && sameLocals) {
                previous
            } else {
                ProvidedStyles(blocks, if (sameLocals) previous.locals else LinkedHashMap(locals))
            }
        }
    }
}

/** Applies collected property and layout declarations before publishing styles to descendants. */
private fun StyleNode.applyCollected(
    element: StyleElement<*, *>,
    collected: StyleCollection?,
    mode: WriteMode,
    previous: ProvidedStyles,
    table: StyleableProperties,
): Boolean {
    val holder = checkNotNull(holder)
    val declarations = collected?.declarations ?: emptyObjectList()
    val propertyMode = if (table.resolve(declarations, mode, this)) WriteMode.RewriteEqual else mode
    val state = checkNotNull(holder.modifierState)
    val properties = collected?.properties ?: emptyObjectList()
    val layoutElements = collected?.layoutElements ?: emptyObjectList()
    var detached = false
    state.records?.forEach(ComponentNodeType, this) { if (!it.node.isAttached) detached = true }
    var invalidates =
        if (detached) {
            attachNodes(holder, properties, holder.owner?.diagnostics)
        } else {
            holder.diffKeyedElements(properties, propertyMode, this, table.rewriteReleased)
        }
    var declaresLayout = declaredLayout.isNotEmpty() || layoutElements.isNotEmpty()
    state.records?.forEach(ParentLayoutNodeType, this) { declaresLayout = true }
    if (declaresLayout && declareLayout(holder, layoutElements, mode)) invalidates = true
    val provides =
        if (collected == null) {
            ProvidedStyles.Empty
        } else {
            ProvidedStyles.of(collected.nested, collected.locals, previous)
        }
    updateProvidedStyles(element, collected?.scope, provides)
    return invalidates
}

/** Reattaches retained style properties, dropping unattached records after any failed step. */
private fun StyleNode.attachNodes(
    holder: SwingNodeHolder<*>,
    properties: ObjectList<SwingModifier.Element>,
    diagnostics: SwingCompositionDiagnostics?,
): Boolean {
    val state = checkNotNull(holder.modifierState)
    val records = checkNotNull(state.records)
    records.forEach(ComponentNodeType, this) { record ->
        if (!properties.any { (it as SwingModifier.NodeElement<*, *>).key == record.key }) {
            records.withdraw(record)
            record.clear()
        }
    }
    records.commitOrder(this, properties)
    var invalidates = false
    try {
        properties.forEach { incoming ->
            val element = incoming as SwingModifier.NodeElement<*, *>
            val record = records.get(ComponentNodeType, this, element.key)
            val written =
                when {
                    record == null -> {
                        attachElement(element, holder, element.key, this)
                    }

                    !record.canRebind(element) -> {
                        records.detachAndRemove(record, diagnostics)
                        attachElement(element, holder, element.key, this)
                    }

                    else -> {
                        (record as ComponentNodeRecord<*, *>).reattach(
                            element,
                            holder,
                            diagnostics,
                        ) { records.withdraw(record) }
                    }
                }
            if (written) invalidates = true
        }
    } finally {
        records.forEach(ComponentNodeType, this) { record ->
            if (!record.node.isAttached) {
                records.withdraw(record)
                record.clear()
            }
        }
        records.commitOrder(this, properties)
    }
    return invalidates
}

/** Reasserts modifier declarations whose properties a departing style declaration restored. */
private fun StyleNode.reassertReleased(names: Set<String>) {
    holder?.modifierState?.rewritePropertySlotsFrom(component, holder?.owner?.diagnostics, except = this) { record ->
        record.element.heldProperties.any { it in names }
    }
}

/** Retains descriptor declarations in their current write order. */
internal class StyleableProperties {
    private var entries: Array<PropertyEntry?> = arrayOfNulls(0)
    private var size = 0

    fun resolve(
        incoming: ObjectList<PropertyElement<*, *>>,
        mode: WriteMode,
        style: StyleNode,
    ): Boolean {
        var written = detachDeparted(incoming, style)
        var rewriting = written || mode.writes(Unit, Unit)
        for (i in 0 until incoming.size) {
            val element = incoming[i]
            var found = i
            while (found < size && entries[found]?.element?.key != element.key) found++
            val entry = if (found == size) PropertyEntry(style, element) else checkNotNull(entries[found])
            if (found == size) {
                if (size == entries.size) entries = entries.copyOf(maxOf(1, size * 2))
                entries.copyInto(entries, i + 1, i, size)
                size++
                rewriting = true
            } else if (found != i) {
                entries.copyInto(entries, i + 1, i, found)
                rewriting = true
            }
            entries[i] = entry
            val writes = rewriting || mode.writes(entry.element, element)
            entry.element = element
            if (!entry.attached || writes) {
                if (entry.attached) entry.write() else entry.attach()
                written = true
                rewriting = true
            }
        }
        return written
    }

    private fun detachDeparted(
        incoming: ObjectList<PropertyElement<*, *>>,
        style: StyleNode,
    ): Boolean {
        var released: Set<String>? = null
        var failure: Throwable? = null
        try {
            for (i in size - 1 downTo 0) {
                val entry = checkNotNull(entries[i])
                if (incoming.any { it.key == entry.element.key }) continue
                released = released?.plus(entry.element.heldProperties) ?: entry.element.heldProperties
                failure = collectFailure(failure) { entry.detach() }
                entries.copyInto(entries, i, i + 1, size)
                entries[--size] = null
            }
        } finally {
            released?.let { names -> failure = collectFailure(failure) { style.reassertReleased(names) } }
        }
        failure?.let { throw it }
        return released != null
    }

    /**
     * Writes again, in declaration order, every attached entry from the first one holding a released property name,
     * so a later entry keeps its precedence over an earlier one it overlaps.
     */
    val rewriteReleased: (Set<String>) -> Unit = { names ->
        var rewriting = false
        for (i in 0 until size) {
            val entry = checkNotNull(entries[i])
            if (!rewriting) rewriting = entry.element.heldProperties.any { it in names }
            if (rewriting && entry.attached) entry.write()
        }
    }

    fun refresh(mode: WriteMode) {
        if (mode.writes(Unit, Unit)) for (i in 0 until size) entries[i]?.takeIf { it.attached }?.write()
    }

    fun detach() {
        var failure: Throwable? = null
        for (i in size - 1 downTo 0) failure = collectFailure(failure) { entries[i]?.detach() }
        failure?.let { throw it }
    }
}

/** Holds a style declaration's original values until it leaves the component. */
internal class PropertyEntry(
    private val owner: StyleNode,
    var element: PropertyElement<*, *>,
) : PropertyChangeListener {
    private var capture: PropertyCaptures.Capture<*, *>? = null
    private var overwritten: Array<PropertyCaptures.Capture<*, *>?>? = null
    private val hasCaptures: Boolean get() = capture != null || overwritten != null
    var attached = false
        private set
    private var rewrites: PropertyRewrites? = null
    private var place = 0L

    fun attach() {
        val holder = checkNotNull(owner.holder)
        val component = holder.component
        val state = checkNotNull(holder.modifierState)
        val failure =
            collectFailure(null) {
                capture = element.capture(component, state.captures())
                val additional = arrayOfNulls<PropertyCaptures.Capture<*, *>>(element.overwrittenCount)
                overwritten = additional
                for (index in additional.indices) {
                    additional[index] = element.captureOverwritten(index, component, state.captures())
                }
                element.rewriteOn?.let { name ->
                    rewrites = state.rewrites()
                    place = 0L
                    component.addPropertyChangeListener(name, this)
                }
                attached = true
            }
        if (failure != null) {
            collectFailure(failure) { detach() }
            throw failure
        }
        write()
    }

    fun write() {
        val holder = checkNotNull(owner.holder)
        holder.owner?.diagnostics.watchWrite(holder.component, this, element) {
            val ordering = rewrites
            if (ordering == null) {
                element.writeTo(holder.component)
            } else {
                place = ordering.next()
                ordering.write(place) { element.writeTo(holder.component) }
            }
        }
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        val ordering = rewrites ?: return
        if (ordering.answers(place)) {
            val holder = checkNotNull(owner.holder)
            ordering.write(place) { element.writeTo(holder.component) }
        }
    }

    fun detach() {
        if (!attached && !hasCaptures && rewrites == null) return
        val holder = checkNotNull(owner.holder)
        attached = false
        var failure: Throwable? = null
        if (rewrites != null) {
            rewrites = null
            failure = collectFailure(failure) { holder.component.removePropertyChangeListener(element.rewriteOn, this) }
        }
        holder.owner?.diagnostics.watchRestore(holder.component, this) {
            val primary = capture
            capture = null
            failure = collectFailure(failure) { primary?.release() }
            val additional = overwritten
            overwritten = null
            additional?.forEach { held -> failure = collectFailure(failure) { held?.release() } }
        }
        failure?.let { throw it }
    }
}
