package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.dumpTree
import org.jetbrains.compose.swing.test.findMatching
import org.jetbrains.compose.swing.test.findMatchingIncludingSelf
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import java.awt.Window
import javax.swing.RootPaneContainer

/**
 * A lazy handle to the single top-level window targeted by a window query. The target is resolved
 * against the live set of realized windows each time it is needed, so it always reflects windows
 * appearing and disappearing across recomposition.
 *
 * A window matches while its native peer is realized, whether or not it is currently shown, so a
 * window declared `visible = false` is still matched once its peer is realized - AWT realizes a window
 * when it is shown, and when it is packed to its content's preferred size. A window that leaves the
 * composition is disposed, which retires its peer and drops it out of the match set; a disposed peer
 * lingering in the global AWT window list is likewise excluded because it is no longer realized.
 *
 * Beyond asserting on the window itself, the interaction scopes node queries to that window's
 * content pane, so a test can assert on the content of each top-level peer independently:
 *
 * ```
 * onWindowWithTitle("Settings").onNodeWithText("Apply").assertIsEnabled()
 * ```
 *
 * All methods are intended to be called from a [org.jetbrains.compose.swing.test.runComposeSwingTest]
 * body, which runs on the EDT.
 */
public class SwingWindowInteraction internal constructor(
    private val test: ComposeSwingTest,
    private val matcher: SwingMatcher,
    private val description: String,
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

    /** The single source of the "expected exactly one realized window" ambiguity failure. */
    private fun throwAmbiguous(matched: Int): Nothing =
        throw AssertionError(
            "Expected exactly one realized window matching '$description' " +
                "but found $matched.\n${realizedWindowsSummary()}",
        )

    private fun collectMatches(): List<Window> = realizedWindows().filter(matcher::matches)

    /**
     * Resolves the matched window and returns it typed as [T], for driving the window's own API
     * directly (e.g. a `JFrame`'s extended state, a `JDialog`'s modality):
     *
     * ```
     * val frame = onWindow().fetch<JFrame>()
     * ```
     *
     * @throws AssertionError if no single window matches, or if the matched window is not a [T].
     */
    public inline fun <reified T : Window> fetch(): T = resolve().castOrFail("Window", matcherDescription)

    /** Human-readable description of this interaction's query, for [fetch] failure messages. */
    @PublishedApi
    internal val matcherDescription: String
        get() = description

    // region assertions

    /** Asserts that exactly one realized window matches. Returns this interaction for chaining. */
    public fun assertExists(): SwingWindowInteraction {
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
    public fun assertIsVisible(): SwingWindowInteraction = assertVisibleState(true)

    /** Asserts the matched window is not visible. */
    public fun assertIsNotVisible(): SwingWindowInteraction = assertVisibleState(false)

    private fun assertVisibleState(expected: Boolean): SwingWindowInteraction {
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
     * Finds the single node matching [matcher] inside this window's content pane. Both the window
     * and the node are resolved lazily when the returned interaction is first used.
     *
     * @param matcher applied to the content pane and everything under it, never to the window
     *   itself; the window is what this interaction's own query matched.
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNode(matcher: SwingMatcher): SwingNodeInteraction<Component> =
        SwingNodeInteraction(
            test,
            "${matcher.description} in window '$description'",
            ::contentRoot,
            NodePick.Single,
            { it },
        ) { contentRoot().findMatchingIncludingSelf(matcher) }

    /**
     * Finds the single node inside this window's content pane whose text equals [text] (or contains
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
     * Finds the single node inside this window's content pane whose [java.awt.Component.getName]
     * equals [name].
     *
     * @param name the name to match, as [SwingMatcher.hasName] matches it.
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNodeWithName(name: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasName(name))

    /**
     * Finds the single node inside this window's content pane tagged with [tag] via
     * `SwingModifier.testTag`.
     *
     * @param tag the tag declared on the node; several nodes of this window sharing one are reached
     *   with [onAllNodesWithTag].
     * @return a handle that fails on use unless exactly one node in this window matches.
     */
    public fun onNodeWithTag(tag: String): SwingNodeInteraction<Component> = onNode(SwingMatcher.hasTestTag(tag))

    /**
     * Finds all nodes matching [matcher] inside this window's content pane.
     *
     * @param matcher applied to every component under the content pane, in depth-first pre-order.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodes(matcher: SwingMatcher): SwingNodeInteractionCollection<Component> =
        SwingNodeInteractionCollection(
            test,
            "${matcher.description} in window '$description'",
            ::contentRoot,
            { it },
        ) { contentRoot().findMatching(matcher) }

    /**
     * Finds all nodes inside this window's content pane whose text equals [text] (or contains it
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
     * Finds all nodes inside this window's content pane tagged with [tag] via
     * `SwingModifier.testTag`.
     *
     * @param tag the tag declared on the nodes; every node of this window carrying it matches.
     * @return a handle to the match set, empty rather than failing when nothing matches.
     */
    public fun onAllNodesWithTag(tag: String): SwingNodeInteractionCollection<Component> =
        onAllNodes(SwingMatcher.hasTestTag(tag))

    // endregion

    // A window realized by Window { }/Dialog { } is a JFrame/JDialog, both RootPaneContainers, so the
    // cast holds for the peers content queries target.
    private fun contentRoot(): Container = (resolve() as RootPaneContainer).contentPane
}

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
    return "${window.javaClass.simpleName} title=\"$title\" $visibility ${window.width}x${window.height}"
}

/** A readable, one-line-per-window summary of all realized windows for failure messages. */
internal fun realizedWindowsSummary(): String {
    val windows = realizedWindows()
    if (windows.isEmpty()) return "Realized windows: none."
    return "Realized windows:\n" + windows.joinToString("\n") { "  " + describeWindow(it) }
}

/**
 * Renders each realized window that carries a content pane as a header line plus its content-pane tree,
 * for appending to a failure message's tree dump. Empty when no realized window carries a content pane.
 */
internal fun realizedWindowsTreeDump(): String =
    realizedWindows()
        .filter { it is RootPaneContainer }
        .joinToString(separator = "") { window ->
            "Visible window: ${describeWindow(window)}\n" + (window as RootPaneContainer).contentPane.dumpTree()
        }
