@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.annotations.CaretUpdatePolicy
import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.stylePropertyOf
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

/**
 * Installs [caret] as the text component's caret - the object that holds the insertion point and the
 * selection, paints them, and answers the focus and mouse gestures that move them. A style that stops
 * setting it puts back the caret the component carried before.
 *
 * The way Swing installs a caret has these effects:
 * - installing one places it at offset 0 with nothing selected, so hand this a caret that outlives a
 *   recomposition - one kept in a `remember`, or an `object` - and it is installed once, leaving the
 *   selection the user makes afterwards in place;
 * - a caret handed in here does not blink until it is given a rate, which [caretBlinkRate] sets: a
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
 * TextField(state, style = { caret(caret); caretBlinkRate(500) })
 * ```
 *
 * Where the caret may go is set by [navigationFilter], and what it selects by `DocumentState.selection`,
 * the one owner of a component's selection.
 *
 * @param caret the caret the component navigates and selects with.
 * @see javax.swing.text.JTextComponent.setCaret
 */
public fun TextStyleScope.caret(caret: Caret): Unit = CaretProperty.provide(caret)

/**
 * How fast the component's caret blinks: the delay in milliseconds between the caret being shown and
 * being hidden again. `0` holds it steady.
 *
 * The rate belongs to the component, not to one caret. A caret that arrives later - one [caret] installs -
 * is given the rate as it is installed. A style that stops setting it puts the rate read at attach onto
 * the caret the component carries then, and a caret the style let go of keeps the rate it was given.
 *
 * @param rate the delay in milliseconds between blinks, or `0` for a caret that does not blink.
 * @see javax.swing.text.Caret.setBlinkRate
 */
public fun TextStyleScope.caretBlinkRate(rate: Int): Unit = CaretBlinkRateProperty.provide(rate)

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
 * Requires a caret that is a [DefaultCaret] - the caret a look and feel installs. A caret that arrives
 * and is not a [DefaultCaret] fails as it is installed.
 *
 * The policy belongs to the component, not to one caret, and is put back as [caretBlinkRate] describes
 * for the rate.
 *
 * @param policy the update constant written to the caret the component carries.
 * @see javax.swing.text.DefaultCaret.setUpdatePolicy
 */
public fun TextStyleScope.caretUpdatePolicy(
    @CaretUpdatePolicy policy: Int,
): Unit = CaretUpdatePolicyProperty.provide(policy)

/** The element that sets the caret's blink rate to [rate]. */
internal fun caretBlinkRateElement(rate: Int): SwingModifier.Element =
    CaretPropertyElement(
        name = "caretBlinkRate",
        value = rate,
        read = { it.blinkRate },
        // Every declaration of one value passes the same lambda instance: equality and the key compare it
        // by identity.
        write = { caret, value ->
            // Zeroed first: a caret given a rate before it has an editable component stashes it, and a later
            // nonzero write reaches the blink timer while the caret keeps answering with the stash. Only a zero
            // rate drops the stash, and a look and feel that gives its caret a rate as it installs - before the
            // caret is told which component it is on - leaves every caret it builds in that state.
            caret.blinkRate = 0
            if (value != 0) caret.blinkRate = value
        },
    )

/** The element that sets the caret's update policy to [policy]. */
internal fun caretUpdatePolicyElement(
    @CaretUpdatePolicy policy: Int,
): SwingModifier.Element =
    CaretPropertyElement(
        name = "caretUpdatePolicy",
        value = policy,
        read = { it.asDefaultCaret().updatePolicy },
        // Every declaration of one value passes the same lambda instance: equality and the key compare it
        // by identity.
        write = { caret, value -> caret.asDefaultCaret().updatePolicy = value },
    )

internal val CaretProperty = stylePropertyOf { caret: Caret -> CaretElement(caret) }
internal val CaretBlinkRateProperty = stylePropertyOf(::caretBlinkRateElement)
internal val CaretUpdatePolicyProperty = stylePropertyOf(::caretUpdatePolicyElement)

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

    /** A caret the style let go of keeps what it was given, so this element answers for no caret of its own. */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node(name, read, write)

    override fun update(node: Node) = node.apply(value)

    override fun equals(other: Any?): Boolean =
        other is CaretPropertyElement && write === other.write && value == other.value

    override fun hashCode(): Int = 31 * System.identityHashCode(write) + value

    class Node(
        private val name: String,
        private val read: (Caret) -> Int,
        private val write: (Caret, Int) -> Unit,
    ) : SwingModifier.ComponentNode<JTextComponent>(),
        PropertyChangeListener {
        private var captured: PropertyCaptures.Capture<*, *>? = null

        override fun onAttach() {
            val component = component
            captured =
                checkNotNull(holder?.modifierState).captures().capture(
                    name,
                    component,
                    { it.caret?.let(read) },
                    { component, value ->
                        if (value != null) component.caret?.let { write(it, value) }
                    },
                )
            component.addPropertyChangeListener("caret", this)
            component.addPropertyChangeListener("UI", this)
        }

        fun apply(value: Int) {
            component.caret?.let { write(it, value) }
        }

        override fun propertyChange(event: PropertyChangeEvent) {
            if (component.caret != null) holder?.modifierState?.refreshLocalConsumer(this)
        }

        override fun onDetach() {
            val component = component
            component.removePropertyChangeListener("caret", this)
            component.removePropertyChangeListener("UI", this)
            captured?.release()
            captured = null
        }
    }
}

/**
 * Installs the declared caret on the component and puts back the one it replaced when the element
 * leaves the style.
 *
 * Two elements are equal when they hold the *same* caret - identity, because a caret is a stateful
 * object carrying the position and selection it has navigated to, so an equal-looking replacement is a
 * different caret starting over.
 */
internal class CaretElement(
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

    class Node : SwingModifier.ComponentNode<JTextComponent>() {
        var caret: Caret? = null

        private var captured: PropertyCaptures.Capture<*, *>? = null

        private var installed: Caret? = null

        override fun onAttach() {
            captured =
                checkNotNull(holder?.modifierState).captures().capture(
                    "caret",
                    component,
                    { it.caret },
                    { component, value ->
                        if (component.caret !== value) component.replaceCaretKeepingSelection(value)
                    },
                )
        }

        fun apply() {
            // Installing a caret resets offset and selection, so skip the write when it is already installed.
            val declared = caret ?: return
            if (component.caret === declared) return
            if (declared === installed) component.replaceCaretKeepingSelection(declared) else component.caret = declared
            installed = declared
        }

        override fun onDetach() {
            captured?.release()
            captured = null
        }
    }
}

/**
 * Installs [caret] at the position and selection the caret it replaces holds. Installing a caret puts it at
 * offset 0 and drops the selection, but both belong to the text, not to the caret object.
 */
private fun JTextComponent.replaceCaretKeepingSelection(caret: Caret?) {
    val dot = this.caret?.dot ?: 0
    val mark = this.caret?.mark ?: dot
    this.caret = caret
    caret?.let {
        it.dot = mark
        if (dot != mark) it.moveDot(dot)
    }
}
