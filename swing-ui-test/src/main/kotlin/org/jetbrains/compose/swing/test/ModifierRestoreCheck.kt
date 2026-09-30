// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.modifier.ModifierInfo
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.util.IdentityHashMap

/**
 * Holds every modifier write to putting back what it overwrote: once every declaration that wrote a property
 * has left, the property is where the component's modifier found it.
 *
 * What a write lands on is learned by reading the component around it, never by naming properties: a
 * write reaches more than the property its declaration names - a coarse geometry covers each axis, a look
 * and feel answering a setter re-derives a font - and a check naming what it expects would be blind to
 * exactly the property a new builder forgets to name. Only the properties a declaration names for
 * itself - its [SwingModifier.NodeElement.heldProperties] - are taken on its word. The properties read
 * are the ones [comparedPropertiesOf] names.
 *
 * [report] is handed one failure per departing declaration, naming every property that declaration left where
 * the modifier did not find it.
 *
 * Each declaration is held to what its element's [SwingModifier.NodeElement.restores] undertakes.
 * [RestorePolicy.None] is owed nothing of its own on the way out, and
 * [RestorePolicy.DeclaredPropertyOnly] only the one property the element declares. What such an element
 * writes is recorded like any other write, so a property it goes on declaring is not owed by a declaration
 * leaving beside it.
 *
 * Only the insets can move with no modifier writing them and still be owed by no declaration: a component's
 * paint outsets derive them. They are read again right before each write or restore on the component,
 * and insets found away from where the last of them left them are owed by no departing declaration until
 * their record is dropped. The cost is that a missed restore of the insets then goes unreported.
 */
internal class ModifierRestoreCheck(
    private val report: (Throwable) -> Unit,
) : SwingCompositionDiagnostics {
    private val watched = IdentityHashMap<Component, WatchedComponent>()

    override fun declaring(
        info: ModifierInfo,
        write: () -> Unit,
    ) {
        val component = info.component
        watched
            .getOrPut(component) { WatchedComponent(component, report) }
            .wrote(info, info.modifier, write)
    }

    override fun restoring(
        info: ModifierInfo,
        restore: () -> Unit,
    ) {
        val component = info.component
        val watch = watched[component] ?: return restore()
        watch.left(info, restore)?.let(report)
        if (watch.holdsNothing) watched.remove(component)
    }
}

/**
 * One component's watched properties: what each held before any installed declaration wrote it, and which of
 * them each installed declaration's writes landed on.
 *
 * The record of a property is taken by the first declaration whose write lands on it and dropped when the last
 * of the declarations that wrote it leaves, which is how the modifier itself holds the value it puts back. A
 * declaration joining a modifier on a later pass is therefore checked against the value the modifier found,
 * not against whatever a sibling had already written.
 */
private class WatchedComponent(
    private val component: Component,
    private val report: (Throwable) -> Unit,
) {
    private val properties = comparedPropertiesOf(component).associateBy { it.name }

    /** What each watched property held before the first of the declarations writing it wrote. */
    private val found = HashMap<String, Any?>()

    /**
     * The recorded insets, where something other than a modifier write moved them; no departing declaration owes
     * them.
     */
    private val movedElsewhere = HashSet<String>()

    /** What every watched property held after the last write or restore, or `null` where that read failed. */
    private var seen: Map<String, Any?>? = null

    /** The installed declarations, each under the properties its writes have landed on. */
    private val slots = IdentityHashMap<ModifierInfo, MutableSet<String>>()

    /** Whether no declaration of this component's modifier writes any property, so nothing is watched here. */
    val holdsNothing: Boolean get() = slots.isEmpty()

    /**
     * Runs one declaration's [write], and records the properties it lands on.
     *
     * Every property [element] holds is recorded whether the write moved it or not: a declaration may
     * write the value the property already holds, leaving nothing for a read to see, and it goes on holding the
     * property while it is installed. Every other property is the write's to reveal.
     *
     * The write runs whether or not the reads around it succeed. A declaration whose read before the write
     * failed holds no property, so the read after it is skipped and one declaring call reports one
     * failure however often the caller's code throws.
     */
    fun wrote(
        info: ModifierInfo,
        element: SwingModifier.NodeElement<*, *>,
        write: () -> Unit,
    ) {
        val before = readBeforeChange()
        val slot = slots.getOrPut(info) { LinkedHashSet() }
        val held = element.heldProperties
        if (before != null) {
            val landed = held.filter { it in before }
            landed.filter { it !in found }.associateWithTo(found) { before[it] }
            slot += landed
        }
        write()
        seen = before?.let { read() }
        val after = seen
        if (before == null || after == null) return
        for ((property, value) in after) {
            if (property !in held && value == before[property]) continue
            if (property !in found) found[property] = before[property]
            slot += property
        }
    }

    /**
     * Runs [restore] for [info], records that its declaration has left, and returns what it
     * left away from where the modifier found it - or `null` where it left nothing.
     *
     * Only a property that no installed declaration writes is owed. The value in a property another
     * declaration still writes is that declaration's, and the value the modifier found is owed only once
     * the last of them has left.
     */
    fun left(
        info: ModifierInfo,
        restore: () -> Unit,
    ): AssertionError? {
        val element = info.modifier
        val before = readBeforeChange()
        restore()
        seen = before?.let { read() }
        val wrote = slots.remove(info) ?: return null
        val last = wrote.filter { property -> slots.values.none { property in it } }
        val owed =
            when {
                element.restores == RestorePolicy.None -> emptyList()
                element.restores == RestorePolicy.DeclaredPropertyOnly -> last.filter { it == element.name }
                else -> last
            }
        val wrong =
            seen
                ?.let { values ->
                    owed
                        .filter { it !in movedElsewhere && values[it] != found[it] }
                        .map { "  $it: found <${found[it]}>, left <${values[it]}>" }
                }.orEmpty()
        for (property in last) {
            found.remove(property)
            movedElsewhere.remove(property)
        }
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
     * Reads [component] ahead of a write or restore, and marks the recorded insets if they moved since
     * the last of them.
     */
    private fun readBeforeChange(): Map<String, Any?>? {
        val now = read()
        val last = seen
        if (now != null && last != null) {
            if ("insets" in found && now["insets"] != last["insets"]) movedElsewhere += "insets"
        }
        return now
    }

    /**
     * What every watched property of [component] holds, or `null` where reading one threw.
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
