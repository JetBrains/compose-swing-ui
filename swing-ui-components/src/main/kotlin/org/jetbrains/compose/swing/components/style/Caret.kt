// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.annotations.CaretUpdatePolicy
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent

/**
 * Installs [caret] as the text component's caret.
 * A style that stops setting it restores the component's previous caret.
 *
 * Keep one caret instance across recompositions with `remember` or an `object`.
 * Installing a caret resets its position to offset 0 and clears the selection.
 * Use [caretBlinkRate] to enable blinking, since a look and feel gives its blink rate only to the caret
 * it creates itself.
 * The caret stays installed when the look and feel changes, because a look and feel replaces only a caret it created.
 *
 * Use a custom caret to control whether the selection remains visible while the component is unfocused.
 * [navigationFilter][org.jetbrains.compose.swing.components.modifier.interaction.navigationFilter]
 * controls where the caret may move.
 * `DocumentState.selection` controls the component's selection.
 *
 * @param caret the caret the component navigates and selects with.
 * @see javax.swing.text.JTextComponent.setCaret
 */
public fun TextStyleScope.caret(caret: Caret): Unit = CaretProperty.provide(caret)

/**
 * Sets the delay in milliseconds between showing and hiding the component's caret.
 * `0` disables blinking.
 *
 * The rate applies to replacement carets too.
 * A style that stops setting it restores the original rate on the current caret.
 * A replaced caret keeps the rate it was given.
 *
 * @param rate the delay in milliseconds between blinks, or `0` for a caret that does not blink.
 * @see javax.swing.text.Caret.setBlinkRate
 */
public fun TextStyleScope.caretBlinkRate(rate: Int): Unit = CaretBlinkRateProperty.provide(rate)

/**
 * Sets how document edits affect the caret's position and scrolling.
 *
 * [DefaultCaret.ALWAYS_UPDATE] updates the caret's position for edits on any thread.
 * It scrolls to keep the caret visible.
 * [DefaultCaret.NEVER_UPDATE] keeps the caret at its current offset without scrolling.
 * If a removal leaves that offset past the end of the document, the caret moves to the end.
 * [DefaultCaret.UPDATE_WHEN_ON_EDT] is the default.
 * It updates the caret's position only for edits on the event dispatch thread.
 *
 * Requires a [DefaultCaret], including any replacement caret.
 * Installing a caret of another type fails.
 * The policy applies to replacement carets and is restored as described in [caretBlinkRate].
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
        overwritten = CaretBlinkRateDescriptor,
        // Every declaration of one value passes the same lambda instance: equality and the key compare it
        // by identity.
        write = Caret::writeBlinkRate,
    )

/** The element that sets the caret's update policy to [policy]. */
internal fun caretUpdatePolicyElement(
    @CaretUpdatePolicy policy: Int,
): SwingModifier.Element =
    CaretPropertyElement(
        name = "caretUpdatePolicy",
        value = policy,
        overwritten = CaretUpdatePolicyDescriptor,
        // Every declaration of one value passes the same lambda instance: equality and the key compare it
        // by identity.
        write = Caret::writeUpdatePolicy,
    )

internal val CaretProperty = stylePropertyOf { caret: Caret -> CaretElement(caret) }
internal val CaretBlinkRateProperty = stylePropertyOf(::caretBlinkRateElement)
internal val CaretUpdatePolicyProperty = stylePropertyOf(::caretUpdatePolicyElement)

/** The blink rate of the caret the component carries, which is the one a replacement caret is given. */
private val CaretBlinkRateDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Int?>(
        "caretBlinkRate",
        read = { it.caret?.blinkRate },
        write = { component, value -> if (value != null) component.caret?.writeBlinkRate(value) },
    )

/** The update policy of the caret the component carries, which is the one a replacement caret is given. */
private val CaretUpdatePolicyDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Int?>(
        "caretUpdatePolicy",
        read = { it.caret?.asDefaultCaret()?.updatePolicy },
        write = { component, value -> if (value != null) component.caret?.writeUpdatePolicy(value) },
    )

private val CaretDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Caret?>(
        "caret",
        read = { it.caret },
        write = { component, value -> if (component.caret !== value) component.replaceCaretKeepingSelection(value) },
    )

// Zeroed first: a caret given a rate before it has an editable component stashes it, and a later nonzero write
// reaches the blink timer while the caret returns the cached rate. Only a zero rate drops the stash, and a look
// and feel that gives its caret a rate as it installs - before the caret is told which component it is on - leaves
// every caret it builds in that state.
private fun Caret.writeBlinkRate(rate: Int) {
    blinkRate = 0
    if (rate != 0) blinkRate = rate
}

private fun Caret.writeUpdatePolicy(policy: Int) {
    asDefaultCaret().updatePolicy = policy
}

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
    private val overwritten: ComponentPropertyDescriptor<JTextComponent, Int?>,
    private val write: (Caret, Int) -> Unit,
) : SwingModifier.NodeElement<JTextComponent, CaretPropertyElement.Node>() {
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    override val key: Any get() = write

    override val declaredValues: Map<String, Any?> get() = mapOf(name to value)

    /** A caret the style let go of keeps what it was given, so this element answers for no caret of its own. */
    override val restores: RestorePolicy get() = RestorePolicy.None

    override val overwrites: List<ComponentPropertyDescriptor<JTextComponent, *>> get() = listOf(overwritten)

    override val rewriteOn: List<String> get() = listOf("caret", "UI")

    override fun create(): Node = Node(write)

    override fun update(node: Node) = node.apply(value)

    override fun equals(other: Any?): Boolean =
        other is CaretPropertyElement && write === other.write && value == other.value

    override fun hashCode(): Int = 31 * System.identityHashCode(write) + value

    class Node(
        private val write: (Caret, Int) -> Unit,
    ) : SwingModifier.ComponentNode<JTextComponent>() {
        fun apply(value: Int) {
            component.caret?.let { write(it, value) }
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

    override val overwrites: List<ComponentPropertyDescriptor<JTextComponent, *>> get() = listOf(CaretDescriptor)

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.apply(caret)
    }

    override fun equals(other: Any?): Boolean = other is CaretElement && caret === other.caret

    override fun hashCode(): Int = System.identityHashCode(caret)

    class Node : SwingModifier.ComponentNode<JTextComponent>() {
        private var installed: Caret? = null

        fun apply(caret: Caret) {
            // Installing a caret resets offset and selection, so skip the write when it is already installed.
            if (component.caret === caret) return
            // A listener on the component that throws from the install is reported; the install stands.
            if (caret === installed) {
                component.replaceCaretKeepingSelection(caret) { report { component.caret = it } }
            } else {
                report { component.caret = caret }
            }
            installed = caret
        }
    }
}

/**
 * Installs [caret] at the position and selection the caret it replaces holds. Installing a caret puts it at
 * offset 0 and drops the selection, but both belong to the text, not to the caret object.
 */
private inline fun JTextComponent.replaceCaretKeepingSelection(
    caret: Caret?,
    install: (Caret?) -> Unit = { this.caret = it },
) {
    val dot = this.caret?.dot ?: 0
    val mark = this.caret?.mark ?: dot
    install(caret)
    caret?.let {
        it.dot = mark
        if (dot != mark) it.moveDot(dot)
    }
}
