// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.modifier.accessibility

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.modifier.accessibility.labelFor
import org.jetbrains.compose.swing.components.modifier.accessibility.labelTarget
import org.jetbrains.compose.swing.components.modifier.accessibility.rememberLabelTarget
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters [LabelTargetFixture] varies. */
public data class LabelTargetParameters(
    val alternateTarget: Boolean,
)

/**
 * A target [Label] carrying the [SwingModifier.labelTarget] modifier inside a [Panel] that also holds two
 * captions, against a raw [JPanel] holding the same three labels with each caption's `labelFor` set by hand.
 */
public object LabelTargetFixture : ComposableFixture<LabelTargetParameters, JPanel>(
    initial = LabelTargetParameters(alternateTarget = false),
) {
    /** The target bound to the second caption's handle instead of the first caption's. */
    override val alternatives: List<LabelTargetParameters> =
        listOf(
            initial.copy(alternateTarget = true),
        )

    @Composable
    override fun Content(parameters: LabelTargetParameters): Unit = Content(parameters.alternateTarget)

    /**
     * Composes a [Panel] of two captions and a target bound to the second caption's handle when [alternateTarget] is
     * set.
     */
    @Composable
    public fun Content(alternateTarget: Boolean) {
        Panel {
            val target = rememberLabelTarget()
            val alternate = rememberLabelTarget()
            Label("First caption", modifier = SwingModifier.labelFor(target))
            Label("Second caption", modifier = SwingModifier.labelFor(alternate))
            Label(
                "Target",
                modifier = SwingModifier.labelTarget(if (alternateTarget) alternate else target),
            )
        }
    }

    /** Builds the raw panel with the two captions and the target, neither caption yet pointing at the target. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            add(JLabel("First caption"))
            add(JLabel("Second caption"))
            add(JLabel("Target"))
        }

    /** Points the selected raw caption's labelFor at the target and clears the other one. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: LabelTargetParameters,
    ) {
        val first = component.getComponent(0) as JLabel
        val second = component.getComponent(1) as JLabel
        val target = component.getComponent(2) as JLabel
        first.labelFor = if (parameters.alternateTarget) null else target
        second.labelFor = if (parameters.alternateTarget) target else null
    }
}
