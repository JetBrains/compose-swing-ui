package org.jetbrains.compose.swing

import kotlinx.coroutines.yield
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * The user's own input, as the toolkit delivers it: a gesture is several events, each dispatched from an
 * event-queue cycle of its own.
 *
 * The harness publishes its gestures on `SwingNodeInteraction`, which resolves a node and settles the
 * composition afterwards. Neither fits here: these tests run their own [core.SwingRecomposer] so the
 * cadence under test is the library's own rather than one a harness drives, and they measure what is
 * painted between the change and the settlement - the window a settling gesture closes by definition.
 * What is shared with the harness is the shape, not the code.
 *
 * An event goes through [KeyboardFocusManager.redispatchEvent] rather than [Component.dispatchEvent],
 * because a key or focus event dispatched plainly at a component off a realized, focused window is
 * claimed by the focus manager and dropped before the toolkit sees it.
 */
internal fun Component.deliverEvent(event: AWTEvent) {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(this, event)
}

/**
 * Clicks the primary mouse button at [position], in this component's own coordinates: the press, the
 * release and the `MOUSE_CLICKED`, each from an event-queue cycle of its own, as the toolkit posts them.
 *
 * The component's own UI resolves the click as it does a user's - a laid-out [javax.swing.AbstractButton]
 * fires its action because its UI's listener fires it. A click aimed outside the component's bounds, or
 * at one nothing laid out, resolves to nothing.
 */
internal suspend fun Component.click(position: Point = center) {
    deliverEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, position, InputEvent.BUTTON1_DOWN_MASK))
    yield()
    deliverEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, position, 0))
    yield()
    deliverEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, position, 0))
}

/**
 * Drags the primary mouse button from [from] to [to] in one step, both in this component's own
 * coordinates: the press, the step and the release, each from an event-queue cycle of its own.
 *
 * The step carries [MouseEvent.NOBUTTON] with [InputEvent.BUTTON1_DOWN_MASK] still set, which is how the
 * toolkit reports motion with a button held: the mask names the button for as long as it is down, and the
 * button field names only the button whose own press or release the event is. A UI reading the held
 * button from the mask - a column header, a slider thumb, a split-pane divider - therefore follows it.
 */
internal suspend fun Component.drag(
    from: Point,
    to: Point,
) {
    deliverEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, from, InputEvent.BUTTON1_DOWN_MASK))
    yield()
    deliverEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, to, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON))
    yield()
    deliverEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, to, 0))
}

/**
 * Presses and releases the key [keyCode], an event-queue cycle apart.
 *
 * The events travel the component's own key handling: its key listeners run and its input and action
 * maps are consulted, so a key stroke a widget binds to an action - an arrow that steps a slider, say -
 * performs that action. Delivering to a component that owns no focus still reaches its bindings, which
 * is what makes key handling testable off-screen.
 */
internal suspend fun Component.pressKey(keyCode: Int) {
    deliverEvent(keyEvent(KeyEvent.KEY_PRESSED, keyCode, KeyEvent.CHAR_UNDEFINED))
    yield()
    deliverEvent(keyEvent(KeyEvent.KEY_RELEASED, keyCode, KeyEvent.CHAR_UNDEFINED))
}

/**
 * Types [text] one character at a time, each as the `KEY_PRESSED`, `KEY_TYPED`, `KEY_RELEASED` triple the
 * toolkit delivers for it, an event-queue cycle apart. A text component inserts each character at its
 * caret through its document filter and editor actions, and a key it binds an action to runs that
 * action instead. A character outside the Basic Multilingual Plane travels as its two UTF-16 units,
 * because a key event carries a single `char`.
 */
internal suspend fun Component.type(text: String) {
    for (character in text) {
        val keyCode = KeyEvent.getExtendedKeyCodeForChar(character.code)
        deliverEvent(keyEvent(KeyEvent.KEY_PRESSED, keyCode, KeyEvent.CHAR_UNDEFINED))
        yield()
        deliverEvent(keyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, character))
        yield()
        deliverEvent(keyEvent(KeyEvent.KEY_RELEASED, keyCode, KeyEvent.CHAR_UNDEFINED))
        yield()
    }
}

/**
 * Types [text] as a fast typist does: every event for every character delivered before the queue is
 * served at all, so whatever one character queues - a settlement, a repaint - is still pending when the
 * next character arrives. [type] leaves a cycle between events, which serves each before the next
 * lands.
 */
internal fun Component.typeBurst(text: String) {
    for (character in text) {
        val keyCode = KeyEvent.getExtendedKeyCodeForChar(character.code)
        deliverEvent(keyEvent(KeyEvent.KEY_PRESSED, keyCode, KeyEvent.CHAR_UNDEFINED))
        deliverEvent(keyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, character))
        deliverEvent(keyEvent(KeyEvent.KEY_RELEASED, keyCode, KeyEvent.CHAR_UNDEFINED))
    }
}

/** The middle of this component, in its own coordinates. */
internal val Component.center: Point get() = Point(width / 2, height / 2)

/** A mouse event of [id] at [position] on this component, shaped the way the toolkit delivers one. */
private fun Component.mouseEvent(
    id: Int,
    position: Point,
    modifiersEx: Int,
    button: Int = MouseEvent.BUTTON1,
): MouseEvent = MouseEvent(this, id, System.currentTimeMillis(), modifiersEx, position.x, position.y, 1, false, button)

/** A key event of [id] for [keyCode] and [keyChar], shaped the way the toolkit delivers one. */
private fun Component.keyEvent(
    id: Int,
    keyCode: Int,
    keyChar: Char,
): KeyEvent = KeyEvent(this, id, System.currentTimeMillis(), 0, keyCode, keyChar)
