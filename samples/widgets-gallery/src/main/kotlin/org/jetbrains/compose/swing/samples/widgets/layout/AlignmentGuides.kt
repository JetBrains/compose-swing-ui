package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.DrawModifierNode
import org.jetbrains.compose.swing.foundation.graphics.decoration
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.translate
import org.jetbrains.compose.swing.foundation.graphics.invalidateDraw
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.PaintOutsets
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * Whether the gallery draws its alignment guides. The "Alignment guides" item of the View menu reaches the cards of
 * every section through it, and a section previewed on its own reads the default.
 */
@Suppress("CompositionLocalAllowlist")
internal val LocalAlignmentGuidesShown = compositionLocalOf { true }

/** A region of a guided child that [alignmentGuides] marks, inside the child's bounds. */
internal enum class GuideMark(
    val label: String,
    val color: Color,
) {
    /** The component's bounds, outlined. */
    Bounds("Bounds", AlignmentGuideColors.Bounds),

    /** The box the parent places and aligns, outlined over the bounds where the two coincide. */
    LayoutBox("Layout box", AlignmentGuideColors.LayoutBox),

    /** The ring between the bounds and the layout box, filled: as wide on each side as the paint outsets there. */
    PaintOutsets("Paint outsets", AlignmentGuideColors.PaintOutsets),
}

/**
 * A line a container aligns its children on. [alignmentGuides] draws it dashed, in [AlignmentGuideColors.Line]: across
 * the container where its content box defines the line, and inside each guided child where the child's layout box does.
 */
internal enum class GuideLine(
    val label: String,
    /** Whether the line runs along a row of pixels. */
    val horizontal: Boolean,
) {
    Top("Top edge", horizontal = true),

    /**
     * A center is the pixel past the middle of a box, `extent / 2`. A child of an even extent centered in an odd one
     * is half a pixel off the middle, and its tick shows one pixel off the container's line.
     */
    CenterVertically("Vertical center", horizontal = true),
    Bottom("Bottom edge", horizontal = true),

    /** The first baseline, which a container does not define: it is drawn in the children only. */
    FirstBaseline("First baseline", horizontal = true),
    Left("Left edge", horizontal = false),
    CenterHorizontally("Horizontal center", horizontal = false),
    Right("Right edge", horizontal = false),
}

/** Colors that read over a light and over a dark widget. The band lets the widget show through. */
internal object AlignmentGuideColors {
    val Bounds = Color(0x8A, 0x8F, 0x98)
    val LayoutBox = Color(0x1E, 0x88, 0xE5)
    val PaintOutsets = Color(0xFF, 0x98, 0x00, 0x60)
    val Line = Color(0xE6, 0x00, 0x7E)
}

/** The guided children of one container, in the order they joined. */
internal class GuidedChildren {
    val children = ArrayList<GuidedChild>()
}

/** A component that declares [alignmentGuide], as the container that draws its guides reads it. */
internal interface GuidedChild {
    /** What its guides are drawn from, in its parent's coordinates, or null while it is hidden. */
    fun boxes(): GuideBoxes?
}

/** What the guides of one child are drawn from. */
internal class GuideBoxes(
    val bounds: Rectangle,
    val layoutBox: Rectangle,
    /** The row of the first baseline, or null for a component without one. */
    val baselineY: Int?,
)

/**
 * Has the container that declares [alignmentGuides] with the same [guides] mark this component, a direct child of it.
 * The component works out its own boxes and repaints itself when they change with its declaration.
 *
 * [paintOutsets] must be the value the component is declared with or inherits: its layout box is then its layout
 * bounds moved in, per side, by the part of the insets the value names past its decoration's own outsets. Without a
 * value, the layout box is the layout bounds. The library hands no component the box its parent placed, so the box is
 * worked out again here, which holds for a decorated component whose only paint outsets are its decoration's.
 */
internal fun SwingModifier.alignmentGuide(
    guides: GuidedChildren,
    paintOutsets: PaintOutsets? = null,
): SwingModifier = this then AlignmentGuideElement(guides, paintOutsets)

/**
 * Draws guides over a Foundation container, after its content and only while [shown]: the [marks] of each visible
 * child that joined [guides], every band under every outline, then each of [lines]. The boxes are asked of the
 * children as the container paints.
 *
 * A child's marks and its part of a line lie inside the child's bounds, and the container's part of a line lies
 * outside them, so the repaints Swing makes for a child that moves, resizes, shows or hides keep the guides right.
 */
internal fun SwingModifier.alignmentGuides(
    guides: GuidedChildren,
    shown: Boolean,
    marks: Set<GuideMark>,
    lines: Set<GuideLine> = emptySet(),
): SwingModifier = if (shown) decoration(AlignmentGuidesElement(guides, marks, lines)) else this

/**
 * A swatch and the name of each of [marks] and [lines], while the guides are shown. A card that outlines both the
 * bounds and the layout box says that the second hides the first where they are equal. The swatch of a line runs the
 * way the line does.
 */
@Composable
internal fun AlignmentGuideLegend(
    marks: Set<GuideMark>,
    lines: Set<GuideLine> = emptySet(),
) {
    if (!LocalAlignmentGuidesShown.current) return
    val entries =
        marks.map { mark ->
            val covers = mark == GuideMark.LayoutBox && GuideMark.Bounds in marks
            LegendEntry(if (covers) "${mark.label} (hides equal bounds)" else mark.label) { drawSwatch(mark) }
        } +
            lines.map { line ->
                LegendEntry(line.label, upright = !line.horizontal) {
                    drawDashes(
                        from = 0,
                        to = if (line.horizontal) width else height,
                        at = (if (line.horizontal) height else width) / 2,
                        horizontal = line.horizontal,
                    )
                }
            }
    // Two to a row, so the legend fits a narrow window.
    Column(verticalArrangement = Arrangement.spacedBy(2)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { entry ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(
                            SwingModifier.preferredSize(if (entry.upright) Dimension(9, 20) else Dimension(20, 9)),
                            renderingHints = null,
                            onDraw = entry.swatch,
                        )
                        Label(entry.label)
                    }
                }
            }
        }
    }
}

private class LegendEntry(
    val label: String,
    /** Whether the swatch is taller than wide, as the swatch of a vertical line is. */
    val upright: Boolean = false,
    val swatch: DrawScope.() -> Unit,
)

/** A filled swatch for the band, and a line for an outline. */
private fun DrawScope.drawSwatch(mark: GuideMark) {
    if (mark == GuideMark.PaintOutsets) {
        drawRect(mark.color)
    } else {
        drawRect(mark.color, y = (height / 2).toFloat(), height = 1f)
    }
}

private data class AlignmentGuideElement(
    val guides: GuidedChildren,
    val paintOutsets: PaintOutsets?,
) : SwingModifier.NodeElement<JComponent, AlignmentGuideNode>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "alignmentGuide"

    override fun create(): AlignmentGuideNode = AlignmentGuideNode(guides, paintOutsets)

    override fun update(node: AlignmentGuideNode) {
        node.declare(guides, paintOutsets)
    }
}

/**
 * Keeps itself among [guides] while attached, and repaints its component as it joins, leaves or is declared anew: the
 * container that draws the guides paints with it, as the painting origin of its children.
 */
private class AlignmentGuideNode(
    private var guides: GuidedChildren,
    private var paintOutsets: PaintOutsets?,
) : SwingModifier.ComponentNode<JComponent>(),
    GuidedChild {
    override fun onAttach() {
        guides.children += this
        component.repaint()
    }

    fun declare(
        guides: GuidedChildren,
        paintOutsets: PaintOutsets?,
    ) {
        if (guides === this.guides && paintOutsets == this.paintOutsets) return
        if (guides !== this.guides) {
            this.guides.children -= this
            guides.children += this
            this.guides = guides
        }
        this.paintOutsets = paintOutsets
        component.repaint()
    }

    override fun onDetach() {
        guides.children -= this
        component.repaint()
    }

    override fun boxes(): GuideBoxes? {
        val target = component
        if (!target.isVisible) return null
        val bounds = target.bounds
        // No typed reference says whether the component paints through a decoration: a stock widget does not.
        val decoration = (target as? Decoratable)?.decoration
        val layoutBox = decoration?.localLayoutBounds(target) ?: Rectangle(bounds.size)
        layoutBox.translate(bounds.x, bounds.y)
        paintOutsets?.let { value ->
            val insets = target.insets
            val outsets = decoration?.paintOutsets() ?: Insets(0, 0, 0, 0)
            val named = value.outsetsOf(target, insets, outsets)
            // The part of the insets the value names, clamped as the library clamps it, past the decoration's outsets.
            val left = named.left.coerceIn(0, maxOf(insets.left, outsets.left, 0)) - outsets.left
            val top = named.top.coerceIn(0, maxOf(insets.top, outsets.top, 0)) - outsets.top
            val right = named.right.coerceIn(0, maxOf(insets.right, outsets.right, 0)) - outsets.right
            val bottom = named.bottom.coerceIn(0, maxOf(insets.bottom, outsets.bottom, 0)) - outsets.bottom
            layoutBox.setBounds(
                layoutBox.x + left,
                layoutBox.y + top,
                layoutBox.width - left - right,
                layoutBox.height - top - bottom,
            )
        }
        val baseline = target.getBaseline(bounds.width, bounds.height)
        return GuideBoxes(bounds, layoutBox, if (baseline < 0) null else bounds.y + baseline)
    }
}

private data class AlignmentGuidesElement(
    val guides: GuidedChildren,
    val marks: Set<GuideMark>,
    val lines: Set<GuideLine>,
) : SwingModifier.NodeElement<Container, AlignmentGuidesNode>() {
    override val targetType: Class<Container> get() = Container::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "alignmentGuides"

    override fun create(): AlignmentGuidesNode = AlignmentGuidesNode(this)

    override fun update(node: AlignmentGuidesNode) {
        node.declared = this
        node.invalidateDraw()
    }
}

private class AlignmentGuidesNode(
    var declared: AlignmentGuidesElement,
) : DrawModifierNode<Container>() {
    override fun ContentDrawScope.draw() {
        drawContent()
        val children = declared.guides.children.mapNotNull { it.boxes() }
        // A draw node's component always paints through a decoration, which no type of its own says.
        val layoutBounds = (component as Decoratable).decoration.localLayoutBounds(component)
        // The drawing starts at the container's layout bounds, and the children's boxes start at its bounds.
        translate(-layoutBounds.x.toFloat(), -layoutBounds.y.toFloat()) {
            val marks = declared.marks
            if (GuideMark.PaintOutsets in marks) children.forEach { drawBand(it) }
            if (GuideMark.Bounds in marks) children.forEach { drawOutline(GuideMark.Bounds.color, it.bounds) }
            if (GuideMark.LayoutBox in marks) children.forEach { drawOutline(GuideMark.LayoutBox.color, it.layoutBox) }
            // The bounds less the insets, which hold the border together with the paint outsets.
            val insets = component.insets
            val content =
                Rectangle(
                    insets.left,
                    insets.top,
                    component.width - insets.left - insets.right,
                    component.height - insets.top - insets.bottom,
                )
            declared.lines.forEach { drawAlignmentLine(it, layoutBounds, content, children) }
        }
    }
}

/** Fills the ring between the bounds and the layout box of [child], on the sides where the box is smaller. */
private fun DrawScope.drawBand(child: GuideBoxes) {
    val outer = child.bounds
    val inner = child.layoutBox.intersection(outer)
    val color = GuideMark.PaintOutsets.color
    fill(color, outer.x, outer.y, outer.width, inner.y - outer.y)
    fill(color, outer.x, inner.y + inner.height, outer.width, outer.y + outer.height - inner.y - inner.height)
    fill(color, outer.x, inner.y, inner.x - outer.x, inner.height)
    fill(color, inner.x + inner.width, inner.y, outer.x + outer.width - inner.x - inner.width, inner.height)
}

/** Draws the outermost pixels of [box], 1 px wide. */
private fun DrawScope.drawOutline(
    color: Color,
    box: Rectangle,
) {
    fill(color, box.x, box.y, box.width, 1)
    fill(color, box.x, box.y + box.height - 1, box.width, 1)
    fill(color, box.x, box.y, 1, box.height)
    fill(color, box.x + box.width - 1, box.y, 1, box.height)
}

/**
 * Draws [line]: across the container's [layoutBounds] where its [content] box defines it, in the gaps the [children]
 * leave, and inside each child where its layout box does, within its bounds. A center line enters a child only as a
 * solid tick of [CENTER_TICK] px from each side, which leaves its text clear.
 */
private fun DrawScope.drawAlignmentLine(
    line: GuideLine,
    layoutBounds: Rectangle,
    content: Rectangle,
    children: List<GuideBoxes>,
) {
    // Along a vertical line x and y trade places, so one walk serves both directions.
    fun Rectangle.oriented() = if (line.horizontal) this else Rectangle(y, x, height, width)
    val spans = children.map { it.bounds.oriented() }
    line.positionIn(content.oriented())?.let { at ->
        val across = layoutBounds.oriented()
        var from = across.x
        spans.filter { at >= it.y && at < it.y + it.height }.sortedBy { it.x }.forEach {
            drawDashes(from, it.x, at, line.horizontal)
            from = maxOf(from, it.x + it.width)
        }
        drawDashes(from, across.x + across.width, at, line.horizontal)
    }
    children.forEachIndexed { index, child ->
        val span = spans[index]
        val at = line.positionIn(child.layoutBox.oriented()) ?: child.baselineY ?: return@forEachIndexed
        if (line == GuideLine.CenterVertically || line == GuideLine.CenterHorizontally) {
            listOf(span.x, span.x + span.width - CENTER_TICK).forEach { from ->
                if (line.horizontal) {
                    fill(AlignmentGuideColors.Line, from, at, CENTER_TICK, 1)
                } else {
                    fill(AlignmentGuideColors.Line, at, from, 1, CENTER_TICK)
                }
            }
        } else {
            drawDashes(span.x, span.x + span.width, at, line.horizontal)
        }
    }
}

/**
 * Where this line crosses [box], a box laid along the line: its row of pixels. Null for the baseline, which a box
 * does not define.
 */
private fun GuideLine.positionIn(box: Rectangle): Int? =
    when (this) {
        GuideLine.Top, GuideLine.Left -> box.y
        GuideLine.CenterVertically, GuideLine.CenterHorizontally -> box.y + box.height / 2
        GuideLine.Bottom, GuideLine.Right -> box.y + box.height - 1
        GuideLine.FirstBaseline -> null
    }

/**
 * Draws the dashes of an alignment line between [from] and [to] along it, [at] the given row of pixels, or column
 * when not [horizontal]. The dashes start at the same coordinates on every line, so the parts of one line join.
 */
private fun DrawScope.drawDashes(
    from: Int,
    to: Int,
    at: Int,
    horizontal: Boolean,
) {
    var dash = from - Math.floorMod(from, DASH_PERIOD)
    while (dash < to) {
        val start = maxOf(dash, from)
        val length = minOf(dash + DASH_PERIOD / 2, to) - start
        if (horizontal) {
            fill(AlignmentGuideColors.Line, start, at, length, 1)
        } else {
            fill(AlignmentGuideColors.Line, at, start, 1, length)
        }
        dash += DASH_PERIOD
    }
}

/** Fills whole pixels, which no antialiasing spreads; nothing for an empty rectangle. */
private fun DrawScope.fill(
    color: Color,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    if (width > 0 && height > 0) drawRect(color, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
}

private const val DASH_PERIOD = 8
private const val CENTER_TICK = 4
