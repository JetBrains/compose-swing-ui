// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.click
import org.jetbrains.compose.swing.test.interaction.performMouseInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuListener
import kotlin.test.Test
import kotlin.test.assertTrue

/** Capture, wheel, and target lifetime behavior of the transformed-child glass pane. */
class PlacementHitTestInputTest {
    @Test
    fun aWheelTurnOnATurnedChildReachesItsPaintedPoint() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onWheel = mockk<(MouseWheelEvent) -> Unit>(relaxed = true)
            val wheel = slot<MouseWheelEvent>()
            every { onWheel(capture(wheel)) } just Runs
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag("layered").size(120, 40).placementLayer { rotationZ = 90f },
                        ) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag("inner")
                                        .fillMaxSize()
                                        .mouseWheelListener(onWheel),
                            ) {}
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag("layered").fetch<JComponent>()
            val at =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag("inner").fetch<JComponent>(),
                    10.0,
                    20.0,
                    turn(120, 40, rotation = 90f),
                )

            stageInput.performMouseInput {
                moveTo(at)
                scroll(1)
            }
            verify(exactly = 1) { onWheel(any()) }
            assertTrue(wheel.captured.point.distance(Point(10, 20)) <= 1.5, "the wheel lands at the unturned point")
        }

    @Test
    fun aWheelTurnThroughNestedGlassPanesReachesTheChildOnce() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onWheel = mockk<(MouseWheelEvent) -> Unit>(relaxed = true)
            val eighthTurn: PlacementLayerScope.() -> Unit = { rotationZ = 45f }
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier = SwingModifier.testTag("layered").size(100, 100).placementLayer(eighthTurn),
                        ) {
                            Box(modifier = SwingModifier.fillMaxSize().placementLayer(eighthTurn)) {
                                Canvas(
                                    modifier =
                                        SwingModifier
                                            .testTag("inner")
                                            .fillMaxSize()
                                            .mouseWheelListener(onWheel),
                                ) {}
                            }
                        }
                    }
                }
            }

            val at = window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)
            stageInput.performMouseInput {
                moveTo(at)
                scroll(1)
            }

            verify(exactly = 1) { onWheel(any()) }
        }

    @Test
    fun aReparentedHoverAndPressTargetReceivesNoMoreEvents() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val heard = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setUpReparentedTarget(heard)
            val target = window.onNodeWithTag("inner").fetch<JComponent>()
            val originalParent = target.parent
            val otherParent = JPanel()
            val at = window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)

            stageInput.performMouseInput { moveTo(at) }
            stageInput.performMouseInput {
                press(MouseEvent.BUTTON1)
            }
            verifySequence {
                heard(match { it.id == MouseEvent.MOUSE_ENTERED })
                heard(match { it.id == MouseEvent.MOUSE_PRESSED })
            }

            originalParent.remove(target)
            otherParent.add(target)
            try {
                stageInput.performMouseInput { moveTo(at) }
                stageInput.performMouseInput {
                    release(MouseEvent.BUTTON1)
                }
                stageInput.performMouseInput { moveTo(at) }
                // A component outside the original child gets no drag, release or exit.
                verifySequence {
                    heard(match { it.id == MouseEvent.MOUSE_ENTERED })
                    heard(match { it.id == MouseEvent.MOUSE_PRESSED })
                }
            } finally {
                otherParent.remove(target)
                originalParent.add(target)
            }
        }

    private suspend fun ComposeSwingTest.setUpReparentedTarget(heard: (MouseEvent) -> Unit) {
        setWindowContent {
            Box {
                Stage {
                    Box(
                        modifier = SwingModifier.testTag("layered").size(100, 100).placementLayer { rotationZ = 45f },
                    ) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("inner")
                                    .fillMaxSize()
                                    .mouseListener(
                                        onMouseEntered = heard,
                                        onMouseExited = heard,
                                        onMousePressed = heard,
                                        onMouseReleased = heard,
                                    ).mouseMotionListener(onMouseDragged = heard),
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
            val heard = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Stage {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("layered")
                                    .size(100, 100)
                                    .placementLayer { rotationZ = 45f }
                                    .mouseListener(
                                        onMousePressed = heard,
                                        onMouseReleased = heard,
                                    ).mouseMotionListener(onMouseDragged = heard),
                        ) {}
                    }
                }
            }
            // The eighth turn keeps the center where it stands unturned; the stage's corner holds no child.
            val center = window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)
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

            // The press, the drag and the release reach the child pressed.
            verifySequence {
                heard(match { it.id == MouseEvent.MOUSE_PRESSED })
                heard(match { it.id == MouseEvent.MOUSE_DRAGGED })
                heard(match { it.id == MouseEvent.MOUSE_RELEASED })
            }
        }

    /** A press on a label inside a turned box goes to the label, which takes no mouse events, not to the box. */
    @Test
    fun aPressOnAPlainLabelInsideATurnedBoxDoesNotReachTheBoxesListener() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("layered")
                                    .size(120, 40)
                                    .placementLayer { rotationZ = 45f }
                                    .mouseListener(onMousePressed = onPressed),
                        ) {
                            Label("Go", SwingModifier.fillMaxSize())
                        }
                    }
                }
            }
            // A turn about the center leaves the center where it is.
            stageInput.performMouseInput {
                click(window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 60, 20))
            }

            verify(exactly = 0) { onPressed(any()) }
        }

    /** A turned child its policy leaves unplaced takes no press; placed again, it takes presses. */
    @Test
    fun aTurnedChildLeftUnplacedStopsTakingPressesUntilPlacedAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            var placed by mutableStateOf(true)
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
            setWindowContent {
                Box {
                    Layout(
                        modifier = SwingModifier.testTag(STAGE_TAG).styleable { preferredSize(300, 300) },
                        content = {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag("layered")
                                        .size(100, 100)
                                        .placementLayer { rotationZ = 45f }
                                        .mouseListener(onMousePressed = onPressed),
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
            val center = window.onNodeWithTag("layered").fetch<JComponent>().unturnedAt(stageInput.fetch(), 50, 50)
            stageInput.performMouseInput { click(center) }
            verify(exactly = 1) { onPressed(any()) }

            placed = false
            awaitIdle()
            stageInput.performMouseInput { click(center) }
            verify(exactly = 1) { onPressed(any()) }

            placed = true
            awaitIdle()
            stageInput.performMouseInput { click(center) }
            verify(exactly = 2) { onPressed(any()) }
        }

    /** A right-click on a turned label that sets a popup menu of its own and has no listener opens that menu. */
    @Test
    fun aRightClickOnATurnedLabelWithItsOwnPopupMenuOpensIt() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val popupListener = mockk<PopupMenuListener>(relaxed = true)
            val menu =
                JPopupMenu().apply {
                    add("Copy")
                    addPopupMenuListener(popupListener)
                }
            setWindowContent {
                Box {
                    Stage {
                        Box(
                            modifier =
                                SwingModifier.testTag("layered").size(120, 40).placementLayer { rotationZ = 30f },
                        ) {
                            SwingNode(
                                factory = { JLabel("Go").apply { componentPopupMenu = menu } },
                                modifier = SwingModifier.testTag("inner").fillMaxSize(),
                            )
                        }
                    }
                }
            }
            val layered = window.onNodeWithTag("layered").fetch<JComponent>()
            val over =
                layered.paintedAt(
                    stageInput.fetch(),
                    window.onNodeWithTag("inner").fetch<JComponent>(),
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

            verify(exactly = 1) { popupListener.popupMenuWillBecomeVisible(any()) }
        }
}
