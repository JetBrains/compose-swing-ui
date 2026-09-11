@file:JvmMultifileClass
@file:JvmName("ComponentDefaultsKt")

package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * An identity key naming an independent part of a scoped cascade of modifier property declarations.
 *
 * Keys compare by identity (`===`). [name] is used for diagnostics, validation failures and [toString];
 * two keys with the same name remain independent. The type parameter `V` is the type of value this key
 * expects, which must be non-nullable (`null` is reserved by [provides] for removing this key from a
 * nested subtree).
 *
 * @property name the non-blank descriptive name of this key.
 * @property apply the transformation applying a provided value to a [SwingModifier].
 */
@Stable
public class ComponentDefaultKey<V : Any> internal constructor(
    public val name: String,
    internal val apply: SwingModifier.(V) -> SwingModifier,
) {
    init {
        require(name.isNotBlank()) { "ComponentDefaultKey name must not be blank" }
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString(): String = "ComponentDefaultKey($name)"
}

/**
 * Creates a new [ComponentDefaultKey] with identity equality.
 *
 * [apply] must be deterministic for its value: any state that changes the modifier it builds belongs in
 * [V], because the provider may reuse the result for a structurally equal provision.
 *
 * @param name non-blank descriptive name of this key.
 * @param apply transforms an empty [SwingModifier] receiver with the provided value [V].
 * @return an independent [ComponentDefaultKey] instance.
 */
public fun <V : Any> componentDefaultKeyOf(
    name: String,
    apply: SwingModifier.(V) -> SwingModifier,
): ComponentDefaultKey<V> = ComponentDefaultKey(name, apply)

/**
 * A token binding a [ComponentDefaultKey] to an optional value to be passed to [ProvideComponentDefaults].
 *
 * Compares by key identity and structural value equality. The type parameter `V` is the value type.
 */
public class ProvidedComponentDefault<V : Any> internal constructor(
    internal val key: ComponentDefaultKey<V>,
    internal val value: V?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProvidedComponentDefault<*>) return false
        return key === other.key && value == other.value
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(key) + (value?.hashCode() ?: 0)

    override fun toString(): String = "${key.name} provides $value"
}

/**
 * Binds this [ComponentDefaultKey] to [value] for use in [ProvideComponentDefaults].
 *
 * Passing `null` removes this key throughout the nested subtree, restoring the outer provision upon
 * exiting the provider.
 *
 * @param value the value to cascade, or `null` to remove this key's declaration.
 * @return a [ProvidedComponentDefault] token.
 */
public infix fun <V : Any> ComponentDefaultKey<V>.provides(value: V?): ProvidedComponentDefault<V> =
    ProvidedComponentDefault(this, value)

/**
 * Reads the nearest surviving value provided for this key in the current composition hierarchy, or
 * `null` when the key is absent or was removed.
 *
 * Reports the inherited declaration rather than the value currently visible on any component: a later key
 * or a node's own explicit modifier may win the same property.
 */
public val <V : Any> ComponentDefaultKey<V>.current: V?
    @Composable
    @ReadOnlyComposable
    get() = LocalComponentDefaults.current.get(this)
