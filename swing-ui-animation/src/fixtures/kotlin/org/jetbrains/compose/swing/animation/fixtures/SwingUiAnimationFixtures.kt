// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.fixtures

import org.jetbrains.compose.swing.animation.core.fixtures.AnimateFloatAsStateFixture
import org.jetbrains.compose.swing.animation.core.fixtures.AnimateIntAsStateFixture
import org.jetbrains.compose.swing.animation.core.fixtures.AnimateValueAsStateFixture
import org.jetbrains.compose.swing.animation.core.fixtures.CreateChildTransitionFixture
import org.jetbrains.compose.swing.animation.core.fixtures.CreateDeferredAnimationFixture
import org.jetbrains.compose.swing.animation.core.fixtures.InfiniteTransitionAnimateFloatFixture
import org.jetbrains.compose.swing.animation.core.fixtures.InfiniteTransitionAnimateValueFixture
import org.jetbrains.compose.swing.animation.core.fixtures.TransitionAnimateFloatFixture
import org.jetbrains.compose.swing.animation.core.fixtures.TransitionAnimateIntFixture
import org.jetbrains.compose.swing.animation.core.fixtures.TransitionAnimateValueFixture
import org.jetbrains.compose.swing.animation.core.fixtures.UpdateTransitionFixture
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
        CreateDeferredAnimationFixture,
        CrossfadeFixture,
        CrossfadeTransitionFixture,
        DeferredAnimatedContentFixture,
        DeferredAnimatedVisibilityFixture,
        InfiniteTransitionAnimateColorFixture,
        InfiniteTransitionAnimateFloatFixture,
        TransitionAnimateColorFixture,
        TransitionAnimatedContentFixture,
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
