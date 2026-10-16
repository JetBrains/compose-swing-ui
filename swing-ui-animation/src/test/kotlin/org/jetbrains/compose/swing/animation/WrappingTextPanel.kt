package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * A panel around a wrapping text area showing the first [length] characters of [WRAPPING_TEXT]. The text area prefers
 * a width that follows the width it has, whatever the look-and-feel font.
 */
internal fun wrappingTextPanel(length: Int): JPanel =
    JPanel(BorderLayout()).apply {
        add(
            JTextArea(WRAPPING_TEXT.take(length)).apply {
                lineWrap = true
                wrapStyleWord = true
            },
        )
    }

/** Appends every size this panel is given to [sizes], in order, as whatever holds it lays it out. */
internal fun JPanel.recordSizesInto(sizes: MutableList<Dimension>): JPanel =
    apply {
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    sizes += Dimension(size)
                }
            },
        )
    }

/**
 * The size a [wrappingTextPanel] of [length] prefers once plain Swing, with no Compose around the panel, has given it
 * each of [sizes] in turn and read its preferred size after each. A wrapping text area's preferred width follows the
 * width it has, so the answer depends on that history.
 */
internal fun wrappingTextPanelPreferredAfter(
    length: Int,
    sizes: List<Dimension>,
): Dimension {
    require(sizes.isNotEmpty()) { "wrappingTextPanelPreferredAfter needs the sizes the panel was given" }
    val panel = wrappingTextPanel(length)
    var preferred = panel.preferredSize
    for (size in sizes) {
        panel.size = size
        panel.doLayout()
        preferred = panel.preferredSize
    }
    return preferred
}
