// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuItem
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/**
 * The parameters [RadioButtonMenuItemFixture] varies: the text, the selected state, the selection callback, the
 * modifier name, the foreground color and the accelerator.
 */
public data class RadioButtonMenuItemParameters(
    val text: String,
    val selected: Boolean,
    val onSelectedChange: (Boolean) -> Unit,
    val name: String,
    val color: Color,
    val accelerator: KeyStroke?,
)

/**
 * A [RadioButtonMenuItem] taking an `onSelectedChange` callback in a [Menu] of a window's [MenuBar] against a raw
 * [JFrame], never shown, whose [JMenuBar] holds a [JMenu] with a [JRadioButtonMenuItem] of the same text, selected
 * state, name, foreground color and accelerator, reporting its selection to the same callback.
 */
public object RadioButtonMenuItemFixture : ComposableFixture<RadioButtonMenuItemParameters, JFrame>(
    initial =
        RadioButtonMenuItemParameters(
            text = "Before",
            selected = false,
            onSelectedChange = {},
            name = "Before",
            color = Color.BLUE,
            accelerator = null,
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<RadioButtonMenuItemParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(selected = true),
            initial.copy(onSelectedChange = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(accelerator = KeyStroke.getKeyStroke("ctrl A")),
        )

    @Composable
    override fun Content(parameters: RadioButtonMenuItemParameters): Unit =
        Content(
            parameters.text,
            parameters.selected,
            parameters.onSelectedChange,
            parameters.name,
            parameters.color,
            parameters.accelerator,
        )

    /**
     * Composes a [RadioButtonMenuItem] showing [text] in [selected] state, reporting selection changes to
     * [onSelectedChange], named [name], drawn in [color] and activated by [accelerator], inside a menu of a hidden
     * window.
     */
    @Composable
    public fun Content(
        text: String,
        selected: Boolean,
        onSelectedChange: (Boolean) -> Unit,
        name: String,
        color: Color,
        accelerator: KeyStroke?,
    ) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    RadioButtonMenuItem(
                        text = text,
                        selected = selected,
                        onSelectedChange = onSelectedChange,
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
     * one with a bare [JRadioButtonMenuItem].
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            jMenuBar = JMenuBar().apply { add(JMenu("Host").apply { add(JRadioButtonMenuItem()) }) }
        }

    /** Applies the text, selected state, name, foreground color, accelerator and change callback of [parameters]. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: RadioButtonMenuItemParameters,
    ) {
        val item =
            component.jMenuBar
                .getMenu(0)
                .menuComponents
                .filterIsInstance<JRadioButtonMenuItem>()
                .single()
        item.text = parameters.text
        item.isSelected = parameters.selected
        item.name = parameters.name
        item.foreground = parameters.color
        item.accelerator = parameters.accelerator
        val listener =
            item.actionListeners.firstNotNullOfOrNull { it as? SelectionListener }
                ?: SelectionListener(item).also { item.addActionListener(it) }
        listener.onSelectedChange = parameters.onSelectedChange
    }

    /** Reports the selection of [item] to [onSelectedChange] whenever the item is activated. */
    private class SelectionListener(
        private val item: JRadioButtonMenuItem,
    ) : ActionListener {
        var onSelectedChange: (Boolean) -> Unit = {}

        override fun actionPerformed(event: ActionEvent) {
            onSelectedChange(item.isSelected)
        }
    }
}
