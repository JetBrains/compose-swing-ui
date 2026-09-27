package org.jetbrains.compose.swing.preview.idea

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Shows the renderings the previews in a file asked for, or a message in place of them.
 *
 * Each composable heads its own group, and each rendering inside it is shown at the size it was
 * rendered at, under a caption and inside a line border, so its extent is visible even where its
 * background is the color of the editor behind it.
 */
internal class SwingPreviewPanel : JPanel(BorderLayout()) {
    private val content = JPanel()
    private val scrollPane = JBScrollPane(content)

    init {
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        content.border = JBUI.Borders.empty(GAP)
        add(scrollPane, BorderLayout.CENTER)
    }

    /**
     * Shows every group in the order given, each under the name of the composable it came from.
     *
     * A group that also carries a reason shows it under its renderings: a composable can ask for
     * several and lose only some of them.
     */
    fun showGroups(groups: List<SwingPreviewGroup>) {
        replaceContent(
            groups.flatMap { group ->
                listOf(heading(group.label)) +
                    group.renderings.flatMapIndexed { index, rendering ->
                        listOf(caption(rendering, index, group.renderings.size), image(rendering))
                    } +
                    listOfNotNull(group.failure?.let { report(it) })
            },
        )
    }

    /** Shows [message] in place of any rendering: what there is to say when there is nothing to show. */
    fun showMessage(message: String) {
        replaceContent(listOf(JBLabel(message, SwingConstants.LEADING)))
    }

    /** Shows why nothing rendered at all. */
    fun showFailure(report: String) {
        replaceContent(listOf(report(report)))
    }

    /**
     * How wide a rendering may be before this has to be scrolled sideways to see all of it.
     *
     * The scroll pane's own viewport rather than this panel, since the panel keeps its width when a
     * vertical scroll bar takes some of it, and the border the content sits inside is not the content's
     * to use. Zero until the panel has been laid out, which is no answer rather than no room.
     */
    fun contentWidth(): Int {
        val insets = content.insets
        return (scrollPane.viewport.width - insets.left - insets.right).coerceAtLeast(0)
    }

    private fun replaceContent(components: List<JComponent>) {
        content.removeAll()
        for (component in components) {
            // One child that is not left-aligned squeezes every sibling that is.
            component.alignmentX = LEFT_ALIGNMENT
            content.add(component)
            content.add(Box.createVerticalStrut(JBUI.scale(GAP)))
        }
        // Space left over collects after the last rendering rather than being shared out between them.
        content.add(Box.createVerticalGlue())
        content.revalidate()
        content.repaint()
    }

    private fun heading(label: String): JComponent = JBLabel(label).apply { font = JBFont.label().asBold() }

    /**
     * Names a rendering by what tells it apart from its siblings: the name its annotation gave it, or
     * its position where a composable asks for several renderings and names none of them. A lone
     * unnamed rendering is left to the group's own heading and states only its size.
     */
    private fun caption(
        rendering: SwingRendering,
        index: Int,
        count: Int,
    ): JComponent {
        val size = "${rendering.size.width} x ${rendering.size.height}"
        val name = rendering.name.ifEmpty { if (count == 1) "" else "#${index + 1}" }
        return JBLabel(if (name.isEmpty()) size else "$name - $size")
            .apply { font = font.deriveFont(font.size2D - 1f) }
    }

    /**
     * A rendering at the size it was laid out at, drawn from a raster of the display's own resolution.
     *
     * The raster holds more pixels than the layout does wherever the display does, so it is wrapped
     * rather than handed over as it is: an image shown at its pixel count would come out as many times
     * too large as the display is dense, and one merely resized to fit would be resampled and blurred.
     */
    private fun image(rendering: SwingRendering): JComponent {
        val image =
            ImageUtil.ensureHiDPI(
                rendering.image,
                ScaleContext.create(this),
                rendering.size.width.toDouble(),
                rendering.size.height.toDouble(),
            )
        return JBLabel(JBImageIcon(image)).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
        }
    }

    /**
     * A failure the reader can select and copy. Read-only rather than a label, because a report is
     * something to paste into a search or an issue, and a label hands out nothing.
     */
    private fun report(text: String): JComponent =
        object : JBTextArea(text) {
            // A vertical box stretches a child up to its maximum, and a text area's is unbounded, so a
            // report would take the whole viewport and push the renderings out of sight. The height is
            // read rather than fixed because a wrapping area's own preferred height follows its width.
            override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = JBFont.create(Font(Font.MONOSPACED, Font.PLAIN, font.size))
            border = JBUI.Borders.empty()
            isOpaque = false
        }

    private companion object {
        const val GAP = 8
    }
}
