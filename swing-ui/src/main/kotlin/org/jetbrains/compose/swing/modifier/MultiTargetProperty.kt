package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * One component type's accessors for a property that several unrelated types declare separately.
 *
 * [read] and [write] are the accessors of exactly one [type]; a case never sees a component of any
 * other type, because [handles] gates both.
 */
internal class PropertyCase<T : Component, V>(
    private val type: Class<T>,
    private val read: (component: T) -> V,
    private val write: (component: T, value: V) -> Unit,
) {
    /** The name of the component type this case serves, for the mismatch message. */
    val typeName: String get() = type.name

    /** Whether this case serves [component]. */
    fun handles(component: Component): Boolean = type.isInstance(component)

    /** Reads the property from [component]; call only when [handles] is `true`. */
    fun readFrom(component: Component): V = read(type.cast(component))

    /** Writes the property onto [component]; call only when [handles] is `true`. */
    fun writeTo(
        component: Component,
        value: V,
    ): Unit = write(type.cast(component), value)
}

/** Builds a [PropertyCase], deriving the component type from the reified [T]. */
internal inline fun <reified T : Component, V> propertyCase(
    noinline read: (component: T) -> V,
    noinline write: (component: T, value: V) -> Unit,
): PropertyCase<T, V> = PropertyCase(T::class.java, read, write)

/**
 * A property that more than one component type declares, with no supertype declaring it between them -
 * `icon`, which `JLabel` and `AbstractButton` each declare for themselves, is the archetype.
 *
 * A modifier element names a single target type, so such a property cannot be expressed as one. It is
 * expressed instead as the [cases] that serve it: the element accepts any component, and the case that
 * [handles][PropertyCase.handles] it supplies both accessors. Routing the read and the write through
 * the same decision is what keeps a value restored through the accessor it was captured with.
 *
 * A component no case serves is rejected when the property is first applied, naming the types that are
 * served, so a caller learns the same thing they would from a target-type mismatch.
 */
internal class MultiTargetProperty<V>(
    private val name: String,
    private vararg val cases: PropertyCase<*, V>,
) {
    /** Reads the property, capturing the value to restore when the element leaves the chain. */
    fun read(component: Component): V = caseFor(component).readFrom(component)

    /** Writes the property. */
    fun write(
        component: Component,
        value: V,
    ): Unit = caseFor(component).writeTo(component, value)

    /** The property's name; also its slot identity, so one property occupies one last-wins slot. */
    override fun toString(): String = name

    private fun caseFor(component: Component): PropertyCase<*, V> =
        cases.firstOrNull { it.handles(component) }
            ?: error(
                "Modifier element $name requires a ${cases.joinToString(" or ") { it.typeName }} " +
                    "target, but the component is a ${component.javaClass.name}",
            )
}

/**
 * A [PropertyElement] for a [MultiTargetProperty]. Accepts any component so the property's own
 * mismatch message is the one a caller sees, and takes its last-wins slot from the property, so every
 * application of one property shares a slot while distinct properties stay independent.
 */
internal class MultiTargetPropertyElement<V>(
    private val property: MultiTargetProperty<V>,
    value: V,
) : PropertyElement<Component, V>(
        Component::class.java,
        value,
        read = property::read,
        write = property::write,
    ) {
    override val key: Any get() = property
}
