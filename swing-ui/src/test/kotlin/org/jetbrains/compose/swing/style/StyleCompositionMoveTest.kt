package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
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
    fun aStyleChangeAfterAnAttachFailedOnMoveSurfacesTheFailingPropertyAndKeepsEarlierValues() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var declared by mutableStateOf("v0")
        var declaresFirst by mutableStateOf(true)
        val attachFailures = AttachFailures()
        val failing = stylePropertyOf { value: String -> FailingAttachElement(value, attachFailures) }
        val probe = StyleProbe("leaf")
        val moved =
            movableContentOf {
                SwingNode(
                    factory = { probe },
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
        val nested = nestedContent({ inOuter }, moved)
        try {
            awaitIdle()
            onProbe("leaf").assertProperty("v0") { first }

            attachFailures.armed = true
            inOuter = true
            assertFailsWith<IllegalStateException> { awaitIdle() }

            declared = "v1"
            assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals("v1", probe.first)

            declaresFirst = false
            assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals("mod", probe.first)
        } finally {
            nested.dispose()
        }
    }

    private fun ComposeSwingTest.nestedContent(
        inOuter: () -> Boolean,
        moved: @Composable () -> Unit,
    ): DisposableHandle {
        val host = JPanel()
        lateinit var context: CompositionContext
        setContent {
            context = rememberCompositionContext()
            SwingNode(factory = { host })
            if (inOuter()) moved()
        }
        return host.setContent(parent = context) { if (!inOuter()) moved() }
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
        val nested = nestedContent({ inOuter }, moved)
        try {
            awaitIdle()
            onProbe("leaf").assertProperty("styled") { first }.assertProperty("s2") { second }

            inOuter = true
            declared = false
            awaitIdle()
            onProbe("leaf")
                .assertProperty("mod") { first }
                .assertProperty(if (dropsWholeStyle) PROBE_OWN_VALUE else "s2") { second }
        } finally {
            nested.dispose()
        }
    }

    @Test
    fun aStylePropertyMovedToAnotherCompositionKeepsTheTextEditedAfterInstallation() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var declaredText by mutableStateOf("declared")
        val initialText = stylePropertyOf(::InstalledTextElement)
        val label = JLabel("own")
        val moved =
            movableContentOf {
                SwingNode(
                    factory = { label },
                    modifier = SwingModifier.styleable(PlainProbe) { initialText.provide(declaredText) },
                )
            }
        val nested = nestedContent({ inOuter }, moved)
        try {
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("declared") { text }
            label.text = "edited"
            onNodeOfType<JLabel>().assertProperty("edited") { text }

            inOuter = true
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("edited") { text }

            declaredText = "changed"
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("changed") { text }
        } finally {
            nested.dispose()
        }
    }

    @Test
    fun droppingATooltipAfterMovingToAnotherCompositionRestoresTheTextItWasAttachedOver() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var overridden by mutableStateOf(true)
        val tooltipFromText = stylePropertyOf(::TooltipFromTextElement)
        val moved =
            movableContentOf {
                SwingNode(
                    factory = { JLabel("original") },
                    modifier =
                        SwingModifier.styleable(PlainProbe) {
                            LabelText.provide("earlier")
                            if (overridden) tooltipFromText.provide("later")
                        },
                )
            }
        val nested = nestedContent({ inOuter }, moved)
        try {
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("later") { toolTipText }

            inOuter = true
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("later") { toolTipText }

            overridden = false
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty("earlier") { toolTipText }
        } finally {
            nested.dispose()
        }
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
        val host = JPanel()
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
        lateinit var context: CompositionContext
        setContent {
            context = rememberCompositionContext()
            SwingNode(factory = { host })
            ProbeContainer(StyleProbe(), {
                if (replaced) provideNestedStyle(Probes) { if (declared) tooltip.provide("second") }
            }) { if (inOuter) moved() }
        }
        val nested =
            host.setContent(parent = context) {
                ProbeContainer(StyleProbe(), {
                    provideNestedStyle(Probes) { tooltip.provide(if (replaced) "first" else "provided") }
                }) { if (!inOuter) moved() }
            }
        try {
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
        } finally {
            nested.dispose()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("movedComponents")
    fun <C : JComponent> styledContentMovedToAnotherCompositionResolvesWithItsNewParent(
        leafFactory: () -> C,
        node: @Composable (C) -> Unit,
        host: (CompositionContext, String, @Composable () -> Unit) -> DisposableHandle,
        text: (C) -> String?,
    ) = runComposeSwingTest {
        var inFirst by mutableStateOf(true)
        lateinit var context: CompositionContext
        val leaf = leafFactory()
        val moved = movableContentOf { node(leaf) }
        setContent { context = rememberCompositionContext() }
        val first = host(context, "first") { if (inFirst) moved() }
        val second = host(context, "second") { if (!inFirst) moved() }
        try {
            awaitIdle()
            assertEquals("first", text(leaf))
            val changes = mutableListOf<String?>()
            leaf.addPropertyChangeListener("text") { changes += text(leaf) }

            inFirst = false
            awaitIdle()
            assertEquals("second", text(leaf))
            assertEquals<List<String?>>(listOf("own", "second"), changes)
        } finally {
            second.dispose()
            first.dispose()
        }
    }

    @Test
    fun movedSubtreeFollowsNewAncestors() = runComposeSwingTest {
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
                ProbeContainer(StyleProbe(), {
                    provideNestedStyle(Probes) {
                        ProbeFirst.provide("first parent")
                        ProbeSecond.provide("first parent")
                    }
                }) { if (underFirst) moved() }
                ProbeContainer(StyleProbe(), { provideNestedStyle(Probes) { ProbeFirst.provide("second parent") } }) {
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

    @Test
    fun removedLeafRestoresBeforeReuse() = runComposeSwingTest {
        val leaf = StyleProbe("leaf")
        var present by mutableStateOf(true)
        var underSecond by mutableStateOf(false)
        setContent {
            ProbeContainer(StyleProbe(), { provideNestedStyle(Probes) { ProbeFirst.provide("provided") } }) {
                if (present) ProbeLeaf(leaf, SwingStyle, NestedProbe)
            }
            ProbeContainer(StyleProbe(), { provideNestedStyle(Probes) { ProbeFirst.provide("second provider") } }) {
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

    companion object {
        @JvmStatic
        fun movedComponents(): List<Arguments> {
            val label: () -> JLabel = { JLabel("own") }
            val menu: () -> JMenu = { JMenu("own") }
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
            val labelNode: @Composable (JLabel) -> Unit = { leaf ->
                SwingNode(factory = { leaf }, modifier = SwingModifier.styleable(labelKind, SwingStyle))
            }
            val menuNode: @Composable (JMenu) -> Unit = { leaf ->
                MenuNode(factory = { leaf }, modifier = SwingModifier.styleable(menuKind, SwingStyle))
            }
            val labelHost: (CompositionContext, String, @Composable () -> Unit) -> DisposableHandle =
                { context, value, content ->
                    JPanel().setContent(parent = context) {
                        ProbeContainer(StyleProbe(), { provideNestedStyle(Probes) { LabelText.provide(value) } }) {
                            content()
                        }
                    }
                }
            val menuHost: (CompositionContext, String, @Composable () -> Unit) -> DisposableHandle =
                { context, value, content ->
                    JPopupMenu().setContent(parent = context) { MenuParent(value, content) }
                }
            val labelText: (JLabel) -> String? = { it.text }
            val menuText: (JMenu) -> String? = { it.text }
            return listOf(
                Arguments.of(Named.of("label", label), labelNode, labelHost, labelText),
                Arguments.of(Named.of("menu", menu), menuNode, menuHost, menuText),
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

@Composable
private fun MenuParent(
    value: String,
    content: @Composable () -> Unit,
) {
    MenuNode(
        factory = { JMenu() },
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
