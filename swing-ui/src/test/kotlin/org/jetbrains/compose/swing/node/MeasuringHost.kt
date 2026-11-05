package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.LayoutDeclaration
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.NestedProbe
import org.jetbrains.compose.swing.style.PlainProbeContainer
import org.jetbrains.compose.swing.style.Probes
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.SwingStyleProperty
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Asserts that [block] fails with [T], including test teardown. */
internal inline fun <reified T : Throwable> assertComposingFails(noinline block: suspend ComposeSwingTest.() -> Unit) {
    assertFailsWith<T> { runComposeSwingTest(block = block) }
}

internal fun Array<Component>.texts(): List<String> = map { (it as JLabel).text }

internal interface TaggedDeclaration {
    val tag: String
}

/** A declaration to the measuring parent, replaced by the next one of its [slot]. */
internal data class Mark(
    override val tag: String,
    val slot: Any,
    override val inheritable: Boolean,
    val protocol: ParentProtocol,
) : ParentLayoutElement,
    TaggedDeclaration {
    override val parentProtocol: ParentProtocol get() = protocol

    override val key: Any get() = slot
}

internal val MeasuringProtocol: ParentProtocol = MeasurementLayoutManager.parentProtocol("measuring parent")

internal fun protocolOf(owner: String): ParentProtocol =
    parentProtocolOf("$owner parent") { (it as? MeasuringHost)?.protocolName == owner }

/** A container whose layout records each child's declaration; [protocolName] names the protocol that accepts it. */
internal class MeasuringHost(
    val protocolName: String = "",
    val recording: RecordingMeasurementLayout = RecordingMeasurementLayout(FlowLayout()),
) : JPanel(recording) {
    fun texts(): List<String> = components.texts()
}

/** Asserts the labels of this host are [expected] in order, and only the `g` labels carry the group's mark. */
internal fun MeasuringHost.assertLabels(
    expected: List<String>,
    case: String,
) {
    assertEquals(expected, texts(), case)
    for (component in components) {
        val grouped = (component as JLabel).text.startsWith("g")
        assertEquals(if (grouped) listOf("G") else emptyList(), recording.tagsOf(component), "$case: ${component.text}")
    }
}

/** Parent data of [protocol]; folds into the list of the tags declared. */
internal data class Data(
    val tag: String,
    val slot: Int,
    override val parentProtocol: ParentProtocol,
) : ParentDataModifier {
    override val key: Any get() = slot

    override fun modifyParentData(parentData: Any?): Any = (parentData as? List<*>).orEmpty() + tag
}

internal fun RecordingMeasurementLayout.tagsOf(component: Component): List<String> =
    elementsOf(component).map { (it as TaggedDeclaration).tag }

internal val LayoutDeclaration.tags: List<String> get() = elements.map { (it as TaggedDeclaration).tag }

internal fun SwingModifier.mark(
    tag: String,
    slot: Int = 1,
    protocol: ParentProtocol = MeasuringProtocol,
): SwingModifier = this then Mark(tag, slot, inheritable = false, protocol)

/** A group of two labels, `g1` and `g2`, declaring [tag]. */
@Composable
internal fun Group(tag: String) {
    VirtualSwingNode(SwingModifier.mark(tag)) {
        Label("g1")
        Label("g2")
    }
}

internal data class NodeMark(
    val tag: String,
    override val key: Any = NodeMark::class.java,
    override val additive: Boolean = false,
    override val inheritable: Boolean = false,
    override val parentProtocol: ParentProtocol = MeasuringProtocol,
) : ParentLayoutNodeElement<NodeMarkNode>() {
    override fun create(): NodeMarkNode = NodeMarkNode(this)

    override fun update(node: NodeMarkNode) {
        node.declaration = this
    }
}

internal class NodeMarkNode(
    var declaration: NodeMark,
) : ParentLayoutNode(),
    TaggedDeclaration {
    override val tag: String get() = declaration.tag
    override val parentProtocol: ParentProtocol get() = declaration.parentProtocol
    override val inheritable: Boolean get() = declaration.inheritable
}

/**
 * The style value [StyledHost] provides to the nested style of its panel, read inside the style. A static local, so a
 * change replaces the locals map of the nodes below it, and the batch resolves their styles again once it has applied
 * every node.
 */
internal val LocalMark = staticCompositionLocalOf { "none" }

/** Provides a nested style whose value is read from a composition local. */
@Composable
internal fun StyledHost(
    host: JPanel,
    style: String,
    property: SwingStyleProperty<String>,
    modifier: SwingModifier =
        SwingModifier.styleable(PlainProbeContainer) {
            provideNestedStyle(Probes) { property.provide(LocalMark.currentValue) }
        },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMark provides style) {
        SwingNode(factory = { JPanel() }, modifier = modifier) {
            SwingNode(factory = { host }, content = content)
        }
    }
}

/** Declares `Mark(value, 2)`. */
internal val MarkProperty = stylePropertyOf<String> { Mark(it, 2, false, MeasuringProtocol) }

/** Declares `Mark(value, 1)`, the key [mark] gives an element by default. */
internal val ReplacingMarkProperty = stylePropertyOf<String> { Mark(it, 1, false, MeasuringProtocol) }

/** A label that takes the style the enclosing panels provide for [Probes]. */
@Composable
internal fun ProbedLabel(
    modifier: SwingModifier = SwingModifier,
    label: JLabel = JLabel("a"),
) {
    SwingNode(factory = { label }, modifier = modifier.styleable(NestedProbe, SwingStyle))
}
