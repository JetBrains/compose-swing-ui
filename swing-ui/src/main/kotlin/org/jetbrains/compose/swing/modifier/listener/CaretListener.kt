@file:JvmMultifileClass
@file:JvmName("ListenerModifiersKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent

/**
 * Attaches a [CaretListener] (`addCaretListener`/`removeCaretListener`). Requires a [JTextComponent]
 * target (`JTextField`, `JTextArea`, ...). Each event carries the caret offset and the selection anchor,
 * so one listener observes both the caret position and the selected range.
 *
 * The listener observes the component's caret rather than a document, so it keeps reporting after the
 * component's `document` is replaced - unlike [documentListener], which binds the document the
 * component holds at install time.
 */
public fun SwingModifier.caretListener(listener: CaretListener): SwingModifier =
    listener<JTextComponent, CaretListener>(
        listener,
        { c, l -> c.addCaretListener(l) },
        { c, l -> c.removeCaretListener(l) },
    )
