// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.InternalFrameState
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLabel

/** The parameters [InternalFrameStateFixture] varies. */
public data class InternalFrameStateParameters(
    val state: InternalFrameState,
)

/**
 * A frame driven by a hoisted [InternalFrameState] on a [DesktopPane], against a raw [JDesktopPane] whose
 * [JInternalFrame] sits on the state's bounds. The frame's other settings are fixed and shared by both sides.
 */
public object InternalFrameStateFixture : ComposableFixture<InternalFrameStateParameters, JDesktopPane>(
    initial = InternalFrameStateParameters(state = InternalFrameState(Rectangle(0, 0, 200, 100))),
) {
    private const val TITLE = "Frame"

    override val alternatives: List<InternalFrameStateParameters> =
        listOf(
            initial.copy(state = InternalFrameState(Rectangle(20, 20, 220, 120))),
        )

    @Composable
    override fun Content(parameters: InternalFrameStateParameters): Unit = Content(parameters.state)

    /** Composes a [DesktopPane] holding one frame titled "Frame" that is driven by [state]. */
    @Composable
    public fun Content(state: InternalFrameState) {
        DesktopPane {
            InternalFrame(title = TITLE, state = state, onClose = {}) {
                Label(text = "")
            }
        }
    }

    /** Builds a raw [JDesktopPane] holding one visible [JInternalFrame] titled "Frame" with an empty [JLabel]. */
    override fun createRawSwing(): JDesktopPane =
        JDesktopPane().apply {
            add(
                JInternalFrame(TITLE).apply {
                    defaultCloseOperation = JInternalFrame.DO_NOTHING_ON_CLOSE
                    contentPane.add(JLabel())
                    isVisible = true
                },
            )
        }

    /** Places the frame of [component] on the bounds of the state in [parameters]. */
    override fun updateRawSwing(
        component: JDesktopPane,
        parameters: InternalFrameStateParameters,
    ) {
        component.allFrames.single().bounds = parameters.state.bounds
    }
}
