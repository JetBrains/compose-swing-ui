@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkListener

/**
 * Attaches a [HyperlinkListener] (`addHyperlinkListener`/`removeHyperlinkListener`). Requires a
 * [JEditorPane] target.
 *
 * The pane reports a link the user entered, left and activated, each as its own event type. An event
 * carries the raw `href` as its description and, resolved against the document's base, a URL - which is
 * null where the two do not make one, as a relative `href` in a document with no base does not.
 *
 * A pane fires these events only while it is not editable: an editable pane is an editor, in which a
 * click places the caret rather than following a link.
 *
 * @see javax.swing.JEditorPane.addHyperlinkListener
 */
public fun SwingModifier.hyperlinkListener(listener: HyperlinkListener): SwingModifier =
    listener<JEditorPane, HyperlinkListener>(
        listener,
        { c, l -> c.addHyperlinkListener(l) },
        { c, l -> c.removeHyperlinkListener(l) },
    )
