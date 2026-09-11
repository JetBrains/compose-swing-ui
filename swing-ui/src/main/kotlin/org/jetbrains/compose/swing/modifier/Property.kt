@file:JvmMultifileClass
@file:JvmName("SwingModifierKt")

package org.jetbrains.compose.swing.modifier

import java.awt.Component
import kotlin.reflect.KClass

/**
 * Declares one Swing property on the component. The property is read as the declaration arrives and
 * written back when the declaration leaves, so a widget that outlives it carries what it did before.
 * Fold the element in only while a value is declared:
 *
 * ```
 * private fun SwingModifier.declaredDividerSize(dividerSize: Int?): SwingModifier =
 *     if (dividerSize == null) {
 *         this
 *     } else {
 *         property<JSplitPane, Int>(
 *             name = "dividerSize",
 *             value = dividerSize,
 *             read = { it.dividerSize },
 *             write = { pane, value -> pane.dividerSize = value },
 *         )
 *     }
 * ```
 *
 * Declare it in a function of its own, not inline at a call site. The property's slot is the class of
 * the [write] lambda. One `write` written out once gives every widget declaring that property one slot.
 * The same property written through two lambdas is two slots writing over each other, and a `write`
 * that captures anything is a fresh instance each pass, written again rather than adopted. A builder
 * may declare several properties, one `write` each.
 *
 * Declare only a property this library ships no modifier of its own for. Two slots writing one property
 * each take their own record of what it stood at, and the property is put back from the one leaving
 * last.
 *
 * Reach for this where a look and feel writes the property onto the widget itself. Where it only
 * publishes the value under a `UIManager` key and writes nothing onto the widget, read that key and
 * pass the answer as [value]: there is nothing to put back, so no element is needed.
 *
 * @param name the Swing property being written. It labels the property in an error and wherever a tool
 *   shows the modifier; [write] is what identifies the slot.
 * @param value the value to write while this declaration stands.
 * @param read answers with the value the component holds, taken as the declaration arrives.
 * @param write puts a value onto the component. It is called to write [value] and again to put back
 *   what [read] answered, so anything the write must ask for afterwards - a layout pass, a repaint -
 *   belongs inside it.
 * @param restores what the component is held to once this declaration leaves.
 *   [RestorePolicy.EverythingWritten], the default, holds it to carrying [read]'s answer again.
 *   [RestorePolicy.DeclaredPropertyOnly] holds it to [name] alone, for a write a look and feel derives a
 *   property of its own from. [RestorePolicy.None] is for a property the component offers no way to give
 *   back - one whose setter refuses the value [read] answered with, or rebuilds the component's UI - and
 *   [write] says what the component keeps instead.
 * @return this modifier with [name] declared on it.
 */
public inline fun <reified T : Component, V> SwingModifier.property(
    name: String,
    value: V,
    noinline read: (component: T) -> V,
    noinline write: (component: T, value: V) -> Unit,
    restores: RestorePolicy = RestorePolicy.EverythingWritten,
): SwingModifier = property(T::class, name, value, read, write, restores)

/**
 * Declares one Swing property on the component, naming the component type as a value.
 *
 * The same contract as the reified overload above, with [targetType] - the thing that one reifies -
 * spelled out. Reach for this when the component type is only known as a `KClass`.
 *
 * @param targetType the component type [read] and [write] receive; a node that is not one is rejected
 *   at apply with a clear error.
 * @param name the Swing property being written. It labels the property in an error and wherever a tool
 *   shows the modifier; [write] is what identifies the slot.
 * @param value the value to write while this declaration stands.
 * @param read answers with the value the component holds, taken as the declaration arrives.
 * @param write puts a value onto the component. It is called to write [value] and again to put back
 *   what [read] answered, so anything the write must ask for afterwards - a layout pass, a repaint -
 *   belongs inside it.
 * @param restores what the component is held to once this declaration leaves.
 * @return this modifier with [name] declared on it.
 */
@Suppress("LongParameterList")
// One parameter per independent facet of the declaration: the component type, the property named, the
// value, how it is read and written, and what the component is held to once the declaration leaves. The
// read and the write stay separate lambdas because the write's own class is what keys the slot.
public fun <T : Component, V> SwingModifier.property(
    targetType: KClass<T>,
    name: String,
    value: V,
    read: (component: T) -> V,
    write: (component: T, value: V) -> Unit,
    restores: RestorePolicy = RestorePolicy.EverythingWritten,
): SwingModifier {
    val type = targetType.java
    return this then
        when (restores) {
            RestorePolicy.None -> UnrestoredDeclaration(type, name, value, read, write)
            RestorePolicy.DeclaredPropertyOnly -> DeclaredOnlyDeclaration(type, name, value, read, write)
            else -> RestoredDeclaration(type, name, value, read, write)
        }
}

/**
 * A declaration made through [property]. Its accessors are the caller's own lambdas, held as they arrive
 * and compared one by one by identity, so a non-capturing pair adopts the slot across passes.
 *
 * What the declaration puts back when it leaves is its class rather than a field, so a pass that changes
 * it hands the slot an element the slot's node was not built for, and the slot restores and is built
 * again instead of quietly changing its word.
 */
private sealed class Declaration<T : Component, V>(
    final override val targetType: Class<T>,
    final override val name: String,
    private val value: V,
    private val read: (component: T) -> V,
    private val write: (component: T, value: V) -> Unit,
) : SwingModifier.NodeElement<T, PropertyNode<T, V>>() {
    final override val key: Any get() = write.javaClass

    final override val declaredValues: Map<String, Any?> get() = mapOf(name to value)

    final override fun create(): PropertyNode<T, V> = PropertyNode(key, read, write)

    final override fun update(node: PropertyNode<T, V>) {
        node.apply(value)
    }

    final override fun equals(other: Any?): Boolean =
        other is Declaration<*, *> &&
            javaClass == other.javaClass &&
            read === other.read &&
            write === other.write &&
            value == other.value

    final override fun hashCode(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + System.identityHashCode(write)
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}

private class RestoredDeclaration<T : Component, V>(
    targetType: Class<T>,
    name: String,
    value: V,
    read: (component: T) -> V,
    write: (component: T, value: V) -> Unit,
) : Declaration<T, V>(targetType, name, value, read, write)

private class DeclaredOnlyDeclaration<T : Component, V>(
    targetType: Class<T>,
    name: String,
    value: V,
    read: (component: T) -> V,
    write: (component: T, value: V) -> Unit,
) : Declaration<T, V>(targetType, name, value, read, write) {
    override val restores: RestorePolicy get() = RestorePolicy.DeclaredPropertyOnly
}

private class UnrestoredDeclaration<T : Component, V>(
    targetType: Class<T>,
    name: String,
    value: V,
    read: (component: T) -> V,
    write: (component: T, value: V) -> Unit,
) : Declaration<T, V>(targetType, name, value, read, write) {
    override val restores: RestorePolicy get() = RestorePolicy.None
}
