package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * One Swing property of one or more component types. Define it once, in a top-level `val`, and declare
 * it on a component with [SwingModifier.property]; see that function for the full contract.
 *
 * Build a property of a single component type with the single-type [invoke] overload:
 *
 * ```
 * private val OpaqueProperty =
 *     ComponentPropertyDescriptor<JComponent, Boolean>(
 *         name = "opaque",
 *         read = { it.isOpaque },
 *         write = { c, v -> c.isOpaque = v }
 *     )
 * ```
 *
 * Build one that several unrelated types each declare for themselves - `icon`, which `JLabel` and
 * `AbstractButton` each declare on their own, with no shared supertype that does - from
 * [ComponentPropertyAccessor]s built with [accessor], one per type:
 *
 * ```
 * private val IconProperty =
 *     ComponentPropertyDescriptor(
 *         "icon",
 *         accessor<JLabel, Icon?>(
 *             read = { it.icon }, write = { c, v -> c.icon = v }
 *         ),
 *         accessor<AbstractButton, Icon?>(
 *             read = { it.icon }, write = { c, v -> c.icon = v }
 *         ),
 *     )
 * ```
 *
 * [name] is this property's whole identity: see [SwingModifier.property] for what that means across
 * declarations and across handles.
 *
 * The primary constructor is public intentionally for callers who need to provide a custom supported-type
 * predicate. Prefer the companion builders when they express the property: they derive the runtime type
 * information and keep the accessors together. When using this constructor directly, [targetType] must be
 * compatible with [T], [handles] must return `true` exactly for component types whose instances [read] and
 * [write] can safely handle, [read] must return the current property value, and [write] must apply the
 * supplied value to that property. The constructor's `name` is the stable property identity;
 * `targetType` identifies the represented component type, `read` and `write` access its value, and
 * `handles` reports which component types the descriptor supports.
 */
public class ComponentPropertyDescriptor<in T : Component, V>(
    public val name: String,
    internal val targetType: Class<out Component>,
    internal val read: (component: T) -> V,
    internal val write: (component: T, value: V) -> Unit,
    internal val handles: (componentClass: Class<*>) -> Boolean,
) {
    /**
     * Takes a hold on this property for [component], capturing what it stands at where nothing holds it
     * yet. A member of this class, rather than a top-level function taking the accessors apart, so
     * [SwingModifier.property]'s `alsoOverwrites` - a `List<ComponentPropertyDescriptor<T, *>>` - can call it without
     * needing this property's own value type.
     */
    internal fun holdCapture(
        captures: PropertyCaptures,
        component: T,
    ): PropertyCaptures.Hold = captures.hold(name, component, read, write)

    /** This property's name, which is also how a tool showing a modifier's elements names it. */
    override fun toString(): String = name

    /** The two ways to build a [ComponentPropertyDescriptor]: for one component type, or for several. */
    public companion object {
        /**
         * The property [name] of components of type [T].
         *
         * Declare it in a function of its own, not inline at a call site, the way
         * [SwingModifier.property]'s own documentation directs: a `write` written out once gives every
         * widget declaring the property one slot, and a `write` that captures anything is a fresh
         * instance each pass.
         *
         * @param name the Swing property [read] and [write] reach.
         * @param read answers with the value a component of type [T] holds.
         * @param write puts a value onto a component of type [T].
         */
        public inline operator fun <reified T : Component, V> invoke(
            name: String,
            noinline read: (component: T) -> V,
            noinline write: (component: T, value: V) -> Unit,
        ): ComponentPropertyDescriptor<T, V> {
            val type = T::class.java
            return ComponentPropertyDescriptor(name, type, read, write, handles = type::isAssignableFrom)
        }

        /**
         * The property [name] that each of [accessors] declares for its own component type.
         *
         * Applying the built property to a component none of [accessors] serves fails with a message naming
         * the served types - whether that application is a direct declaration or an inherited default -
         * the same way applying a single-type property outside its declared component type does; see [SwingModifier.property].
         *
         * @param name the property every case declares under its own accessors.
         * @param accessors one accessor per component type the property serves, built with [accessor].
         */
        public operator fun <V> invoke(
            name: String,
            vararg accessors: ComponentPropertyAccessor<*, V>,
        ): ComponentPropertyDescriptor<Component, V> {
            fun caseFor(component: Component): ComponentPropertyAccessor<*, V> =
                accessors.firstOrNull { it.handles(component) }
                    ?: error(
                        "Modifier element $name requires a ${accessors.joinToString(" or ") { it.typeName }} " +
                            "target, but the component is a ${component.javaClass.name}",
                    )
            return ComponentPropertyDescriptor(
                name,
                Component::class.java,
                read = { component -> caseFor(component).readFrom(component) },
                write = { component, value -> caseFor(component).writeTo(component, value) },
                handles = { componentClass -> accessors.any { it.handles(componentClass) } },
            )
        }

        /**
         * A [ComponentPropertyAccessor] of [T], for this property's multi-type constructor:
         * `ComponentPropertyDescriptor.accessor<JLabel, Icon?>(read, write)`.
         *
         * @param read answers with the value a component of type [T] holds.
         * @param write puts a value onto a component of type [T].
         */
        public inline fun <reified T : Component, V> accessor(
            noinline read: (component: T) -> V,
            noinline write: (component: T, value: V) -> Unit,
        ): ComponentPropertyAccessor<T, V> = ComponentPropertyAccessor(T::class.java, read, write)
    }

    /**
     * One component type's accessors for a property that several unrelated types declare separately,
     * built with [accessor].
     *
     * [read] and [write] are the accessors of exactly one component type; an accessor never sees a component
     * of any other type, because [handles] gates both.
     */
    public class ComponentPropertyAccessor<T : Component, V>
        @PublishedApi
        internal constructor(
            private val type: Class<T>,
            private val read: (component: T) -> V,
            private val write: (component: T, value: V) -> Unit,
        ) {
            /** The name of the component type this accessor serves, for the mismatch message. */
            internal val typeName: String get() = type.name

            internal fun handles(component: Component): Boolean = type.isInstance(component)

            internal fun handles(componentClass: Class<*>): Boolean = type.isAssignableFrom(componentClass)

            /** Call only when [handles] is `true`. */
            internal fun readFrom(component: Component): V = read(type.cast(component))

            /** Call only when [handles] is `true`. */
            internal fun writeTo(
                component: Component,
                value: V,
            ): Unit = write(type.cast(component), value)
        }
}
