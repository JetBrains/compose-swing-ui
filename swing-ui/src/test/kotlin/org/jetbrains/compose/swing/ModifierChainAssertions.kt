package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.tooling.findDeclaringGroup
import java.awt.Component
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Asserts that [declare] leaves the chain it was given standing in its result exactly once.
 *
 * A builder that joins a factory taking its receiver implicitly with `then` - `this then
 * layoutConstraint(x)` - expands to `this then (this then LayoutConstraintElement)`, putting everything
 * the caller declared before it into the chain twice. The chain keeps a slot per appearance of an
 * additive element, so a listener declared ahead of such a builder then reports every event twice.
 */
internal fun assertDeclaredChainCarriedOnce(declare: SwingModifier.() -> SwingModifier) {
    val declared = SwingModifier.testTag("carried")
    val appearances = declared.declare().foldIn(0) { count, element -> count + if (element === declared) 1 else 0 }
    assertEquals(1, appearances, "the chain the builder was given should stand in its result once")
}

/**
 * How many times the chain this component carries declares the [testTag] element naming [tag].
 *
 * A widget assembles its own declarations onto the chain it was handed, through builders private to the
 * file it lives in that no test can call. Reading what the component ends up carrying reaches those the
 * way a caller does, so the same invariant is pinned without widening anything to be tested.
 *
 * The chain is reached through the declaring group, so the composition must have inspection turned on.
 */
internal fun Component.carriedChainAppearancesOf(tag: String): Int {
    val node = assertNotNull(findDeclaringGroup()?.node as? SwingComponentNode, "no composition declared $this")
    return node.modifier.foldIn(0) { count, element -> count + if (element.declaredValues["testTag"] == tag) 1 else 0 }
}
