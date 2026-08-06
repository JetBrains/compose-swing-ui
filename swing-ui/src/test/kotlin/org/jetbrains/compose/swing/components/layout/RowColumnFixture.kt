package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel

/**
 * The tag the [Row] or [Column] under test carries. A test declares exactly one, so its children are
 * read back off it without having to name each of them.
 */
internal const val CONTAINER_TAG: String = "container"

/** The extent every fixture child asks for across the axis its container arranges children along. */
internal const val CHILD_WIDTH: Int = 50

/** The extent every fixture child asks for along that axis. */
internal const val CHILD_HEIGHT: Int = 40

/** How many children a fixture declares when the point being made needs more than one. */
internal const val CHILD_COUNT: Int = 3

/**
 * The modifier of the row or column under test: a tag to find it by, a size for its parent to impose on
 * it, and the reading direction its arrangement and cross-axis alignment resolve against.
 */
internal fun containerModifier(
    width: Int,
    height: Int,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
): SwingModifier = SwingModifier.testTag(CONTAINER_TAG).preferredSize(width, height).componentOrientation(orientation)

/**
 * A child asking for one fixed size and declaring no maximum of its own, so the extent it ends up
 * occupying is the extent its container granted it and nothing else.
 */
@Composable
internal fun SizedChild(
    index: Int,
    modifier: SwingModifier = SwingModifier,
) {
    Label("child $index", modifier = modifier.preferredSize(CHILD_WIDTH, CHILD_HEIGHT))
}

/** The bounds the row or column under test assigned each of its children, in declaration order. */
internal fun ComposeSwingTest.childBounds(): List<Rectangle> = container().components.map { it.bounds }

/** The size the row or column under test asks of its own parent. */
internal fun ComposeSwingTest.containerPreferredSize(): Dimension = container().preferredSize

/** The size the row or column under test was laid out at. */
internal fun ComposeSwingTest.containerSize(): Dimension = container().size

/** The bounds a column assigns children stacked at [tops], each at the fixture child's own size. */
internal fun columnRows(vararg tops: Int): List<Rectangle> = tops.map { Rectangle(0, it, CHILD_WIDTH, CHILD_HEIGHT) }

/** The bounds a row assigns children lined up at [lefts], each at the fixture child's own size. */
internal fun rowCells(vararg lefts: Int): List<Rectangle> = lefts.map { Rectangle(it, 0, CHILD_WIDTH, CHILD_HEIGHT) }

/** The one row or column a test tagged, the container every reading above is taken from. */
private fun ComposeSwingTest.container(): JPanel = onNodeWithTag(CONTAINER_TAG).fetch<JPanel>()
