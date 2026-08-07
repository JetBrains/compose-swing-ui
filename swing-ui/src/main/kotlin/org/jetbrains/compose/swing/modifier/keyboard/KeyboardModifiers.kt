package org.jetbrains.compose.swing.modifier.keyboard

import org.jetbrains.compose.swing.constants.FocusCondition
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/*
 * Keyboard SwingModifiers - raw key events and key-stroke -> action bindings.
 *
 * Two key-stroke elements are equal when they declare the same key-stroke and scope and hold the *same*
 * callback - identity, because a lambda is what it captures - so a declaration made of hoisted
 * callbacks refreshes nothing, while one made of fresh lambdas refreshes the node's fields on every
 * pass.
 */

/**
 * Installs a [KeyListener] whose every event is forwarded to [onKeyEvent].
 *
 * [onKeyEvent] receives each [KeyEvent] (`KEY_PRESSED` / `KEY_RELEASED` / `KEY_TYPED`, read from
 * `event.id`) and returns `true` if it consumed the event - in which case the event stops further
 * processing, mirroring Compose's `onKeyEvent`. The component must be focusable and focused to
 * receive these (see [focusable][org.jetbrains.compose.swing.modifier.interaction.focusable]); for
 * shortcuts that should work regardless of which component holds focus, prefer [onKeyStroke].
 *
 * Multiple `onKeyEvent` applications all fire. [onKeyEvent] is read live, so passing a fresh lambda
 * each recomposition is fine.
 *
 * @see java.awt.Component.addKeyListener
 */
public fun SwingModifier.onKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): SwingModifier =
    this then KeyEventElement(onKeyEvent)

/**
 * Binds a single [KeyStroke] to [onAction] via the component's `InputMap`/`ActionMap` - the
 * idiomatic Swing path for shortcuts. [condition] selects the focus scope (a [FocusCondition]
 * `JComponent.WHEN_*` value) and defaults to [JComponent.WHEN_FOCUSED].
 *
 * Distinct keystrokes compose independently. Binding the **same** [keyStroke] in the same [condition]
 * twice on one component is reported once the change pass has settled, so two bindings that exchange
 * their keystrokes in one pass are unaffected. [onAction] is read live, so passing a fresh lambda each
 * recomposition is fine. Requires a [JComponent] target.
 *
 * @see javax.swing.JComponent.getInputMap
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
 *
 * @see javax.swing.JComponent.getInputMap
 * @see javax.swing.KeyStroke.getKeyStroke
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
 * The additive [SwingModifier.NodeElement] backing [onKeyEvent]. Installs a [KeyListener] once and reads
 * [onKeyEvent] from the node's field, refreshed by `update`, so callbacks stay current.
 */
private class KeyEventElement(
    private val onKeyEvent: (KeyEvent) -> Boolean,
) : SwingModifier.NodeElement<Component, KeyEventElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onKeyEvent = onKeyEvent
    }

    override fun equals(other: Any?): Boolean = other is KeyEventElement && onKeyEvent === other.onKeyEvent

    override fun hashCode(): Int = System.identityHashCode(onKeyEvent)

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
 * The additive [SwingModifier.NodeElement] backing [onKeyStroke]. Each `update` re-keys the binding in
 * `getInputMap(condition)` + `actionMap` under a unique key (the node instance) for the declared
 * [keyStroke]/[condition] pair, unbinding the previous pair first when either changed, and reads
 * [onAction] from the node's field refreshed by `update`; `onDetach` removes the currently bound pair.
 * Binding the same [keyStroke] in the same [condition] twice is reported once the change pass has
 * settled.
 */
private class KeyStrokeElement(
    private val keyStroke: KeyStroke,
    @param:FocusCondition private val condition: Int,
    private val onAction: () -> Unit,
) : SwingModifier.NodeElement<JComponent, KeyStrokeElement.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onAction = onAction
        node.rebind(keyStroke, condition)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyStrokeElement) return false
        if (onAction !== other.onAction) return false
        if (condition != other.condition) return false
        return keyStroke == other.keyStroke
    }

    override fun hashCode(): Int {
        var result = keyStroke.hashCode()
        result = 31 * result + condition
        result = 31 * result + System.identityHashCode(onAction)
        return result
    }

    class Node : SwingModifier.Node<JComponent>() {
        var onAction: () -> Unit = {}

        // A unique ActionMap key per application (the node instance), so removing one binding never
        // clobbers another's entry.
        private val actionKey: Any = this

        private var boundKeyStroke: KeyStroke? = null

        @FocusCondition
        private var boundCondition: Int = JComponent.WHEN_FOCUSED

        /** Registers this node so a sibling's deferred check also verifies it; see [scheduleOwnershipCheck]. */
        override fun onAttach() {
            liveNodesByComponent.getOrPut(component) { mutableSetOf() }.add(this)
        }

        /** Binds [keyStroke]/[condition], first unbinding the currently bound pair if either differs. */
        fun rebind(
            keyStroke: KeyStroke,
            @FocusCondition condition: Int,
        ) {
            if (boundKeyStroke == keyStroke && boundCondition == condition) return
            unbind()
            bind(keyStroke, condition)
        }

        private fun bind(
            keyStroke: KeyStroke,
            @FocusCondition condition: Int,
        ) {
            component.getInputMap(condition).put(keyStroke, actionKey)
            component.actionMap.put(actionKey, KeyStrokeAction { onAction() })
            boundKeyStroke = keyStroke
            boundCondition = condition
            scheduleOwnershipCheck()
        }

        private fun unbind() {
            val keyStroke = boundKeyStroke ?: return
            val inputMap = component.getInputMap(boundCondition)
            // Remove only our own entries, leaving any binding installed elsewhere intact.
            if (inputMap.get(keyStroke) === actionKey) inputMap.remove(keyStroke)
            component.actionMap.remove(actionKey)
            boundKeyStroke = null
        }

        /** Reports if another [KeyStrokeAction] has taken over this node's bound key-stroke. */
        private fun checkOwnership() {
            val keyStroke = boundKeyStroke ?: return
            val currentKey = component.getInputMap(boundCondition).get(keyStroke)
            if (currentKey !== actionKey && component.actionMap.get(currentKey) is KeyStrokeAction) {
                error(
                    "onKeyStroke($keyStroke) is already bound in this focus condition on this " +
                        "component; a key-stroke can only be bound once per condition. Use distinct " +
                        "key-strokes or a single binding.",
                )
            }
        }

        /**
         * Asks for the ownership check of every live [KeyStrokeElement.Node] on this component, a turn
         * after the event queue processes the change pass in flight: two bindings that exchange
         * key-strokes in one pass each release their old stroke and take their new one while the other
         * still holds it, so reading ownership mid-pass would flag a legal swap. Deferring also keeps
         * the refusal off the apply phase, where a throw would kill the composition for good instead of
         * reaching the caller.
         *
         * Scoped to every node on the component, not just this one: a binding that did not rebind this
         * pass runs no check of its own, yet it is exactly the one a colliding sibling can take a stroke
         * from. Checking the whole component catches that.
         */
        private fun scheduleOwnershipCheck() {
            val component = component
            if (!checkScheduledFor.add(component)) return
            SwingUtilities.invokeLater {
                checkScheduledFor.remove(component)
                liveNodesByComponent[component]?.forEach(Node::checkOwnership)
            }
        }

        override fun onDetach() {
            unbind()
            liveNodesByComponent[component]?.remove(this)
        }

        private companion object {
            /** Every attached node, per component, so a check can cover siblings that did not run this pass. */
            private val liveNodesByComponent = WeakHashMap<JComponent, MutableSet<Node>>()

            /** Components with an ownership check already queued for the next turn of the event queue. */
            private val checkScheduledFor: MutableSet<JComponent> =
                Collections.newSetFromMap(WeakHashMap<JComponent, Boolean>())
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
