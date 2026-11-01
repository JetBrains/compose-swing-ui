package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Behavioral tests for the empty-space composables. What a caller can observe of empty space is the
 * three size requests it makes of its parent's layout and the space it ends up occupying, so each test
 * asserts those against the live component: [RigidArea], [Spacer] and [Strut] hold a size, [Glue] absorbs
 * what is left over, and none of them is a place the user's focus can land.
 */
class BoxFillersTest {
    /** The three size requests of [this], as (minimum, preferred, maximum). */
    private fun Box.Filler.sizeRequests(): Triple<Dimension, Dimension, Dimension> =
        Triple(minimumSize, preferredSize, maximumSize)

    @ParameterizedTest(name = "{0}")
    @MethodSource("fillers")
    fun aFillerAsksForItsDeclaredSizeRequests(
        content: @Composable () -> Unit,
        expected: Triple<Dimension, Dimension, Dimension>,
        message: String,
    ) = runComposeSwingTest {
        setContent { content() }
        onNodeOfType<Box.Filler>().assertProperty(expected, message) { sizeRequests() }
    }

    @Test
    fun aSpacerAndARigidAreaOfEqualSidesAskForTheSame() = runComposeSwingTest {
        setContent {
            Spacer(size = 9)
            RigidArea(width = 9, height = 9)
        }

        val (spacer, rigidArea) = onAllNodesOfType<Box.Filler>().fetchAll()
        assertEquals(
            rigidArea.sizeRequests(),
            spacer.sizeRequests(),
            "a spacer must be the rigid area whose sides are both its size",
        )
    }

    @Test
    fun aSpacerSizeDrivenByStateFollowsItsRecomposition() = runComposeSwingTest {
        var size by mutableIntStateOf(5)
        setContent { Spacer(size) }
        val filler = onNodeOfType<Box.Filler>().fetch()
        assertEquals(Dimension(5, 5), filler.preferredSize, "the spacer starts at its declared size")

        size = 30
        awaitIdle()
        assertEquals(
            Triple(Dimension(30, 30), Dimension(30, 30), Dimension(30, 30)),
            filler.sizeRequests(),
            "a recomposed size must reach the same component on every request",
        )
    }

    @Test
    fun aSizeDrivenByStateFollowsItsRecomposition() = runComposeSwingTest {
        var width by mutableIntStateOf(4)
        setContent { RigidArea(width = width, height = 3) }
        val filler = onNodeOfType<Box.Filler>().fetch()
        assertEquals(Dimension(4, 3), filler.preferredSize, "the rigid area starts at its declared size")

        width = 40
        awaitIdle()
        assertEquals(
            Triple(Dimension(40, 3), Dimension(40, 3), Dimension(40, 3)),
            filler.sizeRequests(),
            "a recomposed size must reach the same component on every request",
        )
    }

    @Test
    fun glueBetweenTwoItemsPushesTheSecondToTheTrailingEdge() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box(axis = BoxLayout.X_AXIS), modifier = SwingModifier.preferredSize(400, 40)) {
                Label("leading", modifier = SwingModifier.fixed())
                Glue()
                Label("trailing", modifier = SwingModifier.fixed())
            }
        }

        val box = onNodeWithText("leading").onParent().fetch<JPanel>()
        val leading = onNodeWithText("leading").fetch<JLabel>()
        val trailing = onNodeWithText("trailing").fetch<JLabel>()
        assertEquals(0, leading.x, "the leading item stays at the box's leading edge")
        assertEquals(
            box.width,
            trailing.x + trailing.width,
            "the glue must take the box's leftover width, leaving the trailing item at the far end",
        )
    }

    @Test
    fun withoutGlueTheItemsStayPackedTogether() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box(axis = BoxLayout.X_AXIS), modifier = SwingModifier.preferredSize(400, 40)) {
                Label("leading", modifier = SwingModifier.fixed())
                Label("trailing", modifier = SwingModifier.fixed())
            }
        }

        val leading = onNodeWithText("leading").fetch<JLabel>()
        val trailing = onNodeWithText("trailing").fetch<JLabel>()
        assertEquals(
            leading.x + leading.width,
            trailing.x,
            "with nothing to absorb the leftover space the items sit edge to edge",
        )
    }

    @Test
    fun emptySpaceIsNotAPlaceFocusCanLand() = runComposeSwingTest {
        setContent { Strut(orientation = SwingConstants.HORIZONTAL, size = 4) }

        onNodeOfType<Box.Filler>().assertProperty(false, "empty space must stay out of focus traversal") { isFocusable }
    }

    @Test
    fun aModifierAppliesToEmptySpace() = runComposeSwingTest {
        setContent { Glue(modifier = SwingModifier.testTag("glue")) }

        onNodeOfType<Box.Filler>().assert(SwingMatcher.hasTestTag("glue"))
    }

    @Test
    fun anOrientationAStrutCannotOccupyIsRejected() = runComposeSwingTest {
        assertFailsWith<IllegalArgumentException>("a strut must refuse an orientation it cannot occupy") {
            setContent { Strut(orientation = SwingConstants.LEADING, size = 4) }
            awaitIdle()
        }
    }

    @Test
    fun anOrientationGlueCannotAbsorbAlongIsRejected() = runComposeSwingTest {
        assertFailsWith<IllegalArgumentException>("glue must refuse an orientation it cannot absorb along") {
            setContent { Glue(orientation = SwingConstants.LEADING) }
            awaitIdle()
        }
    }

    companion object {
        private val unbounded = Short.MAX_VALUE.toInt()

        @JvmStatic
        fun fillers(): List<Arguments> = listOf(
            Arguments.of(
                Named.of("rigid area", @Composable { RigidArea(width = 12, height = 7) }),
                Triple(Dimension(12, 7), Dimension(12, 7), Dimension(12, 7)),
                "a rigid area must ask for its size as its minimum, preferred and maximum alike",
            ),
            Arguments.of(
                Named.of("spacer", @Composable { Spacer(6) }),
                Triple(Dimension(6, 6), Dimension(6, 6), Dimension(6, 6)),
                "a spacer must ask for its one size as its width and height, on every request",
            ),
            Arguments.of(
                Named.of("horizontal strut", @Composable { Strut(orientation = SwingConstants.HORIZONTAL, size = 9) }),
                Triple(Dimension(9, 0), Dimension(9, 0), Dimension(9, unbounded)),
                "a horizontal strut must hold its width and leave its height unconstrained",
            ),
            Arguments.of(
                Named.of("vertical strut", @Composable { Strut(orientation = SwingConstants.VERTICAL, size = 9) }),
                Triple(Dimension(0, 9), Dimension(0, 9), Dimension(unbounded, 9)),
                "a vertical strut must hold its height and leave its width unconstrained",
            ),
            Arguments.of(
                Named.of("glue", @Composable { Glue() }),
                Triple(Dimension(0, 0), Dimension(0, 0), Dimension(unbounded, unbounded)),
                "glue must ask for no space of its own and accept any amount on either axis",
            ),
            Arguments.of(
                Named.of("horizontal glue", @Composable { Glue(orientation = SwingConstants.HORIZONTAL) }),
                Triple(Dimension(0, 0), Dimension(0, 0), Dimension(unbounded, 0)),
                "horizontal glue must absorb width only",
            ),
            Arguments.of(
                Named.of("vertical glue", @Composable { Glue(orientation = SwingConstants.VERTICAL) }),
                Triple(Dimension(0, 0), Dimension(0, 0), Dimension(0, unbounded)),
                "vertical glue must absorb height only",
            ),
        )
    }
}

/**
 * A size a `BoxLayout` cannot stretch, so the leftover space of a row is left for whatever else in it
 * can absorb space. A label's own maximum is unbounded, which would let it share the leftover.
 */
private fun SwingModifier.fixed(): SwingModifier = preferredSize(60, 20).maximumSize(60, 20)
