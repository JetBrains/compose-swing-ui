@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent

/**
 * Runs [onCaretUpdate] whenever the caret moves or the selection changes. Requires a [JTextComponent]
 * target. Each event carries the caret offset and the selection anchor, so one lambda observes both.
 *
 * [onCaretUpdate] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.text.JTextComponent.addCaretListener
 */
public fun SwingModifier.caretListener(onCaretUpdate: (CaretEvent) -> Unit): SwingModifier =
    liveCallbackListener<JTextComponent, (CaretEvent) -> Unit, CaretListener>(
        callback = onCaretUpdate,
        adapter = { current -> CaretListener { event -> current()(event) } },
        attach = { component, listener -> component.addCaretListener(listener) },
        detach = { component, listener -> component.removeCaretListener(listener) },
    )

/**
 * Attaches a [CaretListener] (`addCaretListener`/`removeCaretListener`). Requires a [JTextComponent]
 * target (`JTextField`, `JTextArea`, ...). Each event carries the caret offset and the selection anchor,
 * so one listener observes both the caret position and the selected range.
 *
 * The listener observes the component's caret rather than a document, so it keeps reporting after the
 * component's `document` is replaced - unlike [documentListener], which binds the document the
 * component holds at install time.
 *
 * @see javax.swing.text.JTextComponent.addCaretListener
 */
public fun SwingModifier.caretListener(listener: CaretListener): SwingModifier =
    listener<JTextComponent, CaretListener>(
        listener,
        JTextComponent::addCaretListener,
        JTextComponent::removeCaretListener,
    )
