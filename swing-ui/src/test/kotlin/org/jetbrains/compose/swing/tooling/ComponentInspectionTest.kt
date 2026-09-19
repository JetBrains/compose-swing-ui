package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.Composer
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val PANEL_TAG = "inspection-panel"
internal const val LABEL_TAG = "inspection-label"
private const val BUTTON_TAG = "inspection-button"
private const val CHECKBOX_TAG = "inspection-checkbox"
internal const val NESTED_TAG = "inspection-nested-label"

/** The file these tests declare their content in, which is where a declaration trace has to lead back to. */
private const val OWN_FILE = "ComponentInspectionTest.kt"

/**
 * Behavioral tests for what [findDeclaringGroup], [findCompositionData] and
 * [attachComposeStackTrace] answer once [isDebugInspectorInfoEnabled] is on: the group that declared a
 * component, the composition a component stands in, and the trace of where it was declared.
 *
 * The switch is process-wide state, so every test leaves it off again - otherwise it would leak into a
 * later test, and into every composition the rest of the suite mounts. The Compose runtime's diagnostic
 * stack trace mode is process-wide too and belongs to the application, which these tests stand in for:
 * they set that mode before each test and restore its default after.
 */
class ComponentInspectionTest {
    /** Stands in for an application that enabled Compose diagnostic stack traces. */
    @BeforeTest
    fun enableDiagnosticStackTraces() {
        Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)
    }

    @AfterTest
    fun turnInspectionOff() {
        SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
        Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.None)
    }

    @Test
    fun withoutTheApplicationsDiagnosticStackTraceModeTheDataAnswersAndNoTraceIsAttached() = runComposeSwingTest {
        Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.None)
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        val label = taggedLabel()
        assertNotNull(
            label.findDeclaringGroup(),
            "the composition answers for what it declared whatever the application set",
        )
        assertFalse(
            label.attachComposeStackTrace(RuntimeException("boom")),
            "and the switch sets that mode no more than it reads it, so no trace can be built",
        )
    }

    @Test
    fun inspectionOnBeforeContentIsMountedLeadsFromEachComponentToWhereItWasDeclared() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel(PanelLayout.Box()) {
                Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG))
                Button(text = "go", onClick = {}, modifier = SwingModifier.testTag(BUTTON_TAG))
                CheckBox(
                    text = "agree",
                    checked = true,
                    onCheckedChange = {},
                    modifier = SwingModifier.testTag(CHECKBOX_TAG),
                )
            }
        }

        assertDeclaredBy(taggedLabel(), "Label.kt")
        assertDeclaredBy(onNodeWithTag(BUTTON_TAG).fetch(), "Button.kt")
        assertDeclaredBy(onNodeWithTag(CHECKBOX_TAG).fetch(), "CheckBox.kt")
        assertTrue(
            declarationTraceOf(taggedLabel()).contains(OWN_FILE),
            "the trace must reach the call site that declared the component, not only the library file " +
                "the composable is defined in",
        )
        assertTrue(
            declarationTraceOf(onNodeWithTag(CHECKBOX_TAG).fetch()).contains(OWN_FILE),
            "the CheckBox trace must reach the call site that declared the component",
        )
    }

    @Test
    fun aThrowableAlreadyCarryingATraceIsNotGivenASecondOne() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        val label = taggedLabel()
        val throwable = RuntimeException("a failure raised outside composition")
        assertTrue(label.attachComposeStackTrace(throwable), "the first attach names where it was declared")
        assertFalse(
            label.attachComposeStackTrace(throwable),
            "a throwable already carrying a composition stack trace is given no second one",
        )
        assertEquals(1, throwable.suppressedExceptions.size, "so it carries the one trace it was given")
    }

    @Test
    fun aDeclaredComponentIsAnsweredWithItsOwnGroupAndTheWholeCompositionIsReachableFromIt() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel(PanelLayout.Box(), modifier = SwingModifier.testTag(PANEL_TAG)) {
                Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG))
                Button(text = "go", onClick = {}, modifier = SwingModifier.testTag(BUTTON_TAG))
            }
        }
        val panel = onNodeWithTag(PANEL_TAG).fetch()
        val label = taggedLabel()
        val button = onNodeWithTag(BUTTON_TAG).fetch()

        for (component in listOf(panel, label, button)) {
            val group = assertNotNull(component.findDeclaringGroup())
            assertSame(
                component,
                (group.node as? SwingComponentNode)?.component,
                "the group must hold the very component it was asked about, never its parent or a sibling",
            )
        }
        assertEquals(
            setOf(label, button),
            assertNotNull(panel.findDeclaringGroup())
                .allGroups()
                .mapNotNull { (it.node as? SwingComponentNode)?.component }
                .toSet(),
            "a group is a CompositionData too, so descending it finds what that component declared, " +
                "identical to the components the harness fetched",
        )
    }

    @Test
    fun aComponentHostingAContentCompositionIsStillAnsweredWithTheGroupThatDeclaredIt() = runComposeSwingTest {
        val nestedHost = JPanel()
        isDebugInspectorInfoEnabled = true
        setContent { SwingNode(factory = { nestedHost }) }
        val handle = nestedHost.setContent { Label(text = "nested", modifier = SwingModifier.testTag(NESTED_TAG)) }
        try {
            awaitIdle()

            assertSame(
                nestedHost,
                (assertNotNull(nestedHost.findDeclaringGroup()).node as? SwingComponentNode)?.component,
                "the content composition a component hosts declared it no more than a stranger did, so " +
                    "the composition that did declare it is the one that answers",
            )
            assertTrue(
                declarationTraceOf(nestedHost).contains(OWN_FILE),
                "and the trace names where it was declared, not the content composition it carries",
            )
            assertSame(
                onNodeWithTag(NESTED_TAG).fetch(),
                (
                    assertNotNull(onNodeWithTag(NESTED_TAG).fetch().findDeclaringGroup()).node
                        as? SwingComponentNode
                )?.component,
                "while a component inside that composition is answered by it",
            )
            assertSame(
                onNodeWithTag(NESTED_TAG).fetch().findCompositionData(),
                nestedHost.findCompositionData(),
                "and the composition a host stands in is the one it carries, which is nearer than the " +
                    "composition that declared the host",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aRecompositionLeavesTheContentAndItsAnswerAsTheyWere() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var shown by mutableStateOf("first")
        var buildsOfTheContent = 0
        setContent {
            remember { buildsOfTheContent++ }
            Label(text = shown, modifier = SwingModifier.testTag(LABEL_TAG))
        }
        val label = taggedLabel()
        val anchor =
            assertNotNull(
                assertNotNull(label.findDeclaringGroup()).identity,
                "a group of a collecting composition is anchored, which is what a tool holds on to",
            )

        shown = "second"
        awaitIdle()

        assertEquals(1, buildsOfTheContent, "a recomposition does not build the content afresh")
        onNodeWithTag(LABEL_TAG).assertTextEquals("second")
        assertSame(label, taggedLabel(), "the recomposed pass keeps the same component")
        assertSame(
            anchor,
            assertNotNull(label.findDeclaringGroup()).identity,
            "and the same group holds it, so a tool that remembered the group still recognizes it",
        )
        assertSame(
            label,
            ((label.parent.findCompositionData()?.find(anchor))?.node as? SwingComponentNode)?.component,
            "an identity read off an earlier walk finds its group again on a later one, which is what a " +
                "tool holds instead of the group itself",
        )
        assertDeclaredBy(label, "Label.kt")
    }

    @Test
    fun aComponentInsideANestedCompositionIsAnsweredForByThatCompositionsOwnGroups() = runComposeSwingTest {
        val nestedHost = JPanel()
        isDebugInspectorInfoEnabled = true
        setContent {
            Label(text = "outer", modifier = SwingModifier.testTag(LABEL_TAG))
            SwingNode(factory = { nestedHost })
        }
        val handle = nestedHost.setContent { Label(text = "nested", modifier = SwingModifier.testTag(NESTED_TAG)) }
        try {
            awaitIdle()

            assertDeclaredBy(taggedLabel(), "Label.kt")
            val nested = onNodeWithTag(NESTED_TAG).fetch()
            assertDeclaredBy(nested, "Label.kt")
            assertSame(
                assertNotNull(nestedHost.findCompositionData()),
                nested.findCompositionData(),
                "the content composition that declared it is the nearest composition above it, so that " +
                    "is the one it is answered with",
            )
            assertNotSame(
                root.findCompositionData(),
                nested.findCompositionData(),
                "and never the composition around it, which declared the host and nothing inside it",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aComponentNoCompositionDeclaredIsAnsweredForByNothing() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        val outsider = JPanel()
        assertNull(
            outsider.findCompositionData(),
            "a component standing outside every composition is answered for by none of them",
        )
        assertFalse(
            outsider.attachComposeStackTrace(RuntimeException("boom")),
            "and no declaration can be named for it",
        )

        val stranger = JPanel()
        (root as JComponent).add(stranger)
        assertNull(stranger.findDeclaringGroup(), "a component added by hand is declared by no group")
        assertSame(
            root.findCompositionData(),
            stranger.findCompositionData(),
            "but the composition it sits in answers for where it stands",
        )
        assertFalse(
            stranger.attachComposeStackTrace(RuntimeException("boom")),
            "so the composition it sits in names no declaration for it either",
        )
    }

    @Test
    fun withInspectionOffAComponentsOwnNodeAnswersNull() = runComposeSwingTest {
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        assertNull(
            taggedLabel().composedNode(),
            "off is the default, so no node was ever published",
        )
    }

    @Test
    fun withInspectionOnADeclaredComponentAnswersWithTheNodeItsGroupHolds() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        val label = taggedLabel()
        val node = assertNotNull(label.composedNode(), "the component was declared while inspection was on")
        assertSame(label, node.component, "the node holds the very component it was read off")
        assertSame(
            assertNotNull(label.findDeclaringGroup()).node,
            node,
            "it is the same node a slot table walk would find on the declaring group",
        )
    }

    @Test
    fun aComponentRemovedFromTheContentStopsAnsweringWithItsNode() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var shown by mutableStateOf(true)
        setContent { if (shown) Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }
        val label = taggedLabel()
        assertNotNull(label.composedNode(), "the component is declared while it stands in the content")

        shown = false
        awaitIdle()

        assertNull(label.composedNode(), "the node that published it was released along with it")
    }

    @Test
    fun aComponentTwoNodesShareAnswersWithTheNodeDeclaringItNow() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var nested by mutableStateOf(false)
        lateinit var shared: JLabel
        // One component declared by a node in either arm: the node going out is released after the node
        // coming in has published it.
        setContent {
            val label = remember { JLabel("shared") }
            shared = label
            if (nested) {
                Panel(PanelLayout.Box()) { SwingNode(factory = { label }) }
            } else {
                SwingNode(factory = { label })
            }
        }
        val declaredFirst = assertNotNull(shared.composedNode(), "the component is declared where it stands")

        nested = true
        awaitIdle()

        val declaringNow = assertNotNull(shared.composedNode(), "the node declaring it now must answer")
        assertNotSame(declaredFirst, declaringNow, "and it is the node the other arm declared")
        assertSame(
            declaringNow,
            shared.findDeclaringGroup()?.node,
            "which is the node the composition itself answers with",
        )
    }

    @Test
    fun aParkedComponentStopsAnsweringWithTheNodeParkedWithIt() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var active by mutableStateOf(true)
        setContent {
            Panel(PanelLayout.Box()) {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    Label(text = "parked", modifier = SwingModifier.testTag(LABEL_TAG))
                }
            }
        }
        // Fetched while the composition still drives it: a parked component is out of the tree a tag
        // lookup walks.
        val label = taggedLabel()
        assertNotNull(label.composedNode(), "the component is declared while the content is active")

        active = false
        awaitIdle()

        assertNull(label.parent, "parking takes the component out of the Swing tree")
        assertNull(label.composedNode(), "and a parked node is no declaration for the component it left on")
    }

    @Test
    fun withInspectionOnADeclaredMenuItemAnswersWithTheNodeItsGroupHolds() = runComposeSwingTest {
        // A menu tree is composed by an applier of its own, so the node has to be published that way
        // in too: a menu item is asked for its node exactly as a component is.
        isDebugInspectorInfoEnabled = true
        val popup = composeMenu { MenuItem("Cut", onClick = { }) }

        val item = popup.getComponent(0) as JComponent
        val node = assertNotNull(item.composedNode(), "the menu item was declared while inspection was on")
        assertSame(item, node.component, "the node holds the very item it was read off")
        assertSame(
            assertNotNull(item.findDeclaringGroup()).node,
            node,
            "it is the same node a slot table walk would find on the declaring group",
        )
    }

    @Test
    fun turningInspectionOffMakesEveryComponentAnswerNullAgain() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }
        assertNotNull(taggedLabel().composedNode())

        isDebugInspectorInfoEnabled = false
        awaitIdle()

        assertNull(
            taggedLabel().composedNode(),
            "the switch going off re-inserts the content into a fresh, unpublished component",
        )
    }

    @Test
    fun theAnswersMustBeReadOnTheEventDispatchThread() {
        val component = JPanel()

        val groupFailure = assertFailsWith<IllegalStateException> { component.findDeclaringGroup() }
        assertTrue(
            groupFailure.message.orEmpty().contains("Event Dispatch Thread"),
            "the read must name the thread it belongs to, but was: ${groupFailure.message}",
        )
        val dataFailure = assertFailsWith<IllegalStateException> { component.findCompositionData() }
        assertTrue(
            dataFailure.message.orEmpty().contains("Event Dispatch Thread"),
            "and so must the composition it stands in, but was: ${dataFailure.message}",
        )
        val traceFailure =
            assertFailsWith<IllegalStateException> { component.attachComposeStackTrace(RuntimeException("boom")) }
        assertTrue(
            traceFailure.message.orEmpty().contains("Event Dispatch Thread"),
            "and so must attaching a trace, but was: ${traceFailure.message}",
        )
        val nodeFailure = assertFailsWith<IllegalStateException> { component.composedNode() }
        assertTrue(
            nodeFailure.message.orEmpty().contains("Event Dispatch Thread"),
            "and so must reading a component's own node, but was: ${nodeFailure.message}",
        )
    }

    @Test
    fun aComponentWhoseAncestorChainContainsNonJComponentsIsAnsweredForByNothing() = runComposeSwingTest {
        // publishedCompositionData walks getParent() on each ancestor. The mapNotNull skips any
        // ancestor that is not a JComponent. This exercises both the non-JComponent cast branch and
        // the sequence-termination branch when the walk reaches a root component with no parent.
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        // Add a raw AWT Panel (not a JComponent) between the composition root and a detached child.
        val rawContainer = java.awt.Panel()
        val child = JPanel()
        rawContainer.add(child)

        // child is detached from any window and from any composition: no JComponent ancestor carries
        // published composition data, and the walk reaches the raw Panel (skipped) then null (stops).
        assertNull(
            child.findCompositionData(),
            "a component whose only ancestors are non-JComponents and null is answered for by nothing",
        )
        assertNull(
            child.findDeclaringGroup(),
            "and no group declared it either",
        )
    }
}

/** The tagged [JLabel] in the harness content, typed for the reads a component's own node needs. */
private fun ComposeSwingTest.taggedLabel(): JLabel = onNodeWithTag(LABEL_TAG).fetch() as JLabel

/**
 * Asserts [component] is answered for with its own node group, and that the trace of where it was
 * declared names [declaringFile].
 */
internal fun assertDeclaredBy(
    component: Component,
    declaringFile: String,
) {
    val group = assertNotNull(component.findDeclaringGroup())
    assertSame(
        component,
        (group.node as? SwingComponentNode)?.component,
        "the group answered with holds the component the search started at",
    )
    val trace = declarationTraceOf(component)
    assertTrue(trace.contains(declaringFile), "the declaration trace must name $declaringFile, but was: $trace")
}

/**
 * The composition stack trace [component]'s declaration is named by, rendered as the Compose runtime
 * writes it. The rendered text is what a caller reads: the diagnostic exception carrying it is a
 * runtime-internal type.
 *
 * Only the attached exception is rendered, never the carrier: the carrier's own Java frames run
 * through this file, so rendering it would name this file whatever the composition trace holds.
 */
private fun declarationTraceOf(component: Component): String {
    val throwable = RuntimeException("a failure raised outside composition")
    assertTrue(
        component.attachComposeStackTrace(throwable),
        "no composition stack trace was attached for $component",
    )
    val rendered = StringWriter()
    throwable.suppressedExceptions.single().printStackTrace(PrintWriter(rendered))
    return rendered.toString()
}

/**
 * Every group under this one, in depth-first order: each group followed immediately by its descendants. A
 * Swing composition nests far deeper than the declaring code shows, so this recurses with no depth bound.
 */
private fun CompositionData.allGroups(): Sequence<CompositionGroup> =
    compositionGroups.asSequence().flatMap { group -> sequenceOf(group) + group.allGroups() }
