@file:JvmMultifileClass
@file:JvmName("InteractionTestKt")

package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.assertComponentTreesEquivalent
import org.jetbrains.compose.swing.test.layoutOffscreen
import java.awt.Component

/**
 * Asserts that the matched node and [expected] describe the same user interface - the same widgets,
 * nested the same way, holding the same state - and returns this interaction for chaining.
 *
 * Written for comparing a hand-built reference tree against a composed one - the structural counterpart
 * of [captureToImage][org.jetbrains.compose.swing.test.screenshot.captureToImage]. Where a pixel
 * comparison says only that two screens differ, this names the widget that differs and the property it
 * differs in.
 *
 * ```
 * setContent { Button("Save", onClick = {}) }
 *
 * onNodeOfType<JButton>().assertTreeMatches(JButton("Save"))
 * ```
 *
 * [expected] is laid out at the matched node's size first, so a reference built for the comparison
 * carries the bounds the composed tree was laid out at without the caller arranging one.
 *
 * The walk is depth-first pre-order and the first difference it finds is the one reported. At each node
 * it compares, in this order:
 *  - the widget type;
 *  - the bounds, so a tree laid out differently is a difference rather than something only a capture
 *    would show - the two roots on their size alone, since each stands in a tree of its own and a
 *    descendant's bounds are already relative to its parent;
 *  - what every component carries: `visible`, `enabled`, `opaque`, the font, the colors, the insets, the
 *    border, the layout manager, the text, and the number of children, which is what makes the walk
 *    below compare like with like, then its name, cursor, sizes, tool tip, alignments, transfer handler
 *    and key strokes;
 *  - what a stock layout manager holds, read from the container it lays out, along with the constraints
 *    it holds each child under;
 *  - the state the widget's own kind holds, where both nodes are that kind: what it shows and what
 *    stands selected in it, the range it offers, the icons on it, and the columns, rows and tabs it is
 *    read through;
 *  - what it answers assistive technology with.
 *
 * A collaborator a widget delegates to is compared by what it holds where that is reachable: a model by
 * the elements, rows or value it stands for, a document by its text, so two models built apart match on
 * what they carry. One that holds nothing of its own - a renderer, a caret, a transfer handler - is
 * compared by its class, the way a border is, and a library-installed one against a hand-built reference
 * is a difference this reports.
 *
 * Listeners are not compared: one listener is never equal to another.
 *
 * @param expected the reference tree, laid out here before the comparison.
 * @param allowSubclasses when true, the default, a class in the matched tree matches the one in
 *   [expected] as long as it extends it, so a reference naming a JDK class matches the subclass the
 *   library builds over it; when false, the two classes must be equal. Applies to the widget type and
 *   to everything else compared by its class - the border, the layout manager and the collaborators
 *   compared that way.
 * @throws AssertionError if the query has no single target, or if the trees differ, naming the first
 *   differing property, its path from the root of the two trees, and a dump of both trees.
 */
public fun <T : Component> SwingNodeInteraction<T>.assertTreeMatches(
    expected: Component,
    allowSubclasses: Boolean = true,
): SwingNodeInteraction<T> {
    val actual = fetch()
    expected.layoutOffscreen(actual.size)
    assertComponentTreesEquivalent(expected, actual, allowSubclasses)
    return this
}
