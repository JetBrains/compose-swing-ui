package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.firstLabel
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Whether a change of style asks the list that hosts a cell to repaint, since the list paints from the cell. */
class StyleHostRepaintTest {
    @Test
    fun aCellStyleProvidedByRowThroughANestedProviderAsksNoRepaint() = runComposeSwingTest {
        setContent {
            ListBox(
                items = listOf("first", "second"),
                selectedIndices = setOf(0),
                fixedCellWidth = 120,
                fixedCellHeight = 40,
                itemContent = { item ->
                    val row = this
                    Panel(
                        style = {
                            provideNestedStyle(Labels) { if (row.isSelected) background(Color.RED) }
                        },
                    ) {
                        Panel { Label(item) }
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
                itemContent = { item ->
                    val row = this
                    Panel(
                        style = {
                            provideNestedStyle(Labels) { if (row.isSelected) background(Color.RED) }
                        },
                    ) {
                        Panel { Label(item) }
                    }
                },
            )
        }
        onNodeOfType<JList<*>>()
            .assertProperty(Color.RED) { assertNotNull(renderCell(0, isSelected = true).firstLabel()).background }
            .assertProperty(false) { assertNotNull(renderCell(1).firstLabel()).background == Color.RED }
            .assertProperty(Color.RED) { assertNotNull(renderCell(0, isSelected = true).firstLabel()).background }
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
                        itemContent = { item ->
                            val row = this
                            Label(item, style = { if (row.isSelected) background(Color.RED) })
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

    private fun JList<*>.paintOnce() = paint(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).graphics)
}
