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
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/**
 * The parameters [CheckBoxMenuItemFixture] varies: the text, the checked state, the toggle callback, the
 * modifier name, the foreground color and the accelerator.
 */
public data class CheckBoxMenuItemParameters(
    val text: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val name: String,
    val color: Color,
    val accelerator: KeyStroke?,
)

/**
 * A [CheckBoxMenuItem] taking an `onCheckedChange` callback in a [Menu] of a window's [MenuBar] against a raw
 * [JFrame], never shown, whose [JMenuBar] holds a [JMenu] with a [JCheckBoxMenuItem] of the same text, checked state,
 * name, foreground color and accelerator, reporting its selection to the same callback.
 */
public object CheckBoxMenuItemFixture : ComposableFixture<CheckBoxMenuItemParameters, JFrame>(
    initial =
        CheckBoxMenuItemParameters(
            text = "Before",
            checked = false,
            onCheckedChange = {},
            name = "Before",
            color = Color.BLUE,
            accelerator = null,
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<CheckBoxMenuItemParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(checked = true),
            initial.copy(onCheckedChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(accelerator = KeyStroke.getKeyStroke("ctrl A")),
        )

    @Composable
    override fun Content(parameters: CheckBoxMenuItemParameters): Unit =
        Content(
            parameters.text,
            parameters.checked,
            parameters.onCheckedChange,
            parameters.name,
            parameters.color,
            parameters.accelerator,
        )

    /**
     * Composes a [CheckBoxMenuItem] showing [text] in [checked] state, reporting toggles to [onCheckedChange],
     * named [name], drawn in [color] and activated by [accelerator], inside a menu of a hidden window.
     */
    @Composable
    public fun Content(
        text: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        name: String,
        color: Color,
        accelerator: KeyStroke?,
    ) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    CheckBoxMenuItem(
                        text = text,
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = SwingModifier.name(name),
                        style = { foreground(color) },
                        accelerator = accelerator,
                    )
                }
            }
        }
    }

    /**
     * Builds a [JFrame] that does not close on its own, with a [JMenuBar] holding a [JMenu] titled like the composed
     * one with a bare [JCheckBoxMenuItem].
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            jMenuBar = JMenuBar().apply { add(JMenu("Host").apply { add(JCheckBoxMenuItem()) }) }
        }

    /** Applies the text, checked state, name, foreground color, accelerator and change callback of [parameters]. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: CheckBoxMenuItemParameters,
    ) {
        val menu = component.jMenuBar.getMenu(0)
        val item = menu.menuComponents.single() as JCheckBoxMenuItem
        item.text = parameters.text
        item.isSelected = parameters.checked
        item.name = parameters.name
        item.foreground = parameters.color
        item.accelerator = parameters.accelerator
        val listener = item.actionListeners.firstNotNullOfOrNull { it as? SelectionListener }
        if (listener == null) {
            item.addActionListener(SelectionListener(item, parameters.onCheckedChange))
        } else {
            listener.onCheckedChange = parameters.onCheckedChange
        }
    }

    /** Reports the selection of [item] to [onCheckedChange] whenever the item is activated. */
    private class SelectionListener(
        private val item: JCheckBoxMenuItem,
        var onCheckedChange: (Boolean) -> Unit,
    ) : ActionListener {
        override fun actionPerformed(event: ActionEvent) {
            onCheckedChange(item.isSelected)
        }
    }
}
