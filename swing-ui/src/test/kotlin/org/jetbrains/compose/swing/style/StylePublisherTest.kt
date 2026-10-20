package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import kotlin.test.Test

/** The tests cover how a node finds the nearest ancestor that provides styles as the tree changes. */
class StylePublisherTest {
    @Test
    fun aSubtreeMovedUnderAProviderAndAwayTakesItsStylesWithIt() = runComposeSwingTest {
        var underProvider by mutableStateOf(false)
        val provider = SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } }
        setContent {
            val content = remember { movableContentOf { ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe) } }
            ProbeContainer(StyleProbe(), provider) { if (underProvider) content() }
            ProbeContainer(StyleProbe(), SwingStyle) { if (!underProvider) content() }
        }
        val leaf = onProbe("leaf")
        leaf.assertProperty(PROBE_OWN_VALUE, "a leaf outside the provider keeps its own value") { first }

        underProvider = true
        awaitIdle()
        leaf.assertProperty("provided", "the moved leaf takes the provider's style") { first }

        underProvider = false
        awaitIdle()
        leaf.assertProperty(PROBE_OWN_VALUE, "the leaf that moved away returns to its own value") { first }
    }

    @Test
    fun aProviderThatStartsAndStopsAboveASwingNodeHoleReachesTheLeafBelowIt() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val provider = SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } }
        setContent {
            ProbeContainer(StyleProbe(), if (providing) provider else SwingStyle) {
                SwingNode(factory = { JPanel() }) { ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe) }
            }
        }
        val leaf = onProbe("leaf")

        providing = true
        awaitIdle()
        leaf.assertProperty("provided", "the provider reaches the leaf across the hole") { first }

        providing = false
        awaitIdle()
        leaf.assertProperty(PROBE_OWN_VALUE, "the leaf's own value returns") { first }
    }

    @Test
    fun aLeafUnderNestedProvidersKeepsTheInnerOnesStyleWhenTheOuterStops() = runComposeSwingTest {
        var outerProviding by mutableStateOf(true)
        setContent {
            ProbeContainer(
                StyleProbe(),
                {
                    if (outerProviding) provideNestedStyle(Probes) { ProbeFirst.provide("outer") }
                },
            ) {
                ProbeContainer(StyleProbe(), { provideNestedStyle(Probes) { ProbeSecond.provide("inner") } }) {
                    ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe)
                }
            }
        }
        val leaf = onProbe("leaf")
        leaf.assertProperty("outer", "the outer provider styles the leaf") { first }
        leaf.assertProperty("inner", "the inner provider styles the leaf") { second }

        outerProviding = false
        awaitIdle()
        leaf.assertProperty(PROBE_OWN_VALUE, "the leaf loses what the outer provider stopped providing") { first }
        leaf.assertProperty("inner", "the inner provider still styles the leaf") { second }
    }
}
