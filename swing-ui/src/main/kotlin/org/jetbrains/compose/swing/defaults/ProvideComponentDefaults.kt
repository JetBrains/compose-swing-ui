@file:JvmMultifileClass
@file:JvmName("ComponentDefaultsKt")

package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Scopes a single inherited component default declaration over [content].
 *
 * Applicable modifier declarations from [value] cascade onto every descendant Swing and menu node,
 * evaluated before the node's own explicit modifier.
 *
 * This function is target-polymorphic and can be invoked from ordinary Swing composable contexts as
 * well as menu composable contexts.
 *
 * Must be called on the Event Dispatch Thread (EDT).
 *
 * @param value the provision token binding a key to a value or null.
 * @param content the composables scoped by this component default.
 */
@Composable
public fun ProvideComponentDefaults(
    value: ProvidedComponentDefault<*>,
    content: @Composable () -> Unit,
) {
    val parent = LocalComponentDefaults.current
    val defaults =
        remember(parent, value) {
            parent.withProvision(value)
        }
    CompositionLocalProvider(LocalComponentDefaults provides defaults, content = content)
}

/**
 * Scopes multiple inherited component default declarations over [content].
 *
 * Provisions are applied in argument order: repeating a key is last-argument-wins.
 *
 * Applicable modifier declarations cascade onto every descendant Swing and menu node, evaluated before
 * the node's own explicit modifier.
 *
 * This function is target-polymorphic and can be invoked from ordinary Swing composable contexts as
 * well as menu composable contexts.
 *
 * Must be called on the Event Dispatch Thread (EDT).
 *
 * @param values the provision tokens to cascade.
 * @param content the composables scoped by these component defaults.
 */
@Composable
public fun ProvideComponentDefaults(
    vararg values: ProvidedComponentDefault<*>,
    content: @Composable () -> Unit,
) {
    val parent = LocalComponentDefaults.current
    val key = ProvisionsKey(values)
    val defaults =
        remember(parent, key) {
            parent.withProvisions(values)
        }
    CompositionLocalProvider(LocalComponentDefaults provides defaults, content = content)
}
