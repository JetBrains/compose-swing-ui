package org.jetbrains.compose.swing.modifier.keyboard

import org.jetbrains.compose.swing.constants.FocusCondition
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

/*
 * Keyboard SwingModifiers - raw key events and key-stroke -> action bindings.
 *
 * Callbacks are read live, so passing fresh lambdas each recomposition is fine. The focus scope of a
 * key-stroke binding is expressed with [FocusCondition].
 */

/**
 * Installs a [KeyListener] whose every event is forwarded to [onKeyEvent].
 *
 * [onKeyEvent] receives each [KeyEvent] (`KEY_PRESSED` / `KEY_RELEASED` / `KEY_TYPED`, read from
 * `event.id`) and returns `true` if it consumed the event - in which case the event stops further
 * processing, mirroring Compose's `onKeyEvent`. The component must be focusable and focused to
 * receive these (see [focusable]); for shortcuts that should work regardless of which component holds
 * focus, prefer [onKeyStroke].
 *
 * Multiple `onKeyEvent` applications all fire. [onKeyEvent] is read live, so passing a fresh lambda
 * each recomposition is fine.
 */
public fun SwingModifier.onKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): SwingModifier =
    this then KeyEventElement(onKeyEvent)

/**
 * Binds a single [KeyStroke] to [onAction] via the component's `InputMap`/`ActionMap` - the
 * idiomatic Swing path for shortcuts. [condition] selects the focus scope (a [FocusCondition]
 * `JComponent.WHEN_*` value) and defaults to [JComponent.WHEN_FOCUSED].
 *
 * Distinct keystrokes compose independently. Binding the **same** [keyStroke] in the same [condition]
 * twice on one component throws at install. [onAction] is read live, so passing a fresh lambda each
 * recomposition is fine. Requires a [JComponent] target.
 */
public fun SwingModifier.onKeyStroke(
    keyStroke: KeyStroke,
    @FocusCondition condition: Int = JComponent.WHEN_FOCUSED,
    onAction: () -> Unit,
): SwingModifier = this then KeyStrokeElement(keyStroke, condition, onAction)

/**
 * Convenience overload of [onKeyStroke] that parses [keyStroke] via `KeyStroke.getKeyStroke(String)`
 * (e.g. `"ctrl S"`, `"meta shift Z"`). Throws at install if the string is not a valid key-stroke
 * descriptor.
 */
public fun SwingModifier.onKeyStroke(
    keyStroke: String,
    @FocusCondition condition: Int = JComponent.WHEN_FOCUSED,
    onAction: () -> Unit,
): SwingModifier {
    val parsed =
        KeyStroke.getKeyStroke(keyStroke)
            ?: error("onKeyStroke could not parse the key-stroke descriptor \"$keyStroke\"")
    return onKeyStroke(parsed, condition, onAction)
}

/**
 * The additive [SwingModifier.Element] backing [onKeyEvent]. Installs a [KeyListener] once and reads
 * [onKeyEvent] from the node's field, refreshed by `update`, so callbacks stay current.
 */
private class KeyEventElement(
    private val onKeyEvent: (KeyEvent) -> Boolean,
) : SwingModifier.Element<Component, KeyEventElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onKeyEvent = onKeyEvent
    }

    class Node : SwingModifier.Node<Component>() {
        var onKeyEvent: (KeyEvent) -> Boolean = { false }

        private val listener =
            object : KeyListener {
                override fun keyTyped(e: KeyEvent): Unit = dispatch(e)

                override fun keyPressed(e: KeyEvent): Unit = dispatch(e)

                override fun keyReleased(e: KeyEvent): Unit = dispatch(e)

                private fun dispatch(e: KeyEvent) {
                    if (onKeyEvent(e)) e.consume()
                }
            }

        override fun onAttach(): Unit = component.addKeyListener(listener)

        override fun onDetach(): Unit = component.removeKeyListener(listener)
    }
}

/**
 * The additive [SwingModifier.Element] backing [onKeyStroke]. On attach it registers a binding in
 * `getInputMap(condition)` + `actionMap` under a unique key (the node instance), reading [onAction]
 * from the node's field refreshed by `update`; on detach it removes both entries. Binding the same
 * [keyStroke] in the same [condition] twice throws.
 */
private class KeyStrokeElement(
    private val keyStroke: KeyStroke,
    @param:FocusCondition private val condition: Int,
    private val onAction: () -> Unit,
) : SwingModifier.Element<JComponent, KeyStrokeElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node(keyStroke, condition)

    override fun update(node: Node) {
        node.onAction = onAction
    }

    class Node(
        private val keyStroke: KeyStroke,
        @param:FocusCondition private val condition: Int,
    ) : SwingModifier.Node<JComponent>() {
        var onAction: () -> Unit = {}

        // A unique ActionMap key per application (the node instance), so removing one binding never
        // clobbers another's entry.
        private val actionKey: Any = this

        override fun onAttach() {
            val component = component
            val inputMap = component.getInputMap(condition)
            val actionMap = component.actionMap

            // Detect a same-keystroke double-bind in the same condition: if this keystroke already maps
            // to an action key whose action is one of ours, another onKeyStroke owns it. Swing maps one
            // keystroke to one action per condition, so the second binding would silently shadow the
            // first.
            val existingKey = inputMap.get(keyStroke)
            if (existingKey != null && actionMap.get(existingKey) is KeyStrokeAction) {
                error(
                    "onKeyStroke($keyStroke) is already bound in this focus condition on this " +
                        "component; a key-stroke can only be bound once per condition. Use distinct " +
                        "key-strokes or a single binding.",
                )
            }

            inputMap.put(keyStroke, actionKey)
            actionMap.put(actionKey, KeyStrokeAction { onAction() })
        }

        override fun onDetach() {
            val component = component
            val inputMap = component.getInputMap(condition)
            // Remove only our own entries, leaving any binding installed elsewhere intact.
            if (inputMap.get(keyStroke) === actionKey) inputMap.remove(keyStroke)
            component.actionMap.remove(actionKey)
        }
    }
}

/**
 * The [javax.swing.Action] installed by [KeyStrokeElement]. A dedicated type so that collision
 * detection can recognize a binding this library owns.
 */
private class KeyStrokeAction(
    private val onAction: () -> Unit,
) : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?): Unit = onAction()
}
