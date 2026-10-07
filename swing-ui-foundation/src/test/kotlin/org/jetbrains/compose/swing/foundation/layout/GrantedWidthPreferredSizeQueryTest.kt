package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantedWidthPreferredSizeQueryTest {
    @Test
    fun aGrantedWidthIsAppliedBeforeEveryPreferredSizeQuery() {
        val containers: Map<String, @Composable (@Composable ConstrainedScope.() -> Unit) -> Unit> =
            mapOf(
                "Box" to { content -> Box { content() } },
                "Row" to { content -> Row { content() } },
                "Column" to { content -> Column { content() } },
            )
        for ((kind, container) in containers) {
            runComposeSwingTest {
                val preferredSizeWidths = mutableListOf<Int>()
                val area =
                    object : JTextArea("A wrapping sentence with enough words to take several lines. ".repeat(5)) {
                        override fun getPreferredSize(): Dimension {
                            preferredSizeWidths += width
                            return super.getPreferredSize()
                        }
                    }.apply {
                        lineWrap = true
                        wrapStyleWord = true
                    }
                setContent {
                    SwingNode(
                        factory = { JPanel(BorderLayout()) },
                        modifier = SwingModifier.preferredSize(200, 100),
                    ) {
                        container {
                            SwingNode(factory = { area }, modifier = SwingModifier.fillMaxWidth())
                        }
                    }
                }
                repeat(4) {
                    captureToImage()
                    awaitIdle()
                }
                assertEquals(200, area.width, "$kind: granted width")
                assertTrue(preferredSizeWidths.isNotEmpty(), "$kind: the stock leaf provides a preferred-size read")
                assertEquals(
                    setOf(200),
                    preferredSizeWidths.toSet(),
                    "$kind: every preference read uses the granted width",
                )
            }
        }
    }
}
