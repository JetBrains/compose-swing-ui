@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Runs [onHyperlinkUpdate] when the user enters, leaves or activates a link. Requires a [JEditorPane]
 * target, and reports only while the pane is not editable, as [hyperlinkListener] describes.
 *
 * [onHyperlinkUpdate] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.JEditorPane.addHyperlinkListener
 */
public fun SwingModifier.hyperlinkListener(onHyperlinkUpdate: (HyperlinkEvent) -> Unit): SwingModifier =
    liveCallbackListener<JEditorPane, (HyperlinkEvent) -> Unit, HyperlinkListener>(
        callback = onHyperlinkUpdate,
        adapter = { current -> HyperlinkListener { event -> current()(event) } },
        attach = { component, listener -> component.addHyperlinkListener(listener) },
        detach = { component, listener -> component.removeHyperlinkListener(listener) },
    )

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
        JEditorPane::addHyperlinkListener,
        JEditorPane::removeHyperlinkListener,
    )
