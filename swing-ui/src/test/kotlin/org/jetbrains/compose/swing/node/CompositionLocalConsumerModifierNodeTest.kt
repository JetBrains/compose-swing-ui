package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.lang.ref.WeakReference
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val LocalDynamic = compositionLocalOf { "default" }

private val LocalStatic = staticCompositionLocalOf { "default" }

/** Restores the name it found unless [restores] is false, for a node writing over a property element that does. */
private class NameFromLocalNode(
    val local: CompositionLocal<String>,
    private val restores: Boolean,
) : SwingModifier.ComponentNode<Component>(),
    CompositionLocalConsumerModifierNode {
    private var original: String? = null

    override fun onAttach() {
        original = component.name
    }

    override fun onDetach() {
        if (restores) component.name = original
    }
}

private class NameFromLocalElement(
    private val local: CompositionLocal<String>,
    override val additive: Boolean = false,
    private val onCreate: (NameFromLocalNode) -> Unit = {},
) : SwingModifier.NodeElement<Component, NameFromLocalNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): NameFromLocalNode = NameFromLocalNode(local, restores = !additive).also(onCreate)

    override fun update(node: NameFromLocalNode) {
        node.component.name = node.currentValueOf(node.local)
    }

    override fun equals(other: Any?): Boolean =
        other is NameFromLocalElement && local === other.local && additive == other.additive

    override fun hashCode(): Int = System.identityHashCode(local)
}

private fun SwingModifier.nameFrom(
    local: CompositionLocal<String>,
    additive: Boolean = false,
): SwingModifier = this then NameFromLocalElement(local, additive)

private class FixedNameNode : SwingModifier.ComponentNode<Component>() {
    private var original: String? = null

    override fun onAttach() {
        original = component.name
    }

    override fun onDetach() {
        component.name = original
    }
}

/** Writes the same property as [NameFromLocalElement] under another key, reading no local. */
private data class FixedNameElement(
    private val value: String,
) : SwingModifier.NodeElement<Component, FixedNameNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): FixedNameNode = FixedNameNode()

    override fun update(node: FixedNameNode) {
        node.component.name = value
    }
}

private fun SwingModifier.fixedName(name: String): SwingModifier = this then FixedNameElement(name)

class CompositionLocalConsumerModifierNodeTest {
    @Test
    fun currentValueOfResolvesTheLocalInScopeWhereTheNodeWasDeclared() = runComposeSwingTest {
        setContent {
            CompositionLocalProvider(LocalDynamic provides "hello") {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.nameFrom(LocalDynamic))
            }
        }
        assertEquals("hello", onNodeOfType<JLabel>().fetch().name)
    }

    @Test
    fun aChangedLocalDynamicRunsUpdateAgain() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        setContent {
            CompositionLocalProvider(LocalDynamic provides value) {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.nameFrom(LocalDynamic))
            }
        }
        value = "changed"
        awaitIdle()
        assertEquals("changed", onNodeOfType<JLabel>().fetch().name)
    }

    @Test
    fun aChangedLocalStaticRunsUpdateAgain() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.nameFrom(LocalStatic))
            }
        }
        value = "changed"
        awaitIdle()
        assertEquals("changed", onNodeOfType<JLabel>().fetch().name)
    }

    @Test
    fun aChangedLocalStaticIsReadByTheParentLayingOutALayoutNode() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        var node: StaticReadingLayoutNode? = null
        var seen: String? = null
        var parent: Container? = null
        val relayout: (StaticReadingLayoutNode) -> Unit = { parent?.revalidate() }
        val layout = mockk<MeasurementLayoutManager>(relaxed = true)
        every { layout.declareComponentLayout(any(), any(), any()) } answers {
            node = thirdArg<List<ParentLayoutElement>>().filterIsInstance<StaticReadingLayoutNode>().single()
        }
        // Measures under observeReads, as a parent reading a layout node does.
        every { layout.layoutContainer(any()) } answers {
            parent = firstArg()
            node?.let { read -> read.observeReads(relayout) { seen = read.currentValueOf(LocalStatic) } }
        }
        every { layout.preferredLayoutSize(any()) } answers { Dimension() }
        every { layout.minimumLayoutSize(any()) } answers { Dimension() }
        every { layout.maximumLayoutSize(any()) } answers { Dimension(Int.MAX_VALUE, Int.MAX_VALUE) }
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(factory = { JPanel(layout) }, content = {
                    SwingNode(factory = { JLabel() }, modifier = SwingModifier then StaticReadingLayoutElement)
                })
            }
        }
        assertEquals("hello", seen)
        value = "changed"
        awaitIdle()
        assertEquals("changed", seen, "the parent lays the child out again with the new value")
    }

    @Test
    fun aChangedLocalStaticRepaintsTheParentLayingOutALayoutNode() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val repaints = mutableListOf<Rectangle>()
        val layout = mockk<MeasurementLayoutManager>(relaxed = true)
        every { layout.preferredLayoutSize(any()) } answers { Dimension(120, 40) }
        every { layout.minimumLayoutSize(any()) } answers { Dimension() }
        every { layout.maximumLayoutSize(any()) } answers { Dimension(Int.MAX_VALUE, Int.MAX_VALUE) }
        val parent =
            object : JPanel(layout) {
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
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(factory = { parent }, content = {
                    SwingNode(factory = { JLabel() }, modifier = SwingModifier then StaticReadingLayoutElement)
                })
            }
        }
        assertTrue(parent.width > 0 && parent.height > 0, "the parent has an area to repaint")
        repaints.clear()

        value = "changed"
        awaitIdle()

        assertContains(
            repaints,
            Rectangle(0, 0, parent.width, parent.height),
            "the parent of a layout node that reads locals is repainted when the locals change",
        )
    }

    @Test
    fun aChangedLocalStaticIsWrittenOverTheUpdateBlockWrittenInTheSamePass() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.nameFrom(LocalStatic),
                    update = { set(value) { name = "update $it" } },
                )
            }
        }
        value = "changed"
        awaitIdle()
        assertEquals("changed", onNodeOfType<JLabel>().fetch().name, "the modifier writes after the update block")
    }

    @Test
    fun aChangedLocalStaticLeavesALaterPropertyElementStanding() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.nameFrom(LocalStatic).fixedName("later"))
            }
        }
        value = "changed"
        awaitIdle()
        assertEquals("later", onNodeOfType<JLabel>().fetch().name, "the later declaration stands")
    }

    @Test
    fun aChangedLocalDynamicLeavesALaterPropertyElementStanding() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        setContent {
            CompositionLocalProvider(LocalDynamic provides value) {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.nameFrom(LocalDynamic).fixedName("later"))
            }
        }
        value = "changed"
        awaitIdle()
        assertEquals("later", onNodeOfType<JLabel>().fetch().name, "the later declaration stands")
    }

    @Test
    fun aChangedLocalStaticKeepsAnAdditiveElementAfterEveryPropertyElement() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.nameFrom(LocalStatic, additive = true).fixedName("property"),
                )
            }
        }
        assertEquals("hello", onNodeOfType<JLabel>().fetch().name)
        value = "changed"
        awaitIdle()
        assertEquals("changed", onNodeOfType<JLabel>().fetch().name, "additive elements apply after property elements")
    }

    @Test
    fun aChangedLocalRunsUpdateAgainInAMenuComposition() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        val menu =
            composeMenu {
                CompositionLocalProvider(LocalDynamic provides value) {
                    MenuNode(factory = { JMenuItem() }, modifier = SwingModifier.nameFrom(LocalDynamic))
                }
            }
        val item = menu.getComponent(0)
        assertEquals("hello", item.name)
        value = "changed"
        awaitIdle()
        assertEquals("changed", item.name)
    }

    @Test
    fun aDetachedNodeNoLongerFollowsTheLocal() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        var consuming by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalDynamic provides value) {
                SwingNode(
                    factory = { JLabel() },
                    modifier = if (consuming) SwingModifier.nameFrom(LocalDynamic) else SwingModifier,
                )
            }
        }
        consuming = false
        awaitIdle()
        val label = onNodeOfType<JLabel>().fetch()
        assertNull(label.name)
        value = "changed"
        awaitIdle()
        assertNull(label.name)
    }

    @Test
    fun aDetachedNodeIsNotHeldByTheComposition() = runComposeSwingTest {
        var created: WeakReference<NameFromLocalNode>? = null
        val element = NameFromLocalElement(LocalDynamic) { created = WeakReference(it) }
        var consuming by mutableStateOf(true)
        setContent {
            // A provided dynamic local is snapshot state, so the node's read of it registers a scope.
            CompositionLocalProvider(LocalDynamic provides "hello") {
                SwingNode(factory = { JLabel() }, modifier = if (consuming) element else SwingModifier)
            }
        }
        consuming = false
        awaitIdle()
        // One System.gc() is a hint, so a collection is asked for several times before giving up.
        repeat(20) {
            if (created?.get() == null) return@runComposeSwingTest
            System.gc()
            awaitIdle()
        }
        assertNull(created?.get(), "a detached node must not stay reachable from the composition")
    }

    @Test
    fun currentValueOfAndComponentFailOnANodeThatIsNotAttached() {
        val node = NameFromLocalNode(LocalDynamic, restores = true)
        assertFalse(node.isAttached)
        assertFailsWith<IllegalStateException> { node.currentValueOf(LocalDynamic) }
        assertFailsWith<IllegalStateException> { node.component }
    }
}

private class StaticReadingLayoutNode :
    ParentLayoutNode(),
    CompositionLocalConsumerModifierNode {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol
}

private object StaticReadingLayoutElement : ParentLayoutNodeElement<StaticReadingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): StaticReadingLayoutNode = StaticReadingLayoutNode()

    override fun update(node: StaticReadingLayoutNode): Unit = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}
