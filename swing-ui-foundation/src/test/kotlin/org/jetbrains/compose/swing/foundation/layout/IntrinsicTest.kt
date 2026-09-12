package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JSplitPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A container is asked two argument-less questions - what it prefers, and the least it can occupy - and
 * both run its measure policy rather than its layout pass. The policy writes one body; which of the two
 * is being asked decides only which extent a child that cannot answer a constrained question answers
 * with.
 *
 * This stands in for androidx `foundation-layout`'s own `IntrinsicTest`. Every case there measures
 * through `IntrinsicSize.Min` or `IntrinsicSize.Max` as a modifier, or asks for one axis while fixing
 * the other, and neither reaches a Swing widget: `getPreferredSize()` and `getMinimumSize()` take no
 * argument, a cross-axis extent threaded down would be discarded at the first widget, and every chain
 * ends at one. The cases here pin the questions this tree does ask, and which of the policy's two
 * entry points each of them reaches.
 */
class IntrinsicTest {
    @Test
    fun aRowsMinimumStacksWhatEachChildCanShrinkTo() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                }
            }

            assertEquals(
                Dimension(50, 30),
                containerMinimumSize(),
                "a row can shrink to what its children can, which is a question apart from what it prefers",
            )
            assertEquals(Dimension(160, 80), containerPreferredSize(), "and still prefers what they prefer")
        }

    @Test
    fun aNestedRowsMinimumIsWorkedOutByItsOwnPolicy() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Row {
                        ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                        ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                    }
                }
            }

            assertEquals(
                Dimension(50, 30),
                containerMinimumSize(),
                "the question reaches the inner row through getMinimumSize, so the inner policy answers it " +
                    "one level down rather than reporting what that row prefers",
            )
        }

    @Test
    fun aBoxsMinimumIsTheLargestItsChildrenCanShrinkTo() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                }
            }

            assertEquals(
                Dimension(30, 30),
                containerMinimumSize(),
                "a box stacks its children in one place, so it can shrink no further than the largest of " +
                    "them can",
            )
        }

    @Test
    fun aWeightedChildsShareOfTheMinimumIsTakenAgainstItsOwnMinimum() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                    ShrinkableChild(1, prefers = 80, shrinksTo = 30, modifier = SwingModifier.weight(1f))
                }
            }

            assertEquals(
                Dimension(50, 30),
                containerMinimumSize(),
                "a weight divides what the other children leave, and what a weighted child implies for the " +
                    "minimum is the extent it can shrink to rather than the one it prefers",
            )
        }

    @Test
    fun aWeightedIntrinsicRoundsTheUnitShareBeforeItMultipliesIt() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child", modifier = SwingModifier.weight(2f).preferredSize(1, 1))
                }
            }

            assertEquals(
                Dimension(2, 1),
                containerPreferredSize(),
                "a child one unit wide and weighted 2f needs one rounded unit per weight unit, so its row asks for two",
            )
        }

    @Test
    fun anIntrinsicWalkAsksANestedContainerNoConstrainedQuestion() =
        runComposeSwingTest {
            val inner = CountingPanel()
            setContent {
                Column(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    SwingNode(factory = { inner }, modifier = SwingModifier)
                }
            }
            inner.forgetQuestions()

            containerPreferredSize()
            containerMinimumSize()

            assertEquals(
                0,
                inner.constrainedQuestions,
                "a container asked what it prefers has no extent to offer, so it must ask a child the " +
                    "argument-less question and never the constrained one - asking one there would hand the " +
                    "child the outer pass's unbounded constraints",
            )
            assertTrue(inner.plainQuestions > 0, "and it must ask, rather than answering for the child itself")
        }

    @Test
    fun aLayoutPassDoesAskANestedContainerAConstrainedQuestion() =
        runComposeSwingTest {
            val inner = CountingPanel()
            setContent {
                Column(modifier = containerModifier(200, 200)) {
                    SwingNode(factory = { inner }, modifier = SwingModifier)
                }
            }

            assertTrue(
                inner.constrainedQuestions > 0,
                "a container laying its children out has an extent to offer, which is what carries a " +
                    "constraint through a container of this library's own",
            )
        }

    @Test
    fun aSizeSetOnAContainerOutrightAnswersItsConstrainedQuestionToo() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(400, 100)) {
                    Row(modifier = SwingModifier.preferredSize(200, 60)) { SizedChild(0) }
                }
            }

            assertEquals(
                listOf(Rectangle(0, 0, 200, 60)),
                childBounds(),
                "a size set on a container answers for it whatever the question, so the inner row occupies " +
                    "the size it was given rather than the one its own policy works out for its child",
            )
        }

    @Test
    fun aSplitPaneTakesItsDividerLimitFromTheMinimumTheRowsPolicyWorksOut() =
        runComposeSwingTest {
            setContent {
                SplitPane(modifier = SwingModifier.testTag(CONTAINER_TAG).preferredSize(400, 100)) {
                    Row(modifier = SwingModifier.first()) {
                        ShrinkableChild(0, prefers = 80, shrinksTo = 20)
                        ShrinkableChild(1, prefers = 80, shrinksTo = 30)
                    }
                    Label(text = "second", modifier = SwingModifier.second())
                }
            }

            val pane = onNodeWithTag(CONTAINER_TAG).fetch<JSplitPane>()
            assertEquals(
                50,
                pane.minimumDividerLocation - pane.insets.left,
                "a split pane holds its divider clear of what the side it splits can shrink to, and that " +
                    "extent is what the row's own policy works out in its minimum mode",
            )
        }
}

/** A child that prefers one extent on both axes and declares it can shrink to another. */
@Composable
private fun ShrinkableChild(
    index: Int,
    prefers: Int,
    shrinksTo: Int,
    modifier: SwingModifier = SwingModifier,
) {
    Label(
        "child $index",
        modifier = modifier.preferredSize(prefers, prefers).minimumSize(shrinksTo, shrinksTo),
    )
}

/**
 * A container of this library's own, counting which of its policy's two entry points its parent
 * reaches: `measure`, which only a caller holding an extent to offer can ask, and `intrinsicSize`,
 * which is what an argument-less question routes to.
 */
private class CountingPanel : ConstrainedPanel(TestPolicyLayout(CountingPolicy())) {
    private val counting: CountingPolicy get() = (layout as TestPolicyLayout).policy as CountingPolicy

    val constrainedQuestions: Int get() = counting.measured

    val plainQuestions: Int get() = counting.asked

    fun forgetQuestions(): Unit = counting.forget()
}

/** A policy that takes the extent of nothing, and counts which question it was asked. */
private class CountingPolicy : MeasurePolicy {
    var measured: Int = 0
        private set

    var asked: Int = 0
        private set

    fun forget() {
        measured = 0
        asked = 0
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        measured++
        return NoExtent
    }

    override fun MeasureScope.intrinsicSize(measurables: List<Measurable>): MeasureResult {
        asked++
        return NoExtent
    }
}

/** A result occupying nothing and placing nobody. */
private object NoExtent : MeasureResult {
    override val width: Int get() = 0
    override val height: Int get() = 0

    override fun PlacementScope.placeChildren(): Unit = Unit
}
