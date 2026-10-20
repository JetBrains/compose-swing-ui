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
import org.junit.jupiter.params.provider.EnumSource
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
    enum class ButtonKind { Button, CheckBox, MenuItem }

    @ParameterizedTest
    @EnumSource(ButtonKind::class)
    fun stateIconReachesButtonKinds(kind: ButtonKind) = runComposeSwingTest {
        val glyph = squareIcon()
        when (kind) {
            ButtonKind.Button -> {
                val pressed = squareIcon()
                val selected = squareIcon()
                val disabled = squareIcon()
                val disabledSelected = squareIcon()
                val rollover = squareIcon()
                val rolloverSelected = squareIcon()
                val base = squareIcon()
                setContent {
                    Button("Save", onClick = {}, style = {
                        icon(base)
                        pressedIcon(pressed)
                        selectedIcon(selected)
                        disabledIcon(disabled)
                        disabledSelectedIcon(disabledSelected)
                        rolloverIcon(rollover)
                        rolloverSelectedIcon(rolloverSelected)
                    })
                }
                onNodeOfType<JButton>().apply {
                    assertProperty(base) { icon }
                    assertProperty(pressed) { pressedIcon }
                    assertProperty(selected) { selectedIcon }
                    assertProperty(disabled) { disabledIcon }
                    assertProperty(disabledSelected) { disabledSelectedIcon }
                    assertProperty(rollover) { rolloverIcon }
                    assertProperty(rolloverSelected) { rolloverSelectedIcon }
                }
            }

            ButtonKind.CheckBox -> {
                setContent {
                    CheckBox("Enabled", checked = false, onCheckedChange = {}, style = { selectedIcon(glyph) })
                }
                onNodeOfType<JCheckBox>().assertProperty(glyph) { selectedIcon }
            }

            ButtonKind.MenuItem -> {
                val popup = composeMenu { MenuItem("Open", onClick = {}, style = { pressedIcon(glyph) }) }
                assertSame(glyph, (popup.getComponent(0) as JMenuItem).pressedIcon)
            }
        }
    }

    @Test
    fun aChangedStateIconReplacesTheOne() = runComposeSwingTest {
        val first = squareIcon()
        val second = squareIcon()
        var current by mutableStateOf(first)
        setContent { Button("Save", onClick = {}, style = { pressedIcon(current) }) }
        onNodeOfType<JButton>().assertProperty(true, "the declared icon") { pressedIcon === first }
        current = second
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(true, "the icon follows the state driving it") { pressedIcon === second }
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, true")
    fun removedDisabledIconsUseCurrentBaseIcons(
        hasSelected: Boolean,
        hasDisabledSelected: Boolean,
    ) = runComposeSwingTest {
        underMetal {
            val base = squareIcon()
            val selected = squareIcon()
            val wideBase = squareIcon(12)
            val wideSelected = squareIcon(16)
            val disabled = squareIcon()
            val disabledSelected = squareIcon()
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
            onNodeOfType<JButton>().fetch().let { button ->
                assertSame(disabled, button.disabledIcon)
                if (hasDisabledSelected) assertSame(disabledSelected, button.disabledSelectedIcon)
            }
            if (hasSelected) {
                // Separate passes expose a stale derived icon captured before the base icons changed.
                wide = true
                awaitIdle()
            }
            decorated = false
            awaitIdle()
            onNodeOfType<JButton>().fetch().let { button ->
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
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false")
    fun declaredDisabledIconSurvivesBaseChange(
        selected: Boolean,
        remove: Boolean,
    ) = runComposeSwingTest {
        val base = squareIcon()
        val replacement = squareIcon(if (selected) 16 else 12)
        val declared = IconUIResource(squareIcon())
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
        onNodeOfType<JButton>().assertProperty(true) {
            (if (selected) disabledSelectedIcon else disabledIcon) === declared
        }
        changed = true
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(true) {
            (if (selected) disabledSelectedIcon else disabledIcon) === declared
        }
    }

    @Test
    fun rolloverSwitchRestores() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault("Button.rollover") {
                var declared by mutableStateOf(true)
                setContent { Button("Save", onClick = {}, style = { if (declared) rolloverEnabled(true) }) }
                onNodeOfType<JButton>().assertProperty(true) { isRolloverEnabled }
                declared = false
                awaitIdle()
                onNodeOfType<JButton>().assertProperty(false) { isRolloverEnabled }
            }
        }
    }

    @Test
    fun aDeclaredRolloverSwitchStandsWhenTheRolloverIconIsWrittenAgain() = runComposeSwingTest {
        val first = squareIcon()
        val second = squareIcon()
        var current by mutableStateOf(first)
        setContent {
            Button("Save", onClick = {}, style = {
                rolloverEnabled(false)
                rolloverIcon(current)
            })
        }
        onNodeOfType<JButton>().assertProperty(
            false,
            "the declared switch outranks the one the rollover icon switched on",
        ) { isRolloverEnabled }
        current = second
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(
            false,
            "the declared switch stands after the icon is written again",
        ) { isRolloverEnabled }
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
                    val glyph = squareIcon()
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
                    (if (selected) onNodeOfType<JCheckBox>() else onNodeOfType<JButton>())
                        .assertProperty(!explicitSwitch) { isRolloverEnabled }
                    if (!selected && !explicitSwitch) {
                        onNodeOfType<JCheckBox>().assertProperty(false) { isRolloverEnabled }
                    }
                    declared = false
                    awaitIdle()
                    (if (selected) onNodeOfType<JCheckBox>() else onNodeOfType<JButton>()).apply {
                        assertProperty(false) { isRolloverEnabled }
                        assertProperty(null) { if (selected) rolloverSelectedIcon else rolloverIcon }
                    }
                }
            }
        }
    }
}

internal fun squareIcon(size: Int = 4): ImageIcon = ImageIcon(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB))
