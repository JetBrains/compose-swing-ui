// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * What a menu body that throws costs. The menu's composition is built inside the gesture that opens it,
 * so the failure is reported to whoever made the gesture. The window's recomposer is ended on the next
 * turn, with no state change needed to reach it.
 *
 * The case realizes a real [javax.swing.JFrame], so the window hands out and replaces its recomposer on
 * the production path.
 */
class ContextMenuFailureTest {
    @Test
    fun aMenuBodyThatThrowsOnTheGestureEndsTheWindowsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            var caption by mutableStateOf("before")
            var captured: JPopupMenu? = null
            val host = JPanel().also { frame.contentPane.add(it) }
            host.setContent {
                val anchor = rememberPopupAnchor()
                Label(caption)
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                ContextMenu(
                    anchor,
                    display = { popup, _, _, _ -> captured = popup },
                ) {
                    check(false) { "the menu body cannot render" }
                }
            }
            awaitUntil("the content composes in the window") { labelTexts(host) == listOf("before", "target") }
            val target = host.components.filterIsInstance<JLabel>().single { it.text == "target" }

            val failure = assertFailsWith<IllegalStateException> { target.dispatchEvent(popupTrigger(target)) }
            assertEquals("the menu body cannot render", failure.message, "the gesture is handed the body's failure")
            assertNull(captured, "a menu whose body threw is not put on screen")

            awaitUntil("the failed menu composition ends the window's recomposer") {
                frame.swingRecomposerOrNull() ==
                    null
            }
            assertEquals(0, host.componentCount, "the content the window held is torn down")
            caption = "after"

            val next = JPanel().also { frame.contentPane.add(it) }
            next.setContent { Label(caption) }
            awaitUntil("content set after the failure composes on a fresh recomposer") {
                labelTexts(next) == listOf("after")
            }
            caption = "again"
            awaitUntil("the fresh recomposer keeps the content updating") { labelTexts(next) == listOf("again") }
        } finally {
            frame.dispose()
        }
    }
}
