@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.constants.CaretUpdatePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
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
 * @return this chain with [caret] declared on it.
 * @see javax.swing.text.JTextComponent.setCaret
 */
public fun SwingModifier.caret(caret: Caret): SwingModifier = this then CaretElement(caret)

/**
 * How fast the component's caret blinks: the delay in milliseconds between the caret being shown and
 * being hidden again. `0` holds it steady. Removing the declaration puts back the rate the caret
 * carried before.
 *
 * Declare it after the [caret] it belongs to - a chain is applied in the order it is written, so a rate
 * declared first reaches the caret that is about to be replaced.
 *
 * @param rate the delay in milliseconds between blinks, or `0` for a caret that does not blink.
 * @return this chain with the blink rate declared on it.
 * @see javax.swing.text.Caret.setBlinkRate
 */
public fun SwingModifier.caretBlinkRate(rate: Int): SwingModifier =
    this then
        propertyElement<JTextComponent, Int>(
            rate,
            read = { it.caret.blinkRate },
            write = { component, value -> component.caret.blinkRate = value },
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
 * Removing the declaration puts back the policy the caret carried before.
 *
 * @param policy the update constant written to the caret the component carries when the chain is applied, so
 *   declare it after any [caret] of your own.
 * @return this chain with the caret update policy declared on it.
 * @see javax.swing.text.DefaultCaret.setUpdatePolicy
 */
public fun SwingModifier.caretUpdatePolicy(
    @CaretUpdatePolicy policy: Int,
): SwingModifier =
    this then
        propertyElement<JTextComponent, Int>(
            policy,
            read = { it.defaultCaret().updatePolicy },
            write = { component, value -> component.defaultCaret().updatePolicy = value },
        )

/** The component's caret as the [DefaultCaret] the policy is written to. */
private fun JTextComponent.defaultCaret(): DefaultCaret {
    val caret = caret
    check(caret is DefaultCaret) {
        "caretUpdatePolicy requires a ${DefaultCaret::class.java.name} caret, " +
            "but the ${javaClass.name} carries ${caret?.javaClass?.name}"
    }
    return caret
}

/**
 * Installs the declared caret on the component and puts back the one it replaced when the element
 * leaves the chain.
 *
 * Two elements are equal when they hold the *same* caret - identity, because a caret is a stateful
 * object carrying the position and selection it has navigated to, so an equal-looking replacement is a
 * different caret starting over.
 */
private class CaretElement(
    private val caret: Caret,
) : SwingModifier.NodeElement<JTextComponent, CaretElement.Node>() {
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

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
            component.caret = restored
        }
    }
}
