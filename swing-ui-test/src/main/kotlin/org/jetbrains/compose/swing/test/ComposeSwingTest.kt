package org.jetbrains.compose.swing.test

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.core.ContainedCallerFailure
import org.jetbrains.compose.swing.core.setLifecycleOwner
import org.jetbrains.compose.swing.node.debugValidateChildIndexSpace
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.NodePick
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteractionCollection
import org.jetbrains.compose.swing.test.interaction.SwingWindowInteraction
import org.jetbrains.compose.swing.test.interaction.SwingWindowInteractionCollection
import org.jetbrains.compose.swing.test.interaction.castOrFail
import org.jetbrains.compose.swing.test.interaction.realizedWindowsTreeDump
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.InvocationEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The user-facing handle for driving a single isolated Swing-Compose composition under test.
 *
 * An instance is created by [runComposeSwingTest] and is only valid for the duration of the supplied
 * test block. The test body runs **on the AWT event dispatch thread (EDT)**, so every query, action
 * and assertion reads and writes the real AWT component tree directly, with no thread hop:
 *
 * ```
 * @Test
 * fun clickingTheButtonUpdatesTheLabel() = runComposeSwingTest {
 *     var clicks by mutableStateOf(0)
 *     setContent {
 *         Button(text = "Clicks: $clicks", onClick = { clicks++ })
 *     }
 *     onNodeWithText("Clicks: 0").performClick()
 *     onNodeWithText("Clicks: 1").assertExists()
 * }
 * ```
 *
 * The composition runs on [Dispatchers.Swing] (the EDT) with frames produced under test control;
 * frames are never produced automatically.
 */
public interface ComposeSwingTest {
    /**
     * The root [Container] hosting the composition. Useful for advanced assertions, e.g. inspecting
     * a child's layout constraint via the parent's [java.awt.LayoutManager].
     */
    public val root: Container

    /**
     * Manual control over the frames this composition is sent. See [MainTestClock].
     */
    public val mainClock: MainTestClock

    /**
     * The lifecycle state the content this test composes reads through
     * [androidx.lifecycle.compose.LocalLifecycleOwner], for content that inherits no owner of its own.
     *
     * Starts at [Lifecycle.State.STARTED] - a test's root stands in no window, so nothing about where it
     * hangs can answer whether its content is shown, and a test says so itself. Move it to
     * [Lifecycle.State.RESUMED] for work gated on the focused state, and to [Lifecycle.State.DESTROYED]
     * to see what the content does as it ends.
     *
     * A `Window` or `Dialog` this test composes is a top-level window of its own and states its own
     * owner, which this does not reach: what it governs is the content composed into [root].
     */
    public var lifecycleState: Lifecycle.State

    /**
     * Sets the composable [content] of the test [root] and settles the composition so the AWT tree
     * reflects the initial state before returning. May be called only once per test.
     *
     * This settle depends on [mainClock]'s `autoAdvance` exactly as [awaitIdle] does: with it at its
     * default of `true` this call sends whatever frames the composition needs, but with it `false` no
     * frame is sent here either, so an effect gated on the first `withFrameNanos` - a frame-driven
     * animation started from initial composition, for instance - stays parked until the test calls
     * [MainTestClock.advanceTimeByFrame] or [MainTestClock.advanceTimeBy] itself.
     *
     * @throws IllegalStateException if called more than once per test.
     */
    public fun setContent(content: @Composable () -> Unit)

    /**
     * Suspends until the composition is idle, making the AWT tree reflect the latest state.
     *
     * This is suspending rather than blocking, so recomposition can make progress while it waits. It
     * returns once there is neither pending recomposition nor pending snapshot work AND the EDT
     * queue has drained the runnables the settled composition scheduled - a window show that a
     * `Dialog { }` defers to its own dispatch, for example, has landed by the time this returns.
     *
     * With [mainClock]'s `autoAdvance` at its default of `true`, reaching that state is this call's
     * own job: it sends whatever frames are needed. With `autoAdvance` set to `false`, this drains
     * the same pending work but sends no frame itself, so a composition parked waiting for one -
     * mid-animation, or simply holding an unapplied recomposition - is exactly the state this
     * returns on; call [MainTestClock.advanceTimeByFrame] or [MainTestClock.advanceTimeBy] to move
     * it forward.
     *
     * If the composition never settles within a generous frame cap, this fails with an
     * [AssertionError] whose message names the outstanding work and includes a readable dump of the
     * current AWT tree (including realized windows), rather than hanging until the
     * surrounding test framework times out.
     *
     * A failure the library itself raises on the event dispatch thread - reached from neither a
     * recomposition nor a caller callback - fails this call with that failure rather than being lost to
     * the thread's own uncaught-exception handler.
     */
    public suspend fun awaitIdle()

    /**
     * Removes and returns the failures raised by callbacks this test supplied and contained by the
     * composition, oldest first.
     *
     * A callback that throws while a wrapper is writing to its widget does not stop the composition -
     * one misbehaving listener cannot be allowed to leave a window unable to answer state - and a test
     * whose callback threw fails on it once the test ends. A test that provokes such a failure on
     * purpose takes it from here and asserts on it; what it takes no longer fails the test.
     *
     * A failure arrives here once the pass that provoked it has been driven, so take it after the
     * [awaitIdle] or [awaitEventsDelivered] that settles the write - taking it before returns nothing and
     * leaves the failure to end the test.
     */
    public fun takeCallerFailures(): List<Throwable>

    /**
     * Suspends until every AWT notification already queued on the event dispatch thread has been
     * dispatched, **without producing a composition frame**.
     *
     * A frame is what lets the composition recompose and apply its changes, so withholding one takes
     * apart the two things [awaitIdle] settles together. Once this returns, whatever the widgets
     * reported has run - a listener callback, a runnable a widget scheduled for itself - and no
     * recomposition can have contributed to what the AWT tree now shows. A test that has to tell "the
     * widget told us" apart from "a recomposition happened" asserts between this gate and [awaitIdle].
     *
     * Snapshot writes those callbacks make stay pending, so the tree still shows the state of the last
     * frame; [awaitIdle] lets it catch up.
     *
     * Draining is bounded as in [awaitIdle]: a source of scheduled work that never quiesces fails with
     * an [AssertionError] carrying a tree dump rather than spinning forever, and a failure the library
     * itself raises on the event dispatch thread fails this call the same way [awaitIdle] does.
     */
    public suspend fun awaitEventsDelivered()

    /**
     * Suspends until [condition] returns `true`, driving frames between checks.
     *
     * Prefer [awaitIdle] followed by a plain assertion wherever it suffices: it is fully
     * deterministic. Use [waitUntil] only when a settled condition cannot be expressed that way
     * (e.g. work gated on genuinely external timing).
     *
     * Bounded by BOTH a frame cap and the [timeout] wall-clock deadline; whichever trips first
     * fails with an [AssertionError] that includes a tree dump. The frame cap counts only frames the
     * composition consumes, keeping CI deterministic: a condition gated on a recomposition or
     * frame-effect loop that never becomes true fails after a fixed number of frames regardless of
     * machine speed, while a condition gated on external timing (e.g. a native window-system event)
     * keeps being polled until the wall-clock deadline.
     *
     * Frames are sent only while [MainTestClock.autoAdvance] is on. With it off the test owns the
     * frames, so this gate sends none and consumes none of its cap: each poll publishes pending
     * snapshot writes and dispatches queued event-dispatch-thread work - leaving a coroutine parked
     * in `withFrameNanos` exactly where it is - until the condition holds or the deadline passes.
     *
     * A failure the library itself raises on the event dispatch thread fails this call the same way
     * [awaitIdle] does.
     *
     * @param timeout the wall-clock deadline after which an unmet condition fails the test.
     * @param condition the predicate to await; evaluated on the EDT.
     */
    public suspend fun waitUntil(
        timeout: Duration = 1.seconds,
        condition: () -> Boolean,
    )

    /**
     * Finds the single node whose text equals [text] (or contains it when [substring] is `true`).
     * The match is resolved lazily when the returned interaction is first used.
     */
    public fun onNodeWithText(
        text: @Nls String,
        substring: Boolean = false,
    ): SwingNodeInteraction<Component>

    /**
     * Finds the single node whose [Component.getName] equals [name].
     */
    public fun onNodeWithName(name: String): SwingNodeInteraction<Component>

    /**
     * Finds the single node tagged with [tag] via `SwingModifier.testTag`.
     */
    public fun onNodeWithTag(tag: String): SwingNodeInteraction<Component>

    /**
     * Finds the single node matching [matcher]. The match is resolved lazily when the returned
     * interaction is first used, and resolution fails if zero or more than one node matches.
     */
    public fun onNode(matcher: SwingMatcher): SwingNodeInteraction<Component>

    /**
     * Finds all nodes whose text equals [text] (or contains it when [substring] is `true`).
     */
    public fun onAllNodesWithText(
        text: @Nls String,
        substring: Boolean = false,
    ): SwingNodeInteractionCollection<Component>

    /**
     * Finds all nodes tagged with [tag] via `SwingModifier.testTag`.
     */
    public fun onAllNodesWithTag(tag: String): SwingNodeInteractionCollection<Component>

    /**
     * Finds all nodes matching [matcher].
     */
    public fun onAllNodes(matcher: SwingMatcher): SwingNodeInteractionCollection<Component>

    /**
     * Returns an interaction targeting the composition [root] itself.
     */
    public fun onRoot(): SwingNodeInteraction<Component>

    /**
     * Finds the single window matching [matcher] among every window currently realized in the test
     * JVM, whether or not it is shown. A window realized by a
     * [org.jetbrains.compose.swing.window.Window] or [org.jetbrains.compose.swing.window.Dialog]
     * composable stays realized while that composable is in the composition and leaves the match set
     * once it is disposed on leaving the composition. The match is resolved lazily when the returned
     * interaction is first used.
     *
     * ```
     * setContent { Window(onCloseRequest = {}, title = "Settings") { ... } }
     * onWindow(SwingMatcher.hasTitle("Settings")).assertIsVisible()
     * ```
     */
    public fun onWindow(matcher: SwingMatcher): SwingWindowInteraction

    /**
     * Finds all currently realized windows matching [matcher] (see [onWindow] for the match set).
     */
    public fun onAllWindows(matcher: SwingMatcher): SwingWindowInteractionCollection
}

/**
 * Finds the single node of type [T]. Convenience for `onNode(SwingMatcher.isOfType<T>())`.
 *
 * The returned interaction carries [T], so the node is fetched without naming the type a second
 * time:
 *
 * ```
 * val table = onNodeOfType<JTable>().fetch()
 * ```
 */
public inline fun <reified T : Component> ComposeSwingTest.onNodeOfType(): SwingNodeInteraction<T> {
    val matcher = SwingMatcher.isOfType<T>()
    return onNode(matcher).retype { it.castOrFail<T>("Node", matcher.description) }
}

/**
 * Finds all nodes of type [T]. Convenience for `onAllNodes(SwingMatcher.isOfType<T>())`.
 *
 * The returned collection carries [T], so its nodes are fetched without naming the type a second
 * time.
 */
public inline fun <reified T : Component> ComposeSwingTest.onAllNodesOfType(): SwingNodeInteractionCollection<T> {
    val matcher = SwingMatcher.isOfType<T>()
    return onAllNodes(matcher).retype { it.castOrFail<T>("Node", matcher.description) }
}

/**
 * Finds the single realized window (see [ComposeSwingTest.onWindow]). Convenience for the
 * common one-window composition:
 *
 * ```
 * setContent { Window(onCloseRequest = {}, title = "Main") { ... } }
 * val frame = onWindow().fetch<JFrame>()
 * ```
 */
public fun ComposeSwingTest.onWindow(): SwingWindowInteraction = onWindow(SwingMatcher.any())

/**
 * Finds the single realized window titled [title]. Convenience for
 * `onWindow(SwingMatcher.hasTitle(title))`.
 */
public fun ComposeSwingTest.onWindowWithTitle(title: @Nls String): SwingWindowInteraction =
    onWindow(SwingMatcher.hasTitle(title))

/**
 * Finds all realized windows (see [ComposeSwingTest.onWindow]).
 */
public fun ComposeSwingTest.onAllWindows(): SwingWindowInteractionCollection = onAllWindows(SwingMatcher.any())

/**
 * Sets up an isolated Swing-Compose composition, runs the suspending [block] against it on the EDT,
 * and tears everything down.
 *
 * The whole [block] executes as a coroutine on [Dispatchers.Swing] (the EDT); the calling (JUnit)
 * thread is blocked until it completes or [timeout] elapses, whichever comes first. Frames are
 * produced under test control rather than automatically, so the composition advances only across
 * idle/await calls. Because the body runs on the EDT, queries and actions read and write the AWT
 * tree directly, and [ComposeSwingTest.awaitIdle] suspends rather than blocking.
 *
 * Runs with or without a display: the root is never attached to a [java.awt.Window], so no native
 * peer is realized and no UI is shown. The root is given a fixed size and laid out synchronously on
 * every idle pass, so tree/text/constraint AND bounds-based assertions (see
 * [SwingNodeInteraction.assertIsDisplayed]) all work off-screen.
 *
 * Content may also compose real top-level peers (`Window { }`, `Dialog { }`); those are found with
 * [ComposeSwingTest.onWindow] and are torn down with the composition when the block completes. Realizing
 * them requires a display - declare that requirement with a JUnit assumption
 * (`org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), ...)`) at the top
 * of the block, so the test reports SKIPPED rather than failing on headless environments.
 *
 * @param rootSize the off-screen [ComposeSwingTest.root]'s fixed size. The default is large enough
 * that realistic test layouts get sensible, non-zero child bounds under a forced layout pass.
 * @param timeout the wall-clock deadline after which an unfinished [block] fails the test
 * instead of hanging it. The frame caps inside [ComposeSwingTest.awaitIdle] and [ComposeSwingTest.waitUntil]
 * only bound the work those gates drive themselves; this bounds the test as a whole.
 * @param block the test body, run on the Event Dispatch Thread against a fresh [ComposeSwingTest].
 */
public fun runComposeSwingTest(
    rootSize: Dimension = Dimension(800, 600),
    timeout: Duration = 60.seconds,
    block: suspend ComposeSwingTest.() -> Unit,
): TestResult =
    runTest(timeout = timeout) {
        withContext(Dispatchers.Swing) {
            ComposeSwingTestImpl(rootSize).use {
                it.block()
            }
        }
    }

/**
 * The [LifecycleOwner] a test states to the content it composes.
 *
 * The registry is built without androidx's main-thread check. That check answers "is this the main
 * thread" by resolving [kotlinx.coroutines.Dispatchers.Main] and blocking on it, which asks a test to
 * stand up whatever host supplies that dispatcher before it can compose at all. The invariant the check
 * stands for holds here by construction and is checked more exactly elsewhere: a test drives this from
 * the event dispatch thread, which is the only thread the library composes on.
 */
private class HarnessLifecycleOwner : LifecycleOwner {
    val registry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }

    override val lifecycle: Lifecycle get() = registry
}

private class ComposeSwingTestImpl(
    private val rootSize: Dimension,
) : ComposeSwingTest,
    AutoCloseable {
    override val root: JComponent =
        JPanel().apply {
            // Give the off-screen root a concrete size so a forced layout pass assigns real,
            // non-zero bounds to descendants. Without this, an unrealized container reports zero
            // size and every child lays out to 0x0, making bounds-based assertions meaningless.
            // We never attach the root to a Window, so no peer is realized and no UI is shown.
            size = rootSize
            preferredSize = rootSize
        }

    private val lifecycleOwner = HarnessLifecycleOwner()

    override var lifecycleState: Lifecycle.State
        get() = lifecycleOwner.registry.currentState
        set(value) {
            lifecycleOwner.registry.currentState = value
        }

    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)

    override val mainClock: MainTestClock =
        MainTestClockImpl(
            currentTimeNanos = { frameTimeNanos },
            advanceAndSettle = ::advanceAndSettleBlocking,
            diagnostics = { root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote() },
        )

    private var disposeHandle: DisposableHandle? = null
    private var contentSet = false
    private var frameTimeNanos = 0L

    /**
     * The failure that ended recomposition, once one has. Applying a composition's changes runs code the
     * caller supplied - a node's update block, and the listeners a widget notifies from inside one of the
     * wrapper's own writes - and a throw from any of it ends the recomposer permanently: it records the
     * failure and never recomposes again, however many frames follow.
     *
     * Held here rather than left to escape the coroutine it was raised in. Escaping, it would arrive as an
     * uncaught exception with no test attached to it, failing whichever test the runner happened to be on
     * rather than the one that caused it. Kept, it names the composition that stopped in the report of every
     * gate that goes on to find nothing to settle, so a test asserting on a widget the composition no longer
     * drives says why rather than reporting a bare stale value.
     */
    private var compositionFailure: Throwable? = null

    /**
     * The event dispatch thread the test runs on, and the handler it reported uncaught exceptions through
     * before this test claimed it.
     */
    private val dispatchThread: Thread = Thread.currentThread()
    private val enclosingHandler: Thread.UncaughtExceptionHandler? = dispatchThread.uncaughtExceptionHandler

    /**
     * The failures raised by code the caller supplied and contained rather than allowed to end the
     * composition - a listener or callback that threw while a wrapper was writing to its widget. Each
     * entry is the original failure the caller's code raised, unwrapped from the marker the library
     * reports it through.
     *
     * A contained failure leaves the composition working, which is what production wants and what would
     * otherwise let a test pass over a callback that never finished. Collected here so the test that
     * provoked one fails on it, and named in the report of any gate that gives up first.
     */
    private val callerFailures = mutableListOf<Throwable>()

    /**
     * A failure the library itself raised on the event dispatch thread outside the recomposer's own
     * coroutine - a check the applier defers to a later turn of the event queue so it never fires on a
     * pass still in progress, for instance. Nothing reaching the thread's uncaught-exception handler that
     * is not a [ContainedCallerFailure] can be told apart from this, so every such throwable is treated
     * as the library's own failure and recorded here rather than forwarded to whichever handler the
     * thread reported through before this test claimed it.
     *
     * A gate that would otherwise settle by finding nothing left to do throws this instead of returning
     * normally, and clears it once thrown; one still recorded when the test ends fails it too, so a
     * failure that arrives without a further gate call afterward is not silently dropped.
     */
    private var libraryFailure: Throwable? = null

    /**
     * [debugValidateChildIndexSpace] as this test found it, restored in [close]. The applier's own check
     * costs nothing this test does not ask for, and a violation surfaces through [dispatchThread]'s
     * uncaught-exception handler exactly like [libraryFailure] does.
     */
    private val enclosingDebugValidateChildIndexSpace = debugValidateChildIndexSpace

    init {
        // Published before anything composes, so a root mounted into this container resolves this owner
        // rather than minting one that follows a container standing in no window.
        root.setLifecycleOwner(lifecycleOwner)
        debugValidateChildIndexSpace = true
        dispatchThread.setUncaughtExceptionHandler { _, failure ->
            // A ContainedCallerFailure names a caller callback the library deliberately contained; every
            // other throwable reaching this handler is the library's own failure - see libraryFailure.
            val contained = failure as? ContainedCallerFailure
            if (contained != null) {
                callerFailures += contained.cause ?: contained
            } else {
                val existing = libraryFailure
                if (existing != null) {
                    existing.addSuppressed(failure)
                } else {
                    libraryFailure = failure
                }
            }
        }
        scope.launch {
            try {
                recomposer.runRecomposeAndApplyChanges()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                compositionFailure = failure
            }
        }
    }

    /** Names the failure that ended recomposition, for a gate reporting what it could not settle. */
    private fun compositionFailureNote(): String =
        compositionFailure
            ?.let {
                "\nRecomposition ended earlier with $it - the composition has applied nothing since, so what " +
                    "the tree shows below is what it was left holding.\n" + it.stackTraceToString()
            }.orEmpty() + callerFailureNote() + libraryFailureNote()

    /** Names the callback failures contained so far, for a gate reporting what it could not settle. */
    private fun callerFailureNote(): String =
        if (callerFailures.isEmpty()) {
            ""
        } else {
            callerFailures.joinToString(
                prefix =
                    "\n${callerFailures.size} callback(s) supplied by this test threw while a wrapper was " +
                        "writing to its widget. The composition carried on, so the tree below reflects a " +
                        "callback that never finished.\n",
                separator = "\n",
            ) { it.stackTraceToString() }
        }

    /** Names a still-unclaimed [libraryFailure], for a gate reporting what it could not settle. */
    private fun libraryFailureNote(): String =
        libraryFailure
            ?.let {
                "\nA failure was raised on the event dispatch thread outside the recomposer's own coroutine: " +
                    "$it\n" + it.stackTraceToString()
            }.orEmpty()

    /**
     * Throws and forgets [libraryFailure], once one has arrived, so a gate reports the failure the
     * library raised rather than quietly finding nothing left to settle over a tree it already stopped
     * maintaining.
     */
    private fun throwLibraryFailure() {
        val failure = libraryFailure ?: return
        libraryFailure = null
        throw failure
    }

    override fun setContent(content: @Composable () -> Unit) {
        check(!contentSet) { "setContent may only be called once per test." }
        contentSet = true
        // We are already on the EDT (the whole test body runs there), so mount synchronously.
        disposeHandle = root.setContent(parent = recomposer, content = content)
        // Drive an initial settle so the AWT tree reflects the initial state before returning. We are
        // on the EDT and cannot suspend here, so we pump frames and let the recomposer (queued on the
        // EDT) make progress by spinning a nested AWT secondary loop between frames.
        settleBlocking()
    }

    override suspend fun awaitIdle() {
        // Deterministic idle gate. Because the test body runs on the EDT, a blocking wait would
        // deadlock the recomposer (which also runs on the EDT); instead we suspend, yielding the EDT
        // back to the recomposer between passes so it can recompose and apply changes.
        //
        // Each outer pass: publish pending snapshot writes, send a frame unless mainClock has been
        // told to hold it back, yield the EDT so the recomposer coroutine runs, then force a
        // synchronous layout pass. The gate returns only when the composition has reached the state
        // idle() names below AND the EDT queue holds nothing that could revive it. Idleness is never
        // declared while a pending snapshot write or an already-scheduled EDT task could still
        // produce observable work.
        var work = 0
        while (true) {
            // Deliver pending snapshot writes to the recomposer ourselves rather than relying on the
            // production GlobalSnapshotManager, whose apply-notification dispatch is asynchronous and
            // backed by process-wide mutable dedup state shared across tests. Calling it directly here
            // makes each test's idle gate self-contained and deterministic.
            Snapshot.sendApplyNotifications()
            // A frame may be requested either by the recomposer (pending invalidations) or by an
            // effect awaiting withFrameNanos; with mainClock.autoAdvance on, send one unconditionally
            // so awaiters proceed. With it off, the test drives frames itself, so idle() below treats
            // a composition parked waiting for the next frame as the idle state to return on rather
            // than something to chase with a frame this gate produced itself.
            if (mainClock.autoAdvance) {
                frameTimeNanos += FRAME_INTERVAL_NANOS
                clock.sendFrame(frameTimeNanos)
            }
            // Hand the EDT to the recomposer (also on Dispatchers.Swing) so it can observe the frame,
            // recompose, and apply changes to the AWT tree before we re-check idleness.
            yield()
            throwLibraryFailure()
            // Force a synchronous layout pass so descendants get real, non-zero bounds off-screen.
            layoutRoot()
            work++
            if (idle()) {
                // The composition is quiescent, but applying it may have left runnables scheduled on
                // the EDT that a single yield does not reach - a window/dialog defers its realization
                // to a later dispatch, and any such task can chain another invokeLater or wake a frame
                // awaiter that posts its own runnable. Drain until no scheduled runnable remains rather
                // than a fixed number of turns: a yield dispatches exactly the runnables queued before
                // its own continuation, so work scheduled during a yield lands after it and needs
                // another. Yielding is the drain step precisely because it advances queued work without
                // leaving any dispatch artifact behind.
                //
                // The termination condition tracks scheduled runnables only - the invocation events
                // that carry invokeLater callbacks and coroutine continuations - not every event on
                // the queue. A realized visible window peer streams native paint events indefinitely;
                // those never mutate composition state and must not be mistaken for pending work, or a
                // visible window would make the gate spin forever. So the gate returns once the
                // composition is idle AND no invocation is queued: no scheduled EDT callback and no
                // pending recomposition remain to revive observable work. If a dispatched runnable
                // instead revived the composition (a snapshot write, a fresh invalidation), we abandon
                // draining and let the next outer pass send a frame and recompose. MAX_IDLE_FRAMES
                // bounds the combined drains and frames so a runnable source that never quiesces fails
                // readably instead of spinning forever.
                while (idle()) {
                    if (noPendingInvocations()) return
                    yield()
                    throwLibraryFailure()
                    Snapshot.sendApplyNotifications()
                    if (++work >= MAX_IDLE_FRAMES) break
                }
            }
            if (work >= MAX_IDLE_FRAMES) throw notSettled("awaitIdle")
        }
    }

    override suspend fun awaitEventsDelivered() {
        // Yielding is the drain step for the reason it is in awaitIdle: on Dispatchers.Swing it
        // dispatches the runnables queued ahead of its own continuation and leaves no artifact behind.
        // No frame is sent, and the recomposer recomposes and applies only from inside withFrameNanos,
        // so nothing this delivers can reach the AWT tree by way of the composition.
        var drains = 0
        while (!noPendingInvocations()) {
            yield()
            throwLibraryFailure()
            drains++
            if (drains >= MAX_IDLE_FRAMES) throw notDrained()
        }
    }

    private fun notDrained(): AssertionError =
        AssertionError(
            "awaitEventsDelivered did not drain after $MAX_IDLE_FRAMES passes: the event dispatch thread " +
                "still holds scheduled work, so something keeps queueing more. Current tree:\n" +
                root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote(),
        )

    /** True once the composition itself is quiescent: no pending recomposition and no unpublished snapshot writes. */
    private fun composed(): Boolean = !recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()

    /**
     * The idle signal the settle loops drain toward: [composed] with [MainTestClock.autoAdvance] on,
     * or [composedOrAwaitingFrame] with it off - see that property's KDoc for what each one means for
     * whether a settle loop keeps sending frames of its own.
     */
    private fun idle(): Boolean = if (mainClock.autoAdvance) composed() else composedOrAwaitingFrame()

    /**
     * True once nothing further can happen without either an external event or a frame this test
     * sends itself: every published snapshot write has reached the composition, and any pending
     * recomposition has already been reported to [clock] - the recomposer only recomposes and applies
     * from inside a frame, so a pending recomposition and an effect suspended in `withFrameNanos`
     * converge on the exact same state, [clock] parked waiting for the next frame.
     *
     * [recomposer.hasPendingWork][Recomposer.hasPendingWork] alone cannot tell these apart from a
     * recomposition still working its way toward that parked state - only once [clock] itself is the
     * thing being waited on is nothing left that a further yield could still advance. That is this
     * gate for [MainTestClock.autoAdvance] `false`: it is satisfied by the parked state precisely
     * because sending a frame is what wakes it, and this test controls when that happens.
     */
    private fun composedOrAwaitingFrame(): Boolean =
        (!recomposer.hasPendingWork || clock.hasAwaiters) && !Snapshot.current.hasPendingChanges()

    /**
     * True when no invocation event is queued on the EDT - no scheduled `invokeLater` callback and no
     * coroutine continuation awaiting dispatch. This is the "no scheduled runnable remains" signal the
     * idle gate drains toward. It deliberately ignores every other event class: a realized visible
     * window peer posts native paint events continuously, and treating those as pending work would
     * keep the gate spinning even though they never revive the composition.
     */
    private fun noPendingInvocations(): Boolean =
        Toolkit
            .getDefaultToolkit()
            .systemEventQueue
            .peekEvent(InvocationEvent.INVOCATION_DEFAULT) == null

    private fun notSettled(gate: String): AssertionError =
        AssertionError(
            "$gate did not settle after $MAX_IDLE_FRAMES frames: there is still pending " +
                "recomposition work, pending snapshot changes, or scheduled EDT work " +
                "(hasPendingWork=${recomposer.hasPendingWork}, " +
                "hasPendingChanges=${Snapshot.current.hasPendingChanges()}, " +
                "mainClock.autoAdvance=${mainClock.autoAdvance}, awaitingFrame=${clock.hasAwaiters}). " +
                "The composition likely never reaches a stable frame. Current tree:\n" +
                root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote(),
        )

    /** Like [notSettled], but for a gate that sends exactly one frame and then only drains. */
    private fun notSettledAfterDrainPasses(caller: String): AssertionError =
        AssertionError(
            "$caller did not settle after $MAX_IDLE_FRAMES drain passes following one frame: there is " +
                "still pending recomposition work, pending snapshot changes, or scheduled EDT work " +
                "(hasPendingWork=${recomposer.hasPendingWork}, " +
                "hasPendingChanges=${Snapshot.current.hasPendingChanges()}, " +
                "mainClock.autoAdvance=${mainClock.autoAdvance}, awaitingFrame=${clock.hasAwaiters}). " +
                "The composition likely never reaches a stable frame. Current tree:\n" +
                root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote(),
        )

    override suspend fun waitUntil(
        timeout: Duration,
        condition: () -> Boolean,
    ) {
        // Escape hatch (see interface KDoc). Bounded by BOTH a frame cap and a wall-clock deadline;
        // whichever trips first fails. Only frames the composition consumes count toward the cap, so
        // it is the deterministic bound on frame-driven work (a recomposition or frame-effect loop
        // that never meets the condition fails after a fixed number of frames regardless of machine
        // speed), while a condition gated on genuinely external timing - no compose work to consume
        // the frames - keeps being polled, with each yield dispatching arriving AWT events, until
        // the wall-clock deadline.
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var frames = 0
        while (true) {
            throwLibraryFailure()
            if (condition()) return
            if (frames >= MAX_WAIT_UNTIL_FRAMES || System.nanoTime() >= deadline) {
                throw AssertionError(
                    "Condition still not met after $frames consumed frames / $timeout. " +
                        "Current tree:\n" + root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote(),
                )
            }
            Snapshot.sendApplyNotifications()
            // Frames are this gate's to send only while mainClock.autoAdvance is on; with it off the
            // test drives them itself, and every poll still publishes snapshot writes, dispatches
            // queued EDT work and lays the tree out, leaving the wall-clock deadline as the bound.
            var consumedFrame = false
            if (mainClock.autoAdvance) {
                consumedFrame = clock.hasAwaiters || recomposer.hasPendingWork
                frameTimeNanos += FRAME_INTERVAL_NANOS
                clock.sendFrame(frameTimeNanos)
            }
            yield()
            layoutRoot()
            if (consumedFrame) frames++
        }
    }

    /**
     * Blocking variant of the idle gate used only by [setContent]'s one-shot initial settle. It pumps
     * frames and spins the EDT message queue inline (the recomposer runs as queued EDT tasks) without
     * suspending. Safe because it is called exactly once, before the test body needs the EDT for
     * anything else.
     */
    private fun settleBlocking() {
        // Same termination proof as awaitIdle, expressed inline: this variant cannot suspend, so it
        // drives its drain by pumping the EDT queue rather than yielding, but the loop shape and the
        // termination condition are identical, including honoring mainClock.autoAdvance (see idle()).
        // It returns only when the composition has reached that idle state AND a pump found no
        // scheduled runnable still queued, and never declares idleness while a scheduled EDT callback
        // or pending recomposition could still revive work. A pump advances only the work queued
        // before its exit marker, so work scheduled during a pump lands after it and needs another
        // pass; the loop keeps pumping until one pump both drains every queued invocation and finds
        // the composition idle. Like awaitIdle it tracks scheduled runnables only, never the native
        // paint events a visible window peer streams. MAX_IDLE_FRAMES bounds the combined drains and
        // frames as a runaway backstop.
        var work = 0
        while (true) {
            Snapshot.sendApplyNotifications()
            if (mainClock.autoAdvance) {
                frameTimeNanos += FRAME_INTERVAL_NANOS
                clock.sendFrame(frameTimeNanos)
            }
            val invocationsDrained = pumpEdtQueue()
            throwLibraryFailure()
            Snapshot.sendApplyNotifications()
            layoutRoot()
            work++
            if (idle() && invocationsDrained) return
            if (work >= MAX_IDLE_FRAMES) throw notSettled("setContent")
        }
    }

    /**
     * Sends one frame and blocks - without suspending - until it has propagated as far as it can
     * without another one. Backs [mainClock]'s explicit-advance API, which the public surface pins
     * non-suspending: a caller stepping through several frames with no suspension point between the
     * calls still needs each one to reach whatever is waiting rather than being silently dropped on an
     * awaiter that has not yet been dispatched to re-register for the next frame, so this pumps the EDT
     * the same way [settleBlocking] does rather than relying on a caller-supplied yield.
     *
     * Settles toward [composedOrAwaitingFrame] rather than [composed]: the point is to fully consume
     * the one frame just sent, not to chase further frames of its own, and a composition newly parked
     * waiting for the next explicit advance is exactly where this is meant to leave it.
     */
    private fun advanceAndSettleBlocking(
        deltaNanos: Long,
        caller: String,
    ) {
        Snapshot.sendApplyNotifications()
        frameTimeNanos += deltaNanos
        clock.sendFrame(frameTimeNanos)
        var work = 0
        while (true) {
            val invocationsDrained = pumpEdtQueue()
            throwLibraryFailure()
            Snapshot.sendApplyNotifications()
            layoutRoot()
            work++
            if (composedOrAwaitingFrame() && invocationsDrained) return
            if (work >= MAX_IDLE_FRAMES) throw notSettledAfterDrainPasses(caller)
        }
    }

    /**
     * Lets Runnables already queued on the EDT (including the recomposer's apply step) run, without
     * leaving the EDT, and reports whether the pump drained every scheduled runnable. We are on the
     * EDT, so we cannot block on `invokeAndWait`; instead we enter an AWT [java.awt.SecondaryLoop] and
     * post a task that exits it. The secondary loop processes the pending events first, so the
     * recomposer's continuation runs before this returns.
     *
     * Whether any invocation remains is read from inside the exit task, at the one instant it is
     * honest: every event queued before the exit marker has been dispatched, and the secondary loop's
     * own teardown invocation has not yet been posted. A check taken after `enter` returns would
     * instead always see that teardown artifact and could never report a drained queue.
     */
    private fun pumpEdtQueue(): Boolean {
        val loop =
            Toolkit
                .getDefaultToolkit()
                .systemEventQueue
                .createSecondaryLoop()
        // Post the exit AFTER the recomposer's already-queued continuation, so the loop drains those
        // first. enter() blocks the current EDT dispatch until exit() runs, while still pumping events.
        val drained = booleanArrayOf(false)
        SwingUtilities.invokeLater {
            drained[0] = noPendingInvocations()
            loop.exit()
        }
        loop.enter()
        return drained[0]
    }

    private fun layoutRoot() {
        // Re-assert the root size (so it never collapses) and run a synchronous layout pass so every
        // descendant receives real bounds. The applier only calls revalidate(), which defers layout
        // to the RepaintManager; with no realized peer that deferred pass may never run, leaving
        // children at 0x0.
        //
        // We cannot use validate(): on a container with no native peer / validate-root it
        // short-circuits and assigns no child bounds. We instead drive doLayout() top-down ourselves -
        // each container is sized by its parent's layout before we lay out its own children - which
        // assigns real bounds throughout the tree synchronously on the EDT.
        root.size = rootSize
        layoutTree(root)
    }

    private fun layoutTree(component: Component) {
        if (component is Container) {
            // Lay out this container first so its children receive their bounds, then recurse so each
            // child (now sized) lays out its own descendants.
            component.doLayout()
            for (child in component.components) layoutTree(child)
        }
    }

    override fun onNodeWithText(
        text: @Nls String,
        substring: Boolean,
    ): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasText(text, substring))

    override fun onNodeWithName(name: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasName(name))

    override fun onNodeWithTag(tag: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasTestTag(tag))

    override fun onNode(matcher: SwingMatcher): SwingNodeInteraction<Component> =
        SwingNodeInteraction(this, matcher.description, { root }, NodePick.Single, { it }) {
            root.findMatchingIncludingSelf(matcher)
        }

    override fun onAllNodesWithText(
        text: @Nls String,
        substring: Boolean,
    ): SwingNodeInteractionCollection<Component> = onAllNodes(SwingMatcher.hasText(text, substring))

    override fun onAllNodesWithTag(tag: String): SwingNodeInteractionCollection<Component> =
        onAllNodes(SwingMatcher.hasTestTag(tag))

    override fun onAllNodes(matcher: SwingMatcher): SwingNodeInteractionCollection<Component> =
        SwingNodeInteractionCollection(this, matcher.description, { root }, { it }) {
            root.findMatching(matcher)
        }

    override fun onRoot(): SwingNodeInteraction<Component> =
        SwingNodeInteraction(this, "root", { root }, NodePick.Single, { it }) {
            root.findMatchingIncludingSelf(SwingMatcher.isRoot(root))
        }

    override fun takeCallerFailures(): List<Throwable> {
        val taken = callerFailures.toList()
        callerFailures.clear()
        return taken
    }

    override fun onWindow(matcher: SwingMatcher): SwingWindowInteraction =
        SwingWindowInteraction(this, matcher, description = matcher.description)

    override fun onAllWindows(matcher: SwingMatcher): SwingWindowInteractionCollection =
        SwingWindowInteractionCollection(matcher)

    override fun close() {
        // The restores below put back state this test claimed, so they run whatever teardown throws - a
        // node's onRelease is caller code, and disposal runs every one of them.
        try {
            disposeHandle?.dispose()
            recomposer.cancel()
            scope.cancel()
        } finally {
            dispatchThread.setUncaughtExceptionHandler(enclosingHandler)
            debugValidateChildIndexSpace = enclosingDebugValidateChildIndexSpace
            root.setLifecycleOwner(null)
        }
        val contained = callerFailures.toList()
        if (contained.isNotEmpty()) {
            // The composition contains these so a misbehaving callback cannot stop a window answering
            // state. A test is the one place that has to hear about them: a callback that threw did not
            // do what the test asked of it, whatever the widgets ended up showing.
            val failure =
                AssertionError(
                    "${contained.size} callback(s) supplied by this test threw while a wrapper was writing " +
                        "to its widget. The composition contained them and carried on; the test cannot.",
                )
            contained.forEach(failure::addSuppressed)
            libraryFailure?.let(failure::addSuppressed)
            throw failure
        }
        // A library failure a gate never got the chance to throw - the test ended without calling one
        // after it arrived - would otherwise be lost entirely rather than merely late.
        libraryFailure?.let { throw it }
    }

    private companion object {
        val FRAME_INTERVAL_NANOS: Long = MainTestClockImpl.FRAME_DURATION.inWholeNanoseconds

        // Generous frame caps. A healthy composition settles in a handful of frames; these bounds
        // exist only to convert a pathological never-settling loop into a readable failure instead
        // of an indefinite hang.
        const val MAX_IDLE_FRAMES: Int = 10_000
        const val MAX_WAIT_UNTIL_FRAMES: Int = 10_000
    }
}
