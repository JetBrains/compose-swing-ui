package org.jetbrains.compose.swing.test.interaction

import kotlinx.coroutines.yield
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.platform.hostOs
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JTabbedPane
import javax.swing.TransferHandler
import javax.swing.text.JTextComponent

/**
 * Delivers the event [event] builds for the matched node, then settles the composition.
 *
 * The node is resolved from the query, so [event] is handed the live component the query names, and the
 * composition is settled once the event has been delivered. Every gesture in this file is that shape.
 *
 * Build [event] with the node as its source, so its `getSource` agrees with the component it is
 * delivered to.
 *
 * ```
 * onNodeOfType<JTable>().performEvent { table ->
 *     MouseEvent(table, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 4, 4, 0, false)
 * }
 * ```
 *
 * @param event builds the single event this call delivers; a gesture of several events is several
 *   calls, each an event-queue cycle apart.
 * @return this interaction, for chaining a further gesture or assertion.
 * @throws AssertionError if the query does not resolve to a single node.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performEvent(
    event: (T) -> AWTEvent,
): SwingNodeInteraction<T> = settleAfter { node -> node.deliverEvent(event(node)) }

/**
 * Presses the primary mouse button on the matched node at [position], then settles the composition.
 * [position] defaults to the middle of the node, in the node's own coordinates.
 *
 * The button stays down: nothing releases it until [performMouseRelease] does.
 *
 * @param position where the button goes down; the release takes its own, and nothing carries this
 *   one over to it.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMousePress(
    position: Point? = null,
): SwingNodeInteraction<T> = settleAfter { node -> node.deliverMousePress(position ?: node.center) }

/**
 * Releases the primary mouse button on the matched node at [position], then settles the composition.
 * [position] defaults to the middle of the node, in the node's own coordinates.
 *
 * No `MOUSE_CLICKED` follows - [performClick] is the whole gesture.
 *
 * @param position where the button comes up, which need not be where it went down - a release
 *   elsewhere is how a drag ends.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseRelease(
    position: Point? = null,
): SwingNodeInteraction<T> = settleAfter { node -> node.deliverMouseRelease(position ?: node.center) }

/**
 * Clicks the primary mouse button on the matched node at [position], then settles the composition.
 * [position] defaults to the middle of the node, in the node's own coordinates.
 *
 * The gesture is the press, the release and the `MOUSE_CLICKED` the toolkit delivers for one click, each
 * from an event-queue cycle of its own, so the node's own UI decides what the click means exactly as it
 * does for a user: a click on a laid-out [javax.swing.AbstractButton] fires its action because the UI's
 * own listener fires it, not because the test asked the button to.
 *
 * [button] names which button is clicked, [clicks] how many times - `2` is a double click, delivered as
 * the toolkit delivers one, each click carrying its running count - and [modifiers] the keys held
 * throughout, which is how a shift- or control-click extends a selection. The platform's context-menu
 * gesture is [performContextClick], which carries the popup trigger a secondary click alone does not.
 *
 * A click lands where a user's click would land. Aimed outside the node's bounds, or at a node that has
 * no size because nothing laid it out, the UI resolves it to nothing and the node does not react.
 *
 * @param position the point every event of the click carries, so press, release and click all land
 *   together.
 * @param button a `MouseEvent.BUTTON*` constant; the primary button by default.
 * @param clicks the number of clicks in the gesture, one by default; fewer than one is rejected.
 * @param modifiers a mask of `InputEvent.*_DOWN_MASK` values, none by default.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performClick(
    position: Point? = null,
    @MagicConstant(intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()])
    button: Int = MouseEvent.BUTTON1,
    clicks: Int = 1,
    @MagicConstant(flagsFromClass = InputEvent::class)
    modifiers: Int = 0,
): SwingNodeInteraction<T> =
    settleAfter { node ->
        val at = position ?: node.center
        node.clickTimes(at, button, clicks, modifiers, popupTriggerOn = null)
    }

/**
 * Makes the platform's context-menu gesture on the matched node at [position], then settles the
 * composition. [position] defaults to the middle of the node, in the node's own coordinates.
 *
 * The gesture is a secondary-button click carrying the popup trigger on the one event the host platform
 * carries it on: the release on Windows, the press everywhere else. A component reading
 * `isPopupTrigger` off both events is asked once, the way the toolkit asks it.
 *
 * @param position where the gesture lands, which is the point a menu opened from it is placed at.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performContextClick(
    position: Point? = null,
): SwingNodeInteraction<T> =
    settleAfter { node ->
        val at = position ?: node.center
        node.clickTimes(
            at,
            MouseEvent.BUTTON3,
            clicks = 1,
            modifiers = 0,
            popupTriggerOn = if (hostOs.isWindows) MouseEvent.MOUSE_RELEASED else MouseEvent.MOUSE_PRESSED,
        )
    }

/**
 * Moves the pointer to [position] over the matched node, then settles the composition. [position] is in
 * the node's own coordinates.
 *
 * One `MOUSE_MOVED` with no button held, which is what a rollover state and a tooltip follow. Arriving
 * over the node and leaving it are [performMouseEnter] and [performMouseExit].
 *
 * @param position the point the move is reported at; no pointer position is kept between calls, so
 *   every gesture carries its own.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseMove(position: Point): SwingNodeInteraction<T> =
    settleAfter { node -> node.deliverMouseMove(position) }

/**
 * Brings the pointer onto the matched node at [position] and settles the composition. [position]
 * defaults to the middle of the node, in the node's own coordinates.
 *
 * @param position the point the pointer arrives at, which a UI takes its rollover state from.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseEnter(
    position: Point? = null,
): SwingNodeInteraction<T> = settleAfter { node -> node.deliverMouseEnter(position ?: node.center) }

/**
 * Takes the pointer off the matched node at [position] and settles the composition. [position] defaults
 * to the middle of the node, in the node's own coordinates.
 *
 * @param position the point the pointer leaves from; a UI clearing a rollover state does so
 *   wherever this lands.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseExit(
    position: Point? = null,
): SwingNodeInteraction<T> = settleAfter { node -> node.deliverMouseExit(position ?: node.center) }

/**
 * Turns the mouse wheel over the matched node by [rotation] notches - negative away from the user,
 * positive toward them - then settles the composition. [position] defaults to the middle of the node, in
 * the node's own coordinates.
 *
 * @param rotation how many notches the wheel turns; the event scrolls by units, so every notch
 *   moves the same amount.
 * @param position where the wheel turns, which decides what a UI resolves under the pointer.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseWheel(
    rotation: Int,
    position: Point? = null,
): SwingNodeInteraction<T> = settleAfter { node -> node.deliverMouseWheel(rotation, position ?: node.center) }

/**
 * Clicks [clicks] times at [position], as the toolkit delivers a repeated click: each click is its own
 * press, release and `MOUSE_CLICKED`, an event-queue cycle apart, and each carries the running count -
 * so a component reading `clickCount` sees 1 then 2, which is how a double click is told from two
 * single ones.
 */
private suspend fun Component.clickTimes(
    position: Point,
    button: Int,
    clicks: Int,
    modifiers: Int,
    popupTriggerOn: Int?,
) {
    require(clicks >= 1) { "A click gesture is at least one click, and was asked for $clicks." }
    for (count in 1..clicks) {
        deliverMousePress(position, button, count, modifiers, popupTriggerOn == MouseEvent.MOUSE_PRESSED)
        yield()
        deliverMouseRelease(position, button, count, modifiers, popupTriggerOn == MouseEvent.MOUSE_RELEASED)
        yield()
        deliverMouseClicked(position, button, count, modifiers)
        if (count < clicks) yield()
    }
}

/**
 * Drags the primary mouse button across the matched node from [from] to [to], then settles the
 * composition. Both points are in the node's own coordinates.
 *
 * The gesture is a press at [from], one `MOUSE_DRAGGED` step at [to], and a release at [to], each from
 * an event-queue cycle of its own. No `MOUSE_CLICKED` is delivered: a real drag ends in a release and
 * nothing else.
 *
 * The pointer is reported at [to] in one step. A UI that samples intermediate positions, rather than
 * acting on the position it is given, sees only the endpoint; deliver the intermediate steps yourself
 * with [performEvent] where that matters.
 *
 * @param from where the button goes down, which is where a UI that grabs on the press takes hold.
 * @param to the endpoint; a UI acting on the distance from [from] sees the whole drag in one step.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseDrag(
    from: Point,
    to: Point,
): SwingNodeInteraction<T> =
    settleAfter { node ->
        node.deliverMousePress(from)
        yield()
        node.deliverMouseDrag(to)
        yield()
        node.deliverMouseRelease(to)
    }

/**
 * Presses and releases the key [keyCode] on the matched node, holding [modifiers] for both, then
 * settles the composition. [keyCode] is a `KeyEvent.VK_*` constant and [modifiers] a mask of
 * `InputEvent.*_DOWN_MASK` values, empty by default.
 *
 * The press and the release arrive an event-queue cycle apart, and travel the node's own key handling:
 * its key listeners run and its input and action maps are consulted, so a key stroke a widget binds to
 * an action performs that action. This is the gesture for a key that means something other than a
 * character - Enter, Escape, an arrow, a shortcut. It delivers no `KEY_TYPED`, which is the event that
 * inserts a character; type text with [performTyping].
 *
 * The events are delivered to the node as though it owned the focus; see [deliverKeyPressed] for what
 * that does and does not mean off a realized window.
 *
 * @param keyCode the code both events carry, which is what an input map matches a key stroke on.
 * @param modifiers the mask held across both events; the modifier keys themselves are not
 *   delivered, only reported as held.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performKeyPress(
    keyCode: Int,
    modifiers: Int = 0,
): SwingNodeInteraction<T> =
    settleAfter { node ->
        node.deliverKeyPressed(keyCode, modifiers)
        yield()
        node.deliverKeyReleased(keyCode, modifiers)
    }

/**
 * Types [text] on the matched node one character at a time, then settles the composition.
 *
 * Each character arrives as the `KEY_PRESSED`, `KEY_TYPED`, `KEY_RELEASED` triple the toolkit delivers
 * for it, each from an event-queue cycle of its own, so the component's own key bindings decide what it
 * does with each one. A text component inserts a character **at its caret** through its document filter
 * and editor actions; a key it binds an action to runs that action instead, so a tab and a newline type
 * into a text area and do nothing in a single-line field. Content a component's keys will not produce
 * goes in with [performTextPaste].
 *
 * The character carries its own case: an upper-case letter is typed as itself, with no Shift held. Type
 * a shortcut, or any key that is not a character, with [performKeyPress].
 *
 * [text] may be in any script and any plane. A character outside the Basic Multilingual Plane travels
 * as its two UTF-16 units, one `KEY_TYPED` each, because a key event carries a single `char`, and the
 * document reassembles it.
 *
 * This delivers only keystrokes, so it does not exercise a component that reads committed text off an
 * `InputMethodEvent`.
 *
 * @param text typed character by character; an empty text delivers no key event and only settles
 *   the composition.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performTyping(text: @Nls String): SwingNodeInteraction<T> =
    settleAfter { node -> node.type(text) }

/**
 * Types [text] on this component the way the toolkit delivers it: every character as its own
 * `KEY_PRESSED`, `KEY_TYPED` and `KEY_RELEASED`, one event-queue cycle apart.
 */
internal suspend fun Component.type(text: @Nls String) {
    for (character in text) {
        val keyCode =
            KeyEvent
                .getExtendedKeyCodeForChar(character.code)
        deliverKeyPressed(keyCode)
        yield()
        deliverKeyTyped(character)
        yield()
        deliverKeyReleased(keyCode)
        yield()
    }
}

/** Resolves the matched node, makes [gesture] on it, and settles the composition. */
private suspend fun <T : Component> SwingNodeInteraction<T>.settleAfter(
    gesture: suspend (T) -> Unit,
): SwingNodeInteraction<T> {
    gesture(fetch())
    test.awaitIdle()
    return this
}

/**
 * Types [text] onto the end of the matched [JTextComponent]'s current content, then settles the
 * composition.
 *
 * The caret is placed after the existing text and [text] is typed there, character by character,
 * through the component's own key handling - so the edit passes its document filter and its editor
 * actions, and a character the component refuses is refused here too. [performTyping] types at
 * wherever the caret already stands instead.
 *
 * @param text typed at the end of the existing content; what the document refuses never reaches it,
 *   so the node can end up holding less than this.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performTextInput(
    text: @Nls String,
): SwingNodeInteraction<T> {
    val component = textComponent()
    component.caretPosition = component.document.length
    component.type(text)
    test.awaitIdle()
    return this
}

/**
 * Replaces the matched [JTextComponent]'s entire content with [text], then settles the composition.
 *
 * The existing text is selected and [text] typed over it, which is the gesture a user replaces
 * content with; see [performTextInput] for what typing passes through. Replacing with an empty
 * [text] is the selection deleted by the first keystroke of nothing, so it leaves the content
 * selected rather than cleared - type a backspace with [performKeyPress] to clear it.
 *
 * @param text typed over the selected content, so its first character replaces the whole of it.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performTextReplacement(
    text: @Nls String,
): SwingNodeInteraction<T> {
    val component = textComponent()
    component.selectAll()
    component.type(text)
    test.awaitIdle()
    return this
}

/**
 * Pastes [text] into the matched [JTextComponent] over its current selection, then settles the
 * composition.
 *
 * The text goes through the component's own [TransferHandler], as a paste does, so a `DocumentFilter`
 * sees one replace rather than a keystroke each. Nothing is put on the system clipboard.
 *
 * A paste runs no key binding, so it carries content a component's keys will not produce - a tab into a
 * single-line field, say. The document still applies its own rules: a single-line field replaces a
 * pasted newline with a space.
 *
 * Fails where the component has no `TransferHandler`, or where the handler refuses the text.
 *
 * @param text handed to the handler as plain text, so no formatting a real clipboard could carry is
 *   part of it.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performTextPaste(
    text: @Nls String,
): SwingNodeInteraction<T> {
    val component = textComponent()
    val handler =
        component.transferHandler
            ?: throw AssertionError("Node '$description' has no TransferHandler, so it cannot be pasted into.")
    if (!handler.importData(component, StringSelection(text))) {
        throw AssertionError("Node '$description' refused the pasted text.")
    }
    test.awaitIdle()
    return this
}

/** The matched node as a [JTextComponent], failing where it is not one. */
private fun SwingNodeInteraction<*>.textComponent(): JTextComponent {
    val component = resolve()
    if (component !is JTextComponent) {
        throw AssertionError(
            "Node '$description' is a ${component.javaClass.simpleName}, " +
                "which cannot receive text input (expected a JTextComponent).",
        )
    }
    return component
}

/**
 * Delivers a focus-gained notification to the matched node and settles the composition.
 *
 * The node processes a real [FocusEvent] of id [FocusEvent.FOCUS_GAINED]: its own
 * `processFocusEvent` runs, and every registered [java.awt.event.FocusListener] is notified. That
 * is what makes widget behavior driven by focus observable - a formatted text field reformats its
 * value from `processFocusEvent`, not from a listener, so a test that fired the listeners itself
 * would never see it.
 *
 * This does **not** transfer focus: the node does not become the focus owner, and
 * [SwingNodeInteraction.assertIsFocusOwner] still fails afterwards. Ownership is a windowing-system fact
 * that needs a realized, focused window, which the harness root never has; the notification is delivered
 * to the node regardless of that, which is precisely why focus-driven behavior can be tested off-screen.
 *
 * A [temporary] notification is one the widget may treat as a focus change it should not act on.
 *
 * @param temporary marks the notification temporary; `false` by default, which is the permanent
 *   notification a widget treats as really having the focus.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performFocusGained(
    temporary: Boolean = false,
): SwingNodeInteraction<T> = deliverFocusEvent(FocusEvent.FOCUS_GAINED, temporary)

/**
 * Delivers a focus-lost notification to the matched node and settles the composition.
 *
 * The node processes a real [FocusEvent] of id [FocusEvent.FOCUS_LOST]; see [performFocusGained]
 * for what is delivered, what a [temporary] notification means, and why this is not a focus
 * transfer.
 *
 * @param temporary `false` by default; a formatted field commits its value on a permanent loss and
 *   leaves it alone on a temporary one.
 * @return this interaction, for chaining a further gesture or assertion.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performFocusLost(
    temporary: Boolean = false,
): SwingNodeInteraction<T> = deliverFocusEvent(FocusEvent.FOCUS_LOST, temporary)

private suspend fun <T : Component> SwingNodeInteraction<T>.deliverFocusEvent(
    id: Int,
    temporary: Boolean,
): SwingNodeInteraction<T> {
    val component = resolve()
    component.deliverEvent(FocusEvent(component, id, temporary))
    test.awaitIdle()
    return this
}

/**
 * Clicks the tab at [index] on the matched [JTabbedPane] and settles the composition.
 *
 * The click is a real [MouseEvent] aimed at the middle of that tab, so the pane's own UI decides
 * what the click means, exactly as it does for a user: it maps the position back to a tab and
 * selects it, which is what publishes the change to the pane's listeners and so to the callbacks a
 * wrapper declares. Writing the pane's selected index instead is the composition's own kind of
 * write rather than the user's, and a wrapper that tells those two apart cannot be tested that way.
 *
 * Clicking does nothing where a user's click would also do nothing: on a tab of a disabled pane, or
 * on a disabled tab. The strip stays where it was and the callbacks hear nothing.
 *
 * @param index the tab's index in the pane, which a strip laid out in several runs may not show in
 *   that order.
 * @return this interaction, for chaining a further gesture or assertion.
 * @throws AssertionError if the query does not resolve to a single node, if that node is not a
 * [JTabbedPane], if it has no tab at [index], or if the strip does not currently show that tab - a
 * click aimed at a tab with no position on the strip would land on nothing.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performTabClick(index: Int): SwingNodeInteraction<T> {
    val component = resolve()
    if (component !is JTabbedPane) {
        throw AssertionError(
            "Node '$description' is a ${component.javaClass.simpleName}, which has no tabs " +
                "to click (expected a JTabbedPane).",
        )
    }
    val position =
        clickPositionOf(component, index)
            ?: throw AssertionError(noClickablePositionMessage(component, index))
    component.deliverMousePress(position)
    yield()
    component.deliverMouseRelease(position)
    yield()
    component.deliverMouseClicked(position)
    test.awaitIdle()
    return this
}

/** Why the tab at [index] of [pane] cannot be clicked, phrased for whichever reason applies. */
private fun SwingNodeInteraction<*>.noClickablePositionMessage(
    pane: JTabbedPane,
    index: Int,
): String =
    if (index < 0 || index >= pane.tabCount) {
        "Node '$description' has ${pane.tabCount} tab(s), so there is no tab at index " +
            "$index to click."
    } else {
        "The tab at index $index of node '$description' occupies no position the pane " +
            "resolves back to it, so a click aimed at it would land on nothing. That is the state " +
            "of a tab the strip does not currently show: the pane may be laid out too narrow to " +
            "fit the tab, or the tab may sit outside the run of tabs the strip shows. Give the " +
            "pane room for the tab, or bring the tab into the visible run first."
    }

/**
 * The point inside the tab at [index] that [pane] resolves back to that same tab, or null when there is
 * none. Both the tab's bounds and the resolution come from the pane's UI, so a position this returns is
 * one the UI's own click handling maps to [index].
 */
private fun clickPositionOf(
    pane: JTabbedPane,
    index: Int,
): Point? {
    val bounds = if (index in 0 until pane.tabCount) pane.getBoundsAt(index) else null
    val position = bounds?.let { Point(it.x + it.width / 2, it.y + it.height / 2) } ?: return null
    return if (pane.ui?.tabForCoordinate(pane, position.x, position.y) == index) position else null
}
