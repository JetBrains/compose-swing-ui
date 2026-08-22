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
     * its own, or that named a wider one than the component to be driven.
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
     *
     * @param matcher the condition the node must satisfy; its description is what the failure names.
     * @return this interaction, for chaining a further assertion.
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

    /**
     * Asserts the matched node's text equals [expected]. A password field carries no readable
     * text, so this assertion always fails against one.
     *
     * @param expected the whole text the node must carry; a node whose type carries no text fails
     *   against any expectation.
     * @return this interaction, for chaining a further assertion.
     */
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
     *
     * @param expected the placement the parent's layout manager must report for this child.
     * @return this interaction, for chaining a further assertion.
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
