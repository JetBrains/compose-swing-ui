package org.jetbrains.compose.swing.modifier.interaction

import java.awt.Component
import java.awt.event.MouseEvent

/** A [MouseEvent] carrying the platform popup gesture over [component]. */
internal fun popupTrigger(component: Component): MouseEvent = MouseEvent(
    component,
    MouseEvent.MOUSE_PRESSED,
    0L,
    0,
    3,
    4,
    1,
    // popupTrigger = true: this is the platform popup gesture.
    true,
)
