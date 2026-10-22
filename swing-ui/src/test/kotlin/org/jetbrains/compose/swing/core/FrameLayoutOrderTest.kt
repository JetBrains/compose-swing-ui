package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.WindowConstants
import kotlin.test.assertEquals

/** Frame-driven work started from rest reads the layout that its starting pass invalidated. */
class FrameLayoutOrderTest {
    @TestFactory
    fun theFirstAnimationFrameReadsTheLaidOutSize(): List<DynamicTest> =
        listOf("click", "write", "scrollbar report during layout").map { route ->
            dynamicTest("start=$route") { firstFrameAfter(route) }
        }

    private fun firstFrameAfter(route: String) = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display")
        var wide by mutableStateOf(false)
        var widthAtFirstFrame: Int? = null
        lateinit var mirror: MirrorState<Boolean>
        val content = JPanel(FlowLayout())
        val view = JPanel().apply { preferredSize = Dimension(NARROW, HEIGHT) }
        val scrollPane = JScrollPane(view, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER)
        content.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    wide = true
                }
            },
        )
        val frame = showFrame(content, scrollPane)
        var effectReady = false
        val handle =
            content.setContent {
                mirror = rememberMirrorState(false)
                SwingNode(
                    factory = { JLabel("x") },
                    modifier = SwingModifier.preferredSize(if (wide) WIDE else NARROW, HEIGHT),
                    update = { applyMirror(mirror) },
                )
                LaunchedEffect(wide) {
                    if (wide) {
                        withFrameNanos { widthAtFirstFrame = content.getComponent(0).width }
                    } else {
                        effectReady = true
                    }
                }
            }
        try {
            awaitUntil("the initial effect ends and the label is laid out narrow") {
                effectReady && content.componentCount == 1 && content.getComponent(0).width == NARROW
            }
            when (route) {
                "click" -> {
                    content.toolkit.systemEventQueue.postEvent(
                        MouseEvent(content, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false),
                    )
                }

                "write" -> {
                    wide = true
                }

                "scrollbar report during layout" -> {
                    startFromLayout(scrollPane, view, content) { mirror.report(true) { wide = true } }
                }
            }
            awaitUntil("the first frame after the change runs") { widthAtFirstFrame != null }
            assertEquals(
                WIDE,
                widthAtFirstFrame,
                "start=$route: the first animation frame must read the size laid out after its starting pass",
            )
        } finally {
            handle.dispose()
            frame.dispose()
        }
    }

    private suspend fun startFromLayout(
        scrollPane: JScrollPane,
        view: JPanel,
        content: JPanel,
        change: () -> Unit,
    ) {
        var reported = false
        var declaredWidth: Int? = null
        var laidOutWidth: Int? = null
        scrollPane.verticalScrollBar.addAdjustmentListener {
            if (!reported) {
                reported = true
                change()
                declaredWidth = content.getComponent(0).preferredSize.width
                laidOutWidth = content.getComponent(0).width
            }
        }
        view.preferredSize = Dimension(NARROW, 500)
        view.revalidate()
        awaitUntil("the scrollbar reports the range changed by layout") { reported }
        assertEquals(WIDE, declaredWidth, "the scrollbar report applies the wider declaration before returning")
        assertEquals(
            NARROW,
            laidOutWidth,
            "the report leaves the new layout queued until the enclosing validation finishes",
        )
    }

    private fun showFrame(
        content: JPanel,
        scrollPane: JScrollPane,
    ): JFrame = JFrame("frame-layout-order").apply {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        contentPane =
            JPanel(BorderLayout()).apply {
                add(content, BorderLayout.CENTER)
                add(scrollPane, BorderLayout.SOUTH)
            }
        setSize(500, 120)
        isVisible = true
    }

    private companion object {
        const val NARROW: Int = 100
        const val WIDE: Int = 300
        const val HEIGHT: Int = 20
    }
}
