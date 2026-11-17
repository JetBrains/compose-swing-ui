// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JSplitPane

/** The regions a split pane holds its children in. */
private val slotPlacement: ChildPlacement<Container> = ChildPlacement.Slots("left", "right")

/** A fresh attachment that installs a component as the left side of a split pane. */
private fun leftAttachment(): SlotAttachment<JSplitPane> =
    SlotAttachment<JSplitPane> { host, component, _ ->
        host.add(component, JSplitPane.LEFT)
        return@SlotAttachment { host.remove(component) }
    }

/** A fresh attachment that installs a component as the right side of a split pane. */
private fun rightAttachment(): SlotAttachment<JSplitPane> =
    SlotAttachment<JSplitPane> { host, component, _ ->
        host.add(component, JSplitPane.RIGHT)
        return@SlotAttachment { host.remove(component) }
    }

/** A split pane without the default left and right buttons, so only the slotted label fills a region. */
private fun emptySplitPane(): JSplitPane =
    JSplitPane().apply {
        leftComponent = null
        rightComponent = null
    }

/** The parameters [SlotFixture] varies. */
public data class SlotParameters(
    val parentProtocol: ParentProtocol<JSplitPane>,
    val name: String,
    val attachment: SlotAttachment<JSplitPane>?,
)

/** A [Label] filling a region of a split pane through [slot] against a raw [JSplitPane] holding the same label. */
public object SlotFixture : ComposableFixture<SlotParameters, JSplitPane>(
    initial =
        SlotParameters(
            parentProtocol = parentProtocolOf<JSplitPane>("split pane"),
            name = "left",
            attachment = leftAttachment(),
        ),
) {
    override val alternatives: List<SlotParameters> =
        listOf(
            initial.copy(
                parentProtocol = parentProtocolOf<JSplitPane>("alternate split pane"),
            ),
            initial.copy(name = "right", attachment = rightAttachment()),
            initial.copy(attachment = leftAttachment()),
        )

    /** Composes a [Label] in a split pane, filling the region the parameters name through their attachment. */
    @Composable
    override fun Content(parameters: SlotParameters) {
        SwingNode(factory = { emptySplitPane() }, childPlacement = slotPlacement) {
            Label(
                "Slotted",
                modifier =
                    SwingModifier.slot(
                        parentProtocol = parameters.parentProtocol,
                        name = parameters.name,
                        attachment = parameters.attachment,
                    ),
            )
        }
    }

    override fun createRawSwing(): JSplitPane = emptySplitPane()

    override fun updateRawSwing(
        component: JSplitPane,
        parameters: SlotParameters,
    ) {
        val side = if (parameters.name == "right") JSplitPane.RIGHT else JSplitPane.LEFT
        val onRequestedSide = if (side == JSplitPane.RIGHT) component.rightComponent else component.leftComponent
        val label = component.leftComponent ?: component.rightComponent
        if (label != null && onRequestedSide != null) return
        val placed = label ?: JLabel("Slotted")
        component.leftComponent = null
        component.rightComponent = null
        if (side == JSplitPane.RIGHT) component.rightComponent = placed else component.leftComponent = placed
        // Moving a component between regions at runtime is followed by a revalidate and repaint, as any Swing program
        // does.
        component.revalidate()
        component.repaint()
    }
}
