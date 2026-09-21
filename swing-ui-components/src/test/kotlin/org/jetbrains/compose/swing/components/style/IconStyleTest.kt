// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Font
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * An icon is a property of the component, not of one wrapper, so it is carried by a style property that
 * reaches every component able to show one. A label and a button name the property separately, with
 * nothing between them declaring it, so these also pin that both are served. A menu item is built on the
 * same class as a button and reached through a menu tree rather than a component tree, so it is asserted
 * for itself.
 */
class IconStyleTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun removalKeepsTheFontDerivedFromTheIcon(removeIcon: Boolean) = runComposeSwingTest {
        val icon = squareIcon()
        val declaredFont = Font("Monospaced", Font.BOLD, 22)
        var decorated by mutableStateOf(false)
        var fontDeclared by mutableStateOf(false)
        setContent {
            Button("Save", onClick = {}, style = {
                if (decorated) icon(icon)
                if (fontDeclared) font(declaredFont)
            })
        }
        val button = onNodeOfType<JButton>().fetch()
        val original = button.font
        val derived = original.deriveFont(original.size2D - 2f)
        button.addPropertyChangeListener("icon") { event ->
            if (!removeIcon || event.newValue != null) button.font = derived
        }
        decorated = true
        awaitIdle()
        assertEquals(derived, button.font)

        if (removeIcon) {
            decorated = false
            awaitIdle()
            assertNull(button.icon)
        } else {
            fontDeclared = true
            awaitIdle()
            assertEquals(declaredFont, button.font)
            fontDeclared = false
            awaitIdle()
        }
        assertEquals(derived, button.font)
    }

    enum class ButtonKind { CheckBox, MenuItem }

    @ParameterizedTest
    @EnumSource(ButtonKind::class)
    fun iconReachesButtonKinds(kind: ButtonKind) = runComposeSwingTest {
        val glyph = squareIcon()
        when (kind) {
            ButtonKind.CheckBox -> {
                setContent { CheckBox("Enabled", checked = false, onCheckedChange = {}, style = { icon(glyph) }) }
                onNodeOfType<JCheckBox>().assertProperty(glyph) { icon }
            }

            ButtonKind.MenuItem -> {
                val popup = composeMenu { MenuItem("Open", onClick = {}, style = { icon(glyph) }) }
                assertSame(glyph, (popup.getComponent(0) as JMenuItem).icon)
            }
        }
    }

    @Test
    fun aChangedIconReplacesTheOne() = runComposeSwingTest {
        val first = squareIcon()
        val second = squareIcon()
        var current by mutableStateOf(first)
        setContent { Label("Legend", style = { icon(current) }) }

        onNodeOfType<JLabel>().assertProperty(first) { icon }

        current = second
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty(second) { icon }
    }
}
