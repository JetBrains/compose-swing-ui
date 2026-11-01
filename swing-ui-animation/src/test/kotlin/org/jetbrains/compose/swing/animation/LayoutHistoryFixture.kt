package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.provider.Arguments
import java.awt.Component
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The sizes a body takes, one list per frame.
 *
 * The sizes of one list follow one another with no frame between.
 */
enum class FrameResizes(
    val perFrame: List<List<Dimension>>,
) {
    OneResizePerFrame(listOf(listOf(Dimension(320, 80)), listOf(Dimension(120, 60)))),
    SeveralResizesPerFrame(
        listOf(
            listOf(Dimension(240, 40), Dimension(320, 40)),
            listOf(Dimension(400, 40), Dimension(480, 40), Dimension(320, 40)),
        ),
    ),
    SeveralResizesPerFrameEndingNarrow(
        listOf(
            listOf(Dimension(240, 40), Dimension(320, 40), Dimension(160, 40)),
            listOf(Dimension(400, 40), Dimension(480, 40), Dimension(160, 40)),
        ),
    ),

    /** Settles at 320, then goes to 120 and back to 320 within one frame interval, while the first tween still runs. */
    BounceWithinOneFrame(
        listOf(
            listOf(Dimension(320, 40)),
            listOf(Dimension(120, 40), Dimension(320, 40)),
        ),
    ),
}

/** [FrameResizes] crossed with the numbers of unchanged layouts the invariance tests insert. */
internal object UnchangedLayoutCases {
    @JvmStatic
    fun cases(): List<Arguments> =
        FrameResizes.entries.flatMap { resizes -> listOf(1, 5, 12).map { layouts -> Arguments.of(resizes, layouts) } }
}

/** Names [UnchangedLayoutCases.cases] for a `@MethodSource`, which takes the class name of a static method. */
internal const val UNCHANGED_LAYOUT_CASES = "org.jetbrains.compose.swing.animation.UnchangedLayoutCases#cases"

/** What an unchanged-layout run records after a step: the size on screen and the state of the completion. */
internal data class LayoutObservation<S>(
    val size: Dimension,
    val completion: S,
)

/**
 * An animation that an unchanged-layout run drives.
 *
 * Its [content] animates once its flag is set, and has a label tagged `body` whose size the run changes. The run
 * records what [observe] reads, and [assertSettled] states the completion it ends in.
 */
internal class LayoutFixture<S>(
    val content: @Composable (Boolean) -> Unit,
    val observe: ComposeSwingTest.() -> LayoutObservation<S>,
    val assertSettled: (completion: S) -> Unit,
)

/**
 * Asserts that [layouts] layouts in which nothing changed leave the history of [resizes] as it is without them.
 *
 * The layouts come between frames, before a resize is placed, and before the first frame. The history without
 * them ends at the last size of [resizes], in the completion [LayoutFixture.assertSettled] states.
 */
internal fun <S> assertUnchangedLayoutsChangeNothing(
    resizes: FrameResizes,
    layouts: Int,
    newFixture: () -> LayoutFixture<S>,
) {
    val fixture = newFixture()
    val acrossFrames = historyAcrossFrames(fixture, resizes, layoutsBetweenFrames = 0)
    val beforePlacement = historyBeforePlacement(newFixture(), resizes, layouts = 0)
    assertEquals(resizes.perFrame.last().last(), acrossFrames.last().size, "the size the history ends at")
    fixture.assertSettled(acrossFrames.last().completion)

    assertEquals(
        acrossFrames,
        historyAcrossFrames(newFixture(), resizes, layoutsBetweenFrames = layouts),
        "$layouts unchanged layouts between frames",
    )
    assertEquals(
        beforePlacement,
        historyBeforePlacement(newFixture(), resizes, layouts),
        "$layouts unchanged layouts before placement",
    )
    assertEquals(
        acrossFrames,
        historyAcrossFrames(newFixture(), resizes, layoutsBetweenFrames = 0, layoutsBeforeFirstFrame = layouts),
        "$layouts unchanged layouts before the first frame",
    )
}

/** A layout in which nothing changed: [child]'s parent is invalidated and the root is laid out. */
internal fun ComposeSwingTest.layOutAgain(child: Component) {
    child.parent.invalidate()
    root.validate()
}

/**
 * Asserts what holds 1 ms before [deadline], a composition time, and what holds at it.
 *
 * [stillRunning] states the first and [ended] the second.
 */
internal fun ComposeSwingTest.assertEndsAt(
    deadline: Duration,
    stillRunning: () -> Unit,
    ended: () -> Unit,
) {
    mainClock.advanceTimeByFrame(deadline - 1.milliseconds - mainClock.currentTime)
    stillRunning()
    mainClock.advanceTimeByFrame(1.milliseconds)
    ended()
}

private fun <S> historyAcrossFrames(
    fixture: LayoutFixture<S>,
    resizes: FrameResizes,
    layoutsBetweenFrames: Int,
    layoutsBeforeFirstFrame: Int = 0,
): List<LayoutObservation<S>> {
    val observe = fixture.observe
    val history = mutableListOf<LayoutObservation<S>>()
    runComposeSwingTest {
        mainClock.autoAdvance = false
        var changed by mutableStateOf(false)
        var body: JLabel? = null
        val resizeOnNextFrame =
            ResizeOnNextFrame { target ->
                history += retargetWithoutFrame(checkNotNull(body), target, layoutsBetweenFrames, observe)
            }
        setContent {
            fixture.content(changed)
            resizeOnNextFrame.Install()
        }
        changed = true
        mainClock.advanceTimeByFrame()
        val child = onNodeWithTag("body").fetch<JLabel>()
        body = child
        val beforeFirstFrame = observe()
        repeat(layoutsBeforeFirstFrame) {
            layOutAgain(child)
            assertEquals(
                beforeFirstFrame,
                observe(),
                "$layoutsBeforeFirstFrame unchanged layouts before the first frame",
            )
        }
        history += observe()
        mainClock.advanceTimeByFrame()
        history += observe()
        for (sizes in resizes.perFrame) {
            resizeOnNextFrame.sizes = sizes
            mainClock.advanceTimeByFrame(300.milliseconds)
            history += observe()
        }
        for (elapsed in listOf(1, 199, 200, 200, 200, 199, 1, 200, 200, 200)) {
            mainClock.advanceTimeByFrame(elapsed.milliseconds)
            history += observe()
        }
    }
    return history
}

private fun <S> historyBeforePlacement(
    fixture: LayoutFixture<S>,
    resizes: FrameResizes,
    layouts: Int,
): List<LayoutObservation<S>> {
    val observe = fixture.observe
    val history = mutableListOf<LayoutObservation<S>>()
    runComposeSwingTest {
        mainClock.autoAdvance = false
        var changed by mutableStateOf(false)
        var body: JLabel? = null
        val resizeOnNextFrame =
            ResizeOnNextFrame { target ->
                val child = checkNotNull(body)
                repeat(layouts) { layOutAgain(child) }
                child.preferredSize = target
                child.revalidate()
                root.validate()
            }
        setContent {
            fixture.content(changed)
            resizeOnNextFrame.Install()
        }
        changed = true
        repeat(2) { mainClock.advanceTimeByFrame() }
        body = onNodeWithTag("body").fetch<JLabel>()
        for (sizes in resizes.perFrame) {
            resizeOnNextFrame.sizes = sizes
            mainClock.advanceTimeByFrame(300.milliseconds)
            history += observe()
        }
        repeat(8) {
            mainClock.advanceTimeByFrame(200.milliseconds)
            history += observe()
        }
    }
    return history
}

private fun <T> ComposeSwingTest.retargetWithoutFrame(
    child: JLabel,
    target: Dimension,
    extraLayouts: Int,
    observe: ComposeSwingTest.() -> T,
): T {
    layOutAgain(child)
    val standing = observe()
    repeat(extraLayouts) {
        layOutAgain(child)
        assertEquals(
            standing,
            observe(),
            "$extraLayouts unchanged layouts before retarget to $target at ${mainClock.currentTime}",
        )
    }
    child.preferredSize = target
    child.revalidate()
    root.validate()
    val retargeted = observe()
    assertEquals(
        standing,
        retargeted,
        "$extraLayouts unchanged layouts: retarget to $target at ${mainClock.currentTime}",
    )
    return retargeted
}

/** Applies [resize] to each size in [sizes] inside the next frame, with no frame between the sizes. */
private class ResizeOnNextFrame(
    private val resize: (Dimension) -> Unit,
) {
    var sizes = emptyList<Dimension>()

    @Composable
    fun Install() {
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos {
                    sizes.forEach(resize)
                    sizes = emptyList()
                }
                // Re-register after the animations so a retarget sees this frame's value and velocity.
                yield()
            }
        }
    }
}
