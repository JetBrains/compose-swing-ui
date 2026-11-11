// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.CheckBoxMenuItems
import org.jetbrains.compose.swing.components.style.CheckableStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.node.report
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.KeyStroke

/**
 * A menu item carrying a checkmark: a `JCheckBoxMenuItem` that shows [checked] and reports the user's
 * toggle through [onCheckedChange].
 *
 * A toggle the caller does not answer with a matching [checked] is settled back onto the declared value,
 * so the mark on screen is the one the composition holds.
 *
 * @param text the text of the menu item
 * @param checked whether the item shows its checkmark
 * @param onCheckedChange callback invoked with the checked state when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JCheckBoxMenuItem
 */
@Composable
public fun CheckBoxMenuItem(
    text: @Nls String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<CheckableStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    val mirror = rememberMirrorState(checked)
    CheckBoxMenuItemNode(
        text = text,
        modifier =
            modifier.actionListener<JCheckBoxMenuItem> {
                mirror.report { onCheckedChange(isSelected) }
            },
        style = style,
        checked = checked,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/**
 * A JCheckBoxMenuItem driven by a raw [ActionListener] instead of an `onCheckedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text of the menu item
 * @param checked whether the menu item is checked
 * @param actionListener the listener notified when the item is toggled
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JCheckBoxMenuItem
 */
@Composable
public fun CheckBoxMenuItem(
    text: @Nls String,
    checked: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<CheckableStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    val mirror = rememberMirrorState(checked)
    CheckBoxMenuItemNode(
        text = text,
        modifier =
            modifier.actionListener(actionListener),
        style = style,
        checked = checked,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/** [CheckableStyleScope] for CheckBoxMenuItem styles. The declared checked state supplies `isChecked`. */
private val CheckBoxMenuItemKind =
    nestingStyleKind<CheckableStyleScope, Boolean>({ base, checked ->
        object : CheckableStyleScope, ComponentStyleScope by base {
            override val isChecked: Boolean get() = checked
        }
    }) {
        applyNestedStyle(CheckBoxMenuItems)
    }

/**
 * The `JCheckBoxMenuItem` node both [CheckBoxMenuItem] overloads render. [checked] is settled against the
 * item through [mirror] rather than applied on change: the user can toggle the item out from under the
 * declaration, and a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun CheckBoxMenuItemNode(
    text: @Nls String,
    modifier: SwingModifier,
    style: SwingStyle<CheckableStyleScope>,
    checked: Boolean,
    accelerator: KeyStroke?,
    mirror: MirrorState<Boolean>,
) {
    MenuNode(
        factory = { JCheckBoxMenuItem() },
        modifier = modifier.styleable(CheckBoxMenuItemKind, checked, style),
        update = {
            set(text) { this.text = it }
            init { addItemListener { mirror.observed(isSelected) } }
            declare(checked, mirror, JCheckBoxMenuItem::isSelected, JCheckBoxMenuItem::setSelected)
            set(accelerator) { this.accelerator = it }
        },
    )
}
