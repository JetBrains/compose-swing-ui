package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import kotlin.test.assertEquals

/** Asserts that [declare] leaves the chain it was given standing exactly once. */
internal fun assertDeclaredChainCarriedOnce(declare: SwingModifier.() -> SwingModifier) {
    val declared = SwingModifier.testTag("carried")
    val appearances = declared.declare().foldIn(0) { count, element -> count + if (element === declared) 1 else 0 }
    assertEquals(1, appearances, "the chain the builder was given should stand in its result once")
}
