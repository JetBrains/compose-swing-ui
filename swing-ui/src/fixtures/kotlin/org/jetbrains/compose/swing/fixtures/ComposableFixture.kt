// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

import androidx.compose.runtime.Composable
import java.awt.Component

/**
 * A public composable paired with the raw Swing component it must match.
 *
 * Tests check that both sides agree for [initial] and for every value in [alternatives], and the benchmark
 * suites time and measure the same changes on both sides, so one declaration serves both. A fixture's own
 * composable takes raw parameters, so that a lambda inside it captures only the values it reads; [Content]
 * with a [P] only spreads the properties into that function.
 *
 * [P] is the fixture's parameters: a data class whose properties match the raw composable's parameters in
 * name, type and order. [C] is the raw Swing component the composable must match.
 * A modifier fixture's raw composable instead takes the modifier built from [P]'s properties.
 *
 * @property initial the values the fixture starts from.
 */
public abstract class ComposableFixture<P : Any, C : Component>(
    public val initial: P,
) {
    /** The name reports use: the class name without its `Fixture` suffix, so `LabelFixture` reports as `Label`. */
    public val name: String
        get() = javaClass.simpleName.removeSuffix("Fixture")

    /** The values the tests walk and the benchmarks switch to, each a copy of [initial] with some changes. */
    public abstract val alternatives: List<P>

    /** Composes the fixture with [parameters]. */
    @Composable
    public abstract fun Content(parameters: P)

    /** Builds the raw Swing component in its initial state, before [updateRawSwing] applies [initial]. */
    public abstract fun createRawSwing(): C

    /** Applies [parameters] to [component] the way a hand-written Swing program would. */
    public abstract fun updateRawSwing(
        component: C,
        parameters: P,
    )

    /** Hands this fixture to [visitor] with its parameter and component types. */
    public fun <R> accept(visitor: FixtureVisitor<R>): R = visitor.visit(this)
}
