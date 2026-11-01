@file:JvmMultifileClass
@file:JvmName("InteractionTestKt")

package org.jetbrains.compose.swing.test.interaction

import kotlinx.coroutines.yield
import org.intellij.lang.annotations.MagicConstant
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Posts mouse events through the node's showing window and awaits idle after the block.
 * Positions use the node's coordinates. Pointer position and held buttons carry over between calls.
 * Events are posted after the block, one event queue turn apart.
 * A failed call leaves the gesture state undefined for the rest of the test.
 *
 * @throws AssertionError if the node is not in a showing window.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performMouseInput(
    block: MouseInjectionScope.() -> Unit,
): SwingNodeInteraction<T> {
    val node = resolve()
    val window = SwingUtilities.getWindowAncestor(node)
    if (window == null || !node.isShowing) throw AssertionError("Node '$description' is not in a showing window.")
    val scope = MouseScope(node, window, test.mouseState)
    scope.block()
    for (event in scope.events) {
        Toolkit.getDefaultToolkit().systemEventQueue.postEvent(event)
        yield()
    }
    test.awaitIdle()
    return this
}

/** Records mouse input at positions in the matched component's coordinates. */
public sealed interface MouseInjectionScope {
    /** The middle of the matched component. */
    public val center: Point

    /** Moves the pointer, dragging when a button is held. */
    public fun moveTo(position: Point)

    /**
     * Presses a button at the current pointer position.
     *
     * @param button [MouseEvent.BUTTON1], [MouseEvent.BUTTON2], or [MouseEvent.BUTTON3]; primary by default.
     */
    public fun press(
        @MagicConstant(
            intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()],
        )
        button: Int = MouseEvent.BUTTON1,
    )

    /**
     * Releases a held button and clicks if the pointer has not moved.
     *
     * @param button [MouseEvent.BUTTON1], [MouseEvent.BUTTON2], or [MouseEvent.BUTTON3]; primary by default.
     */
    public fun release(
        @MagicConstant(
            intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()],
        )
        button: Int = MouseEvent.BUTTON1,
    )

    /**
     * Turns the wheel at the current pointer position.
     *
     * @param rotation notches to turn; negative away from the user, positive toward them.
     */
    public fun scroll(rotation: Int)
}

/**
 * Clicks at [position] with [button].
 *
 * @param position the click position in the matched component's coordinates; its center by default.
 * @param button [MouseEvent.BUTTON1], [MouseEvent.BUTTON2], or [MouseEvent.BUTTON3]; primary by default.
 */
public fun MouseInjectionScope.click(
    position: Point = center,
    @MagicConstant(intValues = [MouseEvent.BUTTON1.toLong(), MouseEvent.BUTTON2.toLong(), MouseEvent.BUTTON3.toLong()])
    button: Int = MouseEvent.BUTTON1,
) {
    moveTo(position)
    press(button)
    release(button)
}

/** Clicks the secondary button at [position]. */
public fun MouseInjectionScope.contextClick(position: Point = center) {
    click(position, MouseEvent.BUTTON3)
}

internal class MouseState {
    var position: Point? = null
    val pressed = mutableSetOf<Int>()
    val moved = mutableSetOf<Int>()
}

private class MouseScope(
    val node: Component,
    val window: Window,
    val state: MouseState,
) : MouseInjectionScope {
    val events = mutableListOf<MouseEvent>()
    override val center: Point get() = node.center
    private val held: Int get() =
        state.pressed.fold(0) { mask, button -> mask or InputEvent.getMaskForButton(button) }

    override fun moveTo(position: Point) {
        val screen = Point(position)
        SwingUtilities.convertPointToScreen(screen, node)
        if (state.position != screen) state.moved.addAll(state.pressed)
        state.position = screen
        event(if (state.pressed.isEmpty()) MouseEvent.MOUSE_MOVED else MouseEvent.MOUSE_DRAGGED)
    }

    override fun press(button: Int) {
        checkNotNull(state.position) { "Move the pointer before pressing a button." }
        check(button !in state.pressed) { "Button $button is already pressed." }
        state.pressed.add(button)
        state.moved.remove(button)
        event(
            MouseEvent.MOUSE_PRESSED,
            button,
            button == MouseEvent.BUTTON3 && popupTriggerEventId == MouseEvent.MOUSE_PRESSED,
        )
    }

    override fun release(button: Int) {
        check(state.pressed.remove(button)) { "Button $button is not pressed." }
        event(
            MouseEvent.MOUSE_RELEASED,
            button,
            button == MouseEvent.BUTTON3 && popupTriggerEventId == MouseEvent.MOUSE_RELEASED,
        )
        if (!state.moved.remove(button)) event(MouseEvent.MOUSE_CLICKED, button)
    }

    override fun scroll(rotation: Int) {
        val point = position()
        events += window.mouseWheelEvent(rotation, point, held)
    }

    private fun position(): Point {
        val point = Point(checkNotNull(state.position) { "Move the pointer first." })
        SwingUtilities.convertPointFromScreen(point, window)
        return point
    }

    private fun event(
        id: Int,
        button: Int = MouseEvent.NOBUTTON,
        trigger: Boolean = false,
    ) {
        val point = position()
        events +=
            window.mouseEvent(
                id,
                point,
                held,
                MouseGesture(button, if (button == MouseEvent.NOBUTTON) 0 else 1, trigger),
            )
    }
}
