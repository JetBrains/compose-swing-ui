// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.components.style.CheckBoxes
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.plaf.ColorUIResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NestedStyleTest {
    @ParameterizedTest
    @MethodSource("nestedCycles")
    fun nestedCyclesAreRejected(case: NestedCycleCase) {
        assertFailsWith<IllegalStateException> {
            runComposeSwingTest {
                setContent {
                    ProbeContainer(StyleProbe(), case.provider) {
                        ProbeLeaf(StyleProbe(), SwingStyle, NestedProbe)
                    }
                }
            }
        }
    }

    @Test
    fun nestedBlockAppliesAnotherKey() = runComposeSwingTest {
        val leaf = StyleProbe()
        setContent {
            ProbeContainer(StyleProbe(), {
                provideNestedStyle(Probes) {
                    applyNestedStyle(OtherProbes)
                    ProbeSecond.provide("probes")
                }
                provideNestedStyle(OtherProbes) { ProbeFirst.provide("other") }
            }) {
                ProbeLeaf(leaf, SwingStyle, NestedProbe)
            }
        }
        assertEquals(listOf("first=other", "second=probes"), leaf.writes)
    }

    sealed class NestedCycleCase(
        internal val provider: SwingStyle<ProbeContainerScope>,
    ) {
        data object Direct : NestedCycleCase(
            { provideNestedStyle(Probes) { applyNestedStyle(Probes) } },
        )

        data object Indirect : NestedCycleCase(
            {
                provideNestedStyle(Probes) { applyNestedStyle(OtherProbes) }
                provideNestedStyle(OtherProbes) { applyNestedStyle(Probes) }
            },
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("precedenceCases")
    fun nestedStylePrecedence(
        content: @Composable () -> Unit,
        verify: ComposeSwingTest.() -> Unit,
    ) = runComposeSwingTest {
        setContent(content)
        verify()
    }

    @Test
    fun aLateNestedStyleReadsTheLatestCheckedDeclaration() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        var publishing by mutableStateOf(false)
        setContent {
            Panel(style = {
                if (publishing) {
                    provideNestedStyle(CheckBoxes) {
                        background(if (isChecked) Color.RED else Color.BLUE)
                    }
                }
            }) {
                CheckBox("check", checked, onCheckedChange = {})
            }
        }

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(true) { isSelected }
        publishing = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(Color.RED) { background }
        checked = false
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(Color.BLUE) { background }
        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(Color.RED) { background }
    }

    @Test
    fun multipleParentStylesShareNestedPropertiesAndRestoreEachRemainingContribution() = runComposeSwingTest {
        var includeFirst by mutableStateOf(true)
        var includeLater by mutableStateOf(false)
        val child = StyleProbe("child")
        val firstStyle =
            SwingStyle<ProbeContainerScope> {
                provideNestedStyle(Probes) {
                    ProbeFirst.provide("first")
                    ProbeSecond.provide("first only")
                }
            }
        val laterStyle =
            SwingStyle<ProbeContainerScope> {
                provideNestedStyle(Probes) {
                    ProbeFirst.provide("later")
                    ProbeThird.provide("later only")
                }
            }
        setContent {
            val modifier =
                SwingModifier
                    .then(if (includeFirst) SwingModifier.styleable(PlainProbeContainer, firstStyle) else SwingModifier)
                    .then(if (includeLater) SwingModifier.styleable(PlainProbeContainer, laterStyle) else SwingModifier)
            SwingNode(factory = { StyleProbe("parent") }, modifier = modifier) {
                ProbeLeaf(child, SwingStyle, NestedProbe)
            }
        }

        onProbe("child")
            .assertProperty("first") { first }
            .assertProperty("first only") { second }
            .assertProperty(PROBE_OWN_VALUE) { third }

        includeLater = true
        awaitIdle()
        onProbe("child")
            .assertProperty("later") { first }
            .assertProperty("first only") { second }
            .assertProperty("later only") { third }

        includeLater = false
        awaitIdle()
        onProbe("child")
            .assertProperty("first") { first }
            .assertProperty("first only") { second }
            .assertProperty(PROBE_OWN_VALUE) { third }

        includeLater = true
        awaitIdle()
        includeFirst = false
        awaitIdle()
        onProbe("child")
            .assertProperty("later") { first }
            .assertProperty(PROBE_OWN_VALUE) { second }
            .assertProperty("later only") { third }
    }

    @Test
    fun parentStylesCanProvideDifferentNestedKeysToOneChild() = runComposeSwingTest {
        val firstStyle =
            SwingStyle<ProbeContainerScope> {
                provideNestedStyle(Probes) { ProbeFirst.provide("first key") }
            }
        val laterStyle =
            SwingStyle<ProbeContainerScope> {
                provideNestedStyle(OtherProbes) { ProbeFourth.provide("other key") }
            }
        val kind =
            probeKind {
                applyNestedStyle(Probes)
                applyNestedStyle(OtherProbes)
            }
        val child = StyleProbe("child")
        setContent {
            val modifier =
                SwingModifier
                    .styleable(PlainProbeContainer, firstStyle)
                    .then(SwingModifier.styleable(PlainProbeContainer, laterStyle))
            SwingNode(factory = { StyleProbe("parent") }, modifier = modifier) {
                ProbeLeaf(child, SwingStyle, kind)
            }
        }

        onProbe("child")
            .assertProperty("first key") { first }
            .assertProperty("other key") { fourth }
    }

    @Test
    fun aNestedKindTracksAChangingProviderAlongsideTheCallerStyle() = runComposeSwingTest {
        var providerPresent by mutableStateOf(false)
        var nestedKindDeclared by mutableStateOf(true)
        val callerStyle = SwingStyle<ProbeScope> { ProbeSecond.provide("caller") }
        setContent {
            ProbeContainer(
                StyleProbe("parent"),
                {
                    if (providerPresent) provideNestedStyle(Probes) { ProbeFirst.provide("provided") }
                },
            ) {
                val caller = SwingModifier.styleable(PlainProbe, callerStyle)
                SwingNode(
                    factory = { StyleProbe("child") },
                    modifier = if (nestedKindDeclared) caller.styleable(NestedProbe, SwingStyle) else caller,
                )
            }
        }

        onProbe("child")
            .assertProperty(PROBE_OWN_VALUE) { first }
            .assertProperty("caller") { second }
        providerPresent = true
        awaitIdle()
        onProbe("child")
            .assertProperty("provided", "a provider that appears reaches a nested kind declared before it") { first }
            .assertProperty("caller") { second }
        providerPresent = false
        awaitIdle()
        onProbe("child")
            .assertProperty(PROBE_OWN_VALUE) { first }
            .assertProperty("caller") { second }
        providerPresent = true
        nestedKindDeclared = false
        awaitIdle()
        onProbe("child")
            .assertProperty(PROBE_OWN_VALUE) { first }
            .assertProperty("caller") { second }
        nestedKindDeclared = true
        awaitIdle()
        onProbe("child")
            .assertProperty("provided") { first }
            .assertProperty("caller") { second }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun nestedBlocksCannotProvideStyles(sameKey: Boolean) = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Panel(style = {
                        provideNestedStyle(Panels) {
                            if (sameKey) {
                                provideNestedStyle(Panels) { background(Color.RED) }
                            } else {
                                provideNestedStyle(Labels) { foreground(Color.RED) }
                            }
                        }
                    }) {
                        Panel {
                            Label("nested")
                        }
                    }
                }
            }
        val message = error.message.orEmpty()
        assertTrue(
            "cannot run inside a provideNestedStyle block" in message,
            "the error must explain that nested blocks cannot provide styles, but was: $message",
        )
    }

    @Test
    fun aStyleProvidedForAllComponentsReachesTheDescendantsButNotTheProvider() = runComposeSwingTest {
        val providerWrites = mutableListOf<Any?>()
        setContent {
            Panel(
                modifier =
                    SwingModifier.testTag("provider").propertyChangeListener("enabled") {
                        providerWrites += it.newValue
                    },
                style = {
                    provideNestedStyle(AllComponents) { enabled(false) }
                    provideNestedStyle(Panels) { background(Color.BLUE) }
                },
            ) {
                Label("label")
                Label("own", style = { enabled(true) })
                Button("button", onClick = {})
                Panel(
                    SwingModifier.testTag("nested"),
                    style = { provideNestedStyle(Labels) { foreground(Color.RED) } },
                ) { Label("deep") }
            }
        }

        onNodeWithText("label").assertProperty(false, "a Label applies AllComponents") { isEnabled }
        onNodeWithText("own").assertProperty(true) { isEnabled }
        onNodeWithTag("nested").assertProperty(Color.BLUE) { background }
        onNodeWithText("deep").assertProperty(Color.RED) { foreground }
        onNodeWithText("button").assertProperty(false, "and so does a Button") { isEnabled }
        onNodeWithTag("nested").assertProperty(false, "and a Panel below the provider") { isEnabled }
        onNodeWithText("deep").assertProperty(false, "the style reaches every depth") { isEnabled }
        onNodeWithTag("provider").assertProperty(true, "the provider does not apply its own nested style") { isEnabled }
        assertEquals(emptyList(), providerWrites)
    }

    @Test
    fun aKindStyleTakesPrecedenceOverAllComponentsFromANearerProvider() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(Buttons) { enabled(true) } }) {
                Panel(style = { provideNestedStyle(AllComponents) { enabled(false) } }) {
                    Button("button", onClick = {})
                    Label("label")
                }
            }
        }

        onNodeWithText("button").assertProperty(true) { isEnabled }
        onNodeWithText("label").assertProperty(false) { isEnabled }
    }

    @Test
    fun aProvidedStyleStaysInItsSubtreeAndAChangeReachesUnderANearerProvider() = runComposeSwingTest {
        var outer by mutableStateOf(Color.RED)
        val own = JLabel()
        setContent {
            Panel(style = { provideNestedStyle(AllComponents) { background(outer) } }) {
                Label("outer")
                Panel(style = { provideNestedStyle(Labels) { enabled(false) } }) { Label("inner") }
            }
            Panel(style = { provideNestedStyle(AllComponents) { enabled(false) } }) { Label("sibling") }
            Label("outside")
        }
        for (expected in listOf(Color.RED, Color.GREEN)) {
            outer = expected
            awaitIdle()

            onNodeWithText("outer").assertProperty(expected, "$expected: the provider's subtree") { background }
            onNodeWithText("inner").apply {
                assertProperty(expected, "$expected: the value reaches under a nearer provider") { background }
                assertProperty(false, "$expected: beside what the nearer provider sets") { isEnabled }
            }
            onNodeWithText("sibling").apply {
                assertProperty(own.background, "$expected: the style stays out of a sibling subtree") { background }
                assertProperty(false, "$expected: the sibling keeps its own provider's style") { isEnabled }
            }
            onNodeWithText("outside").apply {
                assertProperty(own.background, "$expected: a component under no provider") { background }
                assertProperty(true, "$expected: a component under no provider") { isEnabled }
            }
        }
    }

    @Test
    fun aComponentsOwnValueAndAProvidedOneTradePlacesInBothDirections() = runComposeSwingTest {
        var provided by mutableStateOf<Color?>(Color.RED)
        var own by mutableStateOf<Color?>(Color.BLUE)
        setContent {
            Panel(style = { provided?.let { provideNestedStyle(AllComponents) { background(it) } } }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier = SwingModifier.styleable(ProvidedLeafKind) { own?.let { background(it) } },
                    update = { set(Color.YELLOW) { background = it } },
                )
            }
        }
        onNodeWithText("styled").assertProperty(Color.BLUE, "the own value wins") { background }

        own = null
        awaitIdle()
        onNodeWithText("styled").assertProperty(
            Color.RED,
            "the own value leaving shows the provided one",
        ) { background }

        own = Color.BLUE
        awaitIdle()
        onNodeWithText("styled").assertProperty(Color.BLUE, "the own value returning wins again") { background }

        provided = Color.GREEN
        awaitIdle()
        onNodeWithText("styled").assertProperty(
            Color.BLUE,
            "a changed provided value leaves the own value standing",
        ) { background }

        own = null
        awaitIdle()
        onNodeWithText("styled").assertProperty(
            Color.GREEN,
            "the provided value overwrites what the update block set",
        ) { background }

        provided = null
        awaitIdle()
        onNodeWithText("styled").assertProperty(
            Color.YELLOW,
            "both leaving give back what the update block set",
        ) { background }
    }

    @ParameterizedTest(name = "declares a style: {0}")
    @ValueSource(booleans = [true, false])
    fun aProvidedValueReachesAComponentOfAnyKindAndLeavingGivesBackItsOwnUIResource(declaresStyle: Boolean) =
        runComposeSwingTest {
            var provided by mutableStateOf<Color?>(Color.RED)
            val resource = ColorUIResource(Color.ORANGE)
            setContent {
                Panel(style = { provided?.let { provideNestedStyle(AllComponents) { background(it) } } }) {
                    SwingNode(
                        factory = { JLabel("styled").apply { background = resource } },
                        modifier =
                            if (declaresStyle) SwingModifier.styleable(ProvidedLeafKind, SwingStyle) else SwingModifier,
                    )
                }
            }
            onNodeWithText("styled").assertProperty(Color.RED) { background }

            provided = null
            awaitIdle()
            onNodeWithText("styled").assertProperty(
                true,
                "the label holds its own look-and-feel color again",
            ) { background === resource }
        }

    @Test
    fun aProvidedStyleReachesAnInsertedAReorderedAndAReactivatedDescendant() = runComposeSwingTest {
        var showInserted by mutableStateOf(false)
        var items by mutableStateOf(listOf("A", "B"))
        var active by mutableStateOf(true)
        setContent {
            Panel(style = { provideNestedStyle(AllComponents) { background(Color.MAGENTA) } }) {
                Label("first")
                if (showInserted) Label("inserted")
                for (item in items) key(item) { Label(item) }
                ReusableContentHost(active = active) { Label("parkable") }
            }
        }

        fun assertAllProvided(
            count: Int,
            reason: String,
        ) {
            val labels = onAllNodesOfType<JLabel>()
            labels.assertCountEquals(count)
            repeat(count) { labels[it].assertProperty(Color.MAGENTA, reason) { background } }
        }
        assertAllProvided(4, "every descendant takes the provided value")

        showInserted = true
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(5)
        listOf("first", "inserted", "A", "B", "parkable").forEachIndexed { index, expected ->
            onAllNodesOfType<JLabel>()[index].assertProperty(expected) { text }
        }
        onNodeWithText("inserted").assertProperty(Color.MAGENTA, "an inserted descendant") { background }
        val labelA = onNodeWithText("A").fetch<JLabel>()
        val labelB = onNodeWithText("B").fetch<JLabel>()

        items = listOf("B", "A")
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(5)
        listOf("first", "inserted", "B", "A", "parkable").forEachIndexed { index, expected ->
            onAllNodesOfType<JLabel>()[index].assertProperty(expected) { text }
        }
        onAllNodesOfType<JLabel>()[2].assertProperty(true, "the keyed reorder moves B first") { this === labelB }
        onAllNodesOfType<JLabel>()[3].assertProperty(true, "the keyed reorder moves A second") { this === labelA }
        assertAllProvided(5, "a keyed reorder keeps the provided value")

        active = false
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(4)
        onNodeWithText("parkable").assertDoesNotExist()
        active = true
        awaitIdle()
        onNodeWithText("parkable").assertProperty(Color.MAGENTA, "a reactivated descendant") { background }
    }

    @Test
    fun aChangedProvidedStyleKeepsTheComponentsModifierAndOwnStyle() = runComposeSwingTest {
        var providesBackground by mutableStateOf(false)
        setContent {
            Panel(
                style = {
                    if (providesBackground) {
                        provideNestedStyle(AllComponents) { background(Color.BLUE) }
                    } else {
                        provideNestedStyle(Labels) { enabled(false) }
                    }
                },
            ) {
                Button(
                    "button",
                    onClick = {},
                    modifier = SwingModifier.name("tip"),
                    style = { foreground(Color.RED) },
                )
            }
        }
        providesBackground = true
        awaitIdle()

        onNodeOfType<JButton>().apply {
            assertProperty(Color.BLUE, "the style the provider now provides reaches the button") { background }
            assertProperty(Color.RED, "the button's own style stands") { foreground }
            assertProperty("tip", "the button's modifier stands") { name }
        }
    }

    @Test
    fun aProvidedPropertyWithARewriteOnIsWrittenAgainWhenTheComponentOverwritesIt() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(Labels) { OpaqueProperty.provide(true) } }) { Label("opaque") }
        }
        onNodeWithText("opaque").assertProperty(true) { isOpaque }

        onNodeWithText("opaque").fetch<JLabel>().isOpaque = false
        awaitIdle()
        onNodeWithText("opaque")
            .assertProperty(true, "the property announces the overwrite, so the provided value is written again") {
                isOpaque
            }
    }

    @Test
    fun aProvidersOwnWriteIsNotCheckedAsPartOfAConsumersNestingDefault() = runComposeSwingTest {
        setContent {
            Panel(
                modifier = SwingModifier.testTag("provider"),
                style = {
                    background(Color.RED)
                    provideNestedStyle(AllComponents) {
                        foreground(Color.BLUE)
                        background(Color.BLUE)
                    }
                },
            ) {
                SwingNode(
                    factory = { JLabel("third party") },
                    modifier = SwingModifier.styleable(ThirdPartyNestingKind, SwingStyle),
                )
            }
        }

        onNodeWithText("third party")
            .assertProperty(Color.BLUE) { foreground }
            .assertProperty(Color.BLUE) { background }
        onNodeWithTag("provider").assertProperty(Color.RED) { background }
    }

    @Test
    fun theDefaultOfANestingKindCannotWriteAPropertyOutsideANestedStyle() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Panel(style = { provideNestedStyle(AllComponents) { background(Color.BLUE) } }) {
                        SwingNode(
                            factory = { JLabel("writing") },
                            modifier = SwingModifier.styleable(WritingNestingKind, SwingStyle),
                        )
                    }
                }
            }
        val message = error.message.orEmpty()
        assertTrue(
            "The default of a nesting kind may only apply nested styles" in message,
            "the error must explain that nesting kinds cannot provide properties directly, but was: $message",
        )
    }

    companion object {
        @JvmStatic
        fun nestedCycles(): List<NestedCycleCase> = listOf(NestedCycleCase.Direct, NestedCycleCase.Indirect)

        @JvmStatic
        fun precedenceCases(): List<Arguments> {
            val nearer: @Composable () -> Unit = {
                Panel(style = { provideNestedStyle(Labels) { enabled(false) } }) {
                    Panel(style = { provideNestedStyle(Labels) { enabled(true) } }) { Label("enabled nearer") }
                }
                Panel(style = { provideNestedStyle(Labels) { enabled(true) } }) {
                    Panel(style = { provideNestedStyle(Labels) { enabled(false) } }) { Label("disabled nearer") }
                }
            }
            val verifyNearer: ComposeSwingTest.() -> Unit = {
                onNodeWithText("enabled nearer").assertProperty(true) { isEnabled }
                onNodeWithText("disabled nearer").assertProperty(
                    false,
                    "the order of the values plays no part",
                ) { isEnabled }
            }
            val applyingAndProviding: @Composable () -> Unit = {
                Panel(style = { provideNestedStyle(Panels) { background(Color.BLUE) } }) {
                    Panel(
                        modifier = SwingModifier.testTag("nested"),
                        style = { provideNestedStyle(Labels) { foreground(Color.RED) } },
                    ) { Label("nested") }
                }
            }
            val verifyApplyingAndProviding: ComposeSwingTest.() -> Unit = {
                onNodeWithTag("nested").assertProperty(Color.BLUE) { background }
                onNodeWithText("nested").assertProperty(Color.RED) { foreground }
            }
            val own: @Composable () -> Unit = {
                Panel(style = { provideNestedStyle(AllComponents) { enabled(false) } }) {
                    Label("provided")
                    Label("own", style = { enabled(true) })
                }
            }
            val verifyOwn: ComposeSwingTest.() -> Unit = {
                onNodeWithText("provided").assertProperty(false) { isEnabled }
                onNodeWithText("own").assertProperty(
                    true,
                    "the caller's style runs after the provided ones",
                ) { isEnabled }
            }
            val thirdParty: @Composable () -> Unit = {
                Panel(style = { provideNestedStyle(AllComponents) { background(Color.BLUE) } }) {
                    SwingNode(
                        factory = { JLabel("third party") },
                        modifier = SwingModifier.styleable(ThirdPartyNestingKind, SwingStyle),
                    )
                }
            }
            val verifyThirdParty: ComposeSwingTest.() -> Unit = {
                onNodeWithText("third party").assertProperty(Color.BLUE) { background }
            }
            return listOf(
                Arguments.of(Named.of("nearer provider in both orders", nearer), verifyNearer),
                Arguments.of(Named.of("applying and providing", applyingAndProviding), verifyApplyingAndProviding),
                Arguments.of(Named.of("own style", own), verifyOwn),
                Arguments.of(Named.of("third-party nesting kind", thirdParty), verifyThirdParty),
            )
        }
    }
}

/** A kind a third party builds, whose default applies the styles provided for every component. */
private val ThirdPartyNestingKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
        SwingStyle,
    )

/** A kind whose default writes a property of its own, which a nesting kind cannot do. */
private val WritingNestingKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        OpaqueProperty.provide(true)
    }

/** A raw node's kind that applies the styles provided for every component. */
private val ProvidedLeafKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    )

private val OpaqueProperty =
    stylePropertyOf(
        ComponentPropertyDescriptor<JComponent, Boolean>(
            name = "opaque",
            read = { it.isOpaque },
            write = { component, value -> component.isOpaque = value },
        ),
        rewriteOn = "opaque",
    )
