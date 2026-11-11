// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * assertion or matcher reads better through that. [SwingNodeInteraction.fetch] is the last resort,
 * for a call that requires the matched component itself or for its identity.
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

/**
 * Asserts that the matched node is an [R], and returns this interaction typed as [R] so the
 * assertions and properties after it are written against [R]:
 *
 * ```
 * onNodeWithText("Save").assertIsOfType<JButton>().assertProperty(true) { isDefaultButton }
 * ```
 *
 * The failure names the query, the node's actual class and the expected type, e.g. `Node
 * 'hasText("Save")' is a JLabel, expected a JButton.`
 *
 * @throws AssertionError if the query has no single target, or if the target is not an [R].
 */
public inline fun <reified R : Component> SwingNodeInteraction<*>.assertIsOfType(): SwingNodeInteraction<R> {
    val typed = retype { it.castOrFail<R>("Node", matcherDescription) }
    typed.fetch()
    return typed
}
