package org.jetbrains.compose.swing.samples.agent.transcript

import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.fillMaxSize
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.BorderLayout

/** Mounts a transcript of [turns] filling the harness root, answering its callbacks as the app does. */
internal fun ComposeSwingTest.showTranscript(turns: List<Turn>) {
    root.layout = BorderLayout()
    setContent {
        Box {
            Transcript(
                turns = turns,
                onPermissionDecision = { permission, decision -> permission.decide(decision) },
                modifier = SwingModifier.fillMaxSize(),
            )
        }
    }
}
