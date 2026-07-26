package org.jetbrains.compose.swing.modifier

import java.awt.Component

/**
 * A [SwingModifier.Node] for a single component property. On [onAttach] it captures the property's
 * pre-modifier value as a restore action; on each apply it writes the latest value; on [onDetach] it
 * runs the captured restore. [read] reads the current value (for capture) and [write] applies a value.
 *
 * This is the shape every appearance/layout/metadata/accessibility property element shares: capture
 * once, write the new value, restore on removal. The restore is held as a closure over the captured
 * value, so no value is stored or cast back through erasure.
 */
internal class PropertyNode<T : Component, V>(
    private val read: (component: T) -> V,
    private val write: (component: T, value: V) -> Unit,
) : SwingModifier.Node<T>() {
    private var restore: (() -> Unit)? = null

    override fun onAttach() {
        val component = component
        val original = read(component)
        restore = { write(component, original) }
    }

    /** Writes [value]; call from the owning element's `update` with its latest data. */
    fun apply(value: V): Unit = write(component, value)

    override fun onDetach() {
        restore?.invoke()
    }
}

/**
 * Base [SwingModifier.Element] for a single component property, backed by a [PropertyNode]. Holds the
 * [value] to write plus the property's [read]/[write] accessors. The last-wins slot is keyed by the
 * class of the [write] accessor: each property's builder declares its own `write` lambda - its own
 * class - so two distinct properties never collapse into one slot, while every invocation of one
 * builder shares that builder's slot. [create] builds the node; [update] writes this element's [value]
 * through it.
 *
 * Build a single property with [propertyElement], which derives [targetType] from the reified type.
 * For a property whose distinct instances must be independent slots (a client property keyed by its
 * property key), subclass this and override [SwingModifier.Element.key] instead.
 */
internal open class PropertyElement<T : Component, V>(
    final override val targetType: Class<T>,
    private val value: V,
    private val read: (component: T) -> V,
    private val write: (component: T, value: V) -> Unit,
) : SwingModifier.Element<T, PropertyNode<T, V>> {
    override val key: Any get() = write.javaClass

    final override fun create(): PropertyNode<T, V> = PropertyNode(read, write)

    final override fun update(node: PropertyNode<T, V>) {
        node.apply(value)
    }
}

/**
 * Builds a single-property [SwingModifier.Element], deriving [targetType] from the reified [T]. The
 * element's last-wins slot is keyed by the class of its [write] lambda, so one modifier builder must
 * declare exactly one `write` accessor (call this exactly once): every invocation of that builder then
 * shares one slot (last wins), while a different builder declares a different lambda - a different
 * class, an independent slot.
 *
 * [read] captures the property's pre-modifier value for restore; [write] applies a value. Both are
 * `noinline` - they are stored in the node, not invoked at the call site.
 */
internal inline fun <reified T : Component, V> propertyElement(
    value: V,
    noinline read: (component: T) -> V,
    noinline write: (component: T, value: V) -> Unit,
): SwingModifier.Element<T, PropertyNode<T, V>> = PropertyElement(T::class.java, value, read, write)
