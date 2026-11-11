// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview

@Preview
@Composable
internal fun AnimatedContainersSection() {
    SectionColumn {
        SectionHeading("Animated containers")
        SectionHeading("Everyday transitions")
        AnimatedVisibilityCard()
        AnimatedVisibilityChildrenCard()
        VeilCard()
        AnimatedContentSlideCard()
        CrossfadeCard()
        AnimateContentSizeCard()
        SectionHeading("Advanced transitions")
        KeepUntilTransitionsFinishedCard()
        DeferredDismissCard()
        DeferredPageSwipeCard()
    }
}
