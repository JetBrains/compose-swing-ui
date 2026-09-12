package org.jetbrains.compose.swing.foundation.layout

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
 * The tag the container under test carries. A test declares exactly one, so its children are
 * read back off it without having to name each of them.
 */
internal const val CONTAINER_TAG: String = "container"

/** The width every fixture child asks for. */
internal const val CHILD_WIDTH: Int = 50

/** The height every fixture child asks for. */
internal const val CHILD_HEIGHT: Int = 40

/** How many children a fixture declares when the point being made needs more than one. */
internal const val CHILD_COUNT: Int = 3

/**
 * The modifier of the container under test: a tag to find it by, a size for its parent to impose on it,
 * and the reading direction its placement of a child resolves against.
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

/** The fixture child at a size of its own, for a container holding children of more than one size. */
@Composable
internal fun Child(
    index: Int,
    width: Int,
    height: Int,
    modifier: SwingModifier = SwingModifier,
) {
    Label("child $index", modifier = modifier.preferredSize(width, height))
}

/** The bounds the container under test assigned each of its children, in declaration order. */
internal fun ComposeSwingTest.childBounds(): List<Rectangle> = container().components.map { it.bounds }

/** The size the container under test asks of its own parent. */
internal fun ComposeSwingTest.containerPreferredSize(): Dimension = container().preferredSize

/** The extent the container under test can shrink to, the counterpart of [containerPreferredSize]. */
internal fun ComposeSwingTest.containerMinimumSize(): Dimension = container().minimumSize

/** The last entry of this modifier that describes itself - the one the builder under test declared. */
internal fun SwingModifier.lastElement(): SwingModifier.InspectableElement =
    foldIn<SwingModifier.InspectableElement?>(null) { last, element ->
        element as? SwingModifier.InspectableElement ?: last
    } ?: error("the modifier declares nothing that describes itself")

/** The size the container under test was laid out at. */
internal fun ComposeSwingTest.containerSize(): Dimension = container().size

/** The bounds a column assigns children stacked at [tops], each at the fixture child's own size. */
internal fun columnRows(vararg tops: Int): List<Rectangle> = tops.map { Rectangle(0, it, CHILD_WIDTH, CHILD_HEIGHT) }

/** The bounds a row assigns children lined up at [lefts], each at the fixture child's own size. */
internal fun rowCells(vararg lefts: Int): List<Rectangle> = lefts.map { Rectangle(it, 0, CHILD_WIDTH, CHILD_HEIGHT) }

/** The one container a test tagged, which every reading above is taken from. */
private fun ComposeSwingTest.container(): JPanel = onNodeWithTag(CONTAINER_TAG).fetch<JPanel>()
