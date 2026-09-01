@file:JvmMultifileClass
@file:JvmName("InteractionTestKt")

package org.jetbrains.compose.swing.test.interaction

import java.awt.Component

/**
 * Asserts that the value [actual] reads off the matched node equals [expected], and returns this
 * interaction for chaining.
 *
 * [actual] reads the resolved node, typed as the query named it, so any property a component exposes
 * can be asserted on directly:
 *
 * ```
 * onNodeOfType<JSplitPane>().assertProperty(180) { dividerLocation }
 * ```
 *
 * The failure names the query and both values, e.g. `Node 'isOfType(JSplitPane)' property was 200, expected 180.` It
 * appends [message], which tells several property assertions on the same node apart.
 *
 * Use this for a property the assertion vocabulary does not cover. A property with its own
 * assertion or matcher reads better through that. Reading several properties of one node reads
 * better through a single [SwingNodeInteraction.fetch].
 *
 * @param expected the value [actual] must return, compared with `equals`.
 * @param message appended to the failure after both values; `null` by default.
 * @param actual the property to read, evaluated against the node the query resolves at this call.
 */
public fun <T : Component, V> SwingNodeInteraction<T>.assertProperty(
    expected: V,
    message: String? = null,
    actual: T.() -> V,
): SwingNodeInteraction<T> {
    val value = fetch().actual()
    if (value != expected) {
        throw AssertionError(
            "Node '$description' property was $value, expected $expected." +
                message?.let { " $it" }.orEmpty(),
        )
    }
    return this
}
