package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral tests for the declarative glass pane of a [Window] and a [Dialog]: the declared content
 * is realized on the window's own glass pane and shown there, the overlay keeps following the state
 * that drives it, and letting the declaration leave the composition hands the window back the pane it
 * carried before - the root pane's own on a window the library realizes, the caller's own on a window
 * they provide, shown as it was shown then.
 *
 * A window carries one glass pane, so the two shapes that reach one are pinned apart: a declaration
 * arriving after another has left is served, while two declarations composed for the same window at
 * once are answered.
 *
 * A glass pane sits beside the content pane rather than in it, so the overlay is read off the root
 * pane the window hands its scope, and a window-scoped node query - which targets the content pane -
 * answers that the overlay is over the window rather than in it.
 *
 * A window the library realizes lives and dies with the composition that declares it, so the cases
 * about a window outliving that composition - one already carrying a pane of its own - host the
 * content over a window of the test's own, the way a window's own host does.
 *
 * A glass pane lives on a realized peer's root pane, so these are skipped in headless environments.
 */
class WindowGlassPaneTest {
    @Test
    fun aDeclaredGlassPaneIsPutOverTheWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "glass-pane-window", visible = false) {
                Label(text = "beneath")
                GlassPane { Label(text = "overlay") }
            }
        }

        val window = onWindowWithTitle("glass-pane-window")
        val pane = window.fetch<JFrame>().rootPane.glassPane
        assertEquals(
            listOf("overlay"),
            pane.labelTexts(),
            "the declared content should fill the window's glass pane",
        )
        assertTrue(pane.isVisible, "a declared glass pane should be shown, so the overlay is over the window")
        assertFalse(
            (pane as JComponent).isOpaque,
            "the pane should be transparent, so the window shows through wherever the overlay paints nothing",
        )
        window.onNodeWithText("beneath").assertExists()
        window.onNodeWithText("overlay").assertDoesNotExist()
    }

    @Test
    fun theGlassPaneContentFollowsTheStateDrivingIt() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var status by mutableStateOf("loading")
        setContent {
            Window(onCloseRequest = {}, title = "glass-pane-reactive", visible = false) {
                GlassPane { Label(text = status) }
            }
        }

        val pane = onWindowWithTitle("glass-pane-reactive").fetch<JFrame>().rootPane.glassPane
        assertEquals(listOf("loading"), pane.labelTexts(), "the overlay should start at the declared label")

        status = "ready"
        awaitIdle()
        assertEquals(
            listOf("ready"),
            pane.labelTexts(),
            "the overlay should follow the state driving its label",
        )
    }

    @Test
    fun aGlassPaneLeavingTheCompositionHandsTheWindowBackThePaneItCarried() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var overlaid by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "glass-pane-withdrawn", visible = false) {
                Label(text = "beneath")
                if (overlaid) {
                    GlassPane { Label(text = "overlay") }
                }
            }
        }

        val frame = onWindowWithTitle("glass-pane-withdrawn").fetch<JFrame>()
        val ownPane = frame.rootPane.glassPane
        assertFalse(ownPane.isVisible, "a root pane carries its own glass pane unshown")

        overlaid = true
        awaitIdle()
        val declaredPane = frame.rootPane.glassPane
        assertNotSame(ownPane, declaredPane, "declaring a glass pane should put the declaration's pane on the window")
        assertEquals(listOf("overlay"), declaredPane.labelTexts(), "the arriving pane should carry the overlay")

        overlaid = false
        awaitIdle()
        assertSame(
            ownPane,
            frame.rootPane.glassPane,
            "a glass pane that leaves the composition should hand the window back the pane it carried",
        )
        assertFalse(
            frame.rootPane.glassPane.isVisible,
            "the pane handed back should be shown as it was shown before the declaration",
        )
    }

    @Test
    fun aDeclaredGlassPaneIsPutOverTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Dialog(onCloseRequest = {}, title = "glass-pane-dialog", visible = false) {
                GlassPane { Label(text = "dialog overlay") }
            }
        }

        val pane = onWindowWithTitle("glass-pane-dialog").fetch<JDialog>().rootPane.glassPane
        assertEquals(
            listOf("dialog overlay"),
            pane.labelTexts(),
            "a dialog should carry a declared glass pane as a window does",
        )
        assertTrue(pane.isVisible, "a dialog's declared glass pane should be shown")
    }

    @Test
    fun theGlassPaneContentReachesStateHoistedAroundTheWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var dismissed by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "glass-pane-hoisted", visible = false) {
                Label(text = "dismissed: $dismissed")
                if (!dismissed) {
                    GlassPane { Button(text = "Dismiss", onClick = { dismissed = true }) }
                }
            }
        }

        val window = onWindowWithTitle("glass-pane-hoisted")
        val frame = window.fetch<JFrame>()
        frame.rootPane.glassPane
            .singleButton()
            .doClick()
        awaitIdle()

        assertTrue(dismissed, "the overlay's callback should reach state hoisted around the window")
        // The window's own content reads the same state, so the label the content declares carries what
        // the overlay wrote.
        window.onNodeWithText("dismissed: true").assertExists()
        assertFalse(
            frame.rootPane.glassPane.isVisible,
            "the overlay dismissing itself should leave the window the pane it carried before",
        )
    }

    @Test
    fun aSecondGlassPaneComposedForTheSameWindowSaysSo() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val failure =
            runCatching {
                setContent {
                    Window(onCloseRequest = {}, title = "glass-pane-doubled", visible = false) {
                        GlassPane { Label(text = "first") }
                        GlassPane { Label(text = "second") }
                    }
                }
            }.exceptionOrNull()

        val message = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        assertTrue(
            "GlassPane { }" in message,
            "a second glass pane for one window should name the declaration it collides with, was: $failure",
        )
        assertTrue(
            "one glass pane" in message,
            "the message should say what a window can carry, was: $failure",
        )
    }

    @Test
    fun aRefusedSecondDeclarationLeavesTheWindowThePaneItCarried() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val ownPane = JPanel()
        val frame = JFrame("glass-pane-refused").apply { rootPane.glassPane = ownPane }
        val scope = WindowScope.of(frame.rootPane)
        try {
            runComposeSwingTest {
                runCatching {
                    setContent {
                        with(scope) {
                            GlassPane { Label(text = "first") }
                            GlassPane { Label(text = "second") }
                        }
                    }
                }

                // Answering the second declaration ends the composition, so neither declaration is left
                // serving the window: the window is handed back the pane it came with rather than one a
                // failed composition put on it.
                assertSame(
                    ownPane,
                    frame.rootPane.glassPane,
                    "a window whose composition was refused carries the pane it carried before it",
                )
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aSingleDeclarationAfterARefusedCompositionServesTheWindow() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame("glass-pane-retried")
        val scope = WindowScope.of(frame.rootPane)
        try {
            runComposeSwingTest {
                runCatching {
                    setContent {
                        with(scope) {
                            GlassPane { Label(text = "first") }
                            GlassPane { Label(text = "second") }
                        }
                    }
                }
            }

            // One declaration for a window is a legal state whatever came before it, so a window that
            // outlives a refused composition still serves one declared afterwards.
            runComposeSwingTest {
                setContent {
                    with(scope) {
                        GlassPane { Label(text = "composed") }
                    }
                }

                assertEquals(
                    listOf("composed"),
                    frame.rootPane.glassPane.labelTexts(),
                    "one glass pane declared for a window should be served",
                )
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCompositionDisposedWithItsGlassPaneStillDeclaredLeavesTheWindowItsOwnPane() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var ownPane: JPanel? = null
        var frame: JFrame? = null
        try {
            runComposeSwingTest {
                val pane = JPanel()
                val window = JFrame("glass-pane-disposed").apply { rootPane.glassPane = pane }
                ownPane = pane
                frame = window
                setContent {
                    with(WindowScope.of(window.rootPane)) {
                        GlassPane { Label(text = "composed") }
                    }
                }

                assertEquals(
                    listOf("composed"),
                    window.rootPane.glassPane.labelTexts(),
                    "a declared glass pane reaches the window whose scope it is declared on",
                )
            }

            // The declaration is still composed when the composition ends, so the window a caller owns -
            // which outlives that composition - is left the pane it came with.
            assertSame(
                ownPane,
                frame?.rootPane?.glassPane,
                "a disposed composition leaves the window the pane it carried",
            )
        } finally {
            frame?.dispose()
        }
    }

    @Test
    fun aGlassPaneArrivingOnAShownWindowCoversIt() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val state = WindowState(size = Dimension(320, 240))
        var overlaid by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, state = state, title = "glass-pane-shown") {
                Label(text = "beneath")
                if (overlaid) {
                    GlassPane { Label(text = "overlay") }
                }
            }
        }

        val frame = onWindowWithTitle("glass-pane-shown").fetch<JFrame>()
        overlaid = true
        awaitIdle()

        // A root pane sizes its glass pane to the whole window as it lays itself out, so an arriving
        // pane is given those bounds once the root pane has been laid out again.
        val pane = frame.rootPane.glassPane
        waitUntil(timeout = LAYOUT_PASS_TIMEOUT) { pane.size == frame.rootPane.size }
        assertEquals(
            frame.rootPane.size,
            pane.size,
            "the arriving pane should cover the whole window rather than a part of it",
        )
        assertTrue(pane.width > 0 && pane.height > 0, "the covering pane should be given the window's real bounds")
    }
}

/** The texts of every label below this component, in the order the tree holds them. */
private fun Component.labelTexts(): List<String> = componentsBelow().filterIsInstance<JLabel>().map { it.text }.toList()

/** The single button below this component. */
private fun Component.singleButton(): JButton = componentsBelow().filterIsInstance<JButton>().single()

/** Every component below this one, in depth-first order and excluding this one. */
private fun Component.componentsBelow(): Sequence<Component> = sequence {
    val children = (this@componentsBelow as? Container)?.components?.toList().orEmpty()
    for (child in children) {
        yield(child)
        yieldAll(child.componentsBelow())
    }
}

/**
 * Wall-clock deadline for a layout pass a root pane schedules for itself, which a realized window runs
 * off its own event queue.
 */
private val LAYOUT_PASS_TIMEOUT = 10.seconds
