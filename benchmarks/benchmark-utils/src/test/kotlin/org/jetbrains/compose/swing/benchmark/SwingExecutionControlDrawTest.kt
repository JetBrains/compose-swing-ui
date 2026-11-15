// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.testutils.ComposeTestCase
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests that [SwingExecutionControl] can settle before layout and paint the whole host. */
class SwingExecutionControlDrawTest {
    @Test
    fun `composition can settle before layout and full painting paints a clean host`() =
        runSwingTest {
            val subject = PaintCase()
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.recompose()
                assertEquals(0, subject.panel.width)
                control.host.drawToBitmap()
                assertNotEquals(Color.RED.rgb, control.host.image.getRGB(1, 1))
                control.host.layout()
                assertTrue(subject.panel.width > 0)
                control.host.drawToBitmap()
                assertEquals(Color.RED.rgb, control.host.image.getRGB(1, 1))
            }
        }

    private class PaintCase : ComposeTestCase {
        val panel = PaintPanel()

        @Composable
        override fun Content() {
            SwingNode(factory = { panel })
        }
    }

    private class PaintPanel : JPanel() {
        init {
            preferredSize = Dimension(40, 30)
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            graphics.color = Color.RED
            graphics.fillRect(0, 0, width, height)
        }
    }
}
