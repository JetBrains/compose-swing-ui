package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.currentValueOf
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performEvent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleSlotLocalConsumerTest {
    @Test
    fun aStyleTooltipRefreshPreservesALaterDirectTooltip() = runComposeSwingTest {
        val tooltip = stylePropertyOf<Unit> { MouseToolTipElement }
        setContent {
            SwingNode(
                factory = { JLabel() },
                modifier =
                    SwingModifier
                        .styleable({ tooltip.provide(Unit) }, PlainProbe)
                        .property(DirectToolTipDescriptor, "later"),
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("later") { toolTipText }

        label.performEvent { MouseEvent(it, MouseEvent.MOUSE_MOVED, 0L, 0, 1, 1, 0, false) }
        awaitIdle()

        label.assertProperty("later") { toolTipText }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localKinds")
    fun styleElementFollowsLocalOnce(local: ProvidableCompositionLocal<String>) = runComposeSwingTest {
        var value by mutableStateOf("first")
        val names = mutableListOf<String?>()
        val property = stylePropertyOf { read: CompositionLocal<String> -> LocalNameElement(read) }
        val style = SwingStyle<ProbeScope> { property.provide(local) }
        setContent {
            CompositionLocalProvider(local provides value) {
                SwingNode(
                    factory = {
                        object : JLabel() {
                            override fun setName(name: String?) {
                                names += name
                                super.setName(name)
                            }
                        }
                    },
                    modifier = SwingModifier.styleable(style, PlainProbe),
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("first") { name }
        names.clear()

        value = "second"
        awaitIdle()
        label.assertProperty("second") { name }
        assertEquals(listOf<String?>("second"), names)
    }

    @Test
    fun styleReadsLocalBehindConsumer() = runComposeSwingTest {
        var value by mutableStateOf("first")
        val style = SwingStyle<ProbeScope> { ProbeFirst.provide(LocalValue.currentValue) }
        setContent {
            CompositionLocalProvider(LocalValue provides value) {
                SwingNode(
                    factory = { StyleProbe("probe") },
                    modifier = (SwingModifier then LocalNameElement()).styleable(style, PlainProbe),
                )
            }
        }
        onProbe("probe").assertProperty("first") { first }

        value = "second"
        awaitIdle()
        onProbe("probe").assertProperty("second") { first }
    }

    @Test
    fun layoutLocalChangeRepaintsParent() = runComposeSwingTest {
        var value by mutableStateOf("first")
        val repaints = mutableListOf<Rectangle>()
        val parent =
            object : JPanel(RecordingMeasurementLayout(FlowLayout())) {
                override fun repaint(
                    tm: Long,
                    x: Int,
                    y: Int,
                    width: Int,
                    height: Int,
                ) {
                    repaints += Rectangle(x, y, width, height)
                    super.repaint(tm, x, y, width, height)
                }
            }
        val layoutProperty = stylePropertyOf<Unit> { LocalReadingLayoutElement }
        setContent {
            CompositionLocalProvider(LocalValue provides value) {
                SwingNode(factory = { parent }) {
                    SwingNode(
                        factory = { JLabel("styled") },
                        modifier = SwingModifier.styleable({ layoutProperty.provide(Unit) }, PlainProbe),
                    )
                }
            }
        }
        assertTrue(parent.width > 0 && parent.height > 0, "the parent has an area to repaint")
        repaints.clear()

        value = "second"
        awaitIdle()

        assertContains(repaints, Rectangle(0, 0, parent.width, parent.height))
    }

    companion object {
        @JvmStatic
        fun localKinds(): List<Arguments> = listOf(
            Arguments.of(Named.of("a dynamic local", compositionLocalOf { "default" })),
            Arguments.of(Named.of("a static local", staticCompositionLocalOf { "default" })),
        )
    }
}

private class LocalReadingLayoutNode :
    ParentLayoutNode(),
    CompositionLocalConsumerModifierNode {
    override val parentProtocol: ParentProtocol get() = MeasuringParent
}

private object LocalReadingLayoutElement : ParentLayoutNodeElement<LocalReadingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = MeasuringParent

    override fun create(): LocalReadingLayoutNode = LocalReadingLayoutNode()

    override fun update(node: LocalReadingLayoutNode): Unit = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class LocalNameElement(
    private val local: CompositionLocal<String>? = null,
) : SwingModifier.NodeElement<Component, LocalNameElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val restores: RestorePolicy get() = RestorePolicy.None

    override val heldProperties: Set<String> get() = emptySet()

    override fun create(): Node = Node()

    override fun update(node: Node) {
        local?.let { node.component.name = node.currentValueOf(it) }
    }

    override fun equals(other: Any?): Boolean = other is LocalNameElement && local === other.local

    override fun hashCode(): Int = System.identityHashCode(local)

    class Node :
        SwingModifier.ComponentNode<Component>(),
        CompositionLocalConsumerModifierNode
}

private val LocalValue = staticCompositionLocalOf { "default" }

private object MouseToolTipElement : SwingModifier.NodeElement<JLabel, MouseToolTipElement.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val name: String get() = "toolTip"

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.toolTipText = node.text
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)

    class Node :
        SwingModifier.ComponentNode<JLabel>(),
        CompositionLocalConsumerModifierNode {
        var text: String by mutableStateOf("initial")
            private set
        private var original: String? = null
        private val pointer =
            object : MouseAdapter() {
                override fun mouseMoved(event: MouseEvent) {
                    text = "styled"
                }
            }

        override fun onAttach() {
            original = component.toolTipText
            component.addMouseMotionListener(pointer)
        }

        override fun onDetach() {
            component.removeMouseMotionListener(pointer)
            component.toolTipText = original
        }
    }
}

private val DirectToolTipDescriptor =
    ComponentPropertyDescriptor<JComponent, String?>(
        "toolTipText",
        { it.toolTipText },
        { component, value -> component.toolTipText = value },
    )
