package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JScrollPane
import javax.swing.JTable
import kotlin.test.Test
import kotlin.test.assertSame

class TableScrollPaneHeaderTest {
    @Test
    fun aTableInAScrollPaneGetsItsHeaderInstalledAsTheColumnHeader() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Table(rows = listOf("Ada"), modifier = SwingModifier.viewport()) {
                    column("Name") { it }
                }
            }
        }

        assertSame(
            onNodeOfType<JTable>().fetch().tableHeader,
            onNodeOfType<JScrollPane>().fetch().columnHeader?.view,
        )
    }
}
