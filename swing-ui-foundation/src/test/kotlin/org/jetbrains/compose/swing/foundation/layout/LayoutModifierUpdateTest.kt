package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A layout modifier declared again with other values lays its child out as the same modifier declared with those
 * values from the start, and one declared again with equal values measures nothing again.
 */
class LayoutModifierUpdateTest {
    @Test
    fun aPaddingFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.padding(start = 5, top = 6, end = 7, bottom = 8).fillMaxSize() },
            second = { SwingModifier.absolutePadding(left = 1, top = 2, right = 3, bottom = 4).fillMaxSize() },
            orientation = ComponentOrientation.RIGHT_TO_LEFT,
        )

    @Test
    fun anOffsetFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.offset(x = 5, y = 6) },
            second = { SwingModifier.absoluteOffset(x = 7, y = 8) },
            orientation = ComponentOrientation.RIGHT_TO_LEFT,
        )

    @Test
    fun aSizeFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.size(30, 35) },
            second = { SwingModifier.requiredSize(BOX_EXTENT + 10, BOX_EXTENT + 20) },
        )

    @Test
    fun aFillFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.fillMaxWidth(0.3f) },
            second = { SwingModifier.fillMaxHeight(0.8f) },
        )

    @Test
    fun aWrapContentFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.fillMaxSize().wrapContentWidth(Alignment.End) },
            second = { SwingModifier.fillMaxSize().wrapContentHeight(Alignment.Bottom, unbounded = true) },
            child = { Child(0, 30, BOX_EXTENT + 50, it) },
        )

    @Test
    fun aWrapContentSizeFollowsTheAlignmentItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.fillMaxSize().wrapContentSize(Alignment.TopStart) },
            second = { SwingModifier.fillMaxSize().wrapContentSize(Alignment.BottomEnd) },
        )

    @Test
    fun aDefaultMinSizeFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.defaultMinSize(minWidth = 70) },
            second = { SwingModifier.defaultMinSize(minHeight = 90) },
        )

    @Test
    fun anAspectRatioFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.size(80, 60).aspectRatio(2f) },
            second = { SwingModifier.size(80, 60).aspectRatio(1f, matchHeightConstraintsFirst = true) },
        )

    @Test
    fun anIntrinsicWidthFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.width(IntrinsicSize.Min) },
            second = { SwingModifier.requiredWidth(IntrinsicSize.Max) },
            child = { IntrinsicChild(it) },
            boxExtent = INTRINSIC_BOX_EXTENT,
        )

    @Test
    fun anIntrinsicHeightFollowsEveryValueItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.height(IntrinsicSize.Min) },
            second = { SwingModifier.requiredHeight(IntrinsicSize.Max) },
            child = { IntrinsicChild(it) },
            boxExtent = INTRINSIC_BOX_EXTENT,
        )

    @Test
    fun aLayoutLambdaFollowsTheBlockItIsDeclaredWith() =
        assertFollows(
            first = { SwingModifier.layout(ShiftedBy4) },
            second = { SwingModifier.layout(ShiftedBy9) },
        )

    @Test
    fun aPaddingFollowsAnOrientationChangedAfterItIsDeclared() =
        runComposeSwingTest {
            var orientation by mutableStateOf(ComponentOrientation.LEFT_TO_RIGHT)
            setContent {
                Column {
                    Box(modifier = boxModifier(UPDATED_TAG, BOX_EXTENT, orientation)) {
                        SizedChild(0, SwingModifier.padding(start = 5))
                    }
                    Box(modifier = boxModifier(FRESH_TAG, BOX_EXTENT, ComponentOrientation.RIGHT_TO_LEFT)) {
                        SizedChild(0, SwingModifier.padding(start = 5))
                    }
                }
            }
            val before = childBoundsOf(UPDATED_TAG)

            orientation = ComponentOrientation.RIGHT_TO_LEFT
            awaitIdle()

            assertNotEquals(childBoundsOf(FRESH_TAG), before, "the two orientations must place the child differently")
            assertEquals(
                childBoundsOf(FRESH_TAG),
                childBoundsOf(UPDATED_TAG),
                "a padding must place its child in the orientation its container holds now, not the one it was " +
                    "declared under",
            )
        }

    @Test
    fun aPaddingDeclaredAgainWithEqualValuesMeasuresNothingAgain() =
        runComposeSwingTest {
            var recompositions by mutableIntStateOf(0)
            var start by mutableIntStateOf(4)
            var measures = 0
            setContent {
                Box(modifier = SwingModifier.testTag(UPDATED_TAG)) {
                    // Reading the count recomposes this content, which declares the padding again.
                    if (recompositions >= 0) {
                        SizedChild(0, SwingModifier.padding(start = start).countingMeasures { measures++ })
                    }
                }
            }
            awaitIdle()
            measures = 0

            recompositions++
            awaitIdle()

            assertEquals(0, measures, "a padding declared again with the values it holds must not measure its child")

            start = 8
            awaitIdle()

            assertTrue(measures > 0, "a padding declared with another value must measure its child again")
        }

    @Test
    fun aPaddingDeclaredWithAnotherValueMeasuresTheChildOnceWithoutDiffingTheModifier() =
        runComposeSwingTest {
            var start by mutableIntStateOf(4)
            var measures = 0
            val keyed = KeyReadingElement()
            setContent {
                Box(modifier = SwingModifier.testTag(UPDATED_TAG).preferredSize(BOX_EXTENT, BOX_EXTENT)) {
                    SizedChild(0, SwingModifier.padding(start = start).countingMeasures { measures++ }.then(keyed))
                }
            }
            awaitIdle()
            measures = 0
            val keyReads = keyed.keyReads

            start = 9
            awaitIdle()

            assertEquals(9, childBoundsOf(UPDATED_TAG).x, "a padding declared with another value must place the child")
            assertEquals(1, measures, "a padding declared with another value must measure its child once")
            assertEquals(keyReads, keyed.keyReads, "a padding written in place must leave the modifier undiffed")
        }

    @Test
    fun anOffsetDeclaredWithAnotherValuePlacesTheChildAgainWithoutMeasuringIt() =
        runComposeSwingTest {
            var x by mutableIntStateOf(4)
            var measures = 0
            setContent {
                Box(modifier = SwingModifier.testTag(UPDATED_TAG).preferredSize(BOX_EXTENT, BOX_EXTENT)) {
                    SizedChild(0, SwingModifier.offset(x = x).countingMeasures { measures++ })
                }
            }
            awaitIdle()
            measures = 0

            x = 9
            awaitIdle()

            assertEquals(9, childBoundsOf(UPDATED_TAG).x, "an offset declared with another value must place the child")
            assertEquals(0, measures, "an offset declared with another value must not measure its child again")
        }

    @Test
    fun aSizeDeclaredAgainThroughAnotherBuilderWithEqualBoundsMeasuresNothingAgain() =
        runComposeSwingTest {
            var exact by mutableStateOf(false)
            var measures = 0
            setContent {
                Box(modifier = SwingModifier.testTag(UPDATED_TAG)) {
                    val size = if (exact) SwingModifier.width(10) else SwingModifier.widthIn(10, 10)
                    SizedChild(0, size.countingMeasures { measures++ })
                }
            }
            awaitIdle()
            measures = 0

            exact = true
            awaitIdle()

            assertEquals(0, measures, "width(10) replacing widthIn(10, 10) holds the same bounds and must not measure")
        }

    /**
     * Declares [first] on a child and then [second], beside a child declaring [second] from the start, and asserts
     * the two children end up laid out alike, where [first] had laid the child out otherwise.
     */
    private fun assertFollows(
        first: ConstrainedScope.() -> SwingModifier,
        second: ConstrainedScope.() -> SwingModifier,
        orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
        child: @Composable (SwingModifier) -> Unit = { SizedChild(0, it) },
        boxExtent: Int = BOX_EXTENT,
    ) = runComposeSwingTest {
        var declaresSecond by mutableStateOf(false)
        setContent {
            Column {
                Box(modifier = boxModifier(UPDATED_TAG, boxExtent, orientation)) {
                    child(if (declaresSecond) second() else first())
                }
                Box(modifier = boxModifier(FRESH_TAG, boxExtent, orientation)) { child(second()) }
            }
        }
        val before = childBoundsOf(UPDATED_TAG)

        declaresSecond = true
        awaitIdle()

        assertNotEquals(
            childBoundsOf(FRESH_TAG),
            before,
            "the two declarations must lay the child out differently for this case to show anything",
        )
        assertEquals(
            childBoundsOf(FRESH_TAG),
            childBoundsOf(UPDATED_TAG),
            "a modifier declared again with other values must lay the child out as one declared with them from the " +
                "start",
        )
    }

    private fun boxModifier(
        tag: String,
        extent: Int,
        orientation: ComponentOrientation,
    ): SwingModifier = SwingModifier.testTag(tag).preferredSize(extent, extent).componentOrientation(orientation)

    private fun ComposeSwingTest.childBoundsOf(tag: String): Rectangle =
        onNodeWithTag(tag).fetch<Container>().getComponent(0).bounds

    /** A child whose least width and height are below the ones it prefers. */
    @Composable
    private fun IntrinsicChild(modifier: SwingModifier) {
        Label("intrinsic", modifier = modifier.minimumSize(20, 15).preferredSize(CHILD_WIDTH, CHILD_HEIGHT))
    }

    private companion object {
        const val UPDATED_TAG = "updated"
        const val FRESH_TAG = "fresh"

        /** Space for a fixture child and more along both axes. */
        const val BOX_EXTENT = 100

        /** Less than a fixture child prefers along both axes, and more than it needs. */
        const val INTRINSIC_BOX_EXTENT = 30

        val ShiftedBy4: MeasureScope.(Measurable, Constraints) -> MeasureResult = { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(4, 0) }
        }

        val ShiftedBy9: MeasureScope.(Measurable, Constraints) -> MeasureResult = { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(9, 0) }
        }
    }
}
