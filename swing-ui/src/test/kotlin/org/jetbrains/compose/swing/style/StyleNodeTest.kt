package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.modifier.resetModifierState
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

internal class StyleNodeTest {
    @Test
    fun declaringAStyleDoesNotOwnItsStatesRememberLifecycle() {
        val state = mockk<RememberObserver>(relaxed = true)
        val kind = SwingStyleKind<ComponentStyleScope, RememberObserver>({ base, _ -> base })
        runComposeSwingTest {
            var shown by mutableStateOf(true)
            setContent {
                if (shown) {
                    SwingNode(
                        factory = { JLabel() },
                        modifier = SwingModifier.styleable(kind, state, SwingStyle),
                    )
                }
            }
            verify(exactly = 0) { state.onRemembered() }
            verify(exactly = 0) { state.onForgotten() }
            verify(exactly = 0) { state.onAbandoned() }

            shown = false
            awaitIdle()
            verify(exactly = 0) { state.onRemembered() }
            verify(exactly = 0) { state.onForgotten() }
            verify(exactly = 0) { state.onAbandoned() }
        }
        verify(exactly = 0) { state.onRemembered() }
        verify(exactly = 0) { state.onForgotten() }
        verify(exactly = 0) { state.onAbandoned() }
    }

    @Test
    fun theKindlessStyleOffersJComponentPropertiesAndProvidesNestedStyles() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    SwingModifier.testTag("container").styleable {
                        toolTip("container")
                        provideNestedStyle(Probes) { LabelToolTip.provide("nested") }
                    },
            ) {
                SwingNode(factory = { JLabel() }, modifier = SwingModifier.styleable(NestedProbe, SwingStyle))
            }
        }
        onNodeWithTag("container").assertIsOfType<JPanel>().assertProperty("container") { toolTipText }
        onNodeOfType<JLabel>().assertProperty("nested") { toolTipText }
    }

    @Test
    fun theKindlessStyleRejectsANonJComponentBeforeWritingProperties() = runComposeSwingTest {
        val target = object : Component() {}.apply { background = Color.BLUE }
        assertFailsWith<IllegalArgumentException> {
            setContent {
                SwingNode(factory = { target }, modifier = SwingModifier.styleable { background(Color.RED) })
            }
        }
        assertEquals(Color.BLUE, target.background)
    }

    @Test
    fun aRejectedKindlessStyleProvidesNothingToAnExistingChild() = runComposeSwingTest {
        val owner = TestCompositionOwner()
        val target = object : Component() {}.apply { background = Color.BLUE }
        val parent = CreatedNodeHolder(target).attachedTo(owner)
        val child = CreatedNodeHolder(JLabel()).attachedTo(owner)
        val resolutions = ArrayList<Unit>()
        child.parentTo(parent)
        parent.children += child
        try {
            child.applyDeclaredModifier(
                SwingModifier.styleable(NestedProbe) {
                    resolutions += Unit
                },
            )
            val before = resolutions.toList()
            val childTip = child.component.toolTipText
            val failure =
                assertFailsWith<RuntimeException> {
                    parent.applyDeclaredModifier(
                        SwingModifier.styleable {
                            background(Color.RED)
                            provideNestedStyle(Probes) { LabelToolTip.provide("rejected") }
                        },
                    )
                }
            assertEquals(Color.BLUE, target.background)
            assertEquals(childTip, child.component.toolTipText, "the child receives no rejected nested style")
            assertEquals(before, resolutions, "a rejected provider must not provide or restyle its child")
            assertIs<IllegalArgumentException>(failure)
        } finally {
            child.resetModifierState()
            parent.resetModifierState()
            owner.dispose()
        }
    }

    @Test
    fun anExplicitKindStylesANonJComponent() = runComposeSwingTest {
        var target: Component? = null
        setContent {
            SwingNode(
                factory = { object : Component() {}.also { target = it } },
                modifier = SwingModifier.styleable(PlainProbe) { background(Color.RED) },
            )
        }
        assertEquals(Color.RED, target?.background)
    }

    @Test
    fun switchingANonJComponentFromAKindToTheKindlessStyleFails() {
        val target = object : Component() {}.apply { background = Color.RED }
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                var custom by mutableStateOf(true)
                setContent {
                    SwingNode(
                        factory = { target },
                        modifier =
                            if (custom) {
                                SwingModifier.styleable(PlainProbe) { background(Color.RED) }
                            } else {
                                SwingModifier.styleable { background(Color.BLUE) }
                            },
                    )
                }
                custom = false
                awaitIdle()
            }
        }
        assertEquals(Color.RED, target.background)
    }

    @Test
    fun aChangingStyleAndARepeatedOtherKeyRemainValidAcrossRecompositions() = runComposeSwingTest {
        var tip by mutableStateOf("first")
        setContent {
            val current = tip
            SwingNode(
                factory = { JLabel() },
                modifier =
                    SwingModifier
                        .testTag("first")
                        .testTag("last")
                        .styleable(PlainProbe) { LabelToolTip.provide(current) },
            )
        }
        onNodeWithTag("last").assertIsOfType<JLabel>().assertProperty("first") { toolTipText }
        tip = "second"
        awaitIdle()
        onNodeWithTag("last").assertIsOfType<JLabel>().assertProperty("second") { toolTipText }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aNodeObservingStateStopsWhenItsStyleStopsSettingIt(inParentLayout: Boolean) = runComposeSwingTest {
        var present by mutableStateOf(true)
        val watched = mutableIntStateOf(0)
        var changes = 0
        var detaches = 0
        val observing =
            stylePropertyOf<Unit> {
                if (inParentLayout) {
                    ObservingLayoutElement(watched::intValue, { detaches++ }) { changes++ }
                } else {
                    ObservingElement(watched::intValue, { detaches++ }) { changes++ }
                }
            }
        setContent {
            SwingNode(factory = { JPanel(RecordingMeasurementLayout(FlowLayout())) }) {
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.styleable(PlainProbe) { if (present) observing.provide(Unit) },
                )
            }
        }
        watched.intValue = 1
        awaitIdle()
        assertEquals(1, changes, "the node hears a change while the style sets it")

        present = false
        awaitIdle()
        assertEquals(1, detaches, "the node leaves with the value")

        watched.intValue = 2
        awaitIdle()
        assertEquals(1, changes, "a node that left hears no change")

        present = true
        awaitIdle()
        watched.intValue = 3
        awaitIdle()
        assertEquals(2, changes, "a value set again observes again")
    }

    @Test
    fun aStyledComponentMovedToAnotherCompositionKeepsObservingThroughBothKindsOfDeclaration() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val watched = mutableIntStateOf(0)
        var propertyObserved = -1
        var layoutObserved = -1
        val property =
            stylePropertyOf<Unit> { ObservingElement({ watched.intValue.also { propertyObserved = it } }, {}, {}) }
        val layoutProperty =
            stylePropertyOf<Unit> { ObservingLayoutElement({ watched.intValue.also { layoutObserved = it } }, {}, {}) }
        val modifier =
            SwingModifier.styleable(PlainProbe) {
                property.provide(Unit)
                layoutProperty.provide(Unit)
            }
        val host = JPanel(RecordingMeasurementLayout(FlowLayout()))
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { JLabel("moved") }, modifier = modifier)
                    }
                }
            SwingNode(factory = { host })
            SwingNode(factory = { JPanel(RecordingMeasurementLayout(FlowLayout())) }) {
                if (inOuter) content()
            }
        }
        val handle = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()
        inOuter = true
        awaitIdle()
        handle.dispose()
        watched.intValue++
        awaitIdle()

        assertEquals(listOf(1, 1), listOf(propertyObserved, layoutObserved), "both keep observing in their new place")
    }

    @Test
    fun anUnstyledComponentOfAThirdPartyNestingKindRunsNoDefault() = runComposeSwingTest {
        var defaultRuns = 0
        val kind =
            nestingStyleKind<ProbeScope, Unit>(
                { base, _ -> object : ProbeScope, ComponentStyleScope by base {} },
            ) {
                defaultRuns++
                applyNestedStyle(Probes)
            }
        setContent {
            SwingNode(factory = { StyleProbe() }, modifier = SwingModifier.styleable(kind, SwingStyle))
        }
        assertEquals(0, defaultRuns, "nothing above provides, so the default is not run")
    }

    @Test
    fun styleableModifiersKeepOrderAndRestoreEarlierProperties() = runComposeSwingTest {
        var includeFirst by mutableStateOf(true)
        var includeOrdinary by mutableStateOf(true)
        var includeLast by mutableStateOf(true)
        val target = StyleProbe("target")
        val firstStyle =
            SwingStyle<ProbeScope> {
                ProbeFirst.provide("style A")
                ProbeSecond.provide("A only")
            }
        val lastStyle =
            SwingStyle<ProbeScope> {
                ProbeFirst.provide("style C")
                ProbeThird.provide("C only")
            }
        setContent {
            val modifier =
                (if (includeFirst) SwingModifier.styleable(PlainProbe, firstStyle) else SwingModifier)
                    .then(
                        if (includeOrdinary) {
                            SwingModifier.property(FirstDescriptor, "ordinary modifier")
                        } else {
                            SwingModifier
                        },
                    ).then(if (includeLast) SwingModifier.styleable(PlainProbe, lastStyle) else SwingModifier)
            SwingNode(factory = { target }, modifier = modifier)
        }
        onNodeOfType<StyleProbe>()
            .assertProperty("style C") { first }
            .assertProperty("A only") { second }
            .assertProperty("C only") { third }

        includeLast = false
        awaitIdle()
        onNodeOfType<StyleProbe>()
            .assertProperty("ordinary modifier") { first }
            .assertProperty("A only") { second }
            .assertProperty(PROBE_OWN_VALUE) { third }

        includeOrdinary = false
        awaitIdle()
        onNodeOfType<StyleProbe>().assertProperty("style A") { first }

        includeFirst = false
        awaitIdle()
        onNodeOfType<StyleProbe>()
            .assertProperty(PROBE_OWN_VALUE) { first }
            .assertProperty(PROBE_OWN_VALUE) { second }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("combinedStyles")
    fun aCombinedStyleRunsItsPartsInOrderAndAChangedPartRestyles(case: CombinedStyleCase) = runComposeSwingTest {
        var last by mutableStateOf("last")
        val leading =
            SwingStyle<ProbeScope> {
                ProbeFirst.provide("first")
                ProbeSecond.provide("first only")
            }
        setContent {
            val trailing = SwingStyle<ProbeScope> { ProbeFirst.provide(last) }
            SwingNode(
                factory = { StyleProbe() },
                modifier = SwingModifier.styleable(PlainProbe, case.combine(leading, trailing)),
            )
        }
        onNodeOfType<StyleProbe>()
            .assertProperty(case.expectedFirst("last")) { first }
            .assertProperty("first only") { second }

        last = "changed"
        awaitIdle()
        onNodeOfType<StyleProbe>()
            .assertProperty(case.expectedFirst("changed")) { first }
            .assertProperty("first only") { second }
    }

    @Test
    fun twoStylesBothApplyAfterAllComponentsOnce() = runComposeSwingTest {
        setContent {
            ProbeContainer(
                StyleProbe(),
                {
                    provideNestedStyle(AllComponents) {
                        ProbeThird.provide("all")
                        ProbeFourth.provide("all")
                    }
                },
            ) {
                SwingNode(
                    factory = { StyleProbe("leaf") },
                    modifier =
                        SwingModifier
                            .styleable(PlainProbe) {
                                ProbeFirst.provide("first")
                                ProbeThird.provide("first only")
                            }.styleable(PlainProbe) {
                                ProbeFirst.provide("second")
                                ProbeSecond.provide("second only")
                            },
                )
            }
        }
        onProbe("leaf")
            .assertProperty("second") { first }
            .assertProperty("second only") { second }
            .assertProperty("first only", "AllComponents runs before the first declaration only") { third }
            .assertProperty("all", "a kind that applies no key receives AllComponents") { fourth }
    }

    @ParameterizedTest(name = "removes declaration {0}")
    @ValueSource(ints = [0, 1, 2])
    fun removingAStyleDeclarationRestoresTheLastSurvivingWriter(removed: Int) = runComposeSwingTest {
        var absentNow: Int? by mutableStateOf(null)
        val sharedValues = listOf("style A", "style B", "style C")
        val ownValues = listOf("A only", "B only", "C only")
        val styles =
            listOf(ProbeSecond, ProbeThird, ProbeFourth).mapIndexed { index, own ->
                SwingStyle<ProbeScope> {
                    ProbeFirst.provide(sharedValues[index])
                    own.provide(ownValues[index])
                }
            }
        setContent {
            val declared = styles.filterIndexed { index, _ -> index != absentNow }
            SwingNode(
                factory = { StyleProbe() },
                modifier =
                    declared.fold<SwingStyle<ProbeScope>, SwingModifier>(SwingModifier) { modifier, style ->
                        modifier.styleable(PlainProbe, style)
                    },
            )
        }

        fun assertDeclared(absent: Int?) {
            val survivors = styles.indices.filter { it != absent }

            fun own(index: Int) = if (index == absent) PROBE_OWN_VALUE else ownValues[index]
            onNodeOfType<StyleProbe>()
                .assertProperty(sharedValues[survivors.last()]) { first }
                .assertProperty(own(0)) { second }
                .assertProperty(own(1)) { third }
                .assertProperty(own(2)) { fourth }
        }

        assertDeclared(absent = null)
        absentNow = removed
        awaitIdle()
        assertDeclared(absent = removed)
        absentNow = null
        awaitIdle()
        assertDeclared(absent = null)
    }

    @Test
    fun buttonModifierStyleAndTypedStyleApplyInOrder() = runComposeSwingTest {
        var typedBackground by mutableStateOf(false)
        setContent {
            Button(
                "button",
                onClick = {},
                modifier = SwingModifier.styleable { background(Color.RED) },
                style = {
                    toolTip("typed style")
                    if (typedBackground) background(Color.BLUE)
                },
            )
        }
        onNodeOfType<JButton>()
            .assertProperty("typed style") { toolTipText }
            .assertProperty(Color.RED) { background }
        typedBackground = true
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(Color.BLUE) { background }
        typedBackground = false
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(Color.RED) { background }
    }

    @Test
    fun aProvidedStyleStillAppliesWhereADescendantOverridesALocalTheProviderReads() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    SwingModifier.styleable(PlainProbeContainer) {
                        if (LocalFlag.currentValue) {
                            provideNestedStyle(Probes) { LabelToolTip.provide("provided") }
                        }
                    },
            ) {
                CompositionLocalProvider(LocalFlag provides false) {
                    SwingNode(
                        factory = { JLabel() },
                        modifier = SwingModifier.styleable(NestedProbe, SwingStyle),
                    )
                }
            }
        }

        onNodeOfType<JLabel>().assertProperty("provided") { toolTipText }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localKinds")
    fun aLocalTheProviderReadsReachesADescendantThatOverridesIt(
        localTip: ProvidableCompositionLocal<String>,
        readInsideBlock: Boolean,
    ) = runComposeSwingTest {
        var tip by mutableStateOf("first")
        setContent {
            CompositionLocalProvider(localTip provides tip) {
                SwingNode(
                    factory = { JPanel() },
                    modifier =
                        SwingModifier.styleable(PlainProbeContainer) {
                            if (readInsideBlock) {
                                provideNestedStyle(Probes) { LabelToolTip.provide(localTip.currentValue) }
                            } else {
                                val read = localTip.currentValue
                                provideNestedStyle(Probes) { LabelToolTip.provide(read) }
                            }
                        },
                ) {
                    CompositionLocalProvider(localTip provides "override") {
                        SwingNode(
                            factory = { JLabel() },
                            modifier = SwingModifier.styleable(NestedProbe, SwingStyle),
                        )
                    }
                }
            }
        }
        onNodeOfType<JLabel>().assertProperty("first") { toolTipText }

        tip = "second"
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("second") { toolTipText }
    }

    @Test
    fun anAdditiveElementInAStyleIsRefused() {
        val additive = stylePropertyOf<Unit> { AdditiveElement }
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                setContent {
                    SwingNode(
                        factory = { JLabel() },
                        modifier = SwingModifier.styleable(PlainProbe) { additive.provide(Unit) },
                    )
                }
            }
        }
    }

    /** A way to combine two styles, and the first property it leaves for the second style's [String] value. */
    sealed class CombinedStyleCase(
        val combine: (SwingStyle<ProbeScope>, SwingStyle<ProbeScope>) -> SwingStyle<ProbeScope>,
        val expectedFirst: (String) -> String,
    ) {
        data object Then : CombinedStyleCase({ a, b -> a then b }, { it })

        data object EmptyPartsDropped : CombinedStyleCase(
            { a, b -> SwingStyle(SwingStyle, a, SwingStyle, b) then SwingStyle },
            { it },
        )

        data object ApplyAfterOwnWrite : CombinedStyleCase(
            { a, b ->
                SwingStyle {
                    apply(a)
                    ProbeFirst.provide(PROBE_OWN_VALUE)
                    apply(b)
                }
            },
            { it },
        )

        data object OwnWriteAfterApply : CombinedStyleCase(
            { a, b ->
                SwingStyle {
                    apply(a)
                    apply(b)
                    ProbeFirst.provide(PROBE_OWN_VALUE)
                }
            },
            { PROBE_OWN_VALUE },
        )
    }

    companion object {
        @JvmStatic
        fun combinedStyles(): List<CombinedStyleCase> = listOf(
            CombinedStyleCase.Then,
            CombinedStyleCase.EmptyPartsDropped,
            CombinedStyleCase.ApplyAfterOwnWrite,
            CombinedStyleCase.OwnWriteAfterApply,
        )

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

private object AdditiveElement : SwingModifier.NodeElement<Component, SwingModifier.ComponentNode<Component>>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val additive: Boolean get() = true

    override fun create(): SwingModifier.ComponentNode<Component> = object : SwingModifier.ComponentNode<Component>() {}

    override fun update(node: SwingModifier.ComponentNode<Component>) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 1
}

private val LocalFlag = staticCompositionLocalOf { true }
