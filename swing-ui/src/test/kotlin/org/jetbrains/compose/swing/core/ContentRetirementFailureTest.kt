package org.jetbrains.compose.swing.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContentRetirementFailureTest {
    @Test
    fun runtimeFailureRetiresEveryContentAndSuppressesLaterDisposalFailuresInOrder() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val window = JFrame()
        val failures = List(3) { IllegalStateException("removal-$it") }
        val panels = List(4) { FailingRemovalPanel(failures.getOrNull(it)) }
        val handles = mutableListOf<DisposableHandle>()
        val retired = mutableListOf<Int>()
        var text by mutableStateOf("before")
        try {
            panels.forEachIndexed { index, panel ->
                window.contentPane.add(panel)
                handles +=
                    panel.setContent {
                        DisposableEffect(Unit) { onDispose { retired += index } }
                        Label(text)
                    }
            }
            val labels = panels.map { it.components.single() as JLabel }
            val reported =
                reportedUncaught {
                    assertNotNull(window.findRecomposer()).cancel()
                    awaitUntil("runtime teardown retires every content") { retired.size == panels.size }
                }

            assertSame(failures[0], reported.single())
            assertEquals(failures.drop(1), reported.single().suppressed.toList())
            assertEquals(listOf(0, 1, 2, 3), retired)
            assertEquals(List(4) { 1 }, panels.map { it.detachments })
            assertEquals(List(4) { 0 }, panels.map { it.componentCount })
            assertNull(window.findRecomposer())
            text = "after"
            assertEquals(List(4) { "before" }, labels.map { it.text }, "the retired content's labels keep their text")
            handles.forEach { it.dispose() }
            assertEquals(listOf(0, 1, 2, 3), retired)
            assertEquals(List(4) { 1 }, panels.map { it.detachments })
        } finally {
            handles.forEach { it.dispose() }
            window.dispose()
        }
    }
}
