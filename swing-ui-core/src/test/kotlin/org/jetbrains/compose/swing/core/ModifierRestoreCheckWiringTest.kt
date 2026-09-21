// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Restore checks report un-restored declarations in root, renderer, and window compositions.
 * A failed property read is reported while the composition remains able to recompose.
 */
class ModifierRestoreCheckWiringTest {
    @Test
    fun aRestoreLeftUndoneInTheTestsOwnCompositionIsReported() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent { NamedLabel(declared) }

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aRestoreLeftUndoneInACellRenderersCompositionIsReported() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            ListBox(items = listOf("row")) { item -> NamedLabel(declared, item) }
        }
        awaitIdle()

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aRestoreLeftUndoneInAWindowsCompositionIsReported() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var declared by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, visible = false) { NamedLabel(declared) }
        }
        awaitIdle()

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aCellValueThatThrowsWhereTheCheckReadsIsReportedAndLeavesTheCompositionRecomposing() = runComposeSwingTest {
        var failing by mutableStateOf(false)
        var text by mutableStateOf("first")
        setContent {
            Table(
                rows = listOf(if (failing) FAILING_ROW else "row"),
                columns = { column("Name") { row -> if (row == FAILING_ROW) throw CellValueFailure() else row } },
            )
            Label(text)
        }

        failing = true
        val reported = assertFailsWith<AssertionError> { awaitIdle() }
        assertIs<CellValueFailure>(
            reported.cause,
            "the check's own read of the table should report what the caller's cell value threw",
        )

        failing = false
        text = "second"
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            "second",
            "the composition should go on recomposing after a read that threw",
        ) { this.text }
    }
}

/** The row whose cell value throws, standing for any caller code a watched read runs. */
private const val FAILING_ROW = "failing"

private class CellValueFailure : RuntimeException("the caller's own cell value")

/** A label carrying [UnrestoredName] while [declared], and nothing once it is not. */
@Composable
private fun NamedLabel(
    declared: Boolean,
    text: String = "target",
) {
    Label(text, modifier = if (declared) SwingModifier then UnrestoredName() else SwingModifier)
}
