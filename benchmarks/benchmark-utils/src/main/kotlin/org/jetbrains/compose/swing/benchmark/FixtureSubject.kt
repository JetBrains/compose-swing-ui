// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.testutils.ComposeTestCase
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.FixtureVisitor
import java.awt.Component
import java.awt.Container
import java.awt.Window

/**
 * One instance of a fixture on both arms the suites compare: composed, and raw Swing.
 *
 * Every instance keeps its own state, so changing one leaves its siblings as they were. A change switches the
 * instance between the fixture's initial values and one of its alternatives, so that repeated calls keep changing
 * something.
 */
interface FixtureSubject : ComposeTestCase {
    /** Switches the composed arm between the initial values and the alternative at [alternative]. */
    fun toggle(alternative: Int)

    /**
     * Builds the raw Swing component with the initial values and adds it to [parent].
     *
     * Returns false and adds nothing when the fixture's raw Swing is a window, which a container cannot hold.
     */
    fun mountRawSwing(parent: Container): Boolean

    /** Switches the raw Swing arm between the initial values and the alternative at [alternative]. */
    fun toggleRawSwing(alternative: Int)

    /** Creates subjects of a fixture. */
    companion object {
        /** Creates a subject of [fixture] showing its initial values. */
        fun of(fixture: ComposableFixture<*, *>): FixtureSubject = fixture.accept(SubjectFactory)
    }
}

/** Builds a [FixtureSubject] with the fixture's own parameter and component types. */
private object SubjectFactory : FixtureVisitor<FixtureSubject> {
    override fun <P : Any, C : Component> visit(fixture: ComposableFixture<P, C>): FixtureSubject =
        object : FixtureSubject {
            private var composed by mutableStateOf(fixture.initial)
            private var rawSwing: C? = null
            private var rawSwingValues = fixture.initial

            @Composable
            override fun Content() {
                fixture.Content(composed)
            }

            override fun toggle(alternative: Int) {
                composed = switched(composed, alternative)
            }

            override fun mountRawSwing(parent: Container): Boolean {
                val component = fixture.createRawSwing()
                if (component is Window) {
                    component.dispose()
                    return false
                }
                fixture.updateRawSwing(component, fixture.initial)
                parent.add(component)
                rawSwing = component
                return true
            }

            override fun toggleRawSwing(alternative: Int) {
                val component = checkNotNull(rawSwing) { "Mount the raw Swing component before changing it." }
                rawSwingValues = switched(rawSwingValues, alternative)
                fixture.updateRawSwing(component, rawSwingValues)
            }

            private fun switched(
                current: P,
                alternative: Int,
            ): P {
                val target = fixture.alternatives[alternative]
                return if (current === target) fixture.initial else target
            }
        }
}
