// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

/**
 * The row value the selection components' tests tabulate. Its two fields differ in type, so a column
 * rendering one of them is distinguishable from a column rendering the other.
 */
internal data class Person(
    val name: String,
    val age: Int,
)
