// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.composerules

import org.jetbrains.kotlin.name.FqName

/** Fully qualified names of the Kotlin coroutine types the rules recognize. */
internal object KotlinFqNames {
    val CoroutineScope = FqName("kotlinx.coroutines.CoroutineScope")
    val CoroutineContext = FqName("kotlin.coroutines.coroutineContext")
}
