package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.modifier.ComponentNodeRecord
import org.jetbrains.compose.swing.modifier.ComponentNodeType
import org.jetbrains.compose.swing.modifier.NodeRecord
import org.jetbrains.compose.swing.modifier.ParentLayoutNodeRecord
import org.jetbrains.compose.swing.modifier.ParentLayoutNodeType
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.WriteMode
import org.jetbrains.compose.swing.modifier.attachElement
import org.jetbrains.compose.swing.modifier.detachAndRemove
import org.jetbrains.compose.swing.modifier.diffKeyedElements
import org.jetbrains.compose.swing.modifier.handDeclaredNodes
import org.jetbrains.compose.swing.modifier.rewriteRecordsFrom
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.node.receivesAllComponents
import org.jetbrains.compose.swing.node.restyleBelow
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component

/**
 * Creates and publishes a style layout slot before its first attachment callback.
 *
 * @param holder the component whose modifier state holds the layout slot.
 * @param declaringNode the style that declares and detaches this layout node.
 * @param element the layout element the slot is created for.
 * @param diagnostics the callbacks for declaration writes and restoration, or `null` when absent.
 */
private fun attachLayoutNode(
    holder: SwingNodeHolder<*>,
    declaringNode: StyleNode,
    element: ParentLayoutNodeElement<*>,
    diagnostics: SwingCompositionDiagnostics?,
) {
    val state = checkNotNull(holder.modifierState)
    val record = ParentLayoutNodeRecord.mark(element, holder, element.key, declaringNode)
    record.runAttachLifecycle(state, diagnostics)
}

/**
 * One component style declaration: [kind]'s default style and then [style], run against [kind]'s scope built over
 * [state].
 */
internal open class StyleElement<S : ComponentStyleScope, T>(
    val kind: SwingStyleKind<S, T>,
    val style: SwingStyle<S>,
    val state: T,
) : SwingModifier.NodeElement<Component, StyleNode>() {
    internal fun atOrdinal(ordinal: Int): StyleElement<S, T> =
        if (ordinal == 0) this else OrderedStyleElement(kind, style, state, ordinal)

    /** Whether this is the component's first style declaration, the one that applies [AllComponents]. */
    internal open val isFirstOfComponent: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    /** Each property the style writes is a slot of its own, held to its own policy. */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override val heldProperties: Set<String> get() = emptySet()

    // A style's property set is known only after its body runs and can change on the next resolve.
    override fun affectsProperties(names: Set<String>): Boolean = names.isNotEmpty()

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
     * Runs the styles provided for [AllComponents] where this is the component's first declaration, then [kind]'s
     * default style and then [style], and returns the scope they ran against: [reusable], the scope of a run with this
     * kind and state, or `null` where one is built over what [base] returns. The [collector] enforces the kind's write
     * restriction only while its default runs.
     *
     * @param reusable a scope previously created for this kind and state, or `null` to create one.
     * @param collector collects declarations and enforces the default style's restrictions.
     * @param base supplies the component scope when creating a scope for this kind.
     */
    inline fun execute(
        reusable: ComponentStyleScope?,
        collector: StyleCollector,
        base: () -> NestedStyleProviderScope,
    ): ComponentStyleScope {
        // A reusable scope was built by a kind that builds only an S.
        @Suppress("UNCHECKED_CAST")
        val scope = (reusable as S?) ?: kind.scope(base(), state)
        collector.applyDefault(kind, scope, appliesAllComponents = isFirstOfComponent)
        with(style) { scope.applyStyle() }
        return scope
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is StyleElement<*, *> && kind === other.kind && style == other.style && state === other.state)

    override fun hashCode(): Int =
        31 * (31 * System.identityHashCode(kind) + style.hashCode()) + System.identityHashCode(state)
}

private class OrderedStyleElement<S : ComponentStyleScope, T>(
    kind: SwingStyleKind<S, T>,
    style: SwingStyle<S>,
    state: T,
    ordinal: Int,
) : StyleElement<S, T>(kind, style, state) {
    override val key: Any = StyleKey(ordinal)

    override val isFirstOfComponent: Boolean get() = false
}

private data class StyleKey(
    val ordinal: Int,
)

/**
 * Resolves a component's style and writes each property it sets through a property slot of its own, diffed the
 * way the keyed slots of a modifier are: an equal property is reused, a changed one written, and one that leaves
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
    internal var previousStyle: StyleNode? = null
    internal var nextStyle: StyleNode? = null

    private var properties: StyleableProperties? = null

    override fun diagnosticsFor(given: SwingCompositionDiagnostics?): SwingCompositionDiagnostics? = null

    /**
     * Whether the last resolve read a composition local through the style. A replaced local map refreshes only
     * such a node: a change of a dynamic local the node reads already resolves it again through its observed reads.
     */
    internal var readsLocal = false
        private set

    override val refreshesOnLocalMap: Boolean get() = readsLocal

    override val holdsLocalConsumers: Boolean
        get() = readsLocal || componentReadsLocals || layoutRefreshesOnLocalMap

    private val componentReadsLocals: Boolean
        get() {
            val records = holder?.modifierState?.records ?: return false
            return records.fastFirstOrNull {
                it.isStandingFor(ComponentNodeType, this) && it.node.refreshesOnLocalMap
            } != null
        }

    /** Whether a change to an observed input invalidated the last resolution. */
    internal var isStale = false
        private set

    /** Records that the style reads a composition local on the resolve running. */
    fun readLocal() {
        readsLocal = true
    }

    /** Whether the last resolve ran under observed reads, so a resolve that reads nothing clears them once. */
    private var observed = false

    /** Whether the last resolve provided a nested style key or set a style local. Read without observation. */
    internal val providesStyles: Boolean
        get() =
            Snapshot.withoutReadObservation {
                (publication?.value ?: ProvidedStyles.Empty) !== ProvidedStyles.Empty
            }

    /** Whether the last resolve provided a style for [AllComponents]. Read without observation. */
    internal val providesAllComponents: Boolean
        get() = Snapshot.withoutReadObservation { publication?.value?.nested?.containsKey(AllComponents) == true }

    /** The parent-layout values declared by the last layout reconciliation. */
    internal var declaredLayout: List<ParentLayoutElement> = emptyList()
        private set

    /** [declaredLayout] with node declarations replaced by the shared store's retained nodes. */
    internal val layoutElements: List<ParentLayoutElement>
        get() {
            if (declaredLayout.isEmpty()) return declaredLayout
            return declaredLayout.mapNotNull {
                if (it is ParentLayoutNodeElement<*>) {
                    retainedLayoutNode(it)
                } else {
                    it
                }
            }
        }

    /** Attached layout nodes in the order declared by this style. */
    internal val layoutNodes: List<ParentLayoutNode>
        get() =
            declaredLayout.mapNotNull { element ->
                if (element !is ParentLayoutNodeElement<*>) return@mapNotNull null
                retainedLayoutNode(element)?.takeIf { it.isAttached }
            }

    override var holdsLayoutNodes: Boolean = false
        private set

    /** Whether a layout slot's node reads composition locals. */
    internal val layoutRefreshesOnLocalMap: Boolean
        get() {
            val records = holder?.modifierState?.records ?: return false
            return records.fastFirstOrNull {
                it.isStandingFor(ParentLayoutNodeType, this) && it.node.refreshesOnLocalMap
            } != null
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

    /** The value descendants observe; null until this node first publishes a nested style or local. */
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

    /** The nested blocks and locals this node provides, read as a descendant resolves. */
    fun providedStyles(): ProvidedStyles = publication?.value ?: ProvidedStyles.Empty

    override fun onAttach() {
        val holder = checkNotNull(holder)
        requireKindTarget(kind, holder.component)
        // A style added to a node that has children already reaches them.
        if (holder.children.isNotEmpty()) {
            holder.restyleBelow()
        }
    }

    /**
     * Applies [element] to this node's slots under [mode]. Where [element] is the one the last resolve ran, nothing it
     * read has changed since and no composition local was read, the collected declarations still stand, so the slots
     * are refreshed under [mode] without collecting again. Otherwise the style resolves through observed reads.
     * A resolve that throws leaves no element behind, so the next one collects again and writes every slot.
     *
     * @param element the style declaration to apply.
     * @param mode whether equal declarations may be reused or must be written again.
     */
    fun resolve(
        element: StyleElement<*, *>,
        mode: WriteMode = WriteMode.ReuseEqual,
    ): Boolean {
        val holder = checkNotNull(holder) { "Node is not attached" }
        // The node is reused across kinds, so the requirement is that of the element last applied, not of the first.
        kind = element.kind
        requireKindTarget(kind, holder.component)
        val owner = checkNotNull(holder.owner) { "Node is not attached to a composition" }
        if (!isStale && !readsLocal && element == ownStyle) {
            properties?.refresh(mode)
            return ComponentNodeType.refresh(holder.modifierState?.records, holder, this, mode)
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
        isStale = false
        // Attached slots can reuse equal declarations when no earlier slot wrote.
        val updating = if (ownStyle != null) mode else WriteMode.RewriteEqual
        // A successful apply sets it again; an apply that throws leaves no element behind.
        ownStyle = null
        return try {
            // Only the style's own reads run it again; what a write reads while it lands on the component does not.
            Snapshot.withoutReadObservation {
                if (properties == null && collected?.declarations?.isNotEmpty() == true) {
                    properties = StyleableProperties()
                }
                applyCollected(element, collected, updating, publication?.value ?: ProvidedStyles.Empty, properties)
            }
        } finally {
            collected?.release()
        }
    }

    /**
     * Provides [element], what it [provides] and the [scope] it ran against. A state is written only where it
     * changes, so an equal resolve resolves no descendant again.
     *
     * @param element the successfully resolved declaration.
     * @param scope the scope used for this resolve, or `null` when no style ran.
     * @param provides the nested styles and locals descendants may consume.
     */
    internal fun updateProvidedStyles(
        element: StyleElement<*, *>,
        scope: ComponentStyleScope?,
        provides: ProvidedStyles,
    ) {
        ownStyle = element
        kindScope = scope
        val before = providesStyles
        val beforeAll = providesAllComponents
        val state = publication
        if (state == null) {
            if (provides !== ProvidedStyles.Empty) {
                publication = mutableStateOf(provides)
            }
        } else if (state.value !== provides) {
            state.value = provides
        }
        val after = provides !== ProvidedStyles.Empty
        // Descendants that resolved before this node published anything, or AllComponents, have not observed it yet.
        if (before != after || beforeAll != providesAllComponents) holder?.restyleBelow()
    }

    override fun onReset() {
        val records = holder?.modifierState?.records ?: return
        records.fastForEach { if (it.isStandingFor(ComponentNodeType, this)) it.node.reset() }
        records.fastForEach { if (it.isStandingFor(ParentLayoutNodeType, this)) it.node.reset() }
    }

    /** Marks this style stale and, when requested, resolves it against its current ancestors. */
    fun restyle(replay: Boolean = true) {
        isStale = true
        if (replay && isAttached) holder?.modifierState?.refreshLocalConsumer(this)
    }

    /**
     * Detaches every slot and clears what this node provided, even when a step throws. Property slots keep their
     * nodes across moves. The next resolve attaches the declared property slots again in declared order.
     * Layout declarations rebuild against the new parent on that resolve.
     * The first failure propagates, with the later ones suppressed on it.
     */
    override fun onDetach() {
        val diagnostics = holder?.owner?.diagnostics
        var failure: Throwable? = null
        val records = holder?.modifierState?.records
        records?.asReversed()?.fastForEach {
            if (it.isStandingFor(ComponentNodeType, this) && it.node.isAttached) {
                failure = collectFailure(failure) { it.runDetachLifecycle(diagnostics) }
            }
        }
        val departingLayout = ArrayList<NodeRecord<*, *>>()
        records?.fastForEach {
            if (it.isStandingFor(ParentLayoutNodeType, this) && it.node.isAttached) departingLayout.add(it)
        }
        departingLayout.fastForEach { failure = collectFailure(failure) { it.runDetachLifecycle(diagnostics) } }
        failure = collectFailure(failure) { properties?.detach() }
        records?.asReversed()?.fastForEach {
            if (it.isStandingFor(ComponentNodeType, this) && it.node.isAttached) {
                failure = collectFailure(failure) { it.node.detach() }
            }
        }
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
        holdsLayoutNodes = false
        observed = false
        // Clears what this node provided, so every descendant that read it resolves again.
        failure =
            collectFailure(failure) {
                val provides = providesStyles
                ownStyle = null
                kindScope = null
                publication?.value = ProvidedStyles.Empty
                if (provides) holder?.restyleBelow()
            }
        failure?.let { throw it }
    }

    /**
     * Reconciles layout nodes while publishing the incoming declaration even after a failed update.
     *
     * @param holder the component declaring the layout elements to its parent.
     * @param declared the replacement layout declarations in write order.
     * @param mode whether equal declarations may be reused or must be written again.
     */
    internal fun declareLayout(
        holder: SwingNodeHolder<*>,
        declared: List<ParentLayoutElement>,
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
        val previousNodes = layoutNodes
        var nodesChanged = false
        holder.declaration.restateStyleLayout(this) {
            var written = false
            try {
                state.records?.standing(ParentLayoutNodeType, this)?.fastForEach { record ->
                    if (!declared.any { it.key == record.key && it is ParentLayoutNodeElement<*> }) {
                        state.records?.detachAndRemove(record, diagnostics)
                        nodesChanged = true
                    }
                }
                declared.fastForEach { element ->
                    if (element is ParentLayoutNodeElement<*>) {
                        val record = state.records?.get(ParentLayoutNodeType, this, element.key)
                        when {
                            record == null -> {
                                attachLayoutNode(holder, this, element, diagnostics)
                                nodesChanged = true
                            }

                            !NodeRecord.canUpdate(record, element) -> {
                                state.records?.detachAndRemove(record, diagnostics)
                                attachLayoutNode(holder, this, element, diagnostics)
                                nodesChanged = true
                            }

                            !mode.reuse(record, element) -> {
                                record.updateTo(
                                    element,
                                    holder.component,
                                    diagnostics,
                                    WriteMode.ReuseEqual,
                                )
                                if (holder.declaration.parentReads(record.node)) written = true
                                nodesChanged = true
                            }
                        }
                    }
                }
            } finally {
                declaredLayout = elements
                holdsLayoutNodes = state.records?.fastFirstOrNull {
                    it.isStandingFor(ParentLayoutNodeType, this) && it.node.isAttached
                } != null
            }
            nodesChanged = nodesChanged || previousNodes != layoutNodes
            written
        }
        return nodesChanged
    }
}

/**
 * Runs the `update` of the first slot [from] picks again, and then of every slot declared after it, in declared
 * order, followed by modifier declarations after this style. Returns whether [from] picked a slot. A slot's node
 * reads its own composition locals, which the outer modifier's refresh does not reach.
 *
 * @param onInvalidates called when a rewritten node requires consumer invalidation.
 * @param from selects the first declaration to rewrite; later declarations are rewritten unconditionally.
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

/**
 * Whether resolving this element yields nothing: no style to run, a default that only nests, no key to apply. The
 * unstyled declaration applies only [AllComponents], so it yields nothing unless an ancestor provides that key.
 */
internal fun StyleElement<*, *>.collectsNothingUnder(holder: SwingNodeHolder<*>): Boolean =
    if (this === UnstyledComponent) {
        !holder.receivesAllComponents
    } else {
        style === SwingStyle && kind.defaultAppliesOnlyNestedStyles && holder.publisherAbove == null
    }

/** The one callback every observed style resolve shares, so the observer keeps a single scope map for them. */
private val OnStyleReadChanged: (StyleNode) -> Unit = { node ->
    node.restyle(replay = false)
    val holder = node.holder
    val batch = holder?.owner?.updateBatch
    if (holder != null && batch != null && holder.modifierState?.finished != false) {
        batch.holdForRestyle(holder)
    } else {
        node.restyle()
    }
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

/**
 * Applies collected property and layout declarations before publishing styles to descendants.
 *
 * @param element the declaration to retain after a successful apply.
 * @param collected the resolved declarations, or `null` when no style ran.
 * @param mode whether equal declarations may be reused or must be written again.
 * @param previous the previously published styles whose unchanged values may be retained.
 * @param table the retained descriptor properties, or `null` when none have been declared.
 */
private fun StyleNode.applyCollected(
    element: StyleElement<*, *>,
    collected: StyleCollection?,
    mode: WriteMode,
    previous: ProvidedStyles,
    table: StyleableProperties?,
): Boolean {
    val holder = checkNotNull(holder)
    val declarations = collected?.declarations.orEmpty()
    val state = checkNotNull(holder.modifierState)
    val properties = collected?.properties.orEmpty()
    val layoutElements = collected?.layoutElements.orEmpty()
    val detached = ComponentNodeType.hasDetached(state.records, this)
    var invalidates =
        if (detached) {
            table?.resolve(declarations, mode, this)
            attachNodes(holder, properties, holder.owner?.diagnostics)
        } else {
            holder.diffKeyedElements(properties, mode, this, { table?.rewriteReleased(it) }) {
                if (table?.resolve(declarations, mode, this) == true) WriteMode.RewriteEqual else mode
            }
        }
    val declaresLayout =
        declaredLayout.isNotEmpty() ||
            layoutElements.isNotEmpty() ||
            ParentLayoutNodeType.hasRecords(state.records, this)
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

/** The shared store's retained parent-layout node for [element], if any. */
private fun StyleNode.retainedLayoutNode(element: ParentLayoutNodeElement<*>): ParentLayoutNode? =
    holder
        ?.modifierState
        ?.records
        ?.get(ParentLayoutNodeType, this, element.key)
        ?.node

/** Reattaches retained style properties, dropping unattached records after any failed step. */
private fun StyleNode.attachNodes(
    holder: SwingNodeHolder<*>,
    properties: List<SwingModifier.NodeElement<*, *>>,
    diagnostics: SwingCompositionDiagnostics?,
): Boolean {
    val state = checkNotNull(holder.modifierState)
    val records = checkNotNull(state.records)
    records.standing(ComponentNodeType, this).fastForEach { record ->
        if (!properties.any { it.key == record.key }) {
            records.withdraw(record)
            record.clear()
        }
    }
    records.order.commit(this, properties)
    var invalidates = false
    try {
        properties.fastForEach { element ->
            val record = records.get(ComponentNodeType, this, element.key)
            val written =
                when {
                    record == null -> {
                        attachElement(element, holder, element.key, this)
                    }

                    !NodeRecord.canUpdate(record, element) -> {
                        records.detachAndRemove(record, diagnostics)
                        attachElement(element, holder, element.key, this)
                    }

                    else -> {
                        record.reattach(
                            element,
                            holder,
                            diagnostics,
                            state,
                        )
                    }
                }
            if (written) invalidates = true
        }
    } finally {
        records.standing(ComponentNodeType, this).fastForEach { record ->
            if (!record.node.isAttached) {
                records.withdraw(record)
                record.clear()
            }
        }
        records.order.commit(this, properties)
    }
    return invalidates
}
