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
 * @param onHyperlinkUpdate receives the event, whose `eventType` tells entering, leaving and
 *   activating apart; the link arrives as the `description` and the resolved `url`.
 * @return this chain with the hyperlink callback declared on it.
 * @see javax.swing.JEditorPane.addHyperlinkListener
 */
public fun SwingModifier.hyperlinkListener(onHyperlinkUpdate: (HyperlinkEvent) -> Unit): SwingModifier =
    listener(onHyperlinkUpdate, HYPERLINK_CALLBACKS)

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
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the hyperlink listener declared on it.
 * @see javax.swing.JEditorPane.addHyperlinkListener
 */
public fun SwingModifier.hyperlinkListener(listener: HyperlinkListener): SwingModifier = listener(listener, HYPERLINK)

private val HYPERLINK =
    ListenerRegistration<JEditorPane, HyperlinkListener>(
        JEditorPane::addHyperlinkListener,
        JEditorPane::removeHyperlinkListener,
    )

private val HYPERLINK_CALLBACKS =
    CallbackRegistration<JEditorPane, (HyperlinkEvent) -> Unit, HyperlinkListener>(
        adapter = { current -> HyperlinkListener { event -> current()(event) } },
        registration = HYPERLINK,
    )
