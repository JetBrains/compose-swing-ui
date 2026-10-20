package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import java.awt.Component
import java.awt.FlowLayout

/** Accepts only an [InsetLayout] parent. */
private val InsetProtocol: ParentProtocol = parentProtocolOf("inset parent") { it.layout is InsetLayout }

internal val InsetProperty = stylePropertyOf<Int> { Inset(it) }

/** An inheritable node-backed declaration, keyed by its class. */
internal data class Inset(
    val amount: Int,
) : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = InsetProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(amount)

    override fun update(node: InsetNode) {
        node.amount = amount
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
