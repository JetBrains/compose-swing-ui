// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import javax.swing.JComponent
import kotlin.test.Test

/**
 * Cursor, font, background and foreground are inherited: a component that declares none of its own takes
 * its parent's. Each test declares the property in a style on a mounted surface that has none, drops the
 * style again, and asserts the surface holds none of its own again rather than the value it had resolved
 * to. What it resolves to follows from that.
 */
class InheritedAppearanceRestoreTest {
    @Test
    fun droppingTheCursorStyleLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val inherited = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        var styled by mutableStateOf(false)
        setContent {
            Panel(style = { cursor(inherited) }) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").styleable { cursor(declared) }
                        } else {
                            SwingModifier.testTag("surface")
                        },
                )
            }
        }
        val surface = onNodeWithTag("surface")

        styled = true
        awaitIdle()
        surface.assertProperty(declared, "the declared cursor should apply while present") { cursor }

        styled = false
        awaitIdle()
        surface.assertProperty(
            false,
            "dropping the style should leave no cursor of its own",
        ) { isCursorSet }
    }

    @Test
    fun droppingTheFontStyleLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Font("Monospaced", Font.BOLD, 22)
        val inherited = Font("Serif", Font.PLAIN, 15)
        var styled by mutableStateOf(false)
        setContent {
            Panel(style = { font(inherited) }) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").styleable { font(declared) }
                        } else {
                            SwingModifier.testTag("surface")
                        },
                )
            }
        }
        val surface = onNodeWithTag("surface")

        styled = true
        awaitIdle()
        surface.assertProperty(declared, "the declared font should apply while present") { font }

        styled = false
        awaitIdle()
        surface.assertProperty(false, "dropping the style should leave no font of its own") { isFontSet }
    }

    @Test
    fun droppingTheBackgroundStyleLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Color(12, 34, 56)
        val inherited = Color(200, 180, 160)
        var styled by mutableStateOf(false)
        setContent {
            Panel(style = { background(inherited) }) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").styleable { background(declared) }
                        } else {
                            SwingModifier.testTag("surface")
                        },
                )
            }
        }
        val surface = onNodeWithTag("surface")

        styled = true
        awaitIdle()
        surface.assertProperty(
            declared,
            "the declared color should apply while present",
        ) { background }

        styled = false
        awaitIdle()
        surface.assertProperty(
            false,
            "dropping the style should leave no background of its own",
        ) { isBackgroundSet }
    }

    @Test
    fun droppingTheForegroundStyleLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Color(12, 34, 56)
        val inherited = Color(200, 180, 160)
        var styled by mutableStateOf(false)
        setContent {
            Panel(style = { foreground(inherited) }) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").styleable { foreground(declared) }
                        } else {
                            SwingModifier.testTag("surface")
                        },
                )
            }
        }
        val surface = onNodeWithTag("surface")

        styled = true
        awaitIdle()
        surface.assertProperty(
            declared,
            "the declared color should apply while present",
        ) { foreground }

        styled = false
        awaitIdle()
        surface.assertProperty(
            false,
            "dropping the style should leave no foreground of its own",
        ) { isForegroundSet }
    }
}
