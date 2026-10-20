package org.jetbrains.compose.swing.style

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
