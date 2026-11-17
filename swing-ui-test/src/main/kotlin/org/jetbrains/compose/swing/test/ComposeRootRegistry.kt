// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.core.RootForTest
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

/**
 * The content compositions that exist under one test's recomposer, as the compositions announce them.
 *
 * Owned by one test harness, whose diagnostics forward the root calls to it, and which clears it when
 * it closes, after the composition it hosts has been torn down. Confined to the event dispatch thread, where
 * the compositions announce themselves: every call checks it.
 */
internal class ComposeRootRegistry {
    private val registered = ArrayList<RootForTest>()

    fun onRootForTestCreated(root: RootForTest) {
        checkConfinement()
        check(registered.none { it === root }) {
            "The root ${describeComponent(root.component)} was announced twice."
        }
        registered += root
    }

    fun onRootForTestDisposed(root: RootForTest) {
        checkConfinement()
        registered.removeAll { it === root }
    }

    /** Forgets every root; what is announced afterwards starts the registry over. */
    fun clear() {
        checkConfinement()
        registered.clear()
    }

    /**
     * The component of every registered root, in the order they were announced, each once.
     */
    fun rootComponents(): List<Component> {
        checkConfinement()
        return registered.map { it.component }.distinctByIdentity()
    }

    /**
     * The trees a query searches: [harnessRoot], then every registered root that stands in no other searched root
     * and in no window, in the order they were announced.
     *
     * A root stands in another when the other's tree reaches it the way [childComponents] reads it - a composed
     * popup is reached from the menu that owns it - so a nested composition is searched once, with the tree it
     * hangs in. Window content stays under the window finders, whether or not the window's content is announced.
     * A root that is a [JPopupMenu] or stands inside one is not window content, though a shown popup is parented
     * under its owner window's layered pane or a popup window: it is searched shown or not.
     */
    fun searchedRoots(harnessRoot: Container): List<Container> {
        val all = (listOf<Component>(harnessRoot) + rootComponents()).distinctByIdentity()
        val eligible = all.filter { candidate -> candidate === harnessRoot || !candidate.isWindowContent() }
        return eligible
            .filter { candidate ->
                candidate === harnessRoot || eligible.none { other -> other !== candidate && other.holds(candidate) }
            }.filterIsInstance<Container>()
    }

    private fun Component.isWindowContent(): Boolean =
        !isInPopupMenu() && (this is Window || SwingUtilities.getWindowAncestor(this) != null)

    private fun Component.isInPopupMenu(): Boolean = generateSequence(this) { it.parent }.any { it is JPopupMenu }

    private fun Component.holds(other: Component): Boolean = descendantComponents().any { it === other }

    private fun List<Component>.distinctByIdentity(): List<Component> =
        fold(ArrayList()) { kept, component ->
            if (kept.none { it === component }) kept += component
            kept
        }

    private fun checkConfinement() {
        check(SwingUtilities.isEventDispatchThread()) {
            "The root registry is confined to the event dispatch thread but was used on " +
                "'${Thread.currentThread().name}'."
        }
    }
}
