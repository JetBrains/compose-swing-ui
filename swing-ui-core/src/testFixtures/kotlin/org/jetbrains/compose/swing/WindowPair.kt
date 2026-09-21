// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Dialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window
import javax.swing.JLayeredPane
import javax.swing.JMenuBar
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * A fixture's raw window and the window the composable realizes, which the raw Swing and request checks compare.
 *
 * The raw window is never shown, and its root pane stands where a panel host stands for a component. The composed
 * window is the newest entry of [Window.getWindows] that neither [windowsBefore] nor the raw window accounts for: a
 * change that AWT only accepts on an unrealized window, such as decorations, makes the composable replace its window.
 */
class WindowPair(
    private val test: ComposeSwingTest,
    /** The plain Swing window that the composable's window is compared against. It is never shown. */
    val raw: Window,
    private val windowsBefore: Set<Window>,
) {
    init {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        raw.name = RAW_WINDOW_NAME
    }

    /** The root pane of the raw window, which holds the menu bar and content that the raw Swing side builds. */
    val rawRootPane: JRootPane get() = checkNotNull(SwingUtilities.getRootPane(raw))

    /** The root pane of the window the composable realized, read at the moment of access. */
    val composedRootPane: JRootPane get() = checkNotNull(SwingUtilities.getRootPane(composed()))

    private fun composed(): Window = Window.getWindows().last { it !== raw && it !in windowsBefore }

    /** Whether [component] is in a window the composable realized, the current one or one it has replaced. */
    fun isComposed(component: Component): Boolean =
        SwingUtilities.getWindowAncestor(component)?.let { it !== raw && it !in windowsBefore } == true

    /**
     * Fails unless the composed window's content pane and menu bar match the raw window's, and, with
     * [compareProperties], so do the properties of the windows themselves. A composed window that has no peer yet,
     * which the window finder does not see, gets one for the comparison.
     */
    suspend fun assertMatches(
        stage: String,
        compareProperties: Boolean,
    ) {
        val window = composed()
        val realized = window.isDisplayable
        if (!realized) window.addNotify()
        // validate() lays nothing out under a window without a peer, so the raw window gets one for the comparison too.
        val rawRealized = raw.isDisplayable
        if (!rawRealized) raw.addNotify()
        try {
            // A window lays itself out on an event of its own, after its peer is created or its content changes; the
            // comparison copies its size only after that pass.
            test.awaitIdle()
            if (compareProperties) {
                assertEquals(
                    properties(raw),
                    properties(window),
                    "The composed window must match raw Swing for $stage.",
                )
            }
            // Neither window is shown, so the repaint manager drops the pass a revalidate() schedules. One validate()
            // of each root pane runs that pass, the way the test's own root is laid out at idle: it lays out only what
            // was invalidated, so a change the library fails to invalidate still fails the comparison.
            composedRootPane.validate()
            // The raw root pane lays its menu bar and content out at its own size, which its window never sets.
            rawRootPane.size = composedRootPane.size
            rawRootPane.validate()
            val found = test.onWindow(!SwingMatcher.hasName(RAW_WINDOW_NAME))
            val contentPane =
                SwingMatcher.hasParent(SwingMatcher.isOfType<JLayeredPane>()) and !SwingMatcher.isOfType<JMenuBar>()
            val rawBar = rawRootPane.jMenuBar
            try {
                found.onNode(contentPane).assertTreeMatches(rawRootPane.contentPane)
                if (rawBar == null) {
                    assertNull(composedRootPane.jMenuBar, "The composed window has a menu bar the raw window lacks.")
                } else {
                    found.onNode(SwingMatcher.isOfType<JMenuBar>()).assertTreeMatches(rawBar)
                }
            } catch (failure: AssertionError) {
                throw AssertionError("The composed tree must match raw Swing for $stage. ${failure.message}", failure)
            }
        } finally {
            if (!rawRealized) raw.removeNotify()
            if (!realized) window.removeNotify()
        }
    }

    /** Fails unless the composed window is no longer realized and nothing remains in the test's root. */
    fun assertGone(fixture: String) {
        assertEquals(
            0,
            test.root.componentCount,
            "The host of $fixture must hold no components once its content is gone.",
        )
        assertFalse(
            Window.getWindows().any { it !== raw && it !in windowsBefore && it.isDisplayable },
            "The window $fixture composed must be gone once its content is gone.",
        )
    }

    private fun properties(window: Window): Map<String, Any?> =
        buildMap {
            put("size", window.size)
            put("minimumSize", window.minimumSize)
            put("isAlwaysOnTop", window.isAlwaysOnTop)
            put("isVisible", window.isVisible)
            put("iconImages", window.iconImages)
            if (window is Frame) {
                put("title", window.title)
                put("isResizable", window.isResizable)
                put("isUndecorated", window.isUndecorated)
            }
            if (window is Dialog) {
                put("title", window.title)
                put("isResizable", window.isResizable)
                put("isUndecorated", window.isUndecorated)
                put("modalityType", window.modalityType)
            }
        }

    private companion object {
        const val RAW_WINDOW_NAME = "raw-swing-window"
    }
}
