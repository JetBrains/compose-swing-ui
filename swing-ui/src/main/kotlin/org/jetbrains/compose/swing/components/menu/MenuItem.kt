// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.MenuItems
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JMenuItem
import javax.swing.KeyStroke

/**
 * An item that performs an action when the user picks it: a `JMenuItem` reporting the pick through
 * [onClick].
 *
 * @param text the text of the menu item
 * @param onClick callback to be invoked when the menu item is clicked
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JMenuItem
 */
@Composable
public fun MenuItem(
    text: @Nls String,
    onClick: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ButtonStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    MenuItemNode(
        text = text,
        accelerator = accelerator,
        modifier = modifier.actionListener { onClick() },
        style = style,
    )
}

/** [ButtonStyleScope] for MenuItem styles. No component state is supplied. */
private val MenuItemKind =
    nestingStyleKind<ButtonStyleScope, Unit>({ base, _ -> object : ButtonStyleScope, ComponentStyleScope by base {} }) {
        applyNestedStyle(MenuItems)
    }

/**
 * The `JMenuItem` node [MenuItem] renders. [modifier] already carries the item's
 * activation channel.
 *
 * Inlined into its caller, so it shares the caller's restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun MenuItemNode(
    text: @Nls String,
    accelerator: KeyStroke?,
    modifier: SwingModifier,
    style: SwingStyle<ButtonStyleScope>,
) {
    MenuNode(
        factory = { JMenuItem() },
        modifier = modifier.styleable(MenuItemKind, style),
        update = {
            set(text) { this.text = it }
            set(accelerator) { this.accelerator = it }
        },
    )
}
