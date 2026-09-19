package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.core.CompositionRecorder
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.core.labelTexts
import org.jetbrains.compose.swing.core.realizedFrame
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A window-move regression for a [Spinner] with a composed [Spinner.editor]. Its editor panel joins
 * the spinner's captured composition context directly through `setContent(parent = ...)`. Per §2.6, a
 * window move keeps a composition standing wherever its named parent is unchanged, so a page carrying
 * a spinner whole between two windows must leave the editor's composition running rather than rebuilding
 * it - which is what keeps an edit typed but not yet committed from being thrown away by the move.
 *
 * Modeled on [org.jetbrains.compose.swing.core.SetContentMoveTest]'s explicit-parent case, scoped to
 * the spinner's composed editor.
 */
class SpinnerEditorWindowMoveTest {
    @Test
    fun aSpinnersComposedEditorKeepsAnEditInProgressAcrossAWindowMove() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        try {
            // A page built on a recomposer of its own, standing in the first window - the shape a real
            // caller builds a spinner under, on its own recomposer rather than the window's shared one.
            val page = JPanel().also { first.contentPane.add(it) }
            val recorder = CompositionRecorder()
            // Stands in for an edit in progress: text the editor's composition renders, held outside the
            // composition the way a caller's own state would be.
            var edit by mutableStateOf("v0")
            val pageHandle =
                page.setContent(parent = recomposer.compositionContext) {
                    Spinner(value = 1, onValueChange = {}) {
                        recorder.Read()
                        Label(text = edit)
                    }
                }

            awaitUntil("the composed editor renders the edit in progress") {
                labelTexts(page) == listOf("v0")
            }
            val composedOnce = recorder.remembered

            // The whole page - spinner, node and editor alike - moves from the first window to the
            // second. The editor's composition keeps the same explicitly named parent, so the rejoin
            // catches the window up without recomposing the editor from scratch.
            second.contentPane.add(page)
            second.pack()
            repeat(8) { yield() }

            assertSame(
                composedOnce,
                recorder.remembered,
                "the composed editor must keep its composition across a move that leaves its explicit " +
                    "parent unchanged - only the window around it moved",
            )
            assertEquals(
                listOf("v0"),
                labelTexts(page),
                "the editor must still hold the edit in progress it composed before the move",
            )

            // The composition is still live and driven after the move: an edit in progress keeps
            // recomposing rather than being frozen by it.
            edit = "v1"
            awaitUntil("the editor recomposes after the move") {
                labelTexts(page) == listOf("v1")
            }

            pageHandle.dispose()
        } finally {
            recomposer.dispose()
            second.dispose()
            first.dispose()
        }
    }
}
