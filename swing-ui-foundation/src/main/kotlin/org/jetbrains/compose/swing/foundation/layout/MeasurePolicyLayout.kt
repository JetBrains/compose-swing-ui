package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import org.jetbrains.compose.swing.foundation.graphics.layoutHeight
import org.jetbrains.compose.swing.foundation.graphics.layoutWidth
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.invalidateLayout
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.LayoutManager2
import java.awt.Rectangle
import java.util.EnumSet
import java.util.IdentityHashMap
import java.util.function.BiConsumer
import javax.swing.CellRendererPane

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

    /** The attached nodes the panel was told hold it, in the order they attached. */
    internal val nodes: MutableList<SwingComponentNode<ConstrainedPanel>> = ArrayList(1)

    /** The node every answer records its reads under: the first of [nodes], or `null` while none is attached. */
    internal val node: SwingComponentNode<ConstrainedPanel>? get() = nodes.firstOrNull()

    /** A placement replay request still awaiting a pass that actually places the panel's children. */
    internal var placementPending: Boolean = false

    /** What [placeBlock] places. */
    private lateinit var placed: MeasureResult

    /** What the placement block last placed, or null before its first run. */
    val lastPlaced: MeasureResult? get() = if (::placed.isInitialized) placed else null

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
        if (component === measurables.panel.glassPane) return
        val measurable = measurables.of(component)
        measurable.parentData = constraints
        measurable.decoratable?.linkTo(parentMeasurables = measurables)
        if (isNotPlaced) measurable.unplace(joining = true)
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
        // Invalidation reaches a valid parent on its own but schedules no layout pass, and a child that is
        // its own validate root, such as a JTextField, schedules the validation of itself alone. Revalidating
        // the parent schedules the pass that measures this container again; revalidating the target itself
        // would call back here. A Foundation parent gives up only the extent it holds for this container. A
        // CellRendererPane is left alone: its invalidate() does nothing, whoever renders the cell validates it, and
        // revalidate() on a parent that is no JComponent validates the validate root above it at once.
        if (!measurables.invalidate()) return
        val parent = measurables.panel.decoration.parentMeasurables
        if (parent == null) {
            if (target.parent !is CellRendererPane) target.parent?.revalidate()
        } else {
            parent.find(target)?.preferred = null
            parent.during(RunningCause.ChildInvalidation) { target.parent?.revalidate() }
        }
    }

    override fun preferredLayoutSize(parent: Container): Dimension = measurables.askedSize(MeasureMode.Preferred)

    /**
     * Minimum extents are read fresh every time. A measurable holds the preferred extent only, and a
     * container is asked for its minimum once per validate rather than once per pass.
     */
    override fun minimumLayoutSize(parent: Container): Dimension = measurables.askedSize(MeasureMode.Minimum)

    /** A policy-driven container takes any extent it is offered and places its children inside it. */
    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    /**
     * What the first declared child reports, or [Component.CENTER_ALIGNMENT] where the container has no child to ask.
     *
     * A container built from this package reports its content's alignment rather than a fixed value of its own,
     * so the layout above it places it where it would have placed that content directly. A parent that lines
     * its children up on a shared alignment - `javax.swing.BoxLayout` - reserves space on both sides of that
     * line for every sibling, so a container answering a constant would sit off the line its content belongs
     * on and squeeze whichever siblings can stretch.
     *
     * A hidden child answers like any other, because these containers reserve its place as well: a container
     * whose reserved layout and whose reported alignment disagreed about which children exist would sit off
     * the line its own content was measured against.
     */
    override fun getLayoutAlignmentX(target: Container): Float =
        measurables.panel.stackingOrder.order
            .firstOrNull()
            ?.alignmentX ?: Component.CENTER_ALIGNMENT

    /** What the first declared child reports; see [getLayoutAlignmentX]. */
    override fun getLayoutAlignmentY(target: Container): Float =
        measurables.panel.stackingOrder.order
            .firstOrNull()
            ?.alignmentY ?: Component.CENTER_ALIGNMENT

    /**
     * Whether this container is not placed, as its own Foundation parent's record of it says: left unplaced, or inside
     * a container left unplaced. Such a container lays nothing out, as androidx places nothing under a node its parent
     * leaves unplaced, so it keeps its paint outsets, glass pane and stacking order as they were.
     */
    private val isNotPlaced: Boolean
        get() {
            val panel = measurables.panel
            val record = panel.decoration.parentMeasurables?.find(panel) ?: return false
            return record.lastPlacement != ChildPlacement.Placed
        }

    override fun layoutContainer(parent: Container): Unit =
        synchronized(parent.treeLock) {
            // A replay fits and validates the panel after placing its children.
            // The fitted bounds already keep that placement.
            if (measurables.isPlacingAgain && measurables.isFittingPaintOutsets) return@synchronized
            if (isNotPlaced) {
                measurables.panel.stackingOrder.order
                    .fastForEach { measurables.of(it).unplace() }
                return@synchronized
            }
            val panel = measurables.panel
            val insets = panel.borderInsets()
            val width = innerExtent(panel.decoration.layoutWidth(panel), insets.left, insets.right)
            val height = innerExtent(panel.decoration.layoutHeight(panel), insets.top, insets.bottom)
            // A valid container replays its latest result at its current size.
            // An invalid container is measured again before placement.
            val replaysPlacement = measurables.isPlacingAgain && parent.isValid && ::placed.isInitialized
            placementPending = false
            val result =
                if (replaysPlacement) {
                    measurables.measured?.takeIf { it.width == width && it.height == height } ?: placed
                } else {
                    measurables.settledOn(width, height)
                }
            if (result === measurables.measured) measurables.lineScope.observesReplay = false
            placementScope.begin(insets.left, insets.top, width, parent.componentOrientation.isLeftToRight)
            placed = result
            observe(PlacementReads, placeBlock)
            measurables.panel.stackingOrder.order.fastForEach {
                val child = measurables.of(it)
                if (!child.isPlacedByParent) child.hide()
            }
            measurables.fitContainerPaintOutsets()
            if (measurables.zIndexChanged) {
                measurables.zIndexChanged = false
                measurables.panel.stackingOrder.restack()
            }
        }
}

/**
 * Runs [block] keeping the settled result of this component's own Foundation container, where it drives one, and of
 * every Foundation container standing between it and the root: a visibility change AWT reports up through all of
 * them must not read as one of them measuring something new.
 */
internal fun Component.keepingSettledResult(block: () -> Unit) {
    val measurables = (this as? Decoratable)?.decoration?.childMeasurables
    val parent = parent
    if (measurables != null) {
        measurables.during(RunningCause.ParentPlacement) {
            if (parent != null) parent.keepingSettledResult(block) else block()
        }
    } else if (parent != null) {
        parent.keepingSettledResult(block)
    } else {
        block()
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

internal enum class RunningCause {
    ParentPlacement,
    PlacementReplay,
    PaintOutsetFit,
    GlassPaneChange,
    ChildInvalidation,
    Validation,
}

/**
 * Sets this component's bounds to the layout bounds at ([x], [y]) in its parent's coordinates, [width] by [height],
 * grown by [outsets] on each side.
 */
internal fun Component.setBoundsAround(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    outsets: Insets,
) = setBounds(
    x - outsets.left,
    y - outsets.top,
    width.grownBy(outsets.left + outsets.right),
    height.grownBy(outsets.top + outsets.bottom),
)

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

    /** The receiver [alignmentLinesOf] replays the placement in. */
    internal val lineScope = LineMergingPlacementScope()

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
    val beingPlaced: Boolean get() = RunningCause.ParentPlacement in runningCauses

    /** Whether this container places its children again outside a validation. */
    val isPlacingAgain: Boolean get() = RunningCause.PlacementReplay in runningCauses

    /** Whether this container fits its own bounds, or a child's, to a paint outsets change. */
    val isFittingPaintOutsets: Boolean get() = RunningCause.PaintOutsetFit in runningCauses

    /** Whether this container adds or removes its glass pane. */
    val isChangingGlassPane: Boolean get() = RunningCause.GlassPaneChange in runningCauses

    /** Whether this container's panel is being validated, or validates the children its placement replay resized. */
    val isValidating: Boolean get() = RunningCause.Validation in runningCauses

    /** The Swing size query [askedSize] is answering, which picks the intrinsic hooks [askBlock] runs. */
    var mode: MeasureMode = MeasureMode.Preferred
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
            ChildMeasurable(child, this, child as? Decoratable, child as? Constrainable)
        }

    /** The measurable for [child], or null where this container has made none. */
    fun find(child: Component): ChildMeasurable? = measurables[child]

    /** Counts the runs of the placement block; see [ChildMeasurable.isPlacedByParent]. */
    internal var placementRun: Int = 0

    /** Whether a child was placed at a z-index other than the one it was last placed with. */
    var zIndexChanged: Boolean = false

    /**
     * Whether a repaint that touches a child the last layout pass placed
     * [may have to grow][Decoration.holdsRepaintingWhole]: the child repaints whole, or is a Foundation container
     * this is true for in turn. [Decoration.paintImmediately] walks the children only while it is.
     */
    var hasChildRepaintingWhole: Boolean = false
        private set

    /**
     * Works [hasChildRepaintingWhole] out from what the children hold and, where it changed, has the Foundation parent
     * work its own out, and so on up. [holding] is a child a repaint
     * [may have to grow for][Decoration.holdsRepaintingWhole]: unless the last placement left it unplaced, it answers
     * for all the children; null walks them.
     *
     * [gatherPaintBounds] calls it whenever this container's paint outsets are fitted: at the end of each of its
     * layout passes, when its Foundation parent places it, when its own decoration steps change, and when a child's
     * paint outsets or transform change. [writeFitted] calls it for a child whose own answer changed with neither. A
     * validation lays a container out before its children, so no layout pass of the parent follows a change here.
     * Once a validation ends, every container holds what its children give it.
     */
    fun updateHasChildRepaintingWhole(holding: Component? = null) {
        val value =
            holding?.let(::find)?.isLeftUnplaced == false ||
                layoutPass.fastAny { !it.isLeftUnplaced && it.decoratable?.decoration?.holdsRepaintingWhole == true }
        if (value == hasChildRepaintingWhole) return
        hasChildRepaintingWhole = value
        panel.decoration.parentMeasurables?.updateHasChildRepaintingWhole(panel.takeIf { value })
    }

    /**
     * Gives up the measurable for [child], for a child leaving the container, which leaves it with the paint outsets
     * a parent that is not a Foundation container gives: the part its `paintOutsets` value leaves in layout, none by
     * default. Where they changed, it is revalidated, so it is sized and laid out with them. Its next parent may keep
     * its bounds and give it other layout bounds, so it posts a move event.
     */
    fun forget(child: Component) {
        val measurable = measurables.remove(child) ?: return
        layoutPass.remove(measurable)
        measurable.decoratable?.let {
            val held = it.decoration
            if (held.parentMeasurables === this) {
                held.steps.containerLayer?.placedWithoutLayer()
                val steps = held.steps.inContainerLayer(null)
                val opaque = if (steps === held.steps) held.hasOpaqueSteps else steps.isOpaque
                val value = held.fitted(child, steps, opaque, parentMeasurables = null)
                it.decoration = value
                if (value.heldPaintOutsets != held.heldPaintOutsets) child.revalidate()
            }
        }
        measurable.forEachLayoutNode { if (it.child === measurable) it.child = null }
        measurable.show()
        child.postComponentMoved()
        measured = null
    }

    /**
     * Gives up every extent measured, except what the children prefer under [RunningCause.ChildInvalidation], and
     * reports whether the invalidation came from outside the container's own placement. Such an invalidation can
     * change this container's intrinsic size, so its parent must be laid out too. Placement only assigns the size
     * a parent already measured.
     */
    fun invalidate(): Boolean {
        // The SAM constructor compiles to one shared instance; a bare lambda here is wrapped in a new BiConsumer
        // on every call.
        if (RunningCause.ChildInvalidation !in runningCauses) {
            measurables.forEach(BiConsumer { _, measurable -> measurable.preferred = null })
        }
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

    /** The children a layout pass hands its policy, in declaration order. */
    fun layoutPassOf(): List<Measurable> {
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
    fun measuredSize(constraints: Constraints): Dimension =
        synchronized(panel.treeLock) {
            val insets = panel.borderInsets()
            val horizontal = insets.left.grownBy(insets.right)
            val vertical = insets.top.grownBy(insets.bottom)
            val result = measureObserved(constraints.offset(-horizontal, -vertical), MeasuredReads)
            measured = result
            Dimension(result.width.grownBy(horizontal), result.height.grownBy(vertical))
        }

    /**
     * What the policy asks for in [mode], plus the panel's insets, its paint outsets included, as any Swing layout
     * manager adds them.
     *
     * Swing asks for both axes at once and supplies no cross-axis extent. It therefore combines the
     * two matching CMP intrinsic hooks with an unbounded opposite axis: min hooks for a Swing
     * minimum-size query and max hooks for a preferred-size query.
     */
    fun askedSize(mode: MeasureMode): Dimension =
        synchronized(panel.treeLock) {
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
            Dimension(
                width.grownBy(insets.left.grownBy(insets.right)),
                height.grownBy(insets.top.grownBy(insets.bottom)),
            )
        }

    /**
     * The lines [result], a measure of its container, puts with its inner rectangle at ([originX], [originY]), in
     * [leftToRight] reading order: each line the policy provides, and each other line the children put where the
     * placement puts them, merged by the line's merger, as androidx merges a layout's lines from its placed children.
     * The placement runs as the placement block does, placing nothing; a child it measures is measured again by the
     * next run of the placement block. For a result the container has not placed, its reads are recorded under a
     * callback of their own, kept apart from the placement block's; the placement block's reads cover the result it
     * placed.
     */
    fun alignmentLinesOf(
        result: MeasureResult,
        originX: Int,
        originY: Int,
        leftToRight: Boolean,
    ): Map<AlignmentLine, Int> =
        synchronized(panel.treeLock) {
            val run = placementRun
            val previous = enter(LayoutState.LayingOut)
            try {
                lineScope.replaying(result, originX.toLong(), originY.toLong(), leftToRight)
                Snapshot.withoutReadObservation {
                    lineScope.observesReplay = owner.lastPlaced !== result
                    if (lineScope.observesReplay) owner.observe(LineReads, lineScope.replay) else lineScope.replay()
                }
                lineScope.takeLines()
            } finally {
                layoutState = previous
                placementRun = run
            }
        }

    /** What the policy measures over the children under [constraints], its reads recorded under [onChanged]. */
    internal fun measureObserved(
        constraints: Constraints,
        onChanged: (SwingComponentNode<ConstrainedPanel>) -> Unit,
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
    panel.stackingOrder.order.fastForEach { into.add(of(it)) }
    return into
}

/** A Swing size query a container answers through its policy's intrinsic hooks. */
internal enum class MeasureMode {
    /** getPreferredSize: the policy's max intrinsic hooks answer it. */
    Preferred,

    /** getMinimumSize: the policy's min intrinsic hooks answer it. */
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
    /** [component] itself, where it is a [Decoratable]. */
    val decoratable: Decoratable?,
    /** [component] itself, where it answers constraints on its own. */
    val constrainable: Constrainable?,
) : Measurable {
    /** What the child is registered under, once the parent-data protocol of its container has accepted it. */
    override var parentData: Any? = null
        set(value) {
            owner.owner.parentDataProtocol?.validateParentData(component, value)
            field = value
        }

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

    /** The z-index this child was last placed with, summed over its container and its layout modifiers. */
    var zIndex: Float = 0f

    internal var preferred: Dimension? = null

    /**
     * The block of its container's policy that measured this child in the running pass; [LayoutState.Idle] where
     * none has.
     */
    var measuredByParent: LayoutState = LayoutState.Idle

    /**
     * Whether a request of one of this child's layout nodes waits for a measure of the child that a layout pass of its
     * container runs, as androidx's `measurePending`: set by a measurement request the running event defers, or by a
     * placement request that revalidates the container and is deferred, and cleared as such a measure starts.
     */
    var measurePending: Boolean = false

    /** Whether this child's [measure] runs in a layout pass of its container, as androidx's `Measuring` state. */
    var isMeasuring: Boolean = false
        private set

    /**
     * The block of its container's policy that read one of this child's alignment lines in its running or last run,
     * [LayoutState.Measuring] where both did; [LayoutState.Idle] where neither did. Placing the child again then moves
     * a line that block depended on.
     */
    var lineReadDuring: LayoutState = LayoutState.Idle

    /** The [run][ChildMeasurables.placementRun] of its container's placement block that last placed this child. */
    var placedInRun: Int = -1

    /**
     * Whether the running or last run of its container's placement block placed this child, as androidx's
     * `MeasurePassDelegate.isPlacedByParent`. A child the run leaves unplaced is [hidden][hide].
     */
    val isPlacedByParent: Boolean get() = placedInRun == owner.placementRun

    override fun measure(constraints: Constraints): Placeable {
        trackMeasurementByParent()
        // A container that is not valid lays out what it measures, unlike a valid one its own container measures.
        val inLayoutPass = !owner.panel.isValid
        if (inLayoutPass) measurePending = false
        isMeasuring = inLayoutPass
        try {
            return outerMeasurable?.measure(constraints) ?: measureUnmodified(constraints)
        } finally {
            isMeasuring = false
        }
    }

    // Each reads the field once rather than through a safe call, which would box the Int it answers.
    override fun minIntrinsicWidth(height: Int): Int {
        val outer = outerMeasurable ?: return layoutMinimumSize.width
        return outer.minIntrinsicWidth(height)
    }

    override fun maxIntrinsicWidth(height: Int): Int {
        val outer = outerMeasurable ?: return preferredExtent().width
        return outer.maxIntrinsicWidth(height)
    }

    override fun minIntrinsicHeight(width: Int): Int {
        val outer = outerMeasurable ?: return layoutMinimumSize.height
        return outer.minIntrinsicHeight(width)
    }

    override fun maxIntrinsicHeight(width: Int): Int {
        val outer = outerMeasurable ?: return preferredExtent().height
        return outer.maxIntrinsicHeight(width)
    }

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? {
        val outer = outerMeasurable ?: return UnmodifiedIntrinsicPlaceable(this, width, height)
        return outer.intrinsicPlaceable(width, height)
    }

    override fun maximumSize(): Dimension? = if (component.isMaximumSizeSet) component.maximumSize else null

    /** What the component itself answers under [constraints], with no chain between. */
    internal fun measureUnmodified(constraints: Constraints): ChildPlaceable {
        if (constrainable != null) {
            constrainable.measure(constraints)
            return ChildPlaceable(
                this,
                constrainable.constrainedWidth,
                constrainable.constrainedHeight,
                constraints,
                if (decoratable?.decoration?.childMeasurables == null) constrainable.alignmentLines else emptyMap(),
            )
        }
        // Swing has no constrained-measure operation for an ordinary component.  When both axes
        // are exact, retain the established no-query fast path and use the granted extent. For every
        // other offer, its preferred size is the only answer Swing gives us, so constrain it here.
        // Only a Constrainable or layout modifier can deliberately report real overflow.
        val fixed = constraints.hasFixedWidth && constraints.hasFixedHeight
        val width: Int
        val height: Int
        if (fixed) {
            width = constraints.minWidth
            height = constraints.minHeight
        } else {
            val extent = preferredExtent()
            width = constraints.constrainWidth(extent.width)
            height = constraints.constrainHeight(extent.height)
        }
        return ChildPlaceable(this, width, height, constraints, emptyMap())
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
        this.parentData = parentData
        if (changed) owner.measured = null
    }

    /** What the last placement block did with this child; a child no block has run over yet counts as placed. */
    var lastPlacement: ChildPlacement = ChildPlacement.Placed

    /** Whether the last placement block of its container left this child unplaced, so it paints nothing. */
    val isLeftUnplaced: Boolean
        get() = lastPlacement == ChildPlacement.Unplaced || lastPlacement == ChildPlacement.Hidden

    /**
     * Hides this child at a zero size with `setVisible(false)`, as `CardLayout` hides the cards it does not show:
     * Swing paints nothing of it, sends it no event and moves the focus off it. While it stays hidden it reports no
     * placement, and neither does a component inside it that only Foundation containers lay out. A child already
     * invisible stays invisible, and is set to the zero size too.
     */
    fun hide() {
        if (!isLeftUnplaced) {
            lastPlacement = ChildPlacement.Unplaced
            // Already at a zero size, the setSize below posts no resize, so a container child is laid out again
            // here to leave its own children unplaced.
            if (decoratable != null && layoutWidth == 0 && layoutHeight == 0) component.layOutAgain()
        }
        if (component.isVisible) {
            lastPlacement = ChildPlacement.Hidden
            component.keepingSettledResult { component.isVisible = false }
        }
        component.setSize(0, 0)
    }

    /** Records this child placed again where [hide] or [unplace] left it unplaced, and shows it where either hid it. */
    fun show() {
        if (lastPlacement == ChildPlacement.Placed) return
        val hidden = lastPlacement == ChildPlacement.Hidden || lastPlacement == ChildPlacement.JoinedUnplacedContainer
        lastPlacement = ChildPlacement.Placed
        if (hidden) {
            // Shown, the child revalidates itself as well as its parent, and both keep what they settled on.
            component.keepingSettledResult { component.isVisible = true }
        }
    }

    /** The width of the child's layout bounds. */
    val layoutWidth: Int
        get() = decoratable?.decoration?.layoutWidth(component) ?: component.width

    /** The height of the child's layout bounds. */
    val layoutHeight: Int
        get() = decoratable?.decoration?.layoutHeight(component) ?: component.height
}

private fun ChildMeasurable.preferredExtent(): Dimension =
    preferred?.takeIf { component.isValid } ?: layoutPreferredSize.also { preferred = it }

/**
 * [ChildMeasurable.decoratable], or throws where [ChildMeasurable.component] does not paint through a decoration.
 *
 * @throws IllegalStateException if the component is not a [Decoratable].
 */
internal val ChildMeasurable.requireDecoratable: Decoratable
    get() =
        checkNotNull(decoratable) {
            "A decoration step requires a ${Decoratable::class.java.name} target, but the component is a " +
                "${component.javaClass.name}"
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

    override fun minIntrinsicWidth(height: Int): Int = child.layoutMinimumSize.width

    override fun maxIntrinsicWidth(height: Int): Int = child.preferredExtent().width

    override fun minIntrinsicHeight(width: Int): Int = child.layoutMinimumSize.height

    override fun maxIntrinsicHeight(width: Int): Int = child.preferredExtent().height

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable = UnmodifiedIntrinsicPlaceable(child, width, height)

    override fun maximumSize(): Dimension? = child.maximumSize()
}

/**
 * [child] at [width] by [height], standing in for it in an intrinsic question. It places nothing: only its
 * [FirstBaseline], read from the component itself, answers a line read of it.
 */
private class UnmodifiedIntrinsicPlaceable(
    private val child: ChildMeasurable,
    override val measuredWidth: Int,
    override val measuredHeight: Int,
) : Placeable() {
    override val width: Int = measuredWidth
    override val height: Int = measuredHeight

    override fun get(alignmentLine: AlignmentLine): Int {
        child.recordLineRead()
        return super.get(alignmentLine)
    }

    override fun alignmentLineAt(
        alignmentLine: AlignmentLine,
        x: Long,
        y: Long,
    ): Int {
        if (alignmentLine !== FirstBaseline) return AlignmentLine.UNSPECIFIED
        val baseline = child.layoutBaseline(measuredWidth, measuredHeight)
        return if (baseline < 0) AlignmentLine.UNSPECIFIED else saturateLineCoordinate(y + baseline)
    }
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
            modifier,
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

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? = with(PolicyMeasureScope) { with(modifier) { intrinsicPlaceable(measurable, width, height) } }

    override fun maximumSize(): Dimension? = measurable.maximumSize()
}

/**
 * One independent measurement of a Swing child. It holds the [lines][Constrainable.alignmentLines] a [Constrainable]
 * child provided for this measurement: for a non-container child, that answers for its own measurement after the
 * child is measured again. A Foundation container's lines instead follow the container's latest measure, as
 * androidx's `Placeable` does, worked out by the first read after its measure, and again by the first read after it
 * places its children again.
 */
internal class ChildPlaceable(
    override val child: ChildMeasurable,
    override val measuredWidth: Int,
    override val measuredHeight: Int,
    constraints: Constraints,
    private var cachedLines: Map<AlignmentLine, Int>,
) : PlacedPlaceable() {
    /** The [generation][LineMergingPlacementScope.generation] [cachedLines] were worked out at. */
    private var linesGeneration = Int.MIN_VALUE

    private val lines: Map<AlignmentLine, Int>
        get() {
            val held = child.decoratable?.decoration?.childMeasurables ?: return cachedLines
            val generation = held.lineScope.generation
            if (generation != linesGeneration && held.measured != null) {
                linesGeneration = generation
                cachedLines = checkNotNull(child.constrainable).alignmentLines
            }
            return cachedLines
        }

    override val node: LayoutModifierNode? get() = null

    override val width: Int = constraints.constrainWidth(measuredWidth)

    override val height: Int = constraints.constrainHeight(measuredHeight)

    override fun placeAt(
        x: Long,
        y: Long,
        zIndex: Float,
    ) {
        if (child.zIndex != zIndex) {
            child.zIndex = zIndex
            child.owner.zIndexChanged = true
        }
        val component = child.component
        val resized = child.layoutWidth != measuredWidth || child.layoutHeight != measuredHeight
        // A constrainable child answers its constraints whatever its size.
        val readable = resized && child.constrainable == null
        val read = if (readable) child.preferredAtOldSize() else null
        if (resized) child.preferred = null
        val placedAgain = child.lastPlacement != ChildPlacement.Placed
        // Where this child is already visible, showing it below reports nothing on its own: it owes this report itself.
        val reportsOwnPlacement = placedAgain && component.isVisible
        // Shown before its bounds are set, so fitting them to its paint outsets counts the child placed.
        child.show()
        val placedX = saturateLayoutCoordinate(x + centeredOverflowOffset(width, measuredWidth))
        val placedY = saturateLayoutCoordinate(y + centeredOverflowOffset(height, measuredHeight))
        // At the outsets the child holds, which its container fits once the layout nodes around it are placed.
        val origin = child.owner.panel.decoration.heldPaintOutsets
        val outsets = child.decoratable?.decoration?.heldPaintOutsets ?: NoPaintOutsets
        component.setBoundsAround(placedX + origin.left, placedY + origin.top, measuredWidth, measuredHeight, outsets)
        if (placedAgain) {
            // Ahead of the resize or move event setBounds posts, so that event finds nothing new to report.
            if (reportsOwnPlacement) component.reportPlacedAgain()
            if (!resized && child.decoratable != null) component.layOutAgain()
        }
        if (readable && child.answersOtherwiseThan(read)) child.owner.measured = null
        child.placedInRun = child.owner.placementRun
    }

    override fun mergeAlignmentLines(
        scope: LineMergingPlacementScope,
        x: Long,
        y: Long,
    ) {
        val originY = y + centeredOverflowOffset(height, measuredHeight)
        val lines = lines
        scope.merge(lines, x + centeredOverflowOffset(width, measuredWidth), originY)
        if (FirstBaseline in lines) return
        val baseline = child.layoutBaseline(measuredWidth, measuredHeight)
        if (baseline >= 0) scope.merge(FirstBaseline, originY + baseline)
    }

    override fun alignmentLineAt(
        alignmentLine: AlignmentLine,
        x: Long,
        y: Long,
    ): Int {
        val position = linePosition(alignmentLine)
        if (position == AlignmentLine.UNSPECIFIED) return AlignmentLine.UNSPECIFIED
        val coordinate =
            if (alignmentLine is HorizontalAlignmentLine) {
                y + centeredOverflowOffset(height, measuredHeight) + position
            } else {
                x + centeredOverflowOffset(width, measuredWidth) + position
            }
        return saturateLineCoordinate(coordinate)
    }

    /**
     * Where the child puts [alignmentLine] within the extent it measured, or [AlignmentLine.UNSPECIFIED] where it
     * puts none.
     */
    private fun linePosition(alignmentLine: AlignmentLine): Int {
        val provided = lines[alignmentLine]
        return when {
            provided != null -> {
                provided
            }

            alignmentLine === FirstBaseline -> {
                val baseline = child.layoutBaseline(measuredWidth, measuredHeight)
                if (baseline < 0) AlignmentLine.UNSPECIFIED else baseline
            }

            else -> {
                AlignmentLine.UNSPECIFIED
            }
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
        outerMeasurable == null -> layoutPreferredSize
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
    override val node: LayoutModifierNode,
    private val result: MeasureResult,
    constraints: Constraints,
    private val leftToRight: Boolean,
    override val child: ChildMeasurable,
) : PlacedPlaceable() {
    override val measuredWidth: Int get() = result.width
    override val measuredHeight: Int get() = result.height
    override val width: Int = constraints.constrainWidth(measuredWidth)
    override val height: Int = constraints.constrainHeight(measuredHeight)

    override fun placeAt(
        x: Long,
        y: Long,
        zIndex: Float,
    ) {
        val originX = x + centeredOverflowOffset(width, measuredWidth)
        val originY = y + centeredOverflowOffset(height, measuredHeight)
        val scope = NodePlacementScope(originX, originY, measuredWidth, leftToRight, zIndex, node)
        with(result) { scope.placeChildren() }
        // The component's layout origin, in the coordinates of its container's layout, once the content inside
        // this node is placed.
        val outsets = child.decoratable?.decoration?.heldPaintOutsets ?: return
        val origin = child.owner.panel.decoration.heldPaintOutsets
        val box = node.box ?: Rectangle().also { node.box = it }
        box.setBounds(
            saturateLayoutCoordinate(originX) - (child.component.x + outsets.left - origin.left),
            saturateLayoutCoordinate(originY) - (child.component.y + outsets.top - origin.top),
            measuredWidth,
            measuredHeight,
        )
    }

    /** The modifier's own [alignmentLine] where its result names one, and the content's otherwise. */
    override fun alignmentLineAt(
        alignmentLine: AlignmentLine,
        x: Long,
        y: Long,
    ): Int {
        val originX = x + centeredOverflowOffset(width, measuredWidth)
        val originY = y + centeredOverflowOffset(height, measuredHeight)
        return result.lineAt(alignmentLine, originX, originY, measuredWidth, leftToRight)
    }

    override fun mergeAlignmentLines(
        scope: LineMergingPlacementScope,
        x: Long,
        y: Long,
    ) {
        val originX = x + centeredOverflowOffset(width, measuredWidth)
        scope.merge(result, originX, y + centeredOverflowOffset(height, measuredHeight), leftToRight)
    }
}

/** The real child's centered displacement inside the apparent extent its parent arranged around. */
internal fun centeredOverflowOffset(
    apparent: Int,
    measured: Int,
): Long = (apparent.toLong() - measured.toLong()) / 2L

/**
 * Replays a [LayoutModifierNode]'s result into the coordinate system of the enclosing node or parent, placing with
 * the layer [layoutNode] owns.
 */
private class NodePlacementScope(
    private val originX: Long,
    private val originY: Long,
    override val parentWidth: Int,
    override val isLeftToRight: Boolean,
    private val zIndex: Float,
    private val layoutNode: LayoutModifierNode,
) : PlacementScope() {
    override fun Placeable.place(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        placeWithoutLayer(x.toLong(), y, zIndex)
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        placeWithoutLayer(relativeX(this, x), y, zIndex)
    }

    /**
     * @throws IllegalStateException if the node holds a decorator, or the component placed is not a [Decoratable].
     */
    override fun Placeable.placeWithLayer(
        x: Int,
        y: Int,
        zIndex: Float,
        layerBlock: PlacementLayerScope.() -> Unit,
    ) {
        if (this !is PlacedPlaceable) return
        val layer = layoutNode.layer
        val standing = layoutNode.decorationStep
        check(standing == null || standing === layer) {
            "A layout node holding a decorator cannot place its content with a layer, which paints in the " +
                "decorator's place. Declare the decorator on a node of its own."
        }
        child.requireDecoratable
        layer.placeWith(node, layerBlock) {
            placeAt(
                originX + x,
                originY + y,
                this@NodePlacementScope.zIndex + zIndex,
            )
        }
    }

    /**
     * Places the content plainly, clearing the decorator the node holds from placing it with a layer before, and
     * repainting its component.
     */
    private fun Placeable.placeWithoutLayer(
        x: Long,
        y: Int,
        zIndex: Float,
    ) {
        if (this !is PlacedPlaceable) return
        placeAt(
            originX + x,
            originY + y,
            this@NodePlacementScope.zIndex + zIndex,
        )
        layoutNode.layerOrNull?.placedPlainly()
    }
}

/**
 * The reading order the container carries, which a policy of this library resolves an [Arrangement] and
 * an [Alignment] against. [PlacementScope.isLeftToRight] is the whole of it a policy outside the library
 * needs; these two take the orientation itself.
 */
internal val PlacementScope.orientation: ComponentOrientation
    get() = if (isLeftToRight) ComponentOrientation.LEFT_TO_RIGHT else ComponentOrientation.RIGHT_TO_LEFT

/**
 * Where a policy places its children: inside the container's insets, whose origin every placement is
 * offset by, so a policy works in the coordinates it measured in. A child placed twice in one run of the
 * placement block fails.
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
        zIndex: Float,
    ) {
        placeOnce(originX.toLong() + x.toLong(), originY.toLong() + y.toLong(), zIndex, null)
    }

    override fun Placeable.placeRelative(
        x: Int,
        y: Int,
        zIndex: Float,
    ) {
        placeOnce(originX.toLong() + relativeX(this, x), originY.toLong() + y.toLong(), zIndex, null)
    }

    /** @throws IllegalStateException if the component placed is not a [Decoratable]. */
    override fun Placeable.placeWithLayer(
        x: Int,
        y: Int,
        zIndex: Float,
        layerBlock: PlacementLayerScope.() -> Unit,
    ) {
        placeOnce(originX.toLong() + x.toLong(), originY.toLong() + y.toLong(), zIndex, layerBlock)
    }

    /** @throws IllegalStateException if the component placed is not a [Decoratable]. */
    override fun Placeable.placeRelativeWithLayer(
        x: Int,
        y: Int,
        zIndex: Float,
        layerBlock: PlacementLayerScope.() -> Unit,
    ) {
        placeOnce(originX.toLong() + relativeX(this, x), originY.toLong() + y.toLong(), zIndex, layerBlock)
    }

    /**
     * Places the child at ([x], [y]), inside a layer that [layerBlock] sets, outside every step of its own modifier,
     * or without one where it is null, which removes the layer it was placed with before.
     */
    private fun Placeable.placeOnce(
        x: Long,
        y: Long,
        zIndex: Float,
        layerBlock: (PlacementLayerScope.() -> Unit)?,
    ) {
        if (this !is PlacedPlaceable) return
        val child = this.child
        check(!child.isPlacedByParent) {
            "Place was called on a node which was placed already"
        }
        if (layerBlock == null) {
            placeAt(x, y, zIndex)
            val steps = child.decoratable?.decoration?.steps
            steps?.containerLayer?.placedPlainly()
        } else {
            val decoratable = child.requireDecoratable
            val layer = decoratable.decoration.steps.containerLayer ?: PlacementLayer.ContainerLayer(child)
            layer.placeWith(node, layerBlock) { placeAt(x, y, zIndex) }
        }
        // The layout nodes and layers the paint outsets read have their boxes once every one around the child is
        // placed.
        child.owner.fitPaintOutsets(child)
    }
}

/** Reads behind what `preferredLayoutSize` answers, which Swing caches as the container's preferred size. */
private val PreferredReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }

/** Reads behind what `minimumLayoutSize` answers, which Swing caches as the container's minimum size. */
private val MinimumReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }

/** Reads behind what [ChildMeasurables.measuredSize] answers, which the parent lays out with. */
private val MeasuredReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }

/** Reads behind what [settledOn] answers, which this container places. */
private val SettledReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }

/** Reads behind the children's bounds, which the placement block sets. */
private val PlacementReads: (SwingComponentNode<ConstrainedPanel>) -> Unit = { it.component.placeChildrenAgain() }

/** Reads behind the lines [ChildMeasurables.alignmentLinesOf] works out for a result its container has not placed. */
private val LineReads: (SwingComponentNode<ConstrainedPanel>) -> Unit = {
    val panel = it.component
    if (panel.policyLayout.measurables.lineScope.observesReplay) panel.placeChildrenAgain()
}
