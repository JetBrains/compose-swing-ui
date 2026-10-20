package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Whether a change of style asks the widget that hosts a cell to repaint, since the widget paints from the cell. */
class StyleHostRepaintTest {
    @Test
    fun aCellStyleProvidedByRowThroughANestedProviderAsksNoRepaint() = runComposeSwingTest {
        setContent {
            ListBox(
                items = listOf("first", "second"),
                selectedIndices = setOf(0),
                fixedCellWidth = 120,
                fixedCellHeight = 40,
                itemContent = {
                    val row = this
                    ProbeContainer(
                        StyleProbe(),
                        { provideNestedStyle(Probes) { if (row.isSelected) ProbeFirst.provide("selected") } },
                    ) {
                        ProbeContainer(StyleProbe(), SwingStyle) { ProbeLeaf(StyleProbe(), SwingStyle, NestedProbe) }
                    }
                },
            )
        }
        val list = onNodeOfType<JList<*>>().fetch()
        list.paintOnce()

        withRecordedRepaints {
            list.paintOnce()
            assertEquals(0, it.repaintsOf(list))
        }
    }

    @Test
    fun aCellStyleProvidedByRowThroughANestedProviderIsAppliedBeforeTheRowIsPainted() = runComposeSwingTest {
        setContent {
            ListBox(
                items = listOf("first", "second"),
                itemContent = {
                    val row = this
                    ProbeContainer(
                        StyleProbe(),
                        { provideNestedStyle(Probes) { if (row.isSelected) ProbeFirst.provide("selected") } },
                    ) {
                        ProbeContainer(StyleProbe(), SwingStyle) { ProbeLeaf(StyleProbe(), SwingStyle, NestedProbe) }
                    }
                },
            )
        }
        onNodeOfType<JList<*>>()
            .assertProperty("selected") { assertNotNull(renderCell(0, isSelected = true).leafProbe()).first }
            .assertProperty(false) { assertNotNull(renderCell(1).leafProbe()).first == "selected" }
            .assertProperty("selected") { assertNotNull(renderCell(0, isSelected = true).leafProbe()).first }
    }

    @Test
    fun paintingANestedListWithARowDependentCellStyleAsksNeitherListToRepaint() = runComposeSwingTest {
        setContent {
            ListBox(
                items = listOf("first", "second"),
                modifier = SwingModifier.testTag("outer"),
                fixedCellWidth = 120,
                fixedCellHeight = 40,
                itemContent = {
                    ListBox(
                        items = listOf("a", "b"),
                        selectedIndices = setOf(0),
                        fixedCellWidth = 120,
                        fixedCellHeight = 20,
                        itemContent = {
                            val row = this
                            ProbeLeaf(
                                StyleProbe(),
                                { if (row.isSelected) ProbeFirst.provide("selected") },
                                PlainProbe,
                            )
                        },
                    )
                },
            )
        }
        val outer = onNodeWithTag("outer").fetch() as JList<*>
        outer.paintOnce()
        val inner = outer.renderCell(0) as JList<*>

        withRecordedRepaints {
            outer.paintOnce()
            assertEquals(0, it.repaintsOf(inner))
            assertEquals(0, it.repaintsOf(outer))
        }
    }

    private fun JComponent.paintOnce() = paint(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).graphics)
}

/** The deepest [StyleProbe] in this component's first-child chain, the leaf a cell renders. */
internal fun Component.leafProbe(): StyleProbe? = when {
    this is Container && components.isNotEmpty() -> components.first().leafProbe()
    else -> this as? StyleProbe
}
