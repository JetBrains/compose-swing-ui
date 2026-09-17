/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.node.ModelReadsTest in AndroidX's ui; see this module's
 * META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.decorated
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [Layout] whose policy reads snapshot state: a read made while measuring, placing or painting is
 * observed, and its change invalidates only that stage.
 */
class LayoutObservationTest {
    /**
     * A policy that reads a `State` directly inside `measure` - with no other recomposable input changing
     * - is observed, and the container is measured again once that state changes.
     *
     * [aMeasureReadInvalidatesTheContainerAndItsAncestorsWithoutRepainting] pins the same observation at
     * the instant the state changes, in a real window, before anything queued runs.
     */
    @Test
    fun aPolicyThatReadsStateWhileMeasuringIsMeasuredAgainWhenThatStateChanges() =
        runComposeSwingTest {
            var gap by mutableStateOf(0)
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    measurePolicy = stackedRows { gap },
                    modifier = containerModifier(200, 300),
                )
            }

            assertEquals(columnRows(0, CHILD_HEIGHT), childBounds(), "the container starts ungapped")

            gap = CHILD_HEIGHT
            awaitIdle()

            assertEquals(
                columnRows(0, 2 * CHILD_HEIGHT),
                childBounds(),
                "the container must be measured again once the state its policy read changed, though " +
                    "the policy instance itself never changed",
            )
        }

    /**
     * A policy may write state during `measure`, the way an animation's measure policy reports the
     * extent it settled on. The write invalidates the read it started from and costs one more pass,
     * which computes and writes the same value and stops - rather than looping until the harness's
     * frame ceiling throws.
     */
    @Test
    fun aPolicyThatWritesStateDuringMeasureConvergesInsteadOfLooping() =
        runComposeSwingTest {
            val reportedHeight = mutableStateOf(-1)
            setContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    measurePolicy = stackedRowsReportingHeight(reportedHeight),
                    modifier = containerModifier(200, 300),
                )
            }

            awaitIdle()

            assertEquals(
                2 * CHILD_HEIGHT,
                reportedHeight.value,
                "the write must settle on the height the policy actually measured, converging rather " +
                    "than looping",
            )
        }

    /**
     * A state read only while placing children moves them again on its own, with no other recomposable
     * input changing.
     *
     * [aPlacementReadMovesTheChildrenWithoutInvalidatingAnAncestor] pins the same observation at the
     * instant the state changes, in a real window, before anything queued runs.
     */
    @Test
    fun aPolicyThatReadsStateOnlyWhilePlacingMovesChildrenWhenThatStateChanges() =
        runComposeSwingTest {
            var offset by mutableStateOf(0)
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = stackedRowsPlacingAtOffset { offset },
                    modifier = containerModifier(200, 300),
                )
            }

            assertEquals(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                childBounds().single(),
                "the child starts unoffset",
            )

            offset = CHILD_HEIGHT
            awaitIdle()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                childBounds().single(),
                "the child must move to the new placement-only offset, though the policy instance itself " +
                    "never changed",
            )
        }

    /**
     * A policy writing, from its placement block, a state its measure block read measures the container again, where
     * the placement runs again for a state only that block read. Ported from androidx's
     * `ModelReadsTest.remeasureRequestForTheNodeBeingLaidOut`.
     */
    @Test
    fun aMeasureStateWrittenFromThePlacementBlockMeasuresTheContainerAgain() =
        runComposeSwingTest {
            val measured = mutableIntStateOf(0)
            val placed = mutableIntStateOf(0)
            var readWhileMeasuring = -1
            setContent {
                Layout(
                    measurePolicy = { _, _ ->
                        readWhileMeasuring = measured.intValue
                        layout(CHILD_WIDTH, CHILD_HEIGHT) {
                            if (placed.intValue != 0 && measured.intValue == 0) {
                                measured.intValue = 1
                                Snapshot.sendApplyNotifications()
                            }
                        }
                    },
                )
            }

            placed.intValue = 1
            awaitIdle()

            assertEquals(1, readWhileMeasuring, "the measure block must run again and read the written value")
        }

    /**
     * A policy writing, from the placement block of a layout pass, a state its measure block read measures the
     * container again, though that pass marks the container valid after the write.
     */
    @Test
    fun aMeasureStateWrittenFromThePlacementBlockOfALayoutPassMeasuresTheContainerAgain() =
        runComposeSwingTest {
            val measured = mutableIntStateOf(0)
            val trigger = mutableIntStateOf(0)
            var written = false
            var readWhileMeasuring = -1
            setContent {
                Layout(
                    measurePolicy = { _, _ ->
                        readWhileMeasuring = measured.intValue
                        val writes = trigger.intValue != 0
                        layout(CHILD_WIDTH, CHILD_HEIGHT) {
                            if (writes && !written) {
                                written = true
                                measured.intValue = 1
                                Snapshot.sendApplyNotifications()
                            }
                        }
                    },
                )
            }

            trigger.intValue = 1
            awaitIdle()

            assertEquals(1, readWhileMeasuring, "the measure block must run again and read the written value")
        }

    /**
     * A policy writing, from its placement block, a state that same block read places the children again. Ported from
     * androidx's `ModelReadsTest.relayoutRequestForTheNodeBeingLaidOut`.
     */
    @Test
    fun aPlacementStateWrittenFromThePlacementBlockPlacesTheChildrenAgain() =
        runComposeSwingTest {
            val offset = mutableIntStateOf(0)
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints.copy(minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (offset.intValue == 1) {
                                offset.intValue = CHILD_HEIGHT
                                Snapshot.sendApplyNotifications()
                            }
                            placeable.place(0, offset.intValue)
                        }
                    },
                    modifier = containerModifier(CHILD_WIDTH, 2 * CHILD_HEIGHT),
                )
            }

            offset.intValue = 1
            awaitIdle()

            assertEquals(columnRows(CHILD_HEIGHT), childBounds(), "the children must be placed by the written value")
        }

    /**
     * A measure read invalidates the container and its ancestors, and asks for no repaint: the relayout
     * that follows repaints whatever moved. Read at once, with no harness pass in between, in a showing
     * window, where validity and dirty regions are real.
     */
    @Test
    fun aMeasureReadInvalidatesTheContainerAndItsAncestorsWithoutRepainting() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var gap by mutableIntStateOf(0)
            setWindowContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    measurePolicy = stackedRows { gap },
                    modifier = containerModifier(200, 300),
                )
            }
            val container = windowContainer()
            assertTrue(container.isValidUpToTheValidateRoot(), "the realized container must start valid")

            gap = CHILD_HEIGHT
            Snapshot.sendApplyNotifications()

            assertTrue(
                container.withAncestors().none { it.isValid },
                "a changed measure read must invalidate the container and every ancestor",
            )
            assertTrue(container.dirtyRegion().isEmpty, "a changed measure read must not repaint by itself")

            awaitIdle()
            assertEquals(
                columnRows(0, 2 * CHILD_HEIGHT),
                container.childrenInDeclarationOrder().map { it.bounds },
                "the relayout must place the children at the new gap",
            )
        }

    /**
     * A placement read places the children again and nothing else: the container and its ancestors, a
     * policy container among them, stay valid, the policy is not measured again, and the only repaint is
     * the one moving the child asks for.
     */
    @Test
    fun aPlacementReadMovesTheChildrenWithoutInvalidatingAnAncestor() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var offset by mutableIntStateOf(0)
            var measures = 0
            setWindowContent {
                // A parent policy measures the container, so its layout pass settles on that measure.
                Box {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = stackedRowsPlacingAtOffset(onMeasure = { measures++ }) { offset },
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = windowContainer()
            val child = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).onChild().fetch<Component>()
            assertEquals(Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT), child.bounds)
            assertTrue(container.isValidUpToTheValidateRoot(), "the realized container must start valid")
            val measuresBefore = measures

            offset = CHILD_HEIGHT
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, CHILD_HEIGHT),
                child.bounds,
                "a changed placement read must place the children again, with no harness pass run",
            )
            assertTrue(
                container.isValidUpToTheValidateRoot(),
                "a placement read must invalidate neither the container nor an ancestor",
            )
            assertEquals(measuresBefore, measures, "a placement read must not measure the policy again")
            assertEquals(
                Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                container.dirtyRegion(),
                "the container must be asked to repaint only the area the moved child left",
            )
        }

    /**
     * A placement read after a pass that resized the child places the result that pass settled on: the
     * policy is not run again at the container's own extent, so a share worked out from the parent's wider
     * offer does not shrink.
     */
    @Test
    fun aPlacementReadAfterAChildResizeKeepsTheSettledShare() =
        runComposeSwingTest {
            var divisor by mutableIntStateOf(2)
            var offset by mutableIntStateOf(0)
            var measures = 0
            setContent {
                Box(modifier = SwingModifier.preferredSize(200, 300)) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = shareOfTheOffer({ measures++ }, { divisor }) { offset },
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            divisor = 4
            awaitIdle()
            val child = onNodeWithTag(CONTAINER_TAG).onChild().fetch<Component>()
            assertEquals(Rectangle(0, 0, 50, CHILD_HEIGHT), child.bounds)
            val measuresBefore = measures

            offset = CHILD_HEIGHT
            awaitIdle()

            assertEquals(
                Rectangle(0, CHILD_HEIGHT, 50, CHILD_HEIGHT),
                child.bounds,
                "a placement read must move the child at the share the parent's offer gave it",
            )
            assertEquals(measuresBefore, measures, "a placement read must not measure the policy again")
        }

    /**
     * A read made while the container paints its own decoration repaints the container alone: nothing is
     * invalidated, and the next paint shows the new value.
     */
    @Test
    fun aPaintReadRepaintsTheContainerWithoutLayingItOutAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var color by mutableStateOf(Color.RED)
            setWindowContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = stackedRows(),
                    modifier = decorated { containerModifier(200, 300).background(brush = { _, _ -> color }) },
                )
            }
            val container = windowContainer()
            val node = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG)
            assertEquals(Color.RED.rgb, node.captureToImage().getRGB(container.width - 1, container.height - 1))
            assertTrue(container.isValidUpToTheValidateRoot(), "the realized container must start valid")

            color = Color.BLUE
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, 0, container.width, container.height),
                container.dirtyRegion(),
                "a changed paint read must repaint the container",
            )
            assertTrue(container.isValidUpToTheValidateRoot(), "a paint read must not invalidate anything")

            awaitIdle()
            assertEquals(Color.BLUE.rgb, node.captureToImage().getRGB(container.width - 1, container.height - 1))
        }

    /**
     * A plain component reading state while it paints is not observed through the container that paints it:
     * a child's paint is the child's own to observe, which a `Canvas` does.
     */
    @Test
    fun aPlainChildReadingStateWhilePaintingIsNotObserved() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var color by mutableStateOf(Color.RED)
            setWindowContent {
                Layout(
                    content = {
                        SwingNode(
                            factory = { PaintReadingChild { color } },
                            modifier = SwingModifier.preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
                        )
                    },
                    measurePolicy = stackedRows(),
                    modifier = containerModifier(200, 300),
                )
            }
            val container = windowContainer()
            container.paintImmediately(0, 0, container.width, container.height)

            color = Color.BLUE
            Snapshot.sendApplyNotifications()

            assertTrue(container.dirtyRegion().isEmpty, "the container must not repaint for its child's paint read")
        }

    /**
     * Painting one child alone runs the container's paint clipped to that child, which paints no other child.
     * The reads the skipped children and the container's own decoration make stay observed. The container
     * carries a decoration so that it is the painting origin: without one, painting the child would not run
     * the container's paint at all.
     */
    @Test
    fun aPaintOfOneChildKeepsTheOtherPaintReadsObserved() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var containerColor by mutableStateOf(Color.RED)
            var nestedColor by mutableStateOf(Color.RED)
            var canvasColor by mutableStateOf(Color.RED)
            val unrelated = mutableIntStateOf(0)
            setWindowContent {
                Layout(
                    content = {
                        SizedChild(0, SwingModifier.testTag("painted"))
                        Layout(
                            measurePolicy = { _, _ -> layout(CHILD_WIDTH, CHILD_HEIGHT) {} },
                            modifier =
                                decorated {
                                    SwingModifier.testTag("nested").background(brush = { _, _ -> nestedColor })
                                },
                        )
                        Canvas(modifier = SwingModifier.testTag("canvas").preferredSize(CHILD_WIDTH, CHILD_HEIGHT)) {
                            graphics.color = canvasColor
                            graphics.fillRect(0, 0, width, height)
                        }
                    },
                    measurePolicy = stackedRows(),
                    modifier =
                        decorated { containerModifier(200, 300).background(brush = { _, _ -> containerColor }) },
                )
            }
            val window = onWindowWithTitle(WINDOW_TITLE)
            val container = windowContainer()
            val nested = window.onNodeWithTag("nested").fetch<JComponent>()
            val canvas = window.onNodeWithTag("canvas").fetch<JComponent>()
            container.paintImmediately(0, 0, container.width, container.height)
            // Moves the global snapshot forward, so the next observeReads replaces reads recorded under an
            // earlier snapshot even when nothing else applied one in between.
            unrelated.intValue = 1
            Snapshot.sendApplyNotifications()

            val painted = window.onNodeWithTag("painted").fetch<JComponent>()
            painted.paintImmediately(0, 0, painted.width, painted.height)
            containerColor = Color.BLUE
            nestedColor = Color.BLUE
            canvasColor = Color.BLUE
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, 0, container.width, container.height),
                container.dirtyRegion(),
                "the container's own decoration read must stay observed",
            )
            assertEquals(
                Rectangle(0, 0, nested.width, nested.height),
                nested.dirtyRegion(),
                "a skipped child's own decoration read must stay observed",
            )
            assertEquals(
                Rectangle(0, 0, canvas.width, canvas.height),
                canvas.dirtyRegion(),
                "a skipped Canvas's draw read must stay observed",
            )
        }

    /**
     * A modifier whose key changes detaches and attaches every component node on the container, the layout's
     * observation node included, which drops the reads each cached answer recorded. The observation node
     * revalidates the container when it attaches again, so every cached answer is recorded again.
     */
    @Test
    fun aMeasureReadIsObservedAgainAfterTheModifierNodesAttachAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var gap by mutableIntStateOf(0)
            var generation by mutableIntStateOf(0)
            // One policy instance, so the recomposition the key change causes does not revalidate by itself.
            val policy = stackedRows { gap }
            setWindowContent {
                Layout(
                    content = {
                        SizedChild(0)
                        SizedChild(1)
                    },
                    measurePolicy = policy,
                    modifier = SwingModifier.testTag(CONTAINER_TAG).key(generation),
                )
            }
            generation = 1
            awaitIdle()
            val container = windowContainer()
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid once attached again")

            gap = CHILD_HEIGHT
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "a measure read made after the nodes attached again must be observed")
        }

    /**
     * A modifier whose key changes detaches and attaches every component node on the container, the layout's
     * observation node included, which drops the read the container's own paint recorded. The decoration
     * attaching again repaints the container, which records that read again; see
     * [LayoutNodeLifecycleTest.aKeyChangeOnAnUndecoratedLayoutPaintsNothingAgain].
     */
    @Test
    fun aPaintReadIsObservedAgainAfterTheModifierNodesAttachAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var color by mutableStateOf(Color.RED)
            var generation by mutableIntStateOf(0)
            setWindowContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = stackedRows(),
                    modifier =
                        decorated {
                            SwingModifier.testTag(CONTAINER_TAG).key(generation).background(brush = { _, _ -> color })
                        },
                )
            }
            generation = 1
            awaitIdle()
            val container = windowContainer()
            assertTrue(container.dirtyRegion().isEmpty, "every requested paint must have run")

            color = Color.BLUE
            Snapshot.sendApplyNotifications()

            assertEquals(
                Rectangle(0, 0, container.width, container.height),
                container.dirtyRegion(),
                "a paint read made after the nodes attached again must be observed",
            )
        }

    private companion object {
        /**
         * Stacks its children ungapped and writes the content height they actually occupied into
         * [target] - never the extent [layout] reports, which a fixed container size coerces to its own
         * bounds regardless of what the children measured to.
         */
        fun stackedRowsReportingHeight(target: MutableState<Int>): MeasurePolicy =
            MeasurePolicy { measurables, constraints ->
                var remainingHeight = constraints.maxHeight
                val placeables =
                    measurables.map { measurable ->
                        val measureConstraints =
                            Constraints(maxWidth = constraints.maxWidth, maxHeight = remainingHeight)
                        val measured = measurable.measure(measureConstraints)
                        remainingHeight = (remainingHeight - measured.height).coerceAtLeast(0)
                        measured
                    }
                val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
                val contentHeight = placeables.sumOf { it.height }
                target.value = contentHeight
                layout(width, constraints.constrainHeight(contentHeight)) {
                    var y = 0
                    for (placeable in placeables) {
                        placeable.place(0, y)
                        y += placeable.height
                    }
                }
            }

        /**
         * One child, measured at its own preferred size and placed at a vertical [offset] read only here, in
         * a container reporting twice the child's size. [onMeasure] runs on every measure.
         */
        fun stackedRowsPlacingAtOffset(
            onMeasure: () -> Unit = {},
            offset: () -> Int,
        ): MeasurePolicy =
            MeasurePolicy { measurables, constraints ->
                onMeasure()
                val ceiling = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
                val placeable = measurables.single().measure(ceiling)
                layout(2 * placeable.width, 2 * placeable.height) {
                    placeable.place(0, offset())
                }
            }

        /**
         * One child, measured at the offer's maximum width divided by [divisor] and placed at a vertical [offset]
         * read only here, in a container as large as the child. [onMeasure] runs on every measure.
         */
        fun shareOfTheOffer(
            onMeasure: () -> Unit,
            divisor: () -> Int,
            offset: () -> Int,
        ): MeasurePolicy =
            MeasurePolicy { measurables, constraints ->
                onMeasure()
                val share = Constraints.fixed(constraints.maxWidth / divisor(), CHILD_HEIGHT)
                val placeable = measurables.single().measure(share)
                layout(placeable.width, placeable.height) {
                    placeable.place(0, offset())
                }
            }
    }
}

/** A child painting itself in the color [color] reads while it paints. */
private class PaintReadingChild(
    private val color: () -> Color,
) : JComponent() {
    override fun paintComponent(g: Graphics) {
        g.color = color()
        g.fillRect(0, 0, width, height)
    }
}
