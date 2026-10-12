package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.MutableIntState
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Container
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JComponent
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
        runWindowTest {
            val preferredRead = mutableIntStateOf(0)
            val minimumRead = mutableIntStateOf(0)
            val unrelated = mutableIntStateOf(0)
            val counts = QuestionCounts()
            setStockParentContainer(
                counts.questionReadingPolicy(
                    PolicyOperation.Preferred reads { preferredRead.intValue },
                    PolicyOperation.Minimum reads { minimumRead.intValue },
                ),
            )
            val container = windowContainer()
            assertTrue(counts.of(PolicyOperation.Preferred) > 0, "the parent must have asked for the preferred size")
            assertEquals(0, counts.of(PolicyOperation.Minimum), "nothing may have asked for the minimum size yet")

            // Moves the global snapshot forward, so the next observeReads replaces reads recorded under an
            // earlier snapshot even when nothing else applied one in between.
            unrelated.intValue = 1
            Snapshot.sendApplyNotifications()
            container.minimumSize
            assertEquals(1, counts.of(PolicyOperation.Minimum))

            assertReadStillObserved(container, preferredRead, "the preferred-size read must still be observed")
        }

    /**
     * A size stays cached while the parent measures the container again, so the read behind it stays observed
     * after that measure.
     */
    @ParameterizedTest
    @EnumSource(PolicyOperation::class, names = ["Preferred", "Minimum"])
    fun aSizeReadStaysObservedAfterTheParentMeasuresAgain(question: PolicyOperation) =
        runWindowTest {
            val sizeRead = mutableIntStateOf(0)
            val parentPass = mutableIntStateOf(0)
            val counts = QuestionCounts()
            setMeasuredContainer(
                counts.questionReadingPolicy(question reads { sizeRead.intValue }),
                parentRead = { parentPass.intValue },
            )
            val container = windowContainer()
            container.ask(question)
            val sizeAsked = counts.of(question)
            val measuredAsked = counts.of(PolicyOperation.Loose)
            assertTrue(sizeAsked > 0, "the $question size must have been answered")

            parentPass.intValue = 1
            awaitIdle()
            assertTrue(
                counts.of(PolicyOperation.Loose) > measuredAsked,
                "the parent must have measured the container again",
            )
            assertEquals(sizeAsked, counts.of(question), "the $question size must have stayed cached")

            assertReadStillObserved(container, sizeRead, "the $question-size read must still be observed")
        }

    /** A standalone offer stays observed after the stock parent lays the container out at its assigned bounds. */
    @Test
    fun aMeasureReadStaysObservedAfterTheContainerSettlesAgain() =
        runWindowTest {
            val measureRead = mutableIntStateOf(0)
            val settleRead = mutableIntStateOf(0)
            val counts = QuestionCounts()
            setStockParentContainer(
                counts.questionReadingPolicy(
                    PolicyOperation.Loose reads { measureRead.intValue },
                    PolicyOperation.Settle reads { settleRead.intValue },
                ),
            )
            val container = windowContainer() as ConstrainedPanel
            // An offer smaller than the container, so the layout pass cannot reuse the measure it yields.
            container.measure(Constraints(maxWidth = CHILD_WIDTH / 2, maxHeight = CHILD_HEIGHT / 2))
            val measuredAsked = counts.of(PolicyOperation.Loose)
            val settledAsked = counts.of(PolicyOperation.Settle)

            container.doLayout()
            assertTrue(counts.of(PolicyOperation.Settle) > settledAsked, "the layout pass must have settled afresh")
            assertEquals(
                measuredAsked,
                counts.of(PolicyOperation.Loose),
                "the stock parent must not repeat the explicit offer",
            )

            assertReadStillObserved(container, measureRead, "the read behind the explicit offer must still be observed")
        }

    /** A placement replay keeps the read behind an earlier answer observed. */
    @ParameterizedTest
    @EnumSource(PolicyOperation::class, names = ["Preferred", "Settle"])
    fun aReadStaysObservedAfterAPlacementReplay(question: PolicyOperation) =
        runWindowTest {
            val resultRead = mutableIntStateOf(0)
            var offset by mutableIntStateOf(0)
            val counts = QuestionCounts()
            setStockParentContainer(
                counts.questionReadingPolicy(
                    question reads { resultRead.intValue },
                    PolicyOperation.Place reads { offset },
                ),
            )
            val container = windowContainer()
            assertTrue(counts.of(PolicyOperation.Settle) > 0, "the container must have settled afresh")
            val settledAsked = counts.of(PolicyOperation.Settle)
            val placedAsked = counts.of(PolicyOperation.Place)

            offset = 1
            Snapshot.sendApplyNotifications()
            assertTrue(counts.of(PolicyOperation.Place) > placedAsked, "the placement must have replayed")
            awaitIdle()
            assertEquals(settledAsked, counts.of(PolicyOperation.Settle), "the replay must not settle again")

            assertReadStillObserved(container, resultRead, "the $question read must still be observed")
        }

    /**
     * A placement replay that resizes a child lays that child out, as a validation would: a row the replay
     * widens gives its weighted child the new width at once.
     */
    @Test
    fun aPlacementReplayThatResizesAChildLaysThatChildOut() =
        runWindowTest {
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

/**
 * The questions [questionReadingPolicy] counts: Swing's preferred and minimum size queries, a measure under an
 * offer, a settle at the bounds a stock parent assigned, and a run of the placement block.
 */
enum class PolicyOperation {
    Preferred,
    Minimum,
    Loose,
    Settle,
    Place,
}

/** How often each [PolicyOperation] has been asked. */
private class QuestionCounts {
    private val counts = IntArray(PolicyOperation.entries.size)

    fun of(question: PolicyOperation): Int = counts[question.ordinal]

    fun record(question: PolicyOperation) {
        counts[question.ordinal]++
    }
}

/** Pairs this question with the [read] its policy makes each time it is asked. */
private infix fun PolicyOperation.reads(read: () -> Unit): Pair<PolicyOperation, () -> Unit> = this to read

/**
 * One child at the fixture child's size, counting in this receiver each question it is asked and running the
 * read [operationReads] names for that question.
 */
private fun QuestionCounts.questionReadingPolicy(
    vararg operationReads: Pair<PolicyOperation, () -> Unit>,
): MeasurePolicy {
    val readsByOperation = operationReads.toMap()
    return object : MeasurePolicy {
        private fun answer(question: PolicyOperation) {
            record(question)
            readsByOperation[question]?.invoke()
        }

        private fun MeasureScope.childLaidOut(
            measurables: List<Measurable>,
            width: Int,
            height: Int,
        ): MeasureResult {
            val placeable = measurables.single().measure(Constraints(maxWidth = CHILD_WIDTH, maxHeight = CHILD_HEIGHT))
            return layout(width, height) {
                answer(PolicyOperation.Place)
                placeable.place(0, 0)
            }
        }

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            answer(PolicyOperation.Loose)
            return childLaidOut(
                measurables,
                constraints.constrainWidth(CHILD_WIDTH),
                constraints.constrainHeight(CHILD_HEIGHT),
            )
        }

        override fun MeasureScope.measureAtAssignedSize(
            measurables: List<Measurable>,
            width: Int,
            height: Int,
        ): MeasureResult {
            answer(PolicyOperation.Settle)
            return childLaidOut(measurables, width, height)
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            answer(PolicyOperation.Preferred)
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
            answer(PolicyOperation.Minimum)
            return 0
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = 0
    }
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

/** Runs [block] against a showing window, which needs a display. */
private fun runWindowTest(block: suspend ComposeSwingTest.() -> Unit) =
    runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        block()
    }

/** Shows [policy]'s container under a stock parent, which lays it out at its preferred size. */
private suspend fun ComposeSwingTest.setStockParentContainer(policy: MeasurePolicy) =
    setWindowContent {
        SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) }) {
            Layout(
                content = { SizedChild(0) },
                measurePolicy = policy,
                modifier = SwingModifier.testTag(CONTAINER_TAG),
            )
        }
    }

/** Shows [policy]'s container under a Foundation parent whose policy runs [parentRead] on every measure. */
private suspend fun ComposeSwingTest.setMeasuredContainer(
    policy: MeasurePolicy,
    parentRead: () -> Unit,
) = setWindowContent {
    Layout(measurePolicy = measuringParent(read = parentRead)) {
        Layout(
            content = { SizedChild(0) },
            measurePolicy = policy,
            modifier = SwingModifier.testTag(CONTAINER_TAG),
        )
    }
}

/** Asks this component the Swing size query [question] names. */
private fun JComponent.ask(question: PolicyOperation) {
    when (question) {
        PolicyOperation.Preferred -> preferredSize
        PolicyOperation.Minimum -> minimumSize
        else -> throw IllegalArgumentException("$question is not a Swing size query")
    }
}

/**
 * Asserts [container] is valid, then invalid after [state] changes: the read of [state] is still observed.
 * Applies the change directly, because an idle wait would revalidate the container first.
 */
private fun assertReadStillObserved(
    container: JComponent,
    state: MutableIntState,
    message: String,
) {
    assertTrue(container.isValidUpToTheValidateRoot(), "the container must be valid before the change")

    state.intValue = 1
    Snapshot.sendApplyNotifications()

    assertFalse(container.isValid, message)
}

/** The bounds the row nested in the container under test assigned its children. */
private fun ComposeSwingTest.windowRowChildBounds(): List<Rectangle> =
    (windowContainer().getComponent(0) as Container).components.map { it.bounds }
