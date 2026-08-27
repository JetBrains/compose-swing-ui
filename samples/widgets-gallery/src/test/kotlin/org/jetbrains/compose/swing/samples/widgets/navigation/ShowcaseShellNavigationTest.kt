package org.jetbrains.compose.swing.samples.widgets.navigation

import org.jetbrains.compose.swing.samples.widgets.ShowcaseShell
import org.jetbrains.compose.swing.samples.widgets.onSectionList
import org.jetbrains.compose.swing.samples.widgets.showcaseSections
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gallery shell as it is actually navigated: an `androidx.navigation3` back stack behind a sidebar,
 * a detail region showing only the top entry, and a Back button popping it.
 *
 * These pin the behaviours the back stack adds over a plain index - history, a revisit, and the sidebar
 * following a pop - and the one it takes away, which is the live state of the section left behind.
 */
class ShowcaseShellNavigationTest {
    private companion object {
        const val COMPONENTS = "Components"
        const val TABLE = "Table"
        const val TREE = "Tree"
    }

    private suspend fun ComposeSwingTest.select(title: String) {
        onSectionList().fetch<JList<*>>().selectedIndex = showcaseSections.indexOfFirst { it.title == title }
        awaitIdle()
    }

    private fun ComposeSwingTest.selectedSection(): String =
        showcaseSections[onSectionList().fetch<JList<*>>().selectedIndex].title

    @Test
    fun backReturnsToThePreviousSectionAndTheSidebarFollows() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            assertEquals(showcaseSections.first().title, selectedSection())
            onNodeWithText("Back").assertIsNotEnabled()

            select(TABLE)
            onNodeWithText("Section: $TABLE", substring = true).assertExists()
            onNodeWithText("Back").assertIsEnabled()

            select(TREE)
            onNodeWithText("Back").performClick()
            awaitIdle()

            onNodeWithText("Section: $TABLE", substring = true).assertExists()
            assertEquals(TABLE, selectedSection(), "the sidebar did not follow the pop")
        }

    /**
     * A revisit puts one `contentKey` on the stack twice - the case a display composing every entry
     * fails on, and the reason only the top entry is composed.
     */
    @Test
    fun aSectionCanBeVisitedTwiceOnOneStack() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            select(TABLE)
            select(COMPONENTS)
            awaitIdle()

            assertTrue(takeCallerFailures().isEmpty(), "revisiting a section failed the composition")
            onNodeWithText("Section: $COMPONENTS", substring = true).assertExists()

            onNodeWithText("Back").performClick()
            awaitIdle()
            onNodeWithText("Section: $TABLE", substring = true).assertExists()
        }

    /**
     * Navigating away drops the outgoing section's declaration, so its components are removed and
     * rebuilt on return. What the user typed into one is gone - the cost the back stack imposes, and
     * the reason a screen with state worth keeping has to hoist it or write it with `rememberSaveable`.
     */
    @Test
    fun aSectionSLiveWidgetStateDoesNotSurviveNavigation() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            select(COMPONENTS)
            val field = onAllNodesOfType<JTextField>().fetchAll().first { it.isEditable }
            val typed = field.text + "-edited"
            field.text = typed
            awaitIdle()

            select(TABLE)
            onNodeWithText("Back").performClick()
            awaitIdle()

            val afterReturn = onAllNodesOfType<JTextField>().fetchAll().first { it.isEditable }
            assertTrue(afterReturn !== field, "the section's component survived navigation")
            assertTrue(afterReturn.text != typed, "the typed text survived, so the section was not rebuilt")
        }
}
