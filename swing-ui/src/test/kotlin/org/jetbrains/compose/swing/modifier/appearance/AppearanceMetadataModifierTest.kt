package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.border.Border
import javax.swing.border.LineBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the appearance and metadata [SwingModifier]s that lack a dedicated suite:
 * font, border, cursor, clientProperty, focusable and preferredSize. Each test asserts the applied
 * Swing property AND its restoration to the pre-modifier default once the element leaves the chain -
 * the round-trip contract every property element promises.
 */
class AppearanceMetadataModifierTest {
    @Test
    fun fontModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        val custom = Font("Monospaced", Font.BOLD, 22)
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label("styled", modifier = if (styled) SwingModifier.font(custom) else SwingModifier)
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().font
        assertEquals(custom, styledLabel.fetch<JLabel>().font, "the custom font should apply while present")

        styled = false
        awaitIdle()
        // The element left the chain, so the font is restored to the pre-modifier default.
        assertEquals(default, styledLabel.fetch<JLabel>().font, "removing the modifier should restore the default font")
    }

    @Test
    fun borderModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        val custom: Border = BorderFactory.createLineBorder(Color.RED, 3)
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label("styled", modifier = if (styled) SwingModifier.border(custom) else SwingModifier)
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().border
        assertSame(custom, styledLabel.fetch<JLabel>().border, "the custom border should apply while present")

        styled = false
        awaitIdle()
        // The element left the chain, so the border is restored to the pre-modifier default (the
        // same one the untouched control still shows).
        assertSame(
            default,
            styledLabel.fetch<JLabel>().border,
            "removing the modifier should restore the default border",
        )
    }

    @Test
    fun lineBorderAppliesTheColorAndThickness() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.lineBorder(Color.RED, 3)) }
        val border = onNodeOfType<JLabel>().fetch<JLabel>().border
        assertIs<LineBorder>(border, "the builder should install a line border")
        assertEquals(Color.RED, border.lineColor, "the declared color should reach the border")
        assertEquals(3, border.thickness, "the declared thickness should reach the border")
    }

    @Test
    fun lineBorderDefaultsToASingleThickness() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.lineBorder(Color.BLUE)) }
        val border = onNodeOfType<JLabel>().fetch<JLabel>().border
        assertIs<LineBorder>(border, "the builder should install a line border")
        assertEquals(1, border.thickness, "the default thickness should match the line Swing itself draws")
    }

    @Test
    fun emptyBorderAppliesTheInsets() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.emptyBorder(1, 2, 3, 4)) }
        val label = onNodeOfType<JLabel>().fetch<JLabel>()
        assertEquals(
            Insets(1, 2, 3, 4),
            label.border.getBorderInsets(label),
            "the sides should be taken in top, left, bottom, right order",
        )
    }

    @Test
    fun emptyBorderInsetsFormAppliesTheInsets() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.emptyBorder(Insets(1, 2, 3, 4))) }
        val label = onNodeOfType<JLabel>().fetch<JLabel>()
        assertEquals(Insets(1, 2, 3, 4), label.border.getBorderInsets(label), "the insets should reach the border")
    }

    @Test
    fun emptyBorderUniformFormAppliesEverySide() = runComposeSwingTest {
        setContent { Label("X", modifier = SwingModifier.emptyBorder(5)) }
        val label = onNodeOfType<JLabel>().fetch<JLabel>()
        assertEquals(Insets(5, 5, 5, 5), label.border.getBorderInsets(label), "one count should cover every side")
    }

    @Test
    fun lineBorderKeepsTheSameBorderWhileItsValuesAreUnchanged() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent { Label(caption, modifier = SwingModifier.lineBorder(Color.RED, 2)) }
        val label = onNodeOfType<JLabel>()
        val first = label.fetch<JLabel>().border

        caption = "second"
        awaitIdle()
        // The declaration is made of values, so a recomposition that leaves them alone leaves the
        // border the component already holds in place instead of exchanging it for an equal one.
        assertSame(first, label.fetch<JLabel>().border, "an unchanged declaration should keep the border")
    }

    @Test
    fun lineBorderReplacesTheBorderWhenItsColorChanges() = runComposeSwingTest {
        var color by mutableStateOf(Color.RED)
        setContent { Label("X", modifier = SwingModifier.lineBorder(color)) }
        val label = onNodeOfType<JLabel>()
        val first = label.fetch<JLabel>().border

        color = Color.BLUE
        awaitIdle()
        val second = label.fetch<JLabel>().border
        assertNotSame(first, second, "a changed color should rebuild the border")
        assertIs<LineBorder>(second, "the rebuilt border should still be a line border")
        assertEquals(Color.BLUE, second.lineColor, "the new color should reach the border")
    }

    @Test
    fun lineBorderRestoresTheDefaultOnRemoval() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label("styled", modifier = if (styled) SwingModifier.lineBorder(Color.RED) else SwingModifier)
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().border
        assertIs<LineBorder>(styledLabel.fetch<JLabel>().border, "the line border should apply while present")

        styled = false
        awaitIdle()
        assertSame(
            default,
            styledLabel.fetch<JLabel>().border,
            "removing the modifier should restore the default border",
        )
    }

    @Test
    fun theLastBorderDeclarationInTheChainWins() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label(
                "styled",
                modifier = if (styled) SwingModifier.lineBorder(Color.RED).emptyBorder(4) else SwingModifier,
            )
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().border
        val label = styledLabel.fetch<JLabel>()
        // A line border of the default thickness would report 1 on every side, and the two of them
        // compounded would report 5: the component carries the last declaration alone.
        assertEquals(
            Insets(4, 4, 4, 4),
            label.border.getBorderInsets(label),
            "the last declaration should own the component's one border",
        )

        styled = false
        awaitIdle()
        // Both declarations shared the one slot, so removing them puts back the border the component
        // started with rather than the one the earlier declaration would have captured.
        assertSame(
            default,
            styledLabel.fetch<JLabel>().border,
            "removing both declarations should restore the default border",
        )
    }

    @Test
    fun cursorModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        val hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label("styled", modifier = if (styled) SwingModifier.cursor(hand) else SwingModifier)
        }
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().cursor
        assertEquals(hand, styledLabel.fetch<JLabel>().cursor, "the custom cursor should apply while present")

        styled = false
        awaitIdle()
        assertEquals(
            default,
            styledLabel.fetch<JLabel>().cursor,
            "removing the modifier should restore the default cursor",
        )
    }

    @Test
    fun clientPropertyModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        val key = "JComponent.sizeVariant"
        var styled by mutableStateOf(true)
        setContent {
            Label("X", modifier = if (styled) SwingModifier.clientProperty(key, "small") else SwingModifier)
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(
            "small",
            label.fetch<JLabel>().getClientProperty(key),
            "the client property should apply while present",
        )

        styled = false
        awaitIdle()
        // The element left the chain, so the client property is restored to its prior (null) value.
        assertNull(
            label.fetch<JLabel>().getClientProperty(key),
            "removing the modifier should clear the client property",
        )
    }

    @Test
    fun distinctClientPropertiesAreIndependentSlots() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.clientProperty("k1", "v1").clientProperty("k2", "v2"))
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals("v1", label.getClientProperty("k1"), "key k1 should hold its own value")
        assertEquals("v2", label.getClientProperty("k2"), "key k2 should hold its own value")
    }

    @Test
    fun focusableModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var styled by mutableStateOf(true)
        setContent {
            Label("untouched")
            Label("styled", modifier = if (styled) SwingModifier.focusable(false) else SwingModifier)
        }
        // Every AWT component reports itself focusable until something says otherwise; the modifier
        // is what says otherwise.
        val styledLabel = onNodeWithText("styled")
        val default = onNodeWithText("untouched").fetch<JLabel>().isFocusable
        assertFalse(styledLabel.fetch<JLabel>().isFocusable, "the modifier should make the label unfocusable")

        styled = false
        awaitIdle()
        // The element left the chain, so isFocusable is restored to the untouched control's default.
        assertEquals(
            default,
            styledLabel.fetch<JLabel>().isFocusable,
            "removing the modifier should restore the default focusability",
        )
    }

    @Test
    fun preferredSizeModifierAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var sized by mutableStateOf(true)
        setContent {
            Label("X", modifier = if (sized) SwingModifier.preferredSize(Dimension(123, 45)) else SwingModifier)
        }
        val sizedLabel = onNodeOfType<JLabel>().fetch()
        assertEquals(Dimension(123, 45), sizedLabel.preferredSize, "the preferred size should apply while present")
        assertTrue(sizedLabel.isPreferredSizeSet, "the preferred-size-set flag should be on while present")

        sized = false
        awaitIdle()
        // The element left the chain, so the explicit preferred size is cleared again.
        assertFalse(
            onNodeOfType<JLabel>().fetch().isPreferredSizeSet,
            "removing the modifier should clear the preferred-size-set flag",
        )
    }

    @Test
    fun preferredSizeWidthHeightOverloadAppliesTheDimension() = runComposeSwingTest {
        setContent {
            Label("X", modifier = SwingModifier.preferredSize(123, 45))
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Dimension(123, 45), label.preferredSize, "the overload should apply the dimension")
        assertTrue(label.isPreferredSizeSet, "the overload should set the preferred-size-set flag")
    }
}
