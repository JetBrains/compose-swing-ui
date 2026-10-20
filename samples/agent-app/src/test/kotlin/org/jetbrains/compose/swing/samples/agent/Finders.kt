package org.jetbrains.compose.swing.samples.agent

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/** The name a screen reader announces for the component tagged [tag]. */
internal fun ComposeSwingTest.accessibleNameOf(tag: String): String? =
    onNodeWithTag(tag).fetch<JComponent>().accessibleContext.accessibleName

/** The bounds of the component whose text contains [text], in [ancestor]'s coordinates. */
internal fun ComposeSwingTest.boundsOf(
    text: String,
    ancestor: Component = root,
): Rectangle {
    val component = onNodeWithText(text, substring = true).fetch<Component>()
    return SwingUtilities.convertRectangle(component.parent, component.bounds, ancestor)
}

/** The only scroll pane in the content. */
internal fun ComposeSwingTest.scrollPane(): JScrollPane = onNodeOfType<JScrollPane>().fetch()
