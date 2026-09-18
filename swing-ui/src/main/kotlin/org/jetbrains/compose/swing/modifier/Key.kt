@file:JvmMultifileClass
@file:JvmName("SwingModifierKt")

package org.jetbrains.compose.swing.modifier

/**
 * Ties this modifier's application to [keys]. While they stand the modifier is diffed as usual; a key
 * that does not compare equal to the one applied last rebuilds from scratch what the modifier writes onto
 * the component.
 *
 * This is `androidx.compose.runtime.key` for a modifier - the identity of the modifier's application. It
 * is not the slot key an element is matched by ([SwingModifier.NodeElement.key]).
 *
 * Declare it where a look and feel works a property out from a write of yours and has to work it out
 * again: the theme it derives from has changed, while the declarations describing the component have
 * not. Nothing else asks for that. A declaration whose value stands writes the value already there, and
 * Swing announces nothing for a write that changes nothing, so the derivation never runs. A rebuild
 * writes two different values instead - the restore puts back what the modifier found, then the
 * re-application writes the declaration again - and both are announced.
 *
 * A rebuild is a real teardown. Every [SwingModifier.ComponentNode] detaches in the reverse of the order
 * the modifier declared them, putting back what it captured, then attaches afresh and captures what it
 * finds by then. Anything such a node binds - a caret, a document, a model, an installed listener - is
 * torn down and built again with it, so transient state those objects hold does not survive. A
 * [ParentLayoutNode][org.jetbrains.compose.swing.layout.ParentLayoutNode] writes nothing onto the
 * component, so it stands and is diffed as usual, keeping its state.
 *
 * The keys belong to the modifier rather than to a place in it: `SwingModifier.key(t).background(c)` and
 * `SwingModifier.background(c).key(t)` say the same thing. Declaring again adds keys rather than
 * replacing them, so a builder that ties its own writes to a key keeps the one its caller declared:
 * `key(a).key(b)` says what `key(a, b)` does. One declaration repeated counts once, standing where it
 * was declared last.
 *
 * @param keys what this modifier's application is tied to, each compared by `equals`. A key that is a
 *   fresh instance on every pass - a bare `Any()` - rebuilds on every pass.
 * @return this modifier tied to [keys].
 */
public fun SwingModifier.key(vararg keys: Any?): SwingModifier = this then KeyElement(keys.asList())

/**
 * The keys a modifier's application is tied to. The walk over a modifier takes them off it for the node
 * holder, which compares them against the ones applied last before the element diff runs, so this carries
 * no modifier node of its own.
 *
 * Declaring again adds keys rather than replacing them, so the walk gathers what every declaration
 * carries, in declaration order.
 */
internal data class KeyElement(
    val tokens: List<Any?>,
) : SwingModifier.Element,
    SwingModifier.InspectableElement {
    override val name: String get() = "key"

    override val declaredValues: Map<String, Any?> get() = mapOf("keys" to tokens)
}
