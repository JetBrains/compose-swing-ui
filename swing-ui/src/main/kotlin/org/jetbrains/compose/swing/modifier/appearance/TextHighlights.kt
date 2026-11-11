// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.components.style.HighlightsElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.text.TextRange
import javax.swing.text.Highlighter

/**
 * Marks up [ranges] of a text component with [painter] - the background a search draws behind its
 * matches, the wash behind an error span, the squiggle under a misspelling.
 *
 * The declared ranges are the whole of this modifier's markup: a pass replaces the marks the previous
 * declaration left rather than adding to them, so a range that leaves [ranges] stops being painted and
 * the component ends every pass carrying exactly what was declared. Marks made outside this
 * declaration - the caret's own selection, or another modifier's - are left where they are, and removing
 * the declaration takes only this one's marks away.
 *
 * A range is painted as the span between its two offsets, whichever way round they are, clamped to the
 * document the way a selection is; a range beyond the text paints as far as the text goes.
 *
 * The offsets are the declaration itself, not where a mark starts out: an edit under a mark - the
 * user's typing, or a change to the text the composition declares - leaves the range painted where
 * [ranges] puts it. A span that should move with the edit is declared at its new offsets.
 *
 * The painter is compared by identity, so one built inline is a new painter on every recomposition and
 * repaints the whole set each time; hoist it into a `remember` to repaint only when [ranges] change or
 * the document does.
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
 * @param ranges the spans to mark, as offsets into the document. They are read as the modifier is built, so a
 *   snapshot list mutated in place invalidates the composition that declared it.
 * @param painter draws every one of the marks; a single painter serves the whole set.
 * @return this modifier with the highlights declared on it.
 * @see javax.swing.text.Highlighter.addHighlight
 */
@Deprecated("Set it in the component's style: style = { highlights(ranges, painter) }")
public fun SwingModifier.highlights(
    ranges: List<TextRange>,
    painter: Highlighter.HighlightPainter,
): SwingModifier = this then HighlightsElement(ranges.toList(), painter)
