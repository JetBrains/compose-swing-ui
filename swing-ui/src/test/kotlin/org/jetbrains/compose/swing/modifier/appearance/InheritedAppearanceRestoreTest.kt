package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import javax.swing.JComponent
import kotlin.test.Test

/**
 * Cursor, font, background and foreground are inherited: a component that declares none of its own takes
 * its parent's. Each test declares the property on a mounted surface that has none, drops the modifier
 * again, and asserts the surface holds none of its own again rather than the value it had resolved to.
 * What it resolves to follows from that.
 */
class InheritedAppearanceRestoreTest {
    @Test
    fun droppingTheCursorModifierLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val inherited = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
        var styled by mutableStateOf(false)
        setContent {
            Panel(modifier = SwingModifier.cursor(inherited)) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").cursor(declared)
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
            "dropping the modifier should leave no cursor of its own",
        ) { isCursorSet }
    }

    @Test
    fun droppingTheFontModifierLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Font("Monospaced", Font.BOLD, 22)
        val inherited = Font("Serif", Font.PLAIN, 15)
        var styled by mutableStateOf(false)
        setContent {
            Panel(modifier = SwingModifier.font(inherited)) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").font(declared)
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
        surface.assertProperty(false, "dropping the modifier should leave no font of its own") { isFontSet }
    }

    @Test
    fun droppingTheBackgroundModifierLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Color(12, 34, 56)
        val inherited = Color(200, 180, 160)
        var styled by mutableStateOf(false)
        setContent {
            Panel(modifier = SwingModifier.background(inherited)) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").background(declared)
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
            "dropping the modifier should leave no background of its own",
        ) { isBackgroundSet }
    }

    @Test
    fun droppingTheForegroundModifierLetsTheSurfaceInheritAgain() = runComposeSwingTest {
        val declared = Color(12, 34, 56)
        val inherited = Color(200, 180, 160)
        var styled by mutableStateOf(false)
        setContent {
            Panel(modifier = SwingModifier.foreground(inherited)) {
                SwingNode(
                    factory = { object : JComponent() {} },
                    modifier =
                        if (styled) {
                            SwingModifier.testTag("surface").foreground(declared)
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
            "dropping the modifier should leave no foreground of its own",
        ) { isForegroundSet }
    }
}
