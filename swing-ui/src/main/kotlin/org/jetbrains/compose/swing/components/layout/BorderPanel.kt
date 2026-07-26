@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.core.LocalSwingConstraint
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * A composable wrapper for JPanel with BorderLayout, exposing each region as a declarative slot.
 *
 * Declare the regions you need in [block]:
 * ```
 * BorderPanel {
 *     north { Toolbar() }
 *     center { Editor() }
 *     south { StatusBar() }
 * }
 * ```
 * A region hosts exactly one child; redeclaring it replaces the child, and dropping a region (e.g.
 * behind an `if`) removes its child. Omitted regions attach nothing.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param hgap the horizontal gap between regions
 * @param vgap the vertical gap between regions
 * @param block declares the regions; see [BorderPanelScope]
 */
@Composable
public fun BorderPanel(
    modifier: SwingModifier = SwingModifier,
    hgap: Int = 0,
    vgap: Int = 0,
    block: BorderPanelScope.() -> Unit,
) {
    // Collected fresh on every pass, so a region the caller stops declaring loses its child (see SwingNode).
    val scope = BorderPanelScopeImpl().apply(block)

    SwingNode(
        factory = { JPanel(BorderLayout(hgap, vgap)) },
        update = {
            updateLayout<BorderLayout, _>(hgap) { this.hgap = it }
            updateLayout<BorderLayout, _>(vgap) { this.vgap = it }
            applyModifier(modifier)
        },
        content = {
            scope.slots.forEach { (region, slot) ->
                // key() gives each region a stable composition identity independent of declaration
                // order, so adding or dropping one region never reshuffles the others.
                key(region) {
                    CompositionLocalProvider(LocalSwingConstraint provides region) {
                        slot()
                    }
                }
            }
        },
    )
}

/**
 * Collects the region declarations for one composition. Each region method writes its block under the
 * matching [BorderLayout] constraint string; a repeated call for the same region overwrites the
 * previous entry, so the last call wins. The map preserves first-declaration order purely for stable
 * iteration - placement is governed entirely by each entry's constraint, not its position.
 */
private class BorderPanelScopeImpl : BorderPanelScope {
    val slots: MutableMap<String, @Composable () -> Unit> = LinkedHashMap()

    private fun put(
        region: String,
        block: @Composable () -> Unit,
    ) {
        slots[region] = block
    }

    override fun north(block: @Composable () -> Unit): Unit = put(BorderLayout.NORTH, block)

    override fun south(block: @Composable () -> Unit): Unit = put(BorderLayout.SOUTH, block)

    override fun east(block: @Composable () -> Unit): Unit = put(BorderLayout.EAST, block)

    override fun west(block: @Composable () -> Unit): Unit = put(BorderLayout.WEST, block)

    override fun center(block: @Composable () -> Unit): Unit = put(BorderLayout.CENTER, block)

    override fun pageStart(block: @Composable () -> Unit): Unit = put(BorderLayout.PAGE_START, block)

    override fun pageEnd(block: @Composable () -> Unit): Unit = put(BorderLayout.PAGE_END, block)

    override fun lineStart(block: @Composable () -> Unit): Unit = put(BorderLayout.LINE_START, block)

    override fun lineEnd(block: @Composable () -> Unit): Unit = put(BorderLayout.LINE_END, block)
}
