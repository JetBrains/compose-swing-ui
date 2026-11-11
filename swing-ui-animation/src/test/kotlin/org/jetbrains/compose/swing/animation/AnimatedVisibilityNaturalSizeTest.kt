// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.NaturalSize.LONG
import org.jetbrains.compose.swing.animation.NaturalSize.OWN
import org.jetbrains.compose.swing.animation.NaturalSize.PARENT
import org.jetbrains.compose.swing.animation.NaturalSize.ParentRegion
import org.jetbrains.compose.swing.animation.Stretch.CONTENT
import org.jetbrains.compose.swing.animation.Stretch.FRAME_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.WIDE
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.onSizeChanged
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Dimension
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [AnimatedVisibility] under Swing keeps its content within the container's assigned bounds during transitions,
 * and so does an [AnimatedContent] under a Foundation width cap.
 */
class AnimatedVisibilityNaturalSizeTest {
    @ParameterizedTest(name = "{0}")
    @EnumSource(SizeChange::class)
    fun `a size change keeps content within the original or resized assigned bounds`(change: SizeChange) =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var parent by mutableStateOf(PARENT)
            setContent { Region(parent, visible, change.enter) }
            mainClock.autoAdvance = false
            visible = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertWithinAssignedBounds(parent, "while entering")
            if (change.resizesParent) {
                parent = Dimension(PARENT.width + 20, PARENT.height)
                repeat(3) { mainClock.advanceTimeByFrame() }
                assertWithinAssignedBounds(parent, "after the parent resized")
            }
            assertSettledAt(parent)
        }

    @Test
    fun `a fade-only enter and a shrinking default exit both keep content within assigned bounds`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                ParentRegion(PARENT) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.center().testTag(CONTAINER),
                        enter = fadeIn(tween(LONG, LONG)),
                    ) {
                        Block()
                    }
                }
            }
            mainClock.autoAdvance = false
            visible = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            onNodeWithTag(CONTENT).assertProperty(PARENT, "a fade-only enter changes no size") { size }

            mainClock.autoAdvance = true
            awaitIdle()
            mainClock.autoAdvance = false
            visible = false
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertWithinAssignedBounds(PARENT, "while exiting")
        }

    @Test
    fun `an expand interrupted by a fade keeps content at the assigned bounds on every frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent { Region(PARENT, visible, expandIn(tween(LONG)), fadeOut(tween(2 * LONG))) }
            mainClock.autoAdvance = false
            visible = true
            repeat(3) { mainClock.advanceTimeByFrame() }
            visible = false
            val fadeFrames = 2 * LONG / FRAME_MILLIS
            repeat(fadeFrames + 3) { frame ->
                mainClock.advanceTimeByFrame()
                val present = onAllNodesWithTag(CONTENT).fetchSize() > 0
                if (frame < fadeFrames - 6) assertTrue(present, "frame $frame: the fade has not ended")
                if (present) {
                    assertWithinAssignedBounds(PARENT, "frame $frame")
                    onNodeWithTag(CONTENT)
                        .assertProperty(Point(0, 0), "frame $frame: expand anchoring adds no inner viewport") {
                            location
                        }
                }
            }
            onNodeWithTag(CONTENT).assertDoesNotExist()
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(InterruptedExit::class)
    fun `an expanding re-enter that interrupts an exit keeps content within assigned bounds`(
        interrupted: InterruptedExit,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent { Region(PARENT, visible, expandIn(tween(TRANSITION_MILLIS)), interrupted.exit) }
        visible = true
        awaitIdle()
        onNodeWithTag(CONTENT).assertProperty(PARENT, "the expand ends in the parent's space") { size }

        mainClock.autoAdvance = false
        visible = false
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertWithinAssignedBounds(PARENT, "while exiting")
        visible = true
        repeat(3) { mainClock.advanceTimeByFrame() }
        onNodeWithTag(CONTENT).assertProperty(PARENT, "re-entry keeps the content within assigned bounds") { size }

        mainClock.autoAdvance = true
        awaitIdle()
        onNodeWithTag(CONTENT).assertProperty(PARENT, "the content ends in the parent's space") { size }
    }

    @Test
    fun `an expand interrupted by a fade exit keeps content at assigned size through re-entry`() {
        val sizes =
            contentSizesDuring(
                startsVisible = false,
                enter = expandIn(tween(LONG)),
                exit = fadeOut(tween(2 * LONG)),
            ) { state ->
                state.visible = true
                repeat(3) { mainClock.advanceTimeByFrame() }
                state.visible = false
                repeat(LONG / FRAME_MILLIS + 3) { mainClock.advanceTimeByFrame() }
                assertEquals(listOf(PARENT), state.sizes, "actual size remains the parent's assigned region")
                state.visible = true
                repeat(3) { mainClock.advanceTimeByFrame() }
                assertEquals(listOf(PARENT), state.sizes, "the interrupted transition keeps assigned content size")
                mainClock.autoAdvance = true
                awaitIdle()
            }

        assertEquals(listOf(PARENT), sizes, "completing the transition does not change assigned size")
    }

    @Test
    fun `a shrink interrupted by a fade re-enter keeps content at assigned size through re-entry`() {
        val sizes =
            contentSizesDuring(
                startsVisible = true,
                enter = fadeIn(tween(LONG)),
                exit = shrinkOut(tween(LONG)) + fadeOut(tween(LONG)),
            ) { state ->
                assertEquals(listOf(PARENT), state.sizes, "settled content fits the parent's space")
                state.visible = false
                repeat(LONG / 2 / FRAME_MILLIS) { mainClock.advanceTimeByFrame() }
                state.visible = true
                repeat(3) { mainClock.advanceTimeByFrame() }
                assertEquals(listOf(PARENT), state.sizes, "the interrupted transition keeps assigned content size")
                mainClock.autoAdvance = true
                awaitIdle()
            }

        assertEquals(listOf(PARENT), sizes, "completing the transition does not change assigned size")
    }

    @Test
    fun `a parent resize during a fade updates actual content to the new assigned bounds`() {
        val narrowed = Dimension(PARENT.width - 20, PARENT.height)
        val sizes =
            contentSizesDuring(
                startsVisible = false,
                enter = fadeIn(tween(2 * LONG)),
                exit = shrinkOut(tween(LONG)),
            ) { state ->
                state.visible = true
                repeat(3) { mainClock.advanceTimeByFrame() }
                state.parent = narrowed
                repeat(2 * LONG / FRAME_MILLIS + 3) { mainClock.advanceTimeByFrame() }
            }

        assertEquals(listOf(PARENT, narrowed), sizes, "actual content follows assigned bounds during the fade")
    }

    @Test
    fun `content of a container that starts out exiting keeps the assigned size for the whole exit`() =
        runComposeSwingTest {
            val sizes = mutableListOf<Dimension>()
            setContent {
                ParentRegion(PARENT) {
                    InitiallyExiting(
                        exit = fadeOut(tween(2 * LONG)),
                        modifier = SwingModifier.center().testTag(CONTAINER),
                        enter = expandIn(tween(LONG)),
                    ) {
                        Block(sizes::add)
                    }
                }
            }
            mainClock.autoAdvance = false
            repeat(2 * LONG / FRAME_MILLIS - 3) { mainClock.advanceTimeByFrame() }
            assertEquals(listOf(PARENT), sizes, "the exit keeps actual content at its assigned size")
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CappedContainer::class)
    fun `content never exceeds a Foundation width cap on any frame of its enter`(container: CappedContainer) =
        runComposeSwingTest {
            var shown by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Flow(), style = { preferredSize(WIDE, WIDE) }) {
                    Box {
                        Box(modifier = SwingModifier.width(100)) {
                            with(container) {
                                EnterContent(shown, SwingModifier.testTag(CONTAINER)) {
                                    Label(
                                        "",
                                        modifier = SwingModifier.testTag(CONTENT),
                                        style = { preferredSize(WIDE, 20) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            shown = true
            repeat(TRANSITION_MILLIS / FRAME_MILLIS + 4) {
                mainClock.advanceTimeByFrame()
                onNodeWithTag(CONTENT).assertProperty(100, "the entering content is capped on every frame") { width }
            }
        }

    @Composable
    private fun Region(
        size: Dimension,
        visible: Boolean,
        enter: EnterTransition,
        exit: ExitTransition = ExitTransition.None,
        onContentSize: (Dimension) -> Unit = {},
    ) {
        ParentRegion(size) {
            AnimatedVisibility(
                visible = visible,
                modifier = SwingModifier.center().testTag(CONTAINER),
                enter = enter,
                exit = exit,
            ) {
                Block(onContentSize)
            }
        }
    }

    /**
     * The sizes [Block] reports while [script] drives an [AnimatedVisibility] in [PARENT] that [startsVisible] or not,
     * with the clock stopped. The script reads the sizes reported so far from [VisibilityState.sizes].
     */
    private fun contentSizesDuring(
        startsVisible: Boolean,
        enter: EnterTransition,
        exit: ExitTransition,
        script: suspend ComposeSwingTest.(state: VisibilityState) -> Unit,
    ): List<Dimension> {
        val state = VisibilityState(startsVisible)
        runComposeSwingTest {
            setContent { Region(state.parent, state.visible, enter, exit, state.sizes::add) }
            mainClock.autoAdvance = false
            script(state)
        }
        return state.sizes
    }

    /**
     * What a [contentSizesDuring] script changes: whether the content is visible, and the size of its parent; and
     * what it reads: the sizes the content has reported.
     */
    private class VisibilityState(
        startsVisible: Boolean,
    ) {
        val sizes = mutableListOf<Dimension>()
        var visible by mutableStateOf(startsVisible)
        var parent by mutableStateOf(PARENT)
    }

    /** An enter that changes the container's size, with the parent staying as it is or resizing mid-transition. */
    enum class SizeChange(
        val enter: EnterTransition,
        val resizesParent: Boolean,
    ) {
        ExpandInOriginalParent(expandIn(tween(LONG, LONG)), resizesParent = false),
        ExpandInResizedParent(expandIn(tween(LONG, LONG)), resizesParent = true),
        ExpandHorizontallyOriginalParent(expandHorizontally(tween(LONG, LONG)), resizesParent = false),
        ExpandHorizontallyResizedParent(expandHorizontally(tween(LONG, LONG)), resizesParent = true),
    }

    /** An exit that an expanding re-enter interrupts. */
    enum class InterruptedExit(
        val exit: ExitTransition,
    ) {
        ShrinkingExit(shrinkOut(tween(LONG))),
        FadeOnlyExit(fadeOut(tween(LONG))),
    }

    /** The animated container whose content is entered under a Foundation width cap. */
    enum class CappedContainer {
        Visibility {
            @Composable
            override fun ConstrainedScope.EnterContent(
                shown: Boolean,
                modifier: SwingModifier,
                content: @Composable () -> Unit,
            ) {
                AnimatedVisibility(
                    visible = shown,
                    modifier = modifier,
                    enter = expandIn(tween(TRANSITION_MILLIS)),
                    exit = ExitTransition.None,
                ) {
                    content()
                }
            }
        },
        Content {
            @Composable
            override fun ConstrainedScope.EnterContent(
                shown: Boolean,
                modifier: SwingModifier,
                content: @Composable () -> Unit,
            ) {
                AnimatedContent(
                    shown,
                    modifier,
                    transitionSpec = { expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut() },
                ) { wide ->
                    if (wide) content() else Label("", style = { preferredSize(50, 20) })
                }
            }
        },
        ;

        /** The container of this kind entering [content] once [shown]; the calling scope selects the overload. */
        @Composable
        abstract fun ConstrainedScope.EnterContent(
            shown: Boolean,
            modifier: SwingModifier,
            content: @Composable () -> Unit,
        )
    }
}

/** Prefers [OWN], which is larger than the parent's space on both axes. */
@Composable
private fun Block(onSize: (Dimension) -> Unit = {}) {
    Label("", modifier = SwingModifier.testTag(CONTENT).onSizeChanged(onSize), style = { preferredSize(OWN) })
}
