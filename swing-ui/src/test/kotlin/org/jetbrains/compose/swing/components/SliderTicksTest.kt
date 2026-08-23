package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the tick marks and value labels of a [Slider]: the spacings and the painting
 * flags reach the live `JSlider` and follow a state-driven recomposition, snapping resolves a value to
 * the closest tick, and the declared label map decides which labels are drawn - with Swing's own labels
 * at the major tick marks standing in whenever no map is declared and a later map replacing the labels
 * the one before it drew. A caller may keep the map and mutate it in place rather than declaring a new
 * one, and the labels follow that too.
 */
class SliderTicksTest {
    /** The values the slider draws a label at, in ascending order. */
    private fun JSlider.labeledValues(): List<Int> = labelTable
        .keys()
        .toList()
        .map { it as Int }
        .sorted()

    /** The text of the label the slider draws at [value]. */
    private fun JSlider.labelTextAt(value: Int): String = (labelTable[value] as JLabel).text

    @Test
    fun theTickSpacingsAndTickPaintingFollowStateDrivenRecomposition() = runComposeSwingTest {
        var majorTickSpacing by mutableIntStateOf(25)
        var paintTicks by mutableStateOf(false)
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = majorTickSpacing,
                minorTickSpacing = 5,
                paintTicks = paintTicks,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(25, slider.majorTickSpacing, "the slider should render the declared major spacing")
        assertEquals(5, slider.minorTickSpacing, "the slider should render the declared minor spacing")
        assertFalse(slider.paintTicks, "the slider should start without painted ticks")

        majorTickSpacing = 50
        paintTicks = true
        awaitIdle()

        assertEquals(50, slider.majorTickSpacing, "the slider should adopt the new major spacing")
        assertTrue(slider.paintTicks, "the slider should start painting its ticks")
    }

    @Test
    fun snappingResolvesAValueToTheClosestTick() = runComposeSwingTest {
        var value by mutableIntStateOf(0)
        setContent {
            Slider(
                value = value,
                onValueChange = { value = it },
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                snapToTicks = true,
            )
        }

        value = 30
        awaitIdle()

        assertEquals(
            25,
            onNodeOfType<JSlider>().fetch().value,
            "a snapping slider should land on the closest tick",
        )
        assertEquals(25, value, "the snapped value should be reported back")
    }

    @Test
    fun aValueAndTheTicksItSnapsToCanMoveInTheSameRecomposition() = runComposeSwingTest {
        var value by mutableIntStateOf(0)
        var majorTickSpacing by mutableIntStateOf(25)
        setContent {
            Slider(
                value = value,
                onValueChange = { value = it },
                min = 0,
                max = 100,
                majorTickSpacing = majorTickSpacing,
                snapToTicks = true,
            )
        }

        // 34 sits nearest 25 on the old grid and nearest 40 on the new one, so the resolved value says
        // which grid the slider snapped against.
        value = 34
        majorTickSpacing = 20
        awaitIdle()

        assertEquals(
            40,
            onNodeOfType<JSlider>().fetch().value,
            "the value should snap to the grid declared alongside it, not the old one",
        )
    }

    @Test
    fun paintingLabelsWithoutADeclaredMapDrawsOneAtEveryMajorTick() = runComposeSwingTest {
        setContent {
            Slider(value = 30, onValueChange = {}, min = 0, max = 100, majorTickSpacing = 25, paintLabels = true)
        }

        val slider = onNodeOfType<JSlider>().fetch()
        assertTrue(slider.paintLabels, "the slider should paint its labels")
        assertEquals(listOf(0, 25, 50, 75, 100), slider.labeledValues(), "Swing should label every major tick")
    }

    @Test
    fun theLabelsAtTheMajorTicksFollowAChangedMajorTickSpacing() = runComposeSwingTest {
        var majorTickSpacing by mutableIntStateOf(10)
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = majorTickSpacing,
                paintLabels = true,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(
            listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100),
            slider.labeledValues(),
            "the slider should start with a label at every major tick",
        )

        majorTickSpacing = 25
        awaitIdle()

        assertEquals(
            listOf(0, 25, 50, 75, 100),
            slider.labeledValues(),
            "the labels should sit on the grid the ticks moved to",
        )
        assertEquals("75", slider.labelTextAt(75), "a label should render the value it sits at")
    }

    @Test
    fun aDeclaredLabelMapDecidesWhichLabelsAreDrawn() = runComposeSwingTest {
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = mapOf(0 to "quiet", 100 to "loud"),
            )
        }

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(0, 100), slider.labeledValues(), "only the declared values should be labeled")
        assertEquals("quiet", slider.labelTextAt(0), "the label should render the declared text")
        assertEquals("loud", slider.labelTextAt(100), "the label should render the declared text")
    }

    @Test
    fun swappingOneLabelMapForAnotherRepaintsTheTexts() = runComposeSwingTest {
        var labels by mutableStateOf(mapOf(0 to "quiet", 100 to "loud"))
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = labels,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals("quiet", slider.labelTextAt(0), "the declared text is what the slider paints")

        labels = mapOf(0 to "silent", 100 to "silent")
        awaitIdle()

        assertEquals(listOf(0, 100), slider.labeledValues(), "the new map decides which values are labeled")
        assertEquals("silent", slider.labelTextAt(0), "the new text replaces the one the old map declared")
        assertEquals("silent", slider.labelTextAt(100), "at every value the new map declares it at")
        assertNotSame(
            slider.labelTable[0],
            slider.labelTable[100],
            "each entry is drawn by a label of its own, two entries reading alike included",
        )
    }

    @Test
    fun aDeclaredLabelMapStandsWhenTheRangeMoves() = runComposeSwingTest {
        var labels by mutableStateOf<Map<Int, String>?>(null)
        var max by mutableIntStateOf(100)
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = max,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = labels,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(0, 25, 50, 75, 100), slider.labeledValues(), "Swing should label every major tick")

        labels = mapOf(0 to "quiet", 100 to "loud")
        awaitIdle()
        assertEquals(listOf(0, 100), slider.labeledValues(), "only the declared values should be labeled")

        max = 200
        awaitIdle()

        assertEquals(
            listOf(0, 100),
            slider.labeledValues(),
            "the moved range should not put back the labels the declared map replaced",
        )
        assertEquals("loud", slider.labelTextAt(100), "a declared label should still render its text")
    }

    @Test
    fun theRangeCanMoveAfterTheLabelsAtTheMajorTicksAreWithdrawn() = runComposeSwingTest {
        var min by mutableIntStateOf(0)
        var majorTickSpacing by mutableIntStateOf(25)
        setContent {
            Slider(
                value = 50,
                onValueChange = {},
                min = min,
                max = 100,
                majorTickSpacing = majorTickSpacing,
                paintLabels = true,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(0, 25, 50, 75, 100), slider.labeledValues(), "Swing should label every major tick")

        majorTickSpacing = 0
        awaitIdle()
        assertNull(slider.labelTable, "without a major tick spacing there are no labels to draw")

        min = 10
        awaitIdle()
        assertEquals(10, slider.minimum, "the slider should take the moved minimum")

        majorTickSpacing = 30
        awaitIdle()
        assertEquals(
            listOf(10, 40, 70, 100),
            slider.labeledValues(),
            "the slider should keep taking what the composition declares after the range moved",
        )
    }

    /**
     * A minimum above the maximum is a range no slider can span, and Swing's own model resolves it by
     * bringing the two bounds together on the value declared last. The labels stand at the major ticks
     * of the range the slider was left on, so a second such declaration - which resolves to a different
     * single value - moves them there.
     */
    @Test
    fun theLabelsFollowTheRangeAnInvertedDeclarationResolvesTo() = runComposeSwingTest {
        var min by mutableIntStateOf(10)
        var max by mutableIntStateOf(5)
        setContent {
            Slider(value = 0, onValueChange = {}, min = min, max = max, majorTickSpacing = 2, paintLabels = true)
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(5, slider.minimum, "the slider should resolve the range onto the maximum declared last")
        assertEquals(listOf(5), slider.labeledValues(), "the label should stand at the value the slider spans")

        min = 20
        max = 3
        awaitIdle()

        assertEquals(3, slider.minimum, "the slider should resolve the new range the same way")
        assertEquals(
            listOf(3),
            slider.labeledValues(),
            "the label should move to the value the second range resolved to",
        )
    }

    @Test
    fun withdrawingTheLabelMapBringsBackTheLabelsAtTheMajorTicks() = runComposeSwingTest {
        var labels by mutableStateOf<Map<Int, String>?>(mapOf(0 to "quiet", 100 to "loud"))
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = labels,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(0, 100), slider.labeledValues(), "only the declared values should be labeled")

        labels = null
        awaitIdle()

        assertEquals(listOf(0, 25, 50, 75, 100), slider.labeledValues(), "Swing should label every major tick again")
        assertEquals("25", slider.labelTextAt(25), "the restored labels should render the value they sit at")
    }

    @Test
    fun withdrawingTheLabelMapRestoresTheSameLabelsWhileTheyAreNotPainted() = runComposeSwingTest {
        var labels by mutableStateOf<Map<Int, String>?>(mapOf(0 to "quiet", 100 to "loud"))
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = false,
                labels = labels,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(0, 100), slider.labeledValues(), "only the declared values should be labeled")

        labels = null
        awaitIdle()

        assertEquals(
            listOf(0, 25, 50, 75, 100),
            slider.labeledValues(),
            "the labels a withdrawn map falls back to should not depend on whether they are painted",
        )
    }

    @Test
    fun withdrawingTheLabelMapWithoutAMajorTickSpacingLeavesNoLabels() = runComposeSwingTest {
        var labels by mutableStateOf<Map<Int, String>?>(mapOf(10 to "ten"))
        setContent { Slider(value = 30, onValueChange = {}, min = 0, max = 100, paintLabels = true, labels = labels) }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(10), slider.labeledValues(), "the declared value should be labeled")

        labels = null
        awaitIdle()

        assertNull(slider.labelTable, "without a major tick spacing there are no labels to fall back on")
    }

    @Test
    fun aLabelMapTheCallerMutatesInPlaceRepaintsTheTextItChanged() = runComposeSwingTest {
        val labels = mutableMapOf(0 to "quiet", 100 to "loud")
        var value by mutableIntStateOf(30)
        setContent {
            Slider(
                value = value,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = labels,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals("loud", slider.labelTextAt(100), "the declared text is what the slider paints")

        // The caller keeps the map and edits it, so the slider is handed the same instance a pass later.
        // Nothing tells the two apart but the entries themselves.
        labels[100] = "deafening"
        value = 40
        awaitIdle()

        assertEquals(
            "deafening",
            slider.labelTextAt(100),
            "a map the caller mutates in place should reach the labels the slider paints",
        )
    }

    @Test
    fun mutatingASnapshotLabelMapIsOnItsOwnEnoughToRepaintTheText() = runComposeSwingTest {
        val labels = mutableStateMapOf(0 to "quiet", 100 to "loud")
        setContent {
            Slider(
                value = 30,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = labels,
            )
        }
        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals("loud", slider.labelTextAt(100), "the declared text is what the slider paints")

        // Nothing else is declared anew: the slider recomposes only if the entries were read while it
        // composed, which is what subscribes it to the map.
        labels[100] = "deafening"
        awaitIdle()

        assertEquals(
            "deafening",
            slider.labelTextAt(100),
            "an entry mutated in a snapshot map should invalidate the slider on its own",
        )
    }

    @Test
    fun rememberedLabelsPaintTheirTextAndTheSameTableSurvivesAnUnchangedRecomposition() = runComposeSwingTest {
        var value by mutableIntStateOf(30)
        setContent {
            val labels = mapOf(0 to "quiet", 100 to "loud")
            Slider(
                value = value,
                onValueChange = {},
                min = 0,
                max = 100,
                majorTickSpacing = 25,
                paintLabels = true,
                labels = labels,
            )
        }

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(listOf(0, 100), slider.labeledValues(), "only the declared values should be labeled")
        assertEquals("quiet", slider.labelTextAt(0), "the label should render the declared text")
        assertEquals("loud", slider.labelTextAt(100), "the label should render the declared text")

        val installedTable = slider.labelTable
        value = 40
        awaitIdle()

        assertTrue(
            installedTable === slider.labelTable,
            "a table built from texts that stand unchanged should not be installed afresh",
        )
    }
}
