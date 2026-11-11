// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.apply
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent

/**
 * The tag the container under test carries. A test declares exactly one, so its children are
 * read back off it without having to name each of them.
 */
internal const val CONTAINER_TAG: String = "container"

/** The width every fixture child asks for. */
internal const val CHILD_WIDTH: Int = 50

/** The height every fixture child asks for. */
internal const val CHILD_HEIGHT: Int = 40

/** How many children a fixture declares when the point being made needs more than one. */
internal const val CHILD_COUNT: Int = 3

/** Tags the container under test. */
internal fun SwingModifier.containerTag(): SwingModifier = testTag(CONTAINER_TAG)

/**
 * The style of the container under test: a size for its parent to impose on it, and the reading direction its
 * placement of a child resolves against.
 */
internal fun containerStyle(
    width: Int,
    height: Int,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
): SwingStyle<ComponentStyleScope> =
    SwingStyle {
        preferredSize(width, height)
        componentOrientation(orientation)
    }

/**
 * A child asking for one fixed size and declaring no maximum of its own, so the extent it ends up
 * occupying is the extent its container granted it and nothing else.
 */
@Composable
@NonRestartableComposable
internal fun SizedChild(
    index: Int,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<LabelStyleScope> = SwingStyle,
) {
    Child(index, CHILD_WIDTH, CHILD_HEIGHT, modifier, style)
}

/** The fixture child at a size of its own, for a container holding children of more than one size. */
@Composable
internal fun Child(
    index: Int,
    width: Int,
    height: Int,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<LabelStyleScope> = SwingStyle,
) {
    Label(
        "child $index",
        modifier = modifier,
        style = {
            preferredSize(width, height)
            apply(style)
        },
    )
}

/**
 * A fixture-sized child whose component reports [baseline] at any extent and accepts a decoration, so a layout
 * modifier may place it with a layer.
 */
@Composable
internal fun DecoratedBaselineChild(
    baseline: Int,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { DecoratedBaselinePanel(baseline) },
        modifier = modifier.styleable { preferredSize(CHILD_WIDTH, CHILD_HEIGHT) },
    )
}

/**
 * Paints nothing of a decoration it receives; the tests using it read only where it is placed. Its baseline is
 * [baseline] below the top of its layout bounds, which Swing's `getBaseline` measures from the top of the bounds.
 */
private class DecoratedBaselinePanel(
    private val baseline: Int,
) : DecoratedPanel() {
    override fun getBaseline(
        width: Int,
        height: Int,
    ): Int = decoration.paintOutsets().top + baseline
}

/** The bounds the container under test assigned each of its children, in declaration order. */
internal fun ComposeSwingTest.childBounds(): List<Rectangle> =
    onNodeWithTag(CONTAINER_TAG).fetch<JComponent>().childrenInDeclarationOrder().map {
        it.bounds
    }

/** This container's children in the order its composable content declared them. */
internal fun Container.childrenInDeclarationOrder(): List<Component> =
    if (this is ConstrainedPanel) stackingOrder.order.toList() else components.toList()

/** The size the container under test asks of its own parent. */
internal fun ComposeSwingTest.containerPreferredSize(): Dimension = container().preferredSize

/** The extent the container under test can shrink to, the counterpart of [containerPreferredSize]. */
internal fun ComposeSwingTest.containerMinimumSize(): Dimension = container().minimumSize

/** The last entry of this modifier that describes itself - the one the builder under test declared. */
internal fun SwingModifier.lastElement(): SwingModifier.InspectableElement =
    foldIn<SwingModifier.InspectableElement?>(null) { last, element ->
        element as? SwingModifier.InspectableElement ?: last
    } ?: error("the modifier declares nothing that describes itself")

/** The size the container under test was laid out at. */
internal fun ComposeSwingTest.containerSize(): Dimension = container().size

/** The bounds a column assigns children stacked at [tops], each at the fixture child's own size. */
internal fun columnRows(vararg tops: Int): List<Rectangle> = tops.map { Rectangle(0, it, CHILD_WIDTH, CHILD_HEIGHT) }

/** The bounds a row assigns children lined up at [lefts], each at the fixture child's own size. */
internal fun rowCells(vararg lefts: Int): List<Rectangle> = lefts.map { Rectangle(it, 0, CHILD_WIDTH, CHILD_HEIGHT) }

/** The one container a test tagged, which every reading above is taken from. */
private fun ComposeSwingTest.container(): JComponent = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()
