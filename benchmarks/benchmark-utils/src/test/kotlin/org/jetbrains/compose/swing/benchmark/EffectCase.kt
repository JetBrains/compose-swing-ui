// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.awaitCancellation

/** A case whose only content is a `LaunchedEffect` keyed on state that [toggle] flips. */
internal class EffectCase : ComposeTestCase {
    private var changed by mutableStateOf(false)

    fun toggle() {
        changed = !changed
    }

    @Composable
    override fun Content() {
        LaunchedEffect(changed) {
            awaitCancellation()
        }
    }
}
