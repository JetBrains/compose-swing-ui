package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.findDeclaringGroup
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** What a tool reading a [Layout]'s declared modifier chain is shown. */
class LayoutInspectionTest {
    @AfterTest
    fun turnInspectionOff() {
        SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
    }

    /** A tool reads the caller's elements first, then the observation entry [Layout] appends. */
    @Test
    fun aLayoutsChainIsTheCallersElementsThenItsObservationEntry() =
        runComposeSwingTest {
            isDebugInspectorInfoEnabled = true
            setContent {
                Layout(measurePolicy = { _, _ -> layout(0, 0) {} }, modifier = SwingModifier.testTag(CONTAINER_TAG))
            }

            val group = onNodeWithTag(CONTAINER_TAG).fetch().findDeclaringGroup()
            val node = assertNotNull(group?.node as? SwingComponentNode)
            val names =
                node.modifier.foldIn(emptyList<String?>()) { found, element ->
                    found + (element as? SwingModifier.InspectableElement)?.name
                }
            assertEquals(listOf("testTag", "layoutObservation"), names)
        }
}
