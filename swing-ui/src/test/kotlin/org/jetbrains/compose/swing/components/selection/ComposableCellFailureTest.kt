package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What a composable cell body that throws costs. A cell is composed synchronously, inside the paint or
 * layout pass that asked for it, so the failure is reported to whoever rendered - and to nobody else. The
 * window goes on composing: the cell composition is a child of the window's recomposer, and a failure
 * carried into it would end recomposition for everything the window holds. The cell composition itself
 * stays usable.
 */
class ComposableCellFailureTest {
    @Test
    fun aCellBodyThatThrowsLeavesTheRestOfTheWindowComposing() = runComposeSwingTest {
        var caption by mutableStateOf("before")
        setContent {
            Label(caption)
            ListBox(items = listOf("alpha", "beta")) { item ->
                check(item != "boom") { "the cell body cannot render $item" }
                Label(item)
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        // A row the cell body cannot render. It is rendered directly because a row of the declared items
        // is rendered by every layout pass, this test's own setContent included.
        assertFailsWith<IllegalStateException> {
            list.cellRenderer.renderCell(value = "boom", index = 0, list = list)
        }

        caption = "after"
        awaitIdle()

        onNodeWithText("after").assertExists()
        // Rendered after the recomposer's own pass has run, so that pass still meets whatever the failed
        // render left published. Both rows: a cell frozen on its last render still holds one of them, and
        // the other tells it apart from a cell that recomposes.
        assertEquals(
            "beta",
            list.renderCell(index = 1).firstLabelText(),
            "a cell composition renders the next row it is rendered with",
        )
        assertEquals(
            "alpha",
            list.renderCell(index = 0).firstLabelText(),
            "the row the render that threw could not render renders again",
        )
    }
}
