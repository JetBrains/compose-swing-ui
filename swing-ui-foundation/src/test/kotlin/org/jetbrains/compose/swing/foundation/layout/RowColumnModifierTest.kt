package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row and a column follow a declaration their child changes: an alignment it names for itself, and
 * whether a weight it claims fills the extent it is granted. What the last pass placed is not what the
 * next one places.
 *
 * A case that androidx `foundation-layout`'s own `RowColumnModifierTest` makes keeps that test's name,
 * so the two files read side by side. That test's two `alignBy` cases are not here: they change the
 * block an `alignBy(Measured) -> Int` resolves, and this library carries the one alignment line Swing
 * has, which `alignByBaseline()` reaches.
 */
class RowColumnModifierTest {
    @Test
    fun testRow_updatesOnAlignmentChange() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.Top)
            setContent {
                Box(modifier = SwingModifier.preferredSize(BOX_EXTENT, BOX_EXTENT)) {
                    Row(modifier = SwingModifier.fillMaxWidth().testTag(CONTAINER_TAG)) {
                        RowChildren { index ->
                            SwingModifier.align(alignment).takeIf { index == SHORT } ?: SwingModifier
                        }
                    }
                }
            }

            assertEquals(0, shortChild().y, "the short child sits at the top of the row while it is aligned there")

            alignment = Alignment.CenterVertically
            awaitIdle()

            assertEquals(
                (TALL - SHORT_HEIGHT) / 2,
                shortChild().y,
                "changing the alignment it declares must move the child, not leave it where the last pass put it",
            )
        }

    @Test
    fun testRow_updatesOnWeightChange() =
        runComposeSwingTest {
            var fill by mutableStateOf(false)
            setContent {
                Box(modifier = SwingModifier.preferredSize(WIDE_BOX_EXTENT, WIDE_BOX_EXTENT)) {
                    Row(modifier = SwingModifier.fillMaxWidth().testTag(CONTAINER_TAG)) {
                        RowChildren { SwingModifier.weight(1f, fill) }
                    }
                }
            }

            assertEquals(
                List(CHILDREN) { CHILD_EXTENT },
                childWidths(),
                "a weighted child that does not fill keeps the width it prefers",
            )

            fill = true
            awaitIdle()

            assertEquals(
                List(CHILDREN) { WIDE_BOX_EXTENT / CHILDREN },
                childWidths(),
                "starting to fill must give each child the whole share it is granted",
            )
        }

    @Test
    fun testRow_updatesOnWeightAndAlignmentChange() =
        runComposeSwingTest {
            var fill by mutableStateOf(false)
            var alignment by mutableStateOf(Alignment.Top)
            setContent {
                Box(modifier = SwingModifier.preferredSize(WIDE_BOX_EXTENT, WIDE_BOX_EXTENT)) {
                    Row(modifier = SwingModifier.fillMaxWidth().testTag(CONTAINER_TAG)) {
                        RowChildren { SwingModifier.weight(1f, fill).align(alignment) }
                    }
                }
            }

            assertEquals(List(CHILDREN) { CHILD_EXTENT }, childWidths(), "each child keeps the width it prefers")
            assertEquals(0, shortChild().y, "and sits at the top of the row")

            fill = true
            alignment = Alignment.CenterVertically
            awaitIdle()

            assertEquals(
                List(CHILDREN) { WIDE_BOX_EXTENT / CHILDREN },
                childWidths(),
                "both declarations must be followed in the one pass that sees them change",
            )
            assertEquals((TALL - SHORT_HEIGHT) / 2, shortChild().y, "and the child must move to the new alignment")
        }

    @Test
    fun testColumn_updatesOnAlignmentChange() =
        runComposeSwingTest {
            var alignment by mutableStateOf(Alignment.Start)
            setContent {
                Box(modifier = SwingModifier.preferredSize(BOX_EXTENT, BOX_EXTENT)) {
                    Column(modifier = SwingModifier.fillMaxHeight().testTag(CONTAINER_TAG)) {
                        ColumnChildren { index ->
                            SwingModifier.align(alignment).takeIf { index == SHORT } ?: SwingModifier
                        }
                    }
                }
            }

            assertEquals(0, shortChild().x, "the narrow child sits at the leading edge while it is aligned there")

            alignment = Alignment.CenterHorizontally
            awaitIdle()

            assertEquals(
                (TALL - SHORT_HEIGHT) / 2,
                shortChild().x,
                "changing the alignment it declares must move the child across the column",
            )
        }

    @Test
    fun testColumn_updatesOnWeightChange() =
        runComposeSwingTest {
            var fill by mutableStateOf(false)
            setContent {
                Box(modifier = SwingModifier.preferredSize(WIDE_BOX_EXTENT, WIDE_BOX_EXTENT)) {
                    Column(modifier = SwingModifier.fillMaxHeight().testTag(CONTAINER_TAG)) {
                        ColumnChildren { SwingModifier.weight(1f, fill) }
                    }
                }
            }

            assertEquals(
                List(CHILDREN) { CHILD_EXTENT },
                childHeights(),
                "a weighted child that does not fill keeps the height it prefers",
            )

            fill = true
            awaitIdle()

            assertEquals(
                List(CHILDREN) { WIDE_BOX_EXTENT / CHILDREN },
                childHeights(),
                "starting to fill must give each child the whole share it is granted",
            )
        }

    @Test
    fun testColumn_updatesOnWeightAndAlignmentChange() =
        runComposeSwingTest {
            var fill by mutableStateOf(false)
            var alignment by mutableStateOf(Alignment.Start)
            setContent {
                Box(modifier = SwingModifier.preferredSize(WIDE_BOX_EXTENT, WIDE_BOX_EXTENT)) {
                    Column(modifier = SwingModifier.fillMaxHeight().testTag(CONTAINER_TAG)) {
                        ColumnChildren { SwingModifier.weight(1f, fill).align(alignment) }
                    }
                }
            }

            assertEquals(List(CHILDREN) { CHILD_EXTENT }, childHeights(), "each child keeps the height it prefers")
            assertEquals(0, shortChild().x, "and sits at the leading edge of the column")

            fill = true
            alignment = Alignment.CenterHorizontally
            awaitIdle()

            assertEquals(
                List(CHILDREN) { WIDE_BOX_EXTENT / CHILDREN },
                childHeights(),
                "both declarations must be followed in the one pass that sees them change",
            )
            assertEquals((TALL - SHORT_HEIGHT) / 2, shortChild().x, "and the child must move to the new alignment")
        }
}

/** How many children each case declares, which is what a weight's share is divided among. */
private const val CHILDREN = 5

/** The extent every child asks for along the container's axis, and across it but for the short one. */
private const val CHILD_EXTENT = 20

/** The extent of the children that are not short, which is the container's own extent across the axis. */
private const val TALL = 20

/** Which child is short across the axis, so that an alignment has room to move it. */
private const val SHORT = 4

/** The extent the short child asks for across the axis. */
private const val SHORT_HEIGHT = 10

/** A box large enough that a row of five children at their own extent fits inside it. */
private const val BOX_EXTENT = 100

/** A box wide enough that filling a weighted share is twice the extent a child prefers. */
private const val WIDE_BOX_EXTENT = 200

/** Five children of a row, the last of them short, each carrying what [modifier] declares for its index. */
@Composable
private fun RowScope.RowChildren(modifier: RowScope.(Int) -> SwingModifier) {
    repeat(CHILDREN) { index ->
        Label(
            "child $index",
            modifier = modifier(index).preferredSize(CHILD_EXTENT, if (index == SHORT) SHORT_HEIGHT else TALL),
        )
    }
}

/** Five children of a column, the last of them narrow; see [RowChildren], which this mirrors. */
@Composable
private fun ColumnScope.ColumnChildren(modifier: ColumnScope.(Int) -> SwingModifier) {
    repeat(CHILDREN) { index ->
        Label(
            "child $index",
            modifier = modifier(index).preferredSize(if (index == SHORT) SHORT_HEIGHT else TALL, CHILD_EXTENT),
        )
    }
}

/** The one row or column a test tagged, which every reading is taken from. */
private fun ComposeSwingTest.container(): JComponent = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()

/** The bounds the container assigned the child that is short across its axis. */
private fun ComposeSwingTest.shortChild(): Rectangle = container().childrenInDeclarationOrder()[SHORT].bounds

/** The width the container assigned each of its children, in declaration order. */
private fun ComposeSwingTest.childWidths(): List<Int> = container().childrenInDeclarationOrder().map { it.width }

/** The height the container assigned each of its children, in declaration order. */
private fun ComposeSwingTest.childHeights(): List<Int> = container().childrenInDeclarationOrder().map { it.height }
