package org.jetbrains.compose.swing.modifier

import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * A [SwingModifier.ComponentNode] for a single component property. On [onAttach] it captures the property's
 * pre-modifier value as a restore action; on each apply it writes the latest value; on [onDetach] it
 * runs the captured restore. [read] reads the current value (for capture) and [write] applies a value.
 *
 * This is the shape every appearance/layout/metadata/accessibility property element shares: capture
 * once, write the new value, restore on removal. The restore is held as a closure over the captured
 * value, so no value is stored or cast back through erasure.
 *
 * For a property a component inherits from its parent when it declares none of its own - cursor, font,
 * background, foreground - [read] must return the component's *own* value, and `null` where the matching
 * `isXSet` is false.
 *
 * [interference] names the way another write and this one reach each other; see [PropertyInterference].
 * A second property this [write] overwrites is held beside the declared one and put back after it, so
 * its restore is the last write to reach it. Without that, a removal leaves it wherever the declared
 * property's restore left it, with no declaration naming it.
 *
 * Both are held through the modifier's [PropertyCaptures].
 */
internal class PropertyNode<T : Component, V>(
    private val slot: Any,
    private val read: (component: T) -> V,
    private val write: (component: T, value: V) -> Unit,
    private val interference: PropertyInterference<T>? = null,
) : SwingModifier.ComponentNode<T>(),
    PropertyChangeListener {
    // The bean property whose announcement means the declaration was overwritten, where one names it.
    private val reapplyOn: String? = (interference as? PropertyInterference.OverwrittenOn)?.announcedBy

    private var declared: PropertyCaptures.Hold? = null

    /** This slot's holds on the properties [write] overwrites besides the one it declares. */
    private var overwritten: List<PropertyCaptures.Hold> = emptyList()

    /** Writes the value applied last, held as a closure over it. */
    private var reapply: (() -> Unit)? = null

    override fun onAttach() {
        val component = component
        // A node built outside the modifier machinery holds no state, which this refuses rather than
        // capturing nothing.
        val state = checkNotNull(holder?.modifierState) { "A property node is attached by the modifier it belongs to" }
        val captures = state.captures()
        declared = captures.hold(slot, component, read, write)
        overwritten =
            (interference as? PropertyInterference.AlsoOverwrites<T, *>)?.hold(captures, component).orEmpty()
        reapplyOn?.let { component.addPropertyChangeListener(it, this) }
    }

    /** Writes [value]; call from the owning element's `update` with its latest data. */
    fun apply(value: V) {
        val component = component
        if (reapplyOn != null) reapply = { write(component, value) }
        write(component, value)
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        reapply?.invoke()
    }

    override fun onDetach() {
        reapplyOn?.let { component.removePropertyChangeListener(it, this) }
        declared?.release()
        overwritten.forEach { it.release() }
    }
}

/**
 * Base [SwingModifier.NodeElement] for a single component property, backed by a [PropertyNode]. Holds the
 * [value] to write plus the property's [accessors]. [create] builds the node; [update] writes this
 * element's [value] through it.
 *
 * Build a single property with [propertyElement], which derives [targetType] from the reified type and
 * documents the slot contract.
 *
 * The element reports itself under the accessors' name, with the [value] it declares. The name labels
 * the property and no more; the accessors' write is what identifies the slot.
 *
 * Two elements are equal when they are of the same class, take the same slot, carry the same [value], and
 * hold the *same* [accessors] - identity, so a pair allocated once, beside the builder or on the property
 * object, compares equal across passes, and its slot is adopted rather than written wherever no slot
 * ahead of it wrote on the same pass.
 *
 * [interference] and [inheritable] are fixed per property, so they take no part in equality.
 */
internal open class PropertyElement<T : Component, V>(
    final override val targetType: Class<T>,
    private val accessors: PropertyAccessors<T, V>,
    private val value: V,
    private val interference: PropertyInterference<T>? = null,
    final override val inheritable: Boolean = false,
) : SwingModifier.NodeElement<T, PropertyNode<T, V>>() {
    override val name: String get() = accessors.name

    override val key: Any get() = accessors.write.javaClass

    override val declaredValues: Map<String, Any?> get() = mapOf(name to value)

    override val heldProperties: Set<String>
        get() {
            val alsoOverwrites = interference as? PropertyInterference.AlsoOverwrites<T, *> ?: return setOf(name)
            return setOf(name) + alsoOverwrites.names()
        }

    final override fun create(): PropertyNode<T, V> = PropertyNode(key, accessors.read, accessors.write, interference)

    final override fun update(node: PropertyNode<T, V>) {
        node.apply(value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as PropertyElement<*, *>
        if (key != other.key) return false
        if (accessors !== other.accessors) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + System.identityHashCode(accessors)
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}

/**
 * Builds a single-property [SwingModifier.NodeElement], deriving
 * [targetType][SwingModifier.NodeElement.targetType] from the reified [T]. The element's last-wins slot
 * is keyed by the class of the [accessors]' write lambda, so each property holds its accessors in one
 * top-level value, allocated once: every invocation of the builder shares one slot (last wins) and
 * compares equal for an equal [value], while another property's write is a different class and an
 * independent slot.
 *
 * [inheritable] lets the element be provided to descendants as a component default - a property such as a
 * color or a font that makes sense across a whole subtree.
 *
 * See [PropertyInterference] for [interference]. A property the component works out again for itself is
 * built with [derivedPropertyElement] instead.
 */
internal inline fun <reified T : Component, V> propertyElement(
    accessors: PropertyAccessors<T, V>,
    value: V,
    interference: PropertyInterference<T>? = null,
    inheritable: Boolean = false,
): SwingModifier.NodeElement<T, PropertyNode<T, V>> =
    PropertyElement(T::class.java, accessors, value, interference, inheritable)

/**
 * Builds a [propertyElement] for a property the component works out again for itself - a button's
 * opaque flag following its fill, an alignment a layout derives from the children standing then, an
 * accessible name a context falls back to the widget's own text for. The accessors' read answers `null`
 * where the component holds none of its own, and removing the declaration writes that `null`, which
 * resolves to whatever derives the property at that moment.
 *
 * The fill, the children or the text the property is worked out from may have moved since attach, so
 * the element [restores][SwingModifier.NodeElement.restores] [RestorePolicy.None]: nothing comparing the
 * value read at attach with the one standing now can work the derivation out.
 */
internal inline fun <reified T : Component, V> derivedPropertyElement(
    accessors: PropertyAccessors<T, V>,
    value: V,
    interference: PropertyInterference<T>? = null,
    inheritable: Boolean = false,
): SwingModifier.NodeElement<T, PropertyNode<T, V>> =
    UnrestoredPropertyElement(T::class.java, accessors, value, interference, inheritable)

/**
 * A [PropertyElement] that puts nothing back when it leaves, for a property whose value at attach says
 * nothing about what it should be given back: one the component works out again for itself.
 */
internal class UnrestoredPropertyElement<T : Component, V>(
    targetType: Class<T>,
    accessors: PropertyAccessors<T, V>,
    value: V,
    interference: PropertyInterference<T>? = null,
    inheritable: Boolean = false,
) : PropertyElement<T, V>(targetType, accessors, value, interference, inheritable) {
    override val restores: RestorePolicy get() = RestorePolicy.None
}

/**
 * Two property writes reaching each other, and what the slot does about it. A property nothing else
 * writes, and whose write touches nothing else, names none of these.
 *
 * The two are duals: [OverwrittenOn] is another write landing on the property this slot declares, and
 * [AlsoOverwrites] is this slot's write landing on a property it does not declare.
 */
internal sealed interface PropertyInterference<T : Component> {
    /**
     * The component works the declared property out again whenever [announcedBy] changes - the property
     * itself, where the component recomputes it, or one it derives the property from. The slot listens
     * for that announcement and writes the declared value again on each one.
     *
     * The property the write itself lands on may be named only where re-asserting the declared value
     * stops the announcements: a component announces no change between two equal primitives, but does
     * announce one between two nulls.
     *
     * @property announcedBy the bean property whose change announcement the slot listens for.
     */
    class OverwrittenOn<T : Component>(
        val announcedBy: String,
    ) : PropertyInterference<T>

    /**
     * This slot's write lands on [properties] as well as on the one it declares, because the setter
     * writes them too. A coarse geometry names each axis it covers, and filling a button's content
     * area names the opaque flag its setter keeps in step with it.
     *
     * A property a look and feel works out from the write is not one of these. What one look and feel
     * derives is not what another does, so no list of names can be complete; an element whose write may
     * provoke a derivation [restores][SwingModifier.NodeElement.restores]
     * [RestorePolicy.DeclaredPropertyOnly] instead.
     */
    class AlsoOverwrites<T : Component, S>(
        private vararg val properties: PropertyAccessors<T, S>,
    ) : PropertyInterference<T> {
        /**
         * Takes a hold on each of [properties], capturing the ones this slot is the first of the modifier
         * to write. The accessors are the ones the builder declaring each property uses, so the two
         * meet on one capture rather than taking one each.
         */
        fun hold(
            captures: PropertyCaptures,
            component: T,
        ): List<PropertyCaptures.Hold> =
            properties.map { captures.hold(it.write.javaClass, component, it.read, it.write) }

        /** What each of [properties] is named. */
        fun names(): Set<String> = properties.mapTo(LinkedHashSet()) { it.name }
    }
}

/**
 * One property's own accessors, allocated once in a top-level value so every element writing that property
 * names the same pair: the builder declaring it, and each [PropertyInterference.AlsoOverwrites] whose write
 * lands on it. A pair held once is what makes them meet on one [PropertyCaptures] hold rather than take
 * one each, and what makes two declarations of one value compare equal.
 *
 * The target is contravariant: a pair written against the class that declares the property serves every
 * widget built on it, so a slot targeting a narrower widget can name that same property.
 */
internal class PropertyAccessors<in T : Component, V>(
    val name: String,
    val read: (component: T) -> V,
    val write: (component: T, value: V) -> Unit,
)

/**
 * What each property a modifier writes stood at before any slot of that modifier wrote it, held once for
 * every slot that writes it: the slot declaring it, and each slot whose own write lands on it besides
 * the property that slot declares.
 *
 * The first of those slots to attach captures the value. Capturing per slot instead would read whatever
 * a sibling had already written wherever a slot joins the modifier on a later pass, and put that back as if
 * it were the value the component came with.
 *
 * A property is named the way its slot is keyed, and a [PropertyInterference.AlsoOverwrites] names the
 * second property by the class of the write accessor it holds - the same name the builder declaring
 * that property is keyed under, since the two hold one accessor. Held on the modifier's own state, so it
 * covers one component and lives as long as the modifier applied to it.
 */
internal class PropertyCaptures {
    private val held = HashMap<Any, Hold>()

    /**
     * Takes a hold on the property [read] and [write] name, capturing what it stands at where nothing
     * holds it yet.
     *
     * [name] is what tells one property from another: two slots writing the same property pass the same
     * value and share one hold.
     */
    fun <T : Component, V> hold(
        name: Any,
        component: T,
        read: (component: T) -> V,
        write: (component: T, value: V) -> Unit,
    ): Hold {
        held[name]?.let { standing ->
            standing.share()
            return standing
        }
        val captured = read(component)
        return Hold(name) { write(component, captured) }.also { held[name] = it }
    }

    /** What one captured property stands at before the modifier wrote it, and the slots holding it there. */
    internal inner class Hold(
        private val name: Any,
        private val restore: () -> Unit,
    ) {
        private var users: Int = 1

        /** Takes one more slot onto this hold, for a slot whose write lands on a property already held. */
        fun share() {
            users++
        }

        /** Gives up this hold and puts the property back where it stood before the modifier wrote it. */
        fun release() {
            // Every slot that leaves puts the property back: a declaration that goes has to hand it
            // over whether or not a slot naming it in passing still stands. The value is the same one
            // each time, so the last of them leaves the property where the modifier found it. The record
            // goes with the last hold, so a declaration made again later captures afresh.
            if (--users == 0) held.remove(name)
            restore()
        }
    }
}
