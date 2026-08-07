package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.describeComponent
import org.jetbrains.compose.swing.test.dumpTree
import org.jetbrains.compose.swing.test.textOrNull
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.JTabbedPane
import javax.swing.text.JTextComponent

/**
 * A lazy handle to the single component targeted by a query. The target is resolved against the
 * live AWT tree each time it is needed, so it always reflects the current tree after recomposition.
 *
 * [T] is the component type the query names: `onNodeOfType<JTable>()` targets a `JTable`, and every
 * step that keeps targeting the same node keeps that type, so [fetch] returns it without being told
 * it again. A query that names no type - `onNodeWithText`, `onNodeWithTag`, [onParent] - targets a
 * [Component], and a narrower type is named at the [fetch] that needs it.
 *
 * All methods are intended to be called from a [org.jetbrains.compose.swing.test.runComposeSwingTest]
 * body, which runs on the EDT.
 * Resolution fails with a readable tree dump when the query does not resolve to a single component.
 */
public class SwingNodeInteraction<out T : Component> internal constructor(
    internal val test: ComposeSwingTest,
    internal val description: String,
    internal val root: () -> Container,
    private val pick: NodePick,
    private val asNode: (Component) -> T,
    private val candidates: () -> List<Component>,
) {
    private fun resolveOrNull(): Component? {
        val matches = candidates()
        if (pick == NodePick.Single && matches.size > 1) {
            throw AssertionError("${singleMatchMessage(matches.size)}\nTree:\n${root().dumpTree()}")
        }
        return selectTarget(matches)
    }

    /** Resolves the single targeted component, failing with a tree dump if the query has no target. */
    @PublishedApi
    internal fun resolve(): Component {
        val matches = candidates()
        return selectTarget(matches)
            ?: throw AssertionError("${noTargetMessage(matches.size)}\nTree:\n${root().dumpTree()}")
    }

    /** The single component this query's [pick] selects among [matches], or null when none is selected. */
    private fun selectTarget(matches: List<Component>): Component? =
        when (pick) {
            NodePick.Single -> matches.singleOrNull()
            is NodePick.AtIndex -> matches.getOrNull(pick.index)
            NodePick.Last -> matches.lastOrNull()
        }

    /** Why [resolve] found no target among [matched] matches, phrased for this query's [pick]. */
    private fun noTargetMessage(matched: Int): String =
        when (pick) {
            NodePick.Single -> {
                singleMatchMessage(matched)
            }

            is NodePick.AtIndex -> {
                "Expected a node at '$description' but only $matched node(s) matched."
            }

            NodePick.Last -> {
                "Expected a node at '$description' but none matched."
            }
        }

    /** The single source of the "expected exactly one node" message, for both ambiguity and no match. */
    private fun singleMatchMessage(matched: Int): String =
        "Expected exactly one node matching '$description' but found " +
            "${if (matched == 0) "none" else "$matched"}."

    /**
     * Resolves the matched component and returns it as the [T] the query named, for driving the
     * component's own API directly (e.g. a `JTable`'s model, a `JTree`'s selection, a `JList`'s
     * model). A query that named the type does not name it again:
     *
     * ```
     * val table = onNodeOfType<JTable>().fetch()
     * ```
     *
     * @throws AssertionError if the query has no single target.
     */
    public fun fetch(): T = asNode(resolve())

    /**
     * Resolves the matched component and returns it typed as [R], for a query that named no type of
     * its own - or that named a wider one than the component to be driven:
     *
     * ```
     * onNodeWithText("Save").fetch<JButton>().isDefaultCapable
     * ```
     *
     * @throws AssertionError if the query has no single target, or if the target is not an [R].
     */
    @JvmName("fetchOfType")
    public inline fun <reified R : Component> fetch(): R = resolve().castOrFail("Node", matcherDescription)

    /** Human-readable description of this interaction's query, for [fetch] failure messages. */
    @PublishedApi
    internal val matcherDescription: String
        get() = description

    /**
     * This same query, targeting the same node, handled as one that names node type [R]. [asNode]
     * establishes that the match is an [R], and is what [fetch] resolves the node through.
     */
    @PublishedApi
    internal fun <R : Component> retype(asNode: (Component) -> R): SwingNodeInteraction<R> =
        SwingNodeInteraction(test, description, root, pick, asNode, candidates)

    // region assertions

    /** Asserts that the query resolves to a node. Returns this interaction for chaining. */
    public fun assertExists(): SwingNodeInteraction<T> {
        resolve()
        return this
    }

    /** Asserts that the query resolves to no node. */
    public fun assertDoesNotExist() {
        val match = resolveOrNull()
        if (match != null) {
            throw AssertionError(
                "Expected no node matching '$description' but found one:\n" + describeComponent(match),
            )
        }
    }

    /**
     * Asserts that the matched node satisfies [matcher], and returns this interaction for chaining.
     *
     * This is the general form of the assertion vocabulary: any matcher - including one composed
     * with [SwingMatcher.and], [SwingMatcher.or] and [SwingMatcher.not], or a structural one such as
     * [SwingMatcher.hasParent] - can be asserted on a node.
     *
     * ```
     * onNodeWithTag("agree").assert(SwingMatcher.isSelected())
     * ```
     */
    public fun assert(matcher: SwingMatcher): SwingNodeInteraction<T> {
        val component = resolve()
        if (!matcher.matches(component)) {
            throw AssertionError(
                "Node '$description' does not satisfy '${matcher.description}'. " +
                    "The node is ${describeComponent(component)}.\nTree:\n${root().dumpTree()}",
            )
        }
        return this
    }

    /** Asserts the matched node's text equals [expected]. */
    public fun assertTextEquals(expected: @Nls String): SwingNodeInteraction<T> {
        val actual = resolve().textOrNull()
        if (actual != expected) {
            throw AssertionError(
                "Node '$description' text was ${actual?.let { "\"$it\"" } ?: "null"}, " +
                    "expected \"$expected\".",
            )
        }
        return this
    }

    /** Asserts the matched node is enabled. */
    public fun assertIsEnabled(): SwingNodeInteraction<T> = assertEnabledState(true)

    /** Asserts the matched node is not enabled. */
    public fun assertIsNotEnabled(): SwingNodeInteraction<T> = assertEnabledState(false)

    private fun assertEnabledState(expected: Boolean): SwingNodeInteraction<T> {
        val actual = resolve().isEnabled
        if (actual != expected) {
            throw AssertionError(
                "Node '$description' was ${if (actual) "enabled" else "disabled"}, " +
                    "expected ${if (expected) "enabled" else "disabled"}.",
            )
        }
        return this
    }

    /**
     * Asserts the matched node is displayed, with **off-screen semantics**.
     *
     * The test harness never attaches its root to a window, so no native peer is realized (with or
     * without a display), [java.awt.Component.isShowing] is permanently `false`, and it cannot be
     * used. Instead, "displayed" here means the node has **non-zero bounds** produced by the forced
     * layout pass the harness runs - a real width and height assigned by its ancestor's layout
     * manager within the query's root, the harness root or the window's content pane for a
     * window-scoped query.
     *
     * What this catches off-screen, without requiring an on-screen peer, is a node a layout
     * collapsed to zero size. A node that is no longer in the tree is reported by the query failing
     * to resolve; assert its absence with [assertDoesNotExist], and whether a node has been hidden
     * with [assertIsVisible].
     */
    public fun assertIsDisplayed(): SwingNodeInteraction<T> {
        val component = resolve()
        val currentRoot = root()
        // Every query resolves its target by walking down from its root, so a resolved node is
        // attached to that root. Guards that invariant for future resolution strategies.
        if (!component.isAttachedTo(currentRoot)) {
            throw AssertionError(
                "Node '$description' is not displayed: it is not attached under the query root.\n" +
                    "Tree:\n${currentRoot.dumpTree()}",
            )
        }
        if (component.width <= 0 || component.height <= 0) {
            throw AssertionError(
                "Node '$description' is not displayed: it has zero laid-out size " +
                    "(${component.width}x${component.height}). The forced layout pass assigned " +
                    "it no bounds within its ancestor.\nTree:\n${currentRoot.dumpTree()}",
            )
        }
        return this
    }

    /**
     * Asserts the matched node is visible: neither it nor any ancestor up to the query's root has been
     * hidden with [java.awt.Component.setVisible]. That is the state a container's own layout drives
     * when it shows one child at a time - a `CardPanel` shows a card by hiding the others - so it is
     * what to assert on for anything a layout switches between.
     *
     * Distinct from [assertIsDisplayed], which asks whether the layout gave the node real bounds: a
     * hidden node keeps the bounds of the pass that last laid it out, so it satisfies that assertion
     * while failing this one.
     *
     * The root bounds the walk: a query scoped to a window stops at that window's content pane, so
     * whether the window itself is shown is [SwingWindowInteraction.assertIsVisible]'s question.
     */
    public fun assertIsVisible(): SwingNodeInteraction<T> {
        val component = resolve()
        val hidden = component.hiddenSelfOrAncestor()
        if (hidden != null) {
            val where =
                if (hidden === component) {
                    "its own visible flag is false"
                } else {
                    "the enclosing ${hidden.javaClass.simpleName} is hidden"
                }
            throw AssertionError(
                "Node '$description' is not visible: $where.\nTree:\n${root().dumpTree()}",
            )
        }
        return this
    }

    /**
     * Asserts the matched node is not visible - it, or an ancestor up to the query's root, is hidden.
     * See [assertIsVisible] for what visibility means here.
     */
    public fun assertIsNotVisible(): SwingNodeInteraction<T> {
        val component = resolve()
        if (component.hiddenSelfOrAncestor() == null) {
            throw AssertionError(
                "Node '$description' is visible: neither it nor any ancestor up to the query root " +
                    "is hidden.\nTree:\n${root().dumpTree()}",
            )
        }
        return this
    }

    /**
     * The node itself or its nearest hidden ancestor, searching up to and including the query's root;
     * null when nothing on that path is hidden.
     */
    private fun Component.hiddenSelfOrAncestor(): Component? {
        val stop = root()
        return generateSequence(this) { if (it === stop) null else it.parent }
            .firstOrNull { !it.isVisible }
    }

    /** Walks parents from [this] up to (and including) [ancestor], returning true if reached. */
    private fun Component.isAttachedTo(ancestor: Component): Boolean {
        var current: Component? = this
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    /**
     * Asserts the matched node is placed under the layout constraint [expected] by its parent, and
     * returns this interaction for chaining.
     *
     * The constraint is read from the parent's layout manager, so [expected] takes the form that
     * manager places a child with:
     * - a [BorderLayout] region, e.g. [BorderLayout.NORTH];
     * - a [java.awt.GridBagConstraints], compared field by field.
     *
     * A parent laid out by any other manager fails naming that manager. That includes a
     * [java.awt.CardLayout], which reports nothing about the child behind a card name; a deck is
     * asserted on through the card it shows, with [assertIsVisible] and [assertIsNotVisible].
     */
    public fun assertLayoutConstraint(expected: Any): SwingNodeInteraction<T> {
        layoutConstraintMismatch(description, resolve(), expected)?.let { throw AssertionError(it) }
        return this
    }

    /**
     * Asserts that the matched node is the current focus owner, and returns this interaction for
     * chaining.
     *
     * Focus ownership belongs to the windowing system: it is held by one component of the focused
     * window, so a composition hosted in a window realized by `Window { }` or `Dialog { }` can own
     * focus once that window is focused, and a node under the harness root - which is never attached to
     * a window - never can. Delivering a notification with [performFocusGained] does not confer
     * ownership.
     */
    public fun assertIsFocusOwner(): SwingNodeInteraction<T> = assertFocusOwnership(true)

    /**
     * Asserts that the matched node is not the current focus owner, and returns this interaction for
     * chaining. See [assertIsFocusOwner] for what ownership means.
     */
    public fun assertIsNotFocusOwner(): SwingNodeInteraction<T> = assertFocusOwnership(false)

    private fun assertFocusOwnership(expected: Boolean): SwingNodeInteraction<T> {
        val component = resolve()
        if (component.isFocusOwner != expected) {
            // Naming who holds focus instead is what makes either direction of the failure actionable:
            // the node itself when it was expected not to, and some other component - or nothing at all
            // - when it was expected to.
            val owner: Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            throw AssertionError(
                "Node '$description' should ${if (expected) "be" else "not be"} the focus owner. " +
                    "Focus is currently held by ${owner ?: "nothing"}. Only a component of a realized, " +
                    "focused window can hold it, and the harness root is never attached to a window." +
                    "\nTree:\n${root().dumpTree()}",
            )
        }
        return this
    }

    // endregion

    // region actions

    /**
     * Clicks the matched node and settles the composition. The node must be an [AbstractButton]
     * (Button, CheckBox, RadioButton, etc.); [AbstractButton.doClick] is invoked, firing registered
     * action listeners.
     */
    public suspend fun performClick(): SwingNodeInteraction<T> {
        val component = resolve()
        if (component !is AbstractButton) {
            throw AssertionError(
                "Node '$description' is a ${component.javaClass.simpleName}, " +
                    "which cannot be clicked (expected an AbstractButton).",
            )
        }
        component.doClick()
        test.awaitIdle()
        return this
    }

    /**
     * Appends [text] to the matched [JTextComponent]'s current content, as if typed at its end, then
     * settles the composition.
     */
    public suspend fun performTextInput(text: @Nls String): SwingNodeInteraction<T> {
        editText { it.text + text }
        return this
    }

    /**
     * Replaces the matched [JTextComponent]'s entire content with [text], then settles the
     * composition.
     */
    public suspend fun performTextReplacement(text: @Nls String): SwingNodeInteraction<T> {
        editText { text }
        return this
    }

    private suspend fun editText(transform: (JTextComponent) -> String) {
        val component = resolve()
        if (component !is JTextComponent) {
            throw AssertionError(
                "Node '$description' is a ${component.javaClass.simpleName}, " +
                    "which cannot receive text input (expected a JTextComponent).",
            )
        }
        component.text = transform(component)
        test.awaitIdle()
    }

    /**
     * Delivers a focus-gained notification to the matched node and settles the composition.
     *
     * The node processes a real [FocusEvent] of id [FocusEvent.FOCUS_GAINED]: its own
     * `processFocusEvent` runs, and every registered [java.awt.event.FocusListener] is notified. That
     * is what makes widget behavior driven by focus observable - a formatted text field reformats its
     * value from `processFocusEvent`, not from a listener, so a test that fired the listeners itself
     * would never see it.
     *
     * This does **not** transfer focus: the node does not become the focus owner, and
     * [assertIsFocusOwner] still fails afterwards. Ownership is a windowing-system fact that needs a
     * realized, focused window, which the harness root never has; the notification is delivered to the
     * node regardless of that, which is precisely why focus-driven behavior can be tested off-screen.
     *
     * A [temporary] notification is one the widget may treat as a focus change it should not act on.
     */
    public suspend fun performFocusGained(temporary: Boolean = false): SwingNodeInteraction<T> =
        deliverFocusEvent(FocusEvent.FOCUS_GAINED, temporary)

    /**
     * Delivers a focus-lost notification to the matched node and settles the composition.
     *
     * The node processes a real [FocusEvent] of id [FocusEvent.FOCUS_LOST]; see [performFocusGained]
     * for what is delivered, what a [temporary] notification means, and why this is not a focus
     * transfer.
     */
    public suspend fun performFocusLost(temporary: Boolean = false): SwingNodeInteraction<T> =
        deliverFocusEvent(FocusEvent.FOCUS_LOST, temporary)

    private suspend fun deliverFocusEvent(
        id: Int,
        temporary: Boolean,
    ): SwingNodeInteraction<T> {
        val component = resolve()
        // Dispatching the event at the component would not reach it: AWT routes a focus event through
        // the KeyboardFocusManager first, which refuses focus for a component that is not showing and
        // then claims the event, so nothing is left for the component to process. Redispatching is the
        // manager's own way of handing an event on without being consulted again.
        KeyboardFocusManager
            .getCurrentKeyboardFocusManager()
            .redispatchEvent(component, FocusEvent(component, id, temporary))
        test.awaitIdle()
        return this
    }

    /**
     * Clicks the tab at [index] on the matched [JTabbedPane] and settles the composition.
     *
     * The click is a real [MouseEvent] aimed at the middle of that tab, so the pane's own UI decides
     * what the click means, exactly as it does for a user: it maps the position back to a tab and
     * selects it, which is what publishes the change to the pane's listeners and so to the callbacks a
     * wrapper declares. Writing the pane's selected index instead is the composition's own kind of
     * write rather than the user's, and a wrapper that tells those two apart cannot be tested that way.
     *
     * Clicking does nothing where a user's click would also do nothing: on a tab of a disabled pane, or
     * on a disabled tab. The strip stays where it was and the callbacks hear nothing.
     *
     * @throws AssertionError if the query does not resolve to a single node, if that node is not a
     * [JTabbedPane], if it has no tab at [index], or if the strip does not currently show that tab - a
     * click aimed at a tab with no position on the strip would land on nothing.
     */
    public suspend fun performTabClick(index: Int): SwingNodeInteraction<T> {
        val component = resolve()
        if (component !is JTabbedPane) {
            throw AssertionError(
                "Node '$description' is a ${component.javaClass.simpleName}, which has no tabs " +
                    "to click (expected a JTabbedPane).",
            )
        }
        val position =
            clickPositionOf(component, index)
                ?: throw AssertionError(noClickablePositionMessage(component, index))
        component.dispatchPrimaryClick(position)
        test.awaitIdle()
        return this
    }

    /** Why the tab at [index] of [pane] cannot be clicked, phrased for whichever reason applies. */
    private fun noClickablePositionMessage(
        pane: JTabbedPane,
        index: Int,
    ): String =
        if (index < 0 || index >= pane.tabCount) {
            "Node '$description' has ${pane.tabCount} tab(s), so there is no tab at index " +
                "$index to click."
        } else {
            "The tab at index $index of node '$description' occupies no position the pane " +
                "resolves back to it, so a click aimed at it would land on nothing. That is the state " +
                "of a tab the strip does not currently show: the pane may be laid out too narrow to " +
                "fit the tab, or the tab may sit outside the run of tabs the strip shows. Give the " +
                "pane room for the tab, or bring the tab into the visible run first."
        }

    // endregion
}

/**
 * The point inside the tab at [index] that [pane] resolves back to that same tab, or null when there is
 * none. Both the tab's bounds and the resolution come from the pane's UI, so a position this returns is
 * one the UI's own click handling maps to [index].
 */
private fun clickPositionOf(
    pane: JTabbedPane,
    index: Int,
): Point? {
    val bounds = if (index in 0 until pane.tabCount) pane.getBoundsAt(index) else null
    val position = bounds?.let { Point(it.x + it.width / 2, it.y + it.height / 2) } ?: return null
    return if (pane.ui?.tabForCoordinate(pane, position.x, position.y) == index) position else null
}

/** Presses and releases the primary mouse button at [position], as one click of it. */
private fun JTabbedPane.dispatchPrimaryClick(position: Point) {
    val time = System.currentTimeMillis()
    dispatchMouseEvent(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, position, time)
    dispatchMouseEvent(MouseEvent.MOUSE_RELEASED, 0, position, time)
    dispatchMouseEvent(MouseEvent.MOUSE_CLICKED, 0, position, time)
}

private fun JTabbedPane.dispatchMouseEvent(
    id: Int,
    modifiers: Int,
    position: Point,
    time: Long,
) {
    dispatchEvent(
        MouseEvent(this, id, time, modifiers, position.x, position.y, 1, false, MouseEvent.BUTTON1),
    )
}

/** How a [SwingNodeInteraction] selects its target among the query's matches. */
internal sealed interface NodePick {
    /** The query must match exactly one node. */
    data object Single : NodePick

    /** The query targets the match at [index], in depth-first pre-order; other matches may exist. */
    data class AtIndex(
        val index: Int,
    ) : NodePick

    /** The query targets the last match, re-resolved on every use. */
    data object Last : NodePick
}
