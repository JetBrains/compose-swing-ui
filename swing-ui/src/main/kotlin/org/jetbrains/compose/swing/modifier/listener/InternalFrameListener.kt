@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameListener

/**
 * Attaches an [InternalFrameListener]
 * (`addInternalFrameListener`/`removeInternalFrameListener`). Requires a [JInternalFrame] target.
 *
 * @see javax.swing.JInternalFrame.addInternalFrameListener
 */
public fun SwingModifier.internalFrameListener(listener: InternalFrameListener): SwingModifier =
    listener<JInternalFrame, InternalFrameListener>(
        listener,
        JInternalFrame::addInternalFrameListener,
        JInternalFrame::removeInternalFrameListener,
    )
