package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Constrainable
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.swingParentSlots
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.GridBagConstraints
import javax.swing.JComponent
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val WIDTH = 320
private const val TRANSITION_MILLIS = 1000
private const val CONTAINER = "container"
private const val CONTENT = "content"

/** The size of [content] at the width of the cell. */
private val AtCellWidth = Dimension(WIDTH, (WIDTH * 9f / 16f).roundToInt())

/**
 * Animated containers in a `GridBagLayout` cell that fills its width, with content wider than the cell. Short of the
 * preferred width, `GridBagLayout` lays the cell out by minimum sizes. The content is a label with no minimum size at a
 * ratio, which has no height at its minimum width. The container is laid out at the cell's width and the content's
 * height there, whatever the transition it shows, or showed, the content with.
 *
 * Where a test says androidx measures the content with the constraints its parent hands the container, that is
 * `EnterExitTransitionModifierNode.measure`.
 */
class AnimatedGridBagCellTest {
    @Test
    fun `an AnimatedVisibility takes the content's height at the cell's width once it has entered`() {
        val enters =
            mapOf(
                "no transition" to EnterTransition.None,
                "fadeIn" to fadeIn(spec()),
                "slideInHorizontally" to slideInHorizontally(spec()),
                "slideInVertically" to slideInVertically(spec()),
                "expandIn" to expandIn(spec()),
                "expandHorizontally" to expandHorizontally(spec()),
                "expandVertically" to expandVertically(spec()),
            )
        // A scale transition requires a Foundation parent, so it is shown in a Box only.
        val scalingEnters = mapOf("scaleIn" to scaleIn(spec()))
        val shown =
            buildMap<String, @Composable (SwingModifier) -> Unit> {
                for ((kind, enter) in enters) {
                    this["under the cell, $kind"] = { modifier ->
                        val state = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(state, modifier, enter = enter, exit = ExitTransition.None) { Content() }
                    }
                }
                for ((kind, enter) in enters + scalingEnters) {
                    this["in a Box, $kind"] = { modifier ->
                        Box(modifier = modifier) {
                            val state = remember { MutableTransitionState(false).apply { targetState = true } }
                            AnimatedVisibility(state, enter = enter, exit = ExitTransition.None) { Content() }
                        }
                    }
                }
            }
        assertEachTakesTheCellWidth(shown)
    }

    @Test
    fun `an AnimatedVisibility takes the content's height at the cell's width part-way through a size-keeping exit`() {
        val exits =
            mapOf(
                "fadeOut" to fadeOut(spec()),
                "slideOutHorizontally" to slideOutHorizontally(spec()),
                "slideOutVertically" to slideOutVertically(spec()),
            )
        val exiting =
            exits.mapValues { (_, exit) ->
                @Composable { modifier: SwingModifier ->
                    val state = remember { MutableTransitionState(true).apply { targetState = false } }
                    AnimatedVisibility(state, modifier, enter = EnterTransition.None, exit = exit) { Content() }
                }
            }
        assertEachTakesTheCellWidth(exiting, partWay = true)
    }

    @Test
    fun `an AnimatedContent takes the content's height at the cell's width at rest and once the content entered`() {
        val transitions =
            mapOf(
                "fade" to (fadeIn(spec()) togetherWith fadeOut(spec())),
                "expand and shrink" to (expandIn(spec()) togetherWith shrinkOut(spec())),
            )
        val shown =
            buildMap<String, @Composable (SwingModifier) -> Unit> {
                this["at rest"] = { modifier ->
                    AnimatedContent(targetState = true, modifier = modifier) { shown -> if (shown) Content() }
                }
                for ((kind, transition) in transitions) {
                    this["after a $kind"] = { modifier ->
                        AnimatedContent(rememberGrown(), modifier, transitionSpec = { transition }) { grown ->
                            if (grown) Content() else Label("Other content")
                        }
                    }
                }
            }
        assertEachTakesTheCellWidth(shown)
    }

    @Test
    fun `a container with animateContentSize takes the content's height at the cell's width at rest and once grown`() {
        val shown =
            mapOf<String, @Composable (SwingModifier) -> Unit>(
                "at rest" to { modifier ->
                    Box(modifier = modifier) { Box(modifier = SwingModifier.animateContentSize(spec())) { Content() } }
                },
                "after it grew" to { modifier ->
                    Box(modifier = modifier) {
                        val grown = rememberGrown()
                        Column(modifier = SwingModifier.animateContentSize(spec())) {
                            Content()
                            if (grown) Label("Added")
                        }
                    }
                },
            )
        assertEachTakesTheCellWidth(shown)
    }

    /**
     * A size change lays the content out at its own size part-way, and at the cell's width once the container and the
     * content have entered. A fade changes no size, so it lays the content out at the cell's width throughout. Androidx
     * measures the content with the constraints its parent hands the container, on every frame, so it lays the content
     * out at the cell's width part-way. Aligning with androidx turns this test red.
     */
    @Test
    fun `an AnimatedVisibility entering the cell lays the content out at its own size part-way and with no paint`() {
        // expandIn and expandVertically start from no height, so GridBagLayout zeroes the cell during the delay.
        // expandHorizontally and fadeIn keep the content's height, so the cell is never zeroed.
        val enters =
            mapOf(
                "expandIn" to expandIn(delayedSpec()),
                "expandVertically" to expandVertically(delayedSpec()),
                "expandHorizontally" to expandHorizontally(delayedSpec()),
                "fadeIn" to fadeIn(delayedSpec()),
            )
        val sizes = mutableMapOf<String, List<Dimension>>()
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent { EnteringCell(visible, enter) }
                mainClock.autoAdvance = false
                visible = true
                // Past the delay and half-way through the transition.
                mainClock.advanceTimeBy((TRANSITION_MILLIS + TRANSITION_MILLIS / 2).milliseconds)
                awaitIdle()
                val partWay = onNodeWithTag(CONTENT).fetch<JComponent>().size
                mainClock.autoAdvance = true
                awaitIdle()
                sizes[kind] =
                    listOf(
                        partWay,
                        onNodeWithTag(CONTAINER).fetch<JComponent>().size,
                        onNodeWithTag(CONTENT).fetch<JComponent>().size,
                    )
            }
        }
        assertEquals(
            enters.keys.associateWith { kind ->
                listOf(if (kind == "fadeIn") AtCellWidth else OwnSize, AtCellWidth, AtCellWidth)
            },
            sizes,
            "the content part-way, then the container and the content once entered",
        )
    }

    /**
     * An expand from no height lays the content out at its own size on the frames the cell shows, and at the cell's
     * width once the expand ends. Androidx measures the content with the constraints its parent hands the container, on
     * every frame, so it lays the content out at the cell's width on every frame. Aligning with androidx turns this
     * test red.
     */
    @Test
    fun `an expand from no height in the cell lays the content out at its own size on every frame it shows`() {
        // GridBagLayout zeroes the cell during the delay. A zeroed cell shows nothing, so the frames it is zeroed are
        // not read.
        val enters = mapOf("expandIn" to expandIn(delayedSpec()), "expandVertically" to expandVertically(delayedSpec()))
        val sizes = mutableMapOf<String, Set<Dimension>>()
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent { EnteringCell(visible, enter) }
                mainClock.autoAdvance = false
                visible = true
                val taken = mutableSetOf<Dimension>()
                repeat(2 * TRANSITION_MILLIS / 16) {
                    driveOneFrame()
                    val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                    if (container.width > 0 && container.height > 0) taken += onNodeWithTag(CONTENT).fetch().size
                }
                sizes[kind] = taken
            }
        }
        assertEquals(
            enters.keys.associateWith { kind ->
                if (kind == "expandIn") setOf(OwnSize, AtCellWidth) else setOf(AtCellWidth)
            },
            sizes,
        )
    }

    /**
     * A container in the cell it zeroed answers a minimum height of the content at its own size, which has no height at
     * a width that is asked. Androidx measures the content with the constraints its parent hands the container, on
     * every frame, so it asks the content at the width asked. Aligning with androidx turns this test red.
     */
    @Test
    fun `an AnimatedVisibility in the cell it zeroed answers a minimum height for the content at its own size`() =
        runComposeSwingTest {
            // expandIn starts from no height, so GridBagLayout zeroes the cell during the delay.
            var visible by mutableStateOf(false)
            setContent { EnteringCell(visible, expandIn(delayedSpec())) }
            mainClock.autoAdvance = false
            visible = true
            repeat(3) { driveOneFrame() }
            val container = onNodeWithTag(CONTAINER).fetch<JComponent>() as Constrainable

            assertEquals(0, container.minIntrinsicHeight(WIDTH))
        }

    /**
     * A container in a cell narrowed past the content lays the content out at its own width part-way through a size
     * change, at the content's preferred height. Androidx measures the content with the constraints its parent hands
     * the container, on every frame, so it lays the content out at the narrowed width. Aligning with androidx turns
     * this test red.
     */
    @Test
    fun `an AnimatedVisibility lays the content out at its own size part-way through a size change in a narrow cell`() {
        assertEachKeepsTheContentHeightInANarrowedCell(
            mapOf(
                "expandIn" to { modifier ->
                    val state = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(state, modifier, enter = expandIn(spec()), exit = ExitTransition.None) {
                        TallContent()
                    }
                },
                "shrinkOut" to { modifier ->
                    val state = remember { MutableTransitionState(true).apply { targetState = false } }
                    AnimatedVisibility(state, modifier, enter = EnterTransition.None, exit = shrinkOut(spec())) {
                        TallContent()
                    }
                },
            ),
        )
    }

    /**
     * Known limit: a cell narrower than the content shows no size change. The container answers the content's own
     * width, the cell cannot give it, and `GridBagLayout` lays the cell out by minimum sizes, so the container stays at
     * no size until the transition ends, then takes one frame at the height of the content's own size and settles at
     * the cell's width. androidx measures the content with the constraints its parent hands the container, so the
     * expand shows in the cell. Aligning with androidx turns this test red.
     */
    @Test
    fun `an expand in a cell narrower than the content shows no size change until the transition ends`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent { EnteringCell(visible, expandVertically(delayedSpec())) }
            mainClock.autoAdvance = false
            visible = true
            val frames = mutableListOf<Dimension>()
            repeat(2 * TRANSITION_MILLIS / 16 + 2) {
                driveOneFrame()
                frames += onNodeWithTag(CONTAINER).fetch<JComponent>().size
            }
            val runs = mutableListOf<Pair<Dimension, Int>>()
            for (size in frames) {
                if (runs.lastOrNull()?.first == size) {
                    runs[runs.lastIndex] = size to runs.last().second + 1
                } else {
                    runs += size to 1
                }
            }
            assertEquals(
                listOf(Dimension(), Dimension(WIDTH, 2 * AtCellWidth.height), AtCellWidth),
                runs.map { it.first },
                "the container was at no size, then at the cell's height for the content's own size, then settled",
            )
            assertEquals(1, runs[1].second, "the container was at the content's own size on exactly one frame")
            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            assertEquals(0, cyclesUntilStable(container), "a paint asks for no layout")
        }

    /**
     * A container in a cell narrowed past the contents lays each content out at its own size part-way through a change,
     * at its preferred height. Here androidx's `AnimatedContentMeasurePolicy.measure` (AnimatedContent.kt:1305-1330
     * under compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation) measures every content with
     * the constraints its parent hands the container, so it lays each content out at the narrowed width. Aligning with
     * androidx turns this test red.
     */
    @Test
    fun `an AnimatedContent lays each content out at its own size part-way through a change in a narrow cell`() {
        val transform = expandIn(spec()) togetherWith shrinkOut(spec())
        assertEachKeepsTheContentHeightInANarrowedCell(
            mapOf(
                "a content entering" to { modifier ->
                    AnimatedContent(rememberGrown(), modifier, transitionSpec = { transform }) { grown ->
                        if (grown) TallContent() else Label("Other content")
                    }
                },
                "a content leaving" to { modifier ->
                    AnimatedContent(rememberGrown(), modifier, transitionSpec = { transform }) { grown ->
                        if (grown) Label("Other content") else TallContent()
                    }
                },
            ),
        )
    }

    /** An [AnimatedVisibility] in a `GridBagLayout` cell that fills the width of a panel as wide as the cell. */
    @Composable
    private fun EnteringCell(
        visible: Boolean,
        enter: EnterTransition,
    ) {
        Panel(PanelLayout.Flow()) {
            Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(WIDTH, 2 * WIDTH)) {
                AnimatedVisibility(
                    visible = visible,
                    modifier =
                        SwingModifier
                            .item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL)
                            .testTag(CONTAINER),
                    enter = enter,
                    exit = ExitTransition.None,
                ) {
                    Content()
                }
            }
        }
    }

    /**
     * Shows each of [containers] in the cell, narrows the cell part-way through its transition below the animated width
     * the container answers, so `GridBagLayout` lays the cell out by minimum sizes, and asserts that [TallContent]
     * keeps its own size, [TallOwnSize], while the cell narrows.
     */
    private fun assertEachKeepsTheContentHeightInANarrowedCell(
        containers: Map<String, @Composable (SwingModifier) -> Unit>,
    ) {
        val cell = swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL")
        val sizes = mutableMapOf<String, Dimension>()
        for ((name, container) in containers) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                mainClock.autoAdvance = false
                setContent { cell(width) { modifier -> container(modifier.testTag(CONTAINER)) } }
                mainClock.advanceTimeBy((TRANSITION_MILLIS / 4).milliseconds)
                awaitIdle()
                width = InNarrowedCell.width
                // The frame that applies the new width moves no time, so the animated width is still the wider one.
                mainClock.advanceTimeBy(Duration.ZERO, ignoreFrameDuration = true)
                awaitIdle()
                cyclesUntilStable(onNodeWithTag(CONTAINER).fetch<JComponent>())
                sizes[name] = onNodeWithTag(CONTENT).fetch<JComponent>().size
            }
        }
        assertEquals(containers.keys.associateWith { TallOwnSize }, sizes, "the content in the narrowed cell")
    }

    /**
     * Shows each of [containers] in the cell and asserts that it is as wide as the cell, and the content is at the
     * cell's width, once its transition has finished, or [partWay] through it.
     */
    private fun assertEachTakesTheCellWidth(
        containers: Map<String, @Composable (SwingModifier) -> Unit>,
        partWay: Boolean = false,
    ) {
        val cell = swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL")
        val sizes = mutableMapOf<String, Pair<Int, Dimension>>()
        for ((name, container) in containers) {
            runComposeSwingTest {
                mainClock.autoAdvance = !partWay
                setContent { cell(WIDTH) { modifier -> container(modifier.testTag(CONTAINER)) } }
                if (partWay) mainClock.advanceTimeBy((TRANSITION_MILLIS / 2).milliseconds)
                awaitIdle()
                val holder = onNodeWithTag(CONTAINER).fetch<JComponent>()
                cyclesUntilStable(holder)
                sizes[name] = holder.width to onNodeWithTag(CONTENT).fetch<JComponent>().size
            }
        }
        val expected = containers.keys.associateWith { WIDTH to AtCellWidth }
        assertEquals(expected, sizes, "the container's width and the content")
    }
}

private fun <T> spec() = tween<T>(TRANSITION_MILLIS)

private fun <T> delayedSpec() = tween<T>(TRANSITION_MILLIS, delayMillis = TRANSITION_MILLIS)

/** A label with no minimum size, wider than the cell, at a 16:9 ratio. */
@Composable
private fun ConstrainedScope.Content() {
    Label("", modifier = SwingModifier.testTag(CONTENT).aspectRatio(16f / 9f).preferredSize(2 * WIDTH, WIDTH))
}

/** The size of a cell narrowed below the animated width the container answers, at [TallContent]'s preferred height. */
private val InNarrowedCell = Dimension(WIDTH / 8, WIDTH / 2)

/** The size of [TallContent] measured with no maximum: its preferred size. */
private val TallOwnSize = Dimension(WIDTH / 4, WIDTH / 2)

/** The size of [Content] measured with no maximum: its preferred size. */
private val OwnSize = Dimension(2 * WIDTH, WIDTH)

/** A label filling the width, whose minimum height is far below its preferred height. */
@Composable
private fun ConstrainedScope.TallContent() {
    Label(
        "",
        modifier =
            SwingModifier
                .testTag(CONTENT)
                .fillMaxWidth()
                .preferredSize(WIDTH / 4, InNarrowedCell.height)
                .minimumSize(0, InNarrowedCell.height / 8),
    )
}

/** Whether the composition has grown: false when it is composed, true once an effect has run. */
@Composable
private fun rememberGrown(): Boolean {
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    return grown
}
