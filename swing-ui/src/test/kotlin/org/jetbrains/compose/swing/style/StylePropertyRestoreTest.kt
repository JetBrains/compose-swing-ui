package org.jetbrains.compose.swing.style

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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
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
                    SwingModifier.styleable({
                        property.provide(retained)
                        if (enabled) customProperty.provide(custom)
                    }, PlainProbe),
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
                    SwingModifier.styleable({
                        FirstAndSecond.provide("both")
                        ProbeSecond.provide("second")
                        if (enabled) customFirst.provide("custom")
                    }, PlainProbe),
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
                    SwingModifier.styleable({
                        custom.provide("custom")
                        LabelText.provide(text)
                    }, PlainProbe),
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
                        SwingModifier.styleable({ property.provide("declared") }, PlainProbe)
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
                modifier = SwingModifier.styleable({ property.provide(value) }, PlainProbe),
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
                modifier = SwingModifier.styleable({ InheritedForeground.provide(color) }, PlainProbeContainer),
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier = SwingModifier.styleable({ text?.let { LabelText.provide(it) } }, PlainProbe),
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
                    SwingModifier.styleable(
                        {
                            text?.let { LabelText.provide(it) }
                            tip?.let { LabelToolTip.provide(it) }
                        },
                        PlainProbe,
                    ),
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
                modifier = SwingModifier.styleable({ InheritedForeground.provide(Color.RED) }, PlainProbeContainer),
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier =
                        if (styled) {
                            SwingModifier.styleable(
                                {
                                    LabelText.provide("styled")
                                    LabelToolTip.provide("styled")
                                },
                                PlainProbe,
                            )
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
                    modifier = SwingModifier.styleable({ LabelText.provide("styled") }, PlainProbe),
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
                modifier = SwingModifier.styleable(style, PlainProbe),
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
                    .styleable({ if (on()) ProbeSecond.provide("style") }, PlainProbe)
            },
            case("a style property a recomposed style stops providing", "style", "modifier") { on ->
                SwingModifier
                    .property(SecondDescriptor, "modifier")
                    .styleable(
                        if (on()) SwingStyle<ProbeScope> { ProbeSecond.provide("style") } else SwingStyle,
                        PlainProbe,
                    )
            },
            case("a style property whose write also lands on another property", "style", "modifier") { on ->
                SwingModifier
                    .property(SecondDescriptor, "modifier")
                    .styleable({ if (on()) FirstAndSecond.provide("style") }, PlainProbe)
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
