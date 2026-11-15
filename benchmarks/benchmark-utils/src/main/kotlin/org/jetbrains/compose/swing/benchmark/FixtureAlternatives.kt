// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.FixtureVisitor
import java.awt.Component
import java.lang.reflect.Modifier

/**
 * The name each alternative of [fixture] reports under: the properties that differ from the initial values, joined
 * by `+`, or `unchanged` when equality sees none, as with a fresh lambda from a call site that always returns the same
 * one. Alternatives that change the same properties get a `#2`, `#3` suffix after the first, so that no two
 * share a name.
 */
fun alternativeNames(fixture: ComposableFixture<*, *>): List<String> = fixture.accept(AlternativeNames)

/** Names each alternative of a fixture, see [alternativeNames]. */
private object AlternativeNames : FixtureVisitor<List<String>> {
    override fun <P : Any, C : Component> visit(fixture: ComposableFixture<P, C>): List<String> {
        val seen = mutableMapOf<String, Int>()
        return fixture.alternatives.map { alternative ->
            // Reads the properties of the fixture's own parameters class, which the harness may look into.
            val changed =
                fixture.initial.javaClass.declaredFields
                    .filter { !Modifier.isStatic(it.modifiers) }
                    .filter { field ->
                        field.isAccessible = true
                        field.get(fixture.initial) != field.get(alternative)
                    }.map { it.name }
            val name = changed.joinToString("+").ifEmpty { "unchanged" }
            val count = seen.merge(name, 1, Int::plus)
            if (count == 1) name else "$name#$count"
        }
    }
}
