package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.core.watchRestore
import org.jetbrains.compose.swing.core.watchWrite
import org.jetbrains.compose.swing.modifier.ModifierInfo
import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.PropertyElement
import org.jetbrains.compose.swing.modifier.PropertyRewrites
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.WriteMode
import org.jetbrains.compose.swing.modifier.reassertPropertySlotsFrom
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

/** Retains descriptor declarations in their current write order. */
internal class StyleableProperties {
    private var entries: Array<PropertyEntry?>? = null
    private var size = 0

    fun resolve(
        incoming: List<PropertyElement<*, *>>,
        mode: WriteMode,
        style: StyleNode,
    ): Boolean {
        var written = detachDeparted(incoming, style)
        if (reconcileCaptures(incoming)) written = true
        var rewriting = written || mode.rewritesEqual
        for (i in 0 until incoming.size) {
            val element = incoming[i]
            val found = findEntry(i, element)
            val entry = if (found == size) PropertyEntry(style, element) else checkNotNull(entries?.get(found))
            if (placeEntry(entry, i, found)) rewriting = true
            val writes = rewriting || mode.writes(entry.element, element)
            val registrationChanged = entry.rebind(element)
            if (!entry.attached || writes || registrationChanged) {
                if (entry.attached) entry.write() else entry.attach()
                written = true
                rewriting = true
            }
        }
        return written
    }

    private fun findEntry(
        from: Int,
        element: PropertyElement<*, *>,
    ): Int {
        var found = from
        while (found < size && entries?.get(found)?.element?.key != element.key) found++
        return found
    }

    private fun placeEntry(
        entry: PropertyEntry,
        index: Int,
        found: Int,
    ): Boolean {
        val previous = entries
        if (found == size) {
            if (previous == null) {
                entries = arrayOfNulls(1)
            } else if (size == previous.size) {
                entries = previous.copyOf(size * 2)
            }
        }
        val entries = checkNotNull(entries)
        val moved = found != index || found == size
        if (found == size) {
            entries.copyInto(entries, index + 1, index, size)
            size++
        } else if (found != index) {
            entries.copyInto(entries, index + 1, index, found)
        }
        entries[index] = entry
        return moved
    }

    private fun reconcileCaptures(incoming: List<PropertyElement<*, *>>): Boolean {
        var released = false
        for (index in 0 until incoming.size) {
            val element = incoming[index]
            // An unchanged restyle keeps each entry at its index, so the keyed entry is found without a search.
            val standing = entries
            val inPlace = index < size && checkNotNull(standing?.get(index)).element.key == element.key
            val found = if (inPlace) index else findEntry(0, element)
            val entry = if (found < size) standing?.get(found) else null
            if (entry != null && entry.reconcileCaptures(element)) released = true
        }
        return released
    }

    private fun detachDeparted(
        incoming: List<PropertyElement<*, *>>,
        style: StyleNode,
    ): Boolean {
        val entries = entries ?: return false
        var released: Set<String>? = null
        var failure: Throwable? = null
        try {
            for (i in size - 1 downTo 0) {
                val entry = checkNotNull(entries[i])
                // An unchanged restyle keeps each entry at its index, so the same-position key skips the search.
                val key = entry.element.key
                if ((i < incoming.size && incoming[i].key == key) || incoming.any { it.key == key }) continue
                released = released?.plus(entry.element.heldProperties) ?: entry.element.heldProperties
                failure = collectFailure(failure) { entry.detach() }
                entries.copyInto(entries, i, i + 1, size)
                entries[--size] = null
            }
        } finally {
            released?.let { names -> failure = collectFailure(failure) { style.reassertReleased(names) } }
        }
        failure?.let { throw it }
        return released != null
    }

    /**
     * Writes again, in declaration order, every attached entry from the first one holding a released property name,
     * so a later entry keeps its precedence over an earlier one it overlaps.
     */
    fun rewriteReleased(names: Set<String>) {
        val entries = entries ?: return
        var rewriting = false
        for (i in 0 until size) {
            val entry = checkNotNull(entries[i])
            if (!rewriting) rewriting = entry.element.heldProperties.any { it in names }
            if (rewriting && entry.attached) entry.write()
        }
    }

    fun refresh(mode: WriteMode) {
        val entries = entries ?: return
        if (mode.rewritesEqual) for (i in 0 until size) entries[i]?.takeIf { it.attached }?.write()
    }

    fun detach() {
        val entries = entries ?: return
        var failure: Throwable? = null
        for (i in size - 1 downTo 0) failure = collectFailure(failure) { entries[i]?.detach() }
        failure?.let { throw it }
    }
}

/** Holds a style declaration's original values until it leaves the component. */
internal class PropertyEntry(
    private val owner: StyleNode,
    element: PropertyElement<*, *>,
) : PropertyChangeListener,
    ModifierInfo {
    var element: PropertyElement<*, *> = element
        private set
    override val component: Component get() = owner.component
    override val modifier: SwingModifier.NodeElement<*, *> get() = element
    private var capture: PropertyCaptures.Capture<*, *>? = null
    private var overwritten: Array<PropertyCaptures.Capture<*, *>?>? = null
    private val hasCaptures: Boolean get() = capture != null || overwritten != null
    var attached = false
        private set
    private var rewrites: PropertyRewrites? = null
    private var registeredEvent: String? = null
    private var sequence = 0L

    fun rebind(element: PropertyElement<*, *>): Boolean {
        this.element = element
        return attached && updateRegistration(element.rewriteOn)
    }

    fun reconcileCaptures(element: PropertyElement<*, *>): Boolean {
        val released = if (attached && !declaresSameCaptures(element)) reconcileOverwritten(element) else null
        if (released == null) return false
        var failure: Throwable? = null
        val names = mutableSetOf<String>()
        for (index in released.indices) {
            val held = released[index]
            for (previousIndex in 0 until this.element.overwrittenCount) {
                val name = this.element.overwrittenName(previousIndex)
                if (name == held.name) names.add(name)
            }
            failure = collectFailure(failure) { held.release() }
        }
        failure = collectFailure(failure) { owner.reassertReleased(names) }
        failure?.let { throw it }
        return true
    }

    private fun declaresSameCaptures(element: PropertyElement<*, *>): Boolean {
        val held = this.element
        var index = 0
        while (index < held.overwrittenCount && index < element.overwrittenCount) {
            if (held.overwrittenName(index) != element.overwrittenName(index)) break
            index++
        }
        return index == held.overwrittenCount && index == element.overwrittenCount
    }

    private fun reconcileOverwritten(element: PropertyElement<*, *>): List<PropertyCaptures.Capture<*, *>>? {
        val previous = overwritten
        val retainedCount = retainedCount(previous, element)
        val addedCount = addedCount(previous, element)
        if (retainedCount == (previous?.size ?: 0) && addedCount == 0) return null
        val additional =
            if (retainedCount + addedCount == 0) {
                null
            } else {
                arrayOfNulls<PropertyCaptures.Capture<*, *>>(retainedCount + addedCount)
            }
        var size = 0
        var released: List<PropertyCaptures.Capture<*, *>>? = null
        if (previous != null) {
            for (index in previous.indices) {
                val held = previous[index] ?: continue
                if (retains(element, held)) {
                    checkNotNull(additional)[size++] = held
                } else {
                    released = released.orEmpty() + held
                }
            }
        }
        captureAdditional(element, previous, additional, size)
        overwritten = additional
        return released
    }

    private fun captureAdditional(
        element: PropertyElement<*, *>,
        previous: Array<PropertyCaptures.Capture<*, *>?>?,
        additional: Array<PropertyCaptures.Capture<*, *>?>?,
        retainedCount: Int,
    ) {
        val holder = checkNotNull(owner.holder)
        var size = retainedCount
        val failure =
            collectFailure(null) {
                for (index in 0 until element.overwrittenCount) {
                    if (!hasCapture(previous, element.overwrittenName(index))) {
                        checkNotNull(additional)[size++] =
                            element.captureOverwritten(
                                index,
                                holder.component,
                                checkNotNull(holder.modifierState).captures(),
                            )
                    }
                }
            }
        if (failure != null) {
            for (index in retainedCount until size) collectFailure(failure) { additional?.get(index)?.release() }
            throw failure
        }
    }

    private fun updateRegistration(name: String?): Boolean {
        if (registeredEvent == name) return false
        val holder = checkNotNull(owner.holder)
        registeredEvent?.let { holder.component.removePropertyChangeListener(it, this) }
        registeredEvent = null
        rewrites = null
        sequence = 0L
        name?.let {
            rewrites = checkNotNull(holder.modifierState).rewrites()
            holder.component.addPropertyChangeListener(it, this)
            registeredEvent = it
        }
        return true
    }

    fun attach() {
        val holder = checkNotNull(owner.holder)
        val component = holder.component
        val state = checkNotNull(holder.modifierState)
        val failure =
            collectFailure(null) {
                capture = element.capture(component, state.captures())
                if (element.overwrittenCount != 0) {
                    val additional = arrayOfNulls<PropertyCaptures.Capture<*, *>>(element.overwrittenCount)
                    overwritten = additional
                    for (index in additional.indices) {
                        additional[index] = element.captureOverwritten(index, component, state.captures())
                    }
                }
                updateRegistration(element.rewriteOn)
                attached = true
            }
        if (failure != null) {
            collectFailure(failure) { detach() }
            throw failure
        }
        write()
    }

    fun write() {
        val holder = checkNotNull(owner.holder)
        holder.owner?.diagnostics.watchWrite(this) {
            val ordering = rewrites
            if (ordering == null) {
                element.writeTo(holder.component)
            } else {
                sequence = ordering.next()
                writeOrdered(ordering, holder.component)
            }
        }
    }

    /** Writes the element at [sequence] in [ordering]; a synchronous change event answers only a later declaration. */
    private fun writeOrdered(
        ordering: PropertyRewrites,
        component: Component,
    ) {
        ordering.enter(sequence)
        try {
            element.writeTo(component)
        } finally {
            ordering.exit()
        }
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        val ordering = rewrites ?: return
        if (ordering.mayReapply(sequence)) {
            val holder = checkNotNull(owner.holder)
            writeOrdered(ordering, holder.component)
        }
    }

    fun detach() {
        if (!attached && !hasCaptures && rewrites == null) return
        val holder = checkNotNull(owner.holder)
        attached = false
        var failure: Throwable? = null
        val name = registeredEvent
        registeredEvent = null
        rewrites = null
        if (name != null) {
            failure = collectFailure(failure) { holder.component.removePropertyChangeListener(name, this) }
        }
        holder.owner?.diagnostics.watchRestore(this) {
            val primary = capture
            capture = null
            failure = collectFailure(failure) { primary?.release() }
            val additional = overwritten
            overwritten = null
            additional?.forEach { held -> failure = collectFailure(failure) { held?.release() } }
        }
        failure?.let { throw it }
    }

    private companion object {
        private fun retains(
            element: PropertyElement<*, *>,
            held: PropertyCaptures.Capture<*, *>?,
        ): Boolean {
            var index = 0
            while (held != null && index < element.overwrittenCount) {
                if (element.overwrittenName(index++) == held.name) return true
            }
            return false
        }

        private fun hasCapture(
            previous: Array<PropertyCaptures.Capture<*, *>?>?,
            name: String,
        ): Boolean {
            var index = 0
            while (previous != null && index < previous.size) {
                if (previous[index++]?.name == name) return true
            }
            return false
        }

        private fun retainedCount(
            previous: Array<PropertyCaptures.Capture<*, *>?>?,
            element: PropertyElement<*, *>,
        ): Int {
            var count = 0
            if (previous != null) {
                for (index in previous.indices) if (retains(element, previous[index])) count++
            }
            return count
        }

        private fun addedCount(
            previous: Array<PropertyCaptures.Capture<*, *>?>?,
            element: PropertyElement<*, *>,
        ): Int {
            var count = 0
            for (index in 0 until element.overwrittenCount) {
                if (!hasCapture(previous, element.overwrittenName(index))) count++
            }
            return count
        }
    }
}

/** Reasserts modifier declarations whose properties a departing style declaration restored. */
private fun StyleNode.reassertReleased(names: Set<String>) {
    holder?.modifierState?.reassertPropertySlotsFrom(
        component,
        holder?.owner?.diagnostics,
        except = this,
    ) { it.element.affectsProperties(names) }
}
