package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.composeInNestedComposition
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.Panel
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the appearance and metadata [SwingModifier]s that lack a dedicated suite:
 * font, border, cursor, clientProperty, testTag, focusable and preferredSize. Each test asserts the applied
 * Swing property AND its restoration to the pre-modifier default once the element leaves the modifier -
 * the round-trip contract every property element promises.
 */
class AppearanceMetadataModifierTest {
    @Test
    fun aBorderMovedIntoAnotherCompositionStaysDeclaredAndIsPutBackOnRemoval() = runComposeSwingTest {
        val panel = JPanel()
        val original = panel.border
        val declaredBorder = BorderFactory.createEmptyBorder(1, 2, 3, 4)
        var declared by mutableStateOf(true)
        val nested =
            composeInNestedComposition {
                SwingNode(
                    factory = { panel },
                    modifier = if (declared) SwingModifier.border(declaredBorder) else SwingModifier,
                )
            }

        nested.moveContentOut()
        awaitIdle()

        assertSame(declaredBorder, panel.border, "a moved component keeps the border its modifier declares")
        assertEquals(Insets(1, 2, 3, 4), panel.insets, "a moved component keeps the insets of the declared border")

        declared = false
        awaitIdle()

        assertSame(original, panel.border, "removing the border after the move puts back the border the component had")
        nested.dispose()
    }

    @Test
    fun aBorderRestoreFailureDuringMoveStillAppliesTheDeclarationInTheDestination() = runComposeSwingTest {
        val original = BorderFactory.createLineBorder(Color.BLUE, 7)
        val panel = JPanel().apply { border = original }
        val declaredBorder = BorderFactory.createEmptyBorder(1, 2, 3, 4)
        val failure = IllegalStateException("border restore listener failed")
        var declared by mutableStateOf(true)
        val nested =
            composeInNestedComposition {
                SwingNode(
                    factory = { panel },
                    modifier = if (declared) SwingModifier.border(declaredBorder) else SwingModifier,
                )
            }
        var throwOnRestore = true
        panel.addPropertyChangeListener(
            "border",
            PropertyChangeListener { event ->
                if (throwOnRestore && event.newValue === original) {
                    throwOnRestore = false
                    throw failure
                }
            },
        )

        nested.moveContentOut()
        val reported = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertSame(failure, reported, "the restoration failure is reported unchanged")
        assertSame(declaredBorder, panel.border, "the moved declaration must still be applied in its new composition")

        declared = false
        awaitIdle()
        assertSame(original, panel.border, "removing the declaration restores the border the component had")
        nested.dispose()
    }

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
        assertSame(
            default,
            styledLabel.fetch<JLabel>().border,
            "removing the modifier should restore the default border",
        )
    }

    @Test
    fun borderTakesADeclaredBorderThatComparesEqualToTheOneInPlace() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent { Label(caption, modifier = SwingModifier.border(ValueBorder(2))) }
        val label = onNodeOfType<JLabel>()
        val first = label.fetch<JLabel>().border

        caption = "second"
        awaitIdle()
        val second = label.fetch<JLabel>().border
        // The declaration carries the caller's own object, so what the caller's equals says about the
        // two borders decides nothing: the border last declared is the one the component carries.
        assertEquals(first, second, "the two borders have to compare equal for the case to say anything")
        assertNotSame(first, second, "the border the latest declaration carries should reach the component")
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
        assertNull(
            label.fetch<JLabel>().getClientProperty(key),
            "removing the modifier should clear the client property",
        )
    }

    @Test
    fun removingAClientPropertyLeavesTheFontALookAndFeelWorkedOutFromIt() = runComposeSwingTest {
        val key = "JComponent.sizeVariant"
        var styled by mutableStateOf(false)
        setContent { Label("X", modifier = if (styled) SwingModifier.clientProperty(key, "small") else SwingModifier) }
        val label = onNodeOfType<JLabel>().fetch()
        val original = label.font
        val derived = original.deriveFont(original.size2D - 2f)
        // A look and feel that works the font out again from a styling key and leaves it worked out when
        // the key goes. Standing in for one here, the case says the same thing on every platform.
        label.addPropertyChangeListener(key) { event -> if (event.newValue != null) label.font = derived }

        styled = true
        awaitIdle()
        assertEquals(derived, label.font, "the deriving stand-in should have restyled the label")

        styled = false
        awaitIdle()
        assertNull(label.getClientProperty(key), "removing the declaration should put the key back")
        assertEquals(derived, label.font, "the font worked out from the key is not the declaration's to put back")
    }

    @Test
    fun droppingAFontLeavesTheFontTheStandingClientPropertyDerives() = runComposeSwingTest {
        val key = "JComponent.sizeVariant"
        val declaredFont = Font("Monospaced", Font.BOLD, 22)
        var styled by mutableStateOf(false)
        var fontDeclared by mutableStateOf(false)
        setContent {
            val chain = if (styled) SwingModifier.clientProperty(key, "small") else SwingModifier
            Label("X", modifier = if (fontDeclared) chain.font(declaredFont) else chain)
        }
        val label = onNodeOfType<JLabel>().fetch()
        val original = label.font
        val derived = original.deriveFont(original.size2D - 2f)
        // A look and feel that works the font out again from a styling key, which is what the font the
        // modifier leaves standing has to answer to.
        label.addPropertyChangeListener(key) { label.font = derived }

        styled = true
        awaitIdle()
        assertEquals(derived, label.font, "the deriving stand-in should have worked the font out from the key")

        fontDeclared = true
        awaitIdle()
        assertEquals(declaredFont, label.font, "the declared font should stand over the derived one")

        fontDeclared = false
        awaitIdle()
        assertEquals(
            derived,
            label.font,
            "dropping the font should leave the font the standing key derives, not the one the modifier found",
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
        val styledLabel = onNodeWithText("styled")
        // Every AWT component reports itself focusable until something says otherwise; the modifier is
        // what says otherwise. Were the default false, both assertions below would pass with no modifier
        // applied at all.
        val default = onNodeWithText("untouched").fetch<JLabel>().isFocusable
        assertFalse(styledLabel.fetch<JLabel>().isFocusable, "the modifier should make the label unfocusable")

        styled = false
        awaitIdle()
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

    @Test
    fun testTagOrNullReadsBackWhatTheTestTagModifierApplied() = runComposeSwingTest {
        var tagged by mutableStateOf(true)
        setContent {
            Label("untagged")
            Label("tagged", modifier = if (tagged) SwingModifier.testTag("the-tag") else SwingModifier)
        }
        val label = onNodeWithText("tagged")
        assertEquals("the-tag", label.fetch<JLabel>().testTagOrNull(), "the applied tag should read back")
        assertNull(
            onNodeWithText("untagged").fetch<JLabel>().testTagOrNull(),
            "a component the modifier was never applied to should carry no tag",
        )

        tagged = false
        awaitIdle()
        assertNull(label.fetch<JLabel>().testTagOrNull(), "removing the modifier should clear the tag")
    }

    @Test
    fun testTagOrNullAnswersNullForAComponentThatHoldsNoClientProperties() {
        assertNull(Panel().testTagOrNull(), "a component that is no JComponent carries no tag")
    }

    @Test
    fun applyingATestTagFiresAPropertyChangeUnderThePlainPropertyName() = runComposeSwingTest {
        var tagged by mutableStateOf(false)
        val seen = mutableListOf<Any?>()
        // The name a client-property write fires under is what a bound listener matches on, so it is
        // contract towards callers and the test states it rather than reading it back off the key.
        val watcher =
            SwingModifier.propertyChangeListener("org.jetbrains.compose.swing.testTag") {
                seen += it.newValue
            }
        setContent {
            val tag = if (tagged) SwingModifier.testTag("the-tag") else SwingModifier
            Label("X", modifier = watcher then tag)
        }

        tagged = true
        awaitIdle()
        assertEquals(listOf<Any?>("the-tag"), seen, "applying a tag should notify a listener bound to that name")
    }
}

/**
 * A border of the shape a caller writes: a value, so two built from the same [width] compare equal while
 * being distinct objects, which is what a declaration rebuilt on every recomposition produces.
 */
private data class ValueBorder(
    val width: Int,
) : Border by EmptyBorder(width, width, width, width)
