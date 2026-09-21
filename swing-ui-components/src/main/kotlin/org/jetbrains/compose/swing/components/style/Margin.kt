// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.style.stylePropertyOf
import java.awt.Insets
import javax.swing.AbstractButton
import javax.swing.text.JTextComponent

/**
 * Sets the space a button keeps between its border and its content.
 *
 * This is the space inside the border; to put space outside it, size the button or space it in its container. Once
 * the style stops setting it, the button gets back the space the look and feel had chosen.
 *
 * The button's default border turns this into space, so a button given another border leaves the margin to that
 * border - an empty or a line border ignores it.
 *
 * @param margin how much each side keeps; all four at zero pulls the content flush with the border.
 * @see javax.swing.AbstractButton.setMargin
 */
public fun ButtonStyleScope.margin(margin: Insets): Unit = MarginProperty.provide(margin)

/**
 * Sets the space a text component keeps between its border and its text.
 *
 * This is the space inside the border; to put space outside it, size the component or space it in its container.
 * Once the style stops setting it, the component gets back the space the look and feel had chosen.
 *
 * The component's default border turns this into space, so a component given another border leaves the margin to
 * that border - an empty or a line border ignores it.
 *
 * @param margin how much each side keeps; all four at zero pulls the text flush with the border.
 * @see javax.swing.text.JTextComponent.setMargin
 */
public fun TextStyleScope.margin(margin: Insets): Unit = MarginProperty.provide(margin)

/**
 * A button and a text component each declare margin for themselves, and no supertype between them
 * declares it. Both report no margin as null, even though the public function always takes an [Insets].
 */
internal val MarginDescriptor =
    ComponentPropertyDescriptor(
        "margin",
        accessor<AbstractButton, Insets?>(
            read = { it.margin },
            write = { component, value -> component.margin = value },
        ),
        accessor<JTextComponent, Insets?>(
            read = { it.margin },
            // JTextComponent.setMargin only invalidates. A margin changes the preferred size and also moves
            // the text inside the border, which a relayout that keeps the bounds does not repaint, so this
            // write asks for both.
            write = { component, value ->
                component.margin = value
                component.revalidate()
                component.repaint()
            },
        ),
    )

private val MarginProperty = stylePropertyOf(MarginDescriptor)
