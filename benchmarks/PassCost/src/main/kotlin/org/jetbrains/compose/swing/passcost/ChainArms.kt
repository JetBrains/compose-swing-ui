package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.passcost.harness.Frames
import org.jetbrains.compose.swing.setContent
import java.awt.Container
import javax.swing.JPanel

/*
 * What a modifier chain costs a pass, and what re-applying one costs above re-declaring it unchanged.
 */

/** Every widget carrying the same four-element modifier chain, re-declared on every pass. */
internal fun chainArm(
    name: String,
    freshCallback: Boolean,
): Arm =
    Arm(listOf(name)) { widgets, changing ->
        val tick = mutableStateOf(UNSET_TICK)
        val panelRuns = IntArray(1)
        val mountedListeners = IntArray(widgets)
        Run(
            mount = { root ->
                val handle =
                    root.setContent(parent = Frames.compositionContext) {
                        Column {
                            repeat(widgets) { ChainPanel(tick, freshCallback) { panelRuns[0]++ } }
                        }
                    }
                val panels = chainedPanels(root)
                checkWidgets("chained panels", panels.size, widgets)
                for (index in panels.indices) mountedListeners[index] = panels[index].propertyChangeListeners.size
                handle
            },
            drive = { pass ->
                if (changing) tick.value = pass % 2
                name
            },
            verify = { root, passes ->
                checkWidgets("panels", panelCount(root), widgets + 1)
                checkScopeRuns("the panel scopes", panelRuns[0], widgets * if (changing) 1 + passes else 1)
                val panels = chainedPanels(root)
                checkApplied("chain tool tip", panels.map { it.toolTipText }, CHAIN_TOOL_TIP)
                checkListenersHeld(panels, mountedListeners)
            },
        )
    }

/**
 * The panels a chain was declared onto: every panel the composed [root] holds but the column's own,
 * which comes first and carries no chain.
 */
private fun chainedPanels(root: Container): List<JPanel> = componentsOfType(root, JPanel::class.java).drop(1)

/**
 * Raises unless every one of [panels] still holds the listeners it held once its chain had been applied -
 * which is what says a chain declared again adopted its listener rather than stacking another.
 *
 * [atMount] is what each panel carried then, and not a number of its own: a look and feel installs
 * listeners as it pleases, so what a panel holds says nothing except against what it was composed with.
 */
private fun checkListenersHeld(
    panels: List<JPanel>,
    atMount: IntArray,
) {
    panels.forEachIndexed { index, panel ->
        val held = panel.propertyChangeListeners.size
        check(held == atMount[index]) {
            "a panel holds $held property change listeners, where it was composed holding ${atMount[index]}"
        }
    }
}
