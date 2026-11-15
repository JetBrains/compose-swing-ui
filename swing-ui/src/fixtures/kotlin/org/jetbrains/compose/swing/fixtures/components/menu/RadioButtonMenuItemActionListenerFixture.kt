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
import java.awt.event.ActionListener
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JRadioButtonMenuItem
import javax.swing.WindowConstants

/** The parameters [RadioButtonMenuItemActionListenerFixture] varies: the action listener only. */
public data class RadioButtonMenuItemActionListenerParameters(
    val actionListener: ActionListener,
)

/**
 * A [RadioButtonMenuItem] taking an [ActionListener] instead of an `onSelectedChange` callback, in a [Menu] of a
 * window's [MenuBar], against a raw [JFrame] whose [JMenuBar] holds a [JMenu] with a [JRadioButtonMenuItem] of the
 * same text, selected state, name, foreground color and accelerator as [RadioButtonMenuItemFixture] notifying the same
 * listener.
 */
public object RadioButtonMenuItemActionListenerFixture :
    ComposableFixture<RadioButtonMenuItemActionListenerParameters, JFrame>(
        initial = RadioButtonMenuItemActionListenerParameters(actionListener = {}),
    ) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<RadioButtonMenuItemActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: RadioButtonMenuItemActionListenerParameters): Unit =
        Content(parameters.actionListener)

    /**
     * Composes a [RadioButtonMenuItem] notifying [actionListener], with the fixed text "Before", unselected, named
     * "Before", drawn in blue and without an accelerator, inside a menu of a hidden window.
     */
    @Composable
    public fun Content(actionListener: ActionListener) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    RadioButtonMenuItem(
                        text = "Before",
                        selected = false,
                        actionListener = actionListener,
                        modifier = SwingModifier.name("Before"),
                        style = { foreground(Color.BLUE) },
                        accelerator = null,
                    )
                }
            }
        }
    }

    /**
     * Builds a [JFrame] that does not close on its own, with a [JMenuBar] holding a [JMenu] titled like the composed
     * one with a [JRadioButtonMenuItem] in the fixed state, before the listener is applied.
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            jMenuBar =
                JMenuBar().apply {
                    add(
                        JMenu("Host").apply {
                            add(
                                JRadioButtonMenuItem().also {
                                    it.text = "Before"
                                    it.isSelected = false
                                    it.name = "Before"
                                    it.foreground = Color.BLUE
                                    it.accelerator = null
                                },
                            )
                        },
                    )
                }
        }

    /** Replaces the listener this fixture installed on the menu item of [component] with the one of [parameters]. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: RadioButtonMenuItemActionListenerParameters,
    ) {
        val menu = component.jMenuBar.getMenu(0)
        val item = menu.menuComponents.single() as JRadioButtonMenuItem
        if (parameters.actionListener !in item.actionListeners) {
            item.removeActionListener(initial.actionListener)
            alternatives.forEach { item.removeActionListener(it.actionListener) }
            item.addActionListener(parameters.actionListener)
        }
    }
}
