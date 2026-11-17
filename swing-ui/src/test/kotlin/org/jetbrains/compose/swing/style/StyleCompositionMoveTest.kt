// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasTestTag
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
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
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

internal class StyleCompositionMoveTest {
    @Test
    fun movedComponentRestoresCapturedValues() = runComposeSwingTest {
        val original = BorderFactory.createLineBorder(Color.RED)
        var styled by mutableStateOf(true)
        var inOuter by mutableStateOf(false)
        val moved =
            movableContentOf {
                SwingNode(
                    factory = {
                        JLabel("X").apply {
                            toolTipText = "built-in"
                            border = original
                        }
                    },
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
        nestedContent({ inOuter }, moved)
        awaitIdle()
        inOuter = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("hint", "the style should apply after the move") { toolTipText }

        styled = false
        awaitIdle()
        onNodeOfType<JLabel>()
            .assertProperty("built-in", "removing the style should restore the tooltip it carried") { toolTipText }
            .assertProperty(true, "removing the style should restore the border it carried") { border === original }
    }

    @Test
    fun aStyledComponentMovedToAnotherCompositionKeepsObservingThroughBothKindsOfDeclaration() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var hosted by mutableStateOf(true)
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
        val moved = movableContentOf { Label("moved", modifier = modifier) }
        nestedContent({ inOuter }, moved) { hosted }
        awaitIdle()
        inOuter = true
        awaitIdle()
        hosted = false
        awaitIdle()
        watched.intValue++
        awaitIdle()

        assertEquals(listOf(1, 1), listOf(propertyObserved, layoutObserved), "both keep observing in their new place")
    }

    @Test
    fun aStyleChangeAfterAnAttachFailedOnMoveSurfacesTheFailingPropertyAndKeepsEarlierValues() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var declared by mutableStateOf("v0")
        var declaresFirst by mutableStateOf(true)
        val attachFailures = AttachFailures()
        val failing = stylePropertyOf { value: String -> FailingAttachElement(value, attachFailures) }
        val moved =
            movableContentOf {
                SwingNode(
                    factory = { StyleProbe("leaf") },
                    modifier =
                        SwingModifier
                            .property(FirstDescriptor, "mod")
                            .styleable(PlainProbe) {
                                if (declaresFirst) ProbeFirst.provide(declared)
                                failing.provide(declared)
                                ProbeSecond.provide(declared)
                            },
                )
            }
        nestedContent({ inOuter }, moved)
        awaitIdle()
        val probe = onProbe("leaf").assertProperty("v0") { first }.fetch()

        attachFailures.armed = true
        inOuter = true
        assertFailsWith<IllegalStateException> { awaitIdle() }

        declared = "v1"
        assertFailsWith<IllegalStateException> { awaitIdle() }
        assertEquals("v1", probe.first)

        declaresFirst = false
        assertFailsWith<IllegalStateException> { awaitIdle() }
        assertEquals("mod", probe.first)
    }

    private fun ComposeSwingTest.nestedContent(
        inOuter: () -> Boolean,
        moved: @Composable () -> Unit,
        hosted: () -> Boolean = { true },
    ) {
        setContent {
            val context = rememberCompositionContext()
            if (hosted()) {
                NestedContentHost(layout = RecordingMeasurementLayout(FlowLayout()), parent = context) {
                    if (!inOuter()) moved()
                }
            }
            SwingNode(factory = { JPanel(RecordingMeasurementLayout(FlowLayout())) }) {
                if (inOuter()) moved()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun removedStyleRestoresModifierAfterMove(dropsWholeStyle: Boolean) = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var declared by mutableStateOf(true)
        val moved =
            movableContentOf {
                SwingNode(
                    factory = { StyleProbe("leaf") },
                    modifier =
                        SwingModifier
                            .property(FirstDescriptor, "mod")
                            .styleable(PlainProbe) {
                                if (declared || !dropsWholeStyle) ProbeSecond.provide("s2")
                                if (declared) ProbeFirst.provide("styled")
                            },
                )
            }
        nestedContent({ inOuter }, moved)
        awaitIdle()
        onProbe("leaf").assertProperty("styled") { first }.assertProperty("s2") { second }

        inOuter = true
        declared = false
        awaitIdle()
        onProbe("leaf")
            .assertProperty("mod") { first }
            .assertProperty(if (dropsWholeStyle) PROBE_OWN_VALUE else "s2") { second }
    }

    @Test
    fun aStylePropertyMovedToAnotherCompositionKeepsTheTextEditedAfterInstallation() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var declaredText by mutableStateOf("declared")
        val initialText = stylePropertyOf(::InstalledTextElement)
        val moved =
            movableContentOf {
                Label("own", modifier = SwingModifier.styleable(PlainProbe) { initialText.provide(declaredText) })
            }
        nestedContent({ inOuter }, moved)
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("declared") { text }
        onNodeOfType<JLabel>().fetch().text = "edited"
        onNodeOfType<JLabel>().assertProperty("edited") { text }

        inOuter = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("edited") { text }

        declaredText = "changed"
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("changed") { text }
    }

    @Test
    fun droppingATooltipAfterMovingToAnotherCompositionRestoresTheTextItWasAttachedOver() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var overridden by mutableStateOf(true)
        val tooltipFromText = stylePropertyOf(::TooltipFromTextElement)
        val moved =
            movableContentOf {
                Label(
                    "original",
                    modifier =
                        SwingModifier.styleable(PlainProbe) {
                            LabelText.provide("earlier")
                            if (overridden) tooltipFromText.provide("later")
                        },
                )
            }
        nestedContent({ inOuter }, moved)
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("later") { toolTipText }

        inOuter = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("later") { toolTipText }

        overridden = false
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("earlier") { toolTipText }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun providedTooltipDetachesOnceOnMove(replaced: Boolean) = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var declared by mutableStateOf(true)
        val events = ArrayList<String>()
        val tooltip =
            stylePropertyOf<String> { value ->
                if (value == "second") TooltipFromTextElement(value) else LifecycleTooltipElement(value, events)
            }
        val moved =
            movableContentOf {
                SwingNode(
                    factory = {
                        JLabel("original").apply {
                            toolTipText = "original"
                        }
                    },
                    modifier = SwingModifier.styleable(NestedProbe, SwingStyle),
                )
            }
        setContent {
            val context = rememberCompositionContext()
            NestedContentHost(parent = context) {
                ProbeContainer(null, {
                    provideNestedStyle(Probes) { tooltip.provide(if (replaced) "first" else "provided") }
                }) { if (!inOuter) moved() }
            }
            ProbeContainer(null, {
                if (replaced) provideNestedStyle(Probes) { if (declared) tooltip.provide("second") }
            }) { if (inOuter) moved() }
        }
        awaitIdle()
        onNodeOfType<JLabel>()
            .assertProperty(if (replaced) "first" else "provided") { toolTipText }
        assertEquals(listOf("attach"), events)
        inOuter = true
        awaitIdle()
        onNodeOfType<JLabel>()
            .assertProperty(if (replaced) "second" else "original") { toolTipText }
        assertEquals(listOf("attach", "detach"), events)
        if (replaced) {
            declared = false
            awaitIdle()
            onNodeOfType<JLabel>()
                .assertProperty("original") { toolTipText }
            assertEquals(listOf("attach", "detach"), events)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("movedComponents")
    fun <C : JComponent> styledContentMovedToAnotherCompositionResolvesWithItsNewParent(
        node: @Composable () -> Unit,
        host: @Composable (CompositionContext, String, @Composable () -> Unit) -> Unit,
        find: ComposeSwingTest.() -> SwingNodeInteraction<C>,
        text: (C) -> String?,
    ) = runComposeSwingTest {
        var inFirst by mutableStateOf(true)
        val moved = movableContentOf { node() }
        setContent {
            val context = rememberCompositionContext()
            host(context, "first") { if (inFirst) moved() }
            host(context, "second") { if (!inFirst) moved() }
        }

        find().assertProperty("first") { text(this) }
        val changes = mutableListOf<String?>()
        val leaf = find().fetch()
        leaf.addPropertyChangeListener("text") { changes += text(leaf) }

        inFirst = false
        awaitIdle()
        find().assertProperty("second") { text(this) }
        assertEquals<List<String?>>(listOf("own", "second"), changes)
    }

    @Test
    fun movedSubtreeFollowsNewAncestors() = runComposeSwingTest {
        var underFirst by mutableStateOf(true)
        setContent {
            val moved =
                remember {
                    movableContentOf {
                        ProbeContainer(
                            null,
                            SwingStyle,
                        ) { ProbeLeaf("leaf", SwingStyle, NestedProbe) }
                    }
                }
            ProbeContainer(null, SwingStyle) {
                ProbeContainer(null, {
                    provideNestedStyle(Probes) {
                        ProbeFirst.provide("first parent")
                        ProbeSecond.provide("first parent")
                    }
                }) { if (underFirst) moved() }
                ProbeContainer(null, { provideNestedStyle(Probes) { ProbeFirst.provide("second parent") } }) {
                    if (!underFirst) moved()
                }
            }
        }
        onProbe("leaf")
            .assertProperty("first parent") { first }
            .assertProperty("first parent") { second }

        underFirst = false
        awaitIdle()

        onProbe("leaf")
            .assertProperty("second parent") { first }
            .assertProperty(PROBE_OWN_VALUE) { second }
    }

    @Test
    fun anUnstyledLabelMovedUnderAProviderTakesItsStyleAndRestoresMovedOut() = runComposeSwingTest {
        var underProvider by mutableStateOf(false)
        setContent {
            val moved =
                remember {
                    movableContentOf {
                        Panel(style = { provideNestedStyle(Labels) { foreground(Color.GREEN) } }) { Label("moved") }
                    }
                }
            Panel(style = { provideNestedStyle(Labels) { background(Color.RED) } }) { if (underProvider) moved() }
            Panel { if (!underProvider) moved() }
        }
        val original = UIManager.getColor("Label.background")
        onNodeWithText("moved").assertProperty(original) { background }.assertProperty(Color.GREEN) { foreground }
        val label = onNodeWithText("moved").fetch()

        underProvider = true
        awaitIdle()
        onNodeWithText("moved").assertProperty(Color.RED) { background }.assertProperty(Color.GREEN) { foreground }
        assertSame(label, onNodeWithText("moved").fetch())

        underProvider = false
        awaitIdle()
        onNodeWithText("moved").assertProperty(original) { background }.assertProperty(Color.GREEN) { foreground }
        assertSame(label, onNodeWithText("moved").fetch())
    }

    // The test's subject is one component handed to a second node after the first removed it, so it keeps handing in
    // the component it built.
    @Suppress("SwingNodeFactoryReusesComponent")
    @Test
    fun removedLeafRestoresBeforeReuse() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        var present by mutableStateOf(true)
        var underSecond by mutableStateOf(false)
        setContent {
            ProbeContainer(null, { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } }) {
                if (present) {
                    SwingNode(factory = { leaf }, modifier = SwingModifier.styleable(NestedProbe, SwingStyle))
                }
            }
            ProbeContainer(null, { provideNestedStyle(Probes) { ProbeFirst.provide("second provider") } }) {
                if (underSecond) {
                    SwingNode(factory = { leaf }, modifier = SwingModifier.styleable(NestedProbe, SwingStyle))
                }
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

    companion object {
        @JvmStatic
        fun movedComponents(): List<Arguments> {
            val labelKind =
                probeKind {
                    LabelText.provide("default")
                    applyNestedStyle(Probes)
                }
            val menuKind =
                probeKind {
                    MenuText.provide("default")
                    applyNestedStyle(Probes)
                }
            val labelNode: @Composable () -> Unit = {
                SwingNode(factory = { JLabel("own") }, modifier = SwingModifier.styleable(labelKind, SwingStyle))
            }
            val menuNode: @Composable () -> Unit = {
                Menu("own", modifier = SwingModifier.testTag("leaf").styleable(menuKind, SwingStyle))
            }
            val labelHost: @Composable (CompositionContext, String, @Composable () -> Unit) -> Unit =
                { context, value, content ->
                    NestedContentHost(parent = context) {
                        ProbeContainer(null, { provideNestedStyle(Probes) { LabelText.provide(value) } }) {
                            content()
                        }
                    }
                }
            val menuHost: @Composable (CompositionContext, String, @Composable () -> Unit) -> Unit =
                { context, value, content ->
                    NestedMenuHost(context) { MenuParent(value, content) }
                }
            val findLabel: ComposeSwingTest.() -> SwingNodeInteraction<JLabel> = { onNodeOfType<JLabel>() }
            val findMenu: ComposeSwingTest.() -> SwingNodeInteraction<JMenu> = {
                onAllNodesOfType<JMenu>().filterToOne(hasTestTag("leaf"))
            }
            val labelText: (JLabel) -> String? = { it.text }
            val menuText: (JMenu) -> String? = { it.text }
            return listOf(
                Arguments.of(Named.of("label", labelNode), labelHost, findLabel, labelText),
                Arguments.of(Named.of("menu", menuNode), menuHost, findMenu, menuText),
            )
        }
    }
}

private data class TooltipFromTextElement(
    private val value: String,
) : SwingModifier.NodeElement<JLabel, TooltipFromTextElement.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val name: String get() = "toolTipText"

    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.toolTipText = value
    }

    class Node : SwingModifier.ComponentNode<JLabel>() {
        private var captured: String? = null

        override fun onAttach() {
            captured = component.text
        }

        override fun onDetach() {
            component.toolTipText = captured
        }
    }
}

private data class InstalledTextElement(
    private val value: String,
) : SwingModifier.NodeElement<JLabel, InstalledTextElement.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val name: String get() = "text"

    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node()

    override fun update(node: Node) = node.apply(value)

    class Node : SwingModifier.ComponentNode<JLabel>() {
        private var installed: String? = null

        fun apply(value: String) {
            if (installed != value) {
                component.text = value
                installed = value
            }
        }
    }
}

private val MenuText =
    stylePropertyOf(
        ComponentPropertyDescriptor<JMenu, String?>(
            "text",
            { it.text },
            { component, value -> component.text = value },
        ),
    )

private class NestedMenuPanel : JPanel() {
    private var mountedUnder: CompositionContext? = null
    private var mount: DisposableHandle? = null

    fun mount(
        under: CompositionContext,
        content: State<@Composable () -> Unit>,
    ) {
        if (under === mountedUnder) return
        mount?.dispose()
        mountedUnder = under
        mount = JPopupMenu().setContent(parent = under) { content.value() }
    }

    fun release() {
        mount?.dispose()
        mount = null
    }
}

@Composable
private fun NestedMenuHost(
    parent: CompositionContext,
    content: @Composable () -> Unit,
) {
    val current = rememberUpdatedState(content)
    SwingNode(
        factory = { NestedMenuPanel() },
        update = { set(parent) { mount(it, current) } },
        onRelease = { release() },
    )
}

@Composable
private fun MenuParent(
    value: String,
    content: @Composable () -> Unit,
) {
    Menu(
        "",
        modifier =
            SwingModifier.styleable(PlainProbeContainer) { provideNestedStyle(Probes) { MenuText.provide(value) } },
    ) {
        content()
    }
}

private data class LifecycleTooltipElement(
    private val value: String,
    private val events: MutableList<String>,
) : SwingModifier.NodeElement<JLabel, LifecycleTooltipElement.Node>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override val key: Any get() = TooltipFromTextElement::class.java

    override val name: String get() = "toolTipText"

    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node(events)

    override fun update(node: Node) {
        node.component.toolTipText = value
    }

    class Node(
        private val events: MutableList<String>,
    ) : SwingModifier.ComponentNode<JLabel>() {
        private var captured: String? = null

        override fun onAttach() {
            captured = component.toolTipText
            events += "attach"
        }

        override fun onDetach() {
            component.toolTipText = captured
            events += "detach"
        }
    }
}

private data class FailingAttachElement(
    private val value: String,
    private val failures: AttachFailures,
) : SwingModifier.NodeElement<StyleProbe, FailingAttachElement.Node>() {
    override val targetType: Class<StyleProbe> get() = StyleProbe::class.java

    override val name: String get() = "failingReattach"

    override val restores: RestorePolicy get() = RestorePolicy.None

    override fun create(): Node = Node(failures)

    override fun update(node: Node) = Unit

    class Node(
        private val failures: AttachFailures,
    ) : SwingModifier.ComponentNode<StyleProbe>() {
        override fun onAttach() {
            if (failures.armed) error("boom on attach #${++failures.count}")
        }
    }
}

private class AttachFailures {
    var armed = false
    var count = 0
}
