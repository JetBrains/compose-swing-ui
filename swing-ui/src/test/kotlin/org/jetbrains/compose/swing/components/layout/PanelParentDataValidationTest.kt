// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.FlowLayout
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
        val declared =
            with(BorderPanelScopeImpl) { SwingModifier.north() }
                .foldIn(emptyList<ParentDataModifier>()) { found, element ->
                    if (element is ParentDataModifier) found + element else found
                }.single()

        assertEquals(BorderLayout.NORTH, declared.modifyParentData(null))
        assertTrue(declared.parentProtocol.accepts(JPanel(BorderLayout())), "a BorderLayout host accepts the region")
        assertFalse(declared.parentProtocol.accepts(JPanel(FlowLayout())), "a FlowLayout host does not")
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
}
