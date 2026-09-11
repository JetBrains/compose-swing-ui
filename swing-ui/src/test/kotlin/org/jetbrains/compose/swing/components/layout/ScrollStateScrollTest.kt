package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.util.concurrent.Executors
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ScrollState.scroll], the block that holds the pane's position while it moves it
 * over time. The module ships no animation engine, so each block is driven by hand: it waits on
 * [withFrameNanos] and the test sends the frames, which puts every move and every preemption between two
 * frames the test chose.
 *
 * A block is entered the way a caller enters one - launched from the composition's own scope, so it runs
 * on the event dispatch thread - and what the tests then drive is the one rule the contract stands on:
 * only a move made from inside a block is the block's, and every other move of the position ends it.
 */
class ScrollStateScrollTest {
    @Test
    fun aSecondScrollCancelsTheFirstAndStartsFromWhereItStood() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        val first = pane.scope.launch { state.scroll { stepDownEveryFrame(state) } }
        driveOneFrame()
        val stood = state.y
        assertEquals(20, stood, "the first block must have moved the pane before it is taken over")

        var startedAt = -1
        pane.scope.launch {
            state.scroll {
                withFrameNanos { }
                startedAt = state.y
                scrollTo(0, state.y + 20)
            }
        }
        awaitIdle()
        assertTrue(first.isCancelled, "the scroll that took the position must have ended the one holding it")
        assertTrue(
            state.isScrollInProgress,
            "and the scroll that took it holds it while the one it was taken from unwinds",
        )

        driveOneFrame()
        assertEquals(stood, startedAt, "the block that took over must start from where the one before it stood")
        assertEquals(stood + 20, state.y, "and move the pane on from there")
    }

    @Test
    fun aBlocksMovesAreCoercedToWhatTheContentReaches() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        assertEquals(0, state.y, "the pane starts at the top of its content")
        assertTrue(state.maxY > 0, "and has room to scroll")
        val max = state.maxY

        val asked = listOf(-100, -100, max, max + 1_000, max + 100)
        val landed = mutableListOf<Int>()
        val job =
            pane.scope.launch {
                state.scroll {
                    asked.forEach { target ->
                        withFrameNanos { }
                        scrollTo(0, target)
                        landed += state.y
                    }
                }
            }
        repeat(asked.size) { driveOneFrame() }

        assertEquals(
            listOf(0, 0, max, max, max),
            landed,
            "a move before the start of the content must land at the start and one past its end at the end",
        )
        assertFalse(job.isCancelled, "coercing a move is not a move the block did not make")
    }

    /**
     * Where Compose re-coerces the position and leaves the scroll running, here the layout pass that
     * corrects a position the shorter content no longer reaches is a move the block did not make, so it
     * ends the block.
     */
    @Test
    fun contentShrinkingUnderABlockEndsItAtWhatTheContentStillReaches() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        val job =
            pane.scope.launch {
                state.scroll {
                    withFrameNanos { }
                    scrollTo(0, state.maxY)
                    stepDownEveryFrame(state)
                }
            }
        driveOneFrame()
        val max = state.maxY
        assertEquals(max, state.y, "the block must have carried the pane to the end of its content")
        assertTrue(state.isScrollInProgress, "and must still be holding the position")

        pane.contentHeight = 400 - 100
        driveOneFrame()

        val shortened = state.maxY
        assertTrue(shortened < max, "the content that shrank must leave less room to scroll")
        assertEquals(shortened, state.y, "and the pane must stand at what that content still reaches")
        assertTrue(job.isCancelled, "the correction that moved the pane must have ended the block")
        assertFalse(state.isScrollInProgress, "so no scroll is in progress any more")
    }

    @Test
    fun aWheelGestureDuringABlockEndsItWhereTheUserScrolledTo() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        val job = pane.scope.launch { state.scroll { stepDownEveryFrame(state) } }
        driveOneFrame()
        assertEquals(20, state.y, "the block must be moving the pane when the user takes over")

        onNodeOfType<JScrollPane>().performMouseWheel(rotation = 3)

        val scrolledTo = state.y
        assertTrue(scrolledTo > 20, "the wheel must have moved the pane, but it stood at $scrolledTo")
        assertTrue(job.isCancelled, "the user's own scrolling must have ended the block")
        assertFalse(state.isScrollInProgress, "so no scroll is in progress any more")

        driveOneFrame()
        assertEquals(scrolledTo, state.y, "and the pane must stay where the user's scrolling left it")
    }

    /**
     * A position assigned on the state reaches the pane only after the state has written its own mirror,
     * so the change listener finds the two already agreeing: the setter is the only place left that can
     * see this move at all.
     */
    @Test
    fun assigningThePositionDuringABlockEndsIt() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        val job = pane.scope.launch { state.scroll { stepDownEveryFrame(state) } }
        driveOneFrame()
        assertEquals(20, state.y, "the block must be moving the pane when the position is assigned")

        state.y = 0
        awaitIdle()

        assertTrue(job.isCancelled, "assigning the position must have ended the block")
        assertFalse(state.isScrollInProgress, "so no scroll is in progress any more")
        driveOneFrame()
        assertEquals(0, state.y, "and the pane must stay at the position it was assigned")
    }

    @Test
    fun metricsChangingWithoutThePositionMovingLeavesTheBlockRunning() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        val job = pane.scope.launch { state.scroll { stepDownEveryFrame(state) } }
        driveOneFrame()
        assertEquals(20, state.y, "the block must be moving the pane when the content is resized")

        // Narrowing the content leaves the pane standing where it is: there is no position to correct, so
        // nothing the block did not do has happened to the position.
        pane.contentWidth = 300 - 100
        driveOneFrame()

        assertEquals(300 - 100, state.viewWidth, "the content the block moves must have resized")
        assertFalse(job.isCancelled, "resizing content that does not move the pane must leave the block alone")
        assertTrue(state.isScrollInProgress, "so the scroll must still be in progress")

        val stood = state.y
        driveOneFrame()
        assertEquals(stood + 20, state.y, "and the block must go on moving the pane")
    }

    /**
     * The handover takes the position before it ends the block holding it. Ending first would let the
     * second and third callers both read the first as the block to wait for, and both would go on to run
     * their own.
     */
    @Test
    fun threeOverlappingScrollsRunOnlyTheLastOnesBlock() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val ran = mutableListOf<String>()
        listOf("first", "second", "third").forEach { caller ->
            pane.scope.launch {
                pane.state.scroll {
                    withFrameNanos { }
                    ran += caller
                    scrollTo(0, 20)
                }
            }
        }
        driveOneFrame()

        assertEquals(listOf("third"), ran, "the pane must be moved by the last caller's block and no other")
        assertEquals(20, pane.state.y, "which is the block that moved it")
    }

    /**
     * A claimant waits only for the block it took the position from, so what carries the guarantee to the
     * blocks further back is the chain: each wait runs to its end whatever happens to the claimant making
     * it, and a block that completes therefore leaves every block before it already unwound. A wait that
     * gave way would let the last caller's block start while a block two places back is still writing,
     * and that write - its own block's move, so it preempts nothing - would move the pane under it.
     */
    @Test
    fun theLastBlockRunsOnlyOnceEveryBlockBeforeItHasFinishedWriting() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        pane.scope.launch {
            state.scroll {
                try {
                    awaitCancellation()
                } finally {
                    // Cleanup that suspends past the unwinding of the block that ended this one, so the
                    // move below is what a last caller that stopped waiting would find the pane moved by.
                    withContext(NonCancellable) {
                        yield()
                        yield()
                        scrollTo(0, 20)
                    }
                }
            }
        }
        pane.scope.launch { state.scroll { awaitCancellation() } }
        var stoodAt = -1
        pane.scope.launch { state.scroll { stoodAt = state.y } }
        awaitIdle()

        assertEquals(
            20,
            stoodAt,
            "the last caller's block must start from what the block two places back finished writing",
        )
    }

    @Test
    fun aPreemptedScrollThrowsCancellationToItsCallerAndSkipsWhatFollowsIt() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        var caught: Throwable? = null
        var reachedAfterTheScroll = false
        val caller =
            pane.scope.launch {
                try {
                    state.scroll { stepDownEveryFrame(state) }
                    reachedAfterTheScroll = true
                } catch (cancellation: CancellationException) {
                    caught = cancellation
                }
            }
        driveOneFrame()

        state.y = 0
        awaitIdle()

        assertIs<CancellationException>(caught, "the preempted scroll must throw cancellation to its caller")
        assertFalse(reachedAfterTheScroll, "so the statement after it must not run")
        assertTrue(caller.isCompleted, "the caller's own coroutine runs on past the call it caught")
        assertFalse(caller.isCancelled, "what was cancelled is the scroll, not the coroutine that called it")
    }

    @Test
    fun aScrollIsInProgressOnlyWhileItsBlockRuns() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        assertFalse(state.isScrollInProgress, "no scroll is in progress before one is started")

        val job =
            pane.scope.launch {
                state.scroll {
                    withFrameNanos { }
                    scrollTo(0, 20)
                }
            }
        awaitIdle()
        assertTrue(state.isScrollInProgress, "a block that has taken the position is a scroll in progress")

        driveOneFrame()
        assertTrue(job.isCompleted, "the block must have run to its end")
        assertFalse(job.isCancelled, "on its own terms")
        assertEquals(20, state.y, "having moved the pane")
        assertFalse(state.isScrollInProgress, "and a block that returned holds the position no longer")
    }

    @Test
    fun anEscapedScrollScopeCannotMoveAfterItsBlockEnds() = runComposeSwingTest {
        lateinit var escaped: ScrollScope
        lateinit var state: ScrollState
        lateinit var scope: CoroutineScope
        setContent {
            state = rememberScrollState()
            scope = rememberCoroutineScope()
        }

        val job = scope.launch { state.scroll { escaped = this } }
        awaitIdle()
        assertTrue(job.isCompleted, "the block must have returned before its scope is used")

        assertFailsWith<IllegalStateException> {
            escaped.scrollTo(0, 20)
        }
    }

    @Test
    fun aBlockRunningBeforeTheContentHasRoomFollowsItAsItGrows() = runComposeSwingTest {
        val pane = paneWithRoomToScroll(contentWidth = 50, contentHeight = 20)
        val state = pane.state
        assertEquals(0, state.maxY, "content the viewport shows whole leaves nowhere to scroll")

        val job =
            pane.scope.launch {
                state.scroll {
                    while (true) {
                        withFrameNanos { }
                        scrollTo(0, 4_000)
                    }
                }
            }
        driveOneFrame()
        assertEquals(0, state.y, "a move against content with no room to scroll must land at the top")

        pane.contentHeight = 400
        driveOneFrame()
        assertTrue(state.maxY > 0, "the content that grew under the block must leave room to scroll")

        driveOneFrame()
        assertFalse(job.isCancelled, "content growing without moving the pane must leave the block alone")
        assertEquals(state.maxY, state.y, "and the block's next move must reach the end of the content it has")
    }

    @Test
    fun aScrollFromInsideAScrollBlockIsRefusedAndLeavesTheOuterBlockRunning() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state
        var refusal: Throwable? = null
        val job =
            pane.scope.launch {
                state.scroll {
                    withFrameNanos { }
                    refusal = runCatching { state.scroll { } }.exceptionOrNull()
                    withFrameNanos { }
                    scrollTo(0, 20)
                }
            }
        driveOneFrame()
        assertIs<IllegalStateException>(refusal, "a scroll from inside a block of the same state must be refused")

        driveOneFrame()
        assertTrue(job.isCompleted, "the outer block must have run to its end")
        assertFalse(job.isCancelled, "a refused call takes nothing from the block that made it")
        assertEquals(20, state.y, "and the outer block's move must still reach the pane")
    }

    /**
     * A block carries the state it belongs to on its coroutine context, and a second state's block
     * running inside the first installs its own. What the innermost call has to find is the outermost
     * state, not the nearest one: a call that misses it takes the position from a block it is itself
     * running inside, and waits for its own caller to unwind instead of refusing.
     */
    @Test
    fun aScrollUnderASecondStatesBlockStillFindsItsOwn() = runComposeSwingTest {
        lateinit var state: ScrollState
        lateinit var other: ScrollState
        lateinit var scope: CoroutineScope
        mainClock.autoAdvance = false
        // No pane: what is pinned is which states a block names, and a block with none of them bound
        // moves the position it holds all the same.
        setContent {
            state = rememberScrollState()
            other = rememberScrollState()
            scope = rememberCoroutineScope()
        }
        var refusal: Throwable? = null
        var betweenTheTwoFinished = false
        val job =
            scope.launch {
                state.scroll {
                    withFrameNanos { }
                    other.scroll {
                        refusal = runCatching { state.scroll { } }.exceptionOrNull()
                        betweenTheTwoFinished = true
                    }
                    withFrameNanos { }
                    scrollTo(0, 20)
                }
            }
        driveOneFrame()
        assertIs<IllegalStateException>(refusal, "a scroll must be refused by a block of its own state at any depth")
        assertTrue(betweenTheTwoFinished, "the second state's block the refused call was made in must run on")

        driveOneFrame()
        assertTrue(job.isCompleted, "the outer block must have run to its end")
        assertFalse(job.isCancelled, "a refused call takes nothing from the blocks it was made inside")
        assertEquals(20, state.y, "and the outer block's move must still reach the position")
    }

    @Test
    fun circularCrossStateScrollHandoverIsRefused() = runComposeSwingTest {
        lateinit var stateA: ScrollState
        lateinit var stateB: ScrollState
        lateinit var scope: CoroutineScope
        setContent {
            stateA = rememberScrollState()
            stateB = rememberScrollState()
            scope = rememberCoroutineScope()
        }

        var refusal: Throwable? = null
        val jobA =
            scope.launch {
                stateA.scroll {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            refusal = runCatching { stateB.scroll { } }.exceptionOrNull()
                        }
                    }
                }
            }
        awaitIdle()
        scope.launch {
            stateB.scroll {
                stateA.scroll { }
            }
        }
        awaitIdle()

        assertIs<IllegalStateException>(
            refusal,
            "circular handover between two states must be rejected instead of waiting forever",
        )
        assertTrue(jobA.isCompleted, "the predecessor whose cleanup closes the cycle must finish")
    }

    @Test
    fun aScrollOffTheEventDispatchThreadIsRefused() = runComposeSwingTest {
        val pane = paneWithRoomToScroll()
        val state = pane.state

        // A thread of the test's own, since what this pins is the refusal anywhere but the event dispatch
        // thread the composition runs the test body on.
        val thrown =
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { offTheEventDispatchThread ->
                withContext(offTheEventDispatchThread) { runCatching { state.scroll { } }.exceptionOrNull() }
            }

        val refusal = assertIs<IllegalStateException>(thrown, "a scroll off the event dispatch thread must be refused")
        val message = assertNotNull(refusal.message, "the refusal must carry a diagnostic message")
        assertTrue(
            "Event Dispatch Thread" in message,
            "the refusal must state the thread requirement, but was: $message",
        )
        assertFalse(state.isScrollInProgress, "and a refused call must take the position from nothing")
    }

    /**
     * Composes a pane smaller than its content and hands back everything a block needs: the [Pane.state]
     * the pane declares, the [Pane.scope] to launch a block from - the composition's own, so the block
     * runs on the event dispatch thread a caller's does - and the content size, which a test writes to
     * resize the content under a running block.
     *
     * Frames are the test's to send from the mount onwards, so nothing the tree composes consumes one a
     * block was waiting for.
     */
    private fun ComposeSwingTest.paneWithRoomToScroll(
        contentWidth: Int = 300,
        contentHeight: Int = 400,
    ): Pane {
        val pane = Pane(contentWidth, contentHeight)
        mainClock.autoAdvance = false
        setContent {
            pane.state = rememberScrollState()
            pane.scope = rememberCoroutineScope()
            ScrollPane(modifier = SwingModifier.preferredSize(100, 50), state = pane.state) {
                Label(
                    "body",
                    modifier = SwingModifier.preferredSize(pane.contentWidth, pane.contentHeight).viewport(),
                )
            }
        }
        return pane
    }
}

/** What [ScrollStateScrollTest.paneWithRoomToScroll] composes, and the handles a test drives it by. */
private class Pane(
    contentWidth: Int,
    contentHeight: Int,
) {
    lateinit var state: ScrollState
    lateinit var scope: CoroutineScope
    var contentWidth by mutableIntStateOf(contentWidth)
    var contentHeight by mutableIntStateOf(contentHeight)
}

/**
 * Sends the one frame a block driven by hand is waiting for, settling what is already pending before it
 * and what the frame starts after it, so the block stands exactly one move further on.
 */
private suspend fun ComposeSwingTest.driveOneFrame() {
    awaitIdle()
    mainClock.advanceTimeByFrame()
    awaitIdle()
}

/**
 * Moves the pane 20 pixels further down on every frame, from wherever the position stands, and never
 * returns on its own: the test sends the frames and ends the block by whatever it is pinning.
 */
private suspend fun ScrollScope.stepDownEveryFrame(state: ScrollState) {
    while (true) {
        withFrameNanos { }
        scrollTo(0, state.y + 20)
    }
}
