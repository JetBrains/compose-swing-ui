@file:JvmMultifileClass
@file:JvmName("InteractionTestKt")

package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.test.ComposeSwingTestImpl
import org.jetbrains.compose.swing.test.ComposeTimeoutException
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.dumpTrees
import org.jetbrains.compose.swing.test.findMatchingIncludingSelf
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.PaintEvent
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A lazy handle to the single top-level window targeted by a window query. The target is resolved
 * against the current set of realized windows each time it is needed, so it always reflects windows
 * appearing and disappearing across recomposition.
 *
 * A window matches while its native peer is realized, whether or not it is currently shown, so a
 * window declared `visible = false` is still matched once its peer is realized - AWT realizes a window
 * when it is shown, and when it is packed to its content's preferred size. A window that leaves the
 * composition is disposed, which retires its peer and drops it out of the match set; a disposed peer
 * lingering in the global AWT window list is likewise excluded because it is no longer realized.
 *
 * Beyond asserting on the window itself, the interaction scopes node queries to that window's own
 * content - its content pane and its menu bar - so a test can assert on the content of each
 * top-level peer independently:
 *
 * ```
 * onWindowWithTitle("Settings").onNodeWithText("Apply").assertIsEnabled()
 * ```
 *
 * [T] is the window type the query names: `onWindowOfType<JFrame>()` targets a `JFrame`, and every
 * step that keeps targeting the same window keeps that type. A query that names no type targets a
 * [Window], and a narrower type is named at the [fetch] that needs it.
 *
 * All methods are intended to be called from a [org.jetbrains.compose.swing.test.runComposeSwingTest]
 * body, which runs on the EDT.
 */
public class SwingWindowInteraction<out T : Window> internal constructor(
    private val test: ComposeSwingTestImpl,
    private val matcher: SwingMatcher,
    private val description: String,
    private val asWindow: (Window) -> T,
) {
    private fun resolveOrNull(): Window? {
        val matches = collectMatches()
        return when (matches.size) {
            0 -> null
            1 -> matches.single()
            else -> throwAmbiguous(matches.size)
        }
    }

    /** Resolves the single matching window, failing with a window summary if not exactly one matches. */
    @PublishedApi
    internal fun resolve(): Window {
        val matches = collectMatches()
        return when (matches.size) {
            1 -> matches.single()

            0 -> throw AssertionError(
                "Expected exactly one realized window matching '$description' but found none.\n" +
                    realizedWindowsSummary(),
            )

            else -> throwAmbiguous(matches.size)
        }
    }

    /**
     * This same query, targeting the same window, handled as one that names window type [R]. [asWindow]
     * establishes that the match is an [R], and is what [fetch] resolves the window through.
     */
    @PublishedApi
    internal fun <R : Window> retype(asWindow: (Window) -> R): SwingWindowInteraction<R> =
        SwingWindowInteraction(test, matcher, description, asWindow)

    /** The single source of the "expected exactly one realized window" ambiguity failure. */
    private fun throwAmbiguous(matched: Int): Nothing =
        throw AssertionError(
            "Expected exactly one realized window matching '$description' " +
                "but found $matched.\n${realizedWindowsSummary()}",
        )

    private fun collectMatches(): List<Window> = realizedWindows().filter(matcher::matches)

    /**
     * Resolves the matched window and returns it typed as [T]. This is the last resort, for a call
     * only the matched window itself takes (e.g. a `JFrame`'s extended state) or for its identity.
     * To read a value, use [assertProperty] or an assertion.
     *
     * @throws AssertionError if no single window matches.
     */
    public fun fetch(): T = asWindow(resolve())

    /**
     * Resolves the matched window and returns it typed as [R], for a query that named no type of its
     * own, or that named a wider one than the window to be driven. Like [fetch], it is the last resort;
     * [assertProperty] or an assertion reads a value.
     *
     * ```
     * val frame = onWindow().fetch<JFrame>()
     * ```
     *
     * @throws AssertionError if no single window matches, or if the matched window is not an [R].
     */
    @JvmName("fetchOfType")
    public inline fun <reified R : Window> fetch(): R = resolve().castOrFail("Window", matcherDescription)

    /** Human-readable description of this interaction's query, for [fetch] failure messages. */
    @PublishedApi
    internal val matcherDescription: String
        get() = description

    /**
     * Suspends until the window system has sent this window no move, resize or paint event for
     * [quietPeriod], and returns this interaction. Use it before recording what a window paints.
     *
     * To read or change geometry, wait for the expected value with
     * [org.jetbrains.compose.swing.test.ComposeSwingTest.waitUntil] instead.
     *
     * Both durations use wall-clock time. The composition clock does not advance during the wait.
     *
     * @param quietPeriod how long the window must go without a move, resize or paint event.
     * @param timeout the wall-clock deadline after which a window that keeps receiving them fails the wait.
     * @throws IllegalArgumentException if [quietPeriod] is not shorter than [timeout].
     * @throws AssertionError if the query does not match exactly one realized window.
     * @throws org.jetbrains.compose.swing.test.ComposeTimeoutException if [timeout] expires first.
     */
    public suspend fun awaitWindowSystemIdle(
        quietPeriod: Duration = 250.milliseconds,
        timeout: Duration = 10.seconds,
    ): SwingWindowInteraction<T> {
        require(quietPeriod < timeout) { "quietPeriod ($quietPeriod) must be shorter than timeout ($timeout)" }
        val window = resolve()
        var lastEvent = TimeSource.Monotonic.markNow()
        val reshapes =
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    lastEvent = TimeSource.Monotonic.markNow()
                }

                override fun componentMoved(event: ComponentEvent) {
                    lastEvent = TimeSource.Monotonic.markNow()
                }
            }
        val paintEvents =
            AWTEventListener { event ->
                val source = event.source
                if (event is PaintEvent && source is Component && SwingUtilities.isDescendingFrom(source, window)) {
                    lastEvent = TimeSource.Monotonic.markNow()
                }
            }
        val toolkit = Toolkit.getDefaultToolkit()
        window.addComponentListener(reshapes)
        toolkit.addAWTEventListener(paintEvents, AWTEvent.PAINT_EVENT_MASK)
        try {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (lastEvent.elapsedNow() < quietPeriod) {
                test.awaitEventsDelivered()
                if (deadline.hasPassedNow()) {
                    throw ComposeTimeoutException(
                        "Window '$description' still received a move, resize or paint event within $quietPeriod " +
                            "after $timeout.\n${describeWindow(window)}",
                    )
                }
            }
        } finally {
            toolkit.removeAWTEventListener(paintEvents)
            window.removeComponentListener(reshapes)
        }
        return this
    }

    // region assertions

    /** Asserts that exactly one realized window matches. Returns this interaction for chaining. */
    public fun assertExists(): SwingWindowInteraction<T> {
        resolve()
        return this
    }

    /** Asserts that no realized window matches. */
    public fun assertDoesNotExist() {
        val match = resolveOrNull()
        if (match != null) {
            throw AssertionError(
                "Expected no realized window matching '$description' but found one:\n" +
                    describeWindow(match),
            )
        }
    }

    /** Asserts the matched window is visible. */
    public fun assertIsVisible(): SwingWindowInteraction<T> = assertVisibleState(true)

    /** Asserts the matched window is not visible. */
    public fun assertIsNotVisible(): SwingWindowInteraction<T> = assertVisibleState(false)

    private fun assertVisibleState(expected: Boolean): SwingWindowInteraction<T> {
        val actual = resolve().isVisible
        if (actual != expected) {
            throw AssertionError(
                "Window '$description' was ${if (actual) "visible" else "not visible"}, " +
                    "expected ${if (expected) "visible" else "not visible"}.",
            )
        }
        return this
    }

    // endregion

    // region window-scoped node finders

    /**
     * Finds the single node matching [matcher] inside this window's content. Both the window
     * and the node are resolved lazily when the returned interaction is first used.
     *
     * @param matcher applied to this window's content and everything under it, never to the window
     *   itself; the window is what this interaction's own query matched.
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNode(matcher: SwingMatcher): SwingNodeInteraction<Component> =
        SwingNodeInteraction(
            test,
            "${matcher.description} in window '$description'",
            ::declaredContent,
            NodePick.Single,
            { it },
        ) { declaredContent().flatMap { root -> root.findMatchingIncludingSelf(matcher) } }

    /**
     * Finds the single node inside this window's content whose text equals [text] (or contains
     * it when [substring] is `true`).
     *
     * @param text matched against a label's, button's or text component's own text.
     * @param substring `true` matches text that merely contains [text]; `false` by default.
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNodeWithText(
        text: @Nls String,
        substring: Boolean = false,
    ): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasText(text, substring))

    /**
     * Finds the single node inside this window's content whose [java.awt.Component.getName]
     * equals [name].
     *
     * @param name the name to match, as [SwingMatcher.hasName] matches it.
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNodeWithName(name: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasName(name))

    /**
     * Finds the single node inside this window's content tagged with [tag] via
     * `SwingModifier.testTag`.
     *
     * @param tag the tag declared on the node; several nodes of this window sharing one are reached
     *   with [onAllNodesWithTag].
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNodeWithTag(tag: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasTestTag(tag))

    /**
     * Finds all nodes matching [matcher] inside this window's content.
     *
     * @param matcher applied to this window's content and everything under it, never to the window
     *   itself.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodes(matcher: SwingMatcher): SwingNodeInteractionCollection<Component> =
        SwingNodeInteractionCollection(
            test,
            "${matcher.description} in window '$description'",
            ::declaredContent,
            { it },
        ) { declaredContent().flatMap { root -> root.findMatchingIncludingSelf(matcher) } }

    /**
     * Finds all nodes inside this window's content whose text equals [text] (or contains it
     * when [substring] is `true`).
     *
     * @param text matched against each candidate's own text, as [onNodeWithText] matches it.
     * @param substring `true` widens the match to text containing [text]; `false` by default.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodesWithText(
        text: @Nls String,
        substring: Boolean = false,
    ): SwingNodeInteractionCollection<Component> = onAllNodes(SwingMatcher.hasText(text, substring))

    /**
     * Finds all nodes inside this window's content tagged with [tag] via
     * `SwingModifier.testTag`.
     *
     * @param tag the tag declared on the nodes; every node of this window carrying it matches.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodesWithTag(tag: String): SwingNodeInteractionCollection<Component> =
        onAllNodes(SwingMatcher.hasTestTag(tag))

    // endregion

    /**
     * What a query scoped to this window searches: the window's content pane and its menu bar, each
     * walked from the top.
     *
     * A window realized by Window { }/Dialog { } is a JFrame/JDialog, both RootPaneContainers, so the
     * cast holds for the peers content queries target.
     */
    private fun declaredContent(): List<Container> = (resolve() as RootPaneContainer).rootPane.windowContent()
}

/**
 * Asserts that the value [actual] reads off the matched window equals [expected], and returns this
 * interaction for chaining.
 *
 * [actual] reads the resolved window, typed as the query named it, so any property a window exposes
 * can be asserted on directly:
 *
 * ```
 * onWindowWithTitle("Settings").assertProperty(true) { isAlwaysOnTop }
 * ```
 *
 * The failure names the query and both values, e.g. `Window 'hasTitle("Settings")' property was false,
 * expected true.` It appends [message], which tells several property assertions on the same window
 * apart.
 *
 * Use this for a property the window assertions do not cover. A property with its own assertion reads
 * better through that. [SwingWindowInteraction.fetch] is the last resort, for a call on the matched
 * window itself or for its identity.
 *
 * @param expected the value [actual] must return, compared with `equals`.
 * @param message appended to the failure after both values; `null` by default.
 * @param actual the property to read, evaluated against the window the query resolves at this call.
 */
public fun <T : Window, V> SwingWindowInteraction<T>.assertProperty(
    expected: V,
    message: String? = null,
    actual: T.() -> V,
): SwingWindowInteraction<T> {
    val value = fetch().actual()
    if (value != expected) {
        throw AssertionError(
            "Window '$matcherDescription' property was $value, expected $expected." +
                message?.let { " $it" }.orEmpty(),
        )
    }
    return this
}

/**
 * Asserts that the matched window is an [R], and returns this interaction typed as [R] so the
 * assertions and properties after it are written against [R]:
 *
 * ```
 * onWindowWithTitle("Settings").assertIsOfType<JFrame>().assertProperty(false) { isUndecorated }
 * ```
 *
 * The failure names the query, the window's actual class and the expected type, e.g. `Window
 * 'hasTitle("Settings")' is a JDialog, expected a JFrame.`
 *
 * @throws AssertionError if the query has no single target, or if the target is not an [R].
 */
public inline fun <reified R : Window> SwingWindowInteraction<*>.assertIsOfType(): SwingWindowInteraction<R> {
    val typed = retype { it.castOrFail<R>("Window", matcherDescription) }
    typed.fetch()
    return typed
}

/**
 * The window content a query scoped to a root pane searches: its content pane and its menu bar, each
 * walked from the top.
 *
 * A `JMenuBar` is not added to the content pane: `JRootPane` puts it in the layered pane beside it. The
 * content pane alone would leave a menu, a menu item and the bar itself unreachable.
 *
 * The two are named one by one rather than through the layered pane that holds them. A look and feel
 * that draws a popup inside the window parents that popup into the layered pane. Searching the layered
 * pane would then reach a component no composition declared, and would reach an open menu's items a
 * second time. Whether a look and feel draws a popup that way is its own choice, so such a search would
 * answer differently under each.
 */
internal fun JRootPane.windowContent(): List<Container> = listOfNotNull(contentPane, jMenuBar)

/**
 * Every top-level window whose native peer is currently realized. A window realized by a
 * [org.jetbrains.compose.swing.window.Window] or [org.jetbrains.compose.swing.window.Dialog]
 * composable is realized while that composable is in the composition - whether or not it is shown -
 * and its peer is retired (made non-displayable) once it is disposed on leaving the composition, so
 * disposed peers lingering in the global AWT window list are excluded.
 */
internal fun realizedWindows(): List<Window> = Window.getWindows().filter { it.isDisplayable }

/** One-line description of [window] for failure messages. Must be called on the EDT. */
internal fun describeWindow(window: Window): String {
    val title =
        when (window) {
            is Frame -> window.title
            is Dialog -> window.title
            else -> ""
        }
    val visibility = if (window.isVisible) "visible" else "hidden"
    return "${window.javaClass.simpleName} title=\"$title\" $visibility " +
        "${window.width}x${window.height} at ${window.x},${window.y}"
}

/** A readable, one-line-per-window summary of all realized windows for failure messages. */
internal fun realizedWindowsSummary(): String {
    val windows = realizedWindows()
    if (windows.isEmpty()) return "Realized windows: none."
    return "Realized windows:\n" + windows.joinToString("\n") { "  " + describeWindow(it) }
}

/**
 * Renders each realized window that carries a root pane as a header line plus the tree a query scoped
 * to it searches, for appending to a failure message's tree dump. Empty when no realized window carries
 * one.
 */
internal fun realizedWindowsTreeDump(): String =
    realizedWindows()
        .filter { it is RootPaneContainer }
        .joinToString(separator = "") { window ->
            val content = (window as RootPaneContainer).rootPane.windowContent()
            "Visible window: ${describeWindow(window)}\n" + content.dumpTrees()
        }
