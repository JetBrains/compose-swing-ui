package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.RepaintManager
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A box stacks its children one over another: the last child declared stands over the ones before it,
 * and a child naming a `zIndex` stands over every sibling naming a smaller one wherever the two are
 * declared. The child on top is the one painted last, and the one a mouse event at a point they share
 * is delivered to.
 */
class BoxStackOrderTest {
    private var standingRepaintManager: RepaintManager? = null

    @BeforeTest
    fun rememberRepaintManager() {
        standingRepaintManager = RepaintManager.currentManager(null)
    }

    @AfterTest
    fun restoreRepaintManager() {
        // The repaint manager is process-wide, and a recorder left installed would count the repaints
        // asked for by every later test.
        RepaintManager.setCurrentManager(standingRepaintManager)
    }

    @Test
    fun theChildDeclaredLastPaintsOverTheOnesBeforeIt() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT)) {
                    FilledChild(Color.RED)
                    FilledChild(Color.BLUE)
                }
            }

            val painted = onNodeWithTag(CONTAINER_TAG).captureToImage()

            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "the last child declared must paint over the ones before it",
            )
        }

    /**
     * Hit-testing walks the children in the order their container holds them and takes the first one the
     * point falls in - the same walk that decides which of them paints last - so this is the component a
     * press at that point is delivered to.
     */
    @Test
    fun theChildDeclaredLastTakesAPressWhereTheChildrenOverlap() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT)) {
                    SizedChild(0)
                    SizedChild(1)
                }
            }

            val pressed = SwingUtilities.getDeepestComponentAt(box(), CHILD_WIDTH / 2, CHILD_HEIGHT / 2)

            assertEquals(
                "child 1",
                (pressed as JLabel).text,
                "a press where two children overlap must reach the last one declared, the one painted on top",
            )
        }

    @Test
    fun theStackFollowsDeclarationOrderThroughEveryChange() =
        runComposeSwingTest {
            var declared by mutableStateOf(listOf("a", "b", "c"))
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    for (name in declared) key(name) { Label("child $name") }
                }
            }

            assertEquals(listOf("child a", "child b", "child c"), stackedChildText(), "the children as declared")

            declared = listOf("a", "d", "b", "c")
            awaitIdle()

            assertEquals(
                listOf("child a", "child d", "child b", "child c"),
                stackedChildText(),
                "a child declared between two others must stack between them",
            )

            declared = listOf("a", "d", "c")
            awaitIdle()

            assertEquals(
                listOf("child a", "child d", "child c"),
                stackedChildText(),
                "the children left must keep the order they are declared in",
            )

            val (a, d, c) = stackedChildren()
            declared = listOf("c", "a", "d")
            awaitIdle()

            assertEquals(
                listOf(c, a, d),
                stackedChildren(),
                "a child that moves must carry its own component to where it is now declared",
            )
        }

    @Test
    fun aChildDeclaringAZIndexPaintsOverALaterSibling() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT)) {
                    FilledChild(Color.RED, SwingModifier.zIndex(1f))
                    FilledChild(Color.BLUE)
                }
            }

            val painted = onNodeWithTag(CONTAINER_TAG).captureToImage()

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(CHILD_WIDTH / 2, CHILD_HEIGHT / 2),
                "the child declaring the larger zIndex must paint over one declared after it",
            )
        }

    @Test
    fun aChildDeclaringAZIndexTakesAPressFromALaterSibling() =
        runComposeSwingTest {
            setContent {
                Box(modifier = containerModifier(CHILD_WIDTH, CHILD_HEIGHT)) {
                    SizedChild(0, SwingModifier.zIndex(1f))
                    SizedChild(1)
                }
            }

            val pressed = SwingUtilities.getDeepestComponentAt(box(), CHILD_WIDTH / 2, CHILD_HEIGHT / 2)

            assertEquals(
                "child 0",
                (pressed as JLabel).text,
                "a press where the children overlap must reach the one declaring the larger zIndex",
            )
        }

    @Test
    fun childrenDeclaringOneZIndexStackInDeclarationOrder() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child a", modifier = SwingModifier.zIndex(2f))
                    Label("child b")
                    Label("child c", modifier = SwingModifier.zIndex(2f))
                }
            }

            assertEquals(
                listOf("child b", "child a", "child c"),
                stackedChildText(),
                "children declaring one zIndex must stack in declaration order, over the child declaring none",
            )
        }

    @Test
    fun multipleZIndexDeclarationsAddTheirValues() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child a", modifier = SwingModifier.zIndex(0.6f).zIndex(0.6f))
                    Label("child b", modifier = SwingModifier.zIndex(1f))
                }
            }

            assertEquals(
                listOf("child b", "child a"),
                stackedChildText(),
                "two zIndex declarations must add to 1.2 and lift child a over child b at 1.0",
            )
        }

    @Test
    fun aChildComposedBetweenTwoOfDifferentZIndexStacksWhereItIsDeclared() =
        runComposeSwingTest {
            var declared by mutableStateOf(listOf("a", "b"))
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    for (name in declared) key(name) { LiftedChild(name, lifted = "a") }
                }
            }

            assertEquals(
                listOf("child b", "child a"),
                stackedChildText(),
                "the child declaring a zIndex must stand over the one declared after it",
            )

            declared = listOf("a", "d", "b")
            awaitIdle()

            assertEquals(
                listOf("child d", "child b", "child a"),
                stackedChildText(),
                "a child composed between two others must stack under the one it is declared before",
            )
        }

    @Test
    fun aChildThatChangesItsZIndexIsStackedAgain() =
        runComposeSwingTest {
            var lifted by mutableStateOf(false)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child a", modifier = SwingModifier.zIndex(if (lifted) 1f else 0f))
                    Label("child b")
                }
            }

            val (a, b) = stackedChildren()

            lifted = true
            awaitIdle()

            assertEquals(
                listOf(b, a),
                stackedChildren(),
                "a child whose zIndex rises must be carried over its sibling, and carry its own component there",
            )

            lifted = false
            awaitIdle()

            assertEquals(
                listOf(a, b),
                stackedChildren(),
                "and must go back under that sibling where the zIndex it declares drops again",
            )
        }

    @Test
    fun theChildrenLeftKeepTheirStackWhenOneIsRemoved() =
        runComposeSwingTest {
            var declared by mutableStateOf(listOf("a", "b", "c"))
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    for (name in declared) key(name) { LiftedChild(name, lifted = "b") }
                }
            }

            assertEquals(
                listOf("child a", "child c", "child b"),
                stackedChildText(),
                "the child declaring a zIndex must stand over both the others",
            )

            declared = listOf("b", "c")
            awaitIdle()

            assertEquals(
                listOf("child c", "child b"),
                stackedChildText(),
                "the children left must keep the stack the removed one was part of",
            )

            declared = listOf("b", "d", "c")
            awaitIdle()

            assertEquals(
                listOf("child d", "child c", "child b"),
                stackedChildText(),
                "and must take a child composed among them into that same stack",
            )
        }

    @Test
    fun aChildRemovedIsTheOneTheBoxGivesUp() =
        runComposeSwingTest {
            var declared by mutableStateOf(listOf("a", "b"))
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    for (name in declared) key(name) { SwingNode(factory = { AlikeChild() }) }
                }
            }

            declared = listOf("a")
            awaitIdle()
            declared = listOf("a", "c")
            awaitIdle()

            assertEquals(
                2,
                box().componentCount,
                "the box must give up the child it dropped rather than one equal to it, and take no " +
                    "dropped child back into the stack",
            )
        }

    @Test
    fun aChildLiftedOverASiblingAsksForARepaint() =
        runComposeSwingTest {
            var lifted by mutableStateOf(false)
            setContent {
                Box(modifier = SwingModifier.testTag(CONTAINER_TAG)) {
                    Label("child a", modifier = SwingModifier.zIndex(if (lifted) 1f else 0f))
                    Label("child b")
                }
            }

            var repaints = 0
            recordRepaintsOf(box()) { repaints++ }
            lifted = true
            awaitIdle()

            assertTrue(
                repaints > 0,
                "a stack that changed must ask to be painted again: nothing else in the pass does, because " +
                    "every child keeps the bounds it had",
            )
        }

    @Test
    fun aBoxStacksTheChildrenItTookAfterRefusingOne() {
        val box = OverlapPanel(OverlapLayout(Alignment.TopStart))
        val dropped = JLabel("dropped")

        assertFailsWith<IllegalArgumentException> { box.add(dropped, "North") }

        val (over, under) = liftedPairAddedTo(box)
        assertEquals(
            listOf(over, under, dropped),
            box.components.toList(),
            "a box must stack the children it takes after one whose constraint it refused",
        )
    }

    @Test
    fun aBoxStacksTheChildrenItTakesAfterBeingEmptied() {
        val box = OverlapPanel(OverlapLayout(Alignment.TopStart))
        box.add(JLabel("first"), BoxConstraint())

        box.removeAll()

        val (over, under) = liftedPairAddedTo(box)
        assertEquals(
            listOf(over, under),
            box.components.toList(),
            "a box emptied at once must hold the children it takes next, and hold none it gave up",
        )
    }
}

/**
 * Adds two children to [box] - one declaring a zIndex over the other - and answers them in the order the
 * component array must hold them, the top of the stack first.
 */
private fun liftedPairAddedTo(box: OverlapPanel): Pair<Component, Component> {
    val under = JLabel("under")
    val over = JLabel("over")
    box.add(under, BoxConstraint())
    box.add(over, BoxConstraint(zIndex = 1f))
    return over to under
}

/**
 * Counts the repaints asked for on [component] through [onRepaint], for as long as the test runs.
 *
 * `JComponent.repaint()` routes through `RepaintManager.addDirtyRegion`, which is read before the
 * manager's own `isShowing` gate would drop the request off-screen. `BoxStackOrderTest` puts the
 * standing manager back after each test.
 */
private fun recordRepaintsOf(
    component: JComponent,
    onRepaint: () -> Unit,
) {
    RepaintManager.setCurrentManager(
        object : RepaintManager() {
            override fun addDirtyRegion(
                c: JComponent,
                x: Int,
                y: Int,
                w: Int,
                h: Int,
            ) {
                if (c === component) onRepaint()
                super.addDirtyRegion(c, x, y, w, h)
            }
        },
    )
}

/**
 * A child equal to every other child of its kind, which is how `Container.remove(Component)` - and so
 * the applier removing a child - can resolve a component other than the one the composition dropped.
 */
private class AlikeChild : JLabel("alike") {
    override fun equals(other: Any?): Boolean = other is AlikeChild

    override fun hashCode(): Int = javaClass.hashCode()
}

/** The text of each child of the box, from the bottom of its stack up. */
private fun ComposeSwingTest.stackedChildText(): List<String> = stackedChildren().map { (it as JLabel).text }

/**
 * A child painting [color] over the whole of its bounds and drawing nothing else, so every pixel it
 * covers reads that color whatever the look and feel would have drawn a caption with.
 */
@Composable
private fun FilledChild(
    color: Color,
    modifier: SwingModifier = SwingModifier,
) {
    Panel(
        PanelLayout.Flow(),
        modifier = modifier.opaque(true).background(color).preferredSize(CHILD_WIDTH, CHILD_HEIGHT),
    ) {}
}

/** A child named [name], standing over its siblings where that name is the [lifted] one. */
@Composable
private fun BoxScope.LiftedChild(
    name: String,
    lifted: String,
) {
    Label("child $name", modifier = if (name == lifted) SwingModifier.zIndex(1f) else SwingModifier)
}
