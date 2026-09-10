// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("SvgKt")

package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.renderer.PlatformSupport
import com.github.weisj.jsvg.renderer.animation.AnimationState
import com.github.weisj.jsvg.renderer.awt.AwtComponentPlatformSupport
import com.github.weisj.jsvg.renderer.output.Output
import com.github.weisj.jsvg.view.ViewBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingComponentNodeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.svg.style.SvgImages
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.ceil

/**
 * Draws [document].
 *
 * The drawing stays sharp on a scaled display and inside the surface's border. The preferred size
 * includes the document's intrinsic size and the border insets. Override it with
 * [org.jetbrains.compose.swing.style.preferredSize].
 *
 * An animated document plays while composed, paced by the window's frame clock, and honors
 * [org.jetbrains.compose.swing.core.MotionDurationScale]. Static documents ignore [animation].
 *
 * @param document the SVG to draw.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param style the [SwingStyle] applied after styles inherited through [SvgImages].
 * @param animation playback state for an animated [document]. With `null`, each new animated
 *   document starts from the beginning. A supplied state keeps its position when [document] changes.
 *   Several images can share one state.
 * @param antialias whether to smooth shape edges. Defaults to `true`.
 */
@Composable
public fun SvgImage(
    document: SVGDocument,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    animation: SvgAnimationState? = null,
    antialias: Boolean = true,
) {
    val playback = if (document.isAnimated) animation ?: key(document) { rememberSvgAnimationState() } else null
    SwingNode(
        factory = { SvgSurface(document) },
        modifier = modifier.svgPlayback(document, playback).styleable(SvgImageKind, style),
        update = {
            update(document) { this.document = it }
            set(playback) { this.animation = it }
            set(antialias) { this.antialias = it }
        },
    )
}

private val SvgImageKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(SvgImages)
    }

/** The Swing surface [SvgImage] draws its document on. */
internal class SvgSurface(
    document: SVGDocument,
) : JComponent(),
    Accessible,
    SwingComponentNodeListener {
    private val nodes = mutableListOf<SwingComponentNode<*>>()

    /** The document to draw. Setting another one repaints, and revalidates when its intrinsic size differs. */
    var document: SVGDocument = document
        set(value) {
            val sizeChanged = field.intrinsicSize() != value.intrinsicSize()
            field = value
            if (sizeChanged) revalidate()
            repaint()
        }

    /** The playback state whose time is drawn, `null` for a static document. Setting it repaints. */
    var animation: SvgAnimationState? = null
        set(value) {
            if (field != null) nodes.firstOrNull()?.observeReads(repaintOnTimeChanged) {}
            field = value
            repaint()
        }

    /** Whether the drawing is antialiased. Setting it repaints. */
    var antialias: Boolean = true
        set(value) {
            field = value
            repaint()
        }

    /**
     * Passes this component to the renderer, which takes the font family of text from it and uses it
     * as the image observer and repaint target of the images the document refers to.
     */
    private val platformSupport = AwtComponentPlatformSupport(this)

    init {
        // Paints no background unless made opaque: whatever sits behind shows through untouched pixels.
        isOpaque = false
    }

    override fun onAttached(node: SwingComponentNode<*>) {
        val previous = nodes.firstOrNull()
        nodes += node
        repaintIfPaintNodeChanged(previous)
    }

    override fun onDetached(node: SwingComponentNode<*>) {
        val previous = nodes.firstOrNull()
        nodes -= node
        repaintIfPaintNodeChanged(previous)
    }

    private fun repaintIfPaintNodeChanged(previous: SwingComponentNode<*>?) {
        val current = nodes.firstOrNull() ?: return
        if (current !== previous && animation != null) repaint()
    }

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val size = document.intrinsicSize()
        val border = insets
        return Dimension(size.width + border.left + border.right, size.height + border.top + border.bottom)
    }

    override fun paintComponent(g: Graphics) {
        // No UI delegate fills the background here, and an opaque component must paint all of its bounds.
        if (isOpaque) {
            g.color = background
            g.fillRect(0, 0, width, height)
            // The renderer resolves currentColor from the Graphics color.
            g.color = foreground
        }
        // The drawing stays inside the border, as a widget paints inside its own border.
        val border = insets
        val drawingWidth = width - border.left - border.right
        val drawingHeight = height - border.top - border.bottom
        // A component smaller than its border leaves the drawing no space.
        if (drawingWidth <= 0 || drawingHeight <= 0) return
        val viewBox =
            ViewBox(border.left.toFloat(), border.top.toFloat(), drawingWidth.toFloat(), drawingHeight.toFloat())
        val playback = animation
        if (playback == null) {
            drawDocument(g, viewBox, 0L)
            return
        }
        val node = if (SwingUtilities.isEventDispatchThread()) nodes.firstOrNull() else null
        if (node == null) {
            drawDocument(g, viewBox, playback.time.inWholeMilliseconds)
        } else {
            node.observeReads(repaintOnTimeChanged) {
                drawDocument(g, viewBox, playback.time.inWholeMilliseconds)
            }
        }
    }

    private fun drawDocument(
        graphics: Graphics,
        viewBox: ViewBox,
        timeMillis: Long,
    ) {
        val end = if (document.isAnimated) document.animation().endTime() else Long.MAX_VALUE
        val renderTime =
            if (timeMillis < end || end == Long.MAX_VALUE) {
                timeMillis
            } else {
                // JSVG compares float iteration counts, so a finished track needs a time well beyond its end.
                end + minOf(end / 8 + 1, Long.MAX_VALUE - end)
            }
        document.draw(graphics, platformSupport, viewBox, renderTime, antialias)
    }

    /**
     * Returns a context with the role [AccessibleRole.ICON]. A plain [JComponent] would otherwise have the
     * generic [AccessibleRole.SWING_COMPONENT], which understates a picture. This class is [Accessible]
     * so that its parent's accessible children include it. Name it through the accessible-name style
     * setter (see [org.jetbrains.compose.swing.style.accessibleName]).
     */
    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
            accessibleContext =
                object : AccessibleJComponent() {
                    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.ICON
                }
        }
        return accessibleContext
    }

    private companion object {
        val repaintOnTimeChanged: (SwingComponentNode<*>) -> Unit = { it.component.repaint() }
    }
}

/**
 * Returns the document's intrinsic size, rounded up to whole units. An SVG is drawn at this size when
 * nothing else decides: it is the preferred size of [SvgImage]'s surface and the default size of the icon
 * from [rememberSvgIcon].
 */
internal fun SVGDocument.intrinsicSize(): Dimension {
    val size = size()
    return Dimension(ceil(size.width).toInt(), ceil(size.height).toInt())
}

/**
 * Draws this document onto [graphics] inside [viewBox]. The shapes are antialiased when [antialias] is `true`.
 *
 * @param graphics the graphics to draw onto.
 * @param platformSupport the support that resolves the fonts and the images the document refers to.
 * @param viewBox the area of [graphics] the document fills.
 * @param timeMillis the position on the document's timeline that is drawn.
 * @param antialias whether the shapes are antialiased.
 */
internal fun SVGDocument.draw(
    graphics: Graphics,
    platformSupport: PlatformSupport,
    viewBox: ViewBox,
    timeMillis: Long,
    antialias: Boolean,
) {
    val width = viewBox.width.toInt()
    val height = viewBox.height.toInt()
    if (width <= 0 || height <= 0) return

    if (graphics is Graphics2D) {
        draw(graphics, platformSupport, viewBox, timeMillis, antialias)
    } else {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val imageGraphics = image.createGraphics()
        try {
            // currentColor and text resolve from the graphics the caller painted with.
            imageGraphics.color = graphics.color
            imageGraphics.font = graphics.font
            draw(
                imageGraphics,
                platformSupport,
                ViewBox(0f, 0f, width.toFloat(), height.toFloat()),
                timeMillis,
                antialias,
            )
        } finally {
            imageGraphics.dispose()
        }
        graphics.drawImage(image, viewBox.x.toInt(), viewBox.y.toInt(), null)
    }
}

private fun SVGDocument.draw(
    graphics: Graphics2D,
    platformSupport: PlatformSupport,
    viewBox: ViewBox,
    timeMillis: Long,
    antialias: Boolean,
) {
    val output = Output.createForGraphics(graphics)
    try {
        val hint = if (antialias) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF
        output.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint)
        output.applyClip(viewBox)
        renderWithPlatform(platformSupport, output, viewBox, AnimationState(0, timeMillis))
    } finally {
        output.dispose()
    }
}
