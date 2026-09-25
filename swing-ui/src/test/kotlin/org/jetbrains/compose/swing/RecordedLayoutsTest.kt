package org.jetbrains.compose.swing

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordedLayoutsTest {
    @Test
    fun recordingLayoutDelegatesToLayoutManager() {
        val delegate = FlowLayout()
        val layout = RecordingLayout(delegate)
        val panel = JPanel(layout)
        val child = JLabel("child")
        panel.add(child)
        panel.setSize(160, 80)

        layout.layoutContainer(panel)
        layout.invalidateLayout(panel)

        assertEquals(delegate.preferredLayoutSize(panel), layout.preferredLayoutSize(panel))
        assertEquals(delegate.minimumLayoutSize(panel), layout.minimumLayoutSize(panel))
        assertEquals(Dimension(Int.MAX_VALUE, Int.MAX_VALUE), layout.maximumLayoutSize(panel))
        assertEquals(0.5f, layout.getLayoutAlignmentX(panel))
        assertEquals(0.5f, layout.getLayoutAlignmentY(panel))
        assertEquals(listOf(LayoutRegistration(child, null)), layout.registrations)
        assertEquals(listOf<Container>(panel), layout.layouts)
        assertEquals(listOf<Container>(panel), layout.invalidations)
        assertTrue(child.width > 0 && child.height > 0, "the delegate lays out its child")
    }

    @Test
    fun recordingMeasurementLayoutDropsAChildDeclarationWhenRemoved() {
        val layout = RecordingMeasurementLayout()
        val child = JLabel("child")
        val parentData = Any()
        layout.declareComponentLayout(child, parentData, emptyList())

        layout.removeLayoutComponent(child)

        assertNull(layout.parentDataOf(child))
        assertEquals(listOf<Component>(child), layout.removals)
        assertEquals(1, layout.declarations.size, "the call history remains available after removal")
    }
}
