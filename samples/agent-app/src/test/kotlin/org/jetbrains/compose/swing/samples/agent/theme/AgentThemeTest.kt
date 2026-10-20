package org.jetbrains.compose.swing.samples.agent.theme

import java.awt.Color
import javax.swing.SwingUtilities
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentThemeTest {
    @Test
    fun secondaryTextReadsAtAaContrastOnEveryPaneInBothThemes() {
        for (mode in ThemeMode.entries) {
            val colors = colorsOf(mode)
            for ((name, ground) in listOf(
                "background" to colors.background,
                "surface" to colors.surface,
                "raised surface" to colors.raisedSurface,
            )) {
                assertReadable(colors.mutedText, ground, "$mode: muted text on the $name")
            }
        }
    }

    @Test
    fun textOnTheAccentReadsAtAaContrastInBothThemes() {
        for (mode in ThemeMode.entries) {
            val colors = colorsOf(mode)
            assertReadable(colors.accentText, colors.accent, "$mode: text on the accent")
        }
    }

    @Test
    fun raisedSurfacesAreNeverDarkerThanThePage() {
        for (mode in ThemeMode.entries) {
            val colors = colorsOf(mode)
            assertTrue(
                colors.raisedSurface.luminance() >= colors.surface.luminance(),
                "$mode: a raised surface lifts off the page",
            )
        }
    }

    private fun colorsOf(mode: ThemeMode): AgentColors {
        var colors: AgentColors? = null
        SwingUtilities.invokeAndWait {
            installLookAndFeel(mode)
            colors = readAgentTheme().colors
        }
        return checkNotNull(colors)
    }

    private fun assertReadable(
        foreground: Color,
        background: Color,
        what: String,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5, "$what is $ratio:1, below 4.5:1")
    }

    /** The relative luminance WCAG defines, from 0 for black to 1 for white. */
    private fun Color.luminance(): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    /** The WCAG contrast ratio of two colors, from 1 for equal colors to 21 for black on white. */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val (dark, light) = listOf(a.luminance(), b.luminance()).sorted()
        return (light + 0.05) / (dark + 0.05)
    }
}
