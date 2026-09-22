package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Runs [modifier], for a decoration declared on a component written outside a `Row`, `Column` or `Box`. */
internal fun decorated(modifier: () -> SwingModifier): SwingModifier = modifier()

internal fun SwingModifier.fill(color: Color): SwingModifier = decoration(Fill(color))

internal fun SwingModifier.spill(outsets: Insets): SwingModifier = decoration(Spill(outsets))

internal fun SwingModifier.indent(all: Int): SwingModifier = decoration(Indent(all))

internal fun SwingModifier.cut(arc: Int = ELLIPSE): SwingModifier = decoration(Cut(arc))

private const val ELLIPSE = Int.MAX_VALUE

/** Fills the area with [color], then paints the content over it, declaring [outsets] of its own. */
internal data class Fill(
    private val color: Color,
    override val outsets: Insets = Insets(0, 0, 0, 0),
) : Decorator {
    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        content(graphics, width, height)
    }
}

/** Paints everything inside it [inset] in from each edge, at the size left inside that inset. */
internal data class Indent(
    private val inset: Int,
) : Decorator {
    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.translate(inset, inset)
        content(graphics, (width - inset * 2).coerceAtLeast(0), (height - inset * 2).coerceAtLeast(0))
    }
}

/** Declares [declaredOutsets] around the content, and paints everything inside it unchanged. */
internal data class Spill(
    private val declaredOutsets: Insets,
) : Decorator {
    override val outsets: Insets get() = declaredOutsets.clone() as Insets

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ): Unit = content(graphics, width, height)
}

/** Cuts everything inside it to a rounded rectangle whose corners are [arc] across; the default cuts an ellipse. */
internal data class Cut(
    private val arc: Int = ELLIPSE,
) : Decorator {
    override val isOpaque: Boolean get() = false

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val across = minOf(arc, width).toFloat()
        val down = minOf(arc, height).toFloat()
        graphics.clip(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), across, down))
        content(graphics, width, height)
    }
}

/**
 * The outsets a [SwingModifier.blur] of [radius] reserves on each side for its falloff: those of a [BlurEffect] of
 * that radius with transparent edges, at a scale of `1.0`.
 */
internal fun blurOutsets(radius: Int): Int =
    BlurEffect(radius.toFloat(), edgeTreatment = TileMode.Decal).outsets(1.0).left

/** The side, in pixels, of the square stage [DecorationBuildersTest] and [FadeDecorationTest] paint into. */
internal const val STAGE_SIZE = 64

/** The paint outsets a [ReservingElement] reserves in [DecorationBuildersTest] and [FadeDecorationTest]. */
internal const val STAGE_OUTSETS = 8

/** Content filling its whole area with [color]. */
internal fun fill(color: Color): (Graphics2D, Int, Int) -> Unit =
    { graphics, width, height -> graphics.fill(color, 0, 0, width, height) }

/** Content that paints nothing. */
internal val PaintsNothing: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }

/** How much of the pixel at [x], [y] the painting covers, from `0` to `255`. */
internal fun BufferedImage.opacityAt(
    x: Int,
    y: Int,
): Int = getRGB(x, y) ushr 24

internal fun Graphics2D.fill(
    color: Color,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    paint = color
    fillRect(x, y, width, height)
}

private const val STAGE_TAG = "decorated"

internal fun ComposeSwingTest.stage(side: Int = STAGE_SIZE): Stage = Stage(this, side)

/**
 * One decorated component, composed once, that each [declare] redeclares: [side] square with its paint outsets,
 * the chain it is given outermost, and the content painting inside the chain in the component's layout
 * coordinates. [side] defaults to [STAGE_SIZE].
 */
internal class Stage(
    private val test: ComposeSwingTest,
    private val side: Int = STAGE_SIZE,
) {
    private var declared by mutableStateOf<() -> SwingModifier>({ SwingModifier })
    private var content by mutableStateOf<Decorator?>(null)
    private var size by mutableStateOf(Dimension(side, side))

    init {
        test.setContent {
            Box {
                SwingNode(
                    factory = { DecoratedPanel() },
                    modifier =
                        SwingModifier
                            .testTag(STAGE_TAG)
                            .opaque(false)
                            .preferredSize(size)
                            .then(declared())
                            .then(content?.let { SwingModifier.decoration(it) } ?: SwingModifier),
                )
            }
        }
    }

    /**
     * Declares [chain] around [content], or [chain] alone where [content] is null, sized so the component's
     * bounds are [side] square.
     */
    suspend fun declare(
        chain: () -> SwingModifier = { SwingModifier },
        content: Decorator? = null,
    ): DecoratedPanel {
        declared = chain
        this.content = content
        test.awaitIdle()
        val outsets = component().decoration.paintOutsets()
        size = Dimension(side - outsets.left - outsets.right, side - outsets.top - outsets.bottom)
        test.awaitIdle()
        return component()
    }

    /** What the component shows with [chain] declared around [content]. */
    suspend fun paint(
        chain: () -> SwingModifier = { SwingModifier },
        content: (Graphics2D, Int, Int) -> Unit,
    ): BufferedImage {
        declare(chain, Content(content))
        return test.onNodeWithTag(STAGE_TAG).captureToImage()
    }

    /**
     * What the component prints with [chain] declared around [content], at [scale] device pixels per unit onto
     * an image [setUp] prepares first.
     */
    suspend fun print(
        chain: () -> SwingModifier = { SwingModifier },
        content: (Graphics2D, Int, Int) -> Unit,
        scale: Int = 1,
        setUp: (Graphics2D) -> Unit = {},
    ): BufferedImage {
        val component = declare(chain, Content(content))
        return renderImage(STAGE_SIZE * scale, STAGE_SIZE * scale) { graphics ->
            setUp(graphics)
            graphics.scale(scale.toDouble(), scale.toDouble())
            component.printAll(graphics)
        }
    }

    private fun component(): DecoratedPanel = test.onNodeWithTag(STAGE_TAG).fetch<DecoratedPanel>()
}

/** Paints [paint] as the content, inside every decoration declared before it. */
private data class Content(
    private val paint: (Graphics2D, Int, Int) -> Unit,
) : Decorator {
    override val isOpaque: Boolean get() = false

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) = paint(graphics, width, height)
}

/** A decoration step of one's own, painting its content unchanged inside [outsets], which the hand-over gathers. */
internal data class ReservingElement(
    private val outsets: Insets,
) : SwingModifier.NodeElement<Component, ReservingNode>() {
    override val name: String get() = "reserving"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ReservingNode = ReservingNode(outsets)

    override fun update(node: ReservingNode) {
        node.reservedOutsets = outsets
    }
}

internal class ReservingNode(
    var reservedOutsets: Insets,
) : DecorationModifierNode<Component>() {
    override val outsets: Insets get() = reservedOutsets

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ): Unit = content(graphics, width, height)
}
