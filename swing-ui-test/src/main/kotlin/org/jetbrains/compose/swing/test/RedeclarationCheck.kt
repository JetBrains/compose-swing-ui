// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.modifier.ModifierInfo
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.util.IdentityHashMap

/**
 * Holds a component to one value for each key an element refuses [redeclaration][SwingModifier.NodeElement.allowsRedeclaration]
 * of, across everything that declares onto it: the chain and every style.
 *
 * Production resolves such a conflict across sources by the later declaration winning, so only a pass that has
 * settled can tell a conflict from a transient one: a source may change before another does, and the two meet
 * again at the same value. [conflicts] therefore compares what every installed declaration holds once the
 * composition has settled, and reports one failure per standing conflict, told apart by the set of
 * elements that conflict, however many settles it stands through.
 */
internal class RedeclarationCheck(
    private val report: (Throwable) -> Unit,
) : SwingCompositionDiagnostics {
    private val installed = IdentityHashMap<Component, InstalledDeclarations>()

    override fun declaring(
        info: ModifierInfo,
        write: () -> Unit,
    ) {
        installed.getOrPut(info.component) { InstalledDeclarations() }.declarations[info] = Declared(info.modifier)
        write()
    }

    override fun restoring(
        info: ModifierInfo,
        restore: () -> Unit,
    ) {
        val component = info.component
        val held = installed[component]
        if (held != null) {
            held.declarations.remove(info)
            if (held.declarations.isEmpty()) installed.remove(component)
        }
        restore()
    }

    /** Reports each conflict the installed declarations hold that was not reported already. */
    fun conflicts() {
        installed.values.toList().fastForEach { held ->
            val standing =
                held.declarations.values
                    .groupBy { it.key }
                    .values
                    .filter { it.conflicts() }
                    .map { group ->
                        StandingConflict(
                            elements = group.map { it.element }.filterNot { it.additive }.toSet(),
                            refusing = group.first { !it.element.allowsRedeclaration }.element,
                        )
                    }
            held.reported.retainAll(standing.map { it.elements }.toSet())
            standing.fastForEach { conflict ->
                if (held.reported.add(conflict.elements)) {
                    report(
                        IllegalStateException(
                            "${conflict.refusing.name} is declared twice on this component with different values; " +
                                "a component holds one: declare it once.",
                        ),
                    )
                }
            }
        }
    }

    private fun List<Declared>.conflicts(): Boolean {
        val elements = map { it.element }.filterNot { it.additive }
        return elements.any { first -> elements.any { second -> first.conflictsWith(second) } }
    }

    private fun SwingModifier.NodeElement<*, *>.conflictsWith(other: SwingModifier.NodeElement<*, *>): Boolean =
        (!allowsRedeclaration || !other.allowsRedeclaration) && this != other

    /**
     * An installed declaration's element, and the key it was declared under, read once so a settled pass does
     * not ask an element for its key again.
     */
    private class Declared(
        val element: SwingModifier.NodeElement<*, *>,
    ) {
        val key = element.key
    }

    /**
     * A conflict standing on a component: the distinct elements that conflict, whose set is its identity whatever
     * order the declarations are read in, and the element that refuses the redeclaration, which the report names.
     */
    private class StandingConflict(
        val elements: Set<SwingModifier.NodeElement<*, *>>,
        val refusing: SwingModifier.NodeElement<*, *>,
    )

    /** One component's installed declarations, and the conflicts already reported for it. */
    private class InstalledDeclarations {
        val declarations = IdentityHashMap<ModifierInfo, Declared>()
        val reported = HashSet<Set<SwingModifier.NodeElement<*, *>>>()
    }
}
