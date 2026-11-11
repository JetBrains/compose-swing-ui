// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import kotlin.test.assertTrue

internal suspend fun ComposeSwingTest.openSection(title: String) {
    setContent { ShowcaseShell() }
    selectSection(title)
}

/** Picks [title] in the sidebar of a shell that is already composed. */
internal suspend fun ComposeSwingTest.selectSection(title: String) {
    val index = showcaseSections.indexOfFirst { it.title == title }
    assertTrue(index >= 0, "Section \"$title\" must be registered in showcaseSections")
    onNode(SwingMatcher.hasAccessibleName("Sections")).performAddSelection(index)
    onNodeWithText("Section: $title", substring = true).assertExists()
}
