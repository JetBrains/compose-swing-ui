package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.CircleShape
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.border
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Cursor
import java.awt.Font

// Shadow styles from the Compose shadow guide, built from shadow, background and border alone.
@Composable
internal fun ColumnScope.FoundationShadowStylesCard() {
    var hovered by remember { mutableStateOf<ShadowStyle?>(null) }
    val surface = Color(0xE0, 0xE5, 0xEC)
    ExampleCard("Shadow styles") {
        WrappedCaption(
            "Elevation, a hard neo-brutalist offset, a colored glow, and a neumorphic pair of light and dark " +
                "shadows. Hover a tile to lift it.",
            width = 280,
        )
        Row(
            modifier =
                SwingModifier
                    .fillMaxWidth()
                    .height(120)
                    .background(surface, RectangleShape),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (style in ShadowStyle.entries) {
                ShadowStyleTile(
                    style,
                    lifted = hovered == style,
                    surface = surface,
                    modifier =
                        SwingModifier.onHover(
                            onEnter = { hovered = style },
                            onExit = { if (hovered == style) hovered = null },
                        ),
                )
            }
        }
    }
}

// A lifted tile casts a wider, further shadow, as a raised surface would.
@Composable
private fun RowScope.ShadowStyleTile(
    style: ShadowStyle,
    lifted: Boolean,
    surface: Color,
    modifier: SwingModifier = SwingModifier,
) {
    val lift = if (lifted) 1.6f else 1f
    Box(
        modifier =
            modifier
                .size(64, 64)
                .testTag(SHADOW_STYLE_TAG_PREFIX + style.name)
                .cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                .then(
                    when (style) {
                        ShadowStyle.Elevation -> {
                            SwingModifier
                                .shadow((8 * lift).toInt(), Color(0x1A, 0x23, 0x7E, 90), 0, (4 * lift).toInt())
                                .background(Color.WHITE, RoundedCornerShape(14f))
                        }

                        ShadowStyle.Brutal -> {
                            SwingModifier
                                .shadow(0, Color.BLACK, (5 * lift).toInt(), (5 * lift).toInt())
                                .background(Color(0xFF, 0xD5, 0x4F), RectangleShape)
                                .border(2, Color.BLACK)
                        }

                        ShadowStyle.Glow -> {
                            SwingModifier
                                .shadow((12 * lift).toInt(), Color(0xFF, 0x4E, 0x8A, 220))
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color(0xFF, 0x80, 0xAB),
                                        1f to Color(0xC5, 0x11, 0x62),
                                    ),
                                    CircleShape,
                                )
                        }

                        ShadowStyle.Soft -> {
                            val distance = (5 * lift).toInt()
                            SwingModifier
                                .shadow((7 * lift).toInt(), Color.WHITE, -distance, -distance)
                                .shadow((7 * lift).toInt(), Color(0xA3, 0xB1, 0xC6), distance, distance)
                                .background(surface, RoundedCornerShape(16f))
                        }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Label(
            style.name,
            modifier =
                SwingModifier
                    .font(Font(Font.SANS_SERIF, Font.BOLD, 11))
                    .foreground(if (style == ShadowStyle.Glow) Color.WHITE else Color(0x26, 0x32, 0x38)),
        )
    }
}

private enum class ShadowStyle { Elevation, Brutal, Glow, Soft }

internal const val SHADOW_STYLE_TAG_PREFIX = "foundation-shadow-style-"
