package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import javax.swing.JComponent

/*
 * Metadata SwingModifiers - auxiliary data that does not change appearance or layout: the component
 * name, tooltip, look-and-feel client properties, and the test tag.
 */

/** Sets `name` - the key components are looked up by in tests and automation; `null` clears it. */
public fun SwingModifier.name(name: String?): SwingModifier =
    this then propertyElement<Component, String?>(name, read = { it.name }, write = { c, v -> c.name = v })

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
        write = { c, v -> c.putClientProperty(TEST_TAG_CLIENT_PROPERTY_KEY, v) },
    )

/**
 * The `JComponent` client-property key under which [testTag] stores its tag. The test harness reads
 * this key to resolve a tagged component; it is not intended for application use.
 */
@InternalSwingUiApi
public val TEST_TAG_CLIENT_PROPERTY_KEY: Any = "org.jetbrains.compose.swing.testTag"

/** Sets `toolTipText`; `null` clears the tooltip. Requires a `JComponent` target. */
public fun SwingModifier.toolTip(text: String?): SwingModifier =
    this then
        propertyElement<JComponent, String?>(
            text,
            read = { it.toolTipText },
            write = { c, v -> c.toolTipText = v },
        )

/**
 * Sets a `putClientProperty` entry - the escape hatch for look-and-feel styling keys (e.g. FlatLaf)
 * and accessibility hints. Each distinct [key] is an independent modifier slot; `null` restores the
 * value the component had before. Requires a `JComponent` target.
 */
public fun SwingModifier.clientProperty(
    key: Any,
    value: Any?,
): SwingModifier =
    this then
        // Key the slot by the client-property key so distinct client properties are independent slots.
        KeyedPropertyElement(
            JComponent::class.java,
            key,
            value,
            read = { it.getClientProperty(key) },
            write = { c, v -> c.putClientProperty(key, v) },
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
