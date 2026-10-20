package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.caretBlinkRate
import org.jetbrains.compose.swing.components.style.caretBlinkRateElement
import org.jetbrains.compose.swing.components.style.caretUpdatePolicy
import org.jetbrains.compose.swing.components.style.caretUpdatePolicyElement
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.ToolTipManager
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals

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
                            .styleable(PlainProbe) {
                                ProbeSecond.provide("style")
                                if (replaced) replacement.provide("replacement") else FirstAndSecond.provide("initial")
                            },
                )
            }
            onNodeOfType<StyleProbe>().assertProperty("initial") { second }

            replaced = true
            awaitIdle()

            onNodeOfType<StyleProbe>().apply {
                assertProperty("style") { second }
                assertProperty("replacement") { first }
            }
        }

        @ParameterizedTest(name = "{0} then {2}")
        @CsvSource(
            "both, both, name only, own tip",
            "name only, own tip, both, both",
        )
        fun rebindingWatchedCapturesKeepsTheDeclarationInstalled(
            nameBefore: String,
            tipBefore: String,
            nameAfter: String,
            tipAfter: String,
        ) = runComposeSwingTest {
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
            val declarations = mapOf("both" to both, "name only" to nameOnly)
            var declared by mutableStateOf(nameBefore)
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
                        SwingModifier.styleable(PlainProbe) {
                            if (styled) {
                                declarations.getValue(declared).provide(declared)
                            }
                        },
                )
            }
            onNodeOfType<JLabel>().apply {
                assertProperty(nameBefore) { name }
                assertProperty(tipBefore) { toolTipText }
            }

            declared = nameAfter
            awaitIdle()

            onNodeOfType<JLabel>().apply {
                assertProperty(nameAfter) { name }
                assertProperty(tipAfter) { toolTipText }
            }

            styled = false
            awaitIdle()

            onNodeOfType<JLabel>().apply {
                assertProperty("own name") { name }
                assertProperty("own tip") { toolTipText }
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
                        SwingModifier.styleable(PlainProbe) {
                            property.provide(retained)
                            if (enabled) customProperty.provide(custom)
                        },
                )
            }
            onNodeOfType<JLabel>().assertProperty(custom) { border }

            enabled = false
            awaitIdle()

            onNodeOfType<JLabel>().assertProperty(retained) { border }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(DepartingStyle::class)
        fun departingDescriptorAndCustomPropertyRestoreTheOriginal(removal: DepartingStyle) = runComposeSwingTest {
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
            onNodeOfType<JLabel>().assertProperty("custom") { toolTipText }

            style = removal.after
            awaitIdle()

            onNodeOfType<JLabel>().assertProperty("original") { toolTipText }
        }
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
                    SwingModifier.styleable(PlainProbe) {
                        FirstAndSecond.provide("both")
                        ProbeSecond.provide("second")
                        if (enabled) customFirst.provide("custom")
                    },
            )
        }
        onNodeOfType<StyleProbe>().assertProperty("custom") { first }

        enabled = false
        awaitIdle()

        onNodeOfType<StyleProbe>().apply {
            assertProperty("both") { first }
            assertProperty("second") { second }
        }
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
                    SwingModifier.styleable(PlainProbe) {
                        custom.provide("custom")
                        LabelText.provide(text)
                    },
            )
        }
        onNodeOfType<JLabel>().assertProperty("custom") { this.text }

        text = "second"
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("custom") { this.text }
    }

    /** A setter may announce a change even when reasserting an equal value. */
    @ParameterizedTest
    @EnumSource(RewriteDeclaration::class)
    fun rewriteOnDoesNotReenterItsActiveSetter(declaration: RewriteDeclaration) = runComposeSwingTest {
        setContent {
            SwingNode(factory = { AlwaysNotifyingComponent() }, modifier = declaration.modifier())
        }
        onNodeOfType<AlwaysNotifyingComponent>().apply {
            assertProperty("declared") { value }
            assertProperty(1) { writes }
        }

        onNodeOfType<AlwaysNotifyingComponent>().fetch().value = "external"
        awaitIdle()

        onNodeOfType<AlwaysNotifyingComponent>().apply {
            assertProperty("declared") { value }
            assertProperty(3) { writes }
        }
    }

    @Nested
    inner class StylePropertyRewriteTest {
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
                    modifier = SwingModifier.styleable(PlainProbe) { property.provide(value) },
                )
            }
            onNodeOfType<NotifyingComponent>().apply {
                assertProperty("first") { this.value }
                assertProperty(1) { writes }
            }

            value = "second"
            awaitIdle()

            onNodeOfType<NotifyingComponent>().apply {
                assertProperty("second") { this.value }
                assertProperty(2) { writes }
            }
        }

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
                    modifier = SwingModifier.styleable(PlainProbe) { if (provided) property.provide("declared") },
                )
            }
            onNodeOfType<JLabel>().assertProperty("declared") { name }

            property = stylePropertyOf(descriptor, rewriteOn = replacementEvent)
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("declared") { name }

            onNodeOfType<JLabel>().assertProperty(expectedName) {
                name = "external"
                name
            }
            awaitIdle()

            onNodeOfType<JLabel>().assertProperty(expectedName) { name }

            onNodeOfType<JLabel>().assertProperty("changed") {
                text = "changed"
                text
            }
            awaitIdle()

            onNodeOfType<JLabel>().assertProperty(expectedAfterTextChange) { name }

            provided = false
            awaitIdle()

            onNodeOfType<JLabel>().apply {
                assertProperty(null) { name }
                assertProperty(0) { getPropertyChangeListeners("name").size }
                assertProperty(0) { getPropertyChangeListeners("text").size }
            }
        }
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
        assertEquals(listOf("attach", "update styled"), events)
        onNodeOfType<JLabel>().assertProperty("styled") { toolTipText }
        events.clear()

        pass++
        awaitIdle()
        assertEquals(emptyList(), events, "a pass declaring the same style runs nothing")

        text = "second"
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("second") { this.text }
        assertEquals(listOf("update styled"), events, "a descriptor write reasserts the custom element after it")
        events.clear()

        tip = null
        awaitIdle()
        assertEquals(listOf("detach"), events)
        onNodeOfType<JLabel>().assertProperty("own tip", "the element restores what it captured") { toolTipText }
    }

    @Test
    fun aStyleAndAnInheritedLocalAreWrittenOnAttachAndFollowChanges() = runComposeSwingTest {
        var text: String? by mutableStateOf("styled")
        var color by mutableStateOf(Color.RED)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.styleable(PlainProbeContainer) { InheritedForeground.provide(color) },
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier = SwingModifier.styleable(PlainProbe) { text?.let { LabelText.provide(it) } },
                )
            }
        }
        onNodeOfType<JLabel>().apply {
            assertProperty("styled") { this.text }
            assertProperty(Color.RED, "the label inherits the container's local") { foreground }
        }

        color = Color.BLUE
        text = "changed"
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty("changed") { this.text }
            assertProperty(Color.BLUE) { foreground }
        }
    }

    @Test
    fun aPropertyThatLeavesTheStyleGetsBackTheComponentsOwnValue() = runComposeSwingTest {
        var text: String? by mutableStateOf("styled")
        var tip: String? by mutableStateOf("styled")
        var color by mutableStateOf(Color.RED)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.styleable(PlainProbeContainer) { InheritedForeground.provide(color) },
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier =
                        SwingModifier.styleable(PlainProbe) {
                            text?.let { LabelText.provide(it) }
                            tip?.let { LabelToolTip.provide(it) }
                        },
                )
            }
        }

        onNodeOfType<JLabel>().apply {
            assertProperty("styled") { this.text }
            assertProperty(Color.RED) { foreground }
        }
        color = Color.BLUE
        text = "changed"
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty("changed") { this.text }
            assertProperty(Color.BLUE) { foreground }
        }

        text = null
        tip = null
        awaitIdle()

        onNodeOfType<JLabel>()
            .assertProperty("own text") { this.text }
            .assertProperty(null) { toolTipText }
            .assertProperty(Color.BLUE) { foreground }
    }

    @Test
    fun aStyleLeavingTheModifierRestoresEveryPropertyItWrote() = runComposeSwingTest {
        val ownForeground = JLabel().foreground
        var styled by mutableStateOf(true)
        var text by mutableStateOf("styled")
        var color by mutableStateOf(Color.RED)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.styleable(PlainProbeContainer) { InheritedForeground.provide(color) },
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier =
                        if (styled) {
                            SwingModifier.styleable(PlainProbe) {
                                LabelText.provide(text)
                                LabelToolTip.provide("styled")
                            }
                        } else {
                            SwingModifier
                        },
                )
            }
        }

        onNodeOfType<JLabel>().apply {
            assertProperty("styled") { this.text }
            assertProperty(Color.RED) { foreground }
        }
        color = Color.BLUE
        text = "changed"
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty("changed") { this.text }
            assertProperty(Color.BLUE) { foreground }
        }

        styled = false
        awaitIdle()

        onNodeOfType<JLabel>()
            .assertProperty("own text") { this.text }
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
                    modifier = SwingModifier.styleable(PlainProbe) { LabelText.provide("styled") },
                )
            }
        }

        shown = false
        awaitIdle()

        assertEquals("own text", label.text)
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

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("customPropertyOverlaps")
    fun customPropertiesShareModifierAndStyleBaseline(
        property: OverlapProperty,
        order: RemovalOrder,
    ) = runComposeSwingTest {
        assertCustomPropertyBaseline(property, order)
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
                            SwingModifier.styleable(PlainProbe) {
                                if (replaced) replacement.provide("replacement") else initial.provide("initial")
                            }
                        } else {
                            SwingModifier
                        },
                    ),
            )
        }
    }

    companion object {
        @JvmStatic
        fun customPropertyOverlaps(): List<Arguments> = (
            OverlapProperty.entries - OverlapProperty.toolTips -
                setOf(
                    OverlapProperty.Caret,
                )
        ).flatMap { property ->
            RemovalOrder.entries.map { order -> Arguments.of(property, order) }
        }

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
                    .styleable(PlainProbe) { if (on()) ProbeSecond.provide("style") }
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
                    .styleable(PlainProbe) { if (on()) FirstAndSecond.provide("style") }
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

enum class DepartingStyle(
    internal val after: SwingStyle<ProbeScope>?,
) {
    WholeStyle(null),
    BothDeclarations(SwingStyle),
}

private val AlwaysNotifyingValue =
    ComponentPropertyDescriptor<AlwaysNotifyingComponent, String>(
        "value",
        { it.value },
        { component, value -> component.value = value },
    )

private val AlwaysNotifyingValueProperty = stylePropertyOf(AlwaysNotifyingValue, rewriteOn = "value")

enum class RewriteDeclaration(
    val modifier: () -> SwingModifier,
) {
    FromStyle({ SwingModifier.styleable(PlainProbe) { AlwaysNotifyingValueProperty.provide("declared") } }),
    FromModifier({ SwingModifier.property(AlwaysNotifyingValue, "declared", rewriteOn = "value") }),
}

private val InheritedForeground =
    styleLocalOf(
        ComponentPropertyDescriptor<JComponent, Color?>(
            "foreground",
            { it.foreground },
            { component, value -> component.foreground = value },
        ),
    )

internal class CustomOverlapProperty(
    val read: (JTextField) -> Any?,
    val value: (Int) -> Any?,
    val modifier: (Int) -> SwingModifier,
    val declare: TextStyleScope.(Int) -> Unit,
    val publishesToolTip: ToolTipPublication = ToolTipPublication.Never,
    val swap: CollaboratorSwap? = null,
) {
    @Composable
    fun Field(
        modifierValue: Int?,
        styleValue: Int?,
        modifier: SwingModifier = SwingModifier,
    ) {
        TextField(
            "selection",
            onValueChange = {},
            modifier = modifier.then(modifierValue?.let(this.modifier) ?: SwingModifier),
            style = {
                styleValue?.let { declare(this, it) }
            },
        )
    }
}

private fun SwingNodeInteraction<JTextField>.publishToolTip(case: CustomOverlapProperty) {
    assertProperty(case.value(2)) {
        dispatchEvent(MouseEvent(this, MouseEvent.MOUSE_MOVED, 0L, 0, 1, 1, 0, false))
        case.read(this)
    }
}

/** When a per-location tooltip needs the pointer moved over the field before it answers. */
internal enum class ToolTipPublication(
    val afterStyle: Boolean,
    val afterModifier: Boolean,
) {
    Never(afterStyle = false, afterModifier = false),
    AfterStyle(afterStyle = true, afterModifier = false),
    AfterStyleAndModifier(afterStyle = true, afterModifier = true),
}

/**
 * Replaces the collaborator a custom property is read through, given the field, the case and the baseline
 * read before, and returns the baseline the property then restores.
 */
internal typealias CollaboratorSwap = (SwingNodeInteraction<JTextField>, CustomOverlapProperty, Any?) -> Any?

private fun swappingCaret(change: JTextField.() -> Unit): CollaboratorSwap = { field, case, original ->
    field.assertProperty(case.value(2)) {
        change()
        case.read(this)
    }
    original
}

private val ReplacesCaret = swappingCaret { caret = DefaultCaret() }
private val ReplacesNullCaret =
    swappingCaret {
        caret = null
        caret = DefaultCaret()
    }
private val ReplacesUi = swappingCaret { updateUI() }

/** The order a case takes the modifier and the style off a field they both write. */
enum class RemovalOrder { ModifierRemovedFirst, StyleWithdrawnFirst, ModifierChangedBeneathStyle }

/** Each custom property a modifier and a style both write on a text field, and what its case does around it. */
enum class OverlapProperty(
    internal val build: () -> CustomOverlapProperty,
) {
    ToolTip({
        customOverlapProperty(
            listOf("modifier", "changed", "style"),
            { it.toolTipText },
            { ToolTipElement(it, null) },
        ) {
            toolTip(it)
        }
    }),
    PerLocationToolTip({ perLocationToolTipOverlapProperty() }),
    PerLocationToolTipWithoutText({ perLocationToolTipOverlapProperty(styleText = null) }),
    PerLocationToolTipModifierBeneathConstantStyle({
        perLocationToolTipOverlapProperty(
            publishesToolTip = ToolTipPublication.AfterStyleAndModifier,
            modifier = { text -> ToolTipElement(null) { text } },
            declare = { toolTip(it) },
        )
    }),
    PerLocationToolTipModifierBeneathPerLocationStyle({
        perLocationToolTipOverlapProperty(
            publishesToolTip = ToolTipPublication.AfterStyleAndModifier,
            modifier = { text -> ToolTipElement(null) { text } },
        )
    }),
    Border({
        customOverlapProperty(
            List(3) { BorderFactory.createEmptyBorder(it + 1, 0, 0, 0) },
            { it.border },
            { BorderElement(BorderSpec.Instance(it)) },
            declare = { border(it) },
        )
    }),
    Caret({ caretOverlapProperty() }),
    BlinkRate({ caretBlinkRateOverlapProperty() }),
    BlinkRateAfterCaretSwap({ caretBlinkRateOverlapProperty(ReplacesCaret) }),
    BlinkRateAfterNullCaretSwap({ caretBlinkRateOverlapProperty(ReplacesNullCaret) }),
    BlinkRateAfterUiReplacement({ caretBlinkRateOverlapProperty(ReplacesUi) }),
    BlinkRateBeforeDeclaredCaret({
        val declared = DefaultCaret()
        caretBlinkRateOverlapProperty(ReplacesUi) { caret(declared) }
    }),
    UpdatePolicy({ caretUpdatePolicyOverlapProperty() }),
    UpdatePolicyAfterCaretSwap({ caretUpdatePolicyOverlapProperty(ReplacesCaret) }),
    UpdatePolicyAfterNullCaretSwap({ caretUpdatePolicyOverlapProperty(ReplacesNullCaret) }),
    UpdatePolicyAfterUiReplacement({ caretUpdatePolicyOverlapProperty(ReplacesUi) }),
    UpdatePolicyBeforeDeclaredCaret({
        val declared = DefaultCaret()
        caretUpdatePolicyOverlapProperty(ReplacesUi) { caret(declared) }
    }),
    ;

    companion object {
        internal val toolTips =
            listOf(
                ToolTip,
                PerLocationToolTip,
                PerLocationToolTipWithoutText,
                PerLocationToolTipModifierBeneathConstantStyle,
                PerLocationToolTipModifierBeneathPerLocationStyle,
            )
    }
}

private fun <V> customOverlapProperty(
    values: List<V>,
    read: (JTextField) -> Any?,
    element: (V) -> SwingModifier.Element,
    swap: CollaboratorSwap? = null,
    declare: TextStyleScope.(V) -> Unit,
): CustomOverlapProperty = CustomOverlapProperty(
    read = read,
    value = { values[it] },
    modifier = { SwingModifier then element(values[it]) },
    declare = { declare(values[it]) },
    swap = swap,
)

private fun perLocationToolTipOverlapProperty(
    styleText: String? = "style",
    publishesToolTip: ToolTipPublication = ToolTipPublication.AfterStyle,
    modifier: (String?) -> SwingModifier.Element = { ToolTipElement(it, null) },
    declare: TextStyleScope.(String?) -> Unit = { text -> toolTip { text } },
): CustomOverlapProperty {
    val values = listOf("modifier", "changed", styleText)
    return CustomOverlapProperty(
        read = {
            Triple(
                it.toolTipText,
                it.accessibleContext.accessibleDescription,
                ToolTipManager.sharedInstance() in it.mouseListeners,
            )
        },
        value = { Triple(values[it], values[it], true) },
        modifier = { index -> SwingModifier then modifier(values[index]) },
        declare = { index -> declare(values[index]) },
        publishesToolTip = publishesToolTip,
    )
}

private fun caretBlinkRateOverlapProperty(
    swap: CollaboratorSwap? = null,
    alsoDeclare: TextStyleScope.() -> Unit = {},
): CustomOverlapProperty = customOverlapProperty(
    listOf(0, 123, 456),
    { it.caret.blinkRate },
    ::caretBlinkRateElement,
    swap,
) {
    caretBlinkRate(it)
    alsoDeclare()
}

private fun caretUpdatePolicyOverlapProperty(
    swap: CollaboratorSwap? = null,
    alsoDeclare: TextStyleScope.() -> Unit = {},
): CustomOverlapProperty = customOverlapProperty(
    listOf(DefaultCaret.NEVER_UPDATE, DefaultCaret.UPDATE_WHEN_ON_EDT, DefaultCaret.ALWAYS_UPDATE),
    { (it.caret as DefaultCaret).updatePolicy },
    ::caretUpdatePolicyElement,
    swap,
) {
    caretUpdatePolicy(it)
    alsoDeclare()
}

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

internal suspend fun ComposeSwingTest.assertCustomPropertyBaseline(
    property: OverlapProperty,
    order: RemovalOrder,
) {
    val case = property.build()
    var modified by mutableStateOf(false)
    var styled by mutableStateOf(false)
    var modifierValue by mutableIntStateOf(0)
    var original: Any? = null
    setContent {
        case.Field(if (modified) modifierValue else null, if (styled) 2 else null)
    }
    onNodeOfType<JTextField>().assertProperty(true) {
        original = case.read(this)
        true
    }
    modified = true
    styled = true
    awaitIdle()
    // Both attachment orders must share the baseline, including a modifier added beneath a standing style.
    repeat(2) { round ->
        if (round != 0) {
            styled = true
            awaitIdle()
        }
        if (case.publishesToolTip.afterStyle) onNodeOfType<JTextField>().publishToolTip(case)
        if (round != 0) {
            modified = true
            awaitIdle()
        }
        if (case.publishesToolTip.afterModifier) onNodeOfType<JTextField>().publishToolTip(case)
        case.swap?.let { swap -> original = swap(onNodeOfType<JTextField>(), case, original) }
        onNodeOfType<JTextField>().assertProperty(case.value(2)) { case.read(this) }
        when (order) {
            RemovalOrder.ModifierRemovedFirst -> {
                modified = false
                awaitIdle()
                onNodeOfType<JTextField>().assertProperty(case.value(2)) { case.read(this) }
                styled = false
            }

            RemovalOrder.StyleWithdrawnFirst -> {
                styled = false
                awaitIdle()
                onNodeOfType<JTextField>().assertProperty(case.value(modifierValue)) { case.read(this) }
                modified = false
            }

            RemovalOrder.ModifierChangedBeneathStyle -> {
                modifierValue = 1
                awaitIdle()
                if (case.publishesToolTip.afterModifier) onNodeOfType<JTextField>().publishToolTip(case)
                onNodeOfType<JTextField>().assertProperty(case.value(2)) { case.read(this) }
                styled = false
                awaitIdle()
                onNodeOfType<JTextField>().assertProperty(case.value(1)) { case.read(this) }
                modified = false
            }
        }
        awaitIdle()
        onNodeOfType<JTextField>().assertProperty(original) { case.read(this) }
        modifierValue = 0
    }
}
