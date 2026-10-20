package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.reflect.KProperty1
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What a style resolve collects and when it runs: nested styles farthest to nearest, then the kind's default, then
 * the caller's style; again on a snapshot state the style read, and only for the components that read it; again
 * when the styled ancestors above a component change.
 */
internal class StyleResolutionTest {
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
    fun aStyleInputStillUpdatesTheComponentAfterAnEarlierModifierPropertyChanges(input: String) = runComposeSwingTest {
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
                    modifier = SwingModifier.property(FirstDescriptor, earlier.value).styleable(style, NestedProbe),
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
    fun nestedStylesRunFarthestToNearestThenTheDefaultThenTheCallerStyle() = runComposeSwingTest {
        val leaf = StyleProbe()
        val kind =
            probeKind {
                applyNestedStyle(Probes)
                ProbeThird.provide("default")
                ProbeFourth.provide("default")
            }
        setContent {
            ProbeContainer(
                StyleProbe(),
                {
                    provideNestedStyle(Probes) {
                        ProbeFirst.provide("farther")
                        ProbeSecond.provide("farther")
                        ProbeThird.provide("farther")
                        ProbeFourth.provide("farther")
                    }
                },
            ) {
                ProbeContainer(
                    StyleProbe(),
                    {
                        provideNestedStyle(Probes) {
                            ProbeSecond.provide("nearer")
                            ProbeThird.provide("nearer")
                            ProbeFourth.provide("nearer")
                        }
                    },
                ) {
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
    fun aStateReadInsideTheStyleResolvesAgainWithoutRecomposition() = runComposeSwingTest {
        val probe = StyleProbe()
        var tint by mutableStateOf("light")
        var resolves = 0
        var compositions = 0
        val kind = probeKind { resolves++ }
        val style =
            SwingStyle<ProbeScope> {
                ProbeFirst.provide("fixed")
                ProbeSecond.provide(tint)
            }
        setContent {
            SideEffect { compositions++ }
            ProbeLeaf(probe, style, kind)
        }
        assertEquals(listOf("first=fixed", "second=light"), probe.writes)
        val composed = compositions
        val resolved = resolves
        probe.writes.clear()

        tint = "dark"
        awaitIdle()
        assertEquals(
            listOf("second=dark"),
            probe.writes,
            "only the changed property is written, as nothing declared before it changed",
        )
        assertEquals(resolved + 1, resolves, "the state change resolves the style once")
        assertEquals(composed, compositions, "the state change recomposes nothing")
    }

    @Test
    fun forOneKeyTheNearerProviderWinsPerPropertyAndTheFartherOneStandsWhereItLeavesIt() = runComposeSwingTest {
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
        onProbe("leaf")
            .assertProperty("farther", "the nearer provider does not mask what the farther one sets") { first }
            .assertProperty("nearer") { second }

        leaf.writes.clear()
        nearerProvides = false
        awaitIdle()
        assertEquals(
            listOf("second=farther"),
            leaf.writes,
            "once the nearer provider stops providing the key, the farther one's value takes its place",
        )
    }

    @Test
    fun aProviderDoesNotReceiveTheStyleItProvides() = runComposeSwingTest {
        val provider = StyleProbe()
        val everything = NestedStyleKey<ComponentStyleScope>("Everything")
        val kind = probeContainerKind { applyNestedStyle(everything) }
        setContent {
            ProbeContainer(provider, { provideNestedStyle(everything) { ProbeFirst.provide("nested") } }, kind) {
                ProbeContainer(StyleProbe("inner"), SwingStyle, kind) {}
            }
        }
        assertEquals(PROBE_OWN_VALUE, provider.first, "the provider applies the key, but not its own style for it")
        assertEquals(emptyList(), provider.writes)
        onProbe("inner").assertProperty("nested", "a descendant applying the key receives it") { first }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("movedLeafProperties")
    fun aSubtreeMovedToAnotherParentFollowsItsNewAncestors(
        property: KProperty1<StyleProbe, String?>,
        expected: String,
    ) = runComposeSwingTest {
        var underFirst by mutableStateOf(true)
        setContent {
            val moved =
                remember {
                    movableContentOf {
                        ProbeContainer(
                            StyleProbe(),
                            SwingStyle,
                        ) { ProbeLeaf(StyleProbe("leaf"), SwingStyle, NestedProbe) }
                    }
                }
            ProbeContainer(StyleProbe(), SwingStyle) {
                ProbeContainer(
                    StyleProbe(),
                    {
                        provideNestedStyle(Probes) {
                            ProbeFirst.provide("first parent")
                            ProbeSecond.provide("first parent")
                        }
                    },
                ) { if (underFirst) moved() }
                ProbeContainer(
                    StyleProbe(),
                    { provideNestedStyle(Probes) { ProbeFirst.provide("second parent") } },
                ) {
                    if (!underFirst) moved()
                }
            }
        }
        onProbe("leaf")
            .assertProperty("first parent") { first }
            .assertProperty("first parent") { second }

        underFirst = false
        awaitIdle()

        onProbe("leaf").assertProperty(expected) { property.get(this) }
    }

    @Test
    fun aLeafRemovedFromAProviderRestoresItsOriginalValue() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        var present by mutableStateOf(true)
        var underSecond by mutableStateOf(false)
        setContent {
            ProbeContainer(
                StyleProbe(),
                { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } },
            ) {
                if (present) ProbeLeaf(leaf, SwingStyle, NestedProbe)
            }
            ProbeContainer(
                StyleProbe(),
                { provideNestedStyle(Probes) { ProbeFirst.provide("second provider") } },
            ) {
                if (underSecond) ProbeLeaf(leaf, SwingStyle, NestedProbe)
            }
        }
        onProbe("leaf").assertProperty("provided") { first }

        present = false
        awaitIdle()

        assertEquals("own", leaf.first)

        underSecond = true
        awaitIdle()

        onProbe("leaf").assertProperty("second provider") { first }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lateProviders")
    fun aProviderAppearingAfterItsChildrenResolvedReachesThem(styleAppearsLate: Boolean) = runComposeSwingTest {
        var provides by mutableStateOf(false)
        setContent {
            val style =
                SwingStyle<ProbeContainerScope> {
                    if (provides) provideNestedStyle(Probes) { ProbeFirst.provide("nested") }
                }
            SwingNode(
                factory = { StyleProbe() },
                modifier =
                    if (styleAppearsLate && !provides) {
                        SwingModifier
                    } else {
                        SwingModifier.styleable(style, PlainProbeContainer)
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("nestedCycles")
    fun nestedBlocksApplyingTheirOwnKeyAreRefused(provider: SwingStyle<ProbeContainerScope>) {
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        ProbeContainer(StyleProbe(), provider) {
                            ProbeLeaf(StyleProbe(), SwingStyle, NestedProbe)
                        }
                    }
                }
            }
        assertContains(failure.message.orEmpty(), "$Probes")
    }

    @Test
    fun aNestedBlockAppliesTheStylesProvidedForAnotherKey() = runComposeSwingTest {
        val leaf = StyleProbe()
        setContent {
            ProbeContainer(
                StyleProbe(),
                {
                    provideNestedStyle(Probes) {
                        applyNestedStyle(OtherProbes)
                        ProbeSecond.provide("probes")
                    }
                    provideNestedStyle(OtherProbes) { ProbeFirst.provide("other") }
                },
            ) {
                ProbeLeaf(leaf, SwingStyle, NestedProbe)
            }
        }
        assertEquals(listOf("first=other", "second=probes"), leaf.writes)
    }

    @Test
    fun aChangedProviderStyleResolvesOnlyTheComponentsApplyingItsKeys() = runComposeSwingTest {
        val consumer = StyleProbe()
        val bystander = StyleProbe()
        var dark by mutableStateOf(false)
        var consumerResolves = 0
        var bystanderResolves = 0
        val light =
            SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("light") } }
        val darkStyle =
            SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("dark") } }
        val consumerKind =
            probeKind {
                consumerResolves++
                applyNestedStyle(Probes)
            }
        val bystanderKind =
            probeKind {
                bystanderResolves++
                applyNestedStyle(OtherProbes)
            }
        val bystanderStyle = SwingStyle<ProbeScope> { ProbeSecond.provide("own style") }
        setContent {
            ProbeContainer(StyleProbe(), if (dark) darkStyle else light) {
                ProbeLeaf(consumer, SwingStyle, consumerKind)
                ProbeLeaf(bystander, bystanderStyle, bystanderKind)
            }
        }
        assertEquals("light", consumer.first)
        val consumerResolved = consumerResolves
        val bystanderResolved = bystanderResolves
        consumer.writes.clear()
        bystander.writes.clear()

        dark = true
        awaitIdle()
        assertEquals(listOf("first=dark"), consumer.writes)
        assertEquals(consumerResolved + 1, consumerResolves, "the consumer of the key resolves once")
        assertEquals(emptyList(), bystander.writes, "a component applying another key writes nothing")
        assertEquals(bystanderResolved, bystanderResolves, "a component applying another key does not resolve")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstFrameProviders")
    fun onTheFirstFrameADescendantResolvesOnceAgainstItsContainersStyle(
        container: SwingStyle<ProbeContainerScope>,
        expected: String,
    ) = runComposeSwingTest {
        val leaf = StyleProbe()
        var resolves = 0
        val kind =
            probeKind {
                resolves++
                applyNestedStyle(Probes)
            }
        setContent {
            ProbeContainer(StyleProbe(), container) {
                ProbeLeaf(leaf, SwingStyle, kind)
            }
        }
        assertEquals(listOf(expected), leaf.writes)
        assertEquals(1, resolves, "the container's first publish does not resolve the descendant again")
    }

    @Test
    fun aReplacedLocalMapResolvesAgainOnlyTheStylesThatReadALocal() = runComposeSwingTest {
        var tone by mutableStateOf("light")
        var readerResolves = 0
        var bystanderResolves = 0
        val readerKind =
            probeKind {
                readerResolves++
                ProbeFirst.provide(LocalTone.currentValue)
            }
        val bystanderKind =
            probeKind {
                bystanderResolves++
                ProbeFirst.provide("own style")
            }
        setContent {
            CompositionLocalProvider(LocalTone provides tone) {
                ProbeLeaf(StyleProbe("reader"), SwingStyle, readerKind)
                ProbeLeaf(StyleProbe(), SwingStyle, bystanderKind)
            }
        }
        onProbe("reader").assertProperty("light") { first }
        val readerResolved = readerResolves
        val bystanderResolved = bystanderResolves

        tone = "dark"
        awaitIdle()
        onProbe("reader").assertProperty("dark") { first }
        assertEquals(readerResolved + 1, readerResolves, "a style that read the local resolves once")
        assertEquals(bystanderResolved, bystanderResolves, "a style that read no local does not resolve")
    }

    companion object {
        @JvmStatic
        fun movedLeafProperties(): List<Arguments> = listOf(
            Arguments.of(
                Named.of("the moved leaf applies its new ancestor's nested style", StyleProbe::first),
                "second parent",
            ),
            Arguments.of(
                Named.of("the moved leaf gets back what only its old ancestor wrote", StyleProbe::second),
                PROBE_OWN_VALUE,
            ),
        )

        @JvmStatic
        fun lateProviders(): List<Arguments> = listOf(
            Arguments.of(Named.of("a style added to the parent", true)),
            Arguments.of(Named.of("a key the parent's style starts providing", false)),
        )

        @JvmStatic
        fun nestedCycles(): List<Arguments> = listOf(
            Arguments.of(
                Named.of(
                    "a block applying the key it runs for",
                    SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { applyNestedStyle(Probes) } },
                ),
            ),
            Arguments.of(
                Named.of(
                    "blocks applying each other's keys",
                    SwingStyle<ProbeContainerScope> {
                        provideNestedStyle(Probes) { applyNestedStyle(OtherProbes) }
                        provideNestedStyle(OtherProbes) { applyNestedStyle(Probes) }
                    },
                ),
            ),
        )

        @JvmStatic
        fun firstFrameProviders(): List<Arguments> = listOf(
            Arguments.of(
                Named.of(
                    "a nested style",
                    SwingStyle<ProbeContainerScope> { provideNestedStyle(Probes) { ProbeFirst.provide("nested") } },
                ),
                "first=nested",
            ),
            Arguments.of(
                Named.of("a local", SwingStyle<ProbeContainerScope> { InheritedFirst.provide("inherited") }),
                "first=inherited",
            ),
        )
    }
}

private val LocalTone = staticCompositionLocalOf { "plain" }

/** Provider-local input used by the same-apply publication scenario. */
private val LocalProviderValue = staticCompositionLocalOf { "default" }
