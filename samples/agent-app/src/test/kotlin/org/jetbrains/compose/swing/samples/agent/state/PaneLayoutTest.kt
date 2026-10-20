package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.paneLayoutOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaneLayoutTest {
    @Test
    fun `toggling hides a shown pane and shows it again, keeping its width`() {
        val panes = paneLayoutOf()

        panes.toggleSessions()
        assertFalse(panes.sessions.isVisible)
        assertTrue(panes.context.isVisible, "the other pane stays")

        panes.toggleSessions()
        assertEquals(SidePane(isVisible = true, width = 252), panes.sessions)
    }

    @Test
    fun `widening stops at each pane's bounds`() {
        val panes = paneLayoutOf()

        panes.widenSessions(1_000)
        panes.widenContext(-1_000)

        assertEquals(420, panes.sessions.width)
        assertEquals(220, panes.context.width)
    }
}
