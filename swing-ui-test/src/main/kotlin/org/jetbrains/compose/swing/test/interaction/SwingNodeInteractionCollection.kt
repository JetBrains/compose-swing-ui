package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.describeComponent
import org.jetbrains.compose.swing.test.dumpTree
import java.awt.Component
import java.awt.Container

/**
 * A lazy handle to the set of components matched by a query. The match set is resolved against the
 * live AWT tree each time it is needed, so it reflects the current tree.
 *
 * [T] is the component type the query names - `onAllNodesOfType<JLabel>()` matches `JLabel`s - and
 * every step that keeps matching the same set keeps it, so [fetchAll] and the single-node handles
 * [get], [onFirst], [onLast] and [filterToOne] carry it on. A query that names no type matches
 * [Component]s.
 *
 * All methods are intended to be called from a [org.jetbrains.compose.swing.test.runComposeSwingTest]
 * body, which runs on the EDT.
 */
public class SwingNodeInteractionCollection<out T : Component> internal constructor(
    internal val test: ComposeSwingTest,
    internal val description: String,
    internal val root: () -> Container,
    private val asNode: (Component) -> T,
    private val candidates: () -> List<Component>,
) {
    @PublishedApi
    internal fun resolveAll(): List<Component> = candidates()

    /** Human-readable description of this collection's query, for [fetchAll] failure messages. */
    @PublishedApi
    internal val matcherDescription: String
        get() = description

    /**
     * This same query, matching the same nodes, handled as one that names node type [R]. [asNode]
     * establishes that a match is an [R], and is what [fetchAll] resolves each node through.
     */
    @PublishedApi
    internal fun <R : Component> retype(asNode: (Component) -> R): SwingNodeInteractionCollection<R> =
        SwingNodeInteractionCollection(test, description, root, asNode, candidates)

    /**
     * Returns a collection of the matches that also satisfy [matcher]. Like every interaction, the
     * returned handle re-resolves against the live tree on each use, so it tracks recomposition.
     *
     * Composing a structural matcher scopes a tree-wide query to one subtree:
     *
     * ```
     * onAllNodesOfType<JLabel>().filter(SwingMatcher.hasAnyAncestor(SwingMatcher.hasTestTag("editor")))
     * ```
     *
     * @param matcher narrows the match set, keeping the order the tree yields its matches in.
     * @return a handle to the narrowed match set, empty rather than failing when nothing survives.
     */
    public fun filter(matcher: SwingMatcher): SwingNodeInteractionCollection<T> =
        SwingNodeInteractionCollection(
            test,
            "$description.filter(${matcher.description})",
            root,
            asNode,
        ) { resolveAll().filter(matcher::matches) }

    /**
     * Returns a lazy handle to the single match that also satisfies [matcher]; it fails on use when
     * the filtered set does not hold exactly one node.
     *
     * ```
     * onAllNodesOfType<JCheckBox>().filterToOne(SwingMatcher.isSelected()).assertExists()
     * ```
     *
     * @param matcher must leave exactly one match; it is applied on each use, so which node
     *   survives can change across recomposition.
     * @return a handle that fails on use unless exactly one match survives.
     */
    public fun filterToOne(matcher: SwingMatcher): SwingNodeInteraction<T> =
        SwingNodeInteraction(
            test,
            "$description.filterToOne(${matcher.description})",
            root,
            NodePick.Single,
            asNode,
        ) { resolveAll().filter(matcher::matches) }

    /**
     * Returns a lazy handle to the match at [index], in depth-first pre-order. Like every
     * interaction, the handle re-resolves against the live tree on each use, so it tracks matches
     * added or removed by recomposition; it fails on use when fewer than `index + 1` nodes match.
     *
     * ```
     * onAllNodesWithText("row")[1].assertIsEnabled()
     * ```
     *
     * @param index the position within the match set, which is not the node's index among its
     *   parent's children.
     * @return a handle that fails on use unless at least `index + 1` nodes match.
     */
    public operator fun get(index: Int): SwingNodeInteraction<T> =
        SwingNodeInteraction(
            test,
            "$description[$index]",
            root,
            NodePick.AtIndex(index),
            asNode,
            ::resolveAll,
        )

    /**
     * Returns a lazy handle to the first match, in depth-first pre-order. Convenience for
     * [get]`(0)`.
     */
    public fun onFirst(): SwingNodeInteraction<T> = get(0)

    /**
     * Returns a lazy handle to the last match, in depth-first pre-order. The handle re-resolves
     * against the live tree on each use, so it tracks the current last match across recomposition;
     * it fails on use when nothing matches.
     */
    public fun onLast(): SwingNodeInteraction<T> =
        SwingNodeInteraction(test, "$description.onLast()", root, NodePick.Last, asNode, ::resolveAll)

    /**
     * Asserts that exactly [expected] nodes match.
     *
     * @param expected the number of matches required; the failure names the count found and dumps
     *   the tree.
     * @return this collection, for chaining a further assertion.
     */
    public fun assertCountEquals(expected: Int): SwingNodeInteractionCollection<T> {
        val actual = resolveAll().size
        if (actual != expected) {
            throw AssertionError(
                "Expected $expected nodes matching '$description' but found $actual.\n" +
                    "Tree:\n${root().dumpTree()}",
            )
        }
        return this
    }

    /**
     * Asserts that every matched node satisfies [matcher]. An empty match set satisfies this, as
     * there is no node that violates the matcher; pin the size with [assertCountEquals] where that
     * matters.
     *
     * @param matcher the condition every match must satisfy; the failure describes each node that
     *   does not.
     * @return this collection, for chaining a further assertion.
     */
    public fun assertAll(matcher: SwingMatcher): SwingNodeInteractionCollection<T> {
        val nodes = resolveAll()
        val violations = nodes.filterNot(matcher::matches)
        if (violations.isNotEmpty()) {
            throw AssertionError(
                "Expected every node matching '$description' to satisfy '${matcher.description}', " +
                    "but ${violations.size} of ${nodes.size} did not:\n" + violations.describeEach(),
            )
        }
        return this
    }

    /**
     * Asserts that at least one matched node satisfies [matcher]. An empty match set fails.
     *
     * @param matcher the condition one match is enough to satisfy; the failure describes every node
     *   that was checked.
     * @return this collection, for chaining a further assertion.
     */
    public fun assertAny(matcher: SwingMatcher): SwingNodeInteractionCollection<T> {
        val nodes = resolveAll()
        if (nodes.isEmpty()) {
            throw AssertionError(
                "Expected a node matching '$description' to satisfy '${matcher.description}', " +
                    "but the query matched no node at all.\nTree:\n${root().dumpTree()}",
            )
        }
        if (nodes.none(matcher::matches)) {
            throw AssertionError(
                "Expected a node matching '$description' to satisfy '${matcher.description}', " +
                    "but none of the ${nodes.size} matched nodes did:\n" + nodes.describeEach(),
            )
        }
        return this
    }

    /** Returns the number of currently matching nodes. */
    public fun fetchSize(): Int = resolveAll().size

    /**
     * Resolves every matching component and returns them as the [T] the query named, in depth-first
     * pre-order, for reading or driving each component's own API (e.g. a `JList`'s model, a
     * `JSplitPane`'s divider). A query that named the type does not name it again:
     *
     * ```
     * val labels = onAllNodesOfType<JLabel>().fetchAll()
     * ```
     */
    public fun fetchAll(): List<T> = resolveAll().map(asNode)

    /**
     * Resolves every matching component and returns them typed as [R], in depth-first pre-order, for
     * a query that named no type of its own - or that named a wider one than the components to be
     * driven.
     *
     * @throws AssertionError if any matched node is not an [R].
     */
    @JvmName("fetchAllOfType")
    public inline fun <reified R : Component> fetchAll(): List<R> =
        resolveAll().map { component -> component.castOrFail<R>("Node", matcherDescription) }
}

/** The components of this list, one indented description per line, for a failure message. */
private fun List<Component>.describeEach(): String = joinToString("\n") { "  " + describeComponent(it) }
