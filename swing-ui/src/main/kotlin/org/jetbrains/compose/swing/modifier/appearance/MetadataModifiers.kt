@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.PropertyAccessors
import org.jetbrains.compose.swing.modifier.PropertyNode
import org.jetbrains.compose.swing.modifier.RestorePolicy
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
 * @return this modifier with the name declared on it.
 * @see java.awt.Component.setName
 */
public fun SwingModifier.name(name: String?): SwingModifier = this then propertyElement(NameProperty, name)

/**
 * Tags the component with [tag] so it can be located in tests independently of its name.
 *
 * @param tag the identifier used to find the component.
 * @return this modifier with the test tag declared on it.
 */
public fun SwingModifier.testTag(tag: String): SwingModifier = this then propertyElement(TestTagProperty, tag)

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

private val TestTagProperty =
    PropertyAccessors<JComponent, String?>(
        name = "testTag",
        read = { it[TEST_TAG_KEY] },
        write = { component, value -> component[TEST_TAG_KEY] = value },
    )

/**
 * Sets a `putClientProperty` entry - the way to reach look-and-feel styling keys
 * and accessibility hints. Each distinct [key] is an independent modifier slot; `null` removes the
 * entry, and removing the declaration puts back the value the component carried before. Requires a
 * `JComponent` target.
 *
 * A look and feel may derive other properties from this entry; removing the declaration restores the
 * entry, not what was derived from it. A declaration of the same key and value as the pass before is
 * adopted rather than written again, since writing the value already standing announces nothing for a
 * look and feel to answer. To have a derivation run again, use
 * [org.jetbrains.compose.swing.modifier.key].
 *
 * @param key the property key, matched by equality.
 * @param value the value stored under [key]; a look and feel acts on the change the write announces, not
 *   on later changes made inside the value itself.
 * @return this modifier with the client property declared on it.
 * @see javax.swing.JComponent.putClientProperty
 */
public fun SwingModifier.clientProperty(
    key: Any,
    value: Any?,
): SwingModifier =
    this then
        ClientPropertyElement(key, value)

/**
 * A client-property entry, whose last-wins slot is keyed by the property key rather than by its class,
 * so distinct keys are independent slots even though they share this runtime class. A fixed-property
 * element keyed by its own class never equals such a key, so no collision with one is possible.
 *
 * The accessors are built around [propertyKey], so the slot's node is given its own pair as it is built.
 */
private class ClientPropertyElement(
    private val propertyKey: Any,
    private val declared: Any?,
) : SwingModifier.NodeElement<JComponent, PropertyNode<JComponent, Any?>>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val name: String get() = "clientProperty"

    override val key: Any get() = propertyKey

    override val restores: RestorePolicy get() = RestorePolicy.DeclaredPropertyOnly

    /** The key names the entry written, so it stands beside the value written under it. */
    override val declaredValues: Map<String, Any?> get() = mapOf("key" to propertyKey, name to declared)

    override fun create(): PropertyNode<JComponent, Any?> =
        PropertyNode(
            propertyKey,
            read = { it.getClientProperty(propertyKey) },
            write = { component, value -> component.putClientProperty(propertyKey, value) },
        )

    override fun update(node: PropertyNode<JComponent, Any?>) {
        node.apply(declared)
    }

    /** Two declarations of one key and one value are equal, so a modifier re-declaring the entry is adopted. */
    override fun equals(other: Any?): Boolean =
        other is ClientPropertyElement && propertyKey == other.propertyKey && declared == other.declared

    override fun hashCode(): Int = 31 * propertyKey.hashCode() + (declared?.hashCode() ?: 0)
}

private val NameProperty =
    PropertyAccessors<Component, String?>(
        name = "name",
        read = { it.name },
        write = { component, value -> component.name = value },
    )
