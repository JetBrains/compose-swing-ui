package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.TapElement
import org.jetbrains.compose.swing.foundation.graphics.blur
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.awt.Color
import java.awt.Graphics
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertTrue

internal const val FADE_SIDE = 80

internal class FadeFixture {
    var paints = 0

    // A content change moves it without any state read, which a repaint request makes known.
    private var version = 0

    var alpha by mutableFloatStateOf(0.9f)
    var clip by mutableStateOf(false)
    var scaleX by mutableFloatStateOf(1f)
    var layered by mutableStateOf(true)
    var contentModifier: SwingModifier = SwingModifier

    // The versions have colors of different hues.
    val color: Color get() = listOf(Color.RED, Color.BLUE, Color.GREEN)[version % 3]

    // The content shows the new version once it is repainted.
    fun bumpPlain() {
        version++
    }

    fun paintVersion(
        g: Graphics,
        width: Int,
        height: Int,
    ) {
        paints++
        val fill = color
        g.color = fill
        g.fillRect(0, 0, width, height)
        g.color = fill.darker()
        val stripe = 8
        for (x in stripe until width step 2 * stripe) g.fillRect(x, 0, stripe, height)
    }
}

internal fun assertKeptMatchesFresh(
    kept: BufferedImage,
    fresh: BufferedImage,
    fixture: FadeFixture,
) {
    assertImagesPixelPerfect(kept, fresh)
    assertTrue(kept.shows(fixture.color), "the kept frame does not show the current version of the content")
}

// The proportions of the channels decide, so a layer's alpha or a darker stripe of the same version still shows it.
internal fun BufferedImage.shows(color: Color): Boolean {
    val shown = Color(getRGB(width / 2, height / 2), true)
    if (shown.alpha == 0) return false

    fun Color.hue(): List<Double> = listOf(red, green, blue).let { c -> c.map { it.toDouble() / max(1, c.max()) } }
    return shown.hue().zip(color.hue()).all { (a, b) -> abs(a - b) < 0.1 }
}

internal fun placementLayers(): List<Arguments> =
    listOf(
        Arguments.of(Named.of("modifier layer", false)),
        Arguments.of(Named.of("container layer", true)),
    )

internal suspend fun ComposeSwingTest.fadeTo(
    fixture: FadeFixture,
    alpha: Float,
) {
    fixture.alpha = alpha
    awaitIdle()
}

/**
 * A property a layer block reads from a state, set to 0.5 for a red child of at least 40 by 20 pixels, which then
 * paints as [assertPainted] checks at (20, 10) and (5, 10).
 */
internal enum class LayerRead(
    val write: PlacementLayerScope.(Float) -> Unit,
    /** Whether a change draws the recording again, instead of repainting the component to record the content. */
    val redrawsRecording: Boolean,
    val assertPainted: BufferedImage.() -> Unit,
) {
    Alpha(
        write = { alpha = it },
        redrawsRecording = true,
        assertPainted = {
            assertPixelColor(Color(255, 0, 0, 128), 20, 10, "the faded layer keeps its color")
        },
    ),
    ScaleX(
        write = { scaleX = it },
        redrawsRecording = false,
        assertPainted = {
            assertPixelColor(Color.RED, 20, 10, "the scaled layer still paints its content")
            assertPixelColor(Color(0, true), 5, 10, "the scale leaves the edge transparent")
        },
    ),
}

/** The reads of the arguments of a case that runs once for each layer property a block can read. */
internal fun layerReads(): List<Arguments> = LayerRead.entries.map { Arguments.of(Named.of(it.name, it)) }

/** The fade does not decide the [reach], in logical units, of the effect outside its layers. */
internal enum class FadeEffect(
    val containerLayer: Boolean,
    val modifierLayer: Boolean,
    val outer: SwingModifier = SwingModifier,
    val reach: Int = 0,
) {
    ModifierLayer(containerLayer = false, modifierLayer = true),
    ContainerLayer(containerLayer = true, modifierLayer = false),
    ModifierLayerInContainerLayer(containerLayer = true, modifierLayer = true),
    ModifierLayerInShadow(
        containerLayer = false,
        modifierLayer = true,
        outer = SwingModifier.shadow(8, Color(0, 0, 0, 150)),
        reach = 8,
    ),
    ModifierLayerInBlur(containerLayer = false, modifierLayer = true, outer = SwingModifier.blur(4), reach = 4),
}

internal enum class FadeLeaf { NodeLeaf, CanvasLeaf, CanvasChild }

/** [tap] stands outermost among the content's steps. */
@Composable
internal fun FadeContent(
    fixture: FadeFixture,
    effect: FadeEffect,
    tap: TapElement? = null,
    side: Int = FADE_SIDE,
    child: (@Composable () -> Unit)? = null,
    leaf: FadeLeaf = FadeLeaf.NodeLeaf,
) {
    val layer: PlacementLayerScope.() -> Unit =
        remember(fixture) {
            {
                alpha = fixture.alpha
                clip = fixture.clip
                scaleX = fixture.scaleX
            }
        }
    Box(SwingModifier.testTag("root")) {
        Layout(measurePolicy = { measurables, constraints ->
            val placeable = measurables.single().measure(constraints)
            layout(placeable.width, placeable.height) {
                if (effect.containerLayer && fixture.layered) {
                    placeable.placeWithLayer(0, 0, layerBlock = layer)
                } else {
                    placeable.place(0, 0)
                }
            }
        }) {
            val modifier =
                SwingModifier
                    .testTag("content")
                    .styleable { preferredSize(side, side) }
                    .then(tap ?: SwingModifier)
                    .then(effect.outer)
                    .then(fixture.contentModifier)
                    .then(if (effect.modifierLayer) SwingModifier.placementLayer(layer) else SwingModifier)
                    .then(
                        if (child == null && leaf == FadeLeaf.NodeLeaf) {
                            SwingModifier.drawBehind { fixture.paintVersion(graphics, width, height) }
                        } else {
                            SwingModifier
                        },
                    )
            when (leaf) {
                FadeLeaf.NodeLeaf -> {
                    SwingNode(factory = { ConstrainedPanel() }, modifier = modifier) { child?.invoke() }
                }

                FadeLeaf.CanvasLeaf -> {
                    Canvas(modifier) { fixture.paintVersion(graphics, width, height) }
                }

                FadeLeaf.CanvasChild -> {
                    SwingNode(factory = { ConstrainedPanel() }, modifier = modifier) {
                        Canvas(SwingModifier.size(side, side)) { fixture.paintVersion(graphics, width, height) }
                    }
                }
            }
        }
    }
}
