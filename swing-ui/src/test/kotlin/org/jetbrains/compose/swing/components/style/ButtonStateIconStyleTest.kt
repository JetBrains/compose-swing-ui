package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.compose.swing.withoutLookAndFeelDefault
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JMenuItem
import javax.swing.plaf.IconUIResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ButtonStateIconStyleTest {
    private fun icon(size: Int = 4) = ImageIcon(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB))

    @Test
    fun aStateIconReachesACheckBox() = runComposeSwingTest {
        val glyph = icon()
        setContent { CheckBox("Wrap", checked = false, onCheckedChange = {}, style = { selectedIcon(glyph) }) }
        onNodeOfType<JCheckBox>().assertProperty(true) { selectedIcon === glyph }
    }

    @Test
    fun aStateIconReachesAMenuItem() = runComposeSwingTest {
        val glyph = icon()
        val popup = composeMenu { MenuItem("Open", onClick = {}, style = { pressedIcon(glyph) }) }
        assertSame(glyph, (popup.getComponent(0) as JMenuItem).pressedIcon)
    }

    @Test
    fun pressedIconUpdates() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var current by mutableStateOf(first)
        setContent { Button("Save", onClick = {}, style = { pressedIcon(current) }) }
        val button = onNodeOfType<JButton>()
        button.assertProperty(true) { pressedIcon === first }
        current = second
        awaitIdle()
        button.assertProperty(true) { pressedIcon === second }
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, true")
    fun removedDisabledIconsUseCurrentBaseIcons(
        hasSelected: Boolean,
        hasDisabledSelected: Boolean,
    ) = runComposeSwingTest {
        underMetal {
            val base = icon()
            val selected = icon()
            val wideBase = icon(12)
            val wideSelected = icon(16)
            val disabled = icon()
            val disabledSelected = icon()
            var wide by mutableStateOf(false)
            var decorated by mutableStateOf(true)
            setContent {
                Button("Save", onClick = {}, style = {
                    icon(if (wide) wideBase else base)
                    if (hasSelected) selectedIcon(if (wide) wideSelected else selected)
                    if (decorated) {
                        disabledIcon(disabled)
                        if (hasDisabledSelected) disabledSelectedIcon(disabledSelected)
                    }
                })
            }
            val button = onNodeOfType<JButton>().fetch()
            assertSame(disabled, button.disabledIcon)
            if (hasDisabledSelected) assertSame(disabledSelected, button.disabledSelectedIcon)
            if (hasSelected) {
                // Separate passes expose a stale derived icon captured before the base icons changed.
                wide = true
                awaitIdle()
            }
            decorated = false
            awaitIdle()
            assertNotSame(disabled, button.disabledIcon)
            assertNotSame(disabledSelected, button.disabledSelectedIcon)
            if (hasSelected) {
                assertEquals(12, button.disabledIcon.iconWidth)
                assertEquals(16, button.disabledSelectedIcon.iconWidth)
            } else {
                assertNotSame(disabled, button.disabledSelectedIcon)
            }
        }
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false")
    fun declaredDisabledIconSurvivesBaseChange(
        selected: Boolean,
        remove: Boolean,
    ) = runComposeSwingTest {
        val base = icon()
        val replacement = icon(if (selected) 16 else 12)
        val declared = IconUIResource(icon())
        var changed by mutableStateOf(false)
        setContent {
            Button("Save", onClick = {}, style = {
                val current = if (changed) (if (remove) null else replacement) else base
                // Swing discards disabled UIResource icons when their base changes.
                if (selected) {
                    disabledSelectedIcon(declared)
                    selectedIcon(current)
                } else {
                    disabledIcon(declared)
                    icon(current)
                }
            })
        }
        val button = onNodeOfType<JButton>()
        button.assertProperty(true) { (if (selected) disabledSelectedIcon else disabledIcon) === declared }
        changed = true
        awaitIdle()
        button.assertProperty(true) { (if (selected) disabledSelectedIcon else disabledIcon) === declared }
    }

    @Test
    fun rolloverSwitchRestores() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                var declared by mutableStateOf(true)
                setContent { Button("Save", onClick = {}, style = { if (declared) rolloverEnabled(true) }) }
                val button = onNodeOfType<JButton>()
                button.assertProperty(true) { isRolloverEnabled }
                declared = false
                awaitIdle()
                button.assertProperty(false) { isRolloverEnabled }
            }
        }
    }

    @Test
    fun declaredRolloverSwitchSurvivesIconChange() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var current by mutableStateOf(first)
        setContent {
            Button("Save", onClick = {}, style = {
                rolloverEnabled(false)
                rolloverIcon(current)
            })
        }
        val button = onNodeOfType<JButton>()
        button.assertProperty(false) { isRolloverEnabled }
        current = second
        awaitIdle()
        button.assertProperty(false) { isRolloverEnabled }
    }

    @ParameterizedTest
    @CsvSource("false, false", "true, false", "false, true")
    fun rolloverIconRemovalRestoresSwitch(
        selected: Boolean,
        explicitSwitch: Boolean,
    ) = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                withoutLookAndFeelDefault("CheckBox.rollover") {
                    var declared by mutableStateOf(true)
                    val glyph = icon()
                    setContent {
                        if (selected) {
                            CheckBox("Wrap", checked = true, onCheckedChange = {}, style = {
                                if (declared) rolloverSelectedIcon(glyph)
                            })
                        } else {
                            Button("Save", onClick = {}, style = {
                                if (declared) {
                                    rolloverIcon(glyph)
                                    if (explicitSwitch) rolloverEnabled(false)
                                }
                            })
                        }
                        if (!selected && !explicitSwitch) CheckBox("Plain", checked = false, onCheckedChange = {})
                    }
                    val button = if (selected) onNodeOfType<JCheckBox>() else onNodeOfType<JButton>()
                    button.assertProperty(!explicitSwitch) { isRolloverEnabled }
                    if (!selected && !explicitSwitch) {
                        onNodeOfType<JCheckBox>().assertProperty(false) { isRolloverEnabled }
                    }
                    declared = false
                    awaitIdle()
                    button.assertProperty(false) { isRolloverEnabled }
                    button.assertProperty(null) { if (selected) rolloverSelectedIcon else rolloverIcon }
                }
            }
        }
    }
}
