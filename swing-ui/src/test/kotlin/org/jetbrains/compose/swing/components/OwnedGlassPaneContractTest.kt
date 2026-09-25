package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The glass pane declarations of one host's content scope, which the contract tests write against. */
interface GlassPaneHostScope {
    @Composable
    fun GlassPane(
        modifier: SwingModifier = SwingModifier,
        content: @Composable PanelScope.() -> Unit,
    )

    @Composable
    fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier = SwingModifier,
        content: @Composable S.() -> Unit,
    )
}

/**
 * Behavioral tests for what every glass pane declaration promises, whichever component owns the pane: the
 * pane is the host's own, laid out by the layout the declaration names, configured by its modifier and hidden
 * again once the declaration goes away.
 *
 * A subclass supplies the host and the way to find the panes of the hosts composed.
 */
abstract class OwnedGlassPaneContractTest {
    /** Composes a host whose content is [content], declared in the host's own content scope. */
    @Composable
    protected abstract fun Host(content: @Composable GlassPaneHostScope.() -> Unit)

    /** The glass panes of the hosts composed, in composition order. */
    protected abstract fun ComposeSwingTest.glassPanes(): List<Component>

    private fun ComposeSwingTest.glassPane(): Container = assertIs<Container>(glassPanes().single())

    @Test
    fun thePaneReportsAnEventToTheListenerDeclaredOnItOnce() = runComposeSwingTest {
        var presses = 0
        setContent {
            Host {
                GlassPane(
                    modifier = SwingModifier.mouseListener { if (it.id == MouseEvent.MOUSE_PRESSED) presses++ },
                ) {}
            }
        }

        val pane = glassPane()
        pane.dispatchEvent(MouseEvent(pane, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false))

        assertEquals(1, presses)
    }

    @Test
    fun theGlassPaneIsTheHostsOwnLaidOutByItsFlowLayoutByDefault() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Host { if (showPane) GlassPane { Label(text = "hint") } }
        }

        val own = glassPane()
        assertFalse(own.isVisible, "the pane a host builds for itself starts hidden")

        showPane = true
        awaitIdle()

        assertSame(own, glassPane(), "the declaration should show the host's own pane")
        assertTrue(own.isVisible, "a declared pane is shown")
        assertIs<FlowLayout>(own.layout, "without a layout the pane keeps the FlowLayout it is built with")
        assertEquals(listOf("hint"), own.components.map { (it as JLabel).text }, "the content fills the pane")

        showPane = false
        awaitIdle()

        assertSame(own, glassPane(), "leaving keeps the host's own pane in place")
        assertFalse(own.isVisible, "leaving hides the pane again")
        assertEquals(0, own.componentCount, "leaving removes the children the content composed")
    }

    @Test
    fun aPaneDeclaredOnALaidOutHostCoversItAndLaysItsContentOut() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Host { if (showPane) GlassPane(PanelLayout.Border()) { Label(text = "overlay") } }
        }
        val pane = glassPane()
        val host = pane.parent
        assertTrue(host.width > 0 && host.height > 0, "the host should be laid out before the pane arrives")

        showPane = true
        awaitIdle()

        assertEquals(Rectangle(0, 0, host.width, host.height), pane.bounds, "the pane should cover its host")
        val overlay = pane.components.single()
        assertEquals(pane.size, overlay.size, "the pane should lay its content out as it arrives")
    }

    @Test
    fun theModifierReachesThePaneAndIsGivenBackWhenTheDeclarationLeaves() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Host { if (showPane) GlassPane(modifier = SwingModifier.background(Color.RED).visible(false)) {} }
        }

        val pane = glassPane()
        val earlier = pane.background

        showPane = true
        awaitIdle()

        assertEquals(Color.RED, pane.background, "the modifier reaches the pane")
        assertTrue(pane.isVisible, "a declared pane is shown whatever visibility its modifier declares")

        showPane = false
        awaitIdle()

        assertEquals(earlier, pane.background, "leaving gives the pane its earlier background back")
        assertFalse(pane.isVisible, "and hides it again")
    }

    @Test
    fun aModifierChangedWhileThePaneIsDeclaredReachesThePaneAndIsGivenBackWhenItLeaves() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        var background by mutableStateOf(Color.RED)
        setContent {
            Host { if (showPane) GlassPane(modifier = SwingModifier.background(background)) {} }
        }

        val pane = glassPane()
        val earlier = pane.background
        showPane = true
        awaitIdle()
        assertEquals(Color.RED, pane.background, "the modifier first declared reaches the pane")

        background = Color.BLUE
        awaitIdle()

        assertEquals(Color.BLUE, pane.background, "a modifier changed while the pane is declared reaches it")
        assertTrue(pane.isVisible, "and the pane stays shown")

        showPane = false
        awaitIdle()

        assertEquals(earlier, pane.background, "leaving gives the pane its earlier background back")
        assertFalse(pane.isVisible, "and hides it again")
    }

    @Test
    fun aLayoutKindChangedWhileThePaneIsDeclaredBuildsItsChildrenAnew() = runComposeSwingTest {
        var layout by mutableStateOf<PanelLayout<*>>(PanelLayout.Flow())
        setContent {
            Host { GlassPane(layout) { Label(text = "hint") } }
        }

        val pane = glassPane()
        val flowChild = pane.components.single()

        layout = PanelLayout.Border()
        awaitIdle()

        assertSame(pane, glassPane(), "a new layout kind keeps the host's own pane")
        assertIs<BorderLayout>(pane.layout, "the layout declared later is installed on the pane")
        val borderChild = pane.components.single()
        assertNotSame(flowChild, borderChild, "a new layout kind builds the pane's children anew")

        layout = PanelLayout.Flow()
        awaitIdle()

        assertIs<FlowLayout>(pane.layout, "the layout declared again is installed on the pane")
        assertNotSame(borderChild, pane.components.single(), "and builds the pane's children anew again")
        assertTrue(pane.isVisible, "the pane stays shown through both changes")
    }

    @Test
    fun aContentChangedWhileThePaneIsDeclaredFillsThePane() = runComposeSwingTest {
        val layout = PanelLayout.Flow()
        val hint: @Composable PanelScope.() -> Unit = { Label(text = "hint") }
        var content by mutableStateOf(hint)
        setContent {
            Host { GlassPane(layout, content = content) }
        }

        val pane = glassPane()
        assertEquals(listOf("hint"), pane.components.map { (it as JLabel).text }, "the content fills the pane")

        content = { Label(text = "warning") }
        awaitIdle()

        assertEquals(
            listOf("warning"),
            pane.components.map { (it as JLabel).text },
            "a content declared later with the same layout fills the pane",
        )
    }

    @Test
    fun anIfElseSelectingAnotherDeclarationKeepsThePaneAndRestoresItsOwnValuesOnRemoval() = runComposeSwingTest {
        var present by mutableStateOf(false)
        var alternate by mutableStateOf(false)
        setContent {
            Host {
                if (present) {
                    if (alternate) {
                        GlassPane(PanelLayout.Border(), SwingModifier.background(Color.BLUE)) {
                            Label(text = "second")
                        }
                    } else {
                        GlassPane(modifier = SwingModifier.background(Color.RED)) { Label(text = "first") }
                    }
                }
            }
        }

        val pane = glassPane()
        val earlier = pane.background
        present = true
        awaitIdle()
        assertEquals(listOf("first"), pane.components.map { (it as JLabel).text }, "the first branch fills the pane")

        alternate = true
        awaitIdle()

        assertSame(pane, glassPane(), "selecting the other branch keeps the host's own pane")
        assertTrue(pane.isVisible, "and keeps it shown")
        assertEquals(Color.BLUE, pane.background, "the selected declaration's modifier reaches the pane")
        assertIs<BorderLayout>(pane.layout, "and its layout is installed")
        assertEquals(listOf("second"), pane.components.map { (it as JLabel).text }, "and its content fills the pane")

        present = false
        awaitIdle()

        assertSame(pane, glassPane(), "removal keeps the host's own pane")
        assertFalse(pane.isVisible, "and hides it again")
        assertIs<BorderLayout>(pane.layout, "and leaves the layout the declaration installed")
        assertEquals(earlier, pane.background, "and gives it back its earlier background")
        assertEquals(0, pane.componentCount, "and removes the children the content composed")
    }

    @Test
    fun aGlassPaneMovedToAnotherHostLeavesTheFirstHostsPaneAndShowsTheSecondOnes() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        setContent {
            val overlay =
                remember { movableContentOf { host: GlassPaneHostScope -> host.GlassPane { Label(text = "x") } } }
            Host { if (!inSecond) overlay(this) }
            Host { if (inSecond) overlay(this) }
        }
        val (first, second) = glassPanes().map { assertIs<Container>(it) }

        inSecond = true
        awaitIdle()

        assertFalse(first.isVisible, "the host the declaration left hides its pane")
        assertEquals(0, first.componentCount, "and its pane holds nothing")
        assertTrue(second.isVisible, "the host the declaration arrived in shows its pane")
        assertEquals(listOf("x"), second.components.map { (it as JLabel).text }, "and its pane holds the label")
    }
}
