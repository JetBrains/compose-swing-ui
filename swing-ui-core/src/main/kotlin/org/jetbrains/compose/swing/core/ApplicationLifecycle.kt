// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.awaitCancellation

/**
 * Holds the enclosing application open while the calling host is in the composition.
 *
 * The application loop runs only while its recomposer's effect job has an active child coroutine. A
 * top-level host (a window, dialog, or tray icon) hosts its content through a child composition
 * without launching such a coroutine itself, so this parked effect is the host's reason to keep the
 * application open: it stays suspended until the host leaves the composition, at which point its
 * cancellation lets the application exit.
 */
@Composable
internal fun KeepEnclosingApplicationAlive() {
    LaunchedEffect(Unit) { awaitCancellation() }
}
