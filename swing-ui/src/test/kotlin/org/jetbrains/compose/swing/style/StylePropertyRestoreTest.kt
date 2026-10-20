package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a component gets back when a style, or a property of it, leaves, against the restore check every test runs. */
class StylePropertyRestoreTest {
    @Nested
    inner class OverlappingProperties {
        @Test
        fun aStylePropertyCapturesItsAdditionalProperties() = runComposeSwingTest {
            val fixture = AdditionalCaptureFixture()
            setContent { fixture.Content() }

            onNodeOfType<StyleProbe>()
                .assertProperty("initial") { first }
                .assertProperty("initial") { second }
                .assertProperty("initial") { third }
                .assertProperty("original fourth") { fourth }
        }

        @Test
        fun replacingAStylePropertyReconcilesItsAdditionalCaptures() = runComposeSwingTest {
            val fixture = AdditionalCaptureFixture()
            setContent { fixture.Content() }

            fixture.replaced = true
            awaitIdle()

            onNodeOfType<StyleProbe>()
                .assertProperty("replacement") { first }
                .assertProperty("replacement") { second }
                .assertProperty("original third") { third }
                .assertProperty("replacement") { fourth }
        }

        @Test
        fun aReplacedStylePropertyRestoresItsRemainingCapturesOnRemoval() = runComposeSwingTest {
            val fixture = AdditionalCaptureFixture()
            setContent { fixture.Content() }
            fixture.replaced = true
            awaitIdle()

            fixture.styled = false
            awaitIdle()

            onNodeOfType<StyleProbe>()
                .assertProperty("original first") { first }
                .assertProperty("original second") { second }
                .assertProperty("original third") { third }
                .assertProperty("original fourth") { fourth }
        }

        @Test
        fun releasingAnAdditionalCaptureKeepsTheEarlierStyleAboveTheModifier() = runComposeSwingTest {
            val replacement = stylePropertyOf(FirstDescriptor)
            var replaced by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { StyleProbe() },
                    modifier =
                        SwingModifier
                            .property(SecondDescriptor, "modifier")
                            .styleable(PlainProbe, {
                                ProbeSecond.provide("style")
                                if (replaced) replacement.provide("replacement") else FirstAndSecond.provide("initial")
                            }),
                )
            }
            val probe = onNodeOfType<StyleProbe>()
            probe.assertProperty("initial") { second }

            replaced = true
            awaitIdle()

            probe.assertProperty("style") { second }
            probe.assertProperty("replacement") { first }
        }

        @ParameterizedTest(name = "release capture: {0}")
        @ValueSource(booleans = [false, true])
        fun rebindingWatchedCapturesKeepsTheDeclarationInstalled(releaseCapture: Boolean) = runComposeSwingTest {
            val tipDescriptor =
                ComponentPropertyDescriptor<JLabel, String?>(
                    "toolTipText",
                    { it.toolTipText },
                    { label, value -> label.toolTipText = value },
                )
            val nameDescriptor =
                ComponentPropertyDescriptor<JLabel, String?>(
                    "name",
                    { it.name },
                    { label, value ->
                        label.name = value
                        if (value == "both") label.toolTipText = value
                    },
                )
            val nameOnly = stylePropertyOf(nameDescriptor)
            val both = stylePropertyOf(nameDescriptor, alsoOverwrites = listOf(tipDescriptor))
            var overwrites by mutableStateOf(releaseCapture)
            var styled by mutableStateOf(true)
            setContent {
                SwingNode(
                    factory = {
                        JLabel().apply {
                            name = "own name"
                            toolTipText = "own tip"
                        }
                    },
                    modifier =
                        SwingModifier.styleable(PlainProbe, {
                            if (styled) {
                                if (overwrites) both.provide("both") else nameOnly.provide("name only")
                            }
                        }),
                )
            }
            val label = onNodeOfType<JLabel>()
            label.assertProperty(if (releaseCapture) "both" else "name only") { name }
            label.assertProperty(if (releaseCapture) "both" else "own tip") { toolTipText }

            overwrites = !releaseCapture
            awaitIdle()

            label.assertProperty(if (releaseCapture) "name only" else "both") { name }
            label.assertProperty(if (releaseCapture) "own tip" else "both") { toolTipText }

            styled = false
            awaitIdle()

            label.assertProperty("own name") { name }
            label.assertProperty("own tip") { toolTipText }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = ["whole style", "both declarations"])
        fun departingDescriptorAndCustomPropertyRestoreTheOriginal(removal: String) = runComposeSwingTest {
            val events = ArrayList<String>()
            val customProperty = stylePropertyOf { value: String -> LifecycleElement(value, events) }
            var style by
                mutableStateOf<SwingStyle<ProbeScope>?>(
                    SwingStyle {
                        LabelToolTip.provide("descriptor")
                        customProperty.provide("custom")
                    },
                )
            setContent {
                SwingNode(
                    factory = { JLabel().apply { toolTipText = "original" } },
                    modifier = style?.let { SwingModifier.styleable(PlainProbe, it) } ?: SwingModifier,
                )
            }
            val label = onNodeOfType<JLabel>()
            label.assertProperty("custom") { toolTipText }

            style = if (removal == "whole style") null else SwingStyle
            awaitIdle()

            label.assertProperty("original") { toolTipText }
        }
    }

    @Nested
    inner class StylePropertyRewriteTest {
        @ParameterizedTest
        @CsvSource(
            "none, name, declared, declared",
            "name, none, external, external",
            "name, text, external, declared",
            "text, name, declared, declared",
            "name, name, declared, declared",
            "none, none, external, external",
            nullValues = ["none"],
        )
        fun replacingAStylePropertyUpdatesItsRewriteEvent(
            initialEvent: String?,
            replacementEvent: String?,
            expectedName: String,
            expectedAfterTextChange: String,
        ) = runComposeSwingTest {
            val descriptor =
                ComponentPropertyDescriptor<JLabel, String?>(
                    "name",
                    { it.name },
                    { component, value -> component.name = value },
                )
            var property by mutableStateOf(stylePropertyOf(descriptor, rewriteOn = initialEvent))
            var provided by mutableStateOf(true)
            setContent {
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.styleable(PlainProbe, { if (provided) property.provide("declared") }),
                )
            }
            val label = onNodeOfType<JLabel>()
            label.assertProperty("declared") { name }

            property = stylePropertyOf(descriptor, rewriteOn = replacementEvent)
            awaitIdle()
            label.assertProperty("declared") { name }

            label.assertProperty(expectedName) {
                name = "external"
                name
            }
            awaitIdle()

            label.assertProperty(expectedName) { name }

            label.assertProperty("changed") {
                text = "changed"
                text
            }
            awaitIdle()

            label.assertProperty(expectedAfterTextChange) { name }

            provided = false
            awaitIdle()

            label.assertProperty(null) { name }
            label.assertProperty(0) { getPropertyChangeListeners("name").size }
            label.assertProperty(0) { getPropertyChangeListeners("text").size }
        }
    }

    @Test
    fun aDepartingCustomBorderReassertsTheRetainedDescriptor() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        val descriptor =
            ComponentPropertyDescriptor<JComponent, Border?>(
                "border",
                { it.border },
                { component, value -> component.border = value },
            )
        val property = stylePropertyOf(descriptor)
        val customProperty =
            stylePropertyOf { value: Border ->
                checkNotNull(
                    SwingModifier
                        .property(descriptor, value)
                        .foldIn<SwingModifier.Element?>(null) { _, element -> element },
                )
            }
        val retained = EmptyBorder(1, 2, 3, 4)
        val custom = EmptyBorder(5, 6, 7, 8)
        setContent {
            SwingNode(
                factory = { JLabel() },
                modifier =
                    SwingModifier.styleable(PlainProbe, {
                        property.provide(retained)
                        if (enabled) customProperty.provide(custom)
                    }),
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(custom) { border }

        enabled = false
        awaitIdle()

        label.assertProperty(retained) { border }
    }

    @Test
    fun aDepartingCustomElementKeepsLaterDescriptorsInOrder() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        val customFirst =
            stylePropertyOf { value: String ->
                checkNotNull(
                    SwingModifier
                        .property(FirstDescriptor, value)
                        .foldIn<SwingModifier.Element?>(null) { _, element -> element },
                )
            }
        setContent {
            SwingNode(
                factory = { StyleProbe() },
                modifier =
                    SwingModifier.styleable(PlainProbe, {
                        FirstAndSecond.provide("both")
                        ProbeSecond.provide("second")
                        if (enabled) customFirst.provide("custom")
                    }),
            )
        }
        val probe = onNodeOfType<StyleProbe>()
        probe.assertProperty("custom") { first }

        enabled = false
        awaitIdle()

        probe.assertProperty("both") { first }
        probe.assertProperty("second") { second }
    }

    @Test
    fun anEqualCustomElementReassertsItsPropertyAfterADescriptorChanges() = runComposeSwingTest {
        var text by mutableStateOf("first")
        val custom =
            stylePropertyOf { value: String ->
                checkNotNull(
                    SwingModifier
                        .property(LabelTextDescriptor, value)
                        .foldIn<SwingModifier.Element?>(null) { _, element -> element },
                )
            }
        setContent {
            SwingNode(
                factory = { JLabel() },
                modifier =
                    SwingModifier.styleable(PlainProbe, {
                        custom.provide("custom")
                        LabelText.provide(text)
                    }),
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("custom") { this.text }

        text = "second"
        awaitIdle()

        label.assertProperty("custom") { this.text }
    }

    /** A setter may announce a change even when reasserting an equal value. */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun rewriteOnDoesNotReenterItsActiveSetter(fromStyle: Boolean) = runComposeSwingTest {
        val descriptor =
            ComponentPropertyDescriptor<AlwaysNotifyingComponent, String>(
                "value",
                { it.value },
                { component, value -> component.value = value },
            )
        val property = stylePropertyOf(descriptor, rewriteOn = "value")
        setContent {
            SwingNode(
                factory = { AlwaysNotifyingComponent() },
                modifier =
                    if (fromStyle) {
                        SwingModifier.styleable(PlainProbe, { property.provide("declared") })
                    } else {
                        SwingModifier.property(descriptor, "declared", rewriteOn = "value")
                    },
            )
        }
        val component = onNodeOfType<AlwaysNotifyingComponent>()
        component.assertProperty("declared") { value }
        component.assertProperty(1) { writes }

        component.fetch().value = "external"
        awaitIdle()

        component.assertProperty("declared") { value }
        component.assertProperty(3) { writes }
    }

    @Test
    fun aStylePropertyWritesOnceWhenItsSetterFiresItsOwnChangeEvent() = runComposeSwingTest {
        var value by mutableStateOf("first")
        val property =
            stylePropertyOf(
                ComponentPropertyDescriptor<NotifyingComponent, String>(
                    "value",
                    { it.value },
                    { component, next -> component.value = next },
                ),
                rewriteOn = "value",
            )
        setContent {
            SwingNode(
                factory = { NotifyingComponent() },
                modifier = SwingModifier.styleable(PlainProbe, { property.provide(value) }),
            )
        }
        val component = onNodeOfType<NotifyingComponent>()
        component.assertProperty("first") { this.value }
        component.assertProperty(1) { writes }

        value = "second"
        awaitIdle()

        component.assertProperty("second") { this.value }
        component.assertProperty(2) { writes }
    }

    @Test
    fun aStyleAndAnInheritedLocalAreWrittenOnAttachAndFollowChanges() = runComposeSwingTest {
        var text: String? by mutableStateOf("styled")
        var color by mutableStateOf(Color.RED)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.styleable(PlainProbeContainer, { InheritedForeground.provide(color) }),
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier = SwingModifier.styleable(PlainProbe, { text?.let { LabelText.provide(it) } }),
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("styled") { this.text }
        label.assertProperty(Color.RED, "the label inherits the container's local") { foreground }

        color = Color.BLUE
        text = "changed"
        awaitIdle()
        label.assertProperty("changed") { this.text }
        label.assertProperty(Color.BLUE) { foreground }
    }

    @Test
    fun aPropertyThatLeavesTheStyleGetsBackTheComponentsOwnValue() = runComposeSwingTest {
        var text: String? by mutableStateOf("styled")
        var tip: String? by mutableStateOf("styled")
        setContent {
            SwingNode(
                factory = { JLabel("own text") },
                modifier =
                    SwingModifier.styleable(PlainProbe) {
                        text?.let { LabelText.provide(it) }
                        tip?.let { LabelToolTip.provide(it) }
                    },
            )
        }

        text = null
        tip = null
        awaitIdle()

        onNodeOfType<JLabel>()
            .assertProperty("own text") { this.text }
            .assertProperty(null) { toolTipText }
    }

    @Test
    fun aStyleLeavingTheModifierRestoresEveryPropertyItWrote() = runComposeSwingTest {
        val ownForeground = JLabel().foreground
        var styled by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.styleable(PlainProbeContainer) { InheritedForeground.provide(Color.RED) },
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier =
                        if (styled) {
                            SwingModifier.styleable(PlainProbe) {
                                LabelText.provide("styled")
                                LabelToolTip.provide("styled")
                            }
                        } else {
                            SwingModifier
                        },
                )
            }
        }

        styled = false
        awaitIdle()

        onNodeOfType<JLabel>()
            .assertProperty("own text") { text }
            .assertProperty(null) { toolTipText }
            .assertProperty(ownForeground) { foreground }
    }

    @Test
    fun aComponentLeavingTheCompositionRestoresEveryPropertyItsStyleWrote() = runComposeSwingTest {
        val label = JLabel("own text")
        var shown by mutableStateOf(true)
        setContent {
            if (shown) {
                SwingNode(
                    factory = { label },
                    modifier = SwingModifier.styleable(PlainProbe, { LabelText.provide("styled") }),
                )
            }
        }

        shown = false
        awaitIdle()

        assertEquals("own text", label.text)
    }

    @Test
    fun anElementBackedPropertyAttachesIsAdoptedWhileEqualAndRestoresThroughItsElement() = runComposeSwingTest {
        val events = ArrayList<String>()
        val tipProperty = stylePropertyOf { value: String -> LifecycleElement(value, events) }
        var tip: String? by mutableStateOf("styled")
        var text by mutableStateOf("first")
        var pass by mutableIntStateOf(0)
        val style =
            SwingStyle<ProbeScope> {
                tip?.let { tipProperty.provide(it) }
                LabelText.provide(text)
            }
        setContent {
            readPass(pass)
            SwingNode(
                factory = { JLabel().apply { toolTipText = "own tip" } },
                modifier = SwingModifier.styleable(PlainProbe, style),
            )
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(listOf("attach", "update styled"), events)
        label.assertProperty("styled") { toolTipText }
        events.clear()

        pass++
        awaitIdle()
        assertEquals(emptyList(), events, "a pass declaring the same style runs nothing")

        text = "second"
        awaitIdle()
        label.assertProperty("second") { this.text }
        assertEquals(listOf("update styled"), events, "a descriptor write reasserts the custom element after it")
        events.clear()

        tip = null
        awaitIdle()
        assertEquals(listOf("detach"), events)
        label.assertProperty("own tip", "the element restores what it captured") { toolTipText }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("putBacks")
    fun aPropertyLeavingPutsBackTheModifiersDeclarationOfThatProperty(
        declare: (on: () -> Boolean) -> SwingModifier,
        expectedWhileOn: String,
        expectedAfter: String,
    ) = runComposeSwingTest {
        var on by mutableStateOf(true)
        setContent { SwingNode(factory = { StyleProbe("probe") }, modifier = declare { on }) }
        onProbe("probe").assertProperty(expectedWhileOn) { second }

        on = false
        awaitIdle()

        onProbe("probe")
            .assertProperty(PROBE_OWN_VALUE) { first }
            .assertProperty(expectedAfter) { second }
    }

    private class AdditionalCaptureFixture {
        private val thirdDescriptor =
            ComponentPropertyDescriptor<StyleProbe, String?>(
                "third",
                { it.third },
                { probe, value -> probe.third = value },
            )
        private val fourthDescriptor =
            ComponentPropertyDescriptor<StyleProbe, String?>(
                "fourth",
                { it.fourth },
                { probe, value -> probe.fourth = value },
            )
        private val descriptor =
            ComponentPropertyDescriptor<StyleProbe, String?>(
                "first",
                { it.first },
                { probe, value ->
                    probe.first = value
                    probe.second = value
                    when (value) {
                        "initial" -> probe.third = value
                        "replacement" -> probe.fourth = value
                    }
                },
            )
        private val initial = stylePropertyOf(descriptor, alsoOverwrites = listOf(SecondDescriptor, thirdDescriptor))
        private val replacement =
            stylePropertyOf(
                descriptor,
                alsoOverwrites = listOf(fourthDescriptor, SecondDescriptor),
            )
        var replaced by mutableStateOf(false)
        var styled by mutableStateOf(true)

        @Composable
        fun Content(modifier: SwingModifier = SwingModifier) {
            SwingNode(
                factory = {
                    StyleProbe().apply {
                        first = "original first"
                        second = "original second"
                        third = "original third"
                        fourth = "original fourth"
                    }
                },
                modifier =
                    modifier.then(
                        if (styled) {
                            SwingModifier.styleable(PlainProbe, {
                                if (replaced) replacement.provide("replacement") else initial.provide("initial")
                            })
                        } else {
                            SwingModifier
                        },
                    ),
            )
        }
    }

    companion object {
        private val FirstAndSecondDescriptor =
            ComponentPropertyDescriptor<StyleProbe, String?>(
                "first",
                { it.first },
                { probe, value ->
                    probe.first = value
                    probe.second = value
                },
            )

        private val FirstAndSecond =
            stylePropertyOf(FirstAndSecondDescriptor, alsoOverwrites = listOf(SecondDescriptor))

        private fun case(
            name: String,
            expectedWhileOn: String,
            expectedAfter: String,
            declare: (on: () -> Boolean) -> SwingModifier,
        ): Arguments = Arguments.of(Named.of(name, declare), expectedWhileOn, expectedAfter)

        @JvmStatic
        fun putBacks(): List<Arguments> = listOf(
            case("a style property its state stops providing", "style", "modifier") { on ->
                SwingModifier
                    .property(SecondDescriptor, "modifier")
                    .styleable(PlainProbe, { if (on()) ProbeSecond.provide("style") })
            },
            case("a style property a recomposed style stops providing", "style", "modifier") { on ->
                SwingModifier
                    .property(SecondDescriptor, "modifier")
                    .styleable(
                        PlainProbe,
                        if (on()) SwingStyle<ProbeScope> { ProbeSecond.provide("style") } else SwingStyle,
                    )
            },
            case("a style property whose write also lands on another property", "style", "modifier") { on ->
                SwingModifier
                    .property(SecondDescriptor, "modifier")
                    .styleable(PlainProbe, { if (on()) FirstAndSecond.provide("style") })
            },
            case("a modifier property whose write also lands on another property", "both", "second") { on ->
                val second = SwingModifier.property(SecondDescriptor, "second")
                if (on()) {
                    second.property(FirstAndSecondDescriptor, "both", alsoOverwrites = listOf(SecondDescriptor))
                } else {
                    second
                }
            },
        )
    }
}

private val InheritedForeground =
    styleLocalOf(
        ComponentPropertyDescriptor<JComponent, Color?>(
            "foreground",
            { it.foreground },
            { component, value -> component.foreground = value },
        ),
    )

private class NotifyingComponent : JComponent() {
    var writes: Int = 0
        private set

    var value: String = "own"
        set(value) {
            val previous = field
            field = value
            writes++
            firePropertyChange("value", previous, value)
        }
}

/** Announces every setter call, including a write of the value already held. */
private class AlwaysNotifyingComponent : JComponent() {
    /** Setter calls, including external changes and declaration reassertions. */
    var writes = 0
        private set

    /** Property shared by external writes and the declaration under test. */
    var value = "own"
        set(value) {
            field = value
            writes++
            firePropertyChange("value", null, value)
        }
}
