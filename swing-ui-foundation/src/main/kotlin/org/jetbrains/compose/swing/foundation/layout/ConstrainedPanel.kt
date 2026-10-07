package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.layoutHeight
import org.jetbrains.compose.swing.foundation.graphics.layoutWidth
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingComponentNodeListener
import org.jetbrains.compose.swing.node.asNodeOf
import org.jetbrains.compose.swing.node.invalidateLayout
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * The panel a [MeasurePolicyLayout] lays out, and the one component in this library that answers a
 * constrained question.
 *
 * Every container built on a measure policy is one of these - [Row], [Column], [Box] and a container a
 * caller writes - which is what lets constraints cross any depth of them and stop at the first stock
 * widget or container from elsewhere. It also carries Swing's viewport-scrolling contract.
 *
 * It extends [JComponent] rather than [javax.swing.JPanel], so it installs no UI delegate: a `PanelUI` would paint a
 * background of its own even where the panel answers `false` to [isOpaque]. It is transparent by default and paints
 * its background itself when opaque. [JComponent] does not implement [Accessible], so [getAccessibleContext] is
 * declared here as `JPanel.AccessibleJPanel` does. Without it, a name set through the `accessibleName` modifier has
 * nowhere to land.
 *
 * Records the reads behind its sizes, measure, placement and paint under the first attached node holding it, and
 * records them under the next one when that node detaches.
 *
 * Where two widths each make the other's height wrong, the panel keeps the result for the wider one until its content
 * changes.
 */
@Suppress("TooManyFunctions")
// The Swing component of a Foundation container: what Swing calls on a component, what its container's layout calls
// on it, and the validation it runs are its own.
internal open class ConstrainedPanel(
    val policyLayout: MeasurePolicyLayout,
) : JComponent(),
    Accessible,
    Scrollable,
    Constrainable,
    Decoratable,
    SwingComponentNodeListener {
    override var decoration: Decoration = Decoration.None

    init {
        layout = policyLayout
        policyLayout.measurables.panel = this
        linkTo(childMeasurables = policyLayout.measurables)
    }

    /** Declaration order, and the component array sorted by the z-index each child was last placed with. */
    val stackingOrder: StackingOrder = StackingOrder(this, policyLayout.measurables)

    override fun onAttached(node: SwingComponentNode<*>) {
        val nodes = policyLayout.nodes
        nodes += node.asNodeOf(this)
        // Answers worked out while no node was attached recorded no reads.
        if (nodes.size == 1) revalidate()
    }

    override fun onDetached(node: SwingComponentNode<*>) {
        val layout = policyLayout
        val recorded = node === layout.node
        layout.nodes -= node.asNodeOf(this)
        // The reads recorded under the detached node went with it.
        if (recorded && layout.node != null) {
            revalidate()
            repaint()
        }
    }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun getInsets(): Insets = decoration.insets(super.getInsets())

    override fun getInsets(insets: Insets?): Insets =
        decoration.insets(super.getInsets(insets), insets ?: Insets(0, 0, 0, 0))

    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)

    /**
     * A child repainting itself alone would otherwise be painted around this panel's decoration, or where it stands
     * unturned and unscaled while a layer rotates or scales it; see [paintImmediately]. The [glassPane] stands while
     * a layer does.
     */
    public override fun isPaintingOrigin(): Boolean = decoration.isDecorated || glassPane != null

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = decoration.repaint(this, tm, x, y, width, height)

    /**
     * A repaint a stock descendant records on itself and Swing merges into a dirty Swing ancestor is painted from that
     * ancestor alone, so no container between them that spreads or moves what it paints grows it, as for a `JLayer`.
     */
    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = decoration.paintImmediately(this, x, y, w, h) { grownX, grownY, grownWidth, grownHeight ->
        // Not the Rectangle overload, which calls back here.
        super.paintImmediately(grownX, grownY, grownWidth, grownHeight)
    }

    /**
     * Stands first among the children while one of them rotates or scales; see [updateGlassPane]. Every removal of
     * a child reaches [remove] or [removeAll], which give it up.
     */
    var glassPane: JComponent? = null
        private set

    /**
     * Adds the [glassPane] where [needed], which is while a placement layer rotates or scales a placed child, and
     * removes it otherwise.
     */
    fun updateGlassPane(needed: Boolean) {
        val standing = glassPane
        if (needed == (standing != null)) return
        policyLayout.measurables.during(RunningCause.GlassPaneChange) {
            if (standing == null) {
                val pane = GlassPane()
                pane.setBounds(0, 0, width, height)
                glassPane = pane
                add(pane, 0)
            } else {
                remove(standing)
            }
        }
    }

    /** What [paintBlock] paints into, held only while [paint] runs. */
    private var paintGraphics: Graphics? = null

    private val paintContent: (Graphics) -> Unit = { graphics ->
        val decoration = decoration
        val outsets = decoration.heldPaintOutsets
        val width = decoration.layoutWidth(this)
        val height = decoration.layoutHeight(this)
        if (super.isOpaque() && background != null) {
            graphics.color = background
            graphics.fillRect(outsets.left, outsets.top, width, height)
        }
        border?.paintBorder(this, graphics, outsets.left, outsets.top, width, height)
        paintChildren(graphics)
    }

    // Stored once and reading its graphics from a field, so observing a paint allocates nothing per call.
    private val paintBlock: () -> Unit = {
        decoration.paint(this, checkNotNull(paintGraphics), paintContent)
    }

    override fun paint(g: Graphics) {
        checkPreferences()
        paintGraphics = g
        try {
            policyLayout.observe(PaintReads, paintBlock)
        } finally {
            paintGraphics = null
        }
    }

    /**
     * The heights returned to a parent that is not a Foundation container at an inner [width]. A kind the parent has
     * not read at that width is null.
     */
    private class PreferencesAtWidth(
        val width: Int,
        val minimumHeight: Int?,
        val preferredHeight: Int?,
    )

    private var preferences: PreferencesAtWidth? = null

    /** The inner width of the last size preference read or paint. */
    internal val minimumSizeWidth: Int? get() = preferences?.width

    /**
     * The inner width the preferences last moved away from. While the panel is
     * laid out at that width, its preferences stay
     * those of the width they stand for, so two widths that each make the other's height wrong settle.
     */
    private var movedFrom: Int? = null

    /** Whether a layout at another width than the preferences' width ran since they last moved. */
    private var laidOutAway = false

    /**
     * The inner width the heights are reported at for a panel holding [held]: that width, or the wider of the two where
     * the panel is back at the width its preferences moved away from.
     */
    internal fun widthForPreferences(held: Int): Int {
        val standing = preferences ?: return held
        return if (held == movedFrom && laidOutAway) maxOf(held, standing.width) else held
    }

    /** Notes a layout of the panel at the inner [width]. */
    internal fun laidOutAt(width: Int) {
        if (preferences?.width != width) laidOutAway = true
    }

    /**
     * Notes the [mode] size preference reported to a parent that is not a Foundation
     * container, with its [height] at [width].
     */
    internal fun recordSizePreference(
        mode: MeasureMode,
        width: Int,
        height: Int,
    ) {
        val standing = preferences
        val kept = standing?.takeIf { it.width == width }
        preferences =
            PreferencesAtWidth(
                width,
                if (mode == MeasureMode.Minimum) height else kept?.minimumHeight,
                if (mode == MeasureMode.Preferred) height else kept?.preferredHeight,
            )
        if (standing != null && kept == null) {
            laidOutAway = false
            movedFrom = standing.width
            val before = if (mode == MeasureMode.Minimum) standing.minimumHeight else standing.preferredHeight
            // An invalid panel supplies preferences for its next layout; a valid panel invalidates bounds when the
            // advertised height changes.
            if (isValid && before != null && before != height) preferenceChanged()
        }
    }

    /** Ends the hold on the preferences: what the panel holds changed, so any width may need another height. */
    internal fun contentInvalidated() {
        movedFrom = null
    }

    private fun preferenceChanged() = policyLayout.measurables.during(RunningCause.ParentPlacement) { revalidate() }

    /**
     * Finds the panel laid out at an inner width other than the one it last returned
     * preferences at, as a wrapping `JTextArea` does
     * when it paints. The panel then returns a result at the new width, and
     * revalidates once where a height it returned is
     * another there, so the parent reads again. A panel back at the width its preferences moved away from keeps them. A
     * panel that has returned no preferences takes the width it is painted at as the width of its last preference read.
     */
    private fun checkPreferences() {
        val width = innerWidth()
        val recordedPreferences = preferences
        if (recordedPreferences == null) {
            if (width > 0) preferences = PreferencesAtWidth(width, null, null)
            return
        }
        if (width == recordedPreferences.width) movedFrom = null
        if (width == 0 || width == recordedPreferences.width || width == movedFrom) return
        val minimumHeight =
            recordedPreferences.minimumHeight?.let {
                observedHeight(
                    IntrinsicSize.Min,
                    width,
                    SizeReadKind.CheckedMinHeight,
                )
            }
        val preferredHeight =
            recordedPreferences.preferredHeight?.let {
                observedHeight(
                    IntrinsicSize.Max,
                    width,
                    SizeReadKind.CheckedMaxHeight,
                )
            }
        preferences = PreferencesAtWidth(width, minimumHeight, preferredHeight)
        if (minimumHeight != recordedPreferences.minimumHeight ||
            preferredHeight != recordedPreferences.preferredHeight
        ) {
            preferenceChanged()
        }
    }

    private fun observedHeight(
        intrinsicSize: IntrinsicSize,
        width: Int,
        reads: (SwingComponentNode<*>) -> Unit,
    ): Int = observedIntrinsic(intrinsicSize, IntrinsicWidthHeight.Height, width, reads)

    /**
     * A child's paint is observed by the child itself, so a paint that skips a child drops none of its
     * reads, and a plain component reading state while it paints is not observed.
     */
    override fun paintChildren(g: Graphics) = Snapshot.withoutReadObservation { super.paintChildren(g) }

    /**
     * Validates this panel and its descendants inside one snapshot that observes no read. Each answer a policy
     * computes during the validation observes its reads through that snapshot instead of entering one of its own, and
     * a read made outside an answer is not observed. The panel holds [RunningCause.Validation] while it runs.
     */
    override fun validateTree() =
        policyLayout.measurables.during(RunningCause.Validation) {
            Snapshot.withoutReadObservation { super.validateTree() }
        }

    override fun addImpl(
        comp: Component,
        constraints: Any?,
        index: Int,
    ) {
        if (comp === glassPane) return super.addImpl(comp, constraints, index)
        // In step, the add itself stacks the child where a restack would.
        val inStep = stackingOrder.isInStep
        try {
            super.addImpl(comp, constraints, stackingOrder.stackedIndex(comp, index))
        } finally {
            stackingOrder.declared(comp, index)
        }
        if (!inStep) stackingOrder.restack()
    }

    override fun remove(index: Int) {
        val child = getComponent(index)
        if (child === glassPane) glassPane = null else stackingOrder.dropped(child)
        super.remove(index)
    }

    override fun removeAll() {
        glassPane = null
        stackingOrder.cleared()
        super.removeAll()
    }

    /**
     * A child moved by a placement replay reports an invalidation that replay has already answered, and so
     * does the [StackingOrder.restack] it ends with: `setComponentZOrder` invalidates this panel, and a
     * change of z-order needs no layout. Neither does fitting this panel or a child to changed paint outsets, nor
     * adding or removing the [glassPane].
     *
     * Resized by its parent's fit, the panel keeps its layout bounds but answers other sizes, which carry its paint
     * outsets. It drops the sizes Swing caches for it and keeps what its last pass settled on; see
     * [ChildMeasurables.childPaintOutsetsChanged] for when it is laid out again.
     */
    override fun invalidate() {
        val measurables = policyLayout.measurables
        if (measurables.isFittingPaintOutsets && measurables.beingPlaced) return super.invalidate()
        if (measurables.run { isPlacingAgain || isChangingGlassPane || isFittingPaintOutsets }) return
        super.invalidate()
    }

    private var measured: Dimension = Dimension()

    final override val constrainedWidth: Int get() = measured.width

    final override val constrainedHeight: Int get() = measured.height

    /** Placed by its own parent; see [ChildMeasurables.during] for what that placement must not undo. */
    final override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Unit =
        policyLayout.measurables.during(RunningCause.ParentPlacement) {
            super.setBounds(x, y, width, height)
            glassPane?.setBounds(0, 0, width, height)
        }

    /**
     * A size set on the component outright answers for it, as it does for `getPreferredSize()`: setting
     * one overrides what the layout manager would work out, and a constrained question is the same
     * question asked with an argument. The policy measures only where nothing has been set.
     */
    final override fun measure(constraints: Constraints) {
        measured =
            if (isPreferredSizeSet) {
                super.getPreferredSize()
            } else {
                policyLayout.measurables.run { during(RunningCause.ParentPlacement) { measuredSize(constraints) } }
            }
    }

    /**
     * A size set on the component supplies the max intrinsic sizes, and a minimum size
     * set supplies the min intrinsic sizes, as
     * they provide `getPreferredSize()` and `getMinimumSize()`. Otherwise the
     * policy's own intrinsic functions return results,
     * inside the border insets, as androidx computes a layout's intrinsics from its measure policy.
     */
    final override fun minIntrinsicWidth(height: Int): Int =
        if (isMinimumSizeSet) {
            minimumSize.width
        } else {
            policyWidth(IntrinsicSize.Min, height)
        }

    final override fun maxIntrinsicWidth(height: Int): Int =
        if (isPreferredSizeSet) {
            preferredSize.width
        } else {
            policyWidth(IntrinsicSize.Max, height)
        }

    final override fun minIntrinsicHeight(width: Int): Int = intrinsicHeightUnder(IntrinsicSize.Min, 0, width)

    final override fun maxIntrinsicHeight(width: Int): Int = intrinsicHeightUnder(IntrinsicSize.Max, 0, width)

    /**
     * The [intrinsicSize] height under an offer of a width from [minWidth] up to [width], as a Foundation parent reads
     * it. A set size returns results as it does for [minIntrinsicHeight] and [maxIntrinsicHeight].
     */
    internal fun intrinsicHeightUnder(
        intrinsicSize: IntrinsicSize,
        minWidth: Int,
        width: Int,
    ): Int =
        when {
            intrinsicSize == IntrinsicSize.Min && isMinimumSizeSet -> minimumSize.height
            intrinsicSize == IntrinsicSize.Max && isPreferredSizeSet -> preferredSize.height
            else -> policyHeight(intrinsicSize, minWidth, width)
        }

    /**
     * The policy's [intrinsicSize] height at the inner [width], run under the inner offer from [minWidth], plus the
     * border's top and bottom insets. Read by a stock parent while invalid and not validating, the panel is laid out
     * next, so its stock children are sized to the specified width first.
     */
    private fun policyHeight(
        intrinsicSize: IntrinsicSize,
        minWidth: Int,
        width: Int,
    ): Int {
        val insets = borderInsets()
        val inner = innerExtent(width, insets.left, insets.right)
        val innerMinWidth = innerExtent(minWidth, insets.left, insets.right)
        val scope = widthOfferScope(innerMinWidth.coerceAtMost(inner))
        val height =
            policyLayout.measurables.run {
                // An invalid panel takes no invalidation from its children, so the settled-result mark is needed only
                // while it is valid.
                val cause =
                    if (stockParentLaysOutNext) {
                        RunningCause.ReadingSizePreferencesBeforeLayout
                    } else {
                        RunningCause.ParentPlacement
                    }
                val reads = if (intrinsicSize == IntrinsicSize.Min) SizeReadKind.MinHeight else SizeReadKind.MaxHeight
                during(cause) { observedIntrinsic(intrinsicSize, IntrinsicWidthHeight.Height, inner, reads, scope) }
            }
        return height.grownBy(insets.top.grownBy(insets.bottom))
    }

    /** Which intrinsic function of the policy [intrinsicBlock] runs, at which extent and in which scope. */
    private var intrinsicSize = IntrinsicSize.Max
    private var intrinsicAxis = IntrinsicWidthHeight.Width
    private var intrinsicExtent = 0
    private var intrinsicScope: IntrinsicMeasureScope = PolicyMeasureScope

    /** What [intrinsicBlock] returns. */
    private var intrinsicResult = 0

    // Stored once and reading its inputs from fields, so observing a result allocates nothing per call.
    private val intrinsicBlock: () -> Unit = {
        val extent = intrinsicExtent
        val scope = intrinsicScope
        val height = intrinsicAxis == IntrinsicWidthHeight.Height
        val max = intrinsicSize == IntrinsicSize.Max
        intrinsicResult =
            policyLayout.measurables.readPolicyPreferences { children ->
                with(scope) {
                    when {
                        height && max -> maxIntrinsicHeight(children, extent)
                        height -> minIntrinsicHeight(children, extent)
                        max -> maxIntrinsicWidth(children, extent)
                        else -> minIntrinsicWidth(children, extent)
                    }
                }
            }
    }

    /**
     * What the policy's [intrinsicSize] function on [axis] returns at the inner [extent] in [scope], its reads recorded
     * under [reads], a callback for this result alone, so recording them drops no reads behind another result.
     */
    internal fun observedIntrinsic(
        intrinsicSize: IntrinsicSize,
        axis: IntrinsicWidthHeight,
        extent: Int,
        reads: (SwingComponentNode<*>) -> Unit,
        scope: IntrinsicMeasureScope = PolicyMeasureScope,
    ): Int {
        this.intrinsicSize = intrinsicSize
        intrinsicAxis = axis
        intrinsicExtent = extent
        intrinsicScope = scope
        policyLayout.observe(reads, intrinsicBlock)
        return intrinsicResult
    }

    /** The policy's [intrinsicSize] width at the inner extent of [height], plus the border's left and right insets. */
    private fun policyWidth(
        intrinsicSize: IntrinsicSize,
        height: Int,
    ): Int {
        val insets = borderInsets()
        val inner = innerExtent(height, insets.top, insets.bottom)
        val width =
            policyLayout.measurables.run {
                val reads = if (intrinsicSize == IntrinsicSize.Min) SizeReadKind.MinWidth else SizeReadKind.MaxWidth
                during(RunningCause.ParentPlacement) {
                    observedIntrinsic(intrinsicSize, IntrinsicWidthHeight.Width, inner, reads)
                }
            }
        return width.grownBy(insets.left.grownBy(insets.right))
    }

    /**
     * The lines of the last [measure], moved by the insets it measured inside, worked out on each read; see
     * [ChildMeasurables.alignmentLinesOf]. None where a set preferred size answered it. A Foundation parent reads
     * them on its first read of a line after a measure, holds them with what it measured, and reads them again once
     * this panel places its children again.
     */
    final override val alignmentLines: Map<AlignmentLine, Int>
        get() {
            val result = policyLayout.measurables.measured
            if (isPreferredSizeSet || result == null) return emptyMap()
            val insets = borderInsets()
            return policyLayout.measurables.alignmentLinesOf(
                result,
                insets.left,
                insets.top,
                componentOrientation.isLeftToRight,
            )
        }

    override fun getAccessibleContext(): AccessibleContext =
        accessibleContext ?: AccessibleConstrainedPanel().also { accessibleContext = it }

    private inner class AccessibleConstrainedPanel : AccessibleJComponent() {
        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL

        /**
         * The children in paint order, which is declaration order where no child declares a z-index. [StackingOrder]
         * keeps the component array topmost first, so this lists it from the end.
         */
        override fun getAccessibleChild(i: Int): Accessible? = super.getAccessibleChild(accessibleChildrenCount - 1 - i)
    }

    override fun isOptimizedDrawingEnabled(): Boolean = false

    /**
     * The one child this panel answers a scroll pane for, or `null` where it holds anything but one [Scrollable]
     * child. A pane asks only the view it holds, so a panel wrapping content answers on that content's behalf, and
     * the content scrolls by its own rows or lines as it does without the panel.
     */
    private fun content(): Scrollable? {
        val first = stackingOrder.topmostIndex
        return if (componentCount == first + 1) getComponent(first) as? Scrollable else null
    }

    /** The one scrollable child's answer plus this panel's insets, so a border or a shadow around it is not clipped. */
    override fun getPreferredScrollableViewportSize(): Dimension {
        val content = content()?.preferredScrollableViewportSize ?: return preferredSize
        val insets = insets
        return Dimension(
            content.width.grownBy(insets.left + insets.right),
            content.height.grownBy(insets.top + insets.bottom),
        )
    }

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = content()?.getScrollableUnitIncrement(visibleRect, orientation, direction) ?: getFontMetrics(font).height

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int =
        content()?.getScrollableBlockIncrement(visibleRect, orientation, direction)
            ?: if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    /**
     * Whether the panel is laid out at the viewport's width: where its content asks for that, and where the viewport
     * is wider than the panel asks to be, which is the stretch a pane gives a view that is no [Scrollable].
     */
    override fun getScrollableTracksViewportWidth(): Boolean =
        content()?.scrollableTracksViewportWidth == true || fillsViewport { it.width }

    /** Whether the panel is laid out at the viewport's height; see [getScrollableTracksViewportWidth]. */
    override fun getScrollableTracksViewportHeight(): Boolean =
        content()?.scrollableTracksViewportHeight == true || fillsViewport { it.height }

    /** Captures input through transformed children while this panel holds one. */
    private inner class GlassPane : JComponent() {
        private var pressTarget: Component? = null

        /** The child of this panel holding [pressTarget] or being it; null where it is this panel. */
        private var pressTargetChild: Component? = null

        private var hovered: Component? = null

        /**
         * The child of this panel last found under the pointer, holding [hovered] or being it; stays set once [hovered]
         * becomes null for a pointer over an untransformed child.
         */
        private var hoveredChild: Component? = null

        init {
            enableEvents(
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK,
            )
            isFocusable = false
        }

        /** Claims only points over transformed children. */
        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = isTransformed(topmostChildAt(x, y))

        private fun isTransformed(child: Component?): Boolean =
            child != null &&
                policyLayout.measurables
                    .find(child)
                    ?.decoratable
                    ?.decoration
                    ?.steps
                    ?.isTransformed == true

        override fun paint(g: Graphics) = Unit

        override fun processEvent(event: AWTEvent) {
            if (event is MouseEvent) routeMouseEvent(event) else super.processEvent(event)
        }

        private fun routeMouseEvent(event: MouseEvent) {
            val x = event.x
            val y = event.y
            val exited = event.id == MouseEvent.MOUSE_EXITED
            val child = if (exited) null else topmostChildAt(x, y)
            val over = if (exited) null else transformedComponentAt(child, x, y)
            val overChild = child.takeIf { over !== this@ConstrainedPanel }
            updateHoverTarget(over.takeIf { it !== this@ConstrainedPanel && isTransformed(child) }, overChild, event)
            capturePress(event, over, overChild)
            val recipient = dispatchTargetFor(event, over, pressTarget)
            if (recipient != null) {
                val recipientChild = if (recipient === pressTarget) pressTargetChild else overChild
                dispatchMappedMouseEvent(recipient, recipientChild, event.id, event)
            }
            // Read once the event has reached it: a nested glass pane takes its cursor as it forwards the event.
            if (over != null && cursor !== over.cursor) cursor = over.cursor
        }

        private fun capturePress(
            event: MouseEvent,
            over: Component?,
            overChild: Component?,
        ) {
            if (event.id == MouseEvent.MOUSE_PRESSED && !event.isAnotherButtonHeld()) {
                pressTarget = over
                pressTargetChild = overChild
            }
        }

        /** Reports the pointer leaving [hovered] and entering [over], held by [overChild], where they differ. */
        private fun updateHoverTarget(
            over: Component?,
            overChild: Component?,
            cause: MouseEvent,
        ) {
            if (over === hovered) return
            hovered?.let { dispatchMappedMouseEvent(it, hoveredChild, MouseEvent.MOUSE_EXITED, cause) }
            hovered = over
            hoveredChild = overChild
            over?.let { dispatchMappedMouseEvent(it, overChild, MouseEvent.MOUSE_ENTERED, cause) }
        }

        /** Dispatches [sourceEvent] through [child]'s transform to [recipient], if it is still attached. */
        private fun dispatchMappedMouseEvent(
            recipient: Component,
            child: Component?,
            eventId: Int,
            sourceEvent: MouseEvent,
        ) {
            val point = pointInTarget(recipient, child, sourceEvent.x, sourceEvent.y) ?: return
            val event =
                if (eventId == sourceEvent.id) {
                    SwingUtilities.convertMouseEvent(this, sourceEvent, recipient).apply {
                        translatePoint(point.x - this.x, point.y - this.y)
                    }
                } else {
                    MouseEvent(
                        recipient,
                        eventId,
                        sourceEvent.`when`,
                        sourceEvent.modifiersEx,
                        point.x,
                        point.y,
                        0,
                        false,
                    )
                }
            recipient.dispatchEvent(event)
            // A consumed wheel event does not scroll anything outside the window either.
            if (event.isConsumed) sourceEvent.consume()
        }

        /**
         * The deepest visible component under ([x], [y]), in this panel's coordinates: [child], the one Swing's walk
         * enters, read through its layers once, then searched below it as Swing searches. The child's own `contains`
         * would read the point through its layers again, so the search starts at its children.
         */
        private fun transformedComponentAt(
            child: Component?,
            x: Int,
            y: Int,
        ): Component {
            val point =
                child?.let { component ->
                    val record = policyLayout.measurables.find(component)
                    record?.let { contentPoint(it, x - component.x, y - component.y, clipped = true) }
                }
            if (child == null || point == null) return this@ConstrainedPanel
            var found: Component? = null
            if (child is Container) {
                var index = 0
                while (found == null && index < child.componentCount) {
                    val inner = child.getComponent(index++)
                    if (inner.isVisible) {
                        found = SwingUtilities.getDeepestComponentAt(inner, point.x - inner.x, point.y - inner.y)
                    }
                }
            }
            return found ?: child
        }

        private fun topmostChildAt(
            x: Int,
            y: Int,
        ): Component? {
            for (index in 0 until this@ConstrainedPanel.componentCount) {
                val child = this@ConstrainedPanel.getComponent(index)
                if (child !== this && child.isVisible && child.contains(x - child.x, y - child.y)) return child
            }
            return null
        }

        /** The event point in [target]'s own coordinates, read through [child]'s layers. */
        private fun pointInTarget(
            target: Component,
            child: Component?,
            x: Int,
            y: Int,
        ): Point? {
            if (target !== this@ConstrainedPanel &&
                !SwingUtilities.isDescendingFrom(target, this@ConstrainedPanel)
            ) {
                return null
            }
            return if (child == null) {
                Point(x, y)
            } else {
                val record = policyLayout.measurables.find(child)
                val point = record?.let { contentPoint(it, x - child.x, y - child.y, clipped = false) }
                point?.let { SwingUtilities.convertPoint(child, it, target) }
            }
        }
    }
}

/** Reads a point through this child's paint transform, or returns null where clipping removes it. */
private fun contentPoint(
    child: ChildMeasurable,
    x: Int,
    y: Int,
    clipped: Boolean,
): Point? {
    val decoratable = child.decoratable ?: return Point(x, y)
    return decoratable.decoration.contentPoint(x, y, clipped)
}

/** The press target takes drags and releases; a click reaches it only while still under the pointer. */
private fun dispatchTargetFor(
    event: MouseEvent,
    over: Component?,
    pressTarget: Component?,
): Component? =
    when (event.id) {
        MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED -> null
        MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_RELEASED -> pressTarget
        MouseEvent.MOUSE_CLICKED -> pressTarget.takeIf { it === over }
        else -> over
    }

/** Whether another of the first three buttons was held already, which keeps the press target. */
private fun MouseEvent.isAnotherButtonHeld(): Boolean {
    val held = modifiersEx and InputEvent.getMaskForButton(button).inv()
    val buttons = InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK
    return held and buttons != 0
}

/** Whether this component's viewport is larger than its preferred size on [side]'s axis; `false` outside one. */
internal inline fun Component.fillsViewport(side: (Dimension) -> Int): Boolean {
    val viewport = parent as? JViewport ?: return false
    return side(viewport.size) > side(preferredSize)
}

/**
 * The size reads of a container that invalidate its layout when what they read
 * changes. Each size read records its reads
 * under the callback of its own entry, so recording one drops no reads behind another; see [observe].
 */
internal enum class SizeReadKind : (SwingComponentNode<*>) -> Unit {
    /** What [ConstrainedPanel.minIntrinsicWidth] returns. */
    MinWidth,

    /** What [ConstrainedPanel.maxIntrinsicWidth] returns. */
    MaxWidth,

    /** What [ConstrainedPanel.minIntrinsicHeight] returns a result under any width offer. */
    MinHeight,

    /** What [ConstrainedPanel.maxIntrinsicHeight] returns a result under any width offer. */
    MaxHeight,

    /** The min intrinsic height a paint last checked; see [ConstrainedPanel]. */
    CheckedMinHeight,

    /** The max intrinsic height a paint last checked; see [ConstrainedPanel]. */
    CheckedMaxHeight,
    ;

    override fun invoke(node: SwingComponentNode<*>) = node.invalidateLayout()
}

/** Reads behind the container's own pixels; its children's paint is observed by each child. */
private val PaintReads: (SwingComponentNode<*>) -> Unit = { it.component.repaint() }

/** This panel's layout width less its [insets] on the left and right: the width its policy lays out in. */
internal fun ConstrainedPanel.innerWidth(insets: Insets = borderInsets()): Int =
    innerExtent(decoration.layoutWidth(this), insets.left, insets.right)
