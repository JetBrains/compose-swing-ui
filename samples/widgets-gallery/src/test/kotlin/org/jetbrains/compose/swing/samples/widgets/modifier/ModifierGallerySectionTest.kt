package org.jetbrains.compose.swing.samples.widgets.modifier

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Cursor
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifierGallerySectionTest {
    @Test
    fun theEnabledModifierGatesTheField() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            onNodeWithText("Editable when enabled", substring = true).assertIsEnabled()
            onNodeWithText("Field enabled").performClick()
            onNodeWithText("Editable when enabled", substring = true).assertIsNotEnabled()
        }

    @Test
    fun theSizeAndVisibilityCheckBoxStaysLeftAlignedWithItsColumn() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val checkBox = onNodeWithText("Show the second button").fetch<JCheckBox>()
            val before = checkBox.bounds
            assertTrue(before != Rectangle(), "the checkbox must have a real, laid-out bounds")
            assertEquals(
                checkBox.parent.insets.left,
                before.x,
                "the checkbox must sit flush with the card column's leading edge",
            )

            onNodeWithText("Show the second button").performClick()
            awaitIdle()
            assertEquals(before, onNodeWithText("Show the second button").fetch<JCheckBox>().bounds)
        }

    @Test
    fun theRawActionListenerFiresAlongsideTheWrapper() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            onNodeWithText("Raw listener fired 0 time(s)", substring = true).assertExists()
            onNodeWithText("Click (raw listener)").performClick()
            onNodeWithText("Raw listener fired 1 time(s)", substring = true).assertExists()
        }

    @Test
    fun theCursorModifierSwapsThePointerOverTheTarget() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val target = onNodeWithText("Hover me", substring = true)
            assertEquals(Cursor.HAND_CURSOR, target.fetch<JLabel>().cursor.type)
            onNodeWithText("Hand cursor").performClick()
            assertEquals(Cursor.DEFAULT_CURSOR, target.fetch<JLabel>().cursor.type)
        }

    @Test
    fun theToolTipModifierAttachesHoverHelpText() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val target = onNodeWithText("Hover me", substring = true)
            assertEquals("Click affordance: the hand cursor", target.fetch<JLabel>().toolTipText)
            onNodeWithText("Hand cursor").performClick()
            assertEquals("Plain pointer", target.fetch<JLabel>().toolTipText)
        }

    @Test
    fun theClientPropertyModifierStoresAReadableValue() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val carrier = onNodeWithText("Carries clientProperty", substring = true)
            assertEquals("alpha", carrier.fetch<JLabel>().getClientProperty("role"))
            onNodeWithText("role = beta").performClick()
            assertEquals("beta", carrier.fetch<JLabel>().getClientProperty("role"))
        }

    @Test
    fun theFocusableModifierGatesKeyboardFocus() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val button = onNodeWithText("Tab reaches me only when focusable")
            assertTrue(button.fetch<JButton>().isFocusable)
            onNodeWithText("Button is focusable").performClick()
            assertFalse(button.fetch<JButton>().isFocusable)
        }

    @Test
    fun theMaximumSizeModifierCapsTheButtonWidth() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val button = onNodeWithText("Clamped button").fetch<JButton>()
            assertEquals(240, button.maximumSize.width)
            assertEquals(120, button.minimumSize.width)
        }

    @Test
    fun theRawChangeListenerFiresAlongsideTheWrapper() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            onNodeWithText("Raw change listener fired 0 time(s)", substring = true).assertExists()
            val slider = onNodeOfType<JSlider>().fetch<JSlider>()
            slider.value = 60
            awaitIdle()
            onNodeWithText("Value: 60", substring = true).assertExists()
            val echo = onNodeWithText("Raw change listener fired", substring = true).fetch<JLabel>().text
            assertTrue(echo.first { it.isDigit() } != '0', "the raw change listener fired at least once")
        }

    @Test
    fun theAppearanceLabelRepaintsAsAStableBitmapScreenshotTest() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val styledLabel = onNodeWithText("Styled when the box is checked", substring = true)
            val styled = styledLabel.captureToImage()
            assertTrue(styled.width > 0 && styled.height > 0, "the styled label has real size")
            styledLabel.assertImageMatches(expected = styled)

            assertTrue(
                onNodeWithText("Styled when the box is checked", substring = true).fetch<JLabel>().isOpaque,
                "the styled label paints its background fill (opaque)",
            )
            onNodeWithText("Fancy styling").performClick()
            assertFalse(
                onNodeWithText("Styled when the box is checked", substring = true).fetch<JLabel>().isOpaque,
                "removing the styling clears the opaque background fill",
            )
        }

    @Test
    fun theCursorTargetRendersToAStableBitmapScreenshotTest() =
        runComposeSwingTest {
            openSection("Modifier gallery")

            val target = onNodeWithText("Hover me", substring = true)
            val image = target.captureToImage()
            assertTrue(image.width > 0 && image.height > 0, "the cursor target has real size")
            target.assertImageMatches(expected = image)
        }
}
