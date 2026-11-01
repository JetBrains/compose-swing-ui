@file:JvmMultifileClass
@file:JvmName("InteractionTestKt")

package org.jetbrains.compose.swing.test.interaction

import java.awt.Component
import javax.accessibility.AccessibleAction

/**
 * Performs the accessible action whose description equals [description] and awaits idle. The description
 * is the one the component reports for the action, which a look and feel may localize: a button reports
 * the `AbstractButton.clickText` default of the look and feel, and a spinner reports
 * `AccessibleAction.INCREMENT`. Pass the description in the consumer JVM's locale.
 *
 * @throws AssertionError if the node or action is unavailable, or the action does not run.
 * @return this interaction for chaining.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performAccessibleAction(
    description: String,
): SwingNodeInteraction<T> {
    val action =
        resolve().accessibleContext?.accessibleAction
            ?: throw AssertionError("Node '${this.description}' has no accessible action.")
    val index = action.indexOfDescription(this.description, description)
    if (!action.doAccessibleAction(index)) {
        throw AssertionError("Node '${this.description}' did not perform accessible action '$description'.")
    }
    test.awaitIdle()
    return this
}

private fun AccessibleAction.indexOfDescription(
    node: String,
    description: String,
): Int {
    val descriptions = (0 until accessibleActionCount).map(::getAccessibleActionDescription)
    val index = descriptions.indexOf(description)
    if (index < 0) {
        throw AssertionError("Node '$node' has no accessible action '$description'. Available: $descriptions.")
    }
    return index
}

/**
 * Sets the accessible [value] and awaits idle. The component applies its own range and listener rules.
 *
 * @throws AssertionError if the node or accessible value is unavailable, or the value is refused.
 * @return this interaction for chaining.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performSetValue(value: Number): SwingNodeInteraction<T> {
    val accessibleValue =
        resolve().accessibleContext?.accessibleValue
            ?: throw AssertionError("Node '$description' has no accessible value.")
    if (!accessibleValue.setCurrentAccessibleValue(value)) {
        throw AssertionError("Node '$description' refused accessible value $value.")
    }
    test.awaitIdle()
    return this
}

/**
 * Adds the accessible child at [index] to the selection and awaits idle. Selection is checked
 * immediately; settlement may restore the declared selection afterward.
 *
 * @throws AssertionError if the node or accessible selection is unavailable, or the child is not selected.
 * @return this interaction for chaining.
 */
public suspend fun <T : Component> SwingNodeInteraction<T>.performAddSelection(index: Int): SwingNodeInteraction<T> {
    val selection =
        resolve().accessibleContext?.accessibleSelection
            ?: throw AssertionError("Node '$description' has no accessible selection.")
    selection.addAccessibleSelection(index)
    if (!selection.isAccessibleChildSelected(index)) {
        throw AssertionError("Node '$description' did not select accessible child at index $index.")
    }
    test.awaitIdle()
    return this
}
