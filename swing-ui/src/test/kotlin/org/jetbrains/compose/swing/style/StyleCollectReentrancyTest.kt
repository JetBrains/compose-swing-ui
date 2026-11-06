package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a resolve keeps of its collect while a property it writes resolves another component's style. */
internal class StyleCollectReentrancyTest {
    @Test
    fun nestedResolvePreservesOuterProperties() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        var otherValue by mutableStateOf<String?>("other 0")
        val other = StyleProbe("other")
        val valuesDuringWrite = ArrayList<String?>()
        val restylesOther =
            stylePropertyOf(
                ComponentPropertyDescriptor<StyleProbe, String?>(
                    "fourth",
                    { it.fourth },
                    { component, value ->
                        component.fourth = value
                        Snapshot.withMutableSnapshot { otherValue = value }
                        Snapshot.sendApplyNotifications()
                        if (value == "other 1") valuesDuringWrite += other.fourth
                    },
                ),
            )
        setContent {
            val currentPass = pass
            ProbeLeaf(other, { ProbeFourth.provide(otherValue) }, PlainProbe)
            SwingNode(
                factory = { StyleProbe("outer") },
                modifier =
                    SwingModifier.styleable(
                        {
                            ProbeFirst.provide("first $currentPass")
                            restylesOther.provide("other $currentPass")
                            ProbeSecond.provide("second $currentPass")
                            ProbeThird.provide("third $currentPass")
                        },
                        PlainProbe,
                    ),
            )
        }

        pass = 1
        awaitIdle()

        assertEquals<List<String?>>(listOf("other 1"), valuesDuringWrite)
        onProbe("outer")
            .assertProperty("first 1") { first }
            .assertProperty("second 1") { second }
            .assertProperty("third 1") { third }
        onProbe("other").assertProperty("other 1") { fourth }
    }
}
