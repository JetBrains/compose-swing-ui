package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * Narrows the node to an element's target type. A node that is not the required type is rejected with
 * a clear message naming the element ([SwingModifier.NodeElement.name]) and the required vs. actual type.
 *
 * @param element the element whose [SwingModifier.NodeElement.targetType] the component must be.
 * @param raw the node's component.
 * @return the component, typed.
 */
internal fun <T : Component> checkedTarget(
    element: SwingModifier.NodeElement<T, *>,
    raw: Component,
): T {
    val targetType = element.targetType
    if (!targetType.isInstance(raw)) error(targetTypeMismatch(element.name, targetType.name, raw))
    return targetType.cast(raw)
}

/**
 * What a component of the wrong type is refused with, so an element narrowing the target itself - one
 * whose required type is an interface, which [SwingModifier.NodeElement.targetType] cannot name - is
 * refused in the same words as one the diff narrows.
 *
 * @param name the element's [SwingModifier.NodeElement.name].
 * @param required the name of the type the element requires.
 * @param actual the component it was handed.
 * @return the message.
 */
internal fun targetTypeMismatch(
    name: String,
    required: String,
    actual: Component,
): String = "Modifier element $name requires a $required target, but the component is a ${actual.javaClass.name}"
