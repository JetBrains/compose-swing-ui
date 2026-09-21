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

/** The parameters [LabelForFixture] varies. */
public data class LabelForParameters(
    val alternateTarget: Boolean,
)

/**
 * A caption [Label] carrying the [SwingModifier.labelFor] modifier inside a [Panel] that also holds two
 * targets, against a raw [JPanel] holding the same three labels with the caption's `labelFor` set by hand.
 */
public object LabelForFixture : ComposableFixture<LabelForParameters, JPanel>(
    initial = LabelForParameters(alternateTarget = false),
) {
    /** The second target, which the caption is re-pointed at. */
    override val alternatives: List<LabelForParameters> =
        listOf(
            initial.copy(alternateTarget = true),
        )

    @Composable
    override fun Content(parameters: LabelForParameters): Unit = Content(parameters.alternateTarget)

    /** Composes a [Panel] of two targets and a caption that labels the second when [alternateTarget] is set. */
    @Composable
    public fun Content(alternateTarget: Boolean) {
        Panel {
            val target = rememberLabelTarget()
            val alternate = rememberLabelTarget()
            Label("First target", modifier = SwingModifier.labelTarget(target))
            Label("Second target", modifier = SwingModifier.labelTarget(alternate))
            Label(
                "Caption",
                modifier = SwingModifier.labelFor(if (alternateTarget) alternate else target),
            )
        }
    }

    /** Builds the raw panel with the two targets and the caption, the caption not yet pointing at either target. */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            add(JLabel("First target"))
            add(JLabel("Second target"))
            add(JLabel("Caption"))
        }

    /** Points the raw caption's labelFor at the second target when [parameters] selects it, else at the first. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: LabelForParameters,
    ) {
        val first = component.getComponent(0) as JLabel
        val second = component.getComponent(1) as JLabel
        val caption = component.getComponent(2) as JLabel
        caption.labelFor = if (parameters.alternateTarget) second else first
    }
}
