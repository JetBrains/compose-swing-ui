package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A nested style reaches what is composed inside the component that provides it. A style that reads a state reaches
 * them again when it changes.
 */
class StylePropagationTest {
    @Test
    fun aCellStyleReadingTheRowItRendersDoesNotRepaintTheList() = runComposeSwingTest {
        val list = RepaintCountingList("first", "second")
        setContent {
            val renderer =
                rememberListItemRenderer<String> {
                    val row = this
                    ProbeLeaf(StyleProbe(), { if (row.isSelected) ProbeFirst.provide("selected") }, PlainProbe)
                }
            SwingNode(factory = { list }, modifier = SwingModifier.listItemRenderer(renderer))
        }
        val repaintsBefore = list.repaints

        assertEquals("selected", (list.renderCell(0, isSelected = true) as StyleProbe).first)
        list.renderCell(1)
        awaitIdle()

        assertEquals(repaintsBefore, list.repaints, "rendering rows the list paints asks for no further paint")
    }

    @Test
    fun aRendererReplacedLaterInTheChainIsNoLongerHosted() = runComposeSwingTest {
        setContent {
            val a = rememberListItemRenderer<String> { Label("a:$it") }
            val b = rememberListItemRenderer<String> { Label("b:$it") }
            ListBox(
                items = listOf("x"),
                modifier = SwingModifier.testTag("one").listItemRenderer(a).listItemRenderer(b),
            )
            ListBox(items = listOf("y"), modifier = SwingModifier.testTag("two").listItemRenderer(a))
        }
        onNodeWithTag("one").assertIsOfType<JList<*>>().assertProperty("b:x") { (renderCell(0) as JLabel).text }
        onNodeWithTag("two").assertIsOfType<JList<*>>().assertProperty(
            "a:y",
            "the second list shows the first renderer's cell",
        ) { (renderCell(0) as JLabel).text }
    }

    @Test
    fun aSubtreeMovedUnderAProviderAndAwayTakesItsStylesWithIt() = runComposeSwingTest {
        var underProvider by mutableStateOf(false)
        val provider = SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } }
        setContent {
            val content = remember { movableContentOf { ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe) } }
            ProbeContainer(StyleProbe(), provider) { if (underProvider) content() }
            ProbeContainer(StyleProbe(), SwingStyle) { if (!underProvider) content() }
        }
        val leaf = onProbe("leaf")
        leaf.assertProperty(PROBE_OWN_VALUE, "a leaf outside the provider keeps its own value") { first }

        underProvider = true
        awaitIdle()
        leaf.assertProperty("provided", "the moved leaf takes the provider's style") { first }

        underProvider = false
        awaitIdle()
        leaf.assertProperty(PROBE_OWN_VALUE, "the leaf that moved away returns to its own value") { first }
    }

    @Test
    fun aProviderThatStartsAndStopsAboveASwingNodeHoleReachesTheLeafBelowIt() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val provider = SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } }
        setContent {
            ProbeContainer(StyleProbe(), if (providing) provider else SwingStyle) {
                SwingNode(factory = { JPanel() }) { ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe) }
            }
        }
        val leaf = onProbe("leaf")

        providing = true
        awaitIdle()
        leaf.assertProperty("provided", "the provider reaches the leaf across the hole") { first }

        providing = false
        awaitIdle()
        leaf.assertProperty(PROBE_OWN_VALUE, "the leaf's own value returns") { first }
    }

    @Test
    fun aLeafUnderNestedProvidersKeepsTheInnerOnesStyleWhenTheOuterStops() = runComposeSwingTest {
        var outerProviding by mutableStateOf(true)
        setContent {
            ProbeContainer(
                StyleProbe(),
                {
                    if (outerProviding) provideNestedStyle(Probes) { ProbeFirst.provide("outer") }
                },
            ) {
                ProbeContainer(StyleProbe(), { provideNestedStyle(Probes) { ProbeSecond.provide("inner") } }) {
                    ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe)
                }
            }
        }
        val leaf = onProbe("leaf")
        leaf.assertProperty("outer", "the outer provider styles the leaf") { first }
        leaf.assertProperty("inner", "the inner provider styles the leaf") { second }

        outerProviding = false
        awaitIdle()
        leaf.assertProperty(PROBE_OWN_VALUE, "the leaf loses what the outer provider stopped providing") { first }
        leaf.assertProperty("inner", "the inner provider still styles the leaf") { second }
    }
}

/** A list that counts the repaint requests made on it; every `repaint` overload of a component reaches this one. */
private class RepaintCountingList(
    vararg items: String,
) : JList<String>(items) {
    var repaints: Int = 0
        private set

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        repaints++
        super.repaint(tm, x, y, width, height)
    }
}
