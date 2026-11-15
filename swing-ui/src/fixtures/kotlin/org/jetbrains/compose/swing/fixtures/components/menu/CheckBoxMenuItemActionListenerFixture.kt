// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame

/** The parameters [CheckBoxMenuItemActionListenerFixture] varies: the action listener only. */
public data class CheckBoxMenuItemActionListenerParameters(
    val actionListener: ActionListener,
)

/**
 * A [CheckBoxMenuItem] taking an [ActionListener] instead of an `onCheckedChange` callback, in a [Menu] of a window's
 * [MenuBar], against a raw [JFrame] in the same state as [CheckBoxMenuItemFixture.initial] notifying the same listener.
 */
public object CheckBoxMenuItemActionListenerFixture :
    ComposableFixture<CheckBoxMenuItemActionListenerParameters, JFrame>(
        initial = CheckBoxMenuItemActionListenerParameters(actionListener = {}),
    ) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<CheckBoxMenuItemActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: CheckBoxMenuItemActionListenerParameters): Unit =
        Content(parameters.actionListener)

    /**
     * Composes a [CheckBoxMenuItem] notifying [actionListener], with the shared values of
     * [CheckBoxMenuItemFixture.initial], inside a menu of a hidden window.
     */
    @Composable
    public fun Content(actionListener: ActionListener) {
        val shared = CheckBoxMenuItemFixture.initial
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    CheckBoxMenuItem(
                        text = shared.text,
                        checked = shared.checked,
                        actionListener = actionListener,
                        modifier = SwingModifier.name(shared.name),
                        style = { foreground(shared.color) },
                        accelerator = shared.accelerator,
                    )
                }
            }
        }
    }

    /**
     * Builds a [JFrame] in the shared initial state of [CheckBoxMenuItemFixture], holding its menu item, before the
     * listener is applied.
     */
    override fun createRawSwing(): JFrame =
        CheckBoxMenuItemFixture.createRawSwing().also { frame ->
            val shared = CheckBoxMenuItemFixture.initial
            val item =
                frame.jMenuBar
                    .getMenu(0)
                    .menuComponents
                    .filterIsInstance<JCheckBoxMenuItem>()
                    .single()
            item.apply {
                text = shared.text
                isSelected = shared.checked
                name = shared.name
                foreground = shared.color
                accelerator = shared.accelerator
            }
        }

    /** Replaces the listener this fixture installed on the menu item of [component] with the one of [parameters]. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: CheckBoxMenuItemActionListenerParameters,
    ) {
        val item =
            component.jMenuBar
                .getMenu(0)
                .menuComponents
                .filterIsInstance<JCheckBoxMenuItem>()
                .single()
        if (parameters.actionListener !in item.actionListeners) {
            item.removeActionListener(initial.actionListener)
            alternatives.forEach { item.removeActionListener(it.actionListener) }
            item.addActionListener(parameters.actionListener)
        }
    }
}
