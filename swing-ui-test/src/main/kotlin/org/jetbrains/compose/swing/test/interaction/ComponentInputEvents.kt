package org.jetbrains.compose.swing.test.interaction

import org.intellij.lang.annotations.MagicConstant
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

/**
 * Delivers [event] to this component, the one way an event reaches a component in this harness.
 *
 * What this adds over [Component.dispatchEvent] is the route the event survives:
 * [KeyboardFocusManager.redispatchEvent]. A [KeyEvent] or a [java.awt.event.FocusEvent] dispatched at a
 * component is claimed by the focus manager before the toolkit is notified, and dropped outright off a
 * realized, focused window - which is every component here. Redispatching is the manager's own way of
 * handing an event on without being consulted again, so the event reaches the AWT event listeners and
 * then the component's own processing. An event the manager does not claim, a mouse event among them,
 * takes exactly plain dispatch's route.
 *
 * Delivering an event is also what lets the recomposer settle a declaration ahead of the repaint a
 * change provokes: the toolkit is handed the event at the start of the dispatch, before the component
 * processes it, so a frame queued from an AWT event listener is queued ahead of the repaint the
 * component asks for while processing.
 *
 * Every function here delivers exactly one event, because that is how the toolkit delivers one: a click
 * is a press, a release and a `MOUSE_CLICKED`, each dispatched from an event-queue cycle of its own, and
 * a gesture that ran them together in a single cycle would hide every ordering that depends on the
 * cycles between. The `perform` gestures on [SwingNodeInteraction] compose these into whole gestures,
 * one event-queue cycle apart, and settle the composition afterwards.
 *
 * Build [event] with this component as its source, so its `getSource` agrees with the component it is
 * delivered to.
 */
internal fun Component.deliverEvent(event: AWTEvent) {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(this, event)
}

/**
 * Delivers a primary-button press at [position], in this component's own coordinates.
 *
 * The press is shaped the way the toolkit delivers one: the current time, [MouseEvent.BUTTON1] as the
 * button, and [InputEvent.BUTTON1_DOWN_MASK] set for as long as the button is held. A UI that reacts to
 * a press - arming a button, positioning a caret, grabbing a slider thumb - therefore reacts to this.
 *
 * The button stays down: nothing releases it until [deliverMouseRelease] does.
 */
internal fun Component.deliverMousePress(
    position: Point = center,
    @MagicConstant(intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()])
    button: Int = MouseEvent.BUTTON1,
    clicks: Int = 1,
    modifiers: Int = 0,
    popupTrigger: Boolean = false,
) {
    deliverEvent(
        mouseEvent(
            MouseEvent.MOUSE_PRESSED,
            position,
            modifiers or InputEvent.getMaskForButton(button),
            MouseGesture(button, clicks, popupTrigger),
        ),
    )
}

/**
 * Delivers a primary-button release at [position], in this component's own coordinates.
 *
 * The release carries no button-down mask, as a real one does not: the button is up by the time the
 * event is delivered. The `MOUSE_CLICKED` that completes a click is [deliverMouseClicked].
 */
internal fun Component.deliverMouseRelease(
    position: Point = center,
    @MagicConstant(intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()])
    button: Int = MouseEvent.BUTTON1,
    clicks: Int = 1,
    modifiers: Int = 0,
    popupTrigger: Boolean = false,
) {
    deliverEvent(
        mouseEvent(MouseEvent.MOUSE_RELEASED, position, modifiers, MouseGesture(button, clicks, popupTrigger)),
    )
}

/**
 * Delivers the `MOUSE_CLICKED` that follows a press and release at the same point, in this component's
 * own coordinates.
 *
 * The toolkit sends this after the release, for a press and release the pointer did not travel between.
 * A UI that resolves a click from the press and release themselves - which is what an
 * [javax.swing.AbstractButton] does - has already acted by the time this arrives; a
 * [java.awt.event.MouseListener.mouseClicked] is what waits for it.
 */
internal fun Component.deliverMouseClicked(
    position: Point = center,
    @MagicConstant(intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()])
    button: Int = MouseEvent.BUTTON1,
    clicks: Int = 1,
    modifiers: Int = 0,
) {
    deliverEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, position, modifiers, MouseGesture(button, clicks)))
}

/**
 * Delivers a `MOUSE_MOVED` at [position], in this component's own coordinates: the pointer travelling
 * over the component with no button held, which is what a rollover state and a tooltip follow.
 */
internal fun Component.deliverMouseMove(position: Point) {
    deliverEvent(mouseEvent(MouseEvent.MOUSE_MOVED, position, 0, MouseGesture(MouseEvent.NOBUTTON, clicks = 0)))
}

/** Delivers a `MOUSE_ENTERED` at [position]: the pointer arriving over the component. */
internal fun Component.deliverMouseEnter(position: Point) {
    deliverEvent(mouseEvent(MouseEvent.MOUSE_ENTERED, position, 0, MouseGesture(MouseEvent.NOBUTTON, clicks = 0)))
}

/** Delivers a `MOUSE_EXITED` at [position]: the pointer leaving the component. */
internal fun Component.deliverMouseExit(position: Point) {
    deliverEvent(mouseEvent(MouseEvent.MOUSE_EXITED, position, 0, MouseGesture(MouseEvent.NOBUTTON, clicks = 0)))
}

/**
 * Delivers one `MOUSE_WHEEL` notch at [position], turning by [rotation] - negative away from the user,
 * positive toward them, as the toolkit reports it.
 *
 * The event scrolls by units, which is the wheel's ordinary mode: [rotation] notches of
 * [MouseWheelEvent.getScrollAmount] units each.
 */
internal fun Component.deliverMouseWheel(
    rotation: Int,
    position: Point,
) {
    deliverEvent(
        MouseWheelEvent(
            this,
            MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            0,
            position.x,
            position.y,
            0,
            false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL,
            WHEEL_SCROLL_UNITS,
            rotation,
        ),
    )
}

/**
 * Delivers one `MOUSE_DRAGGED` step at [position], in this component's own coordinates.
 *
 * The event carries [MouseEvent.NOBUTTON] and keeps [InputEvent.BUTTON1_DOWN_MASK] set, which is how the
 * toolkit reports motion with a button held: the mask names the button for as long as it is down, and
 * the button field names only the button whose own press or release the event is. A UI reading the held
 * button from the mask - a slider thumb, a split-pane divider, a column header - therefore follows the
 * step.
 *
 * Nothing presses the button first and nothing releases it after; deliver [deliverMousePress] and
 * [deliverMouseRelease] around the steps where the UI resolves a whole gesture.
 */
internal fun Component.deliverMouseDrag(position: Point) {
    deliverEvent(
        mouseEvent(
            MouseEvent.MOUSE_DRAGGED,
            position,
            InputEvent.BUTTON1_DOWN_MASK,
            MouseGesture(MouseEvent.NOBUTTON),
        ),
    )
}

/**
 * Delivers a `KEY_PRESSED` for [keyCode], holding [modifiers]. [keyCode] is a `KeyEvent.VK_*` constant
 * and [modifiers] a mask of `InputEvent.*_DOWN_MASK` values.
 *
 * The event travels this component's own key handling: its key listeners run, and its input and action
 * maps are consulted, so a key stroke a widget binds to an action performs that action on the press.
 *
 * The events are delivered to this component as though it were the focus owner. This does not transfer
 * focus, and a component off a realized, focused window can never own it; the component handles the key
 * regardless, which is precisely what makes key handling testable off-screen. A key stroke bound by an
 * ancestor is still found, since the component's own key handling walks its ancestors, but one that a
 * real focus owner elsewhere would have handled goes to this component instead.
 */
internal fun Component.deliverKeyPressed(
    keyCode: Int,
    modifiers: Int = 0,
) {
    deliverEvent(keyEvent(KeyEvent.KEY_PRESSED, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED))
}

/** Delivers the `KEY_RELEASED` that ends the stroke [deliverKeyPressed] began. */
internal fun Component.deliverKeyReleased(
    keyCode: Int,
    modifiers: Int = 0,
) {
    deliverEvent(keyEvent(KeyEvent.KEY_RELEASED, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED))
}

/**
 * Delivers the `KEY_TYPED` that carries [character], which is the event a character is inserted from: a
 * text component's editor actions run on this one, through its document filter, so what a widget refuses
 * to accept it does not accept here either.
 *
 * A typed event names no key code, as the toolkit's own does not - the character is what it carries.
 */
internal fun Component.deliverKeyTyped(character: Char) {
    deliverEvent(keyEvent(KeyEvent.KEY_TYPED, 0, KeyEvent.VK_UNDEFINED, character))
}

/** The middle of this component, in its own coordinates. */
internal val Component.center: Point get() = Point(width / 2, height / 2)

/** A mouse event of [id] at [position] on this component, shaped the way the toolkit delivers one. */
private fun Component.mouseEvent(
    id: Int,
    position: Point,
    modifiersEx: Int,
    gesture: MouseGesture,
): MouseEvent =
    MouseEvent(
        this,
        id,
        System.currentTimeMillis(),
        modifiersEx,
        position.x,
        position.y,
        gesture.clicks,
        gesture.popupTrigger,
        gesture.button,
    )

/**
 * What a mouse event carries besides its position: which button it is about, the running count of the
 * click it belongs to, and whether the platform reads it as the gesture that opens a context menu.
 */
private class MouseGesture(
    val button: Int = MouseEvent.BUTTON1,
    val clicks: Int = 1,
    val popupTrigger: Boolean = false,
)

/** The units one wheel notch scrolls, which is the toolkit's own default. */
private const val WHEEL_SCROLL_UNITS: Int = 3

/** A key event of [id] for [keyCode] and [keyChar], shaped the way the toolkit delivers one. */
private fun Component.keyEvent(
    id: Int,
    modifiersEx: Int,
    keyCode: Int,
    keyChar: Char,
): KeyEvent = KeyEvent(this, id, System.currentTimeMillis(), modifiersEx, keyCode, keyChar)
