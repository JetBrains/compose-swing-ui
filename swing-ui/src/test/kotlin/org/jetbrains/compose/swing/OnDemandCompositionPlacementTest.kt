package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.BODY_REGION
import org.jetbrains.compose.swing.node.BodyAttachment
import org.jetbrains.compose.swing.node.ExistingSwingNode
import org.jetbrains.compose.swing.node.Framed
import org.jetbrains.compose.swing.node.FramedHeader
import org.jetbrains.compose.swing.node.FramedPanel
import org.jetbrains.compose.swing.node.FramedProtocol
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The contract of a placed [OnDemandComposition]: it installs its top-level component through its placement,
 * releases it through the action the placement returned, and disposes itself before a failure reaches the
 * caller.
 */
class OnDemandCompositionPlacementTest {
    @Test
    fun aPlacedCompositionInstallsItsComponentIntoTheHost() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val existing = JButton("existing")
        host.add(existing)
        val composition = OnDemandComposition(parentContext, host, addingTo(host)) { Label("placed") }
        try {
            val label = assertIs<JLabel>(host.components.last(), "the first pass installs into the host")
            assertSame(existing, host.components.first())
            assertSame(label, composition.recompose {}, "recompose returns the component the placement installed")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aComponentReplacedInAParentDrivenPassIsPlacedInThatPass() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        var showButton by mutableStateOf(false)
        setContent {
            parentContext = rememberCompositionContext()
            Label("parent")
        }

        val host = JPanel()
        val composition =
            OnDemandComposition(parentContext, host, addingTo(host)) {
                if (showButton) Button("second", onClick = {}) else Label("first")
            }
        try {
            val label = host.components.single()

            showButton = true
            awaitIdle()

            assertIs<JButton>(host.components.single(), "the replacement fills the region in the pass that built it")
            assertNull(label.parent, "the replaced component is released through the placement")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aPlacedCompositionComposingNothingLeavesTheRegionEmpty() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var show by mutableStateOf(false)
        val host = JPanel()
        val composition = OnDemandComposition(parentContext, host, addingTo(host)) { if (show) Label("shown") }
        try {
            assertEquals(0, host.componentCount, "no component composed, so the region has none")
            assertNull(composition.recompose {})

            assertIs<JLabel>(composition.recompose { show = true })
            assertEquals(1, host.componentCount)
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aComponentLeavingIsRemovedThroughThePlacement() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var show by mutableStateOf(true)
        val host = JPanel()
        val composition = OnDemandComposition(parentContext, host, addingTo(host)) { if (show) Label("shown") }
        try {
            assertNull(composition.recompose { show = false })

            assertEquals(0, host.componentCount)
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun disposingRemovesThePlacedComponent() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val composition = OnDemandComposition(parentContext, host, addingTo(host)) { Label("placed") }

        composition.dispose()

        assertEquals(0, host.componentCount)
        assertNull(composition.recompose {}, "a disposed placed composition returns null")
    }

    @Test
    fun aRenderThatKeepsItsComponentDoesNotPlaceItAgain() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var text by mutableStateOf("first")
        val host = JPanel()
        val composition = OnDemandComposition(parentContext, host, addingTo(host)) { Label(text) }
        try {
            val label = assertIs<JLabel>(host.components.single())
            val later = JButton("added after the composition")
            host.add(later)

            repeat(3) { round -> composition.recompose { text = "round $round" } }

            assertEquals("round 2", label.text, "the renders reached the component")
            assertEquals(listOf<Component>(label, later), host.components.toList(), "no render placed it again")
        } finally {
            composition.dispose()
        }
    }

    @TestFactory
    fun aFailingPlacementStepDisposesTheCompositionBeforeTheFailureReachesTheCaller(): List<DynamicTest> =
        Step.entries.flatMap { step ->
            step.passes.map { pass ->
                dynamicTest("${step.name}, ${pass.name}") {
                    runComposeSwingTest {
                        val host = JPanel()

                        val failure =
                            failureOf(pass, host, step.placement(host)) { failing ->
                                if (step.shown(failing)) Label("placed")
                            }

                        assertIs<TestFailure>(failure)
                        assertEquals(
                            emptyList(),
                            failure.suppressedExceptions,
                            "the disposal that followed the failure succeeded",
                        )
                        assertEquals(0, host.componentCount)
                    }
                }
            }
        }

    @Test
    fun aFailingReleaseOnDisposeReachesTheCaller() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val composition = OnDemandComposition(parentContext, host, Step.Release.placement(host)) { Label("placed") }

        assertFailsWith<TestFailure> { composition.dispose() }

        assertEquals(0, host.componentCount)
        assertNull(composition.recompose {}, "a disposed placed composition returns null")
    }

    @TestFactory
    fun aTopLevelComponentTheCompositionCannotPlaceIsRefused(): List<DynamicTest> = Refusal.entries.flatMap { refusal ->
        Pass.entries.map { pass ->
            dynamicTest("${refusal.name}, ${pass.name}") {
                runComposeSwingTest {
                    val host = refusal.host()
                    val before = host.components.toList()

                    val failure = failureOf(pass, host, addingTo(host), refusal.content)

                    assertIs<IllegalStateException>(failure)
                    assertEquals(before, host.components.toList(), "the refusal disposed the composition")
                }
            }
        }
    }

    @TestFactory
    fun aCompositionWithoutAHostRefusesATopLevelComponentThePlacedOneRefuses(): List<DynamicTest> =
        // A claimed part needs the host the composition is placed in to claim it from.
        (Refusal.entries - Refusal.ClaimedNamingARegion).flatMap { refusal ->
            Pass.entries.map { pass ->
                dynamicTest("${refusal.name}, ${pass.name}") {
                    runComposeSwingTest {
                        val failure =
                            failureOf(pass, refusal.content) { parentContext, content ->
                                OnDemandComposition(parentContext, content)
                            }

                        assertIs<IllegalStateException>(failure)
                    }
                }
            }
        }

    @TestFactory
    fun aRegionTheHostRefusesIsRefusedWithoutDisposingTheComposition(): List<DynamicTest> =
        (Pass.entries - Pass.OnConstruction).map { pass ->
            dynamicTest(pass.name) {
                runComposeSwingTest {
                    val host = JPanel()
                    val tabsOnly = parentProtocolOf("tabs only") { it is JTabbedPane }
                    var composition: OnDemandComposition? = null
                    lateinit var parentContext: CompositionContext
                    setContent { parentContext = rememberCompositionContext() }
                    var naming by mutableStateOf(false)

                    val failure =
                        runCatching {
                            pass.drive(
                                this,
                                build = {
                                    OnDemandComposition(parentContext, host, addingTo(host)) {
                                        if (naming) {
                                            Label("named", SwingModifier.slot(tabsOnly, "Tab { }", NoRegion))
                                        } else {
                                            Label("plain")
                                        }
                                    }.also { composition = it }
                                },
                                write = { naming = true },
                            )
                        }.exceptionOrNull()

                    assertIs<IllegalStateException>(failure)
                    val kept = assertNotNull(composition)
                    try {
                        // A disposed composition runs no update, so the failure proves this one was not disposed.
                        assertFailsWith<TestFailure> { kept.recompose { throw TestFailure() } }
                    } finally {
                        // The refused pass stopped partway, so this disposal can throw, as the class documents.
                        runCatching { kept.dispose() }
                    }
                }
            }
        }

    @TestFactory
    fun aDisposalWhoseReleasesFailTwiceReportsEachFailureOnce(): List<DynamicTest> = Twice.entries.flatMap { twice ->
        Pass.entries.map { pass ->
            dynamicTest("${twice.name}, ${pass.name}") {
                runComposeSwingTest {
                    val host = JPanel()
                    val released = twice.failures()
                    val toThrow = released.toMutableList()
                    val placement =
                        SlotAttachment { _, component, _ ->
                            host.add(component)
                            (
                                {
                                    host.remove(component)
                                    throw toThrow.removeFirst()
                                }
                            )
                        }

                    // Two top-level components are refused, and the disposal that follows releases both.
                    val failure =
                        failureOf(pass, host, placement) { failing ->
                            Label("first")
                            if (failing) Label("second")
                        }

                    assertIs<IllegalStateException>(failure)
                    assertEquals(listOf(released.first()), failure.suppressedExceptions)
                    assertEquals(twice.laterSuppressed(released), released.first().suppressedExceptions)
                    assertEquals(0, host.componentCount)
                }
            }
        }
    }

    @TestFactory
    fun oneFailureThrownByTheInstallAndTheReleaseOfAPassReachesTheCallerOnce(): List<DynamicTest> =
        (Pass.entries - Pass.OnConstruction).map { pass ->
            dynamicTest(pass.name) {
                runComposeSwingTest {
                    val host = JPanel()
                    val failure = TestFailure()
                    val placement =
                        SlotAttachment { _, component, _ ->
                            if (component is JButton) throw failure
                            host.add(component)
                            (
                                {
                                    host.remove(component)
                                    throw failure
                                }
                            )
                        }

                    // The pass releases the label and installs the button, and both throw the one instance.
                    val thrown =
                        failureOf(pass, host, placement) { failing ->
                            if (failing) Button("second", onClick = {}) else Label("first")
                        }

                    assertSame(failure, thrown)
                    assertEquals(emptyList(), thrown.suppressedExceptions)
                    assertEquals(0, host.componentCount)
                }
            }
        }

    @TestFactory
    fun disposingRequestsTheRefreshOfTheAreaTheComponentLeaves(): List<DynamicTest> = Put.entries.map { put ->
        dynamicTest(put.name) {
            runComposeSwingTest {
                lateinit var parentContext: CompositionContext
                setContent { parentContext = rememberCompositionContext() }

                val outer = JPanel(null)
                outer.bounds = Rectangle(100, 100, 300, 300)
                val host = JPanel(null)
                host.bounds = Rectangle(0, 30, 200, 200)
                outer.add(host)
                val composition = OnDemandComposition(parentContext, host, put.placement(host)) { Label("placed") }

                withRecordedRepaints { recorded ->
                    composition.dispose()

                    assertTrue(
                        put.area in recorded.dirtyRegionsOf(host),
                        "the host repaints the area the component covered, in its own coordinates: " +
                            "${recorded.dirtyRegionsOf(host)}",
                    )
                    recorded.assertAskedForLayout(host, "disposing the composition")
                }
            }
        }
    }

    @Test
    fun disposingReleasesTheComponentInTheHostAndLeavesItsOtherChildren() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val unrelated = JLabel("unrelated")
        host.add(unrelated)
        val placement =
            SlotAttachment { _, component, _ ->
                host.add(component)
                (
                    {
                        check(component.parent === host)
                        host.remove(component)
                    }
                )
            }
        val composition = OnDemandComposition(parentContext, host, placement) { Label("placed") }

        composition.dispose()

        assertEquals(listOf<Component>(unrelated), host.components.toList(), "the unrelated child stays")
    }

    @Test
    fun disposingReleasesAPlacementThatFindsItsComponentByIndex() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val placement =
            SlotAttachment { _, component, _ ->
                host.add(component)
                ({ host.remove(host.getComponentZOrder(component)) })
            }
        val composition = OnDemandComposition(parentContext, host, placement) { Label("placed") }
        assertEquals(1, host.componentCount)

        composition.dispose()

        assertEquals(0, host.componentCount)
    }

    @Test
    fun disposingLeavesAClaimedPartInTheRegionOfAContainerItsContentComposed() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()
        val composition =
            OnDemandComposition(parentContext, host, addingTo(host)) {
                Framed {
                    FramedHeader()
                    Label("body", SwingModifier.slot(FramedProtocol, BODY_REGION, BodyAttachment))
                }
            }
        val panel = host.getComponent(0) as FramedPanel
        val body = panel.getComponent(panel.componentCount - 1)
        assertSame(panel, body.parent, "the composed region part is in the container")

        composition.dispose()

        assertEquals(0, host.componentCount, "the placement released the container")
        assertSame(panel, panel.header.parent, "the claimed part stays in the container it is in")
        assertNull(body.parent, "the part the composition added to a region leaves with the content")
    }

    @Test
    fun aPlacedCompositionWhoseFirstPassFailsIsDisposedBeforeTheFailureReachesTheCaller() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val host = JPanel()

        assertFailsWith<TestFailure> {
            OnDemandComposition(parentContext, host, addingTo(host)) {
                Label("placed")
                // Runs after the pass applied, so the label is already in the region.
                SideEffect { throw TestFailure() }
            }
        }

        assertEquals(0, host.componentCount, "the region is empty when the caller catches the failure")
    }
}

/**
 * Runs [pass] over a composition of [content] placed in [host] through [placement], where [content] is handed
 * `true` once the pass is to fail it. Checks that the failure disposed the composition and left the parent
 * recomposing, and returns the failure.
 */
private suspend fun ComposeSwingTest.failureOf(
    pass: Pass,
    host: Container,
    placement: SlotAttachment,
    content:
        @Composable @SwingComposable
        (failing: Boolean) -> Unit,
): Throwable = failureOf(pass, content) { parentContext, composed ->
    OnDemandComposition(parentContext, host, placement, composed)
}

/** [failureOf] for a composition that [create] builds from the parent context and the content. */
private suspend fun ComposeSwingTest.failureOf(
    pass: Pass,
    content:
        @Composable @SwingComposable
        (failing: Boolean) -> Unit,
    create: (
        CompositionContext,
        @Composable @SwingComposable
        () -> Unit,
    ) -> OnDemandComposition,
): Throwable {
    lateinit var parentContext: CompositionContext
    var parentText by mutableStateOf("first")
    setContent {
        parentContext = rememberCompositionContext()
        Label(parentText)
    }
    var failing by mutableStateOf(pass.onConstruction)
    var composition: OnDemandComposition? = null

    val failure =
        runCatching {
            pass.drive(
                this,
                build = {
                    create(parentContext) { content(failing) }.also { composition = it }
                },
                write = { failing = true },
            )
        }.exceptionOrNull()

    assertNull(
        composition?.recompose { fail("a disposed composition runs no update") },
        "the failure disposed the composition",
    )
    parentText = "second"
    awaitIdle()
    onNodeWithText("second").assertExists()
    return assertNotNull(failure, "the pass failed")
}

/** A failure thrown by the test's own placement or content. */
private class TestFailure : RuntimeException()

private val AnyContainer = parentProtocolOf("any container") { true }

private val NoRegion = SlotAttachment { _, _, _ -> {} }

/** The failures two releases throw: one instance twice, or an instance each. */
private enum class Twice(
    val failures: () -> List<TestFailure>,
    val laterSuppressed: (released: List<TestFailure>) -> List<Throwable>,
) {
    SameFailure({ TestFailure().let { listOf(it, it) } }, { emptyList() }),
    DistinctFailures({ listOf(TestFailure(), TestFailure()) }, { released -> released.drop(1) }),
}

/** The placement step that throws, and the passes it can throw in. */
private enum class Step(
    val passes: List<Pass>,
    val shown: (failing: Boolean) -> Boolean,
    val placement: (host: Container) -> SlotAttachment,
) {
    Install(Pass.entries, { failing -> failing }, { SlotAttachment { _, _, _ -> throw TestFailure() } }),
    Release(
        Pass.entries - Pass.OnConstruction,
        { failing -> !failing },
        { host ->
            SlotAttachment { _, component, _ ->
                host.add(component)
                (
                    {
                        host.remove(component)
                        throw TestFailure()
                    }
                )
            }
        },
    ),
}

/** A top-level component of a placed composition that the placement cannot honor, and the host it is refused in. */
private enum class Refusal(
    val host: () -> JPanel,
    val content:
        @Composable @SwingComposable
        (failing: Boolean) -> Unit,
) {
    ParentData({ JPanel(BorderLayout()) }, { failing ->
        Label("placed", if (failing) SwingModifier.layoutConstraint(BorderLayout.NORTH) else SwingModifier)
    }),
    CreatedNamingARegion({ JPanel() }, { failing ->
        if (failing) Label("named", SwingModifier.slot(AnyContainer, "Region { }", NoRegion))
    }),
    ClaimedNamingARegion({ JPanel().apply { add(JLabel("claimed")) } }, { failing ->
        if (failing) {
            ExistingSwingNode<JPanel, JLabel>(
                claim = { getComponent(0) as JLabel },
                modifier = SwingModifier.slot(AnyContainer, "Part"),
            )
        }
    }),
}

/** Where a placement puts the component, and the area in the host's coordinates that leaving it frees. */
private enum class Put(
    val area: Rectangle,
    val placement: (host: JPanel) -> SlotAttachment,
) {
    InTheHost(Rectangle(5, 6, 40, 10), { host ->
        SlotAttachment { _, component, _ ->
            component.bounds = Rectangle(5, 6, 40, 10)
            host.add(component)
            ({ host.remove(component) })
        }
    }),
    InADescendantOfTheHost(Rectangle(15, 16, 40, 10), { host ->
        val inner = JPanel(null)
        inner.bounds = Rectangle(10, 10, 100, 100)
        host.add(inner)
        SlotAttachment { _, component, _ ->
            component.bounds = Rectangle(5, 6, 40, 10)
            inner.add(component)
            ({ inner.remove(component) })
        }
    }),
    InAWrapperTheReleaseDetaches(Rectangle(15, 16, 40, 10), { host ->
        val wrapper = JPanel(null)
        wrapper.bounds = Rectangle(10, 10, 100, 100)
        SlotAttachment { _, component, _ ->
            component.bounds = Rectangle(5, 6, 40, 10)
            wrapper.add(component)
            host.add(wrapper)
            ({ host.remove(wrapper) })
        }
    }),
}
