// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.interaction

import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.modifier.binding
import javax.swing.ButtonGroup
import javax.swing.JComponent
import kotlin.test.Test

/**
 * Chain behavior of the widget-specific interaction builders and the binding they build on: each appends
 * its element to the chain once.
 */
class SwingModifierTest {
    @Test
    fun everyInteractionBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { buttonGroup(ButtonGroup()) }
        assertDeclaredChainCarriedOnce { defaultButton() }
        assertDeclaredChainCarriedOnce { documentFilter(null) }
        assertDeclaredChainCarriedOnce { navigationFilter(null) }
        assertDeclaredChainCarriedOnce { onAccept { } }
    }

    @Test
    fun aBindingAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce {
            binding(JComponent::class.java, "binding", "value", { _, _ -> }, { _, _ -> })
        }
    }
}
