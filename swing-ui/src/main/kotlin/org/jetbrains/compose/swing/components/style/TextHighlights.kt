@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.DocumentMirror
import org.jetbrains.compose.swing.modifier.listener.attachSettlingDocumentListener
import org.jetbrains.compose.swing.modifier.listener.detachSwappableDocumentListener
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.text.TextRange
import org.jetbrains.compose.swing.util.fastForEach
import javax.swing.event.DocumentEvent
import javax.swing.text.Document
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent

/**
 * Marks up [ranges] of a text component with [painter] - the background a search draws behind its
 * matches, the wash behind an error span, the squiggle under a misspelling.
 *
 * The declared ranges are the whole of this property's markup: each time the style runs it replaces the marks
 * the previous run left rather than adding to them, so a range that leaves [ranges] stops being painted and the
 * component carries the winning declaration from the nested and caller styles. Marks installed outside
 * styles are preserved, and a style that stops setting highlights removes only the marks it installed.
 *
 * A range is painted as the span between its two offsets, whichever way round they are, clamped to the
 * document the way a selection is; a range beyond the text paints as far as the text goes.
 *
 * The offsets are the declaration itself, not where a mark starts out: an edit under a mark - the
 * user's typing, or a change to the text the composition declares - leaves the range painted where
 * [ranges] puts it. A span that should move with the edit is set at its new offsets.
 *
 * The painter is compared by identity, so one built inline is a new painter on every recomposition and
 * repaints the whole set each time; hoist it into a `remember` to repaint only when [ranges] change or
 * the document does.
 *
 * ```
 * TextArea(state = source, style = { highlights(matches, matchPainter) })
 * ```
 *
 * @param ranges the spans to mark, as offsets into the document. They are read as the style runs, so a
 *   snapshot list the style reads runs it again when the list changes.
 * @param painter draws every one of the marks; a single painter serves the whole set.
 * @see javax.swing.text.Highlighter.addHighlight
 */
public fun TextStyleScope.highlights(
    ranges: List<TextRange>,
    painter: Highlighter.HighlightPainter,
): Unit = HighlightsProperty.provide(HighlightsElement(ranges.toList(), painter))

internal val HighlightsProperty = stylePropertyOf { element: HighlightsElement -> element }

/**
 * Re-paints a text component's highlighter with the declared ranges whenever the declaration changes,
 * keeping the tags the highlighter handed back so exactly those marks are taken away again. A mark is
 * anchored to positions of the document it was added to, and the document alone decides where those go,
 * so the node listens to the document and paints the declaration again for every change: an edit moves
 * or collapses the positions a mark spans, and a swap - a `JEditorPane` switching content type, a
 * `TextArea` rebinding to a new `DocumentState` - leaves the old tags painting nothing.
 *
 * The [painter] is the caller's own object, handed to the highlighter as the mark's painter, so it is
 * compared by identity: the highlighter tells one painter's marks from another's the same way.
 *
 * [ranges] is a list no caller holds. An equal element leaves its node as-is, so
 * what a later declaration is compared against must be a list nothing outside can change under it.
 */
internal class HighlightsElement(
    private val ranges: List<TextRange>,
    private val painter: Highlighter.HighlightPainter,
) : SwingModifier.NodeElement<JTextComponent, HighlightsElement.Node>() {
    override val name: String get() = "highlights"

    override val declaredValues: Map<String, Any?> get() = mapOf("ranges" to ranges, "painter" to painter)
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    /** A removal takes away the marks this declaration made, leaving every other mark painting. */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun equals(other: Any?): Boolean =
        other is HighlightsElement && painter === other.painter && ranges == other.ranges

    override fun hashCode(): Int = 31 * System.identityHashCode(painter) + ranges.hashCode()

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.apply(ranges, painter)
    }

    class Node : SwingModifier.ComponentNode<JTextComponent>() {
        private val tags = ArrayList<Any>()

        /** Paints the declaration again for every change the document makes to what the tags describe. */
        private val documentMirror =
            object : DocumentMirror {
                override fun insertUpdate(event: DocumentEvent): Unit = paintDeclared()

                override fun removeUpdate(event: DocumentEvent): Unit = paintDeclared()

                override fun changedUpdate(event: DocumentEvent) {
                    // An attribute change moves no position, so the marks still span what the declaration says.
                }

                override fun adoptModelSwap(model: Document): Unit = paintDeclared()
            }

        private var ranges: List<TextRange> = emptyList()

        private var painter: Highlighter.HighlightPainter? = null

        override fun onAttach(): Unit = component.attachSettlingDocumentListener(documentMirror)

        fun apply(
            ranges: List<TextRange>,
            painter: Highlighter.HighlightPainter,
        ) {
            this.ranges = ranges
            this.painter = painter
            paintDeclared()
        }

        /**
         * Paints [ranges] with [painter], replacing the marks the previous painting left. Silent until a
         * painter is declared, and on a component whose highlighter has not been installed yet, which
         * carries no marks to replace.
         */
        private fun paintDeclared() {
            val declaredPainter = painter ?: return
            val highlighter = component.highlighter ?: return

            removePainted(highlighter)
            val length = component.document.length
            for (range in ranges) {
                val from = minOf(range.start, range.end).coerceIn(0, length)
                val to = maxOf(range.start, range.end).coerceIn(0, length)
                tags += highlighter.addHighlight(from, to, declaredPainter)
            }
        }

        override fun onDetach() {
            component.detachSwappableDocumentListener(documentMirror)
            val highlighter = component.highlighter ?: return
            removePainted(highlighter)
        }

        private fun removePainted(highlighter: Highlighter) {
            tags.fastForEach { highlighter.removeHighlight(it) }
            tags.clear()
        }
    }
}
