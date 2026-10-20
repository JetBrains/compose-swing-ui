package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

internal class StyleResolutionTest {
    @Test
    fun aWriteChangingStateAnotherStyleReadsResolvesItOnceTheOuterResolveIsWritten() = runComposeSwingTest {
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
                    SwingModifier.styleable(PlainProbe) {
                        ProbeFirst.provide("first $currentPass")
                        restylesOther.provide("other $currentPass")
                        ProbeSecond.provide("second $currentPass")
                        ProbeThird.provide("third $currentPass")
                    },
            )
        }

        pass = 1
        awaitIdle()

        assertEquals<List<String?>>(listOf("other 0"), valuesDuringWrite)
        onProbe("outer")
            .assertProperty("first 1") { first }
            .assertProperty("second 1") { second }
            .assertProperty("third 1") { third }
        onProbe("other").assertProperty("other 1") { fourth }
    }

    @Test
    fun providerConditionUsesAncestorLocal() = runComposeSwingTest {
        setContent {
            Panel(style = {
                if (LocalFlag.currentValue) provideNestedStyle(Labels) { toolTip("provided") }
            }) {
                CompositionLocalProvider(LocalFlag provides false) { Label("label") }
            }
        }

        onNodeOfType<JLabel>().assertProperty("provided") { toolTipText }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localKinds")
    fun providerValueUsesAncestorLocal(
        localTip: ProvidableCompositionLocal<String>,
        readInsideBlock: Boolean,
    ) = runComposeSwingTest {
        var tip by mutableStateOf("first")
        setContent {
            CompositionLocalProvider(localTip provides tip) {
                Panel(style = {
                    if (readInsideBlock) {
                        provideNestedStyle(Labels) { toolTip(localTip.currentValue) }
                    } else {
                        val read = localTip.currentValue
                        provideNestedStyle(Labels) { toolTip(read) }
                    }
                }) {
                    CompositionLocalProvider(localTip provides "override") { Label("label") }
                }
            }
        }
        onNodeOfType<JLabel>().assertProperty("first") { toolTipText }

        tip = "second"
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("second") { toolTipText }
    }

    /** Provider context and nested blocks must both reflect the same component application. */
    @Test
    fun nestedStyleUsesProviderLocalFromTheCurrentApply() = runComposeSwingTest {
        var value by mutableStateOf("first")
        var nestedInput by mutableStateOf("before")
        setContent {
            val input = nestedInput
            CompositionLocalProvider(LocalProviderValue provides value) {
                ProbeContainer(
                    StyleProbe(),
                    { provideNestedStyle(Probes) { ProbeFirst.provide("${LocalProviderValue.currentValue}:$input") } },
                ) {
                    ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe)
                }
            }
        }
        onProbe("leaf").assertProperty("first:before") { first }

        value = "second"
        nestedInput = "after"
        awaitIdle()

        onProbe("leaf").assertProperty("second:after") { first }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["provider property", "provided value", "nested block"])
    fun nestedStateReadsInvalidateOnlyTheScopeThatReadsThem(input: String) = runComposeSwingTest {
        var value by mutableStateOf("first")
        var providerResolves = 0
        var consumerResolves = 0
        val nested =
            SwingStyle<ProbeScope> {
                ProbeFirst.provide(if (input == "nested block") value else "fixed")
            }
        val provider =
            SwingStyle<ProbeContainerScope> {
                providerResolves++
                when (input) {
                    "provider property" -> {
                        ProbeSecond.provide(value)
                        provideNestedStyle(Probes, nested)
                    }

                    "provided value" -> {
                        val provided = value
                        provideNestedStyle(Probes) { ProbeFirst.provide(provided) }
                    }

                    else -> {
                        provideNestedStyle(Probes, nested)
                    }
                }
            }
        val kind =
            probeKind {
                consumerResolves++
                applyNestedStyle(Probes)
            }
        setContent {
            ProbeContainer(StyleProbe("provider"), provider) {
                ProbeLeaf(StyleProbe("leaf"), SwingStyle, kind)
            }
        }
        onProbe("leaf").assertProperty(if (input in listOf("provided value", "nested block")) "first" else "fixed") {
            first
        }
        val providersBefore = providerResolves
        val consumersBefore = consumerResolves

        value = "second"
        awaitIdle()
        val expected =
            when (input) {
                "provided value", "nested block" -> "second"
                else -> "fixed"
            }
        onProbe("leaf").assertProperty(expected) { first }
        if (input == "provider property") onProbe("provider").assertProperty("second") { second }
        assertEquals(providersBefore + if (input == "nested block") 0 else 1, providerResolves)
        assertEquals(consumersBefore + if (input == "provider property") 0 else 1, consumerResolves)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["direct state", "nested style", "inherited style local"])
    fun styleInputSurvivesEarlierModifierChange(input: String) = runComposeSwingTest {
        val value = mutableStateOf("first")
        val earlier = mutableStateOf("before")
        val provider =
            SwingStyle<ProbeContainerScope> {
                when (input) {
                    "nested style" -> provideNestedStyle(Probes) { ProbeFirst.provide(value.value) }
                    "inherited style local" -> InheritedFirst.provide(value.value)
                    else -> Unit
                }
            }
        val style =
            SwingStyle<ProbeScope> {
                if (input == "direct state") ProbeFirst.provide(value.value)
            }
        setContent {
            ProbeContainer(StyleProbe(), provider) {
                SwingNode(
                    factory = { StyleProbe("leaf") },
                    modifier = SwingModifier.property(FirstDescriptor, earlier.value).styleable(NestedProbe, style),
                )
            }
        }
        onProbe("leaf").assertProperty("first") { first }

        earlier.value = "after"
        awaitIdle()
        onProbe("leaf").assertProperty("first") { first }

        value.value = "second"
        awaitIdle()
        onProbe("leaf").assertProperty("second") { first }
    }

    @Test
    fun nestedStylePrecedence() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        val kind =
            probeKind {
                applyNestedStyle(Probes)
                ProbeThird.provide("default")
                ProbeFourth.provide("default")
            }
        setContent {
            ProbeContainer(StyleProbe(), {
                provideNestedStyle(Probes) {
                    ProbeFirst.provide("farther")
                    ProbeSecond.provide("farther")
                    ProbeThird.provide("farther")
                    ProbeFourth.provide("farther")
                }
            }) {
                ProbeContainer(StyleProbe(), {
                    provideNestedStyle(Probes) {
                        ProbeSecond.provide("nearer")
                        ProbeThird.provide("nearer")
                        ProbeFourth.provide("nearer")
                    }
                }) {
                    ProbeLeaf(leaf, { ProbeFourth.provide("caller") }, kind)
                }
            }
        }
        assertEquals(
            listOf("first=farther", "second=nearer", "third=default", "fourth=caller"),
            leaf.writes,
            "each property is written once, with the value of the last style in order that set it",
        )
    }

    @Test
    fun nearerProviderFallsBackPerProperty() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        var nearerProvides by mutableStateOf(true)
        val farther =
            SwingStyle<ProbeContainerScope> {
                provideNestedStyle(Probes) {
                    ProbeFirst.provide("farther")
                    ProbeSecond.provide("farther")
                }
            }
        val nearer =
            SwingStyle<ProbeContainerScope> {
                if (nearerProvides) provideNestedStyle(Probes) { ProbeSecond.provide("nearer") }
            }
        setContent {
            ProbeContainer(StyleProbe(), farther) {
                ProbeContainer(StyleProbe(), nearer) {
                    ProbeLeaf(leaf, SwingStyle, NestedProbe)
                }
            }
        }
        onProbe("leaf").assertProperty("farther") { first }.assertProperty("nearer") { second }
        leaf.writes.clear()
        nearerProvides = false
        awaitIdle()
        assertEquals(listOf("second=farther"), leaf.writes, "only the nearer value is replaced")
    }

    @Test
    fun aStyleNodeAddedLaterReachesExistingChildren() = runComposeSwingTest {
        var provides by mutableStateOf(false)
        setContent {
            val style =
                SwingStyle<ProbeContainerScope> {
                    if (provides) provideNestedStyle(Probes) { ProbeFirst.provide("nested") }
                }
            SwingNode(
                factory = { StyleProbe() },
                modifier =
                    if (provides) {
                        SwingModifier.styleable(PlainProbeContainer, style)
                    } else {
                        SwingModifier
                    },
            ) {
                ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe)
            }
        }
        onProbe("leaf").assertProperty(PROBE_OWN_VALUE) { first }

        provides = true
        awaitIdle()
        onProbe("leaf").assertProperty("nested", "the child already composed receives what the provider now provides") {
            first
        }
    }

    @Test
    fun labelIsStyledBeforeFirstFrame() = runComposeSwingTest {
        mainClock.autoAdvance = false
        val shown = mutableListOf<Color?>()
        setContent {
            Panel(
                modifier =
                    SwingModifier.containerListener { event ->
                        val label = event.child as? JLabel ?: return@containerListener
                        if (shown.isEmpty()) {
                            shown += label.foreground
                            label.addPropertyChangeListener("foreground") { shown += label.foreground }
                        }
                    },
                style = { provideNestedStyle(Labels) { foreground(Color.RED) } },
            ) {
                Label("label")
            }
        }

        onNodeOfType<JLabel>().assertProperty(Color.RED) { foreground }
        val own = JLabel().foreground
        assertEquals(listOf<Color?>(Color.RED), shown.filter { it != own }, "one provided color write: $shown")
    }

    companion object {
        @JvmStatic
        fun localKinds(): List<Arguments> = listOf(
            "static" to staticCompositionLocalOf { "unset" },
            "dynamic" to compositionLocalOf { "unset" },
        ).flatMap { (kind, local) ->
            listOf(false, true).map { inside ->
                val position = if (inside) "inside" else "outside"
                Arguments.of(Named.of("$kind local read $position the block", local), inside)
            }
        }
    }
}

/** Provider-local input used by the same-apply publication scenario. */
private val LocalProviderValue = staticCompositionLocalOf { "default" }

private val LocalFlag = staticCompositionLocalOf { true }
