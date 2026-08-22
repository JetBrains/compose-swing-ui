package org.jetbrains.compose.swing.swingmark

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import javax.swing.JTree
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves both arms of every SwingMark test build the same widgets.
 *
 * A time from one arm read against the other means nothing until they show the same screen: an arm that
 * builds less is faster for a reason no reader of the report would accept.
 */
@Ignore("TODO restore once swing-ui-test publishes assertComponentTreesEquivalent")
class ArmStructureTest {
    @Test
    fun subMenus() = runComposeSwingTest { assertArmsAgree("Sub-Menus") }

    @Test
    fun textArea() = runComposeSwingTest { assertArmsAgree("TextArea") }

    @Test
    fun sliders() = runComposeSwingTest { assertArmsAgree("Sliders") }

    @Test
    fun lists() = runComposeSwingTest { assertArmsAgree("Lists") }

    @Test
    fun tableRows() = runComposeSwingTest { assertArmsAgree("Table Rows") }

    /**
     * A tree's nodes are not components: one `JTree` renders all of them, so a walk of the widgets says
     * the same thing about a tree of one node and a tree of two hundred. What the two arms show is
     * compared here as rows, and the count is required to be a real structure so that a comparison of
     * two roots cannot pass for one.
     */
    @Test
    fun tree() =
        runComposeSwingTest {
            withArms("Tree") { raw, declared ->
                // TODO restore once swing-ui-test publishes assertComponentTreesEquivalent.
                // assertComponentTreesEquivalent(raw, declared)
                val rawNodes = treeOf(raw).nodeCount()
                val declaredNodes = treeOf(declared).nodeCount()
                assertTrue(
                    rawNodes > 1,
                    "the arms were compared on a tree of $rawNodes node(s), which is the tree before it " +
                        "holds anything: the comparison covers none of the structure the suite times",
                )
                assertEquals(rawNodes, declaredNodes, "the arms hold different numbers of tree nodes")
                assertEquals(
                    treeOf(raw).rowCount,
                    treeOf(declared).rowCount,
                    "the arms show different numbers of tree rows",
                )
            }
        }
}

/** The one tree [card] holds, which is the widget both arms of this test are compared on. */
private fun treeOf(card: Container): JTree = checkNotNull(descendantTree(card)) { "this arm built no JTree" }

/** Every node the tree's model holds, which is what it shows once each of them is open. */
private fun JTree.nodeCount(): Int {
    fun count(node: Any): Int = (0 until model.getChildCount(node)).sumOf { count(model.getChild(node, it)) } + 1
    return count(model.root)
}

private fun descendantTree(parent: Container): JTree? {
    for (child in parent.components) {
        val found = child as? JTree ?: (child as? Container)?.let { descendantTree(it) }
        if (found != null) return found
    }
    return null
}

private suspend fun ComposeSwingTest.assertArmsAgree(testName: String) =
    // TODO restore once swing-ui-test publishes assertComponentTreesEquivalent.
    withArms(testName) { _, _ -> }
