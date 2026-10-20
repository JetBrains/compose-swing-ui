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
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.onWindowWithTitle
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
 * Each result a [Layout] caches keeps the reads behind it observed while another result is computed again:
 * the preferred and minimum sizes, the measure the parent lays out with, the extent the container settles
 * on, and its placement.
 */
class LayoutReadObservationTest {
    /**
     * Swing caches the preferred and the minimum size apart, so each keeps its own reads: asking for the
     * minimum size for the first time, after the preferred size was reported under an earlier snapshot,
     * leaves the preferred-size read observed.
     */
    @Test
    fun aPreferredSizeReadStaysObservedAfterTheMinimumSizeIsAsked() =
        runWindowTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
            val preferredRead = mutableIntStateOf(0)
            val minimumRead = mutableIntStateOf(0)
            val unrelated = mutableIntStateOf(0)
            val counts = mutableListOf<PolicyOperation>()
            setStockParentContainer(
                counts.queryReadingPolicy(
                    PolicyOperation.Preferred reads { preferredRead.intValue },
                    PolicyOperation.Minimum reads { minimumRead.intValue },
                ),
            )
            val container = window.onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            assertTrue(PolicyOperation.Preferred in counts)
            assertEquals(0, counts.count { it == PolicyOperation.Minimum })

            // Moves the global snapshot forward, so the next observeReads replaces reads recorded under an
            // earlier snapshot even when nothing else applied one in between.
            unrelated.intValue = 1
            Snapshot.sendApplyNotifications()
            container.minimumSize
            assertEquals(1, counts.count { it == PolicyOperation.Minimum })

            assertReadStillObserved(container, preferredRead, "the preferred-size read must still be observed")
        }

    /**
     * A size stays cached while the parent measures the container again, so the read behind it stays observed
     * after that measure.
     */
    @ParameterizedTest
    @EnumSource(PolicyOperation::class, names = ["Preferred", "Minimum"])
    fun aSizeReadStaysObservedAfterTheParentMeasuresAgain(query: PolicyOperation) =
        runWindowTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
            val sizeRead = mutableIntStateOf(0)
            val parentPass = mutableIntStateOf(0)
            val counts = mutableListOf<PolicyOperation>()
            setMeasuredContainer(
                counts.queryReadingPolicy(query reads { sizeRead.intValue }),
                parentRead = { parentPass.intValue },
            )
            val container = window.onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            container.ask(query)
            assertTrue(query in counts)
            counts.clear()

            parentPass.intValue = 1
            awaitIdle()
            assertTrue(PolicyOperation.Loose in counts)
            assertEquals(0, counts.count { it == query })

            assertReadStillObserved(container, sizeRead, "the $query-size read must still be observed")
        }

    /** A standalone offer stays observed after the stock parent lays the container out at its assigned bounds. */
    @Test
    fun aMeasureReadStaysObservedAfterTheContainerSettlesAgain() =
        runWindowTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
            val measureRead = mutableIntStateOf(0)
            val settleRead = mutableIntStateOf(0)
            val counts = mutableListOf<PolicyOperation>()
            setStockParentContainer(
                counts.queryReadingPolicy(
                    PolicyOperation.Loose reads { measureRead.intValue },
                    PolicyOperation.Settle reads { settleRead.intValue },
                ),
            )
            val container = window.onNodeWithTag(CONTAINER_TAG).fetch<ConstrainedPanel>()
            // An offer smaller than the container, so the layout pass cannot reuse the measure it yields.
            container.measure(Constraints(maxWidth = CHILD_WIDTH / 2, maxHeight = CHILD_HEIGHT / 2))
            counts.clear()

            container.doLayout()
            assertTrue(PolicyOperation.Settle in counts)
            assertEquals(0, counts.count { it == PolicyOperation.Loose })

            assertReadStillObserved(container, measureRead, "the read behind the explicit offer must still be observed")
        }

    /** A placement replay keeps the read behind an earlier result observed. */
    @ParameterizedTest
    @EnumSource(PolicyOperation::class, names = ["Preferred", "Settle"])
    fun aReadStaysObservedAfterAPlacementReplay(query: PolicyOperation) =
        runWindowTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
            val resultRead = mutableIntStateOf(0)
            var offset by mutableIntStateOf(0)
            val counts = mutableListOf<PolicyOperation>()
            setStockParentContainer(
                counts.queryReadingPolicy(
                    query reads { resultRead.intValue },
                    PolicyOperation.Place reads { offset },
                ),
            )
            val container = window.onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            assertTrue(PolicyOperation.Settle in counts)
            counts.clear()

            offset = 1
            Snapshot.sendApplyNotifications()
            assertTrue(PolicyOperation.Place in counts)
            awaitIdle()
            assertEquals(0, counts.count { it == PolicyOperation.Settle })

            assertReadStillObserved(container, resultRead, "the $query read must still be observed")
        }

    /**
     * A placement replay that resizes a child lays that child out, as a validation would: a row the replay
     * widens gives its weighted child the new width at once.
     */
    @Test
    fun aPlacementReplayThatResizesAChildLaysThatChildOut() =
        runWindowTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
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
                    modifier = ContainerModifier.styleable(containerStyle(200, 300)),
                )
            }
            window.onNodeWithTag(CONTAINER_TAG).onChild().assertIsOfType<Container>().assertProperty(
                listOf(Rectangle(0, 0, 30, CHILD_HEIGHT)),
            ) { components.map { it.bounds } }

            wide = true
            Snapshot.sendApplyNotifications()

            window.onNodeWithTag(CONTAINER_TAG).onChild().assertIsOfType<Container>().assertProperty(
                listOf(Rectangle(0, 0, 60, CHILD_HEIGHT)),
                "the widened row must lay its weighted child out at the new width",
            ) { components.map { it.bounds } }
            window.onNodeWithTag(CONTAINER_TAG).assertProperty(true, "the replay must leave the tree valid") {
                isValidUpToTheValidateRoot()
            }
        }
}

/**
 * The queries [queryReadingPolicy] counts: Swing's preferred and minimum size queries, a measure under an
 * offer, a settle at the bounds a stock parent assigned, and a run of the placement block.
 */
enum class PolicyOperation {
    Preferred,
    Minimum,
    Loose,
    Settle,
    Place,
}

/** Pairs this query with the [read] its policy makes each time it is asked. */
private infix fun PolicyOperation.reads(read: () -> Unit): Pair<PolicyOperation, () -> Unit> = this to read

/**
 * One child at the fixture child's size, counting in this receiver each query it is asked and running the
 * read [operationReads] names for that query.
 */
private fun MutableList<PolicyOperation>.queryReadingPolicy(
    vararg operationReads: Pair<PolicyOperation, () -> Unit>,
): MeasurePolicy {
    val readsByOperation = operationReads.toMap()
    return object : MeasurePolicy {
        private fun report(query: PolicyOperation) {
            add(query)
            readsByOperation[query]?.invoke()
        }

        private fun MeasureScope.childLaidOut(
            measurables: List<Measurable>,
            width: Int,
            height: Int,
        ): MeasureResult {
            val placeable = measurables.single().measure(Constraints(maxWidth = CHILD_WIDTH, maxHeight = CHILD_HEIGHT))
            return layout(width, height) {
                report(PolicyOperation.Place)
                placeable.place(0, 0)
            }
        }

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            report(PolicyOperation.Loose)
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
            report(PolicyOperation.Settle)
            return childLaidOut(measurables, width, height)
        }

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int {
            report(PolicyOperation.Preferred)
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
            report(PolicyOperation.Minimum)
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

/** Asks this component the Swing size query [query] names. */
private fun JComponent.ask(query: PolicyOperation) {
    when (query) {
        PolicyOperation.Preferred -> preferredSize
        PolicyOperation.Minimum -> minimumSize
        else -> throw IllegalArgumentException("$query is not a Swing size query")
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
