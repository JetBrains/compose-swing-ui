// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

import java.awt.Component

/**
 * Receives a fixture with its parameter and component types.
 *
 * A list of fixtures of different types holds them as `ComposableFixture<*, *>`, which cannot pass a parameter
 * value back to the fixture. [ComposableFixture.accept] restores the types for the duration of [visit].
 */
public interface FixtureVisitor<R> {
    /** Handles [fixture] and returns the result for it. */
    public fun <P : Any, C : Component> visit(fixture: ComposableFixture<P, C>): R
}
