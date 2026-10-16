package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.Stretch.atAspectWithin
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Constrainable
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val WIDTH = 320
private const val TRANSITION_MILLIS = 1000
private const val CONTAINER = "container"
private const val CONTENT = "content"

/** The size of [content] at the width of the cell. */
private val AtCellWidth = Dimension(WIDTH, (WIDTH * 9f / 16f).roundToInt())

/** The size of the container with [content] at its natural height: 16:9 at twice the width of the cell. */
private val AtNaturalWidth = Dimension(WIDTH, (2 * WIDTH * 9f / 16f).roundToInt())

/**
 * Animated containers in a `GridBagLayout` cell that fills its width, with content wider than the cell. Short of the
 * preferred width, `GridBagLayout` lays the cell out by minimum sizes. The content is a label with no minimum size at a
 * ratio, which has no height at its minimum width. The container is laid out at the cell's width and the content's
 * height there, whatever the transition it shows, or showed, the content with.
 */
class AnimatedGridBagCellTest {
    @Test
    fun `all geometry entry points fit the cell at rest and completion`() {
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
        val containers =
            buildMap<String, @Composable (SwingModifier) -> Unit> {
                for (scoped in listOf(false, true)) {
                    // Scale requires a Foundation parent.
                    for ((kind, enter) in if (scoped) enters + ("scaleIn" to scaleIn(spec())) else enters) {
                        this["Visibility scoped=$scoped, $kind"] = { modifier ->
                            VisibilityCell(modifier, entering = true, scoped = scoped, enter = enter)
                        }
                    }
                }
            }
        assertCellSizes(containers)
    }

    @Test
    fun `size keeping exits fit the cell halfway through the transition`() {
        val containers =
            mapOf<String, @Composable (SwingModifier) -> Unit>(
                "fadeOut" to { modifier -> VisibilityCell(modifier, entering = false, exit = fadeOut(spec())) },
                "slideOutHorizontally" to { modifier ->
                    VisibilityCell(modifier, entering = false, exit = slideOutHorizontally(spec()))
                },
                "slideOutVertically" to { modifier ->
                    VisibilityCell(modifier, entering = false, exit = slideOutVertically(spec()))
                },
            )
        assertCellSizes(containers, partWay = true)
    }

    @Test
    fun `replacement and animateContentSize fit the cell at rest and after growth`() {
        val containers =
            buildMap<String, @Composable (SwingModifier) -> Unit> {
                this["Content at rest"] = { modifier -> AnimatedContent(true, modifier) { if (it) Content() } }
                for ((kind, transform) in mapOf(
                    "fade" to (fadeIn(spec()) togetherWith fadeOut(spec())),
                    "expand and shrink" to (expandIn(spec()) togetherWith shrinkOut(spec())),
                )) {
                    this["Content after $kind"] = { modifier -> ReplacementCell(modifier, transform) }
                }
                for (growing in listOf(false, true)) {
                    this["animateContentSize growing=$growing"] = { modifier ->
                        Box(modifier) {
                            if (growing) {
                                val grown = rememberGrown()
                                Column(SwingModifier.animateContentSize(spec())) {
                                    Content()
                                    if (grown) Label("Added")
                                }
                            } else {
                                Box(SwingModifier.animateContentSize(spec())) { Content() }
                            }
                        }
                    }
                }
            }
        assertCellSizes(containers)
    }

    private fun assertCellSizes(
        containers: Map<String, @Composable (SwingModifier) -> Unit>,
        partWay: Boolean = false,
    ) {
        val cell = swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL")
        for ((name, container) in containers) {
            runComposeSwingTest {
                mainClock.autoAdvance = !partWay
                setContent { cell(WIDTH) { modifier -> container(modifier.testTag(CONTAINER)) } }
                if (partWay) mainClock.advanceTimeBy((TRANSITION_MILLIS / 2).milliseconds)
                awaitIdle()
                val holder = onNodeWithTag(CONTAINER).fetch<JComponent>()
                cyclesUntilStable(holder)
                assertEquals(WIDTH, holder.width, name)
                assertEquals(AtCellWidth, onNodeWithTag(CONTENT).fetch<JComponent>().size, name)
            }
        }
    }

    /** Every enter lays the content out within the rectangle the cell grants the container, and fits at completion. */
    @Test
    fun `delayed entry lays the content out within the granted rectangle and fits at completion`() {
        val enters =
            mapOf(
                "expandIn" to expandIn(delayedSpec()),
                "expandVertically" to expandVertically(delayedSpec()),
                "expandHorizontally" to expandHorizontally(delayedSpec()),
                "fadeIn" to fadeIn(delayedSpec()),
            )
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent { EnteringCell(visible, enter) }
                mainClock.autoAdvance = false
                visible = true
                mainClock.advanceTimeBy((TRANSITION_MILLIS + TRANSITION_MILLIS / 2).milliseconds)
                awaitIdle()
                val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                assertEquals(WIDTH, container.width, "$kind: precondition: the cell grants the container its width")
                if (kind != "fadeIn") {
                    assertTrue(container.size != AtCellWidth, "$kind: precondition: the size change was under way")
                }
                assertEquals(atAspectWithin(container.size), onNodeWithTag(CONTENT).fetch().size, kind)
                mainClock.autoAdvance = true
                awaitIdle()
                val settled = onNodeWithTag(CONTAINER).fetch<JComponent>()
                assertEquals(AtCellWidth, onNodeWithTag(CONTENT).fetch().size, kind)
                assertEquals(WIDTH, settled.width, kind)
                // Content wider than the cell leaves the cell short of its preferred width, so the cell is laid out
                // by the container's minimum height, which is read at the content's natural width.
                val natural = (settled as Constrainable).maxIntrinsicWidth(Constraints.Infinity)
                assertTrue(natural > WIDTH, "$kind: precondition: the cell is short of the preferred width: $natural")
                assertEquals(AtNaturalWidth.height, settled.height, kind)
                assertEquals(settled.minimumSize.height, settled.height, kind)
            }
        }
    }

    /** The content fits the cell's grant on every frame, from no room or from the container's minimum size. */
    @Test
    fun `zero cell entry preserves the minimum size and fits the granted rectangle on every frame`() {
        val enters = mapOf("expandIn" to expandIn(delayedSpec()), "expandVertically" to expandVertically(delayedSpec()))
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent { EnteringCell(visible, enter) }
                mainClock.autoAdvance = false
                visible = true
                val fitted = mutableListOf<Pair<Dimension, Dimension>>()
                val frames =
                    List(2 * TRANSITION_MILLIS / 16 + 2) { frame ->
                        driveOneFrame()
                        val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                        if (kind == "expandIn" && frame == 2) {
                            assertEquals(AtCellWidth.height, (container as Constrainable).minIntrinsicHeight(WIDTH))
                        }
                        if (kind == "expandVertically") {
                            assertEquals(AtNaturalWidth, container.size, "$kind: frame $frame")
                        }
                        if (container.width > 0 && container.height > 0) {
                            fitted += container.size to onNodeWithTag(CONTENT).fetch().size
                        }
                        container.size
                    }
                if (kind == "expandIn") {
                    assertTrue(fitted.size < frames.size, "$kind: precondition: the container began without room")
                }
                for ((granted, size) in fitted) {
                    assertEquals(atAspectWithin(granted), size, "$kind: the content in $granted")
                }
                val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                assertEquals(0, cyclesUntilStable(container), "a paint requests no layout")
            }
        }
    }

    @Test
    fun `visibility and replacement content fill the space a cell narrowed during a size change grants`() {
        val transform = expandIn(spec()) togetherWith shrinkOut(spec())
        val containers: Map<String, @Composable (SwingModifier) -> Unit> =
            buildMap {
                for (entering in listOf(true, false)) {
                    this["Visibility entering=$entering"] = { modifier ->
                        VisibilityCell(
                            modifier,
                            entering = entering,
                            enter = if (entering) expandIn(spec()) else EnterTransition.None,
                            exit = if (entering) ExitTransition.None else shrinkOut(spec()),
                        ) { TallContent() }
                    }
                    this["Content entering=$entering"] = { modifier ->
                        ReplacementCell(modifier, transform, entering) { TallContent() }
                    }
                }
            }
        val cell = swingParentSlots(emptyList()).getValue("GridBagLayout fill HORIZONTAL")
        for ((name, container) in containers) {
            runComposeSwingTest {
                var width by mutableIntStateOf(WIDTH)
                mainClock.autoAdvance = false
                setContent { cell(width) { modifier -> container(modifier.testTag(CONTAINER)) } }
                mainClock.advanceTimeBy((TRANSITION_MILLIS / 4).milliseconds)
                awaitIdle()
                width = InNarrowedCell.width
                // Applying the new width moves no time, so the animated width remains the wider one.
                mainClock.advanceTimeBy(Duration.ZERO, ignoreFrameDuration = true)
                awaitIdle()
                cyclesUntilStable(onNodeWithTag(CONTAINER).fetch<JComponent>())
                val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
                val content = onNodeWithTag(CONTENT).fetch<JComponent>()
                assertEquals(InNarrowedCell.width, container.width, "$name: precondition: the cell was narrowed")
                assertTrue(container.height > 0, "$name: precondition: the container has room: ${container.size}")
                assertEquals(
                    Dimension(container.width, minOf(content.preferredSize.height, container.height)),
                    content.size,
                    name,
                )
            }
        }
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
}

@Composable
private fun VisibilityCell(
    modifier: SwingModifier,
    entering: Boolean,
    scoped: Boolean = false,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    content: @Composable ConstrainedScope.() -> Unit = { Content() },
) {
    val state = remember { MutableTransitionState(!entering).apply { targetState = entering } }
    if (scoped) {
        Box(modifier) { AnimatedVisibility(state, enter = enter, exit = exit, content = content) }
    } else {
        AnimatedVisibility(state, modifier, enter = enter, exit = exit, content = content)
    }
}

@Composable
private fun ReplacementCell(
    modifier: SwingModifier,
    transform: ContentTransform,
    entering: Boolean = true,
    content: @Composable ConstrainedScope.() -> Unit = { Content() },
) {
    AnimatedContent(rememberGrown(), modifier, transitionSpec = { transform }) {
        if (it == entering) content() else Label("Other content")
    }
}

private fun <T> spec() = tween<T>(TRANSITION_MILLIS)

private fun <T> delayedSpec() = tween<T>(TRANSITION_MILLIS, delayMillis = TRANSITION_MILLIS)

/** A label with no minimum size, wider than the cell, at a 16:9 ratio. */
@Composable
private fun ConstrainedScope.Content() {
    Label("", modifier = SwingModifier.testTag(CONTENT).aspectRatio(16f / 9f).preferredSize(2 * WIDTH, WIDTH))
}

/** The size of a cell narrowed below the animated width the container returns, at [TallContent]'s preferred height. */
private val InNarrowedCell = Dimension(WIDTH / 8, WIDTH / 2)

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
