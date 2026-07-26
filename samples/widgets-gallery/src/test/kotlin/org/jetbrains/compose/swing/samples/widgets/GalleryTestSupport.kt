package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import javax.swing.JList
import kotlin.test.assertTrue

internal suspend fun ComposeSwingTest.openSection(title: String) {
    setContent { ShowcaseShell() }
    val index = showcaseSections.indexOfFirst { it.title == title }
    assertTrue(index >= 0, "Section \"$title\" must be registered in showcaseSections")
    val list = onSectionList().fetch<JList<*>>()
    list.selectedIndex = index
    awaitIdle()
    onNodeWithText("Section: $title", substring = true).assertExists()
}

internal fun ComposeSwingTest.onSectionList() = onNode(SwingMatcher.hasAccessibleName("Sections"))
