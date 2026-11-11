// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
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
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Mouse events reach what a rotating or scaling placement layer paints under the pointer. Pointer tests post events
 * to the window where Swing's own search for the component under the pointer matters.
 */
class PlacementHitTestTest {
    @Test
    fun aPressInsideTheTurnedOutlineHitsAtTheUnturnedPointAndOneOnlyInsideTheUnturnedBoxMisses() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val presses = ArrayList<Point>()
            setWindowContent {
                Box {
                    Stage {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(LAYERED_TAG)
                                    .size(100, 100)
                                    .placementLayer { rotationZ = 45f }
                                    .mouseListener(onMousePressed = { presses += it.point }),
                        ) {}
                    }
                }
            }
            val canvas = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val turn = turn(100, 100, rotation = 45f)

            // Near the unturned top-left corner, which the eighth turn puts above the unturned box. The canvas reads
            // its layout bounds' origin where its paint outsets ends.
            val unturned = Point(6 - canvas.paintBounds.x, 6 - canvas.paintBounds.y)
            stageInput.performMouseInput {
                click(
                    canvas.paintedAt(
                        stageInput.fetch(),
                        canvas,
                        unturned.x.toDouble(),
                        unturned.y.toDouble(),
                        turn,
                    ),
                )
            }
            assertEquals(1, presses.size, "a press inside the turned outline must hit")
            val point = presses.single()
            assertTrue(point.distance(unturned) <= 1.5, "the press lands at the unturned point $unturned: $point")

            // Near the unturned top-left corner, which the eighth turn leaves outside the diamond.
            stageInput.performMouseInput {
                click(canvas.unturnedAt(stageInput.fetch(), 4, 4))
            }
            assertEquals(1, presses.size, "a press inside only the unturned box must miss")
        }

    @Test
    fun aButtonInATurnedLayerFiresOnlyInsideItsTurnedOutline() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var clicks = 0
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag(LAYERED_TAG).size(120, 40).placementLayer { rotationZ = 90f },
                        ) {
                            Button("Go", { clicks++ }, SwingModifier.testTag(INNER_TAG).fillMaxSize())
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val button = window.onNodeWithTag(INNER_TAG).fetch<JComponent>()
            val turn = turn(120, 40, rotation = 90f)

            // Near the unturned left edge, which the quarter turn puts above the unturned box.
            stageInput.performMouseInput {
                click(layered.paintedAt(stageInput.fetch(), button, 10.0, 20.0, turn))
            }
            assertEquals(1, clicks, "a click inside the turned outline must fire")

            // Near the unturned left edge, which the quarter turn leaves outside the button.
            stageInput.performMouseInput {
                click(layered.unturnedAt(stageInput.fetch(), 10, 20))
            }
            assertEquals(1, clicks, "a click inside only the unturned box must not fire")
        }

    /** A turn inside a turn: the button fires where the two layers together paint it. */
    @Test
    fun aButtonTurnedInsideATurnedBoxFiresWhereBothTurnsPaintIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var clicks = 0
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag(LAYERED_TAG).size(120, 40).placementLayer { rotationZ = 90f },
                        ) {
                            Box(modifier = SwingModifier.fillMaxSize().placementLayer { rotationZ = 90f }) {
                                Button("Go", { clicks++ }, SwingModifier.testTag(INNER_TAG).size(40, 40))
                            }
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val button = window.onNodeWithTag(INNER_TAG).fetch<JComponent>()

            // The two quarter turns about one center make a half turn, which puts the button at the far end.
            stageInput.performMouseInput {
                click(layered.paintedAt(stageInput.fetch(), button, 20.0, 20.0, turn(120, 40, rotation = 180f)))
            }
            assertEquals(1, clicks, "a click where both turns paint the button must fire")

            stageInput.performMouseInput {
                click(layered.unturnedAt(stageInput.fetch(), 20, 20))
            }
            assertEquals(1, clicks, "a click where the button would stand unturned must not fire")
        }
}

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

class PlacementOverflowHitTestTest {
    /**
     * A child scaled to twice its size paints past [depth] containers of its own size. A press there reaches it,
     * and one on its shadow, which those containers grow by as well, does not.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("containerDepths")
    fun aPressWhereAScaledChildPaintsPastItsContainersReachesIt(depth: Int) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var presses = 0
            setWindowContent {
                Box {
                    Stage {
                        Nested(depth) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .size(120, 30)
                                        .shadow(12, Color.BLACK)
                                        .placementLayer(DoubleScale)
                                        .mouseListener(onMousePressed = { presses++ }),
                            ) {}
                        }
                    }
                }
            }
            val canvas = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val turn = turn(120, 30, rotation = 0f, scale = 2f)

            // Inside the shadow's outsets, which paints past the containers as well.
            stageInput.performMouseInput {
                click(
                    canvas.paintedAt(
                        stageInput.fetch(),
                        canvas,
                        -4.0 - canvas.paintBounds.x,
                        15.0 - canvas.paintBounds.y,
                        turn,
                    ),
                )
            }
            assertEquals(0, presses, "a press on the shadow past the containers must miss")

            // A quarter of the way in, which the doubled layer paints 30 past the containers' left edge.
            stageInput.performMouseInput {
                click(
                    canvas.paintedAt(
                        stageInput.fetch(),
                        canvas,
                        15.0 - canvas.paintBounds.x,
                        15.0 - canvas.paintBounds.y,
                        turn,
                    ),
                )
            }
            assertEquals(1, presses, "a press inside the scaled outline past the containers must hit")
        }

    /** A child wider than its container is hit past the container's layout bounds, mirrored by a layer or not. */
    @Test
    fun aChildOverflowingItsContainerIsHitPastItMirroredOrNot() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var presses = 0
            var scaleX by mutableFloatStateOf(1f)
            setWindowContent {
                Box {
                    Stage {
                        Nested(depth = 1) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .requiredSize(160, 30)
                                        .shadow(12, Color.BLACK)
                                        .placementLayer { this.scaleX = scaleX }
                                        .mouseListener(onMousePressed = { presses++ }),
                            ) {}
                        }
                    }
                }
            }
            val canvas = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            // 8 into the canvas, which overflows the container by 20 on each side.
            val stage = stageInput.fetch<JComponent>()
            val past = canvas.unturnedAt(stage, 8, 15)
            window.onNodeWithTag(OUTER_TAG).assertIsOfType<JComponent>().assertProperty(
                true,
                "the point is past the container: $past",
            ) { past.x < unturnedAt(stage, 0, 0).x }

            stageInput.performMouseInput {
                click(past)
            }
            assertEquals(1, presses, "an untransformed child is hit past its container")

            scaleX = -1f
            awaitIdle()
            stageInput.performMouseInput {
                click(past)
            }
            assertEquals(2, presses, "a mirrored child is hit past its container")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("containerDepths")
    fun aClickWhereAScaledTextFieldPaintsPastItsContainersPlacesTheCaret(depth: Int) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            setWindowContent {
                Box {
                    Stage {
                        Nested(depth) {
                            Box(
                                modifier = SwingModifier.testTag(LAYERED_TAG).size(120, 30).placementLayer(DoubleScale),
                            ) {
                                TextField(
                                    "abcdefghij",
                                    onValueChange = {},
                                    modifier = SwingModifier.testTag(INNER_TAG).fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val field = window.onNodeWithTag(INNER_TAG).fetch<JTextField>()
            val at = layered.charAt(stageInput.fetch(), field, 1, turn(120, 30, rotation = 0f, scale = 2f))
            stageInput.assertProperty(true) {
                val stage = this
                window.onNodeWithTag(OUTER_TAG).assertIsOfType<JComponent>().assertProperty(true) {
                    val edge = unturnedAt(stage, 0, 0)
                    assertTrue(at.x < edge.x, "character 1 paints past the containers' left edge $edge: $at")
                    true
                }
                true
            }

            stageInput.performMouseInput {
                click(at)
            }

            window.onNodeWithTag(INNER_TAG).assertIsOfType<JTextField>().assertProperty(1) {
                caretPosition
            }
        }

    /**
     * A repaint of a descendant merged into a dirty container two levels up still paints where the layer puts the
     * descendant.
     */
    @Test
    fun aCaretRepaintMergedIntoAnOuterContainerRepaintsWhereTheLayerPaintsIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box(modifier = SwingModifier.testTag(OUTER_TAG)) {
                    TurnedTextField(
                        rotation = 90f,
                        modifier = SwingModifier.drawBehind { graphics.clipBounds?.let { repainted += it } },
                    )
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val field = window.onNodeWithTag(INNER_TAG).fetch<JTextField>()
            val stage = stageInput.fetch<JComponent>()
            val outer = window.onNodeWithTag(OUTER_TAG).fetch<JComponent>()
            awaitIdle()
            repainted.clear()

            // The caret's own repaint, and one far from it that makes the outer box the dirty root.
            field.repaint(field.modelToView2D(8).bounds.apply { grow(2, 0) })
            outer.repaint(outer.width - 2, outer.height - 2, 1, 1)
            awaitIdle()

            val caret = layered.charAt(stageInput.fetch(), field, 8, turn(200, 30, rotation = 90f))
            caret.translate(stage.paintBounds.x, stage.paintBounds.y)
            assertTrue(repainted.any { it.contains(caret) }, "the turned caret at $caret must repaint: $repainted")
        }

    @Test
    fun aClickJustOutsideAClippingContainerMissesItsScaledChild() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var presses = 0
            setWindowContent {
                Box {
                    Stage {
                        Box(modifier = SwingModifier.testTag(OUTER_TAG).size(120, 30).clipToBounds()) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .size(120, 30)
                                        .placementLayer(DoubleScale)
                                        .mouseListener(onMousePressed = { presses++ }),
                            ) {}
                        }
                    }
                }
            }
            val canvas = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val turn = turn(120, 30, rotation = 0f, scale = 2f)

            // A quarter of the way in, which the doubled layer paints 30 past the clipping box's left edge.
            stageInput.performMouseInput {
                click(
                    canvas.paintedAt(
                        stageInput.fetch(),
                        canvas,
                        15.0 - canvas.paintBounds.x,
                        15.0 - canvas.paintBounds.y,
                        turn,
                    ),
                )
            }
            assertEquals(0, presses, "a press where the clip cuts the child away must miss")

            stageInput.performMouseInput {
                click(
                    canvas.paintedAt(
                        stageInput.fetch(),
                        canvas,
                        40.0 - canvas.paintBounds.x,
                        15.0 - canvas.paintBounds.y,
                        turn,
                    ),
                )
            }
            assertEquals(1, presses, "a press inside the clip must hit")
        }

    /** A turned child repainting part of itself repaints all it paints, with no decorated container around it. */
    @Test
    fun aTurnedCanvasRepaintingPartOfItselfInAPlainBoxRepaintsAllOfIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box {
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag(LAYERED_TAG)
                                .size(100, 100)
                                .drawBehind { graphics.clipBounds?.let { repainted += it } }
                                .placementLayer(EighthTurn),
                    ) {}
                }
            }
            val canvas = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            awaitIdle()
            repainted.clear()

            canvas.repaint(canvas.width / 2, canvas.height / 2, 2, 2)
            awaitIdle()

            assertTrue(repainted.any { it.width >= 100 && it.height >= 100 }, "the whole canvas repaints: $repainted")
        }

    /** The cursor follows the pointer between two components one turn inside another turns. */
    @Test
    fun thePointerCrossingTwoComponentsInsideANestedTurnShowsEachOnesCursor() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            setWindowContent {
                Box {
                    Stage {
                        Box(modifier = SwingModifier.testTag(LAYERED_TAG).size(200, 30).placementLayer(QuarterTurn)) {
                            Row(modifier = SwingModifier.fillMaxSize().placementLayer(QuarterTurn)) {
                                TextField(
                                    "abcdefghij",
                                    onValueChange = {},
                                    modifier = SwingModifier.testTag(INNER_TAG).size(100, 30),
                                )
                                Button("Go", {}, SwingModifier.testTag("other").size(100, 30))
                            }
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            // The two quarter turns about one center make a half turn.
            val turn = turn(200, 30, rotation = 180f)
            val overField =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag(INNER_TAG).fetch<JComponent>(),
                    50.0,
                    15.0,
                    turn,
                )
            val overButton =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag("other").fetch<JComponent>(),
                    50.0,
                    15.0,
                    turn,
                )

            stageInput.performMouseInput {
                moveTo(overField)
            }
            stageInput.assertProperty(true) {
                val stage = this
                window.assertIsOfType<JFrame>().assertProperty(Cursor.TEXT_CURSOR, "over the field") {
                    findComponentAt(SwingUtilities.convertPoint(stage, overField, this)).cursor.type
                }
                true
            }

            stageInput.performMouseInput {
                moveTo(overButton)
            }
            stageInput.assertProperty(true) {
                val stage = this
                window.assertIsOfType<JFrame>().assertProperty(Cursor.DEFAULT_CURSOR, "over the button") {
                    findComponentAt(SwingUtilities.convertPoint(stage, overButton, this)).cursor.type
                }
                true
            }
        }

    /** As in AWT, a press while another button is held goes where the first press went. */
    @Test
    fun aSecondButtonPressedWhileTheFirstIsHeldGoesToTheFirstPressTarget() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val firstPresses = ArrayList<Int>()
            var otherPresses = 0
            setUpTwoPressTargets(firstPresses, { otherPresses++ })
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val turn = turn(200, 30, rotation = 90f)
            val overFirst =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag(INNER_TAG).fetch<JComponent>(),
                    50.0,
                    15.0,
                    turn,
                )
            val overOther =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag("other").fetch<JComponent>(),
                    50.0,
                    15.0,
                    turn,
                )

            stageInput.performMouseInput {
                moveTo(overFirst)
                press(MouseEvent.BUTTON1)
            }
            stageInput.performMouseInput {
                moveTo(overOther)
            }
            stageInput.performMouseInput {
                press(MouseEvent.BUTTON3)
            }
            stageInput.performMouseInput {
                release(MouseEvent.BUTTON3)
            }
            stageInput.performMouseInput {
                release(MouseEvent.BUTTON1)
            }

            assertEquals(listOf(MouseEvent.BUTTON1, MouseEvent.BUTTON3), firstPresses, "both presses reach the first")
            assertEquals(0, otherPresses, "the component under the second press hears none")
        }

    private suspend fun ComposeSwingTest.setUpTwoPressTargets(
        firstPresses: MutableList<Int>,
        onOtherPress: () -> Unit,
    ) {
        setWindowContent {
            Box {
                Stage {
                    Row(modifier = SwingModifier.testTag(LAYERED_TAG).size(200, 30).placementLayer(QuarterTurn)) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(INNER_TAG)
                                    .size(100, 30)
                                    .mouseListener(onMousePressed = { firstPresses += it.button }),
                        ) {}
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("other")
                                    .size(100, 30)
                                    .mouseListener(onMousePressed = { onOtherPress() }),
                        ) {}
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun containerDepths(): List<Arguments> =
            listOf(
                Arguments.of(Named.of("one container", 1)),
                Arguments.of(Named.of("two containers", 2)),
            )
    }
}

/** Capture, wheel, and target lifetime behavior of the transformed-child glass pane. */
class PlacementHitTestInputTest {
    @Test
    fun aWheelTurnOnATurnedChildReachesItsPaintedPoint() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var childTurns = 0
            var childPoint: Point? = null
            setWindowContent {
                Box {
                    Stage {
                        Box(modifier = SwingModifier.testTag(LAYERED_TAG).size(120, 40).placementLayer(QuarterTurn)) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(INNER_TAG)
                                        .fillMaxSize()
                                        .mouseWheelListener {
                                            childTurns++
                                            childPoint = it.point
                                        },
                            ) {}
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val at =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag(INNER_TAG).fetch<JComponent>(),
                    10.0,
                    20.0,
                    turn(120, 40, rotation = 90f),
                )

            stageInput.performMouseInput {
                moveTo(at)
                scroll(1)
            }
            assertEquals(1, childTurns, "the child hears the wheel turn")
            assertTrue(checkNotNull(childPoint).distance(Point(10, 20)) <= 1.5, "the wheel lands at the unturned point")
        }

    @Test
    fun aWheelTurnThroughNestedGlassPanesReachesTheChildOnce() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var childTurns = 0
            setWindowContent {
                Box {
                    Stage {
                        Box(modifier = SwingModifier.testTag(LAYERED_TAG).size(100, 100).placementLayer(EighthTurn)) {
                            Box(modifier = SwingModifier.fillMaxSize().placementLayer(EighthTurn)) {
                                Canvas(
                                    modifier =
                                        SwingModifier
                                            .testTag(INNER_TAG)
                                            .fillMaxSize()
                                            .mouseWheelListener { childTurns++ },
                                ) {}
                            }
                        }
                    }
                }
            }

            val at = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)
            stageInput.performMouseInput {
                moveTo(at)
                scroll(1)
            }

            assertEquals(1, childTurns)
        }

    @Test
    fun aReparentedHoverAndPressTargetReceivesNoMoreEvents() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val heard = ArrayList<Int>()
            setUpReparentedTarget(heard)
            val target = window.onNodeWithTag(INNER_TAG).fetch<JComponent>()
            val originalParent = target.parent
            val otherParent = JPanel()
            val at = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)

            stageInput.performMouseInput { moveTo(at) }
            stageInput.performMouseInput {
                press(MouseEvent.BUTTON1)
            }
            assertEquals(listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_PRESSED), heard)

            originalParent.remove(target)
            otherParent.add(target)
            try {
                stageInput.performMouseInput { moveTo(at) }
                stageInput.performMouseInput {
                    release(MouseEvent.BUTTON1)
                }
                stageInput.performMouseInput { moveTo(at) }
                assertEquals(
                    listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_PRESSED),
                    heard,
                    "a component outside the original child gets no drag, release or exit",
                )
            } finally {
                otherParent.remove(target)
                originalParent.add(target)
            }
        }

    private suspend fun ComposeSwingTest.setUpReparentedTarget(heard: MutableList<Int>) {
        setWindowContent {
            Box {
                Stage {
                    Box(modifier = SwingModifier.testTag(LAYERED_TAG).size(100, 100).placementLayer(EighthTurn)) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(INNER_TAG)
                                    .fillMaxSize()
                                    .mouseListener(
                                        onMouseEntered = { heard += it.id },
                                        onMouseExited = { heard += it.id },
                                        onMousePressed = { heard += it.id },
                                        onMouseReleased = { heard += it.id },
                                    ).mouseMotionListener(onMouseDragged = { heard += it.id }),
                        ) {}
                    }
                }
            }
        }
    }

    /** The press target of a turned child hears the drag and the release off it, where no child stands. */
    @Test
    fun aDragAndReleaseOffATurnedChildGoToItsPressTarget() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val heard = ArrayList<Int>()
            setWindowContent {
                Box {
                    Stage {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(LAYERED_TAG)
                                    .size(100, 100)
                                    .placementLayer(EighthTurn)
                                    .mouseListener(
                                        onMousePressed = { heard += it.id },
                                        onMouseReleased = { heard += it.id },
                                    ).mouseMotionListener(onMouseDragged = { heard += it.id }),
                        ) {}
                    }
                }
            }
            // The eighth turn keeps the center where it stands unturned; the stage's corner holds no child.
            val center = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)
            val corner = Point(5, 5)

            stageInput.performMouseInput {
                moveTo(center)
                press(MouseEvent.BUTTON1)
            }
            stageInput.performMouseInput {
                moveTo(corner)
            }
            stageInput.performMouseInput {
                release(MouseEvent.BUTTON1)
            }

            assertEquals(
                listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_RELEASED),
                heard,
                "the press, the drag and the release reach the child pressed",
            )
        }

    /** A press on a label inside a turned box goes to the label, which takes no mouse events, not to the box. */
    @Test
    fun aPressOnAPlainLabelInsideATurnedBoxDoesNotReachTheBoxesListener() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var presses = 0
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag(LAYERED_TAG)
                                    .size(120, 40)
                                    .placementLayer(EighthTurn)
                                    .mouseListener(onMousePressed = { presses++ }),
                        ) {
                            Label("Go", SwingModifier.fillMaxSize())
                        }
                    }
                }
            }
            // A turn about the center leaves the center where it is.
            stageInput.performMouseInput {
                click(window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 60, 20))
            }

            assertEquals(0, presses, "the box hears no press")
        }

    /** A turned child its policy leaves unplaced takes no press; placed again, it takes presses. */
    @Test
    fun aTurnedChildLeftUnplacedStopsTakingPressesUntilPlacedAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var placed by mutableStateOf(true)
            var presses = 0
            setWindowContent {
                Box {
                    Layout(
                        modifier = SwingModifier.testTag(STAGE_TAG).styleable { preferredSize(300, 300) },
                        content = {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .size(100, 100)
                                        .placementLayer(EighthTurn)
                                        .mouseListener(onMousePressed = { presses++ }),
                            ) {}
                        },
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints())
                            layout(300, 300) { if (placed) placeable.place(100, 100) }
                        },
                    )
                }
            }
            // The eighth turn keeps the center where it stands unturned.
            val center = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)
            stageInput.performMouseInput { click(center) }
            assertEquals(1, presses, "the placed turned child takes the press")

            placed = false
            awaitIdle()
            stageInput.performMouseInput { click(center) }
            assertEquals(1, presses, "a child left unplaced takes no press")

            placed = true
            awaitIdle()
            stageInput.performMouseInput { click(center) }
            assertEquals(2, presses, "the child placed again takes the press")
        }

    /** A right-click on a turned label that sets a popup menu of its own and has no listener opens that menu. */
    @Test
    fun aRightClickOnATurnedLabelWithItsOwnPopupMenuOpensIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var opened = 0
            val menu =
                JPopupMenu().apply {
                    add("Copy")
                    addPopupMenuListener(
                        object : PopupMenuListener {
                            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
                                opened++
                            }

                            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = Unit

                            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
                        },
                    )
                }
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag(LAYERED_TAG).size(120, 40).placementLayer { rotationZ = 30f },
                        ) {
                            SwingNode(
                                factory = { JLabel("Go").apply { componentPopupMenu = menu } },
                                modifier = SwingModifier.testTag(INNER_TAG).fillMaxSize(),
                            )
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()
            val over =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag(INNER_TAG).fetch<JComponent>(),
                    90.0,
                    20.0,
                    turn(120, 40, rotation = 30f),
                )

            stageInput.performMouseInput {
                moveTo(over)
            }
            stageInput.performMouseInput {
                press(MouseEvent.BUTTON3)
            }
            stageInput.performMouseInput {
                release(MouseEvent.BUTTON3)
            }
            menu.isVisible = false

            assertEquals(1, opened, "the right-click opens the label's own popup menu")
        }
}

/** Mouse events miss what a placement layer that only clips cuts away from its child. */
class PlacementClipHitTestTest {
    @Test
    fun aClickInsideTheLayoutBoundsButOutsideAClipOnlyLayerMissesTheChild() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var presses = 0
            setWindowContent {
                Box {
                    Stage {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(LAYERED_TAG)
                                    .placementLayer { clip = true }
                                    .offset(30, 30)
                                    .size(100, 100)
                                    .mouseListener(onMousePressed = { presses++ }),
                        ) {}
                    }
                }
            }
            val canvas = window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>()

            // Inside the layout bounds, past the box the layer clips to: the offset's, which stands 30 up and left.
            stageInput.performMouseInput {
                click(canvas.unturnedAt(stageInput.fetch(), 85, 85))
            }
            assertEquals(0, presses, "a press the clip cuts away must miss")

            stageInput.performMouseInput {
                click(canvas.unturnedAt(stageInput.fetch(), 20, 20))
            }
            assertEquals(1, presses, "a press inside the clip must hit")
        }
}

/** Input remains correct when a container replaces its composed children. */
class PlacementHitTestLifecycleTest {
    /**
     * A panel emptied by disposing its composition reads presses through the layer of a child the next composition
     * turns, so they reach a component inside that child.
     */
    @Test
    fun aPanelEmptiedByItsCompositionReachesAChildTurnedAfter() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var presses = 0
            var stageScope: BoxScope? = null
            setWindowContent { Box { Stage { stageScope = this } } }
            val stage = stageInput.fetch<JComponent>()
            val scope = checkNotNull(stageScope)
            repeat(2) { round ->
                val composition =
                    stage.setContent {
                        with(scope) {
                            Box(
                                modifier = SwingModifier.testTag(LAYERED_TAG).size(120, 30).placementLayer(QuarterTurn),
                            ) {
                                Canvas(
                                    modifier =
                                        SwingModifier
                                            .testTag(INNER_TAG)
                                            .size(120, 30)
                                            .mouseListener(onMousePressed = { presses++ }),
                                ) {}
                            }
                        }
                    }
                try {
                    awaitIdle()
                    val turn = turn(120, 30, rotation = 90f)

                    // Turned, the box paints this point of its canvas 35 above its unturned box.
                    stageInput.performMouseInput {
                        click(
                            window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().paintedAt(
                                stageInput.fetch(),
                                window.onNodeWithTag(INNER_TAG).fetch<JComponent>(),
                                10.0,
                                15.0,
                                turn,
                            ),
                        )
                    }
                    assertEquals(round + 1, presses, "composition $round: the press reaches the turned child")
                } finally {
                    composition.dispose()
                }
                awaitIdle()
            }
        }
}

/** The pointer entering and leaving what a rotating or scaling placement layer paints is reported once per crossing. */
class PlacementHitTestCrossingTest {
    /**
     * A drag from a turned child onto its container and onto a plain sibling, and the pointer coming back, reports
     * one enter and one exit to the container per crossing, and an exit after each enter to the sibling.
     */
    @Test
    fun aDragOffATurnedChildReportsEachCrossingOnce() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val container = ArrayList<Int>()
            val sibling = ArrayList<Int>()
            setUpCrossingTargets(container, sibling)
            // A turn about the center leaves the center where it is; the container's last third holds no child.
            val overTurned =
                window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overSibling = window.onNodeWithTag("other").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overContainer =
                window.onNodeWithTag(OUTER_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 100, 20)

            stageInput.performMouseInput { moveTo(overContainer) }
            stageInput.performMouseInput { moveTo(overTurned) }
            stageInput.performMouseInput {
                press(MouseEvent.BUTTON1)
            }
            stageInput.performMouseInput { moveTo(overContainer) }
            stageInput.performMouseInput { moveTo(overSibling) }
            stageInput.performMouseInput { release(MouseEvent.BUTTON1) }
            stageInput.performMouseInput { moveTo(overContainer) }
            stageInput.performMouseInput { moveTo(overTurned) }

            val crossings = List(3) { listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED) }.flatten()
            assertEquals(crossings, container, "the container hears each crossing once")
            // AWT reads a drag handed to the press target at the target's own point, and may report the sibling
            // crossed again meanwhile.
            val alternating =
                sibling.isNotEmpty() && sibling.size % 2 == 0 &&
                    sibling.indices.all { sibling[it] == crossings[it % 2] }
            assertTrue(alternating, "the sibling hears an exit after each enter, but heard $sibling")
        }

    private suspend fun ComposeSwingTest.setUpCrossingTargets(
        container: MutableList<Int>,
        sibling: MutableList<Int>,
    ) {
        setWindowContent {
            Box {
                Stage {
                    Row(
                        modifier =
                            SwingModifier
                                .testTag(OUTER_TAG)
                                .size(120, 40)
                                .mouseListener(
                                    onMouseEntered = { container += it.id },
                                    onMouseExited = { container += it.id },
                                ),
                    ) {
                        Canvas(
                            modifier = SwingModifier.testTag(LAYERED_TAG).size(40, 40).placementLayer(EighthTurn),
                        ) {}
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("other")
                                    .size(40, 40)
                                    .mouseListener(
                                        onMouseEntered = { sibling += it.id },
                                        onMouseExited = { sibling += it.id },
                                    ),
                        ) {}
                    }
                }
            }
        }
    }

    /** A child turned, straightened and turned again hears the pointer enter it again. */
    @Test
    fun aChildTurnedAgainHearsThePointerEnterAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var entered = 0
            var rotation by mutableFloatStateOf(45f)
            setWindowContent {
                Box {
                    Stage {
                        Row {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag(LAYERED_TAG)
                                        .size(40, 40)
                                        .placementLayer { rotationZ = rotation }
                                        .mouseListener(onMouseEntered = { entered++ }),
                            ) {}
                            Canvas(modifier = SwingModifier.testTag("other").size(40, 40)) {}
                        }
                    }
                }
            }
            // A turn about the center leaves the center where it is.
            val overTurned =
                window.onNodeWithTag(LAYERED_TAG).fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)
            val overOther = window.onNodeWithTag("other").fetch<JComponent>().unturnedAt(stageInput.fetch(), 20, 20)

            stageInput.performMouseInput { moveTo(overTurned) }
            assertEquals(1, entered, "the pointer enters the turned child")

            rotation = 0f
            awaitIdle()
            stageInput.performMouseInput { moveTo(overOther) }
            rotation = 45f
            awaitIdle()
            stageInput.performMouseInput { moveTo(overTurned) }

            assertEquals(2, entered, "the pointer enters the child turned again")
        }
}

@Composable
private inline fun BoxScope.Stage(
    modifier: SwingModifier = SwingModifier,
    crossinline content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.testTag(STAGE_TAG).styleable { preferredSize(300, 300) },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** [content] inside [depth] nested boxes of its own size, the outermost tagged [OUTER_TAG]. */
@Composable
private fun BoxScope.Nested(
    depth: Int,
    outermost: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = (if (outermost) SwingModifier.testTag(OUTER_TAG) else SwingModifier).size(120, 30)) {
        if (depth > 1) Nested(depth - 1, outermost = false, content) else content()
    }
}

private val EighthTurn: PlacementLayerScope.() -> Unit = { rotationZ = 45f }

private val QuarterTurn: PlacementLayerScope.() -> Unit = { rotationZ = 90f }

private val DoubleScale: PlacementLayerScope.() -> Unit = {
    scaleX = 2f
    scaleY = 2f
}

@Composable
private fun BoxScope.TurnedTextField(
    modifier: SwingModifier = SwingModifier,
    rotation: Float = 0f,
    scale: Float = 1f,
) {
    Stage(modifier) {
        Box(
            modifier =
                SwingModifier.testTag(LAYERED_TAG).size(200, 30).placementLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            TextField("abcdefghij", onValueChange = {}, modifier = SwingModifier.testTag(INNER_TAG).fillMaxSize())
        }
    }
}

/** The matrix a layer turning a [width] by [height] box by [rotation] and [scale] about its center paints through. */
private fun turn(
    width: Int,
    height: Int,
    rotation: Float,
    scale: Float = 1f,
): AffineTransform =
    TransformOrigin.Center.createTransform(
        Rectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble()),
        scale,
        scale,
        rotation,
    )

/** This decorated component's paint bounds, its bounds, from its layout origin. */
private val Component.paintBounds: Rectangle
    get() {
        val outsets = assertIs<Decoratable>(this).decoration.paintOutsets()
        return Rectangle(-outsets.left, -outsets.top, width, height)
    }

/** Where ([x], [y]) of this decorated component's layout bounds stands unturned, in [stage]'s coordinates. */
private fun Component.unturnedAt(
    stage: Component,
    x: Int,
    y: Int,
): Point = SwingUtilities.convertPoint(this, 0, 0, stage).apply { translate(x - paintBounds.x, y - paintBounds.y) }

/**
 * Where this layered component paints ([x], [y]) of [inner], its descendant or itself, through [turn], in
 * [stage]'s coordinates.
 */
private fun Component.paintedAt(
    stage: Component,
    inner: Component,
    x: Double,
    y: Double,
    turn: AffineTransform,
): Point {
    val offset = SwingUtilities.convertPoint(inner, 0, 0, this)
    val painted = turn.transform(Point2D.Double(offset.x + x + paintBounds.x, offset.y + y + paintBounds.y), null)
    val origin = SwingUtilities.convertPoint(this, 0, 0, stage)
    return Point(
        floor(painted.x - paintBounds.x).toInt() + origin.x,
        floor(painted.y - paintBounds.y).toInt() + origin.y,
    )
}

/** Where this layered component paints the middle of [field]'s character [index], in [stage]'s coordinates. */
private fun Component.charAt(
    stage: Component,
    field: JTextField,
    index: Int,
    turn: AffineTransform,
): Point {
    val start = field.modelToView2D(index)
    val end = field.modelToView2D(index + 1)
    return paintedAt(stage, field, (start.x + end.x) / 2 - (end.x - start.x) / 4, start.centerY, turn)
}

private const val STAGE_TAG = "stage"
private const val LAYERED_TAG = "layered"
private const val INNER_TAG = "inner"
private const val OUTER_TAG = "outer"
