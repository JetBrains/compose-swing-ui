@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.annotations.CaretUpdatePolicy
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

/**
 * Installs [caret] as the text component's caret - the object that holds the insertion point and the
 * selection, paints them, and answers the focus and mouse gestures that move them. Removing the
 * declaration puts back the caret the component carried before.
 *
 * Three things follow from the way Swing installs a caret:
 * - installing one places it at offset 0 with nothing selected, so hand this a caret that outlives a
 *   recomposition - one kept in a `remember`, or an `object` - and it is installed once, leaving the
 *   selection the user makes afterwards in place;
 * - a caret handed in here does not blink until it is given a rate, which [caretBlinkRate] declares: a
 *   look and feel gives its blink rate to the caret it created itself and to no other;
 * - a later look and feel leaves this caret in place, for the same reason - a look and feel replaces
 *   only a caret it created.
 *
 * Whether the selection stays painted while the component is unfocused is the caret's own answer,
 * recomputed on every focus change, so a caret of your own is where that answer is given:
 *
 * ```
 * val caret = remember {
 *     object : DefaultCaret() {
 *         override fun focusLost(event: FocusEvent) {
 *             super.focusLost(event)
 *             isSelectionVisible = true
 *         }
 *     }
 * }
 * TextField(state, modifier = SwingModifier.caret(caret).caretBlinkRate(500))
 * ```
 *
 * Where the caret may go is declared by [navigationFilter], and what it selects by
 * `DocumentState.selection`, the one owner of a component's selection; key bindings are declared by
 * `onKeyStroke`. A part of a text component this library ships no builder for - a highlighter, a drop
 * mode - is reachable through a [SwingModifier.NodeElement] of your own; see `docs/CUSTOM-COMPONENTS.md`.
 *
 * @param caret the caret the component navigates and selects with.
 * @return this modifier with [caret] declared on it.
 * @see javax.swing.text.JTextComponent.setCaret
 */
public fun SwingModifier.caret(caret: Caret): SwingModifier = this then CaretElement(caret)

/**
 * How fast the component's caret blinks: the delay in milliseconds between the caret being shown and
 * being hidden again. `0` holds it steady.
 *
 * The rate belongs to the component, not to one caret. A caret that arrives later - one a [caret]
 * declared anywhere in the modifier chain installs - is given the declared rate as it is installed. Removing
 * the declaration puts the rate read at attach onto the caret the component carries then, and a caret
 * the modifier let go of keeps the declared rate.
 *
 * @param rate the delay in milliseconds between blinks, or `0` for a caret that does not blink.
 * @return this modifier with the blink rate declared on it.
 * @see javax.swing.text.Caret.setBlinkRate
 */
public fun SwingModifier.caretBlinkRate(rate: Int): SwingModifier =
    this then
        CaretPropertyElement(
            name = "caretBlinkRate",
            value = rate,
            read = { it.blinkRate },
            write = BlinkRateWrite,
        )

/**
 * Sets what the caret does when the document is edited somewhere other than where the caret sits.
 *
 * [DefaultCaret.ALWAYS_UPDATE] carries the caret along with every edit whichever thread makes it, and
 * keeps the caret visible - the policy a log view sits at the end of, so appended lines scroll into
 * sight. [DefaultCaret.NEVER_UPDATE] leaves the caret at the offset it holds and does not scroll to keep
 * it visible, so a view stays where the reader put it while text arrives above; a removal that shortens
 * the document past that offset moves the caret to the end. [DefaultCaret.UPDATE_WHEN_ON_EDT], the
 * default, carries the caret along with edits made on the event dispatch thread and leaves it alone for
 * edits made off it.
 *
 * Requires a [JTextComponent] whose caret is a [DefaultCaret] - the caret a look and feel installs.
 * A caret that arrives and is not a [DefaultCaret] fails as it is installed, as it would on a pass.
 *
 * The policy belongs to the component, not to one caret, and removal puts it back, as [caretBlinkRate]
 * describes for the rate.
 *
 * @param policy the update constant written to the caret the component carries.
 * @return this modifier with the caret update policy declared on it.
 * @see javax.swing.text.DefaultCaret.setUpdatePolicy
 */
public fun SwingModifier.caretUpdatePolicy(
    @CaretUpdatePolicy policy: Int,
): SwingModifier =
    this then
        CaretPropertyElement(
            name = "caretUpdatePolicy",
            value = policy,
            read = { it.asDefaultCaret().updatePolicy },
            write = UpdatePolicyWrite,
        )

/** Held here rather than built per call, so every declaration of one value takes the same slot. */
private val BlinkRateWrite: (Caret, Int) -> Unit = { caret, value ->
    // Zeroed first, then given the rate. A caret asked for a rate while it carries no editable component
    // stashes it and answers with the stash from then on, so a rate written once it carries one reaches
    // the blink timer while the caret goes on answering with the stashed one. A zero rate is the only
    // write that drops the stash, and a look and feel that gives its caret a rate as it installs -
    // before the caret is told which component it is on - leaves every caret it builds in that state.
    caret.blinkRate = 0
    if (value != 0) caret.blinkRate = value
}

/** [BlinkRateWrite], for the policy, which only a [DefaultCaret] carries. */
private val UpdatePolicyWrite: (Caret, Int) -> Unit = { caret, value -> caret.asDefaultCaret().updatePolicy = value }

/** This caret as the [DefaultCaret] a policy is written to. */
private fun Caret.asDefaultCaret(): DefaultCaret {
    require(this is DefaultCaret) {
        "caretUpdatePolicy requires a ${DefaultCaret::class.java.name} caret, but the caret is a ${javaClass.name}"
    }
    return this
}

/**
 * A property of the caret a text component carries, declared on the component.
 *
 * The caret is the object written to, and the component replaces it - a look and feel installing one of
 * its own, another element declaring one - so the declaration is written again onto whichever caret the
 * component announces next, and a component reports the look and feel it took once that look and feel
 * has installed its caret and given it the defaults' value.
 *
 * What is put back goes onto the caret the component carries when the declaration leaves. A component
 * that took a different caret while the declaration stood would otherwise be left carrying the declared
 * value with nothing declaring it, the restore having gone to a caret it had already let go of.
 */
private class CaretPropertyElement(
    override val name: String,
    private val value: Int,
    private val read: (Caret) -> Int,
    private val write: (Caret, Int) -> Unit,
) : SwingModifier.NodeElement<JTextComponent, CaretPropertyElement.Node>() {
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    override val key: Any get() = write

    override val declaredValues: Map<String, Any?> get() = mapOf(name to value)

    /** A caret the modifier let go of keeps what it was given, so this element answers for no caret of its own. */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node(read, write)

    override fun update(node: Node) = node.apply(value)

    override fun equals(other: Any?): Boolean =
        other is CaretPropertyElement && write === other.write && value == other.value

    override fun hashCode(): Int = 31 * System.identityHashCode(write) + value

    class Node(
        private val read: (Caret) -> Int,
        private val write: (Caret, Int) -> Unit,
    ) : SwingModifier.Node<JTextComponent>(),
        PropertyChangeListener {
        private var declared: Int = 0
        private var captured: Boolean = false
        private var original: Int = 0

        override fun onAttach() {
            val component = component
            component.caret?.let {
                original = read(it)
                captured = true
            }
            component.addPropertyChangeListener("caret", this)
            component.addPropertyChangeListener("UI", this)
        }

        fun apply(value: Int) {
            declared = value
            component.caret?.let { write(it, value) }
        }

        override fun propertyChange(event: PropertyChangeEvent) {
            component.caret?.let { write(it, declared) }
        }

        override fun onDetach() {
            val component = component
            component.removePropertyChangeListener("caret", this)
            component.removePropertyChangeListener("UI", this)
            if (captured) component.caret?.let { write(it, original) }
        }
    }
}

/**
 * Installs the declared caret on the component and puts back the one it replaced when the element
 * leaves the modifier.
 *
 * Two elements are equal when they hold the *same* caret - identity, because a caret is a stateful
 * object carrying the position and selection it has navigated to, so an equal-looking replacement is a
 * different caret starting over.
 */
private class CaretElement(
    private val caret: Caret,
) : SwingModifier.NodeElement<JTextComponent, CaretElement.Node>() {
    override val name: String get() = "caret"

    override val declaredValues: Map<String, Any?> get() = mapOf("caret" to caret)
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    /** Installing a caret resets its position and selection, which a removal carries across, not back. */
    override val restores: RestorePolicy get() = RestorePolicy.DeclaredPropertyOnly

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.caret = caret
        node.apply()
    }

    override fun equals(other: Any?): Boolean = other is CaretElement && caret === other.caret

    override fun hashCode(): Int = System.identityHashCode(caret)

    class Node : SwingModifier.Node<JTextComponent>() {
        var caret: Caret? = null

        // The caret the component carried before the first install, restored whatever the declaration
        // changes to in between - a swapped caret gives back the original, not its predecessor.
        private var restored: Caret? = null

        override fun onAttach() {
            restored = component.caret
        }

        fun apply() {
            // Installing a caret puts it at offset 0 and drops the selection, so the write runs only
            // when the component is not already carrying the declared caret.
            val declared = caret ?: return
            if (component.caret !== declared) component.caret = declared
        }

        override fun onDetach() {
            val component = component
            val declared = component.caret
            val original = restored
            if (declared === original) return

            // Installing a caret puts it at offset 0 and drops the selection. The selection and the
            // caret position belong to the text, not to the caret object being taken off, so a removal
            // carries them across.
            val dot = declared?.dot ?: 0
            val mark = declared?.mark ?: dot
            component.caret = original
            original?.let {
                it.dot = mark
                if (dot != mark) it.moveDot(dot)
            }
        }
    }
}
