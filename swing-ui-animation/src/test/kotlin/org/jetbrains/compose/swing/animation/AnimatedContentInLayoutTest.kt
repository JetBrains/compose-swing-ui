package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxSize
import org.jetbrains.compose.swing.foundation.layout.requiredSize
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An animated container under the three parents that hand a child the same bounds whatever preferred
 * size it declares: a `BorderLayout` center region, a weighted row child, and a grid-bag cell filled
 * along both axes. A size transition there animates a box its parent never acts on, which is the case
 * where nothing else would carry the frame to the screen.
 */
class AnimatedContentInLayoutTest {
    /** A parent that offers animated content a region, and the size a half-filled child takes in that region. */
    enum class HalfFillParent(
        val expected: Dimension,
        val claim: String,
    ) {
        GenuineOffer(
            Dimension(200, 200),
            "a genuine 200..300 offer must preserve its minimum through the animation",
        ),
        AssignedMaxima(
            Dimension(150, 150),
            "assigned 300 maxima should resolve half fill to 150 without an inferred minimum",
        ),
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(HalfFillParent::class)
    fun `a half-filled child of animated content keeps a genuine minimum and gains none from an assigned maximum`(
        parent: HalfFillParent,
    ) = runComposeSwingTest {
        var state by mutableStateOf("small")
        setContent {
            when (parent) {
                HalfFillParent.GenuineOffer -> {
                    Layout(
                        measurePolicy = { measurables, _ ->
                            val placeable = measurables.single().measure(Constraints(200, 300, 200, 300))
                            layout(300, 300) { placeable.place(0, 0) }
                        },
                        content = {
                            AnimatedContent(
                                targetState = state,
                                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                            ) { target ->
                                HalfFilledTarget(target, width = if (target == "small") 40 else 600)
                            }
                        },
                    )
                }

                HalfFillParent.AssignedMaxima -> {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(300, 300)) {
                        AnimatedContent(
                            targetState = state,
                            modifier = SwingModifier.center(),
                            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                        ) { target ->
                            HalfFilledTarget(target, width = if (target == "small") 40 else 600)
                        }
                    }
                }
            }
        }

        mainClock.autoAdvance = false
        state = "large"
        driveOneFrame()

        assertEquals(parent.expected, onNodeWithTag("large-half-fill").fetch().size, parent.claim)
    }

    @Test
    fun `center keeps its assigned region when natural content is smaller`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, 300)) {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.center().testTag("animated"),
                        transitionSpec = {
                            (fadeIn(tween(1_000)) togetherWith fadeOut(tween(1_000))) using
                                SizeTransform { _, _ -> tween(1_000) }
                        },
                    ) { target ->
                        Label(
                            target,
                            modifier =
                                SwingModifier
                                    .testTag("content-$target")
                                    .fillMaxSize()
                                    .preferredSize(200, 150),
                        )
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()

            val animated = onNodeWithTag("animated").fetch<Container>()
            val content = onNodeWithTag("content-b").fetch<JComponent>()
            assertEquals(Dimension(200, 150), animated.preferredSize)
            assertEquals(Dimension(400, 300), animated.size)
            assertEquals(Dimension(400, 300), content.size)
        }

    @Test
    fun `a transition runs to completion under a parent that ignores the size the container asks for`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(REGION_WIDTH, REGION_HEIGHT)) {
                    AnimatedContent(targetState = state, modifier = SwingModifier.center()) { Body(state = it) }
                }
                Row(modifier = SwingModifier.preferredSize(REGION_WIDTH, REGION_HEIGHT)) {
                    AnimatedContent(targetState = state, modifier = SwingModifier.weight(1f)) { Body(state = it) }
                }
                Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(REGION_WIDTH, REGION_HEIGHT)) {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH),
                    ) {
                        Body(state = it)
                    }
                }
            }
            val regions = PARENTS.indices.map { containerIn(it).bounds }

            state = "b"
            // The frame the size animation aims at comes from a layout pass, so a container that read its
            // own animated size back would keep producing frames and this would run to the frame ceiling
            // and fail rather than return.
            awaitIdle()

            for ((index, parent) in PARENTS.withIndex()) {
                val container = containerIn(index)
                assertEquals(1, container.componentCount, "$parent: the content that finished exiting stands still")
                assertEquals("b", container.contentText(0), "$parent: the container did not settle on the target")
                assertEquals(regions[index], container.bounds, "$parent: the parent reshaped its region after all")
            }
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["AnimatedContent", "AnimatedVisibility"])
    fun `required-size content overflows the region of a stock parent the same at rest and during a transition`(
        container: String,
    ) = runComposeSwingTest {
        var state by mutableStateOf("a")
        setContent {
            Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(100, 100)) {
                if (container == "AnimatedContent") {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.center(),
                        transitionSpec = { fadeIn(tween(1_000)) togetherWith fadeOut(tween(1_000)) },
                    ) { target ->
                        Label(target, modifier = SwingModifier.testTag("content-$target").requiredSize(300, 200))
                    }
                } else {
                    AnimatedVisibility(
                        visible = state == "a",
                        modifier = SwingModifier.center(),
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(1_000)),
                    ) {
                        Label("a", modifier = SwingModifier.testTag("content-a").requiredSize(300, 200))
                    }
                }
            }
        }
        val stockParent = root.getComponent(0) as Container
        val expected = Rectangle((100 - 300) / 2, (100 - 200) / 2, 300, 200)

        assertEquals(expected, boundsIn(stockParent, "content-a"), "at rest")

        mainClock.autoAdvance = false
        state = "b"
        driveOneFrame()
        driveOneFrame()

        assertEquals(expected, boundsIn(stockParent, "content-a"), "the exiting content mid-transition")
        if (container == "AnimatedContent") {
            assertEquals(expected, boundsIn(stockParent, "content-b"), "the entering content mid-transition")
        }
    }

    /** The animated container standing in the parent at [index], which is that parent's one child. */
    private fun ComposeSwingTest.containerIn(index: Int): Container =
        (root.getComponent(index) as Container).getComponent(0) as Container
}

/** Content whose size changes with the state, so the container's own size animates between the two. */
@Composable
private fun Body(state: String) =
    Label(
        text = state,
        modifier = SwingModifier.preferredSize(width = if (state == "a") 40 else 160, height = 20),
    )

/** A half-filled box around a state-dependent preferred-size label. */
@Composable
private fun HalfFilledTarget(
    target: String,
    width: Int,
) {
    Box(modifier = SwingModifier.testTag("$target-half-fill")) {
        Label(
            target,
            modifier = SwingModifier.fillMaxSize(0.5f).preferredSize(width, width),
        )
    }
}

/** The parents under test, named so a failure says which one it is. */
private val PARENTS = listOf("a border layout's center region", "a weighted row child", "a filled grid-bag cell")

private const val REGION_WIDTH = 240
private const val REGION_HEIGHT = 80

/** The bounds of the content tagged [tag] in the coordinates of [stockParent], whatever containers hold it. */
private fun ComposeSwingTest.boundsIn(
    stockParent: Container,
    tag: String,
): Rectangle {
    val content = onNodeWithTag(tag).fetch<JComponent>()
    return SwingUtilities.convertRectangle(content.parent, content.bounds, stockParent)
}
