// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core.fixtures

import org.jetbrains.compose.swing.fixtures.ComposableFixture

/** Every fixture of swing-ui-animation-core, for consumers that run all of them, such as the benchmark suites. */
public val swingUiAnimationCoreFixtures: List<ComposableFixture<*, *>> =
    listOf(
        CreateDeferredAnimationFixture,
        InfiniteTransitionAnimateFloatFixture,
        AnimateFloatAsStateFixture,
        AnimateIntAsStateFixture,
        AnimateValueAsStateFixture,
        CreateChildTransitionFixture,
        InfiniteTransitionAnimateValueFixture,
        TransitionAnimateFloatFixture,
        TransitionAnimateIntFixture,
        TransitionAnimateValueFixture,
        UpdateTransitionFixture,
    )
