package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.DefaultListSelectionModel
import javax.swing.JList
import javax.swing.JTable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionModelWriteTest {
    @TestFactory
    fun unchangedDeclaredSelectionsPreserveLeadNotifications(): List<DynamicTest> =
        listOf("ListBox", "Table").flatMap { widget ->
            listOf(true, false).map { announced ->
                val case = "$widget announced=$announced selection=[2, 3, 4]"
                dynamicTest(case) {
                    assertUnchangedSelection(widget, announced, case)
                }
            }
        }

    private fun assertUnchangedSelection(
        widget: String,
        announced: Boolean,
        case: String,
    ) = runComposeSwingTest {
        var marker by mutableStateOf("before")
        setContent {
            if (widget == "Table") {
                Table(
                    rows = (0..7).toList(),
                    columns = { column("Number") { it } },
                    modifier = SwingModifier.name(marker),
                    selectedRowIndices = setOf(2, 3, 4),
                )
            } else {
                ListBox(
                    items = (0..7).toList(),
                    modifier = SwingModifier.name(marker),
                    selectedIndices = setOf(2, 3, 4),
                )
            }
        }
        val model =
            if (widget == "Table") {
                onNodeOfType<JTable>().fetch().selectionModel as DefaultListSelectionModel
            } else {
                onNodeOfType<JList<*>>().fetch().selectionModel as DefaultListSelectionModel
            }
        model.isLeadAnchorNotificationEnabled = announced
        val settled = mutableListOf<IntRange>()
        model.addListSelectionListener {
            if (!it.valueIsAdjusting) settled += it.firstIndex..it.lastIndex
        }
        marker = "after"
        awaitIdle()
        assertEquals(
            announced,
            model.isLeadAnchorNotificationEnabled,
            "$case: reapplying selection must preserve the notification policy",
        )
        assertEquals(
            2,
            model.anchorSelectionIndex,
            "$case: the anchor must stay at the start of the declared interval",
        )
        assertEquals(
            4,
            model.leadSelectionIndex,
            "$case: the lead must stay at the end of the declared interval",
        )
        assertTrue(
            settled.isEmpty(),
            "$case: an unchanged selection and lead must publish no settled event",
        )
        model.moveLeadSelectionIndex(7)
        assertEquals(
            if (announced) listOf(4..7) else emptyList(),
            settled,
            "$case: a later lead change must follow the incoming notification policy",
        )
    }
}
