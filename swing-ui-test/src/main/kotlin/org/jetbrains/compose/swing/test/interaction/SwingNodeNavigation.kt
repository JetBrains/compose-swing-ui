package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.childComponents
import org.jetbrains.compose.swing.test.descendantComponents
import org.jetbrains.compose.swing.test.siblingComponents
import java.awt.Component

/**
 * Returns a handle to the parent of the matched node.
 *
 * The step is lazy like the query it extends: the node and its parent are resolved against the live
 * AWT tree on each use. Resolution fails when the query itself does not resolve to a single node, or
 * when the node has no parent.
 */
public fun SwingNodeInteraction<*>.onParent(): SwingNodeInteraction<Component> =
    derivedNode("onParent()") { listOfNotNull(it.parent) }

/**
 * Returns a handle to the direct children of the matched node, in the order its container holds
 * them. A node that holds no children yields an empty collection.
 */
public fun SwingNodeInteraction<*>.onChildren(): SwingNodeInteractionCollection<Component> =
    derivedNodes("onChildren()") { it.childComponents() }

/**
 * Returns a handle to the only direct child of the matched node. Use this where the node holds
 * exactly one child; resolution fails when it holds any other number.
 */
public fun SwingNodeInteraction<*>.onChild(): SwingNodeInteraction<Component> =
    derivedNode("onChild()") { it.childComponents() }

/**
 * Returns a handle to the direct child of the matched node at [index]. Convenience for
 * [onChildren]`()[index]`.
 */
public fun SwingNodeInteraction<*>.onChildAt(index: Int): SwingNodeInteraction<Component> = onChildren()[index]

/**
 * Returns a handle to the siblings of the matched node: every other child of its parent, in the
 * parent's order. A node with no parent yields an empty collection.
 */
public fun SwingNodeInteraction<*>.onSiblings(): SwingNodeInteractionCollection<Component> =
    derivedNodes("onSiblings()") { it.siblingComponents() }

/**
 * Returns a handle to the only sibling of the matched node. Use this where the node's parent holds
 * exactly two children; resolution fails when the node has any other number of siblings.
 */
public fun SwingNodeInteraction<*>.onSibling(): SwingNodeInteraction<Component> =
    derivedNode("onSibling()") { it.siblingComponents() }

/**
 * Returns a handle to the ancestors of the matched node, nearest first, up to and including the root
 * the query searches - the composition root, or the content pane for a window-scoped query. A node
 * that is itself that root yields an empty collection.
 */
public fun SwingNodeInteraction<*>.onAncestors(): SwingNodeInteractionCollection<Component> =
    derivedNodes("onAncestors()") { node ->
        val stop = root()
        if (node === stop) {
            emptyList()
        } else {
            generateSequence(node.parent) { if (it === stop) null else it.parent }.toList()
        }
    }

/**
 * Returns a handle to every component below the matched node, at any depth, in depth-first
 * pre-order. The node itself is never part of the result, so this is how a query is scoped to one
 * subtree:
 *
 * ```
 * onNodeWithTag("editor").onDescendants().filter(SwingMatcher.isOfType<JLabel>())
 * ```
 */
public fun SwingNodeInteraction<*>.onDescendants(): SwingNodeInteractionCollection<Component> =
    derivedNodes("onDescendants()") { it.descendantComponents().toList() }

/**
 * A single-node handle onto the components [step] derives from this interaction's node, described as
 * this query's description followed by [stepName]. Resolution of both the node and the step is
 * deferred to each use of the returned handle.
 */
private fun SwingNodeInteraction<*>.derivedNode(
    stepName: String,
    step: (Component) -> List<Component>,
): SwingNodeInteraction<Component> =
    SwingNodeInteraction(test, "$description.$stepName", root, NodePick.Single, { it }) { step(resolve()) }

/** The collection counterpart of [derivedNode]: every component [step] derives, in the step's order. */
private fun SwingNodeInteraction<*>.derivedNodes(
    stepName: String,
    step: (Component) -> List<Component>,
): SwingNodeInteractionCollection<Component> =
    SwingNodeInteractionCollection(test, "$description.$stepName", root, { it }) { step(resolve()) }
