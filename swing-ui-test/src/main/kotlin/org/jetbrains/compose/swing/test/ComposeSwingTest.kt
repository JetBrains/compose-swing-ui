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
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.core.ContainedCallerFailure
import org.jetbrains.compose.swing.core.InfiniteAnimationPolicy
import org.jetbrains.compose.swing.core.SwingUiSettings
import org.jetbrains.compose.swing.core.setLifecycleOwner
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
import java.awt.event.ComponentEvent
import java.awt.event.InvocationEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
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
     * Sets the composable [content] of the test [root] and waits until the composition is idle so the AWT tree
     * reflects the initial state before returning. May be called only once per test.
     *
     * This wait depends on [mainClock]'s `autoAdvance` exactly as [awaitIdle] does: with it at its
     * default of `true` this call sends whatever frames the composition needs, but with it `false` no
     * frame is sent here either, so an effect gated on the first `withFrameNanos` - a frame-driven
     * animation started from initial composition, for instance - stays parked until the test calls
     * [MainTestClock.advanceTimeByFrame] or [MainTestClock.advanceTimeBy] itself.
     *
     * @param content composed into [root] on the event dispatch thread, under this test's
     *   [lifecycleState].
     * @throws IllegalStateException if called more than once per test.
     */
    public fun setContent(content: @Composable () -> Unit)

    /**
     * Suspends until the composition is idle, making the AWT tree reflect the latest state.
     *
     * It runs the event dispatch thread's queue itself, so recomposition makes progress while it waits,
     * and returns once there is neither pending recomposition nor pending snapshot work AND the EDT
     * queue has drained the runnables the idle composition scheduled - a window show that a
     * `Dialog { }` defers to its own dispatch, for example, has landed by the time this returns. The tree
     * is validated only once that queue is drained, as a window validates after the events ahead of it.
     *
     * With [mainClock]'s `autoAdvance` at its default of `true`, reaching that state is this call's
     * own job: it sends whatever frames are needed. With `autoAdvance` set to `false`, this drains
     * the same pending work but sends no frame itself, so a composition parked waiting for one -
     * mid-animation, or simply holding an unapplied recomposition - is exactly the state this
     * returns on; call [MainTestClock.advanceTimeByFrame] or [MainTestClock.advanceTimeBy] to move
     * it forward.
     *
     * If the composition never becomes idle within a generous frame cap, this fails with an
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
     * [awaitIdle] or [awaitEventsDelivered] that brings the write through - taking it before returns nothing and
     * leaves the failure to end the test.
     */
    public fun takeCallerFailures(): List<Throwable>

    /**
     * Suspends until every AWT notification already queued on the event dispatch thread has been
     * dispatched, **without producing a composition frame**.
     *
     * A frame is what lets the composition recompose and apply its changes, so withholding one takes
     * apart the two things [awaitIdle] waits for together. Once this returns, whatever the widgets
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
     * deterministic. Use [waitUntil] only when an idle condition cannot be expressed that way
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
     * frames, so this gate sends none and consumes none of its cap: each poll dispatches queued
     * event-dispatch-thread work - leaving a coroutine parked in `withFrameNanos` exactly where it is -
     * until the condition holds or the deadline passes. Either way, a poll validates the tree only once
     * that queue is drained.
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
     *
     * @param text matched against a label's, button's or text component's own text.
     * @param substring `true` matches text that merely contains [text]; `false` by default.
     * @return a handle that fails on use unless exactly one node matches.
     */
    public fun onNodeWithText(
        text: @Nls String,
        substring: Boolean = false,
    ): SwingNodeInteraction<Component>

    /**
     * Finds the single node whose [Component.getName] equals [name].
     *
     * @param name the name to match, as [SwingMatcher.hasName] matches it.
     * @return a handle that resolves lazily, failing on use unless exactly one node matches.
     */
    public fun onNodeWithName(name: String): SwingNodeInteraction<Component>

    /**
     * Finds the single node tagged with [tag] via `SwingModifier.testTag`.
     *
     * @param tag the tag declared on the node; several nodes sharing one are reached with
     *   [onAllNodesWithTag].
     * @return a handle that resolves lazily, failing on use unless exactly one node matches.
     */
    public fun onNodeWithTag(tag: String): SwingNodeInteraction<Component>

    /**
     * Finds the single node matching [matcher].
     *
     * @param matcher the condition the node must satisfy, and the description a failure names the
     *   query by.
     * @return a handle that resolves lazily, failing on use unless exactly one node matches.
     */
    public fun onNode(matcher: SwingMatcher): SwingNodeInteraction<Component>

    /**
     * Finds all nodes whose text equals [text] (or contains it when [substring] is `true`).
     *
     * @param text matched against each candidate's own text, as [onNodeWithText] matches it.
     * @param substring `true` widens the match to text containing [text]; `false` by default.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodesWithText(
        text: @Nls String,
        substring: Boolean = false,
    ): SwingNodeInteractionCollection<Component>

    /**
     * Finds all nodes tagged with [tag] via `SwingModifier.testTag`.
     *
     * @param tag the tag declared on the nodes; every node carrying it matches, however deep it sits.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodesWithTag(tag: String): SwingNodeInteractionCollection<Component>

    /**
     * Finds all nodes matching [matcher].
     *
     * @param matcher applied to [root] and every component under it, in depth-first pre-order.
     * @return a handle to the match set, empty rather than failing when nothing matches.
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
     *
     * @param matcher applied to the window itself - its title, its type - rather than to anything
     *   in its content.
     * @return a handle that fails on use unless exactly one realized window matches.
     */
    public fun onWindow(matcher: SwingMatcher): SwingWindowInteraction

    /**
     * Finds all currently realized windows matching [matcher] (see [onWindow] for the match set).
     *
     * @param matcher applied to every realized window, whether or not it is shown.
     * @return a handle to the matching realized windows, empty rather than failing when none match.
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
 *
 * @param title the exact title, as a frame or dialog reports it.
 * @return a handle that fails on use unless exactly one realized window carries the title.
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
 * Runs with or without a display, and shows nothing. Swing's own invalidation and layout run as they
 * do in a window, at a fixed root size, so tree/text/constraint AND bounds-based assertions (see
 * [SwingNodeInteraction.assertIsDisplayed]) all work off-screen. A heavyweight AWT component that
 * needs a native peer (for example [java.awt.Canvas], [java.awt.Button]) is tested in a window the
 * test owns instead.
 *
 * Content may also compose real top-level peers (`Window { }`, `Dialog { }`); those are found with
 * [ComposeSwingTest.onWindow] and are torn down with the composition when the block completes. Realizing
 * them requires a display - declare that requirement with a JUnit assumption
 * (`org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), ...)`) at the top
 * of the block, so the test reports SKIPPED rather than failing on headless environments.
 *
 * @param rootSize the off-screen [ComposeSwingTest.root]'s fixed size. The default is large enough
 * that realistic test layouts get sensible, non-zero child bounds.
 * @param timeout the wall-clock deadline after which an unfinished [block] fails the test
 * instead of hanging it. The frame caps inside [ComposeSwingTest.awaitIdle] and [ComposeSwingTest.waitUntil]
 * only bound the work those gates drive themselves; this bounds the test as a whole.
 * @param effectContext extra context elements the composition's effects run under - what a
 * [androidx.compose.runtime.LaunchedEffect] body and a [androidx.compose.runtime.rememberCoroutineScope]
 * scope see. The harness's own dispatcher, job, frame clock and policies are added after it and win
 * over anything named here.
 * @param block the test body, run on the Event Dispatch Thread against a fresh [ComposeSwingTest].
 */
public fun runComposeSwingTest(
    rootSize: Dimension = Dimension(800, 600),
    timeout: Duration = 60.seconds,
    effectContext: CoroutineContext = EmptyCoroutineContext,
    block: suspend ComposeSwingTest.() -> Unit,
): TestResult =
    runTest(timeout = timeout) {
        withContext(Dispatchers.Swing) {
            ComposeSwingTestImpl(rootSize, effectContext).use {
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

/**
 * The test's root. [addNotify] gives it the stand-in peer a lightweight component gets, so an
 * invalidation travels up the tree; like a window, it is the root of its focus cycle; and it never
 * shows.
 */
private class TestRootPanel : JPanel() {
    init {
        isFocusCycleRoot = true
    }

    override fun isShowing(): Boolean = false
}

private class ComposeSwingTestImpl(
    rootSize: Dimension,
    effectContext: CoroutineContext,
) : ComposeSwingTest,
    AutoCloseable {
    override val root: JComponent =
        TestRootPanel().apply {
            // The root stands in no window, so nothing else gives it a size to lay its children out in.
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

    /**
     * The restore check every composition this test mounts is held to, stated on the context its
     * recomposer is built over so a nested composition - a cell renderer's, a menu's, a window's -
     * inherits the same one.
     */
    private val restoreCheck = ModifierRestoreCheck(::recordLibraryFailure)

    /**
     * The policy an animation that never ends is held to. While frames run on their own such an
     * animation asks for the next one forever, and every gate that waits for the composition to run out
     * of work would wait with it, so the animation is cancelled at its first frame instead. Under manual
     * frame control it runs untouched: a test driving frames by hand has already said how far it wants
     * the animation to go.
     */
    private val infiniteAnimationPolicy =
        object : InfiniteAnimationPolicy {
            override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
                if (mainClock.autoAdvance) {
                    throw CancellationException(
                        "An animation that never ends was cancelled: with mainClock.autoAdvance on, waiting for " +
                            "it to become idle would never return. Set mainClock.autoAdvance = false and advance the " +
                            "clock to drive it.",
                    )
                }
                return block()
            }
        }

    // Widest to narrowest, the same order a real window's recomposer applies: what the process states is
    // overridden by what this test states, and both lose to what the harness cannot run without.
    private val scope =
        CoroutineScope(
            SwingUiSettings.motionDurationScale + effectContext + Dispatchers.Swing + Job() + clock +
                restoreCheck + infiniteAnimationPolicy,
        )
    private val recomposer = Recomposer(scope.coroutineContext)

    override val mainClock: MainTestClock =
        MainTestClockImpl(
            currentTimeNanos = { frameTimeNanos },
            runFrame = ::runFrame,
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
     * gate that goes on to find nothing left to run, so a test asserting on a widget the composition no longer
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
     * A gate that would otherwise return idle by finding nothing left to do throws this instead of returning
     * normally, and clears it once thrown; one still recorded when the test ends fails it too, so a
     * failure that arrives without a further gate call afterward is not silently dropped.
     */
    private var libraryFailure: Throwable? = null

    init {
        // Published before anything composes, so a root mounted into this container resolves this owner
        // rather than minting one that follows a container standing in no window.
        root.setLifecycleOwner(lifecycleOwner)
        root.addNotify()
        dispatchThread.setUncaughtExceptionHandler { _, failure ->
            // A ContainedCallerFailure names a caller callback the library deliberately contained; every
            // other throwable reaching this handler is the library's own failure - see libraryFailure.
            val contained = failure as? ContainedCallerFailure
            if (contained != null) {
                callerFailures += contained.cause ?: contained
            } else {
                recordLibraryFailure(failure)
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

    /** Names the failure that ended recomposition, for a gate reporting a composition that never became idle. */
    private fun compositionFailureNote(): String =
        compositionFailure
            ?.let {
                "\nRecomposition ended earlier with $it - the composition has applied nothing since, so what " +
                    "the tree shows below is what it was left holding.\n" + it.stackTraceToString()
            }.orEmpty() + callerFailureNote() + libraryFailureNote()

    /** Names the callback failures contained so far, for a gate reporting a composition that never became idle. */
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

    /**
     * Records [failure] as the library's own: the first one stands until a gate throws it, and every
     * later one is suppressed onto it.
     */
    private fun recordLibraryFailure(failure: Throwable) {
        val existing = libraryFailure
        if (existing != null) existing.addSuppressed(failure) else libraryFailure = failure
    }

    /** Names a still-unclaimed [libraryFailure], for a gate reporting a composition that never became idle. */
    private fun libraryFailureNote(): String =
        libraryFailure
            ?.let {
                "\nA failure was raised on the event dispatch thread outside the recomposer's own coroutine: " +
                    "$it\n" + it.stackTraceToString()
            }.orEmpty()

    /**
     * Throws and forgets [libraryFailure], once one has arrived, so a gate reports the failure the
     * library raised rather than quietly finding nothing left to run over a tree it already stopped
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
        disposeHandle = root.setContent(parent = recomposer, content = content)
        runUntil("setContent", nextFrame = ::frameIfNeeded, done = ::idle)
    }

    override suspend fun awaitIdle() {
        runUntil("awaitIdle", nextFrame = ::frameIfNeeded, done = ::idle)
    }

    override suspend fun awaitEventsDelivered() {
        // No frame is sent, and the recomposer recomposes and applies only from inside one, so nothing
        // this delivers can reach the AWT tree by way of the composition.
        var drains = 0
        while (!drainQueue(layOut = false)) {
            if (++drains >= MAX_STEPS) throw notIdle("awaitEventsDelivered")
        }
    }

    override suspend fun waitUntil(
        timeout: Duration,
        condition: () -> Boolean,
    ) {
        // Bounded by both a frame cap and a wall-clock deadline. Only frames the composition consumes
        // count toward the cap, so a frame-driven loop that never meets the condition fails after a fixed
        // number of frames, while a condition waiting on external timing is polled until the deadline.
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var frames = 0
        while (true) {
            throwLibraryFailure()
            if (condition()) return
            if (frames >= MAX_STEPS || System.nanoTime() >= deadline) {
                throw AssertionError(
                    "Condition still not met after $frames consumed frames / $timeout. " +
                        "Current tree:\n" + root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote(),
                )
            }
            val frame = if (mainClock.autoAdvance) FRAME_INTERVAL_NANOS else null
            if (frame != null && (clock.hasAwaiters || recomposer.hasPendingWork)) frames++
            step(frame)
        }
    }

    /**
     * Sends one frame of [deltaNanos] and runs until what it started is done, sending no further frame: a
     * composition newly parked waiting for the next explicit advance is where this leaves it.
     */
    private fun runFrame(
        deltaNanos: Long,
        caller: String,
    ) {
        var frame: Long? = deltaNanos
        runUntil(caller, nextFrame = { frame.also { frame = null } }, done = ::composedOrAwaitingFrame)
    }

    /**
     * Runs [step]s until [done] holds over a drained queue, sending the frame [nextFrame] names before
     * each one.
     */
    private fun runUntil(
        gate: String,
        nextFrame: () -> Long?,
        done: () -> Boolean,
    ) {
        var steps = 0
        while (true) {
            if (step(nextFrame()) && done()) return
            if (++steps >= MAX_STEPS) throw notIdle(gate)
        }
    }

    /** One frame while [MainTestClock.autoAdvance] is on and the composition still has work for one. */
    private fun frameIfNeeded(): Long? = if (mainClock.autoAdvance && !composed()) FRAME_INTERVAL_NANOS else null

    /**
     * One pass of every gate: sends [frameDeltaNanos] as a frame when given, then drains the event
     * dispatch thread and lays [root] out - see [drainQueue]. Answers whether the queue was drained.
     */
    private fun step(frameDeltaNanos: Long?): Boolean {
        if (frameDeltaNanos != null) sendFrame(frameDeltaNanos)
        return drainQueue(layOut = true)
    }

    /**
     * Publishes pending snapshot writes and sends a frame [deltaNanos] after the last one, as the library's
     * own frame clock publishes before every frame. Writes made between frames reach the recomposer
     * through the library's global snapshot manager, whose notification is queued work [drainQueue] runs.
     */
    private fun sendFrame(deltaNanos: Long) {
        Snapshot.sendApplyNotifications()
        frameTimeNanos += deltaNanos
        clock.sendFrame(frameTimeNanos)
    }

    /**
     * Dispatches everything already queued on the event dispatch thread without leaving it, and answers
     * whether nothing a dispatch could still run was left behind - see [noPendingDispatch]. With [layOut],
     * a drained queue is followed by validating [root], as a window lays itself out only after the events
     * ahead of it, and the notifications that layout posts count as left behind.
     *
     * The caller is on the event dispatch thread, so this enters a [java.awt.SecondaryLoop] and posts the
     * task that exits it behind the queued work. The layout and the reading run inside that task: every
     * event queued before it has run, and the loop's own teardown has not been posted yet.
     */
    private fun drainQueue(layOut: Boolean): Boolean {
        val loop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
        val drained = booleanArrayOf(false)
        SwingUtilities.invokeLater {
            try {
                if (layOut && noPendingDispatch()) {
                    root.validate()
                    root.layoutUnplacedSubtrees()
                }
                drained[0] = noPendingDispatch()
            } finally {
                loop.exit()
            }
        }
        loop.enter()
        throwLibraryFailure()
        return drained[0]
    }

    /** True once the composition itself is quiescent: no pending recomposition and no unpublished snapshot writes. */
    private fun composed(): Boolean = !recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()

    /**
     * The idle signal [runUntil] drains toward: [composed] with [MainTestClock.autoAdvance] on,
     * or [composedOrAwaitingFrame] with it off - see that property's KDoc for what each one means for
     * whether [runUntil] keeps sending frames of its own.
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
     * thing being waited on is nothing left that a further drain could still advance. That is this
     * gate for [MainTestClock.autoAdvance] `false`: it is satisfied by the parked state precisely
     * because sending a frame is what wakes it, and this test controls when that happens.
     */
    private fun composedOrAwaitingFrame(): Boolean =
        (!recomposer.hasPendingWork || clock.hasAwaiters) && !Snapshot.current.hasPendingChanges()

    /**
     * True when the EDT holds nothing that could still run something the test can observe: no
     * scheduled `invokeLater` callback or coroutine continuation awaiting dispatch, and no bounds
     * notification a component has queued but not yet delivered to its listeners.
     *
     * This is the "nothing queued remains" signal the gates drain toward. Bounds notifications
     * belong in it because a layout pass posts them rather than delivering them, so a gate that
     * returned on the invocation queue alone would return before a modifier reporting the extent a
     * pass settled on had heard about it - and they are finite, since AWT posts one only for a
     * component whose bounds actually changed.
     *
     * Every other event class is deliberately ignored. A realized visible window peer posts native
     * paint events continuously, and treating those as pending work would keep the gate spinning even
     * though they never revive the composition. A paint event is itself a [ComponentEvent], carrying an
     * id of its own outside the range walked here, so peeking that range takes the notifications a
     * component sends about itself without taking those.
     */
    private fun noPendingDispatch(): Boolean {
        val queue = Toolkit.getDefaultToolkit().systemEventQueue
        return queue.peekEvent(InvocationEvent.INVOCATION_DEFAULT) == null &&
            (ComponentEvent.COMPONENT_FIRST..ComponentEvent.COMPONENT_LAST).none { queue.peekEvent(it) != null }
    }

    /** Fails a gate whose work never ran out, naming what was still outstanding. */
    private fun notIdle(gate: String): AssertionError =
        AssertionError(
            "$gate did not become idle after $MAX_STEPS steps: there is still pending recomposition work, " +
                "pending snapshot changes, or scheduled EDT work " +
                "(hasPendingWork=${recomposer.hasPendingWork}, " +
                "hasPendingChanges=${Snapshot.current.hasPendingChanges()}, " +
                "mainClock.autoAdvance=${mainClock.autoAdvance}, awaitingFrame=${clock.hasAwaiters}). " +
                "The composition likely never reaches a stable frame. Current tree:\n" +
                root.dumpTree() + realizedWindowsTreeDump() + compositionFailureNote(),
        )

    override fun onNodeWithText(
        text: @Nls String,
        substring: Boolean,
    ): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasText(text, substring))

    override fun onNodeWithName(name: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasName(name))

    override fun onNodeWithTag(tag: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasTestTag(tag))

    override fun onNode(matcher: SwingMatcher): SwingNodeInteraction<Component> =
        SwingNodeInteraction(this, matcher.description, { listOf(root) }, NodePick.Single, { it }) {
            root.findMatchingIncludingSelf(matcher)
        }

    override fun onAllNodesWithText(
        text: @Nls String,
        substring: Boolean,
    ): SwingNodeInteractionCollection<Component> = onAllNodes(SwingMatcher.hasText(text, substring))

    override fun onAllNodesWithTag(tag: String): SwingNodeInteractionCollection<Component> =
        onAllNodes(SwingMatcher.hasTestTag(tag))

    override fun onAllNodes(matcher: SwingMatcher): SwingNodeInteractionCollection<Component> =
        SwingNodeInteractionCollection(this, matcher.description, { listOf(root) }, { it }) {
            root.findMatchingIncludingSelf(matcher)
        }

    override fun onRoot(): SwingNodeInteraction<Component> =
        SwingNodeInteraction(this, "root", { listOf(root) }, NodePick.Single, { it }) {
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
            root.removeNotify()
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

        // A healthy composition becomes idle in a handful of steps; this turns one that never does into a
        // readable failure instead of a hang.
        const val MAX_STEPS: Int = 10_000
    }
}
