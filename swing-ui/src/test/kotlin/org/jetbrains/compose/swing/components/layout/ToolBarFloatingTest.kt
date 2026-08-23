package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.core.compositionContext
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicToolBarUI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * [ToolBar]'s `floating` is two-way: it puts the bar into a window of its own or brings it back, and
 * `onFloatingChange` reports what the bar did that the caller did not ask for - a declaration the bar
 * could not take, and a bar the user dragged out or docked themselves. A declaration the bar takes is
 * not reported back, since the caller already holds it.
 *
 * Floating needs a window to open the bar's own beside, so the refusal is measured off-screen and every
 * case that really floats realizes a frame, and therefore skips on a headless environment. A refusal is
 * not the end of the declaration behind it: a bar moved into a window takes the one that stood while it
 * had none.
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
            reported,
            "the bar hands back the docked state it settled for rather than leaving the caller to " +
                "believe its declaration stands",
        )
    }

    @Test
    fun aDeclaredFloatMovesTheBarOutAndWithdrawingItDocksItBack() = onARealizedFrame { frame, mount ->
        val reported = mutableListOf<Boolean>()
        var floating by mutableStateOf(false)
        mount {
            ToolBar(floating = floating, onFloatingChange = { reported += it }) {
                Button(text = "New", onClick = {})
            }
        }
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
    fun aBarDeclaredFloatingBeforeItStandsInAWindowFloatsWithoutReportingARefusal() = onARealizedFrame { frame, mount ->
        val reported = mutableListOf<Boolean>()
        mount {
            ToolBar(floating = true, onFloatingChange = { reported += it }) {
                Button(text = "New", onClick = {})
            }
        }

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
        onARealizedFrame { frame, mount ->
            val reported = mutableListOf<Boolean>()
            mount {
                ToolBar(floating = false, onFloatingChange = { reported += it }) {
                    Button(text = "New", onClick = {})
                }
            }
            awaitUntil("the bar has mounted") { toolBarIn(frame) != null }

            // What the look and feel's own drag handler does when the user pulls the bar out of its
            // container, run here directly since no pointer is dragging it.
            val bar = requireNotNull(toolBarIn(frame))
            (bar.ui as BasicToolBarUI).setFloating(true, null)

            awaitUntil("the move reaches the caller") { reported.isNotEmpty() }
            assertEquals(
                listOf(true),
                reported,
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
    fun aBarRefusedAFloatTakesItOnceItComesToStandInAWindow() =
        onARealizedFrameAndADetachedComposition { frame, composition, mount ->
            val reported = mutableListOf<Boolean>()
            mount {
                ToolBar(floating = true, onFloatingChange = { reported += it }) {
                    Button(text = "New", onClick = {})
                }
            }
            // Composed into a composition standing in no window, so the bar has none to open its own beside
            // and settles docked.
            awaitUntil("the refusal reaches the caller") { reported.isNotEmpty() }
            assertEquals(
                listOf(false),
                reported,
                "a bar with no window to float out of should hand the caller the docked state it " +
                    "settled for",
            )
            assertNull(toolBarIn(frame), "the bar hangs in a composition the frame does not hold")

            frame.contentPane.add(composition)
            frame.validate()

            awaitUntil("the bar floats once it stands in a window") { toolBarIn(frame)?.isFloatingNow == true }
            assertEquals(
                listOf(false),
                reported,
                "the refusal is the last thing the caller hears: the declaration it made is the one " +
                    "the bar ends up taking",
            )
        }

    @Test
    fun leavingTheCompositionWhileFloatingDisposesTheWindowItMovedInto() = onARealizedFrame { frame, mount ->
        var present by mutableStateOf(true)
        mount {
            if (present) {
                ToolBar(floating = true) {
                    Button(text = "New", onClick = {})
                }
            }
        }
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
     * Runs [body] against a realized frame already holding the composition, handing it the mount that
     * composes into that composition.
     */
    private fun onARealizedFrame(body: suspend (JFrame, (@Composable () -> Unit) -> Unit) -> Unit) =
        onARealizedFrameAndADetachedComposition { frame, composition, mount ->
            frame.contentPane.add(composition)
            frame.validate()
            body(frame, mount)
        }

    /**
     * Runs [body] under Metal - whose tool bars drag, which a look and feel need not do - against a
     * realized frame, handing it that frame, a composition standing in no window yet, and the mount that
     * composes into that composition. A body that wants the composition in the frame puts it there itself, which
     * is what makes where a bar stands something a case can decide.
     *
     * The mount names the frame's composition, which is what lets a case compose into a composition that
     * hangs nowhere: a mount left to resolve its own parent waits for its container to reach a window,
     * so a composition standing in none would compose nothing at all. A composition already in the frame
     * resolves to that same composition, so naming it changes nothing for a case that puts it there
     * first.
     *
     * The composition is ended before the frame is, on every exit path. The look and feel is restored
     * process-wide once this returns, which a bar left composing would answer by re-applying its
     * declaration to a frame that is already gone - floating one packs a window, which realizes both it
     * and the frame owning it again.
     */
    private fun onARealizedFrameAndADetachedComposition(
        body: suspend (JFrame, JPanel, (@Composable () -> Unit) -> Unit) -> Unit,
    ) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        underMetal {
            runSwingTest {
                val frame =
                    JFrame().apply {
                        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                        setBounds(0, 0, FRAME_SIZE, FRAME_SIZE)
                        pack()
                    }
                val composition = JPanel()
                // Shown before anything composes into it: a bar that floats while its frame is still
                // hidden leaves the look and feel waiting on the frame's opening to show the bar's own
                // window, and that pending show would realize a window this teardown has already disposed.
                frame.isVisible = true
                var mounted: DisposableHandle? = null
                try {
                    body(frame, composition) { content ->
                        mounted = composition.setContent(parent = frame.compositionContext(), content = content)
                    }
                } finally {
                    // Ends the composition first: the bar leaving it disposes the window a floating bar
                    // stands in, which the line below can then only find where the wrapper never floated
                    // the bar itself - the case where the drag handler was driven directly.
                    mounted?.dispose()
                    toolBarIn(frame)?.takeIf { it.isFloatingNow }?.let {
                        SwingUtilities.getWindowAncestor(it)?.dispose()
                    }
                    frame.dispose()
                }
            }
        }
    }

    private suspend fun awaitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(SETTLE_TIMEOUT) {
                while (!condition()) {
                    yield()
                }
            }
        } catch (timedOut: TimeoutCancellationException) {
            throw AssertionError("Timed out after $SETTLE_TIMEOUT waiting until $description", timedOut)
        }
    }

    private companion object {
        const val FRAME_SIZE: Int = 200
        val SETTLE_TIMEOUT = 10.seconds
    }
}
