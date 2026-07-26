package org.jetbrains.compose.swing.components.selection

/**
 * The row value the selection components' tests tabulate. Its two fields differ in type, so a column
 * rendering one of them is distinguishable from a column rendering the other.
 */
internal data class Person(
    val name: String,
    val age: Int,
)
