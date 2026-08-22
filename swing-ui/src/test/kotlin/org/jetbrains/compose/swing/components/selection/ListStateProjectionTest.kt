package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.ListSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What a [ListState] reports of the list it drives, as opposed to what it declares to it.
 *
 * A declaration is a request. A list has no row for an index its items do not reach, and a selection mode
 * that holds one row keeps one of the rows a wider declaration named. These queries read the list, which
 * is why they can disagree with the state that drove it.
 */
class ListStateProjectionTest {
    private val names = listOf("Ada", "Alan", "Grace")

    @Test
    fun theItemCountFollowsWhatTheListShows() = runComposeSwingTest {
        var items by mutableStateOf(names)
        lateinit var state: ListState
        setContent {
            state = rememberListState()
            ListBox(items = items, state = state)
        }

        assertEquals(names.size, state.itemCount, "every declared item is one the list shows")

        items = names.dropLast(1)
        awaitIdle()

        assertEquals(names.size - 1, state.itemCount, "an item taken away is one the list no longer shows")
    }

    @Test
    fun anUnboundStateAnswersForNoList() {
        val state = ListState(initialSelectedIndices = setOf(0, 1))

        assertEquals(0, state.itemCount, "a state with no list has no items to report")
        assertEquals(emptySet(), state.shownSelectedIndices, "nor a selection")
    }

    @Test
    fun aMultiRowSelectionTheListGrantsIsReportedWhole() = runComposeSwingTest {
        lateinit var state: ListState
        setContent {
            state = rememberListState(initialSelectedIndices = setOf(0, 2))
            ListBox(items = names, state = state)
        }
        awaitIdle()

        assertEquals(
            setOf(0, 2),
            state.shownSelectedIndices,
            "a list that holds every declared row reports every one",
        )
        assertEquals(
            listOf(0, 2),
            onNodeOfType<JList<*>>().fetch<JList<*>>().selectedIndices.toList(),
            "which is what the list stands on",
        )
    }

    @Test
    fun aDeclaredRowTheListDoesNotHoldIsNotReportedSelected() = runComposeSwingTest {
        lateinit var state: ListState
        setContent {
            state = rememberListState(initialSelectedIndices = setOf(0, 9))
            ListBox(items = names, state = state)
        }
        awaitIdle()

        assertEquals(setOf(0, 9), state.selectedIndices, "the declaration stands as it was made")
        assertEquals(
            setOf(0),
            state.shownSelectedIndices,
            "only the row the list holds can be selected on it",
        )
    }

    @Test
    fun aSelectionModeNarrowerThanTheDeclarationReportsWhatSurvived() = runComposeSwingTest {
        lateinit var state: ListState
        setContent {
            state = rememberListState(initialSelectedIndices = setOf(0, 1, 2))
            ListBox(
                items = names,
                state = state,
                selectionMode = ListSelectionModel.SINGLE_SELECTION,
            )
        }
        awaitIdle()

        assertEquals(setOf(0, 1, 2), state.selectedIndices, "the declaration stands as it was made")
        assertEquals(
            1,
            state.shownSelectedIndices.size,
            "a list that holds one selected row keeps one of the three: ${state.shownSelectedIndices}",
        )
        assertNotEquals(
            state.selectedIndices,
            state.shownSelectedIndices,
            "what the list shows is not what was declared to it",
        )
    }
}
