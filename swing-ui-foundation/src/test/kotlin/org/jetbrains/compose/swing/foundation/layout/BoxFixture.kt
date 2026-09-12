package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent

/** The one box a test tagged, the container every reading is taken from. */
internal fun ComposeSwingTest.box(): JComponent = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()

/**
 * The children of the box under test, from the bottom of its stack up. A box holds its children in the
 * reverse of stacking order, and stacks children declaring the same `zIndex` in the order they are
 * declared, so this reads as declaration order wherever no child declares a `zIndex` of its own.
 */
internal fun ComposeSwingTest.stackedChildren(): List<Component> = box().components.reversed()

/** The bounds the box assigned each of its children, from the bottom of its stack up. */
internal fun ComposeSwingTest.stackedChildBounds(): List<Rectangle> = stackedChildren().map { it.bounds }
