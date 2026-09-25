package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.animation.core.snap
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.ScrollState
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.core.MotionDurationScale
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Point
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * How the suspending scroll extensions move a pane: they carry it to a target over several frames, they
 * aim from where the pane stands when they are called, and every frame they write is coerced to what the
 * content reaches.
 *
 * A scroll is entered the way a caller enters one - launched from the composition's own scope, so the
 * test body stays free to drive the frames it travels on - and driven either by the automatic frames a
 * settle gate sends or, where the travel itself is the subject, a frame at a time.
 */
class ScrollStateAnimationTest {
    @Test
    fun `an animated scroll down the pane is coerced to what the content reaches`() =
        runComposeSwingTest {
            assertCoercedToWhatTheContentReaches(
                Axis(
                    position = { it.y },
                    crossPosition = { it.x },
                    max = { it.maxY },
                    animateTo = { state, y -> state.animateScrollTo(y = y) },
                    animateBy = { state, dy -> state.animateScrollBy(dy = dy) },
                ),
            )
        }

    @Test
    fun `an animated scroll across the pane is coerced to what the content reaches`() =
        runComposeSwingTest {
            assertCoercedToWhatTheContentReaches(
                Axis(
                    position = { it.x },
                    crossPosition = { it.y },
                    max = { it.maxX },
                    animateTo = { state, x -> state.animateScrollTo(x = x) },
                    animateBy = { state, dx -> state.animateScrollBy(dx = dx) },
                ),
            )
        }

    @Test
    fun `an animated scroll travels to its target across frames rather than jumping to it`() =
        runComposeSwingTest {
            // The frames are the subject here, so the test owns them from before the pane is composed.
            mainClock.autoAdvance = false
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            val target = state.maxY

            val job = fixture.launchScroll { animateScrollTo(y = target) }
            val path = mutableListOf<Int>()
            while (!job.isCompleted && path.size < FRAME_BUDGET) {
                driveOneFrame()
                path += state.y
            }

            assertTrue(job.isCompleted, "the scroll must reach its target within $FRAME_BUDGET frames: $path")
            assertEquals(target, path.last(), "the frame it ends on must stand at the target: $path")
            val positions = path.distinct()
            assertEquals(
                positions.sorted(),
                positions,
                "the pane must travel toward its target and never back: $path",
            )
            assertTrue(
                positions.count { it in 1 until target } >= 2,
                "the pane must stand between the start and the target on several frames, rather than " +
                    "arriving on the first one: $path",
            )
        }

    @Test
    fun `an animated scroll by a delta moves the pane by that delta`() =
        runComposeSwingTest {
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            val step = state.maxY / 4
            assertTrue(step > 0, "precondition: the content leaves room for two steps of ${state.maxY / 4}")

            var travelled: Point? = null
            runScrollToCompletion(fixture.launchScroll { travelled = animateScrollBy(dy = step) })
            assertEquals(step, state.y, "a delta moves the pane by that much from where it stood")
            assertEquals(Point(0, step), travelled, "and the call returns how far it moved")

            runScrollToCompletion(fixture.launchScroll { travelled = animateScrollBy(dy = step) })
            assertEquals(2 * step, state.y, "and the next one from where the first left it")
            assertEquals(Point(0, step), travelled, "the second call returns its own consumed distance")
            assertEquals(0, state.x, "the axis neither call names must stand still")

            runScrollToCompletion(fixture.launchScroll { travelled = animateScrollBy(dy = state.maxY) })
            assertEquals(state.maxY, state.y, "a delta past the end parks the pane at the end")
            assertEquals(
                Point(0, state.maxY - 2 * step),
                travelled,
                "a delta past the end returns only the part the pane scrolled",
            )
        }

    @Test
    fun `an animated scroll by a delta preempting another aims from where that one stood`() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            val half = state.maxY / 2
            val quarter = state.maxY / 4
            assertTrue(quarter > 0, "precondition: the content leaves room for both travels")

            val first = fixture.launchScroll { animateScrollBy(dy = half) }
            mainClock.advanceTimeUntil { state.y in 1 until half }
            val standing = state.y

            val second = fixture.launchScroll { animateScrollBy(dy = quarter) }
            // No frame is sent here, so the pane cannot move between the delta being read and the
            // handover: what the second scroll aims from is exactly where the first one stood.
            awaitIdle()
            assertTrue(first.isCancelled, "the second scroll must take the position from the first")

            mainClock.advanceTimeUntil(timeout = 10.seconds) { second.isCompleted }
            assertEquals(
                standing + quarter,
                state.y,
                "the second scroll must move by its delta from where the pane stood when it was called, " +
                    "not from where the first one was aiming",
            )
        }

    @Test
    fun `a delta beyond the integer range still scrolls forward to the content end`() =
        runComposeSwingTest {
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            runScrollToCompletion(fixture.launchScroll { animateScrollTo(10, 10, snap()) })
            assertEquals(Point(10, 10), viewportPosition(), "the pane starts away from either edge")

            var consumed: Point? = null
            runScrollToCompletion(
                fixture.launchScroll {
                    consumed = animateScrollBy(Int.MAX_VALUE, Int.MAX_VALUE, snap())
                },
            )

            assertEquals(Point(state.maxX, state.maxY), viewportPosition(), "positive deltas reach the end")
            assertEquals(Point(state.maxX - 10, state.maxY - 10), consumed, "only the distance traveled is returned")
        }

    @Test
    fun `a delta below the integer range stops an unbound state at the integer limit`() =
        runComposeSwingTest {
            lateinit var state: ScrollState
            lateinit var scope: CoroutineScope
            setContent {
                state = rememberScrollState(x = -10, y = -20)
                scope = rememberCoroutineScope()
            }
            var consumed: Point? = null
            runScrollToCompletion(
                scope.launch {
                    consumed = state.animateScrollBy(Int.MIN_VALUE, Int.MIN_VALUE, snap())
                },
            )

            assertEquals(
                Point(Int.MIN_VALUE, Int.MIN_VALUE),
                Point(state.x, state.y),
                "negative deltas keep their direction",
            )
            assertEquals(Point(Int.MIN_VALUE + 10, Int.MIN_VALUE + 20), consumed, "the saturated distance is returned")
        }

    @Test
    fun `a supplied snap spec moves both viewport axes on the first frame`() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            val target = Point(state.maxX / 2, state.maxY / 2)
            val job = fixture.launchScroll { animateScrollTo(target.x, target.y, snap()) }
            awaitIdle()
            assertTrue(state.isScrollInProgress, "the animation holds the position before its first frame")
            assertEquals(Point(0, 0), viewportPosition(), "no movement happens before a frame")

            driveOneFrame()

            assertEquals(target, viewportPosition(), "the supplied snap spec reaches both coordinates in one frame")
            assertTrue(job.isCompleted, "the snap animation completes on that frame")
            assertFalse(job.isCancelled, "the animation completes normally")
            assertFalse(state.isScrollInProgress, "completion releases the position")
        }

    @Test
    fun `a supplied snap spec moves a delta on both axes on the first frame`() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            val fixture = paneWithRoomToScroll()
            var consumed: Point? = null
            val job = fixture.launchScroll { consumed = animateScrollBy(10, 20, snap()) }
            awaitIdle()
            assertEquals(Point(0, 0), viewportPosition(), "the delta waits for its first frame")

            driveOneFrame()

            assertEquals(Point(10, 20), viewportPosition(), "the supplied snap spec moves both axes in one frame")
            assertEquals(Point(10, 20), consumed, "the completed call returns both consumed distances")
            assertTrue(job.isCompleted, "the delta animation completes on that frame")
            assertFalse(job.isCancelled, "the delta animation completes normally")
        }

    @Test
    fun `negative deltas return the consumed distance on both axes`() =
        runComposeSwingTest {
            val fixture = paneWithRoomToScroll()
            runScrollToCompletion(fixture.launchScroll { animateScrollTo(30, 40, snap()) })
            assertEquals(Point(30, 40), viewportPosition(), "the pane starts with room to scroll backward")

            var consumed: Point? = null
            runScrollToCompletion(fixture.launchScroll { consumed = animateScrollBy(-10, -20, snap()) })
            assertEquals(Point(20, 20), viewportPosition(), "both axes move backward by their requested distances")
            assertEquals(Point(-10, -20), consumed, "the returned distance keeps the negative direction")

            runScrollToCompletion(fixture.launchScroll { consumed = animateScrollBy(-1_000, -1_000, snap()) })
            assertEquals(Point(0, 0), viewportPosition(), "a delta beyond the beginning stops at the beginning")
            assertEquals(Point(-20, -20), consumed, "only the distance to the beginning is returned")
        }

    @Test
    fun `cancelling the caller stops the animation and releases the position`() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            val job = fixture.launchScroll { animateScrollTo(y = state.maxY) }
            mainClock.advanceTimeUntil { state.y in 1 until state.maxY }
            assertTrue(state.isScrollInProgress, "the animation is moving when its caller is cancelled")

            job.cancel()
            awaitIdle()
            val stopped = viewportPosition()
            repeat(5) { driveOneFrame() }

            assertTrue(job.isCancelled, "the caller remains cancelled")
            assertFalse(state.isScrollInProgress, "cancellation releases the position")
            assertEquals(stopped, viewportPosition(), "later frames cannot continue the cancelled animation")
        }

    @Test
    fun `with motion switched off an animated scroll arrives on its first frame`() =
        runComposeSwingTest(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor: Float get() = 0f
                },
        ) {
            mainClock.autoAdvance = false
            val fixture = paneWithRoomToScroll()
            val state = fixture.state
            val target = state.maxY

            val job = fixture.launchScroll { animateScrollTo(y = target) }
            awaitIdle()
            assertEquals(0, state.y, "the scroll has taken the position but has had no frame to move on")

            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertEquals(target, state.y, "one frame must carry a scroll with no duration all the way")
            assertTrue(job.isCompleted, "and end it there")
        }
}

/**
 * One of the two axes a [ScrollState] holds, so a case written once runs down the pane and across it.
 *
 * Each axis names the coordinate it moves and the one it leaves alone: a call that names a single axis
 * takes the other's position as its default, so the pane stands still across the axis it was not asked
 * about.
 */
private class Axis(
    val position: (ScrollState) -> Int,
    val crossPosition: (ScrollState) -> Int,
    val max: (ScrollState) -> Int,
    val animateTo: suspend (ScrollState, Int) -> Unit,
    val animateBy: suspend (ScrollState, Int) -> Unit,
)

/**
 * Asserts that every animated scroll lands inside the content on [axis]: a target before the start of
 * the content parks at the start, a target past the end parks at the end, and a delta that would carry
 * the pane past either edge leaves it standing there.
 */
private suspend fun ComposeSwingTest.assertCoercedToWhatTheContentReaches(axis: Axis) {
    val fixture = paneWithRoomToScroll()
    val state = fixture.state
    val max = axis.max(state)

    fun assertStandsAt(
        expected: Int,
        message: String,
    ) {
        assertEquals(expected, axis.position(state), message)
        assertEquals(0, axis.crossPosition(state), "the axis the call does not name must stand still")
    }

    assertStandsAt(0, "the pane starts at the start of its content")

    runScrollToCompletion(fixture.launchScroll { axis.animateTo(this, -100) })
    assertStandsAt(0, "a target before the start of the content lands at the start")

    runScrollToCompletion(fixture.launchScroll { axis.animateBy(this, -100) })
    assertStandsAt(0, "and a delta that would carry the pane before it leaves it there")

    runScrollToCompletion(fixture.launchScroll { axis.animateTo(this, max) })
    assertStandsAt(max, "the largest position the content reaches is reached exactly")

    runScrollToCompletion(fixture.launchScroll { axis.animateTo(this, max + 1_000) })
    assertStandsAt(max, "a target past the end of the content parks at the end")

    runScrollToCompletion(fixture.launchScroll { axis.animateBy(this, 100) })
    assertStandsAt(max, "and a delta that would carry the pane past it leaves it there")
}

/**
 * The state a pane renders, together with the composition's own coroutine scope - the scope a caller
 * launches a scroll from, and the one that puts it on the Event Dispatch Thread.
 */
private class ScrollFixture(
    val state: ScrollState,
    val scope: CoroutineScope,
)

/**
 * Composes a pane smaller than its content on both axes, so there is room to scroll each way, and
 * settles it on one frame so the viewport's metrics are the laid-out ones.
 */
private suspend fun ComposeSwingTest.paneWithRoomToScroll(): ScrollFixture {
    var state: ScrollState? = null
    var scope: CoroutineScope? = null
    setContent {
        val declared = rememberScrollState()
        state = declared
        scope = rememberCoroutineScope()
        ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = declared) {
            Viewport { Label("body", modifier = SwingModifier.preferredSize(300, 400)) }
        }
    }
    driveOneFrame()
    val fixture =
        ScrollFixture(
            state ?: error("the scroll pane did not compose"),
            scope ?: error("the composition handed back no scope"),
        )
    assertTrue(
        fixture.state.maxX > 0 && fixture.state.maxY > 0,
        "precondition: the pane must have room to scroll on both axes, but reaches " +
            "${fixture.state.maxX} by ${fixture.state.maxY}",
    )
    return fixture
}

/** Launches [block] from the composition's scope, the way a caller launches a scroll, without waiting. */
private fun ScrollFixture.launchScroll(block: suspend ScrollState.() -> Unit): Job = scope.launch { state.block() }

private fun ComposeSwingTest.viewportPosition(): Point = onNodeOfType<JScrollPane>().fetch().viewport.viewPosition

/**
 * Drives [job] to its end under automatic frames and asserts it got there rather than being ended: every
 * write a scroll makes stands inside the content, so no layout pass ever corrects one and nothing
 * preempts the scroll.
 */
private suspend fun ComposeSwingTest.runScrollToCompletion(job: Job) {
    awaitIdle()
    // The settle gate carries the whole animation, frame by frame; the deadline bounds a scroll that
    // never ends rather than a scroll that is merely long.
    waitUntil(timeout = 10.seconds) { job.isCompleted }
    assertFalse(job.isCancelled, "a scroll that stays inside the content must run to its end, not be ended")
}

/** Frames enough for a scroll to arrive, past which a travel that never ends is a failure. */
private const val FRAME_BUDGET = 300
