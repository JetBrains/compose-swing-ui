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
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
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
            val canvas = windowNode(LAYERED_TAG)
            val turn = turn(100, 100, rotation = 45f)

            // Near the unturned top-left corner, which the eighth turn puts above the unturned box. The canvas reads
            // its layout bounds' origin where its paint outsets ends.
            val unturned = Point(6 - canvas.paintBounds.x, 6 - canvas.paintBounds.y)
            click(canvas.paintedAt(canvas, unturned.x.toDouble(), unturned.y.toDouble(), turn))
            assertEquals(1, presses.size, "a press inside the turned outline must hit")
            val point = presses.single()
            assertTrue(point.distance(unturned) <= 1.5, "the press lands at the unturned point $unturned: $point")

            // Near the unturned top-left corner, which the eighth turn leaves outside the diamond.
            click(canvas.unturnedAt(4, 4))
            assertEquals(1, presses.size, "a press inside only the unturned box must miss")
        }

    @Test
    fun aButtonInATurnedSceneFiresOnlyInsideItsTurnedOutline() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val layered = windowNode(LAYERED_TAG)
            val button = windowNode(INNER_TAG)
            val turn = turn(120, 40, rotation = 90f)

            // Near the unturned left edge, which the quarter turn puts above the unturned box.
            click(layered.paintedAt(button, 10.0, 20.0, turn))
            assertEquals(1, clicks, "a click inside the turned outline must fire")

            // Near the unturned left edge, which the quarter turn leaves outside the button.
            click(layered.unturnedAt(10, 20))
            assertEquals(1, clicks, "a click inside only the unturned box must not fire")
        }

    /** A turn inside a turn: the button fires where the two layers together paint it. */
    @Test
    fun aButtonTurnedInsideATurnedBoxFiresWhereBothTurnsPaintIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val layered = windowNode(LAYERED_TAG)
            val button = windowNode(INNER_TAG)

            // The two quarter turns about one center make a half turn, which puts the button at the far end.
            click(layered.paintedAt(button, 20.0, 20.0, turn(120, 40, rotation = 180f)))
            assertEquals(1, clicks, "a click where both turns paint the button must fire")

            click(layered.unturnedAt(20, 20))
            assertEquals(1, clicks, "a click where the button would stand unturned must not fire")
        }

    @Test
    fun thePointerOverATurnedTextFieldShowsItsTextCursor() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            setWindowContent { Box { TurnedTextField(rotation = 90f) } }
            val layered = windowNode(LAYERED_TAG)
            val field = windowNode(INNER_TAG)
            val frame = frame()
            val over = layered.paintedAt(field, 10.0, 15.0, turn(200, 30, rotation = 90f))

            sendMouse(frame, MouseEvent.MOUSE_MOVED, over)
            assertEquals(Cursor.TEXT_CURSOR, frame.findComponentAt(over).cursor.type, "over the turned field")

            val unturned = layered.unturnedAt(10, 15)
            sendMouse(frame, MouseEvent.MOUSE_MOVED, unturned)
            assertEquals(Cursor.DEFAULT_CURSOR, frame.findComponentAt(unturned).cursor.type, "where it would stand")
        }

    @Test
    fun aDragAcrossATurnedTextFieldSelectsTheCharactersItCrosses() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            setWindowContent { Box { TurnedTextField(rotation = 90f) } }
            val layered = windowNode(LAYERED_TAG)
            val field = windowNode(INNER_TAG) as JTextField
            val turn = turn(200, 30, rotation = 90f)
            val frame = frame()
            // The Aqua look and feel selects the whole text when the field gains focus at either end of it, and a press
            // inside a selection starts dragging the selection instead of selecting anew.
            field.caretPosition = 8

            sendMouse(frame, MouseEvent.MOUSE_PRESSED, layered.charAt(field, 1, turn), InputEvent.BUTTON1_DOWN_MASK, 1)
            sendMouse(frame, MouseEvent.MOUSE_DRAGGED, layered.charAt(field, 6, turn), InputEvent.BUTTON1_DOWN_MASK)
            sendMouse(frame, MouseEvent.MOUSE_RELEASED, layered.charAt(field, 6, turn), 0, 1)

            onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(INNER_TAG).assertProperty(1 to 6) {
                (this as JTextField).selectionStart to selectionEnd
            }
        }

    @Test
    fun aClickPlacesTheCaretAtTheClickedCharacterUnderARotation() = assertCaretFollowsTheClick(rotation = 30f)

    @Test
    fun aClickPlacesTheCaretAtTheClickedCharacterUnderAScale() = assertCaretFollowsTheClick(scale = 1.5f)

    private fun assertCaretFollowsTheClick(
        rotation: Float = 0f,
        scale: Float = 1f,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setWindowContent { Box { TurnedTextField(rotation = rotation, scale = scale) } }
        val layered = windowNode(LAYERED_TAG)
        val field = windowNode(INNER_TAG) as JTextField

        click(layered.charAt(field, 4, turn(200, 30, rotation, scale)))

        onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(INNER_TAG).assertProperty(4) {
            (this as JTextField).caretPosition
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
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box {
                    TurnedTextField(
                        rotation = 90f,
                        modifier = SwingModifier.drawBehind { graphics.clipBounds?.let { repainted += it } },
                    )
                }
            }
            val layered = windowNode(LAYERED_TAG)
            val field = windowNode(INNER_TAG) as JTextField
            val stage = windowNode(STAGE_TAG)
            awaitIdle()
            repainted.clear()

            field.caretPosition = 8
            awaitIdle()

            val caret =
                SwingUtilities.convertPoint(
                    frame(),
                    layered.charAt(field, 8, turn(200, 30, rotation = 90f)),
                    stage,
                )
            caret.translate(stage.paintBounds.x, stage.paintBounds.y)
            assertTrue(repainted.any { it.contains(caret) }, "the turned caret at $caret must repaint: $repainted")
            assertFalse(repainted.any { it.contains(Point(1, 1)) }, "the caret alone repaints: $repainted")
        }

    @Test
    fun aPressWhereAScaledChildPaintsPastItsContainerReachesIt() = assertPressPastTheContainers(depth = 1)

    @Test
    fun aPressWhereAScaledChildPaintsPastTwoContainersReachesIt() = assertPressPastTheContainers(depth = 2)

    /**
     * A child scaled to twice its size paints past [depth] containers of its own size. A press there reaches it,
     * and one on its shadow, which those containers grow by as well, does not.
     */
    private fun assertPressPastTheContainers(depth: Int) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val canvas = windowNode(LAYERED_TAG)
            val turn = turn(120, 30, rotation = 0f, scale = 2f)

            // Inside the shadow's outsets, which paints past the containers as well.
            click(canvas.paintedAt(canvas, -4.0 - canvas.paintBounds.x, 15.0 - canvas.paintBounds.y, turn))
            assertEquals(0, presses, "a press on the shadow past the containers must miss")

            // A quarter of the way in, which the doubled layer paints 30 past the containers' left edge.
            click(canvas.paintedAt(canvas, 15.0 - canvas.paintBounds.x, 15.0 - canvas.paintBounds.y, turn))
            assertEquals(1, presses, "a press inside the scaled outline past the containers must hit")
        }

    /** A child wider than its container is hit past the container's layout bounds, mirrored by a layer or not. */
    @Test
    fun aChildOverflowingItsContainerIsHitPastItMirroredOrNot() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val canvas = windowNode(LAYERED_TAG)
            // 8 into the canvas, which overflows the container by 20 on each side.
            val past = canvas.unturnedAt(8, 15)
            assertTrue(past.x < windowNode(OUTER_TAG).unturnedAt(0, 0).x, "the point is past the container: $past")

            click(past)
            assertEquals(1, presses, "an untransformed child is hit past its container")

            scaleX = -1f
            awaitIdle()
            click(past)
            assertEquals(2, presses, "a mirrored child is hit past its container")
        }

    @Test
    fun aClickWhereAScaledTextFieldPaintsPastItsContainerPlacesTheCaret() = assertCaretPastTheContainers(depth = 1)

    @Test
    fun aClickWhereAScaledTextFieldPaintsPastTwoContainersPlacesTheCaret() = assertCaretPastTheContainers(depth = 2)

    private fun assertCaretPastTheContainers(depth: Int) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val layered = windowNode(LAYERED_TAG)
            val field = windowNode(INNER_TAG) as JTextField
            val at = layered.charAt(field, 1, turn(120, 30, rotation = 0f, scale = 2f))
            val edge = windowNode(OUTER_TAG).unturnedAt(0, 0)
            assertTrue(at.x < edge.x, "character 1 paints past the containers' left edge $edge: $at")

            click(at)

            onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(INNER_TAG).assertProperty(1) {
                (this as JTextField).caretPosition
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
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box(modifier = SwingModifier.testTag(OUTER_TAG)) {
                    TurnedTextField(
                        rotation = 90f,
                        modifier = SwingModifier.drawBehind { graphics.clipBounds?.let { repainted += it } },
                    )
                }
            }
            val layered = windowNode(LAYERED_TAG)
            val field = windowNode(INNER_TAG) as JTextField
            val stage = windowNode(STAGE_TAG)
            val outer = windowNode(OUTER_TAG)
            awaitIdle()
            repainted.clear()

            // The caret's own repaint, and one far from it that makes the outer box the dirty root.
            field.repaint(field.modelToView2D(8).bounds.apply { grow(2, 0) })
            outer.repaint(outer.width - 2, outer.height - 2, 1, 1)
            awaitIdle()

            val caret =
                SwingUtilities.convertPoint(
                    frame(),
                    layered.charAt(field, 8, turn(200, 30, rotation = 90f)),
                    stage,
                )
            caret.translate(stage.paintBounds.x, stage.paintBounds.y)
            assertTrue(repainted.any { it.contains(caret) }, "the turned caret at $caret must repaint: $repainted")
        }

    @Test
    fun aClickJustOutsideAClippingContainerMissesItsScaledChild() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val canvas = windowNode(LAYERED_TAG)
            val turn = turn(120, 30, rotation = 0f, scale = 2f)

            // A quarter of the way in, which the doubled layer paints 30 past the clipping box's left edge.
            click(canvas.paintedAt(canvas, 15.0 - canvas.paintBounds.x, 15.0 - canvas.paintBounds.y, turn))
            assertEquals(0, presses, "a press where the clip cuts the child away must miss")

            click(canvas.paintedAt(canvas, 40.0 - canvas.paintBounds.x, 15.0 - canvas.paintBounds.y, turn))
            assertEquals(1, presses, "a press inside the clip must hit")
        }

    /** A turned child repainting part of itself repaints all it paints, with no decorated container around it. */
    @Test
    fun aTurnedCanvasRepaintingPartOfItselfInAPlainBoxRepaintsAllOfIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val canvas = windowNode(LAYERED_TAG)
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
            val layered = windowNode(LAYERED_TAG)
            val frame = frame()
            // The two quarter turns about one center make a half turn.
            val turn = turn(200, 30, rotation = 180f)
            val overField = layered.paintedAt(windowNode(INNER_TAG), 50.0, 15.0, turn)
            val overButton = layered.paintedAt(windowNode("other"), 50.0, 15.0, turn)

            sendMouse(frame, MouseEvent.MOUSE_MOVED, overField)
            assertEquals(Cursor.TEXT_CURSOR, frame.findComponentAt(overField).cursor.type, "over the field")

            sendMouse(frame, MouseEvent.MOUSE_MOVED, overButton)
            assertEquals(Cursor.DEFAULT_CURSOR, frame.findComponentAt(overButton).cursor.type, "over the button")
        }

    /** As in AWT, a press while another button is held goes where the first press went. */
    @Test
    fun aSecondButtonPressedWhileTheFirstIsHeldGoesToTheFirstPressTarget() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val firstPresses = ArrayList<Int>()
            var otherPresses = 0
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
                                        .mouseListener(onMousePressed = { otherPresses++ }),
                            ) {}
                        }
                    }
                }
            }
            val layered = windowNode(LAYERED_TAG)
            val turn = turn(200, 30, rotation = 90f)
            val overFirst = layered.paintedAt(windowNode(INNER_TAG), 50.0, 15.0, turn)
            val overOther = layered.paintedAt(windowNode("other"), 50.0, 15.0, turn)
            val bothHeld = InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK

            sendMouse(frame(), MouseEvent.MOUSE_PRESSED, overFirst, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1)
            sendMouse(frame(), MouseEvent.MOUSE_DRAGGED, overOther, InputEvent.BUTTON1_DOWN_MASK)
            sendMouse(frame(), MouseEvent.MOUSE_PRESSED, overOther, bothHeld, MouseEvent.BUTTON3)
            sendMouse(frame(), MouseEvent.MOUSE_RELEASED, overOther, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON3)
            sendMouse(frame(), MouseEvent.MOUSE_RELEASED, overOther, 0, MouseEvent.BUTTON1)

            assertEquals(listOf(MouseEvent.BUTTON1, MouseEvent.BUTTON3), firstPresses, "both presses reach the first")
            assertEquals(0, otherPresses, "the component under the second press hears none")
        }
}

/** Capture, wheel, and target lifetime behavior of the transformed-child glass pane. */
class PlacementHitTestInputTest {
    @Test
    fun aWheelTurnOnATurnedChildReachesItsPaintedPoint() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val layered = windowNode(LAYERED_TAG)
            val at = layered.paintedAt(windowNode(INNER_TAG), 10.0, 20.0, turn(120, 40, rotation = 90f))

            val glassPane = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(STAGE_TAG).onChildAt(0)
            glassPane.performMouseWheel(
                rotation = 1,
                position = SwingUtilities.convertPoint(frame(), at, glassPane.fetch()),
            )
            assertEquals(1, childTurns, "the child hears the wheel turn")
            assertTrue(checkNotNull(childPoint).distance(Point(10, 20)) <= 1.5, "the wheel lands at the unturned point")
        }

    @Test
    fun aWheelTurnThroughNestedGlassPanesReachesTheChildOnce() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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

            val glassPane = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(STAGE_TAG).onChildAt(0)
            val at = windowNode(LAYERED_TAG).unturnedAt(50, 50)
            glassPane.performMouseWheel(
                rotation = 1,
                position = SwingUtilities.convertPoint(frame(), at, glassPane.fetch()),
            )

            assertEquals(1, childTurns)
        }

    @Test
    fun aReparentedHoverAndPressTargetReceivesNoMoreEvents() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val heard = ArrayList<Int>()
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
            val target = windowNode(INNER_TAG)
            val originalParent = target.parent
            val otherParent = JPanel()
            val at = windowNode(LAYERED_TAG).unturnedAt(50, 50)

            sendMouse(frame(), MouseEvent.MOUSE_MOVED, at)
            sendMouse(frame(), MouseEvent.MOUSE_PRESSED, at, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1)
            assertEquals(listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_PRESSED), heard)

            originalParent.remove(target)
            otherParent.add(target)
            try {
                sendMouse(frame(), MouseEvent.MOUSE_DRAGGED, at, InputEvent.BUTTON1_DOWN_MASK)
                sendMouse(frame(), MouseEvent.MOUSE_RELEASED, at, 0, MouseEvent.BUTTON1)
                sendMouse(frame(), MouseEvent.MOUSE_MOVED, at)
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

    /** The press target of a turned child hears the drag and the release off it, where no child stands. */
    @Test
    fun aDragAndReleaseOffATurnedChildGoToItsPressTarget() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val center = windowNode(LAYERED_TAG).unturnedAt(50, 50)
            val corner = windowNode(STAGE_TAG).frameOrigin().apply { translate(5, 5) }

            sendMouse(frame(), MouseEvent.MOUSE_PRESSED, center, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1)
            sendMouse(frame(), MouseEvent.MOUSE_DRAGGED, corner, InputEvent.BUTTON1_DOWN_MASK)
            sendMouse(frame(), MouseEvent.MOUSE_RELEASED, corner, 0, MouseEvent.BUTTON1)

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
            click(windowNode(LAYERED_TAG).unturnedAt(60, 20))

            assertEquals(0, presses, "the box hears no press")
        }

    /** A turned child its policy leaves unplaced takes no press; placed again, it takes presses. */
    @Test
    fun aTurnedChildLeftUnplacedStopsTakingPressesUntilPlacedAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var placed by mutableStateOf(true)
            var presses = 0
            setWindowContent {
                Box {
                    Layout(
                        modifier = SwingModifier.testTag(STAGE_TAG).preferredSize(300, 300),
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
            val center = windowNode(LAYERED_TAG).unturnedAt(50, 50)
            click(center)
            assertEquals(1, presses, "the placed turned child takes the press")

            placed = false
            awaitIdle()
            click(center)
            assertEquals(1, presses, "a child left unplaced takes no press")

            placed = true
            awaitIdle()
            click(center)
            assertEquals(2, presses, "the child placed again takes the press")
        }

    /** A right-click on a turned label that sets a popup menu of its own and has no listener opens that menu. */
    @Test
    fun aRightClickOnATurnedLabelWithItsOwnPopupMenuOpensIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val layered = windowNode(LAYERED_TAG)
            val over = layered.paintedAt(windowNode(INNER_TAG), 90.0, 20.0, turn(120, 40, rotation = 30f))

            sendMouse(frame(), MouseEvent.MOUSE_MOVED, over)
            sendMouse(frame(), MouseEvent.MOUSE_PRESSED, over, InputEvent.BUTTON3_DOWN_MASK, MouseEvent.BUTTON3)
            sendMouse(frame(), MouseEvent.MOUSE_RELEASED, over, 0, MouseEvent.BUTTON3)
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
            val canvas = windowNode(LAYERED_TAG)

            // Inside the layout bounds, past the box the layer clips to: the offset's, which stands 30 up and left.
            click(canvas.unturnedAt(85, 85))
            assertEquals(0, presses, "a press the clip cuts away must miss")

            click(canvas.unturnedAt(20, 20))
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
            var presses = 0
            var stageScope: BoxScope? = null
            setWindowContent { Box { Stage { stageScope = this } } }
            val stage = windowNode(STAGE_TAG)
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
                awaitIdle()
                val turn = turn(120, 30, rotation = 90f)

                // Turned, the box paints this point of its canvas 35 above its unturned box.
                click(windowNode(LAYERED_TAG).paintedAt(windowNode(INNER_TAG), 10.0, 15.0, turn))
                assertEquals(round + 1, presses, "composition $round: the press reaches the turned child")

                composition.dispose()
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
            val container = ArrayList<Int>()
            val sibling = ArrayList<Int>()
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
            // A turn about the center leaves the center where it is; the container's last third holds no child.
            val overTurned = windowNode(LAYERED_TAG).unturnedAt(20, 20)
            val overSibling = windowNode("other").unturnedAt(20, 20)
            val overContainer = windowNode(OUTER_TAG).unturnedAt(100, 20)

            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overContainer)
            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overTurned)
            sendMouse(frame(), MouseEvent.MOUSE_PRESSED, overTurned, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1)
            sendMouse(frame(), MouseEvent.MOUSE_DRAGGED, overContainer, InputEvent.BUTTON1_DOWN_MASK)
            sendMouse(frame(), MouseEvent.MOUSE_DRAGGED, overSibling, InputEvent.BUTTON1_DOWN_MASK)
            sendMouse(frame(), MouseEvent.MOUSE_RELEASED, overSibling, 0, MouseEvent.BUTTON1)
            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overContainer)
            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overTurned)

            val crossings = List(3) { listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED) }.flatten()
            assertEquals(crossings, container, "the container hears each crossing once")
            // AWT reads a drag handed to the press target at the target's own point, and may report the sibling
            // crossed again meanwhile.
            val alternating =
                sibling.isNotEmpty() && sibling.size % 2 == 0 &&
                    sibling.indices.all { sibling[it] == crossings[it % 2] }
            assertTrue(alternating, "the sibling hears an exit after each enter, but heard $sibling")
        }

    /** A child turned, straightened and turned again hears the pointer enter it again. */
    @Test
    fun aChildTurnedAgainHearsThePointerEnterAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
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
            val overTurned = windowNode(LAYERED_TAG).unturnedAt(20, 20)
            val overOther = windowNode("other").unturnedAt(20, 20)

            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overTurned)
            assertEquals(1, entered, "the pointer enters the turned child")

            rotation = 0f
            awaitIdle()
            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overOther)
            rotation = 45f
            awaitIdle()
            sendMouse(frame(), MouseEvent.MOUSE_MOVED, overTurned)

            assertEquals(2, entered, "the pointer enters the child turned again")
        }
}

@Composable
private inline fun BoxScope.Stage(
    modifier: SwingModifier = SwingModifier,
    crossinline content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.testTag(STAGE_TAG).preferredSize(300, 300),
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
        val outsets = (this as Decoratable).decoration.paintOutsets()
        return Rectangle(-outsets.left, -outsets.top, width, height)
    }

/** This component's origin in its window's coordinates. */
private fun Component.frameOrigin(): Point =
    SwingUtilities.convertPoint(this, 0, 0, SwingUtilities.getWindowAncestor(this))

/** Where ([x], [y]) of this decorated component's layout bounds stands unturned, in the window's coordinates. */
private fun Component.unturnedAt(
    x: Int,
    y: Int,
): Point = frameOrigin().apply { translate(x - paintBounds.x, y - paintBounds.y) }

/**
 * Where this layered component paints ([x], [y]) of [inner], its descendant or itself, through [turn], in the
 * window's coordinates.
 */
private fun Component.paintedAt(
    inner: Component,
    x: Double,
    y: Double,
    turn: AffineTransform,
): Point {
    val offset = SwingUtilities.convertPoint(inner, 0, 0, this)
    val painted = turn.transform(Point2D.Double(offset.x + x + paintBounds.x, offset.y + y + paintBounds.y), null)
    val origin = frameOrigin()
    return Point(
        floor(painted.x - paintBounds.x).toInt() + origin.x,
        floor(painted.y - paintBounds.y).toInt() + origin.y,
    )
}

/** Where this layered component paints the middle of [field]'s character [index], in the window's coordinates. */
private fun Component.charAt(
    field: JTextField,
    index: Int,
    turn: AffineTransform,
): Point {
    val start = field.modelToView2D(index)
    val end = field.modelToView2D(index + 1)
    return paintedAt(field, (start.x + end.x) / 2 - (end.x - start.x) / 4, start.centerY, turn)
}

internal fun ComposeSwingTest.windowNode(tag: String): JComponent =
    onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(tag).fetch<JComponent>()

internal fun ComposeSwingTest.frame(): JFrame = onWindowWithTitle(WINDOW_TITLE).fetch<JFrame>()

/** A primary-button press, release and click at [at], in the window's coordinates. */
internal suspend fun ComposeSwingTest.click(at: Point) {
    sendMouse(frame(), MouseEvent.MOUSE_PRESSED, at, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1)
    sendMouse(frame(), MouseEvent.MOUSE_RELEASED, at, 0, MouseEvent.BUTTON1)
    sendMouse(frame(), MouseEvent.MOUSE_CLICKED, at, 0, MouseEvent.BUTTON1)
}

private const val STAGE_TAG = "stage"
private const val LAYERED_TAG = "layered"
private const val INNER_TAG = "inner"
private const val OUTER_TAG = "outer"
