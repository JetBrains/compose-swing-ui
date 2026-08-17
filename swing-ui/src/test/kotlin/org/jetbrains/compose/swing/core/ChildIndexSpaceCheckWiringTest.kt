package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * That the child-index-space walk is actually reached from a running composition, which
 * [ChildIndexSpaceCheckTest] does not cover: those tests call the walk directly on a hand-built holder
 * graph, so they would go on passing if the applier stopped scheduling it. This one goes through the
 * harness and asserts a real divergence surfaces, which holds the scheduling, the flag the harness sets,
 * and the one-turn deferral together.
 */
class ChildIndexSpaceCheckWiringTest {
    @Test
    fun aDivergenceOpenedBehindTheApplierIsReportedToTheTest() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent {
            BoxPanel(modifier = SwingModifier.testTag(HOST)) {
                Label("held")
                Label(caption)
            }
        }
        awaitIdle()

        // Take a child out from under the applier: its children list still holds the label, the container
        // no longer does. Only the walk the applier schedules can tell this test about that.
        val panel = onNodeWithTag(HOST).fetch<JComponent>()
        panel.remove(0)

        assertFailsWith<IllegalStateException> {
            caption = "second"
            awaitIdle()
        }
    }

    private companion object {
        const val HOST = "host-under-test"
    }
}
