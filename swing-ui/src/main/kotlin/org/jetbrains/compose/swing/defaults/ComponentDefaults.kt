@file:JvmMultifileClass
@file:JvmName("ComponentDefaultsKt")

package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.modifier.CombinedSwingModifier
import org.jetbrains.compose.swing.modifier.KeyElement
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import java.awt.Component

/**
 * Validates that all elements in [modifier] returned by a [ComponentDefaultKey.apply] function are
 * eligible to cascade as defaults.
 *
 * Rejects non-inheritable node elements, additive elements, slot attachments, layout constraints,
 * layout measurement elements, and unknown raw [SwingModifier.Element] instances.
 */
internal fun validateDefaultModifier(
    key: ComponentDefaultKey<*>,
    modifier: SwingModifier,
): List<SwingModifier.Element> {
    val elements = mutableListOf<SwingModifier.Element>()
    modifier.foldIn(Unit) { _, element ->
        when (element) {
            is KeyElement -> {
                elements.add(element)
            }

            is SwingModifier.NodeElement<*, *> -> {
                require(element.inheritable) {
                    "ComponentDefaultKey '${key.name}' cannot declare non-inheritable element: $element"
                }
                require(!element.additive) {
                    "ComponentDefaultKey '${key.name}' cannot declare additive element: $element"
                }
                elements.add(element)
            }

            else -> {
                throw IllegalArgumentException(
                    "ComponentDefaultKey '${key.name}' cannot declare element: $element",
                )
            }
        }
    }
    return elements
}

internal class DefaultEntry<V : Any>(
    val key: ComponentDefaultKey<V>,
    val value: V,
    val elements: List<SwingModifier.Element>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DefaultEntry<*>) return false
        return key === other.key && value == other.value && elements == other.elements
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(key)
        result = 31 * result + value.hashCode()
        result = 31 * result + elements.hashCode()
        return result
    }
}

/**
 * An ordered collection of active component default entries, logically immutable.
 */
internal class ComponentDefaults internal constructor(
    private val entries: Map<ComponentDefaultKey<*>, DefaultEntry<*>>,
) {
    // Scoped to this logically immutable defaults snapshot.
    // Populated only for concrete component classes rendered under it.
    private val cache = HashMap<Class<*>, SwingModifier>()

    fun <V : Any> get(key: ComponentDefaultKey<V>): V? {
        // Safe because DefaultEntry is created with matching ComponentDefaultKey<V> and V,
        // and ComponentDefaultKey has identity equality.
        @Suppress("UNCHECKED_CAST")
        return (entries[key] as? DefaultEntry<V>)?.value
    }

    internal fun withProvision(provision: ProvidedComponentDefault<*>): ComponentDefaults =
        derive { applyTypedProvision(it, provision) }

    internal fun withProvisions(provisions: Array<out ProvidedComponentDefault<*>>): ComponentDefaults =
        if (provisions.isEmpty()) this else derive { map -> provisions.forEach { applyTypedProvision(map, it) } }

    private inline fun derive(
        provide: (LinkedHashMap<ComponentDefaultKey<*>, DefaultEntry<*>>) -> Unit,
    ): ComponentDefaults {
        val newMap = LinkedHashMap(entries)
        provide(newMap)
        return when {
            areOrderedEntriesEqual(newMap, entries) -> this
            newMap.isEmpty() -> Empty
            else -> ComponentDefaults(newMap)
        }
    }

    /** [declared] behind the defaults that apply to a component of [componentClass]. */
    fun effectiveModifier(
        componentClass: Class<*>,
        declared: SwingModifier,
    ): SwingModifier {
        val inherited = modifierFor(componentClass)
        return if (inherited === SwingModifier) declared else InheritingSwingModifier(inherited, declared)
    }

    fun modifierFor(componentClass: Class<*>): SwingModifier {
        if (entries.isEmpty()) return SwingModifier
        return cache.getOrPut(componentClass) { buildModifierFor(componentClass) }
    }

    private fun buildModifierFor(componentClass: Class<*>): SwingModifier {
        var result: SwingModifier = SwingModifier
        for (entry in entries.values) {
            // Validation leaves only keys and node elements; a key stays only beside a property that applies.
            val applicable = entry.elements.filter { it is KeyElement || it.appliesTo(componentClass) }
            if (applicable.any { it !is KeyElement }) applicable.forEach { result = result then it }
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComponentDefaults) return false
        return areOrderedEntriesEqual(entries, other.entries)
    }

    override fun hashCode(): Int {
        var result = 1
        for (entry in entries.values) {
            result = 31 * result + entry.hashCode()
        }
        return result
    }

    companion object {
        val Empty: ComponentDefaults = ComponentDefaults(emptyMap())
    }
}

/**
 * Whether this node element can be applied to a component of [componentClass]: for a [PropertyElement],
 * whichever classes its [org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor] handle
 * serves, rather than [SwingModifier.NodeElement.targetType], which a multi-type property leaves at
 * `Component` so its own mismatch message is the one a direct declaration sees.
 */
private fun SwingModifier.Element.appliesTo(componentClass: Class<*>): Boolean =
    when (this) {
        is PropertyElement<*, *> -> handles(componentClass)
        else -> (this as SwingModifier.NodeElement<*, *>).targetType.isAssignableFrom(componentClass)
    }

/**
 * An effective chain: the inherited defaults, then the modifier the node declared. Marked apart from any
 * other combined chain so the declared part can be recovered when only the defaults change.
 */
private class InheritingSwingModifier(
    inherited: SwingModifier,
    declared: SwingModifier,
) : CombinedSwingModifier(inherited, declared)

/**
 * Re-diffs this node's modifier behind the defaults it now stands under, unless the chain it applied last
 * already carries them and its last diff finished. A node released since it was held has applied nothing and is
 * skipped.
 */
internal fun SwingNodeHolder<Component>.refreshInheritedDefaults() {
    val state = modifierState ?: return
    val declared = state.declared
    val defaults = compositionLocalMap[LocalComponentDefaults]
    val inheriting = declared as? InheritingSwingModifier
    val finished = state.applied === declared
    if (finished && (inheriting?.outer ?: SwingModifier) == defaults.modifierFor(component.javaClass)) return
    applyDeclaredModifier(defaults.effectiveModifier(component.javaClass, inheriting?.inner ?: declared))
}

private fun areOrderedEntriesEqual(
    first: Map<ComponentDefaultKey<*>, DefaultEntry<*>>,
    second: Map<ComponentDefaultKey<*>, DefaultEntry<*>>,
): Boolean {
    if (first.size != second.size) return false
    val it2 = second.entries.iterator()
    return first.entries.all { e1 ->
        val e2 = it2.next()
        e1.key === e2.key && e1.value == e2.value
    }
}

private fun <V : Any> applyTypedProvision(
    map: LinkedHashMap<ComponentDefaultKey<*>, DefaultEntry<*>>,
    provision: ProvidedComponentDefault<V>,
) {
    checkEventDispatchThread()
    val key = provision.key
    val value = provision.value
    if (value == null) {
        map.remove(key)
    } else {
        // Remove and reinsert non-null replacements so the latest provision remains last in iteration order.
        map.remove(key)
        val entry = createEntry(key, value)
        map[key] = entry
    }
}

private fun <V : Any> createEntry(
    key: ComponentDefaultKey<V>,
    value: V,
): DefaultEntry<V> {
    val modifier = SwingModifier.(key.apply)(value)
    val elements = validateDefaultModifier(key, modifier)
    return DefaultEntry(key, value, elements)
}

// Scoped by ProvideComponentDefaults to cascade inheritable defaults across nodes
// rather than acting as an app-level implicit dependency.
@Suppress("CompositionLocalAllowlist")
internal val LocalComponentDefaults: ProvidableCompositionLocal<ComponentDefaults> =
    compositionLocalOf { ComponentDefaults.Empty }

internal class ProvisionsKey(
    private val provisions: Array<out ProvidedComponentDefault<*>>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProvisionsKey) return false
        return provisions.contentEquals(other.provisions)
    }

    override fun hashCode(): Int = provisions.contentHashCode()
}
