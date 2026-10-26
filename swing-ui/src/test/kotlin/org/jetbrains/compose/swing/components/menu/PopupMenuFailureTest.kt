// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.menu

import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.core.labelTexts
import org.jetbrains.compose.swing.core.realizedFrame
import org.jetbrains.compose.swing.core.swingRecomposerOrNull
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What a [PopupMenu] body that throws costs. The menu opens once its anchor is showing, and its composition
 * is built inside the event that shows the anchor, so the failure reaches whoever caused that event. The
 * window's recomposer is ended on the next turn, with no state change needed to reach it.
 */
@ExclusiveWindowSystem
class PopupMenuFailureTest {
    @Test
    fun aMenuBodyThatThrowsWhenItOpensEndsTheWindowsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame().apply { setSize(FRAME_SIDE, FRAME_SIDE) }
        try {
            // Showing, so the anchor below is on screen once it is visible.
            frame.isVisible = true
            // Hidden, so the anchor is not showing and the menu declared open waits for it.
            val host = JPanel().also { it.isVisible = false }
            frame.contentPane.add(host)
            host.setContent {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                PopupMenu(anchor, expanded = true, onDismiss = {}) {
                    check(false) { "the menu body cannot render" }
                }
            }
            awaitUntil("the content composes in the window") { labelTexts(host) == listOf("target") }

            val failure = assertFailsWith<IllegalStateException> { host.isVisible = true }
            assertEquals("the menu body cannot render", failure.message, "the caller is handed the body's failure")
            awaitUntil("the failed menu composition ends the window's recomposer") {
                frame.swingRecomposerOrNull() ==
                    null
            }
            assertEquals(0, host.componentCount, "the content the window held is torn down")
        } finally {
            frame.dispose()
        }
    }

    private companion object {
        const val FRAME_SIDE = 200
    }
}
