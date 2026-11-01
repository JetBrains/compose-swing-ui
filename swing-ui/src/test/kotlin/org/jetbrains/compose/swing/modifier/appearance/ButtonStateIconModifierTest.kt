package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
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
import javax.swing.plaf.IconUIResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A button paints a different icon for each state it can be in, so each state is its own modifier.
 * These pin that every one of them reaches a button, that the value follows the state driving it, and
 * that removing it restores what the button carried before.
 *
 * Two of them are not plain values. The disabled icons are derived from the base icon by the look and
 * feel when nothing sets them, so a removal leaves the button holding none of its own and the look and
 * feel derives one again from the icons the button carries then. The rollover icons switch rollover
 * painting on by themselves, which is what makes the state reachable at all; the switch is also
 * declarable on its own, and is then latched the way a button's other painting flags are.
 */
class ButtonStateIconModifierTest {
    private fun icon(size: Int = SMALL) = ImageIcon(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB))

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
                onClick = { },
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
        setContent {
            CheckBox("Wrap", checked = false, onCheckedChange = {}, modifier = SwingModifier.selectedIcon(checkBoxIcon))
        }

        assertSame(checkBoxIcon, onNodeOfType<JCheckBox>().fetch().selectedIcon, "check box")
    }

    @Test
    fun aStateIconReachesAMenuItem() = runComposeSwingTest {
        val menuItemIcon = icon()
        val popup = composeMenu { MenuItem("Open", onClick = { }, modifier = SwingModifier.pressedIcon(menuItemIcon)) }

        assertSame(menuItemIcon, (popup.getComponent(0) as JMenuItem).pressedIcon, "menu item")
    }

    @Test
    fun aChangedStateIconReplacesTheOne() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var current by mutableStateOf(first)
        setContent { Button("Save", onClick = { }, modifier = SwingModifier.pressedIcon(current)) }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(first, button.pressedIcon, "the declared icon")

        current = second
        awaitIdle()

        assertSame(second, button.pressedIcon, "the icon follows the state driving it")
    }

    @Test
    fun droppingAStateIconRestoresTheOneTheButtonHad() = runComposeSwingTest {
        val declared = icon()
        var decorated by mutableStateOf(true)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier = if (decorated) SwingModifier.selectedIcon(declared) else SwingModifier,
            )
        }

        val button = onNodeOfType<AbstractButton>()
        button.assertProperty(declared, "the icon is installed while declared") { selectedIcon }

        decorated = false
        awaitIdle()

        button.assertProperty(null, "dropping the modifier restores the button's own state icon") { selectedIcon }
    }

    @Test
    fun droppingTheDisabledIconHandsTheStateBackToTheLookAndFeel() = runComposeSwingTest {
        var decorated by mutableStateOf(true)
        val base = icon()
        val declared = icon()
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier =
                    SwingModifier.icon(base).let {
                        if (decorated) it.disabledIcon(declared) else it
                    },
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(declared, button.disabledIcon, "the declared icon is what the button shows")

        decorated = false
        awaitIdle()

        // Nothing sets the property now, so the button answers with the grayed icon the look and feel
        // derives from the base one rather than with the icon that was declared.
        assertNotSame(declared, button.disabledIcon, "dropping the modifier hands the state back")
    }

    @Test
    fun droppingTheDisabledIconsLeavesTheButtonDerivingFromTheIconsItCarriesThen() = runComposeSwingTest {
        underMetal {
            val base = icon()
            val selected = icon()
            val wideBase = icon(WIDE_BASE)
            val wideSelected = icon(WIDE_SELECTED)
            val disabled = icon()
            val disabledSelected = icon()
            var wide by mutableStateOf(false)
            var decorated by mutableStateOf(true)
            setContent {
                Button(
                    "Save",
                    onClick = { },
                    modifier =
                        SwingModifier
                            .icon(if (wide) wideBase else base)
                            .selectedIcon(if (wide) wideSelected else selected)
                            .let { if (decorated) it.disabledIcon(disabled) else it }
                            .let { if (decorated) it.disabledSelectedIcon(disabledSelected) else it },
                )
            }

            val button = onNodeOfType<JButton>().fetch()
            assertSame(disabled, button.disabledIcon, "the declared disabled icon")
            assertSame(disabledSelected, button.disabledSelectedIcon, "the declared disabled selected icon")

            // The icons the disabled states are derived from are replaced while the declarations stand,
            // so a derived icon read when they attached is no longer what the button would show. Each
            // change is its own pass: one pass doing both would restore before writing the new icons, and
            // the button discards a restored derived icon as it takes them.
            wide = true
            awaitIdle()
            decorated = false
            awaitIdle()

            assertEquals(
                WIDE_BASE,
                button.disabledIcon.iconWidth,
                "the disabled icon is derived from the icon the button carries now",
            )
            assertEquals(
                WIDE_SELECTED,
                button.disabledSelectedIcon.iconWidth,
                "the disabled selected icon is derived from the selected icon the button carries now",
            )
        }
    }

    @Test
    fun droppingTheDisabledIconsLeavesNeitherOnAButtonCarryingNoSelectedIcon() = runComposeSwingTest {
        underMetal {
            val base = icon()
            val disabled = icon()
            val disabledSelected = icon()
            var decorated by mutableStateOf(true)
            setContent {
                Button(
                    "Save",
                    onClick = { },
                    modifier =
                        SwingModifier
                            .icon(base)
                            .let { if (decorated) it.disabledIcon(disabled) else it }
                            .let { if (decorated) it.disabledSelectedIcon(disabledSelected) else it },
                )
            }

            val button = onNodeOfType<JButton>().fetch()
            assertSame(disabledSelected, button.disabledSelectedIcon, "the declared disabled selected icon")

            decorated = false
            awaitIdle()

            assertNotSame(disabled, button.disabledIcon, "the disabled declaration is gone")
            assertNotSame(disabledSelected, button.disabledSelectedIcon, "the disabled selected declaration is gone")

            // With no selected icon to gray, the button answers a disabled selected icon read with its
            // disabled icon. That is the other declaration's value, not one this element may hand back.
            assertNotSame(disabled, button.disabledSelectedIcon, "the disabled icon is not this one to keep")
        }
    }

    @Test
    fun aDeclaredDisabledIconStandsWhenTheBaseIconIsReplacedUnderIt() = runComposeSwingTest {
        val base = icon()
        val wideBase = icon(WIDE_BASE)
        val declared = IconUIResource(icon())
        var wide by mutableStateOf(false)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier = SwingModifier.disabledIcon(declared).icon(if (wide) wideBase else base),
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(declared, button.disabledIcon, "the declared icon is what the button shows")

        // A button drops a disabled icon that is a UIResource as it takes a new base icon, telling a
        // declared one from a derived one by that class alone.
        wide = true
        awaitIdle()

        assertSame(declared, button.disabledIcon, "the declaration stands after the base icon is replaced")
    }

    @Test
    fun aDeclaredDisabledIconStandsWhenTheBaseIconIsTakenAway() = runComposeSwingTest {
        val base = icon()
        val declared = IconUIResource(icon())
        var shown by mutableStateOf(true)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier = SwingModifier.disabledIcon(declared).icon(if (shown) base else null),
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(declared, button.disabledIcon, "the declared icon is what the button shows")

        // A button announces the icon it is left without the same way it announces a replacement, and
        // drops the declared disabled icon either way.
        shown = false
        awaitIdle()

        assertSame(declared, button.disabledIcon, "the declaration stands after the base icon is taken away")
    }

    @Test
    fun aDeclaredDisabledSelectedIconStandsWhenTheSelectedIconIsReplacedUnderIt() = runComposeSwingTest {
        val selected = icon()
        val wideSelected = icon(WIDE_SELECTED)
        val declared = IconUIResource(icon())
        var wide by mutableStateOf(false)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier =
                    SwingModifier
                        .disabledSelectedIcon(declared)
                        .selectedIcon(if (wide) wideSelected else selected),
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertSame(declared, button.disabledSelectedIcon, "the declared icon is what the button shows")

        wide = true
        awaitIdle()

        assertSame(declared, button.disabledSelectedIcon, "the declaration stands after the selected icon is replaced")
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
                        Button("Save", onClick = { }, modifier = SwingModifier.rolloverIcon(icon()))
                        CheckBox("Wrap", checked = false, onCheckedChange = {})
                    }

                    onNodeOfType<JButton>().assertProperty(
                        true,
                        "a rollover icon switches the state on",
                    ) { isRolloverEnabled }
                    onNodeOfType<JCheckBox>().assertProperty(
                        false,
                        "a button with no rollover icon",
                    ) { isRolloverEnabled }
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
                    Button(
                        "Save",
                        onClick = { },
                        modifier = if (rollover) SwingModifier.rolloverEnabled(true) else SwingModifier,
                    )
                }

                val button = onNodeOfType<JButton>()
                button.assertProperty(true, "the declared value is what the button carries") { isRolloverEnabled }

                rollover = false
                awaitIdle()

                button.assertProperty(false, "dropping it restores the value the button had") { isRolloverEnabled }
            }
        }
    }

    @Test
    fun aDeclaredRolloverSwitchStandsWhenTheRolloverIconIsWrittenAgain() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var current by mutableStateOf(first)
        setContent {
            Button("Save", onClick = { }, modifier = SwingModifier.rolloverEnabled(false).rolloverIcon(current))
        }

        val button = onNodeOfType<JButton>()
        button.assertProperty(
            false,
            "the declared switch outranks the one the rollover icon switched on",
        ) { isRolloverEnabled }

        // Writing a rollover icon switches rollover painting on again. The switch is declared ahead of
        // the icon, so nothing in the pass writes it after; only the declaration answering the button's
        // own change puts it back.
        current = second
        awaitIdle()

        button.assertProperty(false, "the declared switch stands after the icon is written again") { isRolloverEnabled }
    }

    @Test
    fun droppingARolloverIconAndItsSwitchTogetherLeavesTheButtonWhereItStood() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                var declared by mutableStateOf(true)
                setContent {
                    Button(
                        "Save",
                        onClick = { },
                        modifier =
                            if (declared) {
                                SwingModifier.rolloverIcon(icon()).rolloverEnabled(false)
                            } else {
                                SwingModifier
                            },
                    )
                }

                val button = onNodeOfType<JButton>()
                button.assertProperty(false, "the declared switch stands while it is declared") { isRolloverEnabled }

                declared = false
                awaitIdle()

                button.assertProperty(null, "the rollover icon the button never carried is gone") { rolloverIcon }
                button.assertProperty(
                    false,
                    "dropping both leaves rollover painting off, as the button carried it",
                ) { isRolloverEnabled }
            }
        }
    }

    @Test
    fun droppingARolloverIconLeavesRolloverPaintingWhereTheButtonCarriedIt() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                var declared by mutableStateOf(true)
                setContent {
                    Button(
                        "Save",
                        onClick = { },
                        modifier = if (declared) SwingModifier.rolloverIcon(icon()) else SwingModifier,
                    )
                }

                val button = onNodeOfType<JButton>()
                button.assertProperty(true, "declaring the icon switches rollover painting on") { isRolloverEnabled }

                declared = false
                awaitIdle()

                button.assertProperty(
                    false,
                    "dropping the icon takes the switch it turned on back off",
                ) { isRolloverEnabled }
            }
        }
    }

    @Test
    fun droppingARolloverSelectedIconLeavesRolloverPaintingWhereTheButtonCarriedIt() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("CheckBox.rollover") {
                var declared by mutableStateOf(true)
                setContent {
                    CheckBox(
                        "Wrap",
                        checked = true,
                        onCheckedChange = { },
                        modifier = if (declared) SwingModifier.rolloverSelectedIcon(icon()) else SwingModifier,
                    )
                }

                val box = onNodeOfType<JCheckBox>()
                box.assertProperty(true, "declaring the icon switches rollover painting on") { isRolloverEnabled }

                declared = false
                awaitIdle()

                box.assertProperty(
                    false,
                    "dropping the icon takes the switch it turned on back off",
                ) { isRolloverEnabled }
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

    private companion object {
        /** The size every icon a test declares first is built at. */
        const val SMALL = 4

        /**
         * The sizes of the icons that replace them. A derived icon keeps the size of the icon it was
         * derived from, so a size tells which of them a button derived the one it shows from.
         */
        const val WIDE_BASE = 12
        const val WIDE_SELECTED = 16
    }
}
