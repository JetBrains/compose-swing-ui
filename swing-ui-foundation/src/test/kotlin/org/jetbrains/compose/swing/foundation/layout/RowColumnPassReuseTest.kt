package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * A Row or Column calculates each pass from the children and arrangement now declared. Each test runs
 * two passes over one container and asserts the second is not placed by state the first one left behind.
 */
class RowColumnPassReuseTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("configuredContainers")
    internal fun aConfiguredContainerKeepsItsArrangementWhenARecompositionAddsAChild(case: ConfiguredContainer) =
        runComposeSwingTest {
            var count by mutableStateOf(1)
            setContent {
                val childCount = count
                case.container { repeat(childCount) { SizedChild(it) } }
            }
            val container = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
            val initialPolicy =
                assertIs<MeasurePolicyLayout>(container.layout, "expected the measure policy layout").policy
            val children = onNodeWithTag(CONTAINER_TAG).onChildren()
            children.assertCountEquals(case.before.size)
            case.before.forEachIndexed { index, expected ->
                onNodeWithText("child $index").assertProperty(
                    expected,
                    "${case.name}: child $index before recomposition",
                ) { bounds }
            }

            count = 2
            awaitIdle()

            assertSame(
                initialPolicy,
                assertIs<MeasurePolicyLayout>(container.layout, "expected the measure policy layout").policy,
                "an unchanged ${case.name} configuration must keep its remembered measure policy",
            )
            children.assertCountEquals(case.after.size)
            case.after.forEachIndexed { index, expected ->
                onNodeWithText("child $index").assertProperty(
                    expected,
                    "${case.name}: child $index after recomposition",
                ) { bounds }
            }
        }

    @Test
    fun aColumnThatLosesAChildPlacesOnlyTheOnesThatRemain() =
        runComposeSwingTest {
            var count by mutableStateOf(3)
            setContent {
                Column(modifier = containerModifier(100, 300)) {
                    repeat(count) { SizedChild(it) }
                }
            }
            assertEquals(columnRows(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT), childBounds(), "three children stack in order")

            count = 2
            awaitIdle()

            assertEquals(
                columnRows(0, CHILD_HEIGHT),
                childBounds(),
                "the children that remain keep their own places once the third leaves",
            )
        }

    @Test
    fun aColumnThatGainsAChildPlacesItAfterTheOthers() =
        runComposeSwingTest {
            var count by mutableStateOf(2)
            setContent {
                Column(modifier = containerModifier(100, 300)) {
                    repeat(count) { SizedChild(it) }
                }
            }
            assertEquals(columnRows(0, CHILD_HEIGHT), childBounds(), "two children stack in order")

            count = 3
            awaitIdle()

            assertEquals(
                columnRows(0, CHILD_HEIGHT, 2 * CHILD_HEIGHT),
                childBounds(),
                "a child arriving takes the place after the ones already there",
            )
        }

    @Test
    fun aChildThatStopsBeingWeightedTakesTheExtentItPrefersAgain() =
        runComposeSwingTest {
            var weighted by mutableStateOf(true)
            setContent {
                Column(modifier = containerModifier(100, 300)) {
                    SizedChild(0)
                    SizedChild(1, if (weighted) SwingModifier.weight(1f) else SwingModifier)
                }
            }
            assertEquals(
                listOf(
                    Rectangle(0, 0, CHILD_WIDTH, CHILD_HEIGHT),
                    Rectangle(0, CHILD_HEIGHT, CHILD_WIDTH, 260),
                ),
                childBounds(),
                "a weighted child takes the height the column has left over",
            )

            weighted = false
            awaitIdle()

            assertEquals(
                columnRows(0, CHILD_HEIGHT),
                childBounds(),
                "dropping the weight must return the child to the extent it prefers, not leave it at its share",
            )
        }

    @Test
    fun anOffsetTheArrangementLeavesAloneIsZeroRatherThanThePreviousPassValue() =
        runComposeSwingTest {
            val arrangement = GatedArrangement()
            setContent {
                Column(modifier = containerModifier(100, 300), verticalArrangement = arrangement) {
                    repeat(CHILD_COUNT) { SizedChild(it) }
                }
            }
            assertEquals(
                columnRows(0, STEP, 2 * STEP),
                childBounds(),
                "the arrangement places each child at its own step while it is writing offsets",
            )

            arrangement.writesOffsets = false
            onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().doLayout()

            assertEquals(
                columnRows(0, 0, 0),
                childBounds(),
                "an offset this pass never wrote must be zero, not the offset the pass before it wrote",
            )
        }

    companion object {
        @JvmStatic
        fun configuredContainers(): List<Arguments> =
            listOf(
                ConfiguredContainer(
                    name = "Row",
                    container = { content ->
                        Row(
                            modifier = containerModifier(300, 100),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.Bottom,
                        ) { content() }
                    },
                    before = listOf(Rectangle(250, 60, CHILD_WIDTH, CHILD_HEIGHT)),
                    after =
                        listOf(
                            Rectangle(200, 60, CHILD_WIDTH, CHILD_HEIGHT),
                            Rectangle(250, 60, CHILD_WIDTH, CHILD_HEIGHT),
                        ),
                ),
                ConfiguredContainer(
                    name = "Column",
                    container = { content ->
                        Column(
                            modifier = containerModifier(100, 300),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.End,
                        ) { content() }
                    },
                    before = listOf(Rectangle(50, 260, CHILD_WIDTH, CHILD_HEIGHT)),
                    after =
                        listOf(
                            Rectangle(50, 220, CHILD_WIDTH, CHILD_HEIGHT),
                            Rectangle(50, 260, CHILD_WIDTH, CHILD_HEIGHT),
                        ),
                ),
            ).map { Arguments.of(Named.of(it.name, it)) }
    }
}

/** A container with an arrangement and alignment of its own, and where one child and then two are placed in it. */
internal class ConfiguredContainer(
    val name: String,
    val container: @Composable (@Composable () -> Unit) -> Unit,
    val before: List<Rectangle>,
    val after: List<Rectangle>,
)

/** The gap [GatedArrangement] leaves between children while it is placing them. */
private const val STEP = 60

/**
 * An arrangement that places children at a fixed step until it is told to stop, and then writes no offset
 * at all - which is what a pass reusing the offsets of the one before it would fail to notice.
 */
private class GatedArrangement : Arrangement.Vertical {
    var writesOffsets: Boolean = true

    override fun arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        if (!writesOffsets) return
        for (index in outPositions.indices) {
            outPositions[index] = index * STEP
        }
    }
}
