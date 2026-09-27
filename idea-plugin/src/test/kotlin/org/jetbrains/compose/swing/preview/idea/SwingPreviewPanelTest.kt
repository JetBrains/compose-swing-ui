package org.jetbrains.compose.swing.preview.idea

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JLabel
import javax.swing.JTextArea

/**
 * Measures how renderings are grouped and labelled, which is all that tells several of them apart.
 */
class SwingPreviewPanelTest : BasePlatformTestCase() {
    fun `test heads each group with the composable it came from`() {
        val panel = SwingPreviewPanel()

        panel.showGroups(listOf(group("SignInForm", rendering("Light")), group("Toolbar", rendering("Light"))))

        assertEquals(listOf("SignInForm", "Light - 120 x 40", "Toolbar", "Light - 120 x 40"), textsIn(panel))
    }

    fun `test labels each rendering with the name its annotation gave`() {
        val panel = SwingPreviewPanel()

        panel.showGroups(listOf(group("SignInForm", rendering("Light"), rendering("Dark"))))

        assertEquals(listOf("SignInForm", "Light - 120 x 40", "Dark - 120 x 40"), textsIn(panel))
    }

    fun `test leaves a lone unnamed rendering to its group's heading`() {
        val panel = SwingPreviewPanel()

        panel.showGroups(listOf(group("SignInForm", rendering(""))))

        assertEquals(listOf("SignInForm", "120 x 40"), textsIn(panel))
    }

    fun `test labels unnamed renderings by position once there is more than one`() {
        val panel = SwingPreviewPanel()

        panel.showGroups(listOf(group("SignInForm", rendering(""), rendering(""))))

        assertEquals(listOf("SignInForm", "#1 - 120 x 40", "#2 - 120 x 40"), textsIn(panel))
    }

    fun `test shows a composable that produced nothing under its own heading`() {
        val panel = SwingPreviewPanel()

        panel.showGroups(listOf(group("Broken", failure = "it laid out to 0x0"), group("Toolbar", rendering(""))))

        assertEquals(listOf("Broken", "Toolbar", "120 x 40"), textsIn(panel))
        assertEquals(listOf("it laid out to 0x0"), reportsIn(panel).map { it.text })
    }

    fun `test hands a failure over as text the reader can select and copy`() {
        val panel = SwingPreviewPanel()

        panel.showFailure("the preview host wrote no manifest")

        val report = reportsIn(panel).single()
        assertFalse("a report must not be editable", report.isEditable)
        report.selectAll()
        assertEquals("the preview host wrote no manifest", report.selectedText)
    }

    fun `test shows each rendering at the size it was rendered at`() {
        val panel = SwingPreviewPanel()

        panel.showGroups(listOf(group("SignInForm", rendering("Light", 200, 90))))

        val icon = labelsIn(panel).single { it.icon != null }.icon
        assertEquals(200, icon.iconWidth)
        assertEquals(90, icon.iconHeight)
    }

    private fun group(
        label: String,
        vararg renderings: SwingRendering,
        failure: String? = null,
    ) = SwingPreviewGroup(label, renderings.toList(), failure)

    private fun rendering(
        name: String,
        width: Int = 120,
        height: Int = 40,
    ) = SwingRendering(name, Dimension(width, height), BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))

    private fun textsIn(panel: SwingPreviewPanel): List<String> =
        labelsIn(panel).filter { it.icon == null && it.text.isNotEmpty() }.map { it.text }

    private fun labelsIn(panel: SwingPreviewPanel): List<JLabel> = componentsIn(panel).filterIsInstance<JLabel>()

    private fun reportsIn(panel: SwingPreviewPanel): List<JTextArea> =
        componentsIn(panel).filterIsInstance<JTextArea>()

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + (component as? Container)?.components?.flatMap { componentsIn(it) }.orEmpty()
}
