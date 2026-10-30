package org.jetbrains.compose.swing.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * Behavioral tests for content mounted inside content that names its parent, when the two move to another
 * window together.
 *
 * Every case realizes a real top-level peer, so each skips (reports SKIPPED) on a headless environment.
 */
class SetContentNestedMoveTest {
    @Test
    fun contentMountedInsideContentNamingAWindowKeepsItsCompositionWhenTheyMoveTogether() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            var innerDisposed = false
            val named = soleContentNaming(first) { Label(text = "outer") }
            val inner = JPanel().also { named.panel.add(it) }
            val recorder = CompositionRecorder()
            inner.setContent {
                recorder.Read()
                DisposableEffect(Unit) {
                    onDispose { innerDisposed = true }
                }
                Label(text = "inner")
            }
            awaitUntil("the inner content composes") { labelTexts(inner) == listOf("inner") }
            val composedOnce = recorder.remembered

            second.contentPane.add(named.panel)
            second.pack()
            awaitQueuedTurns()

            assertSame(
                composedOnce,
                recorder.remembered,
                "content inside content naming a window must keep its composition when they move together",
            )
            assertFalse(innerDisposed, "the move must not end the inner content")

            first.dispose()

            awaitUntil("closing the named window ends the content mounted inside the moved content") {
                innerDisposed
            }
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun closingTheWindowTheyMovedToEndsContentMountedInsideAndLetsTheNamedWindowEndItsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            var innerDisposed = false
            val named = soleContentNaming(first) { Label(text = "outer") }
            val inner = JPanel().also { named.panel.add(it) }
            inner.setContent {
                DisposableEffect(Unit) {
                    onDispose { innerDisposed = true }
                }
                Label(text = "inner")
            }
            awaitUntil("the inner content composes") { labelTexts(inner) == listOf("inner") }

            second.contentPane.add(named.panel)
            second.pack()
            awaitQueuedTurns()

            assertFalse(innerDisposed, "the move must not end the inner content")

            second.dispose()

            awaitUntil("closing the window they moved to ends the inner content") { innerDisposed }
            awaitUntil("closing the window they moved to ends the outer content") {
                labelTexts(named.panel).isEmpty()
            }
            awaitUntil("the emptied named window ends its recomposer") { first.findRecomposer() == null }
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun contentMountedInsideContentNamingAHostContextOutlivesTheWindowTheyMovedOutOf() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val hostFrame = realizedFrame()
        val first = realizedFrame()
        val second = realizedFrame()
        try {
            val host = hostIn(hostFrame)
            // Gives the first window the recomposer the outer content registers with.
            JPanel().also { first.contentPane.add(it) }.setContent { Label(text = "other") }
            val outer = JPanel().also { first.contentPane.add(it) }
            outer.setContent(parent = host.context) { Label(text = "outer") }
            val inner = JPanel().also { outer.add(it) }
            var text by mutableStateOf("before")
            inner.setContent { Label(text = text) }
            awaitUntil("the inner content composes") { labelTexts(inner) == listOf("before") }

            second.contentPane.add(outer)
            second.pack()
            awaitQueuedTurns()

            first.dispose()
            awaitQueuedTurns()

            assertEquals(listOf("before"), labelTexts(inner), "closing the window they left must not end the content")
            text = "after"
            awaitUntil("the inner content recomposes") { labelTexts(inner) == listOf("after") }
        } finally {
            second.dispose()
            first.dispose()
            hostFrame.dispose()
        }
    }
}
