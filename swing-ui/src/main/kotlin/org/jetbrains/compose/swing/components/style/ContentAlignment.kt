// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.annotations.HorizontalAlignment
import org.jetbrains.compose.swing.annotations.VerticalAlignment
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JTextField

/**
 * Sets where a label's content sits along its width, when it is given more width than it needs.
 *
 * @param alignment a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve against the
 *   component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @see javax.swing.JLabel.setHorizontalAlignment
 */
public fun LabelStyleScope.horizontalAlignment(
    @HorizontalAlignment alignment: Int,
): Unit = HorizontalAlignmentProperty.provide(alignment)

/**
 * Sets where a button's content sits along its width, when it is given more width than it needs.
 *
 * @param alignment a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve against the
 *   component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @see javax.swing.AbstractButton.setHorizontalAlignment
 */
public fun ButtonStyleScope.horizontalAlignment(
    @HorizontalAlignment alignment: Int,
): Unit = HorizontalAlignmentProperty.provide(alignment)

/**
 * Sets where a text field's text sits along its width, when it is given more width than it needs.
 *
 * @param alignment a [HorizontalAlignment] `SwingConstants` value; `LEADING` and `TRAILING` resolve against the
 *   component's orientation. Any other value is refused with an `IllegalArgumentException`.
 * @see javax.swing.JTextField.setHorizontalAlignment
 */
public fun TextFieldStyleScope.horizontalAlignment(
    @HorizontalAlignment alignment: Int,
): Unit = HorizontalAlignmentProperty.provide(alignment)

/**
 * Sets where a label's content sits along its height, when it is given more height than it needs.
 *
 * @param alignment a [VerticalAlignment] `SwingConstants` value; any other value is refused with an
 *   `IllegalArgumentException`.
 * @see javax.swing.JLabel.setVerticalAlignment
 */
public fun LabelStyleScope.verticalAlignment(
    @VerticalAlignment alignment: Int,
): Unit = VerticalAlignmentProperty.provide(alignment)

/**
 * Sets where a button's content sits along its height, when it is given more height than it needs.
 *
 * @param alignment a [VerticalAlignment] `SwingConstants` value; any other value is refused with an
 *   `IllegalArgumentException`.
 * @see javax.swing.AbstractButton.setVerticalAlignment
 */
public fun ButtonStyleScope.verticalAlignment(
    @VerticalAlignment alignment: Int,
): Unit = VerticalAlignmentProperty.provide(alignment)

internal val HorizontalAlignmentDescriptor =
    ComponentPropertyDescriptor(
        "horizontalAlignment",
        accessor<JLabel, Int>(
            read = { it.horizontalAlignment },
            write = { component, value -> component.horizontalAlignment = value },
        ),
        accessor<AbstractButton, Int>(
            read = { it.horizontalAlignment },
            write = { component, value -> component.horizontalAlignment = value },
        ),
        accessor<JTextField, Int>(
            read = { it.horizontalAlignment },
            write = { component, value -> component.horizontalAlignment = value },
        ),
    )

internal val VerticalAlignmentDescriptor =
    ComponentPropertyDescriptor(
        "verticalAlignment",
        accessor<JLabel, Int>(
            read = { it.verticalAlignment },
            write = { component, value -> component.verticalAlignment = value },
        ),
        accessor<AbstractButton, Int>(
            read = { it.verticalAlignment },
            write = { component, value -> component.verticalAlignment = value },
        ),
    )

private val HorizontalAlignmentProperty = stylePropertyOf(HorizontalAlignmentDescriptor)

private val VerticalAlignmentProperty = stylePropertyOf(VerticalAlignmentDescriptor)
