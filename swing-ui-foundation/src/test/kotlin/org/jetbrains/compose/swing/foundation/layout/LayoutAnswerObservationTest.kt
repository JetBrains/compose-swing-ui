package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Each answer a [Layout] caches keeps the reads behind it observed while another answer is computed again:
 * the preferred and minimum sizes, the measure the parent lays out with, the extent the container settles
 * on, and its placement.
 */
class LayoutAnswerObservationTest {
    /**
     * Swing caches the preferred and the minimum size apart, so each keeps its own reads: asking for the
     * minimum size for the first time, after the preferred size was answered under an earlier snapshot,
     * leaves the preferred-size read observed.
     */
    @Test
    fun aPreferredSizeReadStaysObservedAfterTheMinimumSizeIsAsked() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val preferredRead = mutableIntStateOf(0)
            val minimumRead = mutableIntStateOf(0)
            val unrelated = mutableIntStateOf(0)
            val asked = QuestionCounts()
            val policy =
                asked.questionReadingPolicy(
                    preferred = { preferredRead.intValue },
                    minimum = { minimumRead.intValue },
                )
            setWindowContent {
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) }) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = policy,
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = windowContainer()
            assertTrue(asked.preferredAsked > 0, "the parent must have asked for the preferred size")
            assertEquals(0, asked.minimumAsked, "nothing may have asked for the minimum size yet")

            // Moves the global snapshot forward, so the next observeReads replaces reads recorded under an
            // earlier snapshot even when nothing else applied one in between.
            unrelated.intValue = 1
            Snapshot.sendApplyNotifications()
            container.minimumSize
            assertEquals(1, asked.minimumAsked)
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

            preferredRead.intValue = 1
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "the preferred-size read must still be observed")
        }

    /**
     * The preferred size stays cached while the parent measures the container again, so the preferred-size
     * read stays observed after that measure.
     */
    @Test
    fun aPreferredSizeReadStaysObservedAfterTheParentMeasuresAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val preferredRead = mutableIntStateOf(0)
            val parentPass = mutableIntStateOf(0)
            val asked = QuestionCounts()
            val policy = asked.questionReadingPolicy(preferred = { preferredRead.intValue })
            setWindowContent {
                Layout(measurePolicy = measuringParent(read = { parentPass.intValue })) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = policy,
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = windowContainer()
            container.preferredSize
            val preferredAsked = asked.preferredAsked
            val measuredAsked = asked.looseAsked
            assertTrue(preferredAsked > 0, "the preferred size must have been answered")

            parentPass.intValue = 1
            awaitIdle()
            assertTrue(asked.looseAsked > measuredAsked, "the parent must have measured the container again")
            assertEquals(preferredAsked, asked.preferredAsked, "the preferred size must have stayed cached")
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

            preferredRead.intValue = 1
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "the preferred-size read must still be observed")
        }

    /**
     * The minimum size stays cached while the parent measures the container again, so the minimum-size read
     * stays observed after that measure.
     */
    @Test
    fun aMinimumSizeReadStaysObservedAfterTheParentMeasuresAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val minimumRead = mutableIntStateOf(0)
            val parentPass = mutableIntStateOf(0)
            val asked = QuestionCounts()
            val policy = asked.questionReadingPolicy(minimum = { minimumRead.intValue })
            setWindowContent {
                Layout(measurePolicy = measuringParent(read = { parentPass.intValue })) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = policy,
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = windowContainer()
            container.minimumSize
            val minimumAsked = asked.minimumAsked
            val measuredAsked = asked.looseAsked
            assertTrue(minimumAsked > 0, "the minimum size must have been answered")

            parentPass.intValue = 1
            awaitIdle()
            assertTrue(asked.looseAsked > measuredAsked, "the parent must have measured the container again")
            assertEquals(minimumAsked, asked.minimumAsked, "the minimum size must have stayed cached")
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

            minimumRead.intValue = 1
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "the minimum-size read must still be observed")
        }

    /**
     * What the parent measured stays in use while this container settles on its bounds again, so the read
     * behind the parent's measure stays observed after a later settle that reads something else.
     *
     * A layout modifier on the container measures it a second time at a size it is not placed at, so a
     * layout pass settles the container on its bounds afresh.
     */
    @Test
    fun aMeasureReadStaysObservedAfterTheContainerSettlesAgain() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val measureRead = mutableIntStateOf(0)
            val settleRead = mutableIntStateOf(0)
            val asked = QuestionCounts()
            val policy =
                asked.questionReadingPolicy(
                    loose = { measureRead.intValue },
                    settle = { settleRead.intValue },
                )
            setWindowContent {
                Layout(measurePolicy = measuringParent()) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = policy,
                        modifier = SwingModifier.testTag(CONTAINER_TAG) then ProbeElement(probe = { true }),
                    )
                }
            }
            val container = windowContainer()
            val measuredAsked = asked.looseAsked
            val settledAsked = asked.settleAsked

            container.doLayout()
            assertTrue(asked.settleAsked > settledAsked, "the layout pass must have settled afresh")
            assertEquals(measuredAsked, asked.looseAsked, "the parent must not have measured the container again")
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

            measureRead.intValue = 1
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "the read behind the parent's measure must still be observed")
        }

    /**
     * A placement replay places the last result again without settling, so the read behind an earlier settle
     * stays observed.
     *
     * A layout modifier on the container first measures it again at a size it is not placed at, so the
     * container settles on its bounds afresh; it then stops.
     */
    @Test
    fun aSettleReadStaysObservedAfterAPlacementReplay() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val settleRead = mutableIntStateOf(0)
            var probe by mutableStateOf(true)
            var offset by mutableIntStateOf(0)
            val asked = QuestionCounts()
            val policy = asked.questionReadingPolicy(settle = { settleRead.intValue }, place = { offset })
            setWindowContent {
                Layout(measurePolicy = measuringParent()) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = policy,
                        modifier = SwingModifier.testTag(CONTAINER_TAG) then ProbeElement(probe = { probe }),
                    )
                }
            }
            val container = windowContainer()
            assertTrue(asked.settleAsked > 0, "the container must have settled afresh")
            probe = false
            awaitIdle()
            val settledAsked = asked.settleAsked
            val placedAsked = asked.placeAsked

            offset = 1
            Snapshot.sendApplyNotifications()
            assertTrue(asked.placeAsked > placedAsked, "the placement must have replayed")
            awaitIdle()
            assertEquals(settledAsked, asked.settleAsked, "the replay must not settle again")
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

            settleRead.intValue = 1
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "the settle read must still be observed")
        }

    /** A placement replayed under a later snapshot leaves the read behind the preferred size observed. */
    @Test
    fun aPreferredSizeReadStaysObservedAfterAPlacementReplay() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val preferredRead = mutableIntStateOf(0)
            var offset by mutableIntStateOf(0)
            val asked = QuestionCounts()
            val policy = asked.questionReadingPolicy(preferred = { preferredRead.intValue }, place = { offset })
            setWindowContent {
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) }) {
                    Layout(
                        content = { SizedChild(0) },
                        measurePolicy = policy,
                        modifier = SwingModifier.testTag(CONTAINER_TAG),
                    )
                }
            }
            val container = windowContainer()
            val placedAsked = asked.placeAsked

            offset = 1
            Snapshot.sendApplyNotifications()
            assertTrue(asked.placeAsked > placedAsked, "the placement must have replayed")
            awaitIdle()
            assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

            preferredRead.intValue = 1
            Snapshot.sendApplyNotifications()

            assertFalse(container.isValid, "the preferred-size read must still be observed")
        }

    /**
     * A placement replay that resizes a child lays that child out, as a validation would: a row the replay
     * widens gives its weighted child the new width at once.
     */
    @Test
    fun aPlacementReplayThatResizesAChildLaysThatChildOut() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var wide by mutableStateOf(false)
            setWindowContent {
                Layout(
                    content = {
                        Row {
                            SizedChild(0, SwingModifier.weight(1f))
                        }
                    },
                    measurePolicy = { measurables, constraints ->
                        val row = measurables.single()
                        layout(constraints.minWidth, constraints.minHeight) {
                            val width = if (wide) 60 else 30
                            row.measure(Constraints(width, width, CHILD_HEIGHT, CHILD_HEIGHT)).place(0, 0)
                        }
                    },
                    modifier = containerModifier(200, 300),
                )
            }
            assertEquals(listOf(Rectangle(0, 0, 30, CHILD_HEIGHT)), windowRowChildBounds())

            wide = true
            Snapshot.sendApplyNotifications()

            assertEquals(
                listOf(Rectangle(0, 0, 60, CHILD_HEIGHT)),
                windowRowChildBounds(),
                "the widened row must lay its weighted child out at the new width",
            )
            assertTrue(windowContainer().isValidUpToTheValidateRoot(), "the replay must leave the tree valid")
        }
}

/** How often each question [questionReadingPolicy] answers has been asked. */
private class QuestionCounts {
    var preferredAsked = 0
    var minimumAsked = 0
    var looseAsked = 0
    var settleAsked = 0
    var placeAsked = 0
}

/**
 * One child at the fixture child's size, making a different read for each question and counting it in this
 * receiver: [preferred] and [minimum] for the two intrinsic queries, [loose] for a measure under an offer that
 * is not one exact size, [settle] for a measure under one exact size larger than a single pixel, and [place]
 * in the placement block.
 */
private fun QuestionCounts.questionReadingPolicy(
    preferred: () -> Unit = {},
    minimum: () -> Unit = {},
    loose: () -> Unit = {},
    settle: () -> Unit = {},
    place: () -> Unit = {},
): MeasurePolicy =
    object : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            if (!constraints.hasFixedWidth || !constraints.hasFixedHeight) {
                looseAsked++
                loose()
            } else if (constraints.maxWidth > 1) {
                settleAsked++
                settle()
            }
            val placeable = measurables.single().measure(Constraints(maxWidth = CHILD_WIDTH, maxHeight = CHILD_HEIGHT))
            return layout(constraints.constrainWidth(CHILD_WIDTH), constraints.constrainHeight(CHILD_HEIGHT)) {
                placeAsked++
                place()
                placeable.place(0, 0)
            }
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            preferredAsked++
            preferred()
            return CHILD_WIDTH
        }

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = CHILD_HEIGHT

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            minimumAsked++
            minimum()
            return 0
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = 0
    }

/**
 * Measures its one child under a ceiling of its own extent and places it at that measure. [read] runs on every
 * measure.
 */
private fun measuringParent(read: () -> Unit = {}): MeasurePolicy =
    MeasurePolicy { measurables, constraints ->
        read()
        val child = measurables.single()
        val placeable = child.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        layout(constraints.constrainWidth(placeable.width), constraints.constrainHeight(placeable.height)) {
            placeable.place(0, 0)
        }
    }

/**
 * Places its child at the child's first measure. Where [probe] is `true`, it measures the child again at a single
 * pixel afterwards, which the child then holds as its last measure without being placed at it. Its intrinsic
 * functions ask the child directly, so a size query runs no probe.
 */
private data class ProbeElement(
    private val probe: () -> Boolean,
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        if (probe()) measurable.measure(Constraints(1, 1, 1, 1))
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/** The bounds the row nested in the container under test assigned its children. */
private fun ComposeSwingTest.windowRowChildBounds(): List<Rectangle> =
    (windowContainer().getComponent(0) as Container).components.map { it.bounds }
