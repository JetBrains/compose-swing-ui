// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import java.awt.event.MouseEvent
import javax.swing.JTextField

/** The order a case takes the modifier and the style off a field they both write. */
enum class RemovalOrder {
    /** The modifier is removed first, while the style still writes the field. */
    ModifierRemovedFirst,

    /** The style is withdrawn first, so the modifier value shows until it is removed too. */
    StyleWithdrawnFirst,

    /** The modifier changes its value beneath the style, then the style is withdrawn. */
    ModifierChangedBeneathStyle,
}

/**
 * Pairs the read function and expected values of a property with the modifier and style that both write it.
 * Test authors use it to check that the property keeps its baseline when the two writers overlap.
 */
class CustomOverlapProperty(
    /** Reads the current value of the property from the text field. */
    val read: (JTextField) -> Any?,
    /** Returns the expected property value for the case at the given index. */
    val value: (Int) -> Any?,
    /** Builds the modifier that writes the property for the case at the given index. */
    val modifier: (Int) -> SwingModifier,
    /** Applies the style declaration that writes the property for the case at the given index. */
    val declare: TextStyleScope.(Int) -> Unit,
    /** Says when the tooltip is published, so the test can move the pointer over the field first. */
    val publishesToolTip: ToolTipPublication = ToolTipPublication.Never,
    /** Optional hook that replaces the collaborator the property is read through, or null to read it directly. */
    val swap: CollaboratorSwap? = null,
) {
    /** Renders a text field that applies the modifier and style values, each attached only when non-null. */
    @Composable
    fun Field(
        modifierValue: Int?,
        styleValue: Int?,
        modifier: SwingModifier = SwingModifier,
    ) {
        TextField(
            "selection",
            onValueChange = {},
            modifier = modifier.then(modifierValue?.let(this.modifier) ?: SwingModifier),
            style = {
                styleValue?.let { declare(this, it) }
            },
        )
    }
}

/** When a per-location tooltip needs the pointer moved over the field before it answers. */
enum class ToolTipPublication(
    /** True when the tooltip is published after the style is applied. */
    val afterStyle: Boolean,
    /** True when the tooltip is published after the modifier is applied. */
    val afterModifier: Boolean,
) {
    /** Publishes no tooltip, so the pointer does not need to move over the field. */
    Never(afterStyle = false, afterModifier = false),

    /** Publishes the tooltip after the style is applied and before the modifier. */
    AfterStyle(afterStyle = true, afterModifier = false),

    /** Publishes the tooltip after both the style and the modifier are applied. */
    AfterStyleAndModifier(afterStyle = true, afterModifier = true),
}

/**
 * Replaces the collaborator a custom property is read through, given the field, the case and the baseline
 * read before, and returns the baseline the property then restores.
 */
typealias CollaboratorSwap = (SwingNodeInteraction<JTextField>, CustomOverlapProperty, Any?) -> Any?

/** Builds a CustomOverlapProperty whose case at each index takes the value at that index in the list. */
fun <V> customOverlapProperty(
    values: List<V>,
    read: (JTextField) -> Any?,
    element: (V) -> SwingModifier.Element,
    swap: CollaboratorSwap? = null,
    declare: TextStyleScope.(V) -> Unit,
): CustomOverlapProperty =
    CustomOverlapProperty(
        read = read,
        value = { values[it] },
        modifier = { SwingModifier then element(values[it]) },
        declare = { declare(values[it]) },
        swap = swap,
    )

private fun SwingNodeInteraction<JTextField>.publishToolTip(case: CustomOverlapProperty) {
    assertProperty(case.value(2)) {
        dispatchEvent(MouseEvent(this, MouseEvent.MOUSE_MOVED, 0L, 0, 1, 1, 0, false))
        case.read(this)
    }
}

/**
 * Asserts that the property reads the same baseline whether the modifier and style attach in either order.
 * The case is removed in the given order, and the test must run on the EDT through a ComposeSwingTest body.
 */
suspend fun ComposeSwingTest.assertCustomPropertyBaseline(
    case: CustomOverlapProperty,
    order: RemovalOrder,
) {
    var modified by mutableStateOf(false)
    var styled by mutableStateOf(false)
    var modifierValue by mutableIntStateOf(0)
    var original: Any? = null
    setContent {
        case.Field(if (modified) modifierValue else null, if (styled) 2 else null)
    }
    onNodeOfType<JTextField>().assertProperty(true) {
        original = case.read(this)
        true
    }
    modified = true
    styled = true
    awaitIdle()
    // Both attachment orders must share the baseline, including a modifier added beneath a standing style.
    repeat(2) { round ->
        if (round != 0) {
            styled = true
            awaitIdle()
        }
        if (case.publishesToolTip.afterStyle) onNodeOfType<JTextField>().publishToolTip(case)
        if (round != 0) {
            modified = true
            awaitIdle()
        }
        if (case.publishesToolTip.afterModifier) onNodeOfType<JTextField>().publishToolTip(case)
        case.swap?.let { swap -> original = swap(onNodeOfType<JTextField>(), case, original) }
        onNodeOfType<JTextField>().assertProperty(case.value(2)) { case.read(this) }
        when (order) {
            RemovalOrder.ModifierRemovedFirst -> {
                modified = false
                awaitIdle()
                onNodeOfType<JTextField>().assertProperty(case.value(2)) { case.read(this) }
                styled = false
            }

            RemovalOrder.StyleWithdrawnFirst -> {
                styled = false
                awaitIdle()
                onNodeOfType<JTextField>().assertProperty(case.value(modifierValue)) { case.read(this) }
                modified = false
            }

            RemovalOrder.ModifierChangedBeneathStyle -> {
                modifierValue = 1
                awaitIdle()
                if (case.publishesToolTip.afterModifier) onNodeOfType<JTextField>().publishToolTip(case)
                onNodeOfType<JTextField>().assertProperty(case.value(2)) { case.read(this) }
                styled = false
                awaitIdle()
                onNodeOfType<JTextField>().assertProperty(case.value(1)) { case.read(this) }
                modified = false
            }
        }
        awaitIdle()
        onNodeOfType<JTextField>().assertProperty(original) { case.read(this) }
        modifierValue = 0
    }
}
