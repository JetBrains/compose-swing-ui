package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.util.IdentityHashMap

/**
 * Holds every modifier write to putting back what it overwrote: once every slot that wrote a property
 * has left, the property stands where the component's modifier found it.
 *
 * What a write lands on is learned by reading the component around it, never by naming properties: a
 * write reaches more than the property its slot declares - a coarse geometry covers each axis, a look
 * and feel answering a setter re-derives a font - and a check naming what it expects would be blind to
 * exactly the property a new builder forgets to name. Only the properties a declaration names for
 * itself - its [SwingModifier.NodeElement.heldProperties] - are taken on its word. The properties read
 * are the ones [comparedPropertiesOf] names.
 *
 * [report] is handed one failure per departing slot, naming every property that slot left where the
 * modifier did not find it.
 *
 * Each slot is held to what its element's [SwingModifier.NodeElement.restores] undertakes.
 * [RestorePolicy.None] answers for nothing of its own on the way out, and
 * [RestorePolicy.DeclaredPropertyOnly] for the one property the element declares. What such an element
 * writes is recorded like any other write, so a property it goes on declaring is not owed by a slot
 * leaving beside it.
 */
internal class ModifierRestoreCheck(
    private val report: (Throwable) -> Unit,
) : SwingCompositionDiagnostics {
    private val watched = IdentityHashMap<Component, WatchedComponent>()

    override fun declaring(
        component: Component,
        node: SwingModifier.ComponentNode<*>,
        element: SwingModifier.NodeElement<*, *>,
        write: () -> Unit,
    ) {
        watched
            .getOrPut(component) { WatchedComponent(component, report) }
            .wrote(node, element, write)
    }

    override fun restoring(
        component: Component,
        node: SwingModifier.ComponentNode<*>,
        restore: () -> Unit,
    ) {
        restore()
        val watch = watched[component] ?: return
        watch.left(node)?.let(report)
        if (watch.holdsNothing) watched.remove(component)
    }
}

/**
 * One component's watched properties: what each stood at before any live slot wrote it, and which of
 * them each live slot's writes landed on.
 *
 * The record of a property is taken by the first slot whose write lands on it and dropped when the last
 * of the slots that wrote it leaves, which is how the modifier itself holds the value it puts back. A
 * slot joining a modifier on a later pass is therefore answered against the value the modifier found,
 * not against whatever a sibling had already written.
 */
private class WatchedComponent(
    private val component: Component,
    private val report: (Throwable) -> Unit,
) {
    private val properties = comparedPropertiesOf(component).associateBy { it.name }

    /** What each watched property stood at before the first of the slots writing it wrote. */
    private val found = HashMap<String, Any?>()

    /** The live slots, each under the properties its writes have landed on. */
    private val slots = IdentityHashMap<SwingModifier.ComponentNode<*>, WritingSlot>()

    /** Whether no slot of this component's modifier writes any property, so nothing is watched here. */
    val holdsNothing: Boolean get() = slots.isEmpty()

    /**
     * Runs one slot's [write], and records the properties it lands on.
     *
     * Every property [element] holds is recorded whether the write moved it or not: a declaration may
     * write the value already standing, leaving nothing for a read to see, and it goes on holding the
     * property while it stands. Every other property is the write's to reveal.
     *
     * The write runs whether or not the reads around it answer. A slot whose read before the write
     * failed holds no property, so the read after it is skipped and one declaring call reports one
     * failure however often the caller's code throws.
     */
    fun wrote(
        node: SwingModifier.ComponentNode<*>,
        element: SwingModifier.NodeElement<*, *>,
        write: () -> Unit,
    ) {
        val before = read()
        write()
        val slot = slots.getOrPut(node) { WritingSlot(element) }
        if (before == null) return
        val after = read() ?: return
        val held = element.heldProperties
        for ((property, value) in after) {
            if (property !in held && value == before[property]) continue
            if (property !in found) found[property] = before[property]
            slot.wrote += property
        }
    }

    /**
     * Records that the slot [node] holds has left, its restore already run, and answers what it left
     * standing away from where the modifier found it - or `null` where it left nothing.
     *
     * Only a property no live slot writes any more is answered for. What stands in a property another
     * slot still writes is that slot's declaration, and the value the modifier found is owed only once
     * the last of them has left.
     */
    fun left(node: SwingModifier.ComponentNode<*>): AssertionError? {
        val slot = slots.remove(node) ?: return null
        val element = slot.element
        val last = slot.wrote.filter { property -> slots.values.none { property in it.wrote } }
        val owed =
            when {
                element.restores == RestorePolicy.None -> emptyList()
                element.restores == RestorePolicy.DeclaredPropertyOnly -> last.filter { it == element.name }
                else -> last
            }
        val standing = if (owed.isEmpty()) emptyMap() else read()
        val wrong =
            standing
                ?.let { values ->
                    owed.filter { values[it] != found[it] }.map { "  $it: found <${found[it]}>, left <${values[it]}>" }
                }.orEmpty()
        last.forEach(found::remove)
        return if (wrong.isEmpty()) {
            null
        } else {
            AssertionError(
                "Modifier restore check: the departing ${element.name} declaration leaves a " +
                    "${component.javaClass.name} where its modifier did not find it.\n" + wrong.joinToString("\n"),
            )
        }
    }

    /**
     * What every watched property of [component] stands at, or `null` where reading one threw.
     *
     * Reading runs the caller's own code - the lambda a table cell's value comes from among it. A read
     * that threw is reported the way a violation is, and the pass goes on.
     *
     * Every throwable is caught: what that code throws is the caller's choice, and naming only some
     * types would leave the rest free to end the composition.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun read(): Map<String, Any?>? =
        try {
            properties.mapValues { (_, property) -> property.read(component) }
        } catch (failure: Throwable) {
            report(
                AssertionError(
                    "Modifier restore check: reading a ${component.javaClass.name} to see what its " +
                        "declarations write threw.",
                    failure,
                ),
            )
            null
        }
}

/**
 * One live slot: the element occupying it, which says what it owes back when it leaves, and the
 * properties its writes have landed on.
 */
private class WritingSlot(
    val element: SwingModifier.NodeElement<*, *>,
) {
    val wrote: MutableSet<String> = LinkedHashSet()
}
