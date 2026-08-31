@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.text.TextRange
import java.beans.PropertyChangeListener
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent

/**
 * Marks up [ranges] of a text component with [painter] - the background a search draws behind its
 * matches, the wash behind an error span, the squiggle under a misspelling.
 *
 * The declared ranges are the whole of this modifier's markup: a pass replaces the marks the previous
 * declaration left rather than adding to them, so a range that leaves [ranges] stops being painted and
 * the component ends every pass carrying exactly what was declared. Marks made outside this
 * declaration - the caret's own selection, or another chain's - are left where they are, and removing
 * the declaration takes only this one's marks away.
 *
 * A range is painted as the span between its two offsets, whichever way round they are, clamped to the
 * document the way a selection is; a range beyond the text paints as far as the text goes.
 *
 * The painter is compared by identity, so one built inline is a new painter on every recomposition and
 * repaints the whole set each time; hoist it into a `remember` to repaint only when [ranges] change.
 *
 * ```
 * TextArea(
 *     state = source,
 *     modifier = SwingModifier.highlights(matches, matchPainter),
 * )
 * ```
 *
 * Requires a [JTextComponent] target.
 *
 * @param ranges the spans to mark, as offsets into the document. They are read as the chain is built, so a
 *   snapshot list mutated in place invalidates the composition that declared it.
 * @param painter draws every one of the marks; a single painter serves the whole set.
 * @return this chain with the highlights declared on it.
 * @see javax.swing.text.Highlighter.addHighlight
 */
public fun SwingModifier.highlights(
    ranges: List<TextRange>,
    painter: Highlighter.HighlightPainter,
): SwingModifier = this then HighlightsElement(ranges.toList(), painter)

/**
 * Re-paints a text component's highlighter with the declared ranges whenever the declaration changes,
 * keeping the tags the highlighter handed back so exactly those marks are taken away again. Carries the
 * declaration across document swaps via a one-time `document`-property listener: a highlight's tag is
 * bound to the document instance current when it was added, so a swap - a `JEditorPane` switching content
 * type, a `TextArea` rebinding to a new `DocumentState` - stops the old tags from painting anything.
 *
 * The [painter] is the caller's own object, handed to the highlighter as the mark's painter, so it is
 * compared by identity - the same comparison the node makes against what it has already painted.
 *
 * [ranges] is a list no caller holds. An equal element leaves the chain it declares adopted as-is, so
 * what a later declaration is compared against must be a list nothing outside can change under it.
 */
private class HighlightsElement(
    private val ranges: List<TextRange>,
    private val painter: Highlighter.HighlightPainter,
) : SwingModifier.NodeElement<JTextComponent, HighlightsElement.Node>() {
    override val name: String get() = "highlights"

    override val declaredValues: Map<String, Any?> get() = mapOf("ranges" to ranges, "painter" to painter)
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    override fun equals(other: Any?): Boolean =
        other is HighlightsElement && painter === other.painter && ranges == other.ranges

    override fun hashCode(): Int = 31 * System.identityHashCode(painter) + ranges.hashCode()

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.ranges = ranges
        node.painter = painter
        node.apply(force = false)
    }

    class Node : SwingModifier.Node<JTextComponent>() {
        private val tags = ArrayList<Any>()
        private var appliedRanges: List<TextRange>? = null
        private var appliedPainter: Highlighter.HighlightPainter? = null
        private var documentListener: PropertyChangeListener? = null

        var ranges: List<TextRange> = emptyList()
        var painter: Highlighter.HighlightPainter? = null

        override fun onAttach() {
            // force = true: the tags belong to the document being swapped out, not the one replacing it.
            val listener = PropertyChangeListener { apply(force = true) }
            component.addPropertyChangeListener("document", listener)
            documentListener = listener
        }

        /**
         * Paints [ranges] with [painter], unless what is already painted came from an equal declaration
         * against the same document and [force] is not set.
         */
        fun apply(force: Boolean) {
            val declaredRanges = ranges
            val declaredPainter = painter
            // A component whose highlighter has not been installed yet carries no marks to replace;
            // leaving the declaration unrecorded lets the next pass paint it.
            val highlighter = component.highlighter
            if (declaredPainter == null || highlighter == null) return
            if (!force && declaredPainter === appliedPainter && declaredRanges == appliedRanges) return
            appliedRanges = declaredRanges
            appliedPainter = declaredPainter

            removePainted(highlighter)
            val length = component.document.length
            for (range in declaredRanges) {
                val from = minOf(range.start, range.end).coerceIn(0, length)
                val to = maxOf(range.start, range.end).coerceIn(0, length)
                tags += highlighter.addHighlight(from, to, declaredPainter)
            }
        }

        override fun onDetach() {
            documentListener?.let { component.removePropertyChangeListener("document", it) }
            documentListener = null
            val highlighter = component.highlighter ?: return
            removePainted(highlighter)
        }

        private fun removePainted(highlighter: Highlighter) {
            for (tag in tags) {
                highlighter.removeHighlight(tag)
            }
            tags.clear()
        }
    }
}
