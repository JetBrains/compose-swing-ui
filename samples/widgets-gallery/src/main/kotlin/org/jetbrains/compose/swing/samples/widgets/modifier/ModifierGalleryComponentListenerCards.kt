package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelListener

// Raw java.awt listener cards for a component's own lifecycle (pointer motion/wheel, show/hide,
// hierarchy changes, and a container's child additions/removals) rather than a specific widget's
// value.

@Composable
internal fun ColumnScope.MouseMotionAndWheelListenerCard() {
    ExampleCard("mouseMotionListener / mouseWheelListener") {
        var position by remember { mutableStateOf("outside") }
        var rotation by remember { mutableIntStateOf(0) }
        val motionListener =
            remember {
                object : MouseMotionAdapter() {
                    override fun mouseMoved(event: MouseEvent) {
                        position = "${event.x}, ${event.y}"
                    }
                }
            }
        val wheelListener = remember { MouseWheelListener { event -> rotation += event.wheelRotation } }
        Label(
            "Move the pointer or scroll over this label",
            modifier =
                SwingModifier
                    .opaque(true)
                    .background(Color(0xF3, 0xE5, 0xF5))
                    .preferredSize(Dimension(260, 32))
                    .mouseMotionListener(motionListener)
                    .mouseWheelListener(wheelListener),
        )
        Label("Position: $position, wheel rotation: $rotation")
    }
}

@Composable
internal fun ColumnScope.ComponentAndHierarchyListenerCard() {
    ExampleCard("componentListener / hierarchyListener") {
        var shown by remember { mutableStateOf(true) }
        var componentEvents by remember { mutableIntStateOf(0) }
        var hierarchyEvents by remember { mutableIntStateOf(0) }
        val watcher =
            remember {
                object : ComponentAdapter() {
                    override fun componentShown(event: ComponentEvent) {
                        componentEvents++
                    }

                    override fun componentHidden(event: ComponentEvent) {
                        componentEvents++
                    }
                }
            }
        val hierarchyWatcher = remember { HierarchyListener { hierarchyEvents++ } }
        CheckBox(text = "Shown", checked = shown, onCheckedChange = { shown = it })
        FlowPanel(modifier = SwingModifier.preferredSize(Dimension(180, 40)), hgap = 0, vgap = 0) {
            Button(
                "Watched button",
                modifier =
                    SwingModifier
                        .visible(shown)
                        .componentListener(watcher)
                        .hierarchyListener(hierarchyWatcher),
            )
        }
        Label("Component events: $componentEvents, hierarchy events: $hierarchyEvents")
    }
}

@Composable
internal fun ColumnScope.ContainerListenerCard() {
    ExampleCard("containerListener (raw ContainerListener)") {
        var childPresent by remember { mutableStateOf(false) }
        var added by remember { mutableIntStateOf(0) }
        var removed by remember { mutableIntStateOf(0) }
        val listener =
            remember {
                object : ContainerAdapter() {
                    override fun componentAdded(event: ContainerEvent) {
                        added++
                    }

                    override fun componentRemoved(event: ContainerEvent) {
                        removed++
                    }
                }
            }
        CheckBox(text = "Add a child", checked = childPresent, onCheckedChange = { childPresent = it })
        FlowPanel(modifier = SwingModifier.containerListener(listener)) {
            if (childPresent) Label("I was just added")
        }
        Label("Added: $added, removed: $removed")
    }
}
