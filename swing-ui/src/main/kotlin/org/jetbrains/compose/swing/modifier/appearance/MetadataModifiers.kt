@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import javax.swing.JComponent

/*
 * Metadata SwingModifiers - auxiliary data that does not change appearance or layout: the component
 * name, look-and-feel client properties, and the test tag.
 */

/**
 * Sets `name` - the key components are looked up by in tests and automation; `null` clears it.
 *
 * @see java.awt.Component.setName
 */
public fun SwingModifier.name(name: String?): SwingModifier =
    this then
        propertyElement<Component, String?>(
            name,
            read = { it.name },
            write = { component, value -> component.name = value },
        )

/**
 * Tags the component with [tag] so it can be located in tests independently of its name.
 *
 * @param tag the identifier used to find the component.
 */
public fun SwingModifier.testTag(tag: String): SwingModifier = this then TestTagElement(tag)

private class TestTagElement(
    tag: Any?,
) : PropertyElement<JComponent, Any?>(
        JComponent::class.java,
        tag,
        read = { it.getClientProperty(TEST_TAG_CLIENT_PROPERTY_KEY) },
        write = { component, value -> component.putClientProperty(TEST_TAG_CLIENT_PROPERTY_KEY, value) },
    )

/**
 * Sets a `putClientProperty` entry - the escape hatch for look-and-feel styling keys (e.g. FlatLaf)
 * and accessibility hints. Each distinct [key] is an independent modifier slot; `null` restores the
 * value the component had before. Requires a `JComponent` target.
 *
 * @see javax.swing.JComponent.putClientProperty
 */
public fun SwingModifier.clientProperty(
    key: Any,
    value: Any?,
): SwingModifier =
    this then
        KeyedPropertyElement(
            JComponent::class.java,
            key,
            value,
            read = { it.getClientProperty(key) },
            write = { component, declared -> component.putClientProperty(key, declared) },
        )

/**
 * A [PropertyElement] whose last-wins slot is keyed by an explicit [slotKey] rather than its class, so
 * distinct keys (e.g. distinct client-property keys) are independent slots even though they share this
 * runtime class. A fixed-property element keyed by its own class never equals such a key, so no
 * collision with a class-keyed property is possible.
 */
private class KeyedPropertyElement<T : Component, V>(
    targetType: Class<T>,
    private val slotKey: Any,
    value: V,
    read: (component: T) -> V,
    write: (component: T, value: V) -> Unit,
) : PropertyElement<T, V>(targetType, value, read, write) {
    override val key: Any get() = slotKey
}
