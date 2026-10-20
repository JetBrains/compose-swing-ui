package org.jetbrains.compose.swing.modifier

import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/**
 * A [SwingModifier.ComponentNode] for a single component property. On [onAttach] it captures the property's
 * pre-modifier value; on each apply it writes the latest value; on [onDetach] it
 * restores the captured value. [read] reads the current value (for capture) and [write] applies a value.
 *
 * This is the shape every appearance/layout/metadata/accessibility property element shares: capture
 * once, write the new value, restore on removal. The typed capture retains the original
 * value without casting it back through erasure. [SwingModifier.property] builds one of
 * these per declaration; a client property keyed by something other than a [ComponentPropertyDescriptor] (see
 * `clientProperty`) builds one directly, with no [rewriteOn] or [alsoOverwrites].
 *
 * For a property a component inherits from its parent when it declares none of its own - cursor, font,
 * background, foreground - [read] must return the component's *own* value, and `null` where the matching
 * `isXSet` is false.
 *
 * [rewriteOn] names a bean property whose announced change means the declared value may have been
 * overwritten; the node listens for it and writes the declared value again, unless a declaration applied
 * after this one made the change (see [PropertyRewrites]). [alsoOverwrites] names
 * properties this [write] lands on besides the one this node declares; each is captured beside the declared
 * one and put back after it, so its restore is the last write to reach it. Without that, a removal
 * leaves it wherever the declared property's restore left it, with no declaration naming it.
 *
 * Both are captured through the modifier's [PropertyCaptures], under [name] and under each overwritten
 * property's own [ComponentPropertyDescriptor.name] - which is what lets two different
 * [ComponentPropertyDescriptor] handles of one name, declared through two different slots, share one capture.
 */
internal class PropertyNode<T : Component, V>(
    private val name: Any,
    private var read: (component: T) -> V,
    private var write: (component: T, value: V) -> Unit,
    private val rewriteOn: String? = null,
    private val alsoOverwrites: List<ComponentPropertyDescriptor<T, *>> = emptyList(),
) : SwingModifier.ComponentNode<T>(),
    PropertyChangeListener {
    /** This declaration's shared original value. */
    private var capture: PropertyCaptures.Capture<*, *>? = null

    /** This slot's captures of the properties [write] overwrites besides the one it declares. */
    private var overwrittenCaptures: Array<PropertyCaptures.Capture<*, *>?>? = null

    /** Write ordering shared by this component's property declarations, when [rewriteOn] is set. */
    private var rewrites: PropertyRewrites? = null

    /** This declaration's order in its most recent apply. Zero means it has not been applied. */
    private var place = 0L

    /** Repeats the most recent apply with its original value and declaration order. */
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
        capture = captures.capture(name, component, read, write)
        overwrittenCaptures =
            if (alsoOverwrites.isEmpty()) {
                null
            } else {
                Array(alsoOverwrites.size) { alsoOverwrites[it].holdCapture(captures, component) }
            }
        rewriteOn?.let {
            rewrites = state.rewrites()
            place = 0L
            reapply = null
            component.addPropertyChangeListener(it, this)
        }
    }

    /** Writes [value]; call from the owning element's `update` with its latest data. */
    fun apply(value: V) {
        val component = component
        val rewrites = rewrites
        if (rewrites == null) {
            write(component, value)
            return
        }
        place = rewrites.next()
        val written = { rewrites.write(place) { write(component, value) } }
        reapply = written
        written()
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        val rewrites = rewrites ?: return
        if (rewrites.answers(place)) reapply?.invoke()
    }

    override fun onDetach() {
        rewriteOn?.let { component.removePropertyChangeListener(it, this) }
        capture?.release()
        overwrittenCaptures?.forEach { it?.release() }
    }
}

/**
 * What each property a modifier writes stood at before any slot of that modifier wrote it, captured once for
 * every slot that writes it: the slot declaring it, and each slot whose own write lands on it besides
 * the property that slot declares.
 *
 * The first of those slots to attach captures the value. Capturing per slot instead would read whatever
 * a sibling had already written wherever a slot joins the modifier on a later pass, and put that back as if
 * it were the value the component came with.
 *
 * A property is named the way its slot is keyed - a [ComponentPropertyDescriptor]'s
 * [ComponentPropertyDescriptor.name] for a declaration made through [SwingModifier.property], and a client property's
 * own key for one made through `clientProperty`. An [SwingModifier.property] `alsoOverwrites` entry is captured under
 * the second
 * property's own name too, the same name the handle declaring that property is keyed under, since a capture
 * is named the way the property it captures is, whichever slot asks for it first. Held on the modifier's
 * own state, so it covers one component and lives as long as the modifier applied to it.
 */
internal class PropertyCaptures {
    /** Captures shared by property key across all slots of this component. */
    private var held: Array<Capture<*, *>?>? = null

    /** The occupied prefix of [held]. */
    private var size: Int = 0

    /** Captures on first use, otherwise shares the existing capture without reading again. */
    fun <T : Component, V> capture(
        name: Any,
        component: T,
        read: (component: T) -> V,
        write: (component: T, value: V) -> Unit,
    ): Capture<*, *> {
        for (index in 0 until size) {
            val standing = checkNotNull(held?.get(index))
            if (standing.name == name) {
                standing.share()
                return standing
            }
        }
        val original = read(component)
        val records = held
        if (records == null) {
            held = arrayOfNulls(1)
        } else if (size == records.size) {
            held = records.copyOf(size * 2)
        }
        return Capture(this, name, component, original, write).also { checkNotNull(held)[size++] = it }
    }

    /** Removes a capture before its final restore can capture the property again. */
    private fun remove(capture: Capture<*, *>) {
        val held = checkNotNull(held)
        for (index in 0 until size) {
            if (held[index] === capture) {
                held.copyInto(held, index, index + 1, size)
                held[--size] = null
                return
            }
        }
    }

    /** Retains a typed original value and the accessor that first captured it. */
    internal class Capture<T : Component, V>(
        /** The captures that remove this capture on its final release. */
        private val captures: PropertyCaptures,
        /** The shared property identity. */
        val name: Any,
        /** The component captured on first use. */
        private val component: T,
        /** The original value, including a legitimate null. */
        private val original: V,
        /** The writer belonging to the first capture. */
        private val write: (T, V) -> Unit,
    ) {
        /** The number of declarations still sharing this original value. */
        private var users: Int = 1

        /** Adds another declaration without recapturing the property. */
        fun share() {
            users++
        }

        /** Restores on every release and removes this capture before the final restore. */
        fun release() {
            if (--users == 0) captures.remove(this)
            write(component, original)
        }
    }
}

/**
 * Tracks the application order of one component's `rewriteOn` declarations.
 *
 * During a write and any nested writes, only declarations applied after the latest declaration that
 * wrote may reapply their values. This order remains in effect until all nested writes return because
 * the component may deliver an earlier write's event after a later write returns. All declarations
 * may reapply their values for changes made outside these writes.
 */
internal class PropertyRewrites {
    /** The order assigned to the most recent apply. */
    private var last = 0L

    /** The number of active nested writes. */
    private var depth = 0

    /** The greatest declaration order written during active writes, or zero when no write is active. */
    private var latest = 0L

    /** Assigns the next order to a declaration being applied. */
    fun next(): Long = ++last

    /**
     * Returns whether the declaration at [place] may reapply its value for the current change.
     * External changes qualify. During active writes, the declaration must have been applied after
     * the latest declaration that wrote; that declaration and earlier declarations do not qualify.
     */
    fun answers(place: Long): Boolean = latest < place

    /**
     * Runs [write] for the declaration at [place]. This declaration and earlier declarations cannot
     * reapply their values for synchronous change events. Later declarations can reapply their values.
     * The greatest order written remains in effect until this write and all enclosing writes return,
     * including when a write throws.
     */
    fun write(
        place: Long,
        write: () -> Unit,
    ) {
        depth++
        if (place > latest) latest = place
        try {
            write()
        } finally {
            if (--depth == 0) latest = 0L
        }
    }
}
