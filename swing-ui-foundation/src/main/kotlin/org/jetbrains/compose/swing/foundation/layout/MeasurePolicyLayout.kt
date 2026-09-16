package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.util.EnumSet
import java.util.IdentityHashMap
import java.util.function.BiConsumer

/**
 * The layout manager a [MeasurePolicy] drives: it holds one [Measurable] per child, answers the three
 * extents `LayoutManager2` asks for from the policy, and lays the container out by what the policy
 * measured.
 *
 * `preferredLayoutSize` asks the policy's [MeasurePolicy.maxIntrinsicWidth] and
 * [MeasurePolicy.maxIntrinsicHeight], `minimumLayoutSize` its min ones, and `layoutContainer` runs
 * [MeasurePolicy.measure]. Of the three, only `layoutContainer` has a rectangle for the policy to divide
 * among its children. The other rectangle comes from outside: a parent measuring this container offers
 * one through [ChildMeasurables.measuredSize], which places nothing.
 *
 * The policy works inside the container's insets: it is handed the inner extent and places children
 * relative to the inner rectangle's own origin.
 *
 * @property policy the policy this container is laid out by, written in place when a later pass of [Layout] hands
 *   a different one.
 * @property parentDataProtocol the parent data [policy] reads, the same instance while the layout is composed.
 */
internal class MeasurePolicyLayout(
    var policy: MeasurePolicy,
    val parentDataProtocol: LayoutParentDataProtocol? = null,
) : LayoutManager2,
    MeasurementLayoutManager {
    /** One measurable per child, and the two ways the policy is run over them. */
    val measurables: ChildMeasurables = ChildMeasurables(this)

    /** The receiver the policy places its children in, rewritten at the start of each layout pass. */
    private val placementScope = InnerPlacementScope()

    /** The node every answer records its reads under while the panel's [LayoutObservation] is attached. */
    internal var node: LayoutObservationNode? = null

    /** What [placeBlock] places. */
    private lateinit var placed: MeasureResult

    // Stored once, so observing a placement allocates nothing per pass.
    private val placeBlock: () -> Unit = {
        val previous = measurables.enter(LayoutState.LayingOut)
        try {
            with(placed) { placementScope.placeChildren() }
        } finally {
            measurables.layoutState = previous
        }
    }

    /**
     * Records what [component] was registered under, for its policy to read back through
     * [Measurable.parentData], once [parentDataProtocol] has accepted it.
     */
    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        measurables.updateParentData(component, constraints)
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    /** Records every declaration [component] makes to this Foundation layout; see [ChildMeasurable.declare]. */
    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ): Unit = measurables.of(component).declare(parentData, elements)

    /** Gives up what [component] was registered under, and the extent measured for it. */
    override fun removeLayoutComponent(component: Component) {
        measurables.forget(component)
    }

    override fun invalidateLayout(target: Container) {
        if (measurables.invalidate()) target.parent?.revalidate()
    }

    override fun preferredLayoutSize(parent: Container): Dimension = measurables.askedSize(MeasureMode.Preferred)

    /**
     * Minimum extents are read fresh every time. A measurable holds the preferred extent only, and a
     * container is asked for its minimum once per validate rather than once per pass.
     */
    override fun minimumLayoutSize(parent: Container): Dimension = measurables.askedSize(MeasureMode.Minimum)

    /** A policy-driven container takes any extent it is offered and places its children inside it. */
    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    /** What the policy's chosen child reports; see [firstChildAlignment]. */
    override fun getLayoutAlignmentX(target: Container): Float =
        firstChildAlignment(alignmentChild(target)) { it.alignmentX }

    override fun getLayoutAlignmentY(target: Container): Float =
        firstChildAlignment(alignmentChild(target)) { it.alignmentY }

    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val width = innerExtent(parent.width, insets.left, insets.right)
        val height = innerExtent(parent.height, insets.top, insets.bottom)
        // A placement read's pass over a container still valid places the last result again without running the
        // policy. A container invalidated since, by a child added or removed or a size changed, has a validation
        // coming, and is measured again as that validation would.
        val replaysPlacement = measurables.isPlacingAgain && parent.isValid && ::placed.isInitialized
        val result = if (replaysPlacement) placed else measurables.settledOn(width, height)
        placementScope.begin(insets.left, insets.top, width, parent.componentOrientation.isLeftToRight)
        placed = result
        observe(PlacementReads, placeBlock)
    }
}

private fun MeasurePolicyLayout.alignmentChild(target: Container): Component? =
    when ((policy as? ParentAlignmentPolicy)?.parentAlignmentChild ?: ParentAlignmentChild.FirstDeclared) {
        ParentAlignmentChild.FirstDeclared -> {
            (target as? ConstrainedPanel)?.firstChildInDeclarationOrder()
                ?: if (target.componentCount > 0) target.getComponent(0) else null
        }

        ParentAlignmentChild.Topmost -> {
            if (target.componentCount == 0) {
                null
            } else if (target is ConstrainedPanel) {
                target.getComponent(0)
            } else {
                target.getComponent(target.componentCount - 1)
            }
        }
    }

/** The non-negative extent left after the insets on its two edges have taken their space. */
private fun innerExtent(
    extent: Int,
    firstInset: Int,
    secondInset: Int,
): Int = (extent.toLong() - firstInset.grownBy(secondInset)).coerceAtLeast(0L).toInt()

/** [this] plus [amount], held between zero and the largest extent the geometry APIs can represent. */
internal fun Int.grownBy(amount: Int): Int = (toLong() + amount).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

internal enum class RunningCause { ParentPlacement, PlacementReplay }

/**
 * One container's children as its policy sees them, and the two ways that policy is run over them: for
 * an extent it is offered, and for one it is asked to name.
 *
 * A layout pass and an intrinsic walk take lists of their own, so asking a container what it prefers
 * while a layout pass is in flight does not take that pass's own list away.
 */
internal class ChildMeasurables(
    internal val owner: MeasurePolicyLayout,
) {
    private val measurables = IdentityHashMap<Component, ChildMeasurable>()
    private val intrinsicWalk = ArrayList<ChildMeasurable>()

    /** The panel laid out by [owner], which keeps the stacking order of its children. */
    lateinit var panel: ConstrainedPanel

    /** The children the last layout pass handed its policy, less any removed since. */
    internal val layoutPass = ArrayList<ChildMeasurable>()

    /** What the last [measuredSize] or [settledOn] settled on, including the independent placeables it granted. */
    internal var measured: MeasureResult? = null

    /** What [measureBlock] measures under, and what it answers until [measureObserved] hands it on. */
    private lateinit var measureConstraints: Constraints
    private var measureResult: MeasureResult? = null

    /** What [askBlock] answers. */
    private var askedWidth = 0
    private var askedHeight = 0

    /** Which block of its policy this container is running. */
    var layoutState: LayoutState = LayoutState.Idle
        internal set

    // Stored once, reading inputs from fields, so observing an answer allocates nothing per call.
    private val measureBlock: () -> Unit = {
        val children = layoutPassOf()
        val previous = enter(LayoutState.Measuring)
        try {
            measureResult = with(owner.policy) { PolicyMeasureScope.measure(children, measureConstraints) }
        } finally {
            layoutState = previous
        }
    }

    private val askBlock: () -> Unit = {
        val children = intrinsicWalk
        with(owner.policy) {
            if (mode == MeasureMode.Minimum) {
                askedWidth = PolicyMeasureScope.minIntrinsicWidth(children, Int.MAX_VALUE)
                askedHeight = PolicyMeasureScope.minIntrinsicHeight(children, Int.MAX_VALUE)
            } else {
                askedWidth = PolicyMeasureScope.maxIntrinsicWidth(children, Int.MAX_VALUE)
                askedHeight = PolicyMeasureScope.maxIntrinsicHeight(children, Int.MAX_VALUE)
            }
        }
    }

    private val runningCauses = EnumSet.noneOf(RunningCause::class.java)

    /** Whether the invalidation arriving is the one this container's own parent causes by placing it. */
    private val beingPlaced: Boolean get() = RunningCause.ParentPlacement in runningCauses

    /** Whether this container places its children again outside a validation. */
    val isPlacingAgain: Boolean get() = RunningCause.PlacementReplay in runningCauses

    /**
     * Which extent a child that cannot be asked a constrained question answers with. The policy never
     * learns it: it is what tells one policy body apart from the same body run for a different question.
     */
    var mode: MeasureMode = MeasureMode.Measure
        private set

    /**
     * The measurable for [child], made on the first call. A child always reaches
     * `addLayoutComponent` - `Container.addImpl` hands a `LayoutManager2` a null constraint where the
     * caller named none - so this also stands for a child added while another manager was in place.
     *
     * A child this container has not held before gives up what the last pass settled on: that pass
     * measured the children of the moment, and this one is not among them.
     */
    fun of(child: Component): ChildMeasurable =
        measurables.getOrPut(child) {
            measured = null
            ChildMeasurable(child, this)
        }

    /** What [child] was registered under, or `null` where this container does not hold it. */
    fun declaredBy(child: Component): Any? = measurables[child]?.parentData

    /**
     * Stores [parentData] once the parent-data protocol of [owner] has accepted it, and stacks the children again
     * where it moves [component] to another z-index.
     */
    fun updateParentData(
        component: Component,
        parentData: Any?,
    ) {
        owner.parentDataProtocol?.validateParentData(component, parentData)
        val measurable = of(component)
        val previousZIndex = (measurable.parentData as? StackingParentData)?.zIndex ?: 0f
        measurable.parentData = parentData
        val zIndex = (parentData as? StackingParentData)?.zIndex ?: 0f
        if (zIndex != previousZIndex) (component.parent as? ConstrainedPanel)?.stackingOrder?.restack()
    }

    /** Gives up the measurable for [child], for a child leaving the container. */
    fun forget(child: Component) {
        val measurable = measurables.remove(child) ?: return
        layoutPass.remove(measurable)
        measurable.forEachLayoutNode { if (it.child === measurable) it.child = null }
        measured = null
    }

    /**
     * Gives up every extent measured, and reports whether the invalidation came from outside the
     * container's own placement. Such an invalidation can change this container's intrinsic size, so
     * its parent must be laid out too; placement only assigns the size a parent already measured.
     */
    fun invalidate(): Boolean {
        // The SAM constructor compiles to one shared instance; a bare lambda here is wrapped in a new BiConsumer
        // on every call.
        measurables.forEach(BiConsumer { _, measurable -> measurable.preferred = null })
        if (beingPlaced) return false
        measured = null
        return true
    }

    /** Marks [cause] for [block], preserving any cause already marked by an enclosing run. */
    inline fun during(
        cause: RunningCause,
        block: () -> Unit,
    ) {
        val added = runningCauses.add(cause)
        try {
            block()
        } finally {
            if (added) runningCauses.remove(cause)
        }
    }

    /**
     * The children a layout pass hands its policy, in declaration order, with the pass put in the mode a measure
     * under given constraints runs in.
     */
    fun layoutPassOf(): List<Measurable> {
        mode = MeasureMode.Measure
        measured = null
        return gather(layoutPass)
    }

    /**
     * What the policy occupies under [constraints], plus the insets it measured inside - the answer a
     * container's own [Constrainable] gives its parent.
     *
     * Nothing is placed: the parent is deciding an extent, and the placement follows from the bounds it
     * then assigns, which is what `layoutContainer` runs.
     */
    fun measuredSize(constraints: Constraints): Dimension {
        val insets = panel.insets
        val horizontal = insets.left.grownBy(insets.right)
        val vertical = insets.top.grownBy(insets.bottom)
        val result = measureObserved(constraints.offset(-horizontal, -vertical), MeasuredReads)
        measured = result
        return Dimension(result.width.grownBy(horizontal), result.height.grownBy(vertical))
    }

    /**
     * What the policy asks for in [mode], plus the insets the policy measured inside.
     *
     * Swing asks for both axes at once and supplies no cross-axis extent. It therefore combines the
     * two matching CMP intrinsic hooks with an unbounded opposite axis: min hooks for a Swing
     * minimum-size query and max hooks for a preferred-size query.
     */
    fun askedSize(mode: MeasureMode): Dimension {
        gather(intrinsicWalk)
        val retainedMode = this.mode
        val retainedResult = measured
        this.mode = mode
        measured = null
        try {
            owner.observe(if (mode == MeasureMode.Minimum) MinimumReads else PreferredReads, askBlock)
        } finally {
            this.mode = retainedMode
            measured = retainedResult
        }
        val width = askedWidth
        val height = askedHeight
        val insets = panel.insets
        return Dimension(
            width.grownBy(insets.left.grownBy(insets.right)),
            height.grownBy(insets.top.grownBy(insets.bottom)),
        )
    }

    /** What the policy measures over the children under [constraints], its reads recorded under [onChanged]. */
    internal fun measureObserved(
        constraints: Constraints,
        onChanged: (LayoutObservationNode) -> Unit,
    ): MeasureResult {
        measureConstraints = constraints
        owner.observe(onChanged, measureBlock)
        val result = checkNotNull(measureResult)
        measureResult = null
        return result
    }
}

/**
 * What the policy settled on for the inner extent [width] by [height] the container now holds: the pass a parent
 * already ran over this container, where that pass settled on this very extent, and a fresh one otherwise. Either is
 * kept for the next pass at the same extent, until the container is invalidated or its placement resizes a
 * child to an extent at which what the pass read from the child no longer holds.
 *
 * A container its parent measured has run its policy once for the offer that placement came from,
 * and its children still hold the extents that pass granted them. Running the policy again for the
 * extent it settled on would divide that extent instead of the offer, so a share worked out from a
 * wider offer would shrink under the child it was granted to.
 */
internal fun ChildMeasurables.settledOn(
    width: Int,
    height: Int,
): MeasureResult {
    measured?.let { if (it.width == width && it.height == height) return it }
    val result = measureObserved(Constraints(width, width, height, height), SettledReads)
    measured = result
    return result
}

private fun ChildMeasurables.gather(into: ArrayList<ChildMeasurable>): List<Measurable> {
    into.clear()
    panel.forEachChildInDeclarationOrder { into.add(of(it)) }
    return into
}

/** Which extent a child answers with when the question reaches its own argument-less Swing call. */
internal enum class MeasureMode {
    /** A layout pass: the child takes the extent it prefers, held to the constraints it was measured under. */
    Measure,

    /** The container is being asked what it prefers, so each child answers with what it prefers. */
    Preferred,

    /** The container is being asked for its minimum, so each child answers with its own minimum. */
    Minimum,
}

/**
 * One child of a container driven by a [MeasurePolicy]. Each measurement returns a [ChildPlaceable]
 * that retains its own result, matching androidx's measure-then-place contract.
 *
 * A child that is not a [Constrainable], offered one extent on both axes, takes that extent and is asked
 * nothing, since whatever it answered would be discarded. A [Constrainable] always answers the constrained
 * question.
 *
 * The extent the child prefers is otherwise measured once and kept while the child still holds the
 * extent it was read at, so a pass with nothing to re-measure asks the child nothing. A placement that
 * resizes the child gives that reading up, as does an invalidation of the container. A child with no
 * peer is measured afresh: a child resized by anything other than this container reaches its manager by
 * invalidating that container, and AWT carries the invalidation up only to a container `isValid` reports
 * true for, which requires a peer. Without one none ever arrives, and the extent held here would be
 * stale for good.
 */
internal class ChildMeasurable(
    /** The component this stands for, which the container's own policy reads properties of. */
    val component: Component,
    internal val owner: ChildMeasurables,
) : Measurable {
    override var parentData: Any? = null

    /**
     * The measurable of this child's outermost layout modifier, which measures through the next one inward, the
     * innermost measuring the unmodified child; null where the child has no layout modifier. It is built again only
     * when the child declares other nodes.
     *
     * Intrinsic queries answer through it rather than through [measure], and the unmodified end answers with the
     * component's own preferred or minimum extent. A stateful node overriding the intrinsic hooks, such as an
     * animation node, then answers without running its `measure`, which would retarget its animation once per query
     * in a single validate cycle.
     */
    var outerMeasurable: Measurable? = null
        private set

    internal var preferred: Dimension? = null

    /**
     * Whether the running or last measure block of its container read this child's baseline. Placing the
     * child again then moves a baseline that measure depended on.
     */
    var lineReadByMeasure: Boolean = false

    override fun measure(constraints: Constraints): Placeable =
        outerMeasurable?.measure(constraints) ?: measureUnmodified(constraints)

    // Each reads the field once rather than through a safe call, which would box the Int it answers.
    override fun minIntrinsicWidth(height: Int): Int {
        val outer = outerMeasurable ?: return component.minimumSize.width
        return outer.minIntrinsicWidth(height)
    }

    override fun maxIntrinsicWidth(height: Int): Int {
        val outer = outerMeasurable ?: return preferredExtent().width
        return outer.maxIntrinsicWidth(height)
    }

    override fun minIntrinsicHeight(width: Int): Int {
        val outer = outerMeasurable ?: return component.minimumSize.height
        return outer.minIntrinsicHeight(width)
    }

    override fun maxIntrinsicHeight(width: Int): Int {
        val outer = outerMeasurable ?: return preferredExtent().height
        return outer.maxIntrinsicHeight(width)
    }

    override fun maximumSize(): Dimension? = if (component.isMaximumSizeSet) component.maximumSize else null

    /**
     * Where the baseline falls within the extent this measurable states. The nested layout result is
     * replayed into a baseline-only placement scope, so custom layout modifiers participate exactly as
     * they do during real placement.
     */
    override fun baseline(
        width: Int,
        height: Int,
    ): Int {
        if (owner.layoutState == LayoutState.Measuring) lineReadByMeasure = true
        return measure(Constraints(width, width, height, height)).baselineAt(0L, 0L)
    }

    /** What the component itself answers under [constraints], with no chain between. */
    internal fun measureUnmodified(constraints: Constraints): ChildPlaceable {
        val constrainable = component as? Constrainable
        if (constrainable != null && owner.mode == MeasureMode.Measure) {
            constrainable.measure(constraints)
            return ChildPlaceable(
                this,
                constrainable.constrainedWidth,
                constrainable.constrainedHeight,
                constraints,
            )
        }
        // Swing has no constrained-measure operation for an ordinary component.  When both axes
        // are exact, retain the established no-query fast path and use the granted extent. For every
        // other offer, its preferred or minimum size is the only answer Swing gives us, so constrain
        // it here. Only a Constrainable or layout modifier can deliberately report real overflow.
        val fixed = constraints.hasFixedWidth && constraints.hasFixedHeight
        val extent = if (fixed) Dimension(constraints.minWidth, constraints.minHeight) else plainExtent()
        val width = constraints.constrainWidth(extent.width)
        return ChildPlaceable(this, width, constraints.constrainHeight(extent.height), constraints)
    }

    /**
     * Records every declaration this child makes to its container.
     *
     * Core has already resolved and folded parent-data modifiers into [parentData]. Foundation retains
     * only its ordered layout-modifier chain and rejects another parent's remaining element rather than
     * silently ignoring a declaration it cannot apply.
     */
    fun declare(
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        elements.fastForEach {
            require(it is LayoutModifierNode) {
                "Foundation layout received an unsupported parent-layout modifier: ${it.name}."
            }
            it.child = this
        }
        var kept = 0
        var chainChanged = false
        forEachLayoutNode {
            if (chainChanged) return@forEachLayoutNode
            if (kept < elements.size && elements[kept] == it) kept++ else chainChanged = true
        }
        chainChanged = chainChanged || kept != elements.size
        if (chainChanged) {
            forEachLayoutNode { if (it.child === this && it !in elements) it.child = null }
            outerMeasurable =
                if (elements.isEmpty()) {
                    null
                } else {
                    var outer: Measurable = UnmodifiedChildMeasurable(this)
                    for (index in elements.lastIndex downTo 0) {
                        outer = LayoutModifierMeasurable(elements[index] as LayoutModifierNode, outer, this)
                    }
                    outer
                }
        }
        val changed = this.parentData != parentData || chainChanged
        owner.updateParentData(component, parentData)
        if (changed) owner.measured = null
    }
}

/** What the child answers through its own argument-less Swing call, for the mode the pass is in. */
private fun ChildMeasurable.plainExtent(): Dimension =
    when (owner.mode) {
        MeasureMode.Minimum -> component.minimumSize
        else -> preferredExtent()
    }

private fun ChildMeasurable.preferredExtent(): Dimension =
    if (component.isDisplayable) {
        preferred ?: component.preferredSize.also { preferred = it }
    } else {
        component.preferredSize
    }

/** Runs [action] on each layout modifier node this child is measured through, outermost first. */
private inline fun ChildMeasurable.forEachLayoutNode(action: (LayoutModifierNode) -> Unit) {
    var measurable = outerMeasurable
    while (measurable is LayoutModifierMeasurable) {
        action(measurable.modifier)
        measurable = measurable.measurable
    }
}

/** The unmodified child, which its innermost layout modifier measures through. */
private class UnmodifiedChildMeasurable(
    internal val child: ChildMeasurable,
) : Measurable {
    override val parentData: Any? get() = child.parentData

    override fun measure(constraints: Constraints): Placeable = child.measureUnmodified(constraints)

    override fun minIntrinsicWidth(height: Int): Int = child.component.minimumSize.width

    override fun maxIntrinsicWidth(height: Int): Int = child.preferredExtent().width

    override fun minIntrinsicHeight(width: Int): Int = child.component.minimumSize.height

    override fun maxIntrinsicHeight(width: Int): Int = child.preferredExtent().height

    override fun maximumSize(): Dimension? = child.maximumSize()

    override fun baseline(
        width: Int,
        height: Int,
    ): Int = child.component.getBaseline(width, height)
}

/** One layout modifier of a child, measuring through [measurable]. */
private class LayoutModifierMeasurable(
    val modifier: LayoutModifierNode,
    val measurable: Measurable,
    private val placement: ChildMeasurable,
) : Measurable {
    override val parentData: Any? get() = measurable.parentData

    override fun measure(constraints: Constraints): Placeable =
        LayoutModifierPlaceable(
            with(PolicyMeasureScope) { with(modifier) { measure(measurable, constraints) } },
            constraints,
            placement.owner.panel.componentOrientation.isLeftToRight,
            placement,
        )

    override fun minIntrinsicWidth(height: Int): Int =
        with(PolicyMeasureScope) { with(modifier) { minIntrinsicWidth(measurable, height) } }

    override fun maxIntrinsicWidth(height: Int): Int =
        with(PolicyMeasureScope) { with(modifier) { maxIntrinsicWidth(measurable, height) } }

    override fun minIntrinsicHeight(width: Int): Int =
        with(PolicyMeasureScope) { with(modifier) { minIntrinsicHeight(measurable, width) } }

    override fun maxIntrinsicHeight(width: Int): Int =
        with(PolicyMeasureScope) { with(modifier) { maxIntrinsicHeight(measurable, width) } }

    override fun maximumSize(): Dimension? = measurable.maximumSize()

    override fun baseline(
        width: Int,
        height: Int,
    ): Int = measure(Constraints(width, width, height, height)).baselineAt(0L, 0L)
}

/** One independent measurement of a Swing child. */
internal class ChildPlaceable(
    override val child: ChildMeasurable,
    override val measuredWidth: Int,
    override val measuredHeight: Int,
    constraints: Constraints,
) : Placeable() {
    override val width: Int = constraints.constrainWidth(measuredWidth)

    override val height: Int = constraints.constrainHeight(measuredHeight)

    override fun placeAt(
        x: Long,
        y: Long,
    ) {
        val component = child.component
        val resized = component.width != measuredWidth || component.height != measuredHeight
        // A constrainable child answers its constraints whatever its size.
        val readable = resized && component !is Constrainable
        val read = if (readable) child.preferredAtOldSize() else null
        if (resized) child.preferred = null
        component.setBounds(
            saturateLayoutCoordinate(x + centeredOverflowOffset(width, measuredWidth)),
            saturateLayoutCoordinate(y + centeredOverflowOffset(height, measuredHeight)),
            measuredWidth,
            measuredHeight,
        )
        if (readable && child.answersOtherwiseThan(read)) child.owner.measured = null
    }

    override fun baselineAt(
        x: Long,
        y: Long,
    ): Int {
        val baseline = child.component.getBaseline(measuredWidth, measuredHeight)
        return if (baseline < 0) {
            baseline
        } else {
            saturateLayoutCoordinate(y + centeredOverflowOffset(height, measuredHeight) + baseline.toLong())
        }
    }
}

/**
 * What this child preferred at its old size, read before a placement resizes it, or null where there is nothing to
 * compare. A displayable child holds what it answered, and nothing where it was not asked; a child with no peer holds
 * nothing, so it is asked again.
 */
private fun ChildMeasurable.preferredAtOldSize(): Dimension? =
    when {
        component.isDisplayable -> preferred
        outerMeasurable == null -> component.preferredSize
        else -> null
    }

/**
 * Whether this child, resized by its placement, no longer answers what the result its container settled on read:
 * [read], its [preferredAtOldSize], which a child whose height follows its width answers differently at the new size.
 * A minimum is never held, so a child whose layout modifiers may have asked for one answers otherwise whenever
 * nothing else was read.
 */
private fun ChildMeasurable.answersOtherwiseThan(read: Dimension?): Boolean =
    if (read == null) outerMeasurable != null else preferredExtent() != read

/** A [MeasureResult] held with the reading order it must replay its placement under. */
private class LayoutModifierPlaceable(
    private val result: MeasureResult,
    constraints: Constraints,
    private val leftToRight: Boolean,
    override val child: ChildMeasurable,
) : Placeable() {
    override val measuredWidth: Int get() = result.width
    override val measuredHeight: Int get() = result.height
    override val width: Int = constraints.constrainWidth(measuredWidth)
    override val height: Int = constraints.constrainHeight(measuredHeight)

    override fun placeAt(
        x: Long,
        y: Long,
    ) {
        val originX = x + centeredOverflowOffset(width, measuredWidth)
        val originY = y + centeredOverflowOffset(height, measuredHeight)
        val scope = NodePlacementScope(originX, originY, measuredWidth, leftToRight)
        with(result) { scope.placeChildren() }
    }

    override fun baselineAt(
        x: Long,
        y: Long,
    ): Int =
        BaselinePlacementScope(
            x + centeredOverflowOffset(width, measuredWidth),
            y + centeredOverflowOffset(height, measuredHeight),
            measuredWidth,
            leftToRight,
        ).also { scope ->
            with(result) { scope.placeChildren() }
        }.baseline
}

/** The real child's centered displacement inside the apparent extent its parent arranged around. */
private fun centeredOverflowOffset(
    apparent: Int,
    measured: Int,
): Long = (apparent.toLong() - measured.toLong()) / 2L

/** Replays a [LayoutModifierNode]'s result into the coordinate system of the enclosing node or parent. */
private class NodePlacementScope(
    private val originX: Long,
    private val originY: Long,
    override val parentWidth: Int,
    override val isLeftToRight: Boolean,
) : PlacementScope() {
    override fun Placeable.place(
        x: Int,
        y: Int,
    ) {
        placeAt(originX + x.toLong(), originY + y.toLong())
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
    ) {
        placeAt(originX + relativeX(this, x), originY + y.toLong())
    }
}

/** Replays placement only far enough to discover the component baseline inside a modifier chain. */
private class BaselinePlacementScope(
    private val originX: Long,
    private val originY: Long,
    override val parentWidth: Int,
    override val isLeftToRight: Boolean,
) : PlacementScope() {
    var baseline: Int = -1
        private set

    override fun Placeable.place(
        x: Int,
        y: Int,
    ) {
        val placed = baselineAt(originX + x.toLong(), originY + y.toLong())
        if (baseline < 0) baseline = placed
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
    ) {
        val placed = baselineAt(originX + relativeX(this, x), originY + y.toLong())
        if (baseline < 0) baseline = placed
    }
}

/** The Swing component behind an intrinsic measurable, if one is present. */
internal val IntrinsicMeasurable.componentOrNull: Component?
    get() =
        when (this) {
            is ChildMeasurable -> component
            is UnmodifiedChildMeasurable -> child.component
            is LayoutModifierMeasurable -> measurable.componentOrNull
            is IntrinsicMeasurableAdapter -> source.componentOrNull
        }

/** The component a policy of this library reads a property of - a maximum size, a baseline. */
internal val Measurable.component: Component
    get() = componentOrNull ?: error("Foundation can inspect a Swing component only from one of its child measurables.")

/**
 * The reading order the container carries, which a policy of this library resolves an [Arrangement] and
 * an [Alignment] against. [PlacementScope.isLeftToRight] is the whole of it a policy outside the library
 * needs; these two take the orientation itself.
 */
internal val PlacementScope.orientation: ComponentOrientation
    get() = if (isLeftToRight) ComponentOrientation.LEFT_TO_RIGHT else ComponentOrientation.RIGHT_TO_LEFT

/**
 * Where a policy places its children: inside the container's insets, whose origin every placement is
 * offset by, so a policy works in the coordinates it measured in.
 */
internal class InnerPlacementScope : PlacementScope() {
    private var originX: Int = 0
    private var originY: Int = 0

    override var parentWidth: Int = 0
        private set

    override var isLeftToRight: Boolean = true
        private set

    fun begin(
        originX: Int,
        originY: Int,
        parentWidth: Int,
        isLeftToRight: Boolean,
    ) {
        this.originX = originX
        this.originY = originY
        this.parentWidth = parentWidth
        this.isLeftToRight = isLeftToRight
    }

    override fun Placeable.place(
        x: Int,
        y: Int,
    ) {
        placeAt(originX.toLong() + x.toLong(), originY.toLong() + y.toLong())
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
    ) {
        placeAt(originX.toLong() + relativeX(this, x), originY.toLong() + y.toLong())
    }
}

/** Reads behind what `preferredLayoutSize` answers, which Swing caches as the container's preferred size. */
private val PreferredReads: (LayoutObservationNode) -> Unit = { it.remeasure() }

/** Reads behind what `minimumLayoutSize` answers, which Swing caches as the container's minimum size. */
private val MinimumReads: (LayoutObservationNode) -> Unit = { it.remeasure() }

/** Reads behind what [ChildMeasurables.measuredSize] answers, which the parent lays out with. */
private val MeasuredReads: (LayoutObservationNode) -> Unit = { it.remeasure() }

/** Reads behind what [settledOn] answers, which this container places. */
private val SettledReads: (LayoutObservationNode) -> Unit = { it.remeasure() }

/** Reads behind the children's bounds, which the placement block sets. */
private val PlacementReads: (LayoutObservationNode) -> Unit = { it.component.placeChildrenAgain() }
