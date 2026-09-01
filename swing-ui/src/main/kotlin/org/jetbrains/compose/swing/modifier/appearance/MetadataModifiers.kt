@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import javax.swing.JComponent

/*
 * Metadata SwingModifiers - auxiliary data that does not change appearance or layout: the component
 * name, look-and-feel client properties, and the test tag.
 */

/**
 * Sets `name` - the key components are looked up by in tests and automation; `null` clears it.
 *
 * @param name a label the component carries for lookup alone: Swing never displays it and never requires
 *   it to be unique.
 * @return this chain with the name declared on it.
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
 * @return this chain with the test tag declared on it.
 */
public fun SwingModifier.testTag(tag: String): SwingModifier = this then TestTagElement(tag)

/**
 * The tag [testTag] set on this component, or `null` where it carries none: a component the modifier
 * was never applied to, and one that is no `JComponent` and so holds no client properties at all.
 *
 * A test harness resolves a tagged component through this. What the library publishes is the read; the
 * slot the tag sits in stays its own.
 *
 * Marked [InternalSwingUiApi]; it may change or be removed without notice in any release.
 */
@InternalSwingUiApi
public fun Component.testTagOrNull(): String? = (this as? JComponent)?.get(TEST_TAG_KEY)

/** The client property [testTag] stores its tag under, read back by [testTagOrNull]. */
private val TEST_TAG_KEY: Key<String> = Key("org.jetbrains.compose.swing.testTag")

private class TestTagElement(
    tag: String?,
) : PropertyElement<JComponent, String?>(
        JComponent::class.java,
        tag,
        read = { it[TEST_TAG_KEY] },
        write = { component, value -> component[TEST_TAG_KEY] = value },
    )

/**
 * Sets a `putClientProperty` entry - the escape hatch for look-and-feel styling keys (e.g. FlatLaf)
 * and accessibility hints. Each distinct [key] is an independent modifier slot; `null` removes the
 * entry, and removing the declaration puts back the value the component carried before. Requires a
 * `JComponent` target.
 *
 * @param key the property key, matched by equality.
 * @param value the value stored under [key]; a look and feel acts on the change the write announces, not
 *   on later changes made inside the value itself.
 * @return this chain with the client property declared on it.
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
