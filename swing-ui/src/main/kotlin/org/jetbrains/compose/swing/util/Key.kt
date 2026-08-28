package org.jetbrains.compose.swing.util

import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

/**
 * A typed [JComponent] client-property key, read and written through the indexing operators:
 * `component[KEY]` answers what is stored under it, `component[KEY] = value` stores, and assigning
 * `null` clears.
 *
 * Every client property the library owns is declared as one of these. A raw key belongs only to the
 * public `clientProperty` modifier, which carries a key of someone else's - a look-and-feel styling
 * property, say.
 *
 * The type parameter is what the property holds, so a read answers that type and a write takes it, and
 * [name] is what the key shows as - what a component's client-property bag lists it under, and the
 * property name `putClientProperty` fires its `PropertyChangeEvent` with, which a listener bound to a
 * property matches on.
 *
 * A key is also a slot nobody else can reach: a raw string key is collided with by any third-party
 * `putClientProperty` storing under the same string, while a key compares by identity, so the only way
 * to address a slot is the declaration that owns it. Declare a key once, as a top-level `val` - two
 * keys sharing a [name] are two slots.
 */
internal class Key<T>(
    @NonNls val name: String,
) {
    /** The property name a write fires under: `putClientProperty` names the event `key.toString()`. */
    override fun toString(): String = name
}

/** The value stored under [key], or `null` where this component carries none. */
internal operator fun <T> JComponent.get(key: Key<T>): T? {
    // Only set(Key<T>, T?) writes under a Key, and it takes a T, so what is stored is a T.
    @Suppress("UNCHECKED_CAST")
    return getClientProperty(key) as T?
}

/** Stores [value] under [key], clearing the property when passed `null`. */
internal operator fun <T> JComponent.set(
    key: Key<T>,
    value: T?,
) {
    putClientProperty(key, value)
}
