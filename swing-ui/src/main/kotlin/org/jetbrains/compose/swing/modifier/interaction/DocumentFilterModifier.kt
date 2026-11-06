@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.components.style.DocumentFilterElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.text.AbstractDocument
import javax.swing.text.DocumentFilter
import javax.swing.text.JTextComponent

/*
 * Document-filter SwingModifier - gates and rewrites edits to a text component before they reach its
 * document, the seam for masked, length-limited, or validated input.
 */

/**
 * Installs [filter] on the text component's document so it can inspect, reject, or rewrite every
 * insert, remove, and replace before it is applied. A `null` filter clears any filter the modifier
 * previously installed. Requires a [JTextComponent] target whose document is an [AbstractDocument].
 * A `JFormattedTextField` is rejected: its document filter belongs to its formatter, which returns
 * it from `JFormattedTextField.AbstractFormatter.getDocumentFilter` and reinstalls it whenever the
 * field reformats.
 *
 * The filter follows the component across document swaps: replacing the component's document - as a
 * `JEditorPane` does when it switches content type - moves the filter onto the new document so it
 * stays active. Every document is handed back the filter it carried before this one arrived on it: the
 * document being left on a swap, and the current one once the declaration goes.
 *
 * ```
 * TextField(
 *     value = digits,
 *     onValueChange = { digits = it },
 *     modifier = SwingModifier.documentFilter(DigitsOnlyFilter),
 * )
 * ```
 *
 * @param filter the [DocumentFilter] to apply, or `null` to remove the installed filter.
 * @return this modifier with the document filter declared on it.
 * @see javax.swing.text.AbstractDocument.setDocumentFilter
 */
@Deprecated("Set it in the component's style: style = { documentFilter(filter) }")
public fun SwingModifier.documentFilter(filter: DocumentFilter?): SwingModifier =
    this then DocumentFilterElement(filter)
