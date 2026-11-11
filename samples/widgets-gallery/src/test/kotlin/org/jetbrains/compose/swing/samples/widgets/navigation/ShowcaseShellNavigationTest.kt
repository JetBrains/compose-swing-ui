// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.navigation

import org.jetbrains.compose.swing.samples.widgets.ShowcaseShell
import org.jetbrains.compose.swing.samples.widgets.showcaseSections
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gallery shell as it is actually navigated: an `androidx.navigation3` back stack behind a sidebar,
 * a detail region showing only the top entry, and a Back button popping it.
 *
 * These pin the behaviors the back stack adds over a plain index - history, a revisit, and the sidebar
 * following a pop - and the one it takes away, which is the live state of the section left behind.
 */
class ShowcaseShellNavigationTest {
    private companion object {
        const val COMPONENTS = "Components"
        const val TABLE = "Table"
        const val TREE = "Tree"
    }

    private suspend fun ComposeSwingTest.select(title: String) {
        onNode(SwingMatcher.hasAccessibleName("Sections"))
            .performAddSelection(showcaseSections.indexOfFirst { it.title == title })
    }

    @Test
    fun backReturnsToThePreviousSectionAndTheSidebarFollows() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            onAllNodesOfType<JList<*>>()
                .filterToOne(SwingMatcher.hasAccessibleName("Sections"))
                .assertProperty(showcaseSections.first().title) { showcaseSections[selectedIndex].title }
            onNodeWithText("Back").assertIsNotEnabled()

            select(TABLE)
            onNodeWithText("Section: $TABLE", substring = true).assertExists()
            onNodeWithText("Back").assertIsEnabled()

            select(TREE)
            onNodeWithText("Back").performClick()
            awaitIdle()

            onNodeWithText("Section: $TABLE", substring = true).assertExists()
            onAllNodesOfType<JList<*>>()
                .filterToOne(SwingMatcher.hasAccessibleName("Sections"))
                .assertProperty(TABLE, "the sidebar did not follow the pop") { showcaseSections[selectedIndex].title }
        }

    /**
     * The sidebar is an `AnimatedVisibility` container in the west region, so hiding it shrinks the
     * region a frame at a time and then takes the list out of the Swing tree entirely; showing it puts
     * the list back in that region, still driving the back stack.
     */
    @Test
    fun theSidebarShrinksAwayAndComesBackNavigating() =
        runComposeSwingTest {
            setContent { ShowcaseShell() }

            val shown = onNode(SwingMatcher.hasAccessibleName("Sections")).fetch<Component>()
            val shownWidth = shown.animatedContainer().width
            assertTrue(shownWidth > 0, "the sidebar was laid out with no width")
            val sidebar = shown.sidebarScrollPane()
            assertEquals(
                sidebar.parent.height,
                sidebar.height,
                "the sidebar did not fill the region the animated container was given",
            )

            mainClock.autoAdvance = false
            onNodeWithText("Hide sidebar").performClick()
            val sections = onNode(SwingMatcher.hasAccessibleName("Sections"))
            mainClock.advanceTimeUntil { sections.fetch<Component>().animatedContainer().width < shownWidth }
            sections.assertProperty(true, "the sidebar jumped straight to nothing") {
                animatedContainer().width in 1 until shownWidth
            }

            mainClock.autoAdvance = true
            awaitIdle()
            onNode(SwingMatcher.hasAccessibleName("Sections")).assertDoesNotExist()

            onNodeWithText("Show sidebar").performClick()
            awaitIdle()
            select(TABLE)
            onNodeWithText("Section: $TABLE", substring = true).assertExists()
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
            val initial = field.text
            val typed = "$initial-edited"
            field.text = typed
            awaitIdle()
            assertEquals(typed, field.text, "the edit is in the field before navigating away")

            select(TABLE)
            onNodeWithText("Back").performClick()
            awaitIdle()

            val afterReturn = onAllNodesOfType<JTextField>().fetchAll().first { it.isEditable }
            assertTrue(afterReturn !== field, "the section's component survived navigation")
            assertEquals(
                initial,
                afterReturn.text,
                "the rebuilt field starts from its declared text, not the typed one",
            )
        }
}

// The list sits in a scroll pane, in the container whose bounds the size transition animates. The scroll
// pane keeps the width it asks for throughout and travels leftwards inside the container, which is what
// the shrink looks like.
private fun Component.sidebarScrollPane(): JScrollPane =
    generateSequence(parent) { it.parent }.filterIsInstance<JScrollPane>().first()

private fun Component.animatedContainer(): Component = sidebarScrollPane().parent
