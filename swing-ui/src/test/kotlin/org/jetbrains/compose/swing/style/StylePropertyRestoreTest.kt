package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.style.CaretElement
import org.jetbrains.compose.swing.components.style.DocumentFilterElement
import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.caretBlinkRate
import org.jetbrains.compose.swing.components.style.caretBlinkRateElement
import org.jetbrains.compose.swing.components.style.caretUpdatePolicy
import org.jetbrains.compose.swing.components.style.caretUpdatePolicyElement
import org.jetbrains.compose.swing.components.style.documentFilter
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
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
import javax.swing.text.AbstractDocument
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.DocumentFilter
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun removedPropertiesRestoreOwnValues(removeStyle: Boolean) = runComposeSwingTest {
        val ownForeground = JLabel().foreground
        var text: String? by mutableStateOf("styled")
        var tip: String? by mutableStateOf("styled")
        var color by mutableStateOf(Color.RED)
        var styled by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.styleable({ InheritedForeground.provide(color) }, PlainProbeContainer),
            ) {
                SwingNode(
                    factory = { JLabel("own text") },
                    modifier =
                        if (styled) {
                            SwingModifier.styleable({
                                text?.let { LabelText.provide(it) }
                                tip?.let { LabelToolTip.provide(it) }
                            }, PlainProbe)
                        } else {
                            SwingModifier
                        },
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty("styled") { this.text }.assertProperty(Color.RED) { foreground }
        color = Color.BLUE
        text = "changed"
        awaitIdle()
        label.assertProperty("changed") { this.text }.assertProperty(Color.BLUE) { foreground }

        if (removeStyle) {
            styled = false
        } else {
            text = null
            tip = null
        }
        awaitIdle()
        label
            .assertProperty("own text") { this.text }
            .assertProperty(null) { toolTipText }
            .assertProperty(if (removeStyle) ownForeground else Color.BLUE) { foreground }
    }

    @Test
    fun movedComponentRestoresCapturedValues() = runComposeSwingTest {
        val original = BorderFactory.createLineBorder(Color.RED)
        val label =
            JLabel("X").apply {
                toolTipText = "built-in"
                border = original
            }
        val host = JPanel()
        var styled by mutableStateOf(true)
        var inOuter by mutableStateOf(false)
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(
                            factory = { label },
                            modifier =
                                if (styled) {
                                    SwingModifier.styleable(TestNodeKind) {
                                        toolTip("hint")
                                        emptyBorder(8)
                                    }
                                } else {
                                    SwingModifier
                                },
                        )
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val nested = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()

        inOuter = true
        awaitIdle()
        assertEquals("hint", label.toolTipText, "the style should apply after the move")

        styled = false
        awaitIdle()
        assertEquals("built-in", label.toolTipText, "removing the style should restore the tooltip it carried")
        assertSame(original, label.border, "removing the style should restore the border it carried")
        nested.dispose()
    }

    @Test
    fun removedComponentRestoresOwnText() = runComposeSwingTest {
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
    fun equalElementIsAdoptedThenRestored() = runComposeSwingTest {
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
    fun removedPropertyRestoresModifier(
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
        property: String,
        order: String,
    ) = runComposeSwingTest {
        val case = customOverlapProperty(property)
        var modified by mutableStateOf(true)
        var styled by mutableStateOf(true)
        var modifierValue by mutableIntStateOf(0)
        var original: Any? = null
        setContent {
            SwingNode(
                factory = { JTextField("selection").also { original = case.read(it) } },
                modifier =
                    (if (modified) case.modifier(modifierValue) else SwingModifier)
                        .styleable(TestTextNodeKind) { if (styled) case.declare(this, 2) },
            )
        }
        val field = onNodeOfType<JTextField>()
        // Both attachment orders must share the baseline, including a modifier added beneath a standing style.
        repeat(2) { round ->
            if (round != 0) {
                styled = true
                awaitIdle()
            }
            if (property.startsWith("per-location tooltip")) field.publishToolTip(case)
            if (round != 0) {
                modified = true
                awaitIdle()
            }
            if (property.startsWith("per-location tooltip modifier")) field.publishToolTip(case)
            original = field.replaceOverlapCollaborator(property, case, original)
            field.assertProperty(case.value(2)) { case.read(this) }
            when (order) {
                "modifier removed first" -> {
                    modified = false
                    awaitIdle()
                    field.assertProperty(case.value(2)) { case.read(this) }
                    styled = false
                }

                "style withdrawn first" -> {
                    styled = false
                    awaitIdle()
                    field.assertProperty(case.value(modifierValue)) { case.read(this) }
                    modified = false
                }

                else -> {
                    modifierValue = 1
                    awaitIdle()
                    if (property.startsWith("per-location tooltip modifier")) field.publishToolTip(case)
                    field.assertProperty(case.value(2)) { case.read(this) }
                    styled = false
                    awaitIdle()
                    field.assertProperty(case.value(1)) { case.read(this) }
                    modified = false
                }
            }
            awaitIdle()
            field.assertProperty(original) { case.read(this) }
            modifierValue = 0
        }
    }

    companion object {
        @JvmStatic
        fun customPropertyOverlaps(): List<Arguments> = listOf(
            "tooltip",
            "per-location tooltip",
            "per-location tooltip without text",
            "per-location tooltip modifier beneath constant style",
            "per-location tooltip modifier beneath per-location style",
            "border",
            "caret",
            "blink rate",
            "blink rate after caret swap",
            "blink rate after null caret swap",
            "blink rate after UI replacement",
            "blink rate before declared caret",
            "update policy",
            "update policy after caret swap",
            "update policy after null caret swap",
            "update policy after UI replacement",
            "update policy before declared caret",
            "document filter",
            "document filter after swap",
        ).flatMap { property ->
            listOf("modifier removed first", "style withdrawn first", "modifier changed beneath style")
                .map { order -> Arguments.of(property, order) }
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

private class CustomOverlapProperty(
    val read: (JTextField) -> Any?,
    val value: (Int) -> Any?,
    val modifier: (Int) -> SwingModifier,
    val declare: TextStyleScope.(Int) -> Unit,
)

private fun SwingNodeInteraction<JTextField>.publishToolTip(case: CustomOverlapProperty) {
    assertProperty(case.value(2)) {
        dispatchEvent(MouseEvent(this, MouseEvent.MOUSE_MOVED, 0L, 0, 1, 1, 0, false))
        case.read(this)
    }
}

private fun SwingNodeInteraction<JTextField>.replaceOverlapCollaborator(
    property: String,
    case: CustomOverlapProperty,
    original: Any?,
): Any? {
    var baseline = original
    if (property == "document filter after swap") {
        assertProperty(original) {
            val previous = document as AbstractDocument
            val arriving = PlainDocument().apply { documentFilter = DocumentFilter() }
            baseline = arriving.documentFilter
            document = arriving
            previous.documentFilter
        }
    }
    if (property.endsWith("caret swap") || property.endsWith("UI replacement") || property.endsWith("declared caret")) {
        assertProperty(case.value(2)) {
            when {
                property.endsWith("UI replacement") || property.endsWith("declared caret") -> {
                    updateUI()
                }

                else -> {
                    if (property.endsWith("null caret swap")) caret = null
                    caret = DefaultCaret()
                }
            }
            case.read(this)
        }
    }
    return baseline
}

private fun <V> customOverlapProperty(
    values: List<V>,
    read: (JTextField) -> Any?,
    element: (V) -> SwingModifier.Element,
    declare: TextStyleScope.(V) -> Unit,
): CustomOverlapProperty = CustomOverlapProperty(
    read = read,
    value = { values[it] },
    modifier = { SwingModifier then element(values[it]) },
    declare = { declare(values[it]) },
)

private fun customOverlapProperty(property: String): CustomOverlapProperty = when (property) {
    "per-location tooltip",
    "per-location tooltip without text",
    "per-location tooltip modifier beneath constant style",
    "per-location tooltip modifier beneath per-location style",
    -> {
        perLocationToolTipOverlapProperty(property)
    }

    "tooltip" -> {
        customOverlapProperty(
            listOf("modifier", "changed", "style"),
            { it.toolTipText },
            { ToolTipElement(it, null) },
            { toolTip(it) },
        )
    }

    "border" -> {
        customOverlapProperty(
            List(3) { BorderFactory.createEmptyBorder(it + 1, 0, 0, 0) },
            { it.border },
            { BorderElement(BorderSpec.Instance(it)) },
            { border(it) },
        )
    }

    "caret" -> {
        customOverlapProperty(
            List<Caret>(3) { DefaultCaret() },
            { it.caret },
            { CaretElement(it) },
            { caret(it) },
        )
    }

    "blink rate",
    "blink rate after caret swap",
    "blink rate after null caret swap",
    "blink rate after UI replacement",
    "blink rate before declared caret",
    -> {
        caretBlinkRateOverlapProperty(property)
    }

    "update policy",
    "update policy after caret swap",
    "update policy after null caret swap",
    "update policy after UI replacement",
    "update policy before declared caret",
    -> {
        caretUpdatePolicyOverlapProperty(property)
    }

    else -> {
        customOverlapProperty(
            List(3) { DocumentFilter() },
            { (it.document as AbstractDocument).documentFilter },
            { DocumentFilterElement(it) },
            { documentFilter(it) },
        )
    }
}

private fun perLocationToolTipOverlapProperty(property: String): CustomOverlapProperty {
    val values = listOf("modifier", "changed", if (property == "per-location tooltip without text") null else "style")
    return CustomOverlapProperty(
        read = {
            Triple(
                it.toolTipText,
                it.accessibleContext.accessibleDescription,
                ToolTipManager.sharedInstance() in it.mouseListeners,
            )
        },
        value = { Triple(values[it], values[it], true) },
        modifier = { index ->
            SwingModifier then
                if (property.startsWith("per-location tooltip modifier")) {
                    ToolTipElement(null) { values[index] }
                } else {
                    ToolTipElement(values[index], null)
                }
        },
        declare = { index ->
            if (property == "per-location tooltip modifier beneath constant style") {
                toolTip(values[index])
            } else {
                toolTip { values[index] }
            }
        },
    )
}

private fun caretBlinkRateOverlapProperty(property: String): CustomOverlapProperty {
    val declaredCaret = DefaultCaret()
    return customOverlapProperty(
        listOf(0, 123, 456),
        { it.caret.blinkRate },
        ::caretBlinkRateElement,
        {
            caretBlinkRate(it)
            if (property == "blink rate before declared caret") caret(declaredCaret)
        },
    )
}

private fun caretUpdatePolicyOverlapProperty(property: String): CustomOverlapProperty {
    val declaredCaret = DefaultCaret()
    return customOverlapProperty(
        listOf(DefaultCaret.NEVER_UPDATE, DefaultCaret.UPDATE_WHEN_ON_EDT, DefaultCaret.ALWAYS_UPDATE),
        { (it.caret as DefaultCaret).updatePolicy },
        ::caretUpdatePolicyElement,
        {
            caretUpdatePolicy(it)
            if (property == "update policy before declared caret") caret(declaredCaret)
        },
    )
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
