// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.click
import org.jetbrains.compose.swing.test.interaction.performMouseInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacementTextFieldHitTestTest {
    @Test
    fun thePointerOverATurnedTextFieldShowsItsTextCursor() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            setWindowContent { Box { TurnedTextField(rotation = 90f) } }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val field = window.onNodeWithTag(INNER_TAG).fetch<JComponent>()
            val over = layered.paintedAt(stageInput.fetch(), field, 10.0, 15.0, turn(200, 30, rotation = 90f))

            stageInput.performMouseInput {
                moveTo(over)
            }
            stageInput.assertProperty(true) {
                val stage = this
                window.assertIsOfType<JFrame>().assertProperty(Cursor.TEXT_CURSOR, "over the turned field") {
                    findComponentAt(SwingUtilities.convertPoint(stage, over, this)).cursor.type
                }
                true
            }

            val unturned = layered.unturnedAt(stageInput.fetch(), 10, 15)
            stageInput.performMouseInput {
                moveTo(unturned)
            }
            stageInput.assertProperty(true) {
                val stage = this
                window.assertIsOfType<JFrame>().assertProperty(Cursor.DEFAULT_CURSOR, "where it would stand") {
                    findComponentAt(SwingUtilities.convertPoint(stage, unturned, this)).cursor.type
                }
                true
            }
        }

    @Test
    fun aDragAcrossATurnedTextFieldSelectsTheCharactersItCrosses() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            setWindowContent { Box { TurnedTextField(rotation = 90f) } }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val field = window.onNodeWithTag(INNER_TAG).fetch<JTextField>()
            val turn = turn(200, 30, rotation = 90f)
            // The Aqua look and feel selects the whole text when the field gains focus at either end of it, and a press
            // inside a selection starts dragging the selection instead of selecting anew.
            field.caretPosition = 8

            stageInput.performMouseInput {
                moveTo(layered.charAt(stageInput.fetch(), field, 1, turn))
                press(MouseEvent.BUTTON1)
            }
            stageInput.performMouseInput {
                moveTo(layered.charAt(stageInput.fetch(), field, 6, turn))
            }
            stageInput.performMouseInput {
                release(MouseEvent.BUTTON1)
            }

            window.onNodeWithTag(INNER_TAG).assertIsOfType<JTextField>().assertProperty(
                1 to 6,
            ) {
                selectionStart to selectionEnd
            }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caretTransforms")
    fun aClickPlacesTheCaretAtTheClickedCharacterUnderATransform(
        rotation: Float,
        scale: Float,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val window = onWindowWithTitle(WINDOW_TITLE)
        val stageInput = window.onNodeWithTag(STAGE_TAG)
        setWindowContent { Box { TurnedTextField(rotation = rotation, scale = scale) } }
        val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
        val field = window.onNodeWithTag(INNER_TAG).fetch<JTextField>()

        stageInput.performMouseInput {
            click(layered.charAt(stageInput.fetch(), field, 4, turn(200, 30, rotation, scale)))
        }

        window.onNodeWithTag(INNER_TAG).assertIsOfType<JTextField>().assertProperty(4) {
            caretPosition
        }
    }

    /**
     * A repaint the caret asks for paints where the layer puts the caret, which a repaint of the area the caret
     * takes unturned never reaches.
     */
    @Test
    fun aCaretMovingUnderARotationRepaintsWhereTheLayerPaintsIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box {
                    TurnedTextField(
                        rotation = 90f,
                        modifier = SwingModifier.drawBehind { graphics.clipBounds?.let { repainted += it } },
                    )
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val field = window.onNodeWithTag(INNER_TAG).fetch<JTextField>()
            val stage = stageInput.fetch<JComponent>()
            awaitIdle()
            repainted.clear()

            field.caretPosition = 8
            awaitIdle()

            val caret = layered.charAt(stageInput.fetch(), field, 8, turn(200, 30, rotation = 90f))
            caret.translate(stage.paintBounds.x, stage.paintBounds.y)
            assertTrue(repainted.any { it.contains(caret) }, "the turned caret at $caret must repaint: $repainted")
            assertFalse(
                repainted.any {
                    it.contains(Point(1, 1))
                },
                "the caret alone repaints: $repainted",
            )
        }

    companion object {
        @JvmStatic
        fun caretTransforms(): List<Arguments> =
            listOf(
                Arguments.of(Named.of("a rotation", 30f), 1f),
                Arguments.of(Named.of("a scale", 0f), 1.5f),
            )
    }
}
