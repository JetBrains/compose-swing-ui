@file:JvmMultifileClass
@file:JvmName("SwingModifierKt")

package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * Declares [descriptor] on the component, [value] for as long as this declaration stands. The property is
 * read as the declaration arrives and written back when the declaration leaves, so a widget that
 * outlives it carries what it did before.
 *
 * Build [descriptor] once, as a top-level `val`, with [ComponentPropertyDescriptor]'s constructors:
 *
 * ```
 * private val DividerSizeProperty =
 *     ComponentPropertyDescriptor<JSplitPane, Int>(
 *         name = "dividerSize",
 *         read = { it.dividerSize },
 *         write = { pane, v -> pane.dividerSize = v },
 *     )
 *
 * private fun SwingModifier.declaredDividerSize(dividerSize: Int?): SwingModifier =
 *     if (dividerSize == null) this else property(DividerSizeProperty, dividerSize)
 * ```
 *
 * Declare only a property this library ships no modifier of its own for. Two declarations of one
 * [ComponentPropertyDescriptor.name] - even through two different handles - share one slot: the last one declared
 * wins, and they take one record of what the property stood at before either wrote it, put back from
 * whichever of them leaves last.
 *
 * @param descriptor the property being written.
 * @param value the value to write while this declaration stands.
 * @param restores what the component is held to once this declaration leaves.
 *   [RestorePolicy.EverythingWritten], the default, holds it to carrying [descriptor]'s read answer again.
 *   [RestorePolicy.DeclaredPropertyOnly] holds it to [descriptor]'s name alone, for a write a look and
 *   feel derives a property of its own from. [RestorePolicy.None] is for a property the component offers
 *   no way to give back - one whose setter refuses the value read answered with, or rebuilds the
 *   component's UI - and [descriptor]'s own write says what the component keeps instead.
 * @param inheritable lets the declaration be provided to descendants as a component default - a property
 *   such as a color or a font that makes sense across a whole subtree. A property [descriptor] serves only
 *   some component types reaches only those among the descendants.
 * @param rewriteOn names a bean property whose announced change means [value] may have been overwritten,
 *   so the node listens for it and writes [value] again. Name the property itself where the component
 *   announces every change of it, its own write included. Reassertion happens only after a matching
 *   `PropertyChangeEvent`; a component or look and feel that mutates it silently cannot be observed or
 *   reasserted.
 * @param alsoOverwrites properties [descriptor]'s own write lands on besides the one it declares - a
 *   coarse geometry naming each axis it covers, a button's fill keeping its opaque flag in step with it.
 *   Each is held beside the declared one and given back after it, so a removal puts back every property
 *   the write touched, not only the one this declaration names.
 * @return this modifier with [descriptor] declared on it.
 */
@Suppress("LongParameterList")
// One parameter per independent facet of the declaration: the property and its value, what the
// component is held to once the declaration leaves, whether it cascades as a default, and the two ways
// a write reaches another property.
public fun <T : Component, V> SwingModifier.property(
    descriptor: ComponentPropertyDescriptor<T, V>,
    value: V,
    restores: RestorePolicy = RestorePolicy.EverythingWritten,
    inheritable: Boolean = false,
    rewriteOn: String? = null,
    alsoOverwrites: List<ComponentPropertyDescriptor<T, *>> = emptyList(),
): SwingModifier =
    this then PropertyElement(descriptor, value, restores, inheritable, rewriteOn, alsoOverwrites)

/**
 * A declaration made through [descriptor]. Identified by [descriptor]'s own
 * [ComponentPropertyDescriptor.name] rather than by a class or an accessor's identity, so two declarations
 * of one name - even through two different
 * [ComponentPropertyDescriptor] handles - share one slot and one [PropertyCaptures] hold, the way [descriptor]'s
 * documentation describes.
 *
 * Two elements are equal when they declare the *same* [descriptor] - identity, so a pair allocated once,
 * beside the builder or on the property object, compares equal across passes - and carry an equal
 * [value]. [restores], [inheritable], [rewriteOn] and [alsoOverwrites] are fixed per property, so they
 * take no part in equality.
 *
 * Internal rather than file-private so [org.jetbrains.compose.swing.defaults.ComponentDefaults] can tell
 * a property declaration apart from a plain [SwingModifier.NodeElement] and ask [handles] which classes
 * an inherited default built from one reaches.
 */
internal class PropertyElement<T : Component, V>(
    private val descriptor: ComponentPropertyDescriptor<T, V>,
    private val value: V,
    override val restores: RestorePolicy,
    override val inheritable: Boolean,
    private val rewriteOn: String?,
    private val alsoOverwrites: List<ComponentPropertyDescriptor<T, *>>,
) : SwingModifier.NodeElement<T, PropertyNode<T, V>>() {
    @Suppress("UNCHECKED_CAST")
    override val targetType: Class<T> get() = descriptor.targetType as Class<T>

    override val name: String get() = descriptor.name

    override val key: Any get() = descriptor.name

    /** Whether a component of [componentClass] is one [descriptor] applies to. */
    fun handles(componentClass: Class<*>): Boolean = descriptor.handles(componentClass)

    override val declaredValues: Map<String, Any?> get() = mapOf(name to value)

    override val heldProperties: Set<String>
        get() = if (alsoOverwrites.isEmpty()) setOf(name) else alsoOverwrites.mapTo(mutableSetOf(name)) { it.name }

    override fun create(): PropertyNode<T, V> =
        PropertyNode(descriptor.name, descriptor.read, descriptor.write, rewriteOn, alsoOverwrites)

    override fun update(node: PropertyNode<T, V>) {
        node.rebind(descriptor.read, descriptor.write)
        node.apply(value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyElement<*, *>) return false
        return descriptor === other.descriptor && value == other.value
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(descriptor) + (value?.hashCode() ?: 0)
}
