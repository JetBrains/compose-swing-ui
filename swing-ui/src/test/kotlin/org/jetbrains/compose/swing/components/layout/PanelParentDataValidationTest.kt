// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.node.attachedTo
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelParentDataValidationTest {
    // That an applier refuses parent data its host does not accept is ParentDeclarationTest's; this
    // pins what the catalog's border scope declares for it to refuse.
    @Test
    fun aBorderScopeRegionDeclaresParentDataOnlyABorderLayoutAccepts() {
        val child: SwingNodeHolder<*> = CreatedNodeHolder(JLabel("child"))
        child.applyModifierDiff(with(BorderPanelScopeImpl) { SwingModifier.north() })

        val protocol = child.declaration.parentProtocol ?: error("north() declared no parent data")

        assertEquals(BorderLayout.NORTH, child.declaration.parentData)
        assertTrue(protocol.accepts(JPanel(BorderLayout())), "a BorderLayout host accepts the region")
        assertFalse(protocol.accepts(JPanel(FlowLayout())), "a FlowLayout host does not")
    }

    @Test
    fun aHoistedBorderRegionUnderAFlowPanelIsRefusedAndTheFlowPanelStaysEmpty() = runComposeSwingTest {
        val hoisted = with(BorderPanelScopeImpl) { SwingModifier.north() }
        val flowPanel = root.apply { layout = FlowLayout() }

        assertFailsWith<IllegalStateException> {
            setContent { Label(text = "hoisted", modifier = hoisted) }
        }

        assertEquals(0, flowPanel.componentCount, "the refused region child must not reach the flow panel")
    }

    @Test
    fun rawLayoutConstraintRemainsAnUntypedEscapeHatch() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child: SwingNodeHolder<*> = CreatedNodeHolder(JLabel("child"))
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
