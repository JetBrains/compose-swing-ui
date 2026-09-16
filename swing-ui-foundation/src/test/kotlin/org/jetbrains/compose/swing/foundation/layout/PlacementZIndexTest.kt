package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Component
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The z-index a policy places a child with decides where that child stands among its siblings: which one
 * paints on top and which one a mouse event at a shared point reaches. A layout modifier's own z-index
 * adds to it.
 */
class PlacementZIndexTest {
    @Test
    fun aChildPlacedAtAHigherZIndexPaintsOnTopAndTakesThePress() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Panel(PanelLayout.Flow(), modifier = filled(Color.RED).testTag("lifted")) {}
                        Panel(PanelLayout.Flow(), modifier = filled(Color.BLUE)) {}
                    },
                    measurePolicy = overlapping { index -> if (index == 0) 1f else 0f },
                    modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT),
                )
            }

            val container = onNodeWithTag(CONTAINER_TAG)
            assertEquals(
                Color.RED.rgb,
                container.captureToImage().getRGB(CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "the child placed at the larger zIndex must paint over the one declared after it",
            )
            assertEquals(
                onNodeWithTag("lifted").fetch<Component>(),
                SwingUtilities.getDeepestComponentAt(container.fetch<Component>(), CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "a press where the children overlap must reach the child placed at the larger zIndex",
            )
        }

    @Test
    fun aChildPlacedRelativeAtAHigherZIndexTakesThePress() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Panel(PanelLayout.Flow(), modifier = filled(Color.RED).testTag("lifted")) {}
                        Panel(PanelLayout.Flow(), modifier = filled(Color.BLUE)) {}
                    },
                    measurePolicy = overlapping(relative = true) { index -> if (index == 0) 1f else 0f },
                    modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT),
                )
            }

            val container = onNodeWithTag(CONTAINER_TAG).fetch<Component>()
            assertEquals(
                onNodeWithTag("lifted").fetch<Component>(),
                SwingUtilities.getDeepestComponentAt(container, CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "a press where the children overlap must reach the child placed relative at the larger zIndex",
            )
        }

    @Test
    fun aLayoutModifiersZIndexAddsToTheOneItsContainerPlacesWith() =
        runComposeSwingTest {
            setContent {
                Layout(
                    content = {
                        Label("child a", modifier = SwingModifier.zIndex(0.6f))
                        Label("child b")
                    },
                    measurePolicy = overlapping { index -> if (index == 0) 0.6f else 1f },
                    modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT),
                )
            }

            assertEquals(
                listOf("child b", "child a"),
                stackedChildren().map { (it as JLabel).text },
                "0.6 placed plus 0.6 declared must lift child a over child b placed at 1.0",
            )
        }

    /**
     * A z-index read only while placing restacks the children when it changes, with no recomposition: the
     * container and its ancestors stay valid, and only the bounds of the child that moved are repainted.
     */
    @Test
    fun aZIndexReadWhilePlacingRestacksWithoutRecomposingOrInvalidating() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var lifted by mutableFloatStateOf(0f)
            var compositions = 0
            setWindowContent {
                SideEffect { compositions++ }
                Box { InsetOverlappingChildren { index -> if (index == 0) lifted else 0f } }
            }
            val container = windowContainer()
            val (first, second) = container.components.reversed()
            assertTrue(container.isValidUpToTheValidateRoot(), "the realized container must start valid")
            val compositionsBefore = compositions

            lifted = 1f
            Snapshot.sendApplyNotifications()

            assertEquals(listOf(first, second), container.components.toList(), "the lifted child must stand on top")
            assertEquals(compositionsBefore, compositions, "a restack must not recompose")
            assertTrue(container.isValidUpToTheValidateRoot(), "a restack must invalidate nothing")
            assertEquals(
                first.bounds,
                container.dirtyRegion(),
                "a restack must repaint only the bounds of the child that moved",
            )
        }

    /** Placing the children again at the z-indices they already stand at leaves the stack and the screen alone. */
    @Test
    fun aPlacementReplayAtUnchangedZIndicesRestacksNothing() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            // Every write notifies, so writing the value it holds replays the placement that reads it.
            var lifted by mutableStateOf(1f, neverEqualPolicy())
            setWindowContent {
                Box { InsetOverlappingChildren { index -> if (index == 0) lifted else 0f } }
            }
            val container = windowContainer()
            val stacked = container.components.toList()

            lifted = 1f
            Snapshot.sendApplyNotifications()

            assertEquals(stacked, container.components.toList(), "an unchanged zIndex must keep the stack")
            assertTrue(container.isValidUpToTheValidateRoot(), "a replay must invalidate nothing")
            assertTrue(container.dirtyRegion().isEmpty, "a replay at unchanged bounds and zIndex must repaint nothing")
        }
}

/**
 * The container under test, larger than its two children and holding both of them overlapping, away from
 * its origin, at the z-index [zIndexOf] names for each declaration index.
 */
@Composable
private fun InsetOverlappingChildren(zIndexOf: (Int) -> Float) {
    Layout(
        content = {
            SizedChild(0)
            SizedChild(1)
        },
        measurePolicy = overlapping(10, zIndexOf = zIndexOf),
        modifier = containerModifier(CHILD_WIDTH + 20, CHILD_HEIGHT + 20),
    )
}

/** A child filling its whole area with [color], at the fixture child's size. */
private fun filled(color: Color): SwingModifier =
    SwingModifier.opaque(true).background(color).preferredSize(CHILD_WIDTH, CHILD_HEIGHT)

/**
 * Places every child at [inset] from the container's origin, from its leading edge where [relative], each at the
 * z-index [zIndexOf] names for its declaration index, in a container leaving
 * [inset] around them.
 */
private fun overlapping(
    inset: Int = 0,
    relative: Boolean = false,
    zIndexOf: (Int) -> Float,
): MeasurePolicy =
    MeasurePolicy { measurables, _ ->
        val placeables =
            measurables.map {
                it.measure(
                    Constraints(CHILD_WIDTH, CHILD_WIDTH, CHILD_HEIGHT, CHILD_HEIGHT),
                )
            }
        layout(CHILD_WIDTH + 2 * inset, CHILD_HEIGHT + 2 * inset) {
            placeables.forEachIndexed { index, placeable ->
                if (relative) {
                    placeable.placeRelative(inset, inset, zIndexOf(index))
                } else {
                    placeable.place(inset, inset, zIndexOf(index))
                }
            }
        }
    }
