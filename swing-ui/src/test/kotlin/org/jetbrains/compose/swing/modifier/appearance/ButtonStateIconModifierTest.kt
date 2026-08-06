package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.compose.swing.withoutLookAndFeelDefault
import java.awt.image.BufferedImage
import javax.swing.AbstractButton
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A button paints a different icon for each state it can be in, so each state is its own modifier.
 * These pin that every one of them reaches a button, that the value follows the state driving it, and
 * that removing it restores what the button carried before.
 *
 * Two of them are not plain values. The disabled icons are derived from the base icon by the look and
 * feel when nothing sets them, so what a removal restores is a derived icon rather than nothing. The
 * rollover icons switch rollover painting on by themselves, which is what makes the state reachable at
 * all; the switch is also declarable on its own, and is then latched the way a button's other painting
 * flags are.
 */
class ButtonStateIconModifierTest {
    private fun icon() = ImageIcon(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB))

    @Test
    fun everyStateIconReachesAButton() = runComposeSwingTest {
        val pressed = icon()
        val selected = icon()
        val disabled = icon()
        val disabledSelected = icon()
        val rollover = icon()
        val rolloverSelected = icon()
        val base = icon()
        setContent {
            Button(
                "Save",
                modifier =
                    SwingModifier
                        .icon(base)
                        .pressedIcon(pressed)
                        .selectedIcon(selected)
                        .disabledIcon(disabled)
                        .disabledSelectedIcon(disabledSelected)
                        .rolloverIcon(rollover)
                        .rolloverSelectedIcon(rolloverSelected),
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(pressed, button.pressedIcon, "pressedIcon")
        assertSame(selected, button.selectedIcon, "selectedIcon")
        assertSame(disabled, button.disabledIcon, "disabledIcon")
        assertSame(disabledSelected, button.disabledSelectedIcon, "disabledSelectedIcon")
        assertSame(rollover, button.rolloverIcon, "rolloverIcon")
        assertSame(rolloverSelected, button.rolloverSelectedIcon, "rolloverSelectedIcon")
    }

    @Test
    fun aStateIconReachesACheckBox() = runComposeSwingTest {
        val checkBoxIcon = icon()
        setContent { CheckBox("Wrap", checked = false, modifier = SwingModifier.selectedIcon(checkBoxIcon)) }

        assertSame(checkBoxIcon, onNodeOfType<JCheckBox>().fetch().selectedIcon, "check box")
    }

    @Test
    fun aStateIconReachesAMenuItem() = runComposeSwingTest {
        val menuItemIcon = icon()
        val popup = composeMenu { MenuItem("Open", modifier = SwingModifier.pressedIcon(menuItemIcon)) }

        assertSame(menuItemIcon, (popup.getComponent(0) as JMenuItem).pressedIcon, "menu item")
    }

    @Test
    fun aChangedStateIconReplacesTheOne() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var current by mutableStateOf(first)
        setContent { Button("Save", modifier = SwingModifier.pressedIcon(current)) }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(first, button.pressedIcon, "the declared icon")

        current = second
        awaitIdle()

        assertSame(second, button.pressedIcon, "the icon follows the state driving it")
    }

    @Test
    fun droppingAStateIconRestoresTheOneTheButtonHad() = runComposeSwingTest {
        var decorated by mutableStateOf(true)
        setContent {
            Button("Save", modifier = if (decorated) SwingModifier.selectedIcon(icon()) else SwingModifier)
        }

        val button = onNodeOfType<AbstractButton>().fetch()
        assertTrue(button.selectedIcon != null, "the icon is installed while declared")

        decorated = false
        awaitIdle()

        assertNull(button.selectedIcon, "dropping the modifier restores the button's own state icon")
    }

    @Test
    fun droppingTheDisabledIconHandsTheStateBackToTheLookAndFeel() = runComposeSwingTest {
        var decorated by mutableStateOf(true)
        val base = icon()
        val declared = icon()
        setContent {
            Button(
                "Save",
                modifier = SwingModifier.icon(base).let { if (decorated) it.disabledIcon(declared) else it },
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(declared, button.disabledIcon, "the declared icon is what the button shows")

        decorated = false
        awaitIdle()

        // Nothing sets the property now, so the button answers with the greyed icon the look and feel
        // derives from the base one rather than with the icon that was declared.
        assertNotSame(declared, button.disabledIcon, "dropping the modifier hands the state back")
    }

    @Test
    fun declaringARolloverIconSwitchesRolloverPaintingOn() = runComposeSwingTest {
        // A look and feel installs rollover painting only where it names a default for it; naming none
        // for either component isolates what the modifier itself switches on, from a state both start
        // with off.
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                withoutLookAndFeelDefault("CheckBox.rollover") {
                    setContent {
                        Button("Save", modifier = SwingModifier.rolloverIcon(icon()))
                        CheckBox("Wrap", checked = false)
                    }

                    assertTrue(
                        onNodeOfType<JButton>().fetch().isRolloverEnabled,
                        "a rollover icon switches the state on",
                    )
                    assertFalse(
                        onNodeOfType<JCheckBox>().fetch().isRolloverEnabled,
                        "a button with no rollover icon",
                    )
                }
            }
        }
    }

    @Test
    fun rolloverPaintingIsDeclarableOnItsOwnAndRestoredOnRemoval() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                var rollover by mutableStateOf(true)
                setContent {
                    Button("Save", modifier = if (rollover) SwingModifier.rolloverEnabled(true) else SwingModifier)
                }

                val button = onNodeOfType<JButton>().fetch()
                assertTrue(button.isRolloverEnabled, "the declared value is what the button carries")

                rollover = false
                awaitIdle()

                assertFalse(button.isRolloverEnabled, "dropping it restores the value the button had")
            }
        }
    }

    @Test
    fun aComponentThatIsNotAButtonIsRejected() {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent { Label("Legend", modifier = SwingModifier.pressedIcon(icon())) }
                }
            }

        assertTrue(
            AbstractButton::class.java.name in failure.message.orEmpty(),
            "the message should name the required type, but was: ${failure.message}",
        )
    }
}
