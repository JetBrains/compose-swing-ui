package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PanelParentDataValidationTest {
    @Test
    fun aHoistedBorderScopeDeclarationIsRefusedBeforeAFlowPanelAddsTheChild() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child: SwingNodeHolder<Component> = SwingNodeHolder(JLabel("child"))
        child.applyModifierDiff(with(BorderPanelScopeImpl) { SwingModifier.north() })

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()
            assertEquals(0, root.componentCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rawLayoutConstraintRemainsAnUntypedEscapeHatch() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner.observing()
        val applier = SwingApplier(SwingNodeHolder(root).attachedTo(owner))
        val child: SwingNodeHolder<Component> = SwingNodeHolder(JLabel("child"))
        child.applyModifierDiff(SwingModifier.layoutConstraint(BorderLayout.NORTH))

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()
            assertEquals(1, root.componentCount)
        } finally {
            owner.dispose()
        }
    }
}
