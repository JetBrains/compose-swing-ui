package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.observeReads
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty1

internal const val PROBE_OWN_VALUE = "own"

internal class StyleProbe(
    name: String? = null,
) : JPanel() {
    val writes = ArrayList<String>()

    var first: String? by recorded("first")
    var second: String? by recorded("second")
    var third: String? by recorded("third")
    var fourth: String? by recorded("fourth")

    init {
        this.name = name
    }

    private fun recorded(name: String) = Delegates.observable<String?>(PROBE_OWN_VALUE) { _, _, value ->
        writes += "$name=$value"
    }
}

internal fun ComposeSwingTest.onProbe(name: String) = onNodeWithName(name).assertIsOfType<StyleProbe>()

internal fun readPass(pass: Int) {
    pass.let { }
}

internal interface ProbeScope : ComponentStyleScope

internal interface ProbeContainerScope :
    ProbeScope,
    NestedStyleProviderScope

internal val FirstDescriptor = probeDescriptor(StyleProbe::first)
internal val SecondDescriptor = probeDescriptor(StyleProbe::second)

internal val ProbeFirst = stylePropertyOf(FirstDescriptor)
internal val ProbeSecond = stylePropertyOf(SecondDescriptor)
internal val ProbeThird = stylePropertyOf(probeDescriptor(StyleProbe::third))
internal val ProbeFourth = stylePropertyOf(probeDescriptor(StyleProbe::fourth))

internal val InheritedFirst = styleLocalOf(FirstDescriptor)
internal val InheritedSecond = styleLocalOf(SecondDescriptor)

internal val LabelTextDescriptor =
    ComponentPropertyDescriptor<JLabel, String?>("text", { it.text }, { label, value -> label.text = value })

internal val LabelText = stylePropertyOf(LabelTextDescriptor)

internal val LabelToolTip =
    stylePropertyOf(
        ComponentPropertyDescriptor<JLabel, String?>(
            "toolTipText",
            { it.toolTipText },
            { label, value -> label.toolTipText = value },
        ),
    )

internal val Probes = NestedStyleKey<ProbeScope>("Probes")

internal val OtherProbes = NestedStyleKey<ProbeScope>("OtherProbes")

internal fun probeKind(default: SwingStyle<ProbeScope> = SwingStyle): SwingStyleKind<ProbeScope, Unit> =
    SwingStyleKind({ base, _ -> object : ProbeScope, ComponentStyleScope by base {} }, default)

internal fun probeContainerKind(
    default: SwingStyle<ProbeContainerScope> = SwingStyle,
): SwingStyleKind<ProbeContainerScope, Unit> =
    SwingStyleKind({ base, _ -> object : ProbeContainerScope, NestedStyleProviderScope by base {} }, default)

internal val PlainProbe = probeKind()

internal val NestedProbe = probeKind { applyNestedStyle(Probes) }

internal val PlainProbeContainer = probeContainerKind()

@Composable
internal fun ProbeContainer(
    probe: StyleProbe,
    style: SwingStyle<ProbeContainerScope>,
    kind: SwingStyleKind<ProbeContainerScope, Unit> = PlainProbeContainer,
    content: @Composable () -> Unit,
) {
    SwingNode(factory = { probe }, modifier = SwingModifier.styleable(kind, style)) { content() }
}

@Composable
internal fun ProbeLeaf(
    probe: StyleProbe,
    style: SwingStyle<ProbeScope>,
    kind: SwingStyleKind<ProbeScope, Unit>,
) {
    SwingNode(factory = { probe }, modifier = SwingModifier.styleable(kind, style))
}

internal val MeasuringParent: ParentProtocol = MeasurementLayoutManager.parentProtocol("measuring parent")

internal class ObservingElement(
    private val read: () -> Int,
    private val onDetach: () -> Unit,
    private val onChange: () -> Unit,
) : SwingModifier.NodeElement<Component, ObservingElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): Node = Node(read, onDetach, onChange)

    override fun update(node: Node) = Unit

    override fun equals(other: Any?): Boolean = other is ObservingElement

    override fun hashCode(): Int = ObservingElement::class.hashCode()

    class Node(
        private val read: () -> Int,
        private val onDetach: () -> Unit,
        private val onChange: () -> Unit,
    ) : SwingModifier.ComponentNode<Component>(),
        ObserverModifierNode {
        override fun onAttach() = observe()

        override fun onDetach() = onDetach.invoke()

        override fun onObservedReadsChanged() {
            onChange()
            observe()
        }

        private fun observe() = observeReads { read() }
    }
}

internal class ObservingLayoutElement(
    private val read: () -> Int,
    private val onDetach: () -> Unit,
    private val onChange: () -> Unit,
) : ParentLayoutNodeElement<ObservingLayoutElement.Node>() {
    override val parentProtocol: ParentProtocol get() = MeasuringParent

    override fun create(): Node = Node(read, onDetach, onChange)

    override fun update(node: Node) = Unit

    override fun equals(other: Any?): Boolean = other is ObservingLayoutElement

    override fun hashCode(): Int = ObservingLayoutElement::class.hashCode()

    class Node(
        private val read: () -> Int,
        private val onDetach: () -> Unit,
        private val onChange: () -> Unit,
    ) : ParentLayoutNode(),
        ObserverModifierNode {
        override val parentProtocol: ParentProtocol get() = MeasuringParent

        override fun onAttach() = observe()

        override fun onDetach() = onDetach.invoke()

        override fun onObservedReadsChanged() {
            onChange()
            observe()
        }

        private fun observe() = observeReads { read() }
    }
}

internal class LifecycleElement(
    private val value: String,
    private val events: MutableList<String>,
    private val updateFailure: Throwable? = null,
    private val detachFailure: Throwable? = null,
) : SwingModifier.NodeElement<JComponent, LifecycleElement.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val name: String get() = "toolTipText"

    override fun create(): Node = Node(events, detachFailure)

    override fun update(node: Node) {
        events += "update $value"
        updateFailure?.let { throw it }
        node.component.toolTipText = value
    }

    override fun equals(other: Any?): Boolean =
        other is LifecycleElement && value == other.value && events === other.events

    override fun hashCode(): Int = value.hashCode()

    class Node(
        private val events: MutableList<String>,
        private val detachFailure: Throwable?,
    ) : SwingModifier.ComponentNode<JComponent>() {
        private var captured: String? = null

        override fun onAttach() {
            events += "attach"
            captured = component.toolTipText
        }

        override fun onDetach() {
            events += "detach"
            component.toolTipText = captured
            detachFailure?.let { throw it }
        }
    }
}

internal data class NonRestoringLifecycleElement(
    private val value: String,
) : SwingModifier.NodeElement<JComponent, NonRestoringLifecycleElement.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val name: String get() = "toolTipText"

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.toolTipText = value
    }

    class Node : SwingModifier.ComponentNode<JComponent>()
}

private fun probeDescriptor(property: KMutableProperty1<StyleProbe, String?>) =
    ComponentPropertyDescriptor<StyleProbe, String?>(
        property.name,
        { property.get(it) },
        { probe, value -> property.set(probe, value) },
    )
