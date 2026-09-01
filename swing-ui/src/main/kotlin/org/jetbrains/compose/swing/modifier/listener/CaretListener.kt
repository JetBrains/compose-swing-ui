@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent

/**
 * Runs [onCaretUpdate] whenever the caret moves or the selection changes. Requires a [JTextComponent]
 * target.
 *
 * [onCaretUpdate] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onCaretUpdate receives the event, whose `dot` is where the caret sits and whose `mark` is the
 *   other end of the selection; the two are equal where nothing is selected.
 * @return this chain with the caret callback declared on it.
 * @see javax.swing.text.JTextComponent.addCaretListener
 */
public fun SwingModifier.caretListener(onCaretUpdate: (CaretEvent) -> Unit): SwingModifier =
    listener(onCaretUpdate, CARET_CALLBACKS)

/**
 * Attaches a [CaretListener] (`addCaretListener`/`removeCaretListener`). Requires a [JTextComponent]
 * target (`JTextField`, `JTextArea`, ...). Each event carries the caret offset and the selection anchor,
 * so one listener observes both the caret position and the selected range.
 *
 * The listener observes the component's caret rather than a document, so it keeps reporting after the
 * component's `document` is replaced - as does [documentListener], which follows the document the
 * component swaps in.
 *
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the caret listener declared on it.
 * @see javax.swing.text.JTextComponent.addCaretListener
 */
public fun SwingModifier.caretListener(listener: CaretListener): SwingModifier = listener(listener, CARET)

private val CARET =
    ListenerRegistration<JTextComponent, CaretListener>(
        JTextComponent::addCaretListener,
        JTextComponent::removeCaretListener,
    )

private val CARET_CALLBACKS =
    CallbackRegistration<JTextComponent, (CaretEvent) -> Unit, CaretListener>(
        adapter = { current -> CaretListener { event -> current()(event) } },
        registration = CARET,
    )
