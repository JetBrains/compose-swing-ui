@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameEvent
import javax.swing.event.InternalFrameListener

/**
 * Runs [onFrameChange] on every change to the internal frame - opened, closing, closed, iconified,
 * deiconified, activated and deactivated alike. Requires a [JInternalFrame] target. Declare the
 * changes one by one to tell them apart.
 *
 * [onFrameChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see javax.swing.JInternalFrame.addInternalFrameListener
 */
public fun SwingModifier.internalFrameListener(onFrameChange: (InternalFrameEvent) -> Unit): SwingModifier =
    internalFrameListener(
        onFrameOpened = onFrameChange,
        onFrameClosing = onFrameChange,
        onFrameClosed = onFrameChange,
        onFrameIconified = onFrameChange,
        onFrameDeiconified = onFrameChange,
        onFrameActivated = onFrameChange,
        onFrameDeactivated = onFrameChange,
    )

/**
 * Runs each lambda on the change to the internal frame it is declared for. Requires a
 * [JInternalFrame] target. A change left undeclared reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @see javax.swing.JInternalFrame.addInternalFrameListener
 */
@Suppress("LongParameterList")
// One parameter per method of the listener interface, each optional and named at the call
// site; a caller that does not tell the methods apart declares the single-lambda overload.
public fun SwingModifier.internalFrameListener(
    onFrameOpened: (InternalFrameEvent) -> Unit = UNDECLARED,
    onFrameClosing: (InternalFrameEvent) -> Unit = UNDECLARED,
    onFrameClosed: (InternalFrameEvent) -> Unit = UNDECLARED,
    onFrameIconified: (InternalFrameEvent) -> Unit = UNDECLARED,
    onFrameDeiconified: (InternalFrameEvent) -> Unit = UNDECLARED,
    onFrameActivated: (InternalFrameEvent) -> Unit = UNDECLARED,
    onFrameDeactivated: (InternalFrameEvent) -> Unit = UNDECLARED,
): SwingModifier {
    requireAnyDeclared(
        "internalFrameListener",
        declared(onFrameOpened) || declared(onFrameClosing) || declared(onFrameClosed) || declared(onFrameIconified) ||
            declared(onFrameDeiconified) || declared(onFrameActivated) || declared(onFrameDeactivated),
    )
    return listener(
        InternalFrameCallbacks(
            onFrameOpened,
            onFrameClosing,
            onFrameClosed,
            onFrameIconified,
            onFrameDeiconified,
            onFrameActivated,
            onFrameDeactivated,
        ),
        INTERNAL_FRAME_CALLBACKS,
    )
}

/**
 * Attaches an [InternalFrameListener]
 * (`addInternalFrameListener`/`removeInternalFrameListener`). Requires a [JInternalFrame] target.
 *
 * @see javax.swing.JInternalFrame.addInternalFrameListener
 */
public fun SwingModifier.internalFrameListener(listener: InternalFrameListener): SwingModifier =
    listener(listener, INTERNAL_FRAME)

/** The lambdas [internalFrameListener] was declared with, as one value the built listener reads. */
@Suppress("LongParameterList")
// One field per method of the listener interface, holding the lambda declared for it.
private class InternalFrameCallbacks(
    val onFrameOpened: (InternalFrameEvent) -> Unit,
    val onFrameClosing: (InternalFrameEvent) -> Unit,
    val onFrameClosed: (InternalFrameEvent) -> Unit,
    val onFrameIconified: (InternalFrameEvent) -> Unit,
    val onFrameDeiconified: (InternalFrameEvent) -> Unit,
    val onFrameActivated: (InternalFrameEvent) -> Unit,
    val onFrameDeactivated: (InternalFrameEvent) -> Unit,
)

private val INTERNAL_FRAME =
    ListenerRegistration<JInternalFrame, InternalFrameListener>(
        JInternalFrame::addInternalFrameListener,
        JInternalFrame::removeInternalFrameListener,
    )

private val INTERNAL_FRAME_CALLBACKS =
    CallbackRegistration<JInternalFrame, InternalFrameCallbacks, InternalFrameListener>(
        adapter = { current ->
            object : InternalFrameListener {
                override fun internalFrameOpened(event: InternalFrameEvent): Unit = current().onFrameOpened(event)

                override fun internalFrameClosing(event: InternalFrameEvent): Unit = current().onFrameClosing(event)

                override fun internalFrameClosed(event: InternalFrameEvent): Unit = current().onFrameClosed(event)

                override fun internalFrameIconified(event: InternalFrameEvent): Unit = current().onFrameIconified(event)

                override fun internalFrameDeiconified(event: InternalFrameEvent): Unit =
                    current().onFrameDeiconified(event)

                override fun internalFrameActivated(event: InternalFrameEvent): Unit = current().onFrameActivated(event)

                override fun internalFrameDeactivated(event: InternalFrameEvent): Unit =
                    current().onFrameDeactivated(event)
            }
        },
        registration = INTERNAL_FRAME,
    )
