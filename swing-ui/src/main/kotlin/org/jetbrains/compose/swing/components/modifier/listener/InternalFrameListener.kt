// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.components.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
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
 * @param onFrameChange receives the event, whose `id` tells the seven apart and whose `internalFrame`
 *   is the frame that changed.
 * @return this chain with the internal frame callback declared on it.
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
 * [JInternalFrame] target. A change left `null` reports nowhere.
 *
 * Each lambda is read when its event fires, so writing a fresh one on every recomposition
 * registers nothing again.
 *
 * Declaring none at all is refused.
 *
 * @param onFrameOpened runs once, the first time the frame is shown.
 * @param onFrameClosing runs before the close takes effect - from the close control and from
 *   `setClosed(true)` alike.
 * @param onFrameClosed runs when the frame is disposed; a close operation that only hides it never
 *   reports here.
 * @param onFrameIconified runs when the frame is minimized within the desktop.
 * @param onFrameDeiconified runs when the frame is restored from being minimized.
 * @param onFrameActivated runs when the frame becomes the selected one on the desktop.
 * @param onFrameDeactivated runs when another frame takes that selection.
 * @return this chain with the internal frame callbacks declared on it.
 * @see javax.swing.JInternalFrame.addInternalFrameListener
 */
@Suppress("LongParameterList")
// One parameter per method of the listener interface, each optional and named at the call
// site; a caller that does not tell the methods apart declares the single-lambda overload.
public fun SwingModifier.internalFrameListener(
    onFrameOpened: ((InternalFrameEvent) -> Unit)? = null,
    onFrameClosing: ((InternalFrameEvent) -> Unit)? = null,
    onFrameClosed: ((InternalFrameEvent) -> Unit)? = null,
    onFrameIconified: ((InternalFrameEvent) -> Unit)? = null,
    onFrameDeiconified: ((InternalFrameEvent) -> Unit)? = null,
    onFrameActivated: ((InternalFrameEvent) -> Unit)? = null,
    onFrameDeactivated: ((InternalFrameEvent) -> Unit)? = null,
): SwingModifier {
    require(
        onFrameOpened != null || onFrameClosing != null || onFrameClosed != null || onFrameIconified != null ||
            onFrameDeiconified != null || onFrameActivated != null || onFrameDeactivated != null,
    ) {
        "internalFrameListener declares no callback, so the listener it would register can never report anything; " +
            "declare at least one, or leave the modifier off"
    }
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
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the internal frame listener declared on it.
 * @see javax.swing.JInternalFrame.addInternalFrameListener
 */
public fun SwingModifier.internalFrameListener(listener: InternalFrameListener): SwingModifier =
    listener(listener, INTERNAL_FRAME)

/** The lambdas [internalFrameListener] was declared with, as one value the built listener reads. */
@Suppress("LongParameterList")
// One field per method of the listener interface, holding the lambda declared for it.
private class InternalFrameCallbacks(
    val onFrameOpened: ((InternalFrameEvent) -> Unit)?,
    val onFrameClosing: ((InternalFrameEvent) -> Unit)?,
    val onFrameClosed: ((InternalFrameEvent) -> Unit)?,
    val onFrameIconified: ((InternalFrameEvent) -> Unit)?,
    val onFrameDeiconified: ((InternalFrameEvent) -> Unit)?,
    val onFrameActivated: ((InternalFrameEvent) -> Unit)?,
    val onFrameDeactivated: ((InternalFrameEvent) -> Unit)?,
)

private val INTERNAL_FRAME =
    ListenerRegistration<JInternalFrame, InternalFrameListener>(
        name = "internalFrameListener",
        JInternalFrame::addInternalFrameListener,
        JInternalFrame::removeInternalFrameListener,
    )

private val INTERNAL_FRAME_CALLBACKS =
    CallbackRegistration.byCallbackIdentity<JInternalFrame, InternalFrameCallbacks, InternalFrameListener>(
        adapter = { callback ->
            object : InternalFrameListener {
                override fun internalFrameOpened(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameOpened?.invoke(event) }

                override fun internalFrameClosing(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameClosing?.invoke(event) }

                override fun internalFrameClosed(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameClosed?.invoke(event) }

                override fun internalFrameIconified(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameIconified?.invoke(event) }

                override fun internalFrameDeiconified(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameDeiconified?.invoke(event) }

                override fun internalFrameActivated(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameActivated?.invoke(event) }

                override fun internalFrameDeactivated(event: InternalFrameEvent): Unit =
                    callback.report { it.onFrameDeactivated?.invoke(event) }
            }
        },
        registration = INTERNAL_FRAME,
        InternalFrameCallbacks::onFrameOpened,
        InternalFrameCallbacks::onFrameClosing,
        InternalFrameCallbacks::onFrameClosed,
        InternalFrameCallbacks::onFrameIconified,
        InternalFrameCallbacks::onFrameDeiconified,
        InternalFrameCallbacks::onFrameActivated,
        InternalFrameCallbacks::onFrameDeactivated,
    )
