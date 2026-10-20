package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingComponentNode
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel

/** Accepts only an [InsetLayout] parent. */
internal val InsetProtocol: ParentProtocol = parentProtocolOf("inset parent") { it.layout is InsetLayout }

internal val InsetProperty = stylePropertyOf<Int> { Inset(it) }

/** An inheritable node-backed declaration with a class key unless an explicit key is supplied. */
internal data class Inset(
    val amount: Int,
    override val key: Any = Inset::class.java,
    val onWrite: (Int) -> Unit = {},
) : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(amount)

    override fun update(node: InsetNode) {
        node.amount = amount
        onWrite(amount)
    }
}

internal class InsetNode(
    var amount: Int,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val inheritable: Boolean get() = true
}

/** The measuring manager [InsetProtocol] accepts. */
internal class InsetLayout : RecordingMeasurementLayout(FlowLayout()) {
    /** The inset node declared for [component]. */
    fun insetOf(component: Component): InsetNode = elementsOf(component).filterIsInstance<InsetNode>().single()
}

/** A declaration every measuring parent interprets. */
internal data object MeasuredAnywhere : ParentLayoutElement {
    override val parentProtocol: ParentProtocol = MeasuringParent
}

/** A measuring manager [InsetProtocol] refuses. */
internal class OtherMeasuringLayout : RecordingMeasurementLayout(FlowLayout())

internal class DeclaredLayoutLabel(
    text: String = "",
) : JLabel(text),
    DeclaredNodesListener {
    var declared: List<SwingModifier.Node> = emptyList()
        private set

    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) {
        declared = nodes
    }
}
