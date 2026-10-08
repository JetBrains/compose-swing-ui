package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JComponent

internal const val SLOT_HEIGHT = 600

/** A slot of a Swing layout manager: given its width, it places the subject it is handed under a modifier. */
public typealias Slot = @Composable (Int, @Composable (SwingModifier) -> Unit) -> Unit

/**
 * The slots of Swing layout managers a container is shown in: the [WidthSettingSlots] and the [widthTakingSlots] at
 * each of [viewportHeights].
 */
public fun swingParentSlots(viewportHeights: List<Int>): Map<String, Slot> =
    WidthSettingSlots + widthTakingSlots(viewportHeights)

/**
 * The slots whose parent sets the width of the container it holds, whatever width the container prefers:
 * `BorderLayout` north and south, `BoxLayout` along the Y axis and a `GridBagLayout` cell filling its width.
 */
internal val WidthSettingSlots: Map<String, Slot> =
    mapOf(
        "BorderLayout NORTH" to borderLayoutSlot(BorderLayout.NORTH),
        "BorderLayout SOUTH" to borderLayoutSlot(BorderLayout.SOUTH),
        "BoxLayout Y_AXIS" to { width, subject ->
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                Panel(PanelLayout.Box(BoxLayout.Y_AXIS), modifier = SwingModifier.north()) {
                    subject(SwingModifier)
                }
            }
        },
        "GridBagLayout fill HORIZONTAL" to { width, subject ->
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                Panel(PanelLayout.GridBag, modifier = SwingModifier.north()) {
                    subject(SwingModifier.item(weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
                }
            }
        },
    )

internal fun borderLayoutSlot(region: String): Slot =
    { width, subject ->
        Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
            subject(SwingModifier.layoutConstraint(region))
        }
    }

/**
 * The slots whose parent takes the width of the container it holds from the width the container prefers: a
 * `JScrollPane` viewport at each of [viewportHeights], where the container prefers more than the viewport's width, and
 * `FlowLayout`.
 */
internal fun widthTakingSlots(viewportHeights: List<Int>): Map<String, Slot> =
    buildMap {
        for (height in viewportHeights) {
            this["JScrollPane $height high"] = { width, subject ->
                ScrollPane(modifier = SwingModifier.preferredSize(width, height)) {
                    Viewport { subject(SwingModifier) }
                }
            }
        }
        this["FlowLayout"] = { width, subject ->
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                Panel(PanelLayout.Flow(), modifier = SwingModifier.north()) { subject(SwingModifier) }
            }
        }
    }

/**
 * Paints the tree, as a window's next frame would, and runs the validation that paint requests over [subject], until a
 * paint requests none: the number of validations it took.
 */
public suspend fun ComposeSwingTest.cyclesUntilStable(subject: JComponent): Int {
    var cycles = 0
    val validationLimit = 8
    repeat(validationLimit) {
        val layoutRequested =
            withRecordedRepaints { recorder ->
                captureToImage()
                recorder.relayouts.any {
                    it === subject || it.isAncestorOf(subject) || subject.isAncestorOf(it)
                }
            }
        if (!layoutRequested) return cycles
        awaitIdle()
        cycles++
    }
    error("${subject.javaClass.simpleName} never settles within $validationLimit validations ($cycles attempted)")
}

internal class SwingParentLayout(
    private val preferred: (Container) -> Dimension,
    private val minimum: (Container) -> Dimension = preferred,
    private val place: (Container) -> Unit,
) : LayoutManager {
    override fun preferredLayoutSize(parent: Container): Dimension = preferred(parent)

    override fun minimumLayoutSize(parent: Container): Dimension = minimum(parent)

    override fun layoutContainer(parent: Container): Unit = place(parent)

    override fun addLayoutComponent(
        name: String?,
        component: Component?,
    ): Unit = Unit

    override fun removeLayoutComponent(component: Component?): Unit = Unit
}
