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
import kotlin.math.roundToInt
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
internal class LayoutScene<S>(
    val content: @Composable (Boolean) -> Unit,
    val observe: ComposeSwingTest.() -> LayoutObservation<S>,
    val assertSettled: (completion: S) -> Unit,
)

/**
 * Asserts that [layouts] layouts in which nothing changed leave the history of [resizes] as it is without them.
 *
 * The layouts come between frames, before a resize is placed, and before the first frame. The history without
 * them ends at the last size of [resizes], in the completion [LayoutScene.assertSettled] states.
 */
internal fun <S> assertUnchangedLayoutsChangeNothing(
    resizes: FrameResizes,
    layouts: Int,
    newScene: () -> LayoutScene<S>,
) {
    val scene = newScene()
    val acrossFrames = historyAcrossFrames(scene, resizes, layoutsBetweenFrames = 0)
    val beforePlacement = historyBeforePlacement(newScene(), resizes, layouts = 0)
    assertEquals(resizes.perFrame.last().last(), acrossFrames.last().size, "the size the history ends at")
    scene.assertSettled(acrossFrames.last().completion)

    assertEquals(
        acrossFrames,
        historyAcrossFrames(newScene(), resizes, layoutsBetweenFrames = layouts),
        "$layouts unchanged layouts between frames",
    )
    assertEquals(
        beforePlacement,
        historyBeforePlacement(newScene(), resizes, layouts),
        "$layouts unchanged layouts before placement",
    )
    assertEquals(
        acrossFrames,
        historyAcrossFrames(newScene(), resizes, layoutsBetweenFrames = 0, layoutsBeforeFirstFrame = layouts),
        "$layouts unchanged layouts before the first frame",
    )
}

/** A layout in which nothing changed: [child]'s parent is invalidated and the root is laid out. */
internal fun ComposeSwingTest.layOutAgain(child: Component) {
    child.parent.invalidate()
    root.validate()
}

/** Advances the clock by [duration] in a single frame, and waits for the work that frame started. */
internal suspend fun ComposeSwingTest.advanceOneFrameBy(duration: Duration) {
    mainClock.advanceTimeBy(duration, ignoreFrameDuration = true)
    awaitIdle()
}

/**
 * Asserts what holds 1 ms before [deadline], a composition time, and what holds at it.
 *
 * [stillRunning] states the first and [ended] the second.
 */
internal suspend fun ComposeSwingTest.assertEndsAt(
    deadline: Duration,
    stillRunning: () -> Unit,
    ended: () -> Unit,
) {
    advanceOneFrameBy(deadline - 1.milliseconds - mainClock.currentTime)
    stillRunning()
    advanceOneFrameBy(1.milliseconds)
    ended()
}

/** [layouts] sizes, each larger than the one before. */
internal fun growingSizes(layouts: Int): List<Dimension> = List(layouts) { Dimension(160 + it * 8, 80 + it * 4) }

/** Gives [body] each of [sizes] in turn, waiting for idle after each, and asserts the size it takes. */
internal suspend fun ComposeSwingTest.resizeBeforeFrame(
    body: JLabel,
    sizes: List<Dimension>,
) {
    for (size in sizes) {
        body.preferredSize = size
        body.revalidate()
        awaitIdle()
        val parent = requireNotNull(body.parent)
        assertEquals(
            Dimension(minOf(size.width, parent.width), minOf(size.height, parent.height)),
            body.size,
        )
    }
}

/**
 * Asserts the curve of a 1000 ms linear tween on a scene whose `body` was resized [layouts] times before the frame
 * that starts it.
 *
 * 680 ms after that frame [allocatedWidth] has covered 68% of the way from [startWidth] to the body's size, and it
 * reaches that size at the deadline, 1000 ms after the frame, and not 1 ms before. [completion] is told whether
 * the animation has ended, and the size the body has.
 */
internal suspend fun ComposeSwingTest.assertRetargetedCurve(
    layouts: Int,
    allocatedWidth: () -> Int,
    startWidth: Int = 0,
    completion: (ended: Boolean, target: Dimension) -> Unit,
) {
    val body = onNodeWithTag("body").fetch<JLabel>()
    resizeBeforeFrame(body, growingSizes(layouts))
    driveOneFrame()
    val firstFrame = mainClock.currentTime
    advanceOneFrameBy(300.milliseconds)
    val target = body.preferredSize
    advanceOneFrameBy(380.milliseconds)
    assertEquals(
        (startWidth + (target.width - startWidth) * 0.68f).roundToInt(),
        allocatedWidth(),
        "the width 680 ms into the tween",
    )
    assertEndsAt(
        firstFrame + 1000.milliseconds,
        stillRunning = { completion(false, target) },
        ended = {
            assertEquals(target.width, allocatedWidth(), "the width at the deadline")
            completion(true, target)
        },
    )
}

private fun <S> historyAcrossFrames(
    scene: LayoutScene<S>,
    resizes: FrameResizes,
    layoutsBetweenFrames: Int,
    layoutsBeforeFirstFrame: Int = 0,
): List<LayoutObservation<S>> {
    val observe = scene.observe
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
            scene.content(changed)
            resizeOnNextFrame.Install()
        }
        changed = true
        driveOneFrame()
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
        driveOneFrame()
        history += observe()
        for (sizes in resizes.perFrame) {
            resizeOnNextFrame.sizes = sizes
            advanceOneFrameBy(300.milliseconds)
            history += observe()
        }
        for (elapsed in listOf(1, 199, 200, 200, 200, 199, 1, 200, 200, 200)) {
            advanceOneFrameBy(elapsed.milliseconds)
            history += observe()
        }
    }
    return history
}

private fun <S> historyBeforePlacement(
    scene: LayoutScene<S>,
    resizes: FrameResizes,
    layouts: Int,
): List<LayoutObservation<S>> {
    val observe = scene.observe
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
            scene.content(changed)
            resizeOnNextFrame.Install()
        }
        changed = true
        repeat(2) { driveOneFrame() }
        body = onNodeWithTag("body").fetch<JLabel>()
        for (sizes in resizes.perFrame) {
            resizeOnNextFrame.sizes = sizes
            advanceOneFrameBy(300.milliseconds)
            history += observe()
        }
        repeat(8) {
            advanceOneFrameBy(200.milliseconds)
            history += observe()
        }
    }
    return history
}

fun <T> ComposeSwingTest.retargetWithoutFrame(
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
