// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
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
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

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
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
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
                                        .mouseListener(onMousePressed = onPressed),
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
            verify(exactly = 0) { onPressed(any()) }

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
            verify(exactly = 1) { onPressed(any()) }
        }

    /** A child wider than its container is hit past the container's layout bounds, mirrored by a layer or not. */
    @Test
    fun aChildOverflowingItsContainerIsHitPastItMirroredOrNot() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
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
                                        .mouseListener(onMousePressed = onPressed),
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
            verify(exactly = 1) { onPressed(any()) }

            scaleX = -1f
            awaitIdle()
            stageInput.performMouseInput {
                click(past)
            }
            verify(exactly = 2) { onPressed(any()) }
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
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
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
                                        .mouseListener(onMousePressed = onPressed),
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
            verify(exactly = 0) { onPressed(any()) }

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
            verify(exactly = 1) { onPressed(any()) }
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
                                .placementLayer { rotationZ = 45f },
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
            val quarterTurn: PlacementLayerScope.() -> Unit = { rotationZ = 90f }
            setWindowContent {
                Box {
                    Stage {
                        Box(modifier = SwingModifier.testTag(LAYERED_TAG).size(200, 30).placementLayer(quarterTurn)) {
                            Row(modifier = SwingModifier.fillMaxSize().placementLayer(quarterTurn)) {
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
            val onFirstPress = mockk<(MouseEvent) -> Unit>(relaxed = true)
            val onOtherPress = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setUpTwoPressTargets(onFirstPress, onOtherPress)
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

            verifySequence {
                onFirstPress(match { it.button == MouseEvent.BUTTON1 })
                onFirstPress(match { it.button == MouseEvent.BUTTON3 })
            }
            verify(exactly = 0) { onOtherPress(any()) }
        }

    private suspend fun ComposeSwingTest.setUpTwoPressTargets(
        onFirstPress: (MouseEvent) -> Unit,
        onOtherPress: (MouseEvent) -> Unit,
    ) {
        setWindowContent {
            Box {
                Stage {
                    Row(
                        modifier =
                            SwingModifier.testTag(LAYERED_TAG).size(200, 30).placementLayer { rotationZ = 90f },
                    ) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag(INNER_TAG)
                                    .size(100, 30)
                                    .mouseListener(onMousePressed = onFirstPress),
                        ) {}
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("other")
                                    .size(100, 30)
                                    .mouseListener(onMousePressed = onOtherPress),
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

private val DoubleScale: PlacementLayerScope.() -> Unit = {
    scaleX = 2f
    scaleY = 2f
}

private const val OUTER_TAG = "outer"
