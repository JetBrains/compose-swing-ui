// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.ScrollPaneScope
import org.jetbrains.compose.swing.core.OnDemandComposition
import org.jetbrains.compose.swing.core.addingTo
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A caller-owned component placed through movableComponentOf in one root's composition. */
class MovableComponentTest {
    @Test
    fun theFirstCallTakesTheComponentFromItsContainer() = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)

        setContent {
            Panel(SwingModifier.name("A")) { movable(SwingModifier.name("placed")) }
        }
        awaitIdle()

        assertEquals("A", field.parent?.name, "the component should stand in A")
        assertEquals(0, field.home.componentCount, "the component should have left home")
        assertEquals("placed", field.name, "the modifier should have written the name")
        assertEquals(listOf("placed"), field.nameWrites, "the write should find the component out of its container")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endingPlacements")
    fun anEndedPlacementGivesTheComponentBack(ending: EndingCase) = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var placed by mutableStateOf(true)
        setContent { ending.content(movable, placed) }
        awaitIdle()
        assertEquals(ending.standsIn, field.parent?.name, "the component should stand in the container of its call")
        assertEquals("placed", field.name, "the modifier should have written the name")

        placed = false
        awaitIdle()

        assertNull(field.parent, "the ended placement should take the component out of its container")
        assertEquals("own", field.name, "the ended placement should give the name back")
        onNodeWithName("A")
            .assertIsOfType<Container>()
            .assertProperty(0, "the panel should be empty") { componentCount }
    }

    @Test
    fun deactivatedContentTakesTheComponentAgainWhenReactivated() = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var active by mutableStateOf(true)
        setContent {
            Panel(SwingModifier.name("A")) { ReusableContentHost(active) { movable(SwingModifier.name("placed")) } }
        }
        awaitIdle()
        assertEquals("A", field.parent?.name, "the component should stand in A")

        active = false
        awaitIdle()
        assertNull(field.parent, "deactivated content should give the component back")
        assertEquals("own", field.name, "deactivated content should give the name back")

        active = true
        awaitIdle()
        assertEquals("A", field.parent?.name, "reactivated content should take the component again")
        assertEquals("placed", field.name, "reactivated content should write the name again")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whereTheComponentStandsWhenHidden")
    fun aComponentTheCallerAddsBackWhileEndingThePlacementStaysThere(movedToNested: Boolean) = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var nested by mutableStateOf(false)
        var shown by mutableStateOf(true)
        setContent {
            Panel(SwingModifier.name("A")) {
                if (shown && !nested) movable(SwingModifier.name("placed"))
                NestedContentHost(SwingModifier.name("N")) {
                    if (shown && nested) movable(SwingModifier.name("placed"))
                }
            }
        }
        awaitIdle()
        if (movedToNested) {
            nested = true
            awaitIdle()
            assertEquals("N", field.parent?.name, "the component should stand in the nested composition")
        }

        shown = false
        field.home.add(field)
        awaitIdle()

        assertSame(field.home, field.parent, "the component the caller added back should stay there")
        assertEquals(1, field.home.componentCount, "home should hold the component once")
        assertEquals("own", field.name, "the ended placement should give the name back")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("regionsOfAScrollPane")
    fun anEndedPlacementInAScrollPaneRegionLeavesNoViewportHoldingIt(region: RegionCase) = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var shown by mutableStateOf(true)
        setContent { ScrollPane(SwingModifier.name("S")) { if (shown) region.content(this, movable) } }
        awaitIdle()
        onNodeWithName("S")
            .assertIsOfType<JScrollPane>()
            .assertProperty(field, "the region should hold the component") { region.view(this) }

        shown = false
        awaitIdle()

        assertNull(field.parent, "the ended placement should take the component out of the region")
        onNodeWithName("S")
            .assertIsOfType<JScrollPane>()
            .assertProperty(false, "the region should not hold the component") { region.view(this) === field }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocations")
    fun aRelocationMovesTheComponentWithoutEndingThePlacement(relocation: RelocationCase) = runComposeSwingTest {
        val field = CallerOwnedField()
        var step by mutableIntStateOf(0)
        setContent { relocation.content({ movableComponentOf(field) }, step) }
        awaitIdle()
        field.nameWrites.clear()
        field.moves.clear()

        step = 1
        awaitIdle()

        assertEquals(relocation.target, field.parent?.name, "the component should stand in the new place")
        assertFalse("home" in field.moves, "the component should not go home on the way")
        assertEquals(emptyList(), field.nameWrites, "the modifier writes should not be given back")
    }

    @Test
    fun movesIntoANestedCompositionAndBack() = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var nested by mutableStateOf(false)
        setContent {
            Panel(SwingModifier.name("A")) {
                if (!nested) movable(SwingModifier.name("placed"))
                NestedContentHost(SwingModifier.name("N")) {
                    if (nested) movable(SwingModifier.name("placed"))
                }
            }
        }
        awaitIdle()
        field.moves.clear()

        nested = true
        awaitIdle()
        assertEquals("N", field.parent?.name, "the component should move into the nested composition")
        assertEquals("placed", field.name, "the modifier write should stay")
        assertFalse("home" in field.moves, "the component should not go home on the way in")

        nested = false
        awaitIdle()
        assertEquals("A", field.parent?.name, "the component should move back")
        assertEquals("placed", field.name, "the modifier write should stay")
        assertFalse("home" in field.moves, "the component should not go home on the way back")
    }

    @Test
    fun insideAnOuterMovableContentTravelsWithIt() = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        val outer =
            movableContentOf {
                Panel(SwingModifier.name("inner")) {
                    movable(SwingModifier.name("placed"))
                }
            }
        var first by mutableStateOf(true)
        setContent {
            if (first) {
                Panel(SwingModifier.name("A")) { outer() }
            } else {
                Panel(SwingModifier.name("B")) { outer() }
            }
        }
        awaitIdle()
        assertEquals("inner", field.parent?.name, "the component should stand in the inner panel")
        field.nameWrites.clear()
        field.moves.clear()

        first = false
        awaitIdle()

        assertEquals("inner", field.parent?.name, "the component should stay in the travelling panel")
        onNodeWithName("B")
            .assertIsOfType<Container>()
            .assertProperty(field.parent, "the travelling panel should stand in B") { getComponent(0) }
        assertFalse(field.moves.any { it != "inner" }, "the component should not stand outside the travelling panel")
        assertEquals(emptyList(), field.nameWrites, "the modifier writes should not be given back")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("secondCalls")
    fun aSecondStandingCallIsRefusedAndWritesNothing(calls: TwoCalls) = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)

        assertFailsWith<IllegalStateException> {
            setContent { calls.content(movable) }
            awaitIdle()
        }

        assertEquals(emptyList(), field.nameWrites, "the incoming call should write nothing")
    }

    @Test
    fun aCallMovedToAnotherContentCompositionStillStandsWhenTheOriginalIsDisposed() = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var inNested by mutableStateOf(true)
        var nestedShown by mutableStateOf(true)
        var second by mutableStateOf(false)
        setContent {
            Panel(SwingModifier.name("outer")) {
                if (nestedShown) NestedContentHost { if (inNested) movable(SwingModifier.name("standing")) }
                if (!inNested) movable(SwingModifier.name("standing"))
                if (second) movable(SwingModifier.name("incoming"))
            }
        }
        awaitIdle()
        inNested = false
        awaitIdle()
        nestedShown = false
        awaitIdle()
        assertEquals("outer", field.parent?.name, "the moved placement should keep the component")
        assertEquals("standing", field.name, "the moved placement should keep its name")
        field.nameWrites.clear()

        second = true
        assertFailsWith<IllegalStateException> { awaitIdle() }

        assertEquals(emptyList(), field.nameWrites, "the incoming call should write nothing")
        assertEquals("outer", field.parent?.name, "the moved placement should keep the component")
        assertEquals("standing", field.name, "the moved placement should keep its name")
    }

    @Test
    fun aSecondCallAfterTheFrameSettlesIsRefusedBeforeItWrites() = runComposeSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        lateinit var parentContext: CompositionContext
        var second by mutableStateOf(false)
        setContent {
            parentContext = rememberCompositionContext()
            if (second) Panel { movable(SwingModifier.name("second")) }
        }
        var generation by mutableIntStateOf(0)
        val composition =
            OnDemandComposition(parentContext) { key(generation) { movable(SwingModifier.name("placed")) } }
        try {
            composition.recompose {}
            composition.recompose { generation = 1 }
            awaitIdle()
            val parentBefore = field.parent
            field.nameWrites.clear()

            assertFailsWith<IllegalStateException> {
                second = true
                awaitIdle()
            }

            assertEquals(emptyList(), field.nameWrites, "the refused call should write nothing")
            assertSame(parentBefore, field.parent, "the standing call should keep the component")
            assertEquals("placed", field.name, "the standing call should keep its name")
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aThrowingDisposalEffectLeavesNoClaimForAFreshSetContentRoot() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        val recomposer = TestRecomposer(this)
        val handle =
            JPanel().setContent(recomposer.recomposer) {
                Panel {
                    movable(SwingModifier.name("placed"))
                    // Composed after the call, so it is disposed first and stops the runtime before the call's release.
                    DisposableEffect(Unit) { onDispose { error("effect disposal failed") } }
                }
            }
        try {
            assertEquals("placed", field.name, "the modifier should have written the name")
            assertFailsWith<IllegalStateException> { handle.dispose() }

            var named by mutableStateOf(false)
            val freshRoot = JPanel()
            val fresh =
                freshRoot.setContent(recomposer.recomposer) {
                    movable(if (named) SwingModifier.name("placed") else SwingModifier)
                }
            try {
                assertSame(freshRoot, field.parent, "the fresh root should place the component")
                assertEquals("own", field.name, "the stale write should have been given back")

                named = true
                recomposer.awaitIdle()
                assertEquals("placed", field.name, "a value equal to the stale one should be written again")
            } finally {
                fresh.dispose()
            }
            assertEquals("own", field.name, "the fresh root's disposal should give the name back")
        } finally {
            recomposer.cancel()
        }
    }

    @Test
    fun aThrowingDisposalEffectLeavesNoClaimForAFreshOnDemandRoot() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        val failure = IllegalStateException("effect disposal failed")
        val composition =
            OnDemandComposition(parentContext) {
                movable(SwingModifier.name("placed"))
                // Composed after the call, so it is disposed first and stops the runtime before the call's release.
                DisposableEffect(Unit) { onDispose { throw failure } }
            }
        composition.recompose {}
        assertEquals("placed", field.name, "the modifier should have written the name")

        assertSame(
            failure,
            assertFailsWith<IllegalStateException> { composition.dispose() },
            "the disposal should fail with the effect's exception",
        )

        assertNull(field.parent, "the placement should end with the disposal")
        var named by mutableStateOf(false)
        val host = JPanel()
        val fresh =
            OnDemandComposition(parentContext, host, addingTo(host)) {
                movable(if (named) SwingModifier.name("placed") else SwingModifier)
            }
        try {
            fresh.recompose {}
            assertSame(host, field.parent, "the fresh root should place the component in its container")
            assertEquals("own", field.name, "the stale write should have been given back")

            fresh.recompose { named = true }
            assertEquals("placed", field.name, "a value equal to the stale one should be written again")
        } finally {
            fresh.dispose()
        }
        assertEquals("own", field.name, "the fresh root's disposal should give the name back")
    }

    @Test
    fun aRefusedPlacementLeavesTheComponentForAFreshRoot() = runSwingTest {
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        val recomposer = TestRecomposer(this)
        val later = TestRecomposer(this)
        val laterRoot = JPanel()
        try {
            assertFailsWith<RuntimeException> {
                JPanel().setContent(recomposer.recomposer) { ScrollPane { movable(SwingModifier.name("placed")) } }
            }

            val handle = laterRoot.setContent(later.recomposer) { movable(SwingModifier.name("again")) }
            try {
                assertSame(laterRoot, field.parent, "a fresh root should place the component")
                assertEquals("again", field.name, "a fresh root should write its name")
            } finally {
                handle.dispose()
            }
            assertEquals("own", field.name, "the fresh root's disposal should give the name back")
        } finally {
            recomposer.cancel()
            later.cancel()
        }
    }

    @Test
    fun anUnusedHandleLeavesTheComponentWhereItIsAndKeepsTheDisposalThatRemovesIt() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        val composition = OnDemandComposition(parentContext) { Panel {} }
        val created = assertIs<JComponent>(composition.recompose {})
        val mine = JPanel()
        mine.add(created)

        movableComponentOf(created)

        assertSame(mine, created.parent, "creating a handle should not move the component")

        composition.dispose()

        assertNull(created.parent, "the disposal should still take the created component out of its container")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("disposalMoments")
    fun disposingAnOnDemandCompositionAfterARelocationTakesTheComponentOut(settled: Boolean) = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        val field = CallerOwnedField()
        val movable = movableComponentOf(field)
        var generation by mutableIntStateOf(0)
        val composition =
            OnDemandComposition(parentContext) { key(generation) { movable(SwingModifier.name("placed")) } }
        val mine = JPanel()
        mine.add(composition.recompose {})
        composition.recompose { generation = 1 }
        if (settled) awaitIdle()
        assertEquals("placed", field.name, "the modifier should have written the name")

        composition.dispose()

        assertNull(field.parent, "the disposal should take the component out of the container it is in")
        assertEquals(0, mine.componentCount, "the disposal should leave the caller's container empty")
        // A composition disposed before the frame settles has released no node of the relocation yet.
        if (settled) assertEquals("own", field.name, "the disposal should give the name back")
    }

    /** What a test needs to end one placement: the name of the container the call stands in, and the content. */
    class EndingCase(
        val standsIn: String,
        val content:
            @Composable @SwingComposable
            (movable: MovableComponent, placed: Boolean) -> Unit,
    )

    /** One region of a scroll pane that a call can fill, and how to read back what the pane holds there. */
    class RegionCase(
        val view: (JScrollPane) -> Component?,
        val content:
            @Composable @SwingComposable
            ScrollPaneScope.(movable: MovableComponent) -> Unit,
    )

    /** One way a call changes place without ending, and the container the component stands in afterwards. */
    class RelocationCase(
        val target: String,
        val content:
            @Composable @SwingComposable
            (handle: () -> MovableComponent, step: Int) -> Unit,
    )

    /** Two calls of one handle composed in the same pass; the second is named `incoming`. */
    class TwoCalls(
        val content:
            @Composable @SwingComposable
            (MovableComponent) -> Unit,
    )

    companion object {
        @JvmStatic
        fun endingPlacements(): List<Named<EndingCase>> = listOf(
            Named.of(
                "the call is no longer composed",
                EndingCase("A") { movable, placed ->
                    Panel(SwingModifier.name("A")) { if (placed) movable(SwingModifier.name("placed")) }
                },
            ),
            Named.of(
                "its content is deactivated",
                EndingCase("A") { movable, placed ->
                    Panel(SwingModifier.name("A")) {
                        ReusableContentHost(placed) { movable(SwingModifier.name("placed")) }
                    }
                },
            ),
            Named.of(
                "its nested content composition is disposed",
                EndingCase("N") { movable, placed ->
                    Panel(SwingModifier.name("A")) {
                        if (placed) NestedContentHost(SwingModifier.name("N")) { movable(SwingModifier.name("placed")) }
                    }
                },
            ),
        )

        @JvmStatic
        fun whereTheComponentStandsWhenHidden(): List<Named<Boolean>> = listOf(
            Named.of("placed in a panel", false),
            Named.of("moved into a nested content composition first", true),
        )

        @JvmStatic
        fun regionsOfAScrollPane(): List<Named<RegionCase>> = listOf(
            Named.of("Viewport", RegionCase({ it.viewport.view }) { movable -> Viewport { movable() } }),
            Named.of(
                "row header",
                RegionCase({ it.rowHeader?.view }) { movable -> movable(SwingModifier.rowHeader()) },
            ),
            Named.of(
                "column header",
                RegionCase({ it.columnHeader?.view }) { movable -> movable(SwingModifier.columnHeader()) },
            ),
        )

        @JvmStatic
        fun relocations(): List<Named<RelocationCase>> = listOf(
            Named.of(
                "a changed key around the call",
                RelocationCase("A") { handle, step ->
                    Panel(SwingModifier.name("A")) { key(step) { handle()(SwingModifier.name("placed")) } }
                },
            ),
            Named.of(
                "if and else between two panels",
                RelocationCase("B") { handle, step ->
                    if (step == 0) {
                        Panel(SwingModifier.name("A")) { handle()(SwingModifier.name("placed")) }
                    } else {
                        Panel(SwingModifier.name("B")) { handle()(SwingModifier.name("placed")) }
                    }
                },
            ),
            Named.of(
                "a handle remembered under a changed key",
                RelocationCase("A") { handle, step ->
                    Panel(SwingModifier.name("A")) {
                        key(step) {
                            val remembered = remember { handle() }
                            remembered(SwingModifier.name("placed"))
                        }
                    }
                },
            ),
        )

        @JvmStatic
        fun secondCalls(): List<Named<TwoCalls>> = listOf(
            Named.of(
                "a sibling call in the same panel",
                TwoCalls { movable ->
                    Panel {
                        movable()
                        movable(SwingModifier.name("incoming"))
                    }
                },
            ),
            Named.of(
                "two nested content compositions of one recomposer",
                TwoCalls { movable ->
                    Panel {
                        NestedContentHost { movable() }
                        NestedContentHost { movable(SwingModifier.name("incoming")) }
                    }
                },
            ),
        )

        @JvmStatic
        fun disposalMoments(): List<Named<Boolean>> =
            listOf(Named.of("disposed at once", false), Named.of("disposed after the frame settles", true))
    }
}
