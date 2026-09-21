// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import org.jetbrains.compose.swing.fixtures.ComposableFixture

/** Every fixture of swing-ui-animation, for consumers that run all of them, such as the benchmark suites. */
public val swingUiAnimationFixtures: List<ComposableFixture<*, *>> =
    listOf(
        AnimateColorAsStateFixture,
        AnimateContentSizeFixture,
        AnimateEnterExitFixture,
        AnimatedContentFixture,
        AnimatedVisibilityFixture,
        AnimatedVisibilityStateFixture,
        AnimatedVisibilityTransitionFixture,
        ConstrainedAnimatedContentFixture,
        CrossfadeFixture,
        CrossfadeTransitionFixture,
        DeferredAnimatedContentFixture,
        DeferredAnimatedVisibilityFixture,
        InfiniteTransitionAnimateColorFixture,
        TransitionAnimateColorFixture,
        TransitionAnimatedContentFixture,
    )
