package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.TapStep
import org.jetbrains.compose.swing.foundation.graphics.blur
import org.jetbrains.compose.swing.foundation.graphics.channelDifference
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import java.awt.Color
import java.awt.Graphics
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
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

/** The interior is the frame shrunk by [reach], the effect's outsets around the content, and by one pixel for edges. */
internal fun assertKeptMatchesFresh(
    kept: BufferedImage,
    fresh: BufferedImage,
    fixture: FadeFixture,
    reach: Int,
) {
    val inset = reach + 1
    val width = kept.width - 2 * inset
    val height = kept.height - 2 * inset
    assertTrue(width > 0 && height > 0, "the interior of ${kept.width} by ${kept.height} inside $inset is empty")
    assertEquals(kept.width, fresh.width)
    assertEquals(kept.height, fresh.height)
    val keptInterior = kept.getSubimage(inset, inset, width, height)
    val difference = keptInterior.channelDifference(fresh.getSubimage(inset, inset, width, height))
    assertTrue(difference <= 1, "the kept frame differs from the fresh one by $difference levels inside the interior")
    assertTrue(kept.shows(fixture.color), "the kept frame does not show the current version of the content")
}

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

/** [tap] stands outermost among the content's steps. */
@Composable
internal fun FadeContent(
    fixture: FadeFixture,
    effect: FadeEffect,
    tap: TapStep? = null,
    side: Int = FADE_SIDE,
    child: (@Composable () -> Unit)? = null,
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
                    .preferredSize(side, side)
                    .then(tap ?: SwingModifier)
                    .then(effect.outer)
                    .then(if (effect.modifierLayer) SwingModifier.placementLayer(layer) else SwingModifier)
                    .then(
                        if (child == null) {
                            SwingModifier.drawBehind { fixture.paintVersion(graphics, width, height) }
                        } else {
                            SwingModifier
                        },
                    )
            SwingNode(factory = { ConstrainedPanel() }, modifier = modifier) { child?.invoke() }
        }
    }
}
