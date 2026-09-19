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
 * value, so no value is stored or cast back through erasure. [SwingModifier.property] builds one of
 * these per declaration; a client property keyed by something other than a [ComponentPropertyDescriptor] (see
 * `clientProperty`) builds one directly, with no [rewriteOn] or [alsoOverwrites].
 *
 * For a property a component inherits from its parent when it declares none of its own - cursor, font,
 * background, foreground - [read] must return the component's *own* value, and `null` where the matching
 * `isXSet` is false.
 *
 * [rewriteOn] names a bean property whose announced change means the declared value may have been
 * overwritten; the node listens for it and writes the declared value again. [alsoOverwrites] names
 * properties this [write] lands on besides the one this node declares; each is held beside the declared
 * one and put back after it, so its restore is the last write to reach it. Without that, a removal
 * leaves it wherever the declared property's restore left it, with no declaration naming it.
 *
 * Both are held through the modifier's [PropertyCaptures], under [slot] and under each overwritten
 * property's own [ComponentPropertyDescriptor.name] - which is what lets two different
 * [ComponentPropertyDescriptor] handles of one name, declared through two different slots, share one hold.
 */
internal class PropertyNode<T : Component, V>(
    private val slot: Any,
    private var read: (component: T) -> V,
    private var write: (component: T, value: V) -> Unit,
    private val rewriteOn: String? = null,
    private val alsoOverwrites: List<ComponentPropertyDescriptor<T, *>> = emptyList(),
) : SwingModifier.ComponentNode<T>(),
    PropertyChangeListener {
    private var declared: PropertyCaptures.Hold? = null

    /** This slot's holds on the properties [write] overwrites besides the one it declares. */
    private var overwritten: List<PropertyCaptures.Hold> = emptyList()

    /** Writes the value applied last, held as a closure over it. */
    private var reapply: (() -> Unit)? = null

    fun rebind(
        read: (component: T) -> V,
        write: (component: T, value: V) -> Unit,
    ) {
        this.read = read
        this.write = write
    }

    override fun onAttach() {
        val component = component
        // A node built outside the modifier machinery holds no state, which this refuses rather than
        // capturing nothing.
        val state = checkNotNull(holder?.modifierState) { "A property node is attached by the modifier it belongs to" }
        val captures = state.captures()
        declared = captures.hold(slot, component, read, write)
        overwritten = alsoOverwrites.map { it.holdCapture(captures, component) }
        rewriteOn?.let { component.addPropertyChangeListener(it, this) }
    }

    /** Writes [value]; call from the owning element's `update` with its latest data. */
    fun apply(value: V) {
        val component = component
        if (rewriteOn != null) reapply = { write(component, value) }
        write(component, value)
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        reapply?.invoke()
    }

    override fun onDetach() {
        rewriteOn?.let { component.removePropertyChangeListener(it, this) }
        declared?.release()
        overwritten.forEach { it.release() }
    }
}

/**
 * What each property a modifier writes stood at before any slot of that modifier wrote it, held once for
 * every slot that writes it: the slot declaring it, and each slot whose own write lands on it besides
 * the property that slot declares.
 *
 * The first of those slots to attach captures the value. Capturing per slot instead would read whatever
 * a sibling had already written wherever a slot joins the modifier on a later pass, and put that back as if
 * it were the value the component came with.
 *
 * A property is named the way its slot is keyed - a [ComponentPropertyDescriptor]'s
 * [ComponentPropertyDescriptor.name] for a declaration made through [SwingModifier.property], and a client property's
 * own key for one made through `clientProperty`. An [SwingModifier.property] `alsoOverwrites` entry is held under
 * the second
 * property's own name too, the same name the handle declaring that property is keyed under, since a hold
 * is named the way the property it captures is, whichever slot asks for it first. Held on the modifier's
 * own state, so it covers one component and lives as long as the modifier applied to it.
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
