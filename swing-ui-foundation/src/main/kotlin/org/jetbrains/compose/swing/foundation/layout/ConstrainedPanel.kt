package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.layoutHeight
import org.jetbrains.compose.swing.foundation.graphics.layoutWidth
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
 */
@Suppress("TooManyFunctions") // Swing and Constrainable require these methods on the panel.
internal open class ConstrainedPanel(
    val policyLayout: MeasurePolicyLayout,
) : JComponent(),
    Accessible,
    Scrollable,
    Constrainable,
    Decoratable {
    override var decoration: Decoration = Decoration.None

    init {
        layout = policyLayout
        policyLayout.measurables.panel = this
        linkTo(childMeasurables = policyLayout.measurables)
    }

    /** Declaration order, and the component array sorted by the z-index each child was last placed with. */
    val stackingOrder: StackingOrder = StackingOrder(this, policyLayout.measurables)

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
        paintGraphics = g
        try {
            policyLayout.observe(PaintReads, paintBlock)
        } finally {
            paintGraphics = null
        }
    }

    /**
     * A child's paint is observed by the child itself, so a paint that skips a child drops none of its
     * reads, and a plain component reading state while it paints is not observed.
     */
    override fun paintChildren(g: Graphics) = Snapshot.withoutReadObservation { super.paintChildren(g) }

    /**
     * Validates this panel and its descendants inside one snapshot that observes no read. Each answer a policy
     * computes during the validation observes its reads through that snapshot instead of entering one of its own, and
     * a read made outside an answer is not observed.
     */
    override fun validateTree() = Snapshot.withoutReadObservation { super.validateTree() }

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
     * [ChildMeasurables.childPaintOutsetsChanged] for its layout. Its parent, fitting it, lays nothing out.
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
    ) = policyLayout.measurables.during(RunningCause.ParentPlacement) {
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
                policyLayout.measurables.measuredSize(constraints)
            }
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

/** Reads behind the container's own pixels; its children's paint is observed by each child. */
private val PaintReads: (LayoutObservationNode) -> Unit = { it.component.repaint() }
