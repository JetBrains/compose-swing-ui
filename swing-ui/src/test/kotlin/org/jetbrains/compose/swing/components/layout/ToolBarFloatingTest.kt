package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicToolBarUI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [ToolBar]'s `floating` is two-way: it puts the bar into a window of its own or brings it back, and
 * `onFloatingChange` reports what the bar did that the caller did not ask for - a declaration the bar
 * could not take, and a bar the user dragged out or docked themselves. A declaration the bar takes is
 * not reported back, since the caller already holds it.
 *
 * Floating needs a window to open the bar's own beside, so the refusal is measured off-screen and every
 * case that really floats realizes a frame, and therefore skips on a headless environment.
 */
class ToolBarFloatingTest {
    @Test
    fun aBarWithNoWindowStaysDockedAndReportsThat() = runComposeSwingTest {
        val reported = mutableListOf<Boolean>()
        setContent {
            ToolBar(floating = true, onFloatingChange = { reported += it }) {
                Button(text = "New", onClick = {})
            }
        }

        val bar = onNodeOfType<JToolBar>().fetch()
        assertFalse(bar.isFloatingNow, "a bar with no window to open one beside cannot float")
        assertEquals(
            listOf(false),
            reported.distinctConsecutive(),
            "the bar hands back the docked state it settled for rather than leaving the caller to " +
                "believe its declaration stands",
        )
    }

    @Test
    fun aDeclaredFloatMovesTheBarOutAndWithdrawingItDocksItBack() = onARealizedFrame { frame, island ->
        val reported = mutableListOf<Boolean>()
        var floating by mutableStateOf(false)
        island.setContent {
            ToolBar(floating = floating, onFloatingChange = { reported += it }) {
                Button(text = "New", onClick = {})
            }
        }
        frame.isVisible = true
        awaitUntil("the bar has mounted") { toolBarIn(frame) != null }
        assertFalse(requireNotNull(toolBarIn(frame)).isFloatingNow, "a bar declared docked starts docked")

        floating = true
        awaitUntil("the bar floats") { toolBarIn(frame)?.isFloatingNow == true }

        floating = false
        awaitUntil("the bar docks again") { toolBarIn(frame)?.isFloatingNow == false }

        assertEquals(
            emptyList(),
            reported,
            "a declaration the bar takes is the caller's own, so nothing is reported back to it",
        )
    }

    @Test
    fun aBarDeclaredFloatingBeforeItStandsInAWindowFloatsWithoutReportingARefusal() =
        onARealizedFrame { frame, island ->
            val reported = mutableListOf<Boolean>()
            island.setContent {
                ToolBar(floating = true, onFloatingChange = { reported += it }) {
                    Button(text = "New", onClick = {})
                }
            }
            frame.isVisible = true

            // The bar is declared floating on the pass that inserts it, before it is in a window to open
            // one beside. That is a declaration still to be taken, not one refused.
            awaitUntil("the bar floats once it stands in a window") { toolBarIn(frame)?.isFloatingNow == true }
            assertEquals(
                emptyList(),
                reported,
                "a declaration the bar takes as soon as it can is the caller's own, so nothing is " +
                    "reported back to it - least of all a refusal it then contradicts",
            )
        }

    @Test
    fun theUserFloatingTheBarIsReportedAndSnapsBackWhileTheDeclarationStandsDocked() =
        onARealizedFrame { frame, island ->
            val reported = mutableListOf<Boolean>()
            island.setContent {
                ToolBar(floating = false, onFloatingChange = { reported += it }) {
                    Button(text = "New", onClick = {})
                }
            }
            frame.isVisible = true
            awaitUntil("the bar has mounted") { toolBarIn(frame) != null }

            // What the look and feel's own drag handler does when the user pulls the bar out of its
            // container, run here directly since no pointer is dragging it.
            val bar = requireNotNull(toolBarIn(frame))
            (bar.ui as BasicToolBarUI).setFloating(true, null)

            awaitUntil("the move reaches the caller") { reported.isNotEmpty() }
            assertEquals(
                listOf(true),
                reported.distinctConsecutive(),
                "a move the composition did not make is the user's, and the caller is told of it",
            )

            // The declaration still says docked and the caller did not adopt the move, so the next pass
            // writes the declaration back - the contract every controlled value here follows.
            awaitUntil("the unadopted move is undone") { toolBarIn(frame)?.isFloatingNow == false }
            assertFalse(
                requireNotNull(toolBarIn(frame)).isFloatingNow,
                "an unadopted move snaps back to the standing declaration",
            )
        }

    @Test
    fun leavingTheCompositionWhileFloatingDisposesTheWindowItMovedInto() = onARealizedFrame { frame, island ->
        var present by mutableStateOf(true)
        island.setContent {
            if (present) {
                ToolBar(floating = true) {
                    Button(text = "New", onClick = {})
                }
            }
        }
        frame.isVisible = true
        awaitUntil("the bar floats") { toolBarIn(frame)?.isFloatingNow == true }
        val floatingWindow = requireNotNull(SwingUtilities.getWindowAncestor(requireNotNull(toolBarIn(frame))))

        present = false
        awaitUntil("the window the bar moved into is disposed") { !floatingWindow.isDisplayable }

        assertFalse(
            floatingWindow.isDisplayable,
            "the window the look and feel moved the bar into should be disposed once the bar leaves the composition",
        )
    }

    /**
     * Runs [body] under Metal - whose tool bars drag, which a look and feel need not do - against a
     * realized frame and an island inside it, disposing the frame on every exit path.
     */
    private fun onARealizedFrame(body: suspend (JFrame, Container) -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        underMetal {
            runBlocking(Dispatchers.Swing) {
                val frame =
                    JFrame().apply {
                        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                        setBounds(0, 0, FRAME_SIZE, FRAME_SIZE)
                        pack()
                    }
                try {
                    body(frame, JPanel().also { frame.contentPane.add(it) })
                } finally {
                    toolBarIn(frame)?.takeIf { it.isFloatingNow }?.let {
                        SwingUtilities.getWindowAncestor(it)?.dispose()
                    }
                    frame.dispose()
                }
            }
        }
    }

    /** Whether the bar stands in a window of its own, read the way the wrapper itself reads it. */
    private val JToolBar.isFloatingNow: Boolean
        get() = (ui as? BasicToolBarUI)?.isFloating == true

    /**
     * The single [JToolBar] belonging to [frame], wherever it currently stands: a floating bar has left
     * the frame's own tree for a window the look and feel opens, which it owns from [frame].
     *
     * The search stays within [frame] and the windows it owns, so a bar another case left standing in
     * this JVM is never mistaken for this one's.
     */
    private fun toolBarIn(frame: JFrame): JToolBar? {
        val bars = mutableListOf<JToolBar>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JToolBar) bars += child
                if (child is Container) visit(child)
            }
        }
        visit(frame)
        frame.ownedWindows.forEach { visit(it) }
        return bars.firstOrNull()
    }

    /** Collapses runs of the same reported value, which re-settling on an unchanged state produces. */
    private fun List<Boolean>.distinctConsecutive(): List<Boolean> =
        filterIndexed { index, value -> index == 0 || this[index - 1] != value }

    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT_MILLIS) {
                while (!condition()) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after ${SETTLE_TIMEOUT_MILLIS}ms waiting until $description", timedOut)
        }
    }

    private companion object {
        const val FRAME_SIZE: Int = 200
        const val SETTLE_TIMEOUT_MILLIS: Long = 10_000
    }
}
