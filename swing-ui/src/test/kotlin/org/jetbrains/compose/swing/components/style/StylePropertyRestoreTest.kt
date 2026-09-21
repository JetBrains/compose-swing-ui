// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.CollaboratorSwap
import org.jetbrains.compose.swing.style.CustomOverlapProperty
import org.jetbrains.compose.swing.style.RemovalOrder
import org.jetbrains.compose.swing.style.assertCustomPropertyBaseline
import org.jetbrains.compose.swing.style.customOverlapProperty
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JTextField
import javax.swing.text.DefaultCaret

class StylePropertyRestoreTest {
    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("customPropertyOverlaps")
    fun customPropertiesShareModifierAndStyleBaseline(
        property: CaretOverlapProperty,
        order: RemovalOrder,
    ) = runComposeSwingTest {
        assertCustomPropertyBaseline(property.build(), order)
    }

    companion object {
        @JvmStatic
        fun customPropertyOverlaps(): List<Arguments> = CaretOverlapProperty.entries.flatMap { property ->
            RemovalOrder.entries.map { order -> Arguments.of(property, order) }
        }
    }
}

/** Each caret property a modifier and a style both write on a text field, and what its case does around it. */
enum class CaretOverlapProperty(
    internal val build: () -> CustomOverlapProperty,
) {
    BlinkRate({ caretBlinkRateOverlapProperty() }),
    BlinkRateAfterCaretSwap({ caretBlinkRateOverlapProperty(ReplacesCaret) }),
    BlinkRateAfterNullCaretSwap({ caretBlinkRateOverlapProperty(ReplacesNullCaret) }),
    BlinkRateAfterUiReplacement({ caretBlinkRateOverlapProperty(ReplacesUi) }),
    BlinkRateBeforeDeclaredCaret({
        val declared = DefaultCaret()
        caretBlinkRateOverlapProperty(ReplacesUi) { caret(declared) }
    }),
    UpdatePolicy({ caretUpdatePolicyOverlapProperty() }),
    UpdatePolicyAfterCaretSwap({ caretUpdatePolicyOverlapProperty(ReplacesCaret) }),
    UpdatePolicyAfterNullCaretSwap({ caretUpdatePolicyOverlapProperty(ReplacesNullCaret) }),
    UpdatePolicyAfterUiReplacement({ caretUpdatePolicyOverlapProperty(ReplacesUi) }),
    UpdatePolicyBeforeDeclaredCaret({
        val declared = DefaultCaret()
        caretUpdatePolicyOverlapProperty(ReplacesUi) { caret(declared) }
    }),
}

private fun swappingCaret(change: JTextField.() -> Unit): CollaboratorSwap = { field, case, original ->
    field.assertProperty(case.value(2)) {
        change()
        case.read(this)
    }
    original
}

private val ReplacesCaret = swappingCaret { caret = DefaultCaret() }
private val ReplacesNullCaret =
    swappingCaret {
        caret = null
        caret = DefaultCaret()
    }
private val ReplacesUi = swappingCaret { updateUI() }

private fun caretBlinkRateOverlapProperty(
    swap: CollaboratorSwap? = null,
    alsoDeclare: TextStyleScope.() -> Unit = {},
): CustomOverlapProperty = customOverlapProperty(
    listOf(0, 123, 456),
    { it.caret.blinkRate },
    ::caretBlinkRateElement,
    swap,
) {
    caretBlinkRate(it)
    alsoDeclare()
}

private fun caretUpdatePolicyOverlapProperty(
    swap: CollaboratorSwap? = null,
    alsoDeclare: TextStyleScope.() -> Unit = {},
): CustomOverlapProperty = customOverlapProperty(
    listOf(DefaultCaret.NEVER_UPDATE, DefaultCaret.UPDATE_WHEN_ON_EDT, DefaultCaret.ALWAYS_UPDATE),
    { (it.caret as DefaultCaret).updatePolicy },
    ::caretUpdatePolicyElement,
    swap,
) {
    caretUpdatePolicy(it)
    alsoDeclare()
}
