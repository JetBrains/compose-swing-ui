// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.firstLabel
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Whether a change of style asks the list that hosts a cell to repaint, since the list paints from the cell. */
class StyleHostRepaintTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aCellStyleReadingTheRowItRendersDoesNotRepaintTheList(nested: Boolean) = runComposeSwingTest {
        setContent {
            val renderer =
                rememberListItemRenderer<String> { item ->
                    val row = this
                    if (nested) {
                        Panel(style = { provideNestedStyle(Labels) { if (row.isSelected) background(Color.RED) } }) {
                            Panel { Label(item) }
                        }
                    } else {
                        Label(item, style = { if (row.isSelected) background(Color.RED) })
                    }
                }
            ListBox(
                items = listOf("first", "second"),
                selectedIndices = setOf(0),
                fixedCellWidth = 120,
                fixedCellHeight = 40,
                modifier = SwingModifier.listItemRenderer(renderer),
            )
        }
        onNodeOfType<JList<*>>().captureToImage()
        withRecordedRepaints { recorded ->
            onNodeOfType<JList<*>>().apply {
                assertProperty(Color.RED) { assertNotNull(renderCell(0, isSelected = true).firstLabel()).background }
                assertProperty("second") { assertNotNull(renderCell(1).firstLabel()).text }
                assertProperty(false) { assertNotNull(renderCell(1).firstLabel()).background == Color.RED }
                assertProperty(Color.RED) { assertNotNull(renderCell(0, isSelected = true).firstLabel()).background }
                awaitIdle()
                assertProperty(
                    0,
                    "rendering rows the list paints asks for no further paint",
                ) { recorded.repaintsOf(this) }
                captureToImage()
                assertProperty(0) { recorded.repaintsOf(this) }
            }
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun nestedRendererMeasuresPrototype() = runComposeSwingTest {
        setContent {
            ListBox(
                items = listOf("outer"),
                modifier = SwingModifier.testTag("outer"),
                fixedCellWidth = 120,
                fixedCellHeight = 40,
                itemContent = {
                    val renderer = rememberListItemRenderer<String> { item -> Label(item) }
                    ListBox(
                        items = listOf("inner"),
                        modifier = SwingModifier.listItemRenderer(renderer),
                        prototypeCellValue = "prototype",
                    )
                },
            )
        }

        onNodeWithTag("outer")
            .assertIsOfType<JList<*>>()
            .assertProperty("prototype") { (renderCell(0) as JList<*>).prototypeCellValue }
            .assertProperty("inner") { ((renderCell(0) as JList<*>).renderCell(0) as JLabel).text }
    }

    @Test
    fun nestedRowPaintingDoesNotRepaintHosts() = runComposeSwingTest {
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
        onNodeWithTag("outer").assertIsOfType<JList<*>>().captureToImage()

        withRecordedRepaints { repaints ->
            onNodeWithTag("outer").assertIsOfType<JList<*>>().assertProperty(0) {
                val inner = renderCell(0) as JList<*>
                onNodeWithTag("outer").captureToImage()
                assertEquals(0, repaints.repaintsOf(inner))
                repaints.repaintsOf(this)
            }
        }
    }
}
