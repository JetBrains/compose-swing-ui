// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.awaitUntil
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
 * What a [SwingModifier.tab] header that throws on its first composition costs: the failure reaches whoever
 * composed the pane, and the window's recomposer is ended on the next turn, after the pane's pass has
 * unwound.
 */
class TabbedPaneHeaderFailureTest {
    @Test
    fun aHeaderThatThrowsOnItsFirstCompositionEndsTheWindowsRecomposer() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = realizedFrame()
        try {
            val host = JPanel().also { frame.contentPane.add(it) }
            val failure = IllegalStateException("the header cannot render")

            val thrown =
                assertFailsWith<IllegalStateException> {
                    host.setContent {
                        TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                            Label(
                                text = "body",
                                modifier = SwingModifier.tab(title = "General", header = { throw failure }),
                            )
                        }
                    }
                }

            assertEquals(failure.message, thrown.message, "the caller is handed the header's failure")
            awaitUntil("the failed header composition ends the window's recomposer") {
                frame.swingRecomposerOrNull() ==
                    null
            }
            assertEquals(0, host.componentCount, "the content the window held is torn down")
        } finally {
            frame.dispose()
        }
    }
}
