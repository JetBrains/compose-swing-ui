// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseMotionListener
import javax.swing.JLabel

/** The parameters [MouseMotionListenerListenerFixture] varies: the listener attached to the label. */
public data class MouseMotionListenerListenerParameters(
    val listener: MouseMotionListener,
)

/**
 * A [Label] with a [MouseMotionListener] attached, against a raw [JLabel] with the
 * same listener attached by hand.
 */
public object MouseMotionListenerListenerFixture :
    ComposableFixture<MouseMotionListenerListenerParameters, JLabel>(
        initial = MouseMotionListenerListenerParameters(listener = object : MouseMotionAdapter() {}),
    ) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MouseMotionListenerListenerParameters> =
        listOf(
            initial.copy(listener = object : MouseMotionAdapter() {}),
        )

    @Composable
    override fun Content(parameters: MouseMotionListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a label host with [listener] attached to it. */
    @Composable
    public fun Content(listener: MouseMotionListener) {
        Label(text = "", modifier = SwingModifier.mouseMotionListener(listener = listener))
    }

    /** Builds a bare [JLabel] before any of the fixture's values are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Replaces the fixture's earlier mouse motion listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: MouseMotionListenerListenerParameters,
    ) {
        component.removeMouseMotionListener(initial.listener)
        alternatives.forEach { component.removeMouseMotionListener(it.listener) }
        component.addMouseMotionListener(parameters.listener)
    }
}
