// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.foreground
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/**
 * The restore check does not report a pane color declaration that changes or is put back while the pane
 * holds tabs. A tab that declares no color reads the pane's.
 *
 * A gate throws what the check reports, so a case that expects nothing passes by reaching its end.
 */
class ModifierRestoreCheckDerivedPropertyTest {
    @TestFactory
    fun aPaneColorChangedOverATabIsNotReportedWhenItLeaves(): List<DynamicTest> = listOf<
        Pair<String, (Color) -> SwingStyle<ContainerStyleScope>>,
    >(
        "background" to { color -> SwingStyle { background(color) } },
        "foreground" to { color -> SwingStyle { foreground(color) } },
    ).flatMap { (property, declaration) ->
        listOf(true, false).map { paneLeaves ->
            dynamicTest("$property, ${if (paneLeaves) "with the pane" else "while the tab stays"}") {
                runComposeSwingTest {
                    var color by mutableStateOf<Color?>(Color.MAGENTA)
                    var shown by mutableStateOf(true)
                    setContent { if (shown) Tabs(color?.let(declaration) ?: SwingStyle) }
                    awaitIdle()

                    color = Color.RED
                    awaitIdle()
                    if (paneLeaves) shown = false else color = null
                    awaitIdle()
                }
            }
        }
    }

    @TestFactory
    fun aBackgroundDeclaredOverTabsLeaves(): List<DynamicTest> = listOf(
        TabsChange("with the pane", tabsWhenDeclared = 1, tabsWhenLeaving = 1, paneLeaves = true),
        TabsChange("after a tab arrived under it", tabsWhenDeclared = 1, tabsWhenLeaving = 2, paneLeaves = false),
        TabsChange("after a tab left under it", tabsWhenDeclared = 2, tabsWhenLeaving = 1, paneLeaves = false),
    ).map { case ->
        dynamicTest(case.name) {
            runComposeSwingTest {
                var declared by mutableStateOf(false)
                var shown by mutableStateOf(true)
                var tabs by mutableIntStateOf(case.tabsWhenDeclared)
                setContent {
                    if (shown) Tabs(if (declared) SwingStyle { background(Color.MAGENTA) } else SwingStyle, tabs)
                }
                awaitIdle()

                declared = true
                awaitIdle()
                tabs = case.tabsWhenLeaving
                awaitIdle()
                if (case.paneLeaves) shown = false else declared = false
                awaitIdle()
            }
        }
    }

    // No read tells a tab's own color from the pane's when they match, so the tab reads as declaring
    // none until the pane's color moves, and then reads as having moved with it.
    @Test
    fun aTabDeclaringTheColorOfThePaneIsReportedOnceThePanesDeclarationLeaves() = runComposeSwingTest {
        var color by mutableStateOf<Color?>(Color.RED)
        setContent {
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = {},
                style = color?.let { SwingStyle { background(it) } } ?: SwingStyle,
            ) {
                Label(text = "page", modifier = SwingModifier.tab("Tab", background = Color.RED))
            }
        }
        awaitIdle()

        color = Color.BLUE
        awaitIdle()
        color = null

        val failure = assertFailsWith<AssertionError> { awaitIdle() }
        assertContains(failure.message.orEmpty(), "tabBackgrounds: found <[null]>, left <[${Color.RED}]>")
    }

    private class TabsChange(
        val name: String,
        val tabsWhenDeclared: Int,
        val tabsWhenLeaving: Int,
        val paneLeaves: Boolean,
    )
}

/** A pane of [tabs] tabs, none of them declaring a color of its own. */
@Composable
private fun Tabs(
    style: SwingStyle<ContainerStyleScope>,
    tabs: Int = 1,
) {
    TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}, style = style) {
        repeat(tabs) { index -> Label(text = "page $index", modifier = SwingModifier.tab("Tab $index")) }
    }
}
