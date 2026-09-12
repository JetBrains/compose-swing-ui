package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A row or a column registers each child under what the child declared to its scope - a weight, a
 * cross-axis alignment, a fill - gathered into the one constraint a parent registers a child under.
 *
 * Anything else is refused, the way Swing's own constrained layout managers throw for a constraint they
 * cannot read. Nothing here would place a child by a foreign constraint, so accepting one would leave
 * the caller a declaration that does nothing.
 */
class RowColumnConstraintRefusalTest {
    /** No test elsewhere combines a weight's own stretch with a cross-axis alignment declared beside it. */
    @Test
    fun aWeightedChildAlignedAcrossTheRowStretchesAlongItAndSitsAtTheAlignment() =
        runComposeSwingTest {
            setContent {
                Row(modifier = containerModifier(ALONG_EXTENT, ACROSS_EXTENT)) {
                    SizedChild(0, SwingModifier.weight(1f).align(Alignment.Bottom))
                }
            }

            assertEquals(
                listOf(Rectangle(0, ACROSS_EXTENT - CHILD_HEIGHT, ALONG_EXTENT, CHILD_HEIGHT)),
                childBounds(),
                "a weighted child must stretch along the row and sit at the alignment it declares across it",
            )
        }

    @Test
    fun aRowRefusesAChildComposedUnderALayoutConstraint() =
        runComposeSwingTest {
            // The applier registers the child as it attaches it, so the refusal comes from the pass rather
            // than from building the modifier, and reaches the caller the way other placement refusals do.
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    setContent {
                        Row {
                            Label(text = "placed", modifier = SwingModifier.layoutConstraint(CONSTRAINT))
                        }
                    }
                    awaitIdle()
                }

            val message = failure.message.orEmpty()
            assertTrue(
                "can carry no layout constraint" in message,
                "a row should refuse a child composed under a constraint it would never read: $message",
            )
            assertTrue(
                "weight()" in message,
                "and the refusal should name what places a child there instead: $message",
            )
        }

    @Test
    fun aChainDeclaringTwoKindsOfConstraintIsRefused() {
        // The scope's own builder and the modifier builder each declare a constraint, and a parent
        // registers a child under one.
        val declared = with(RowScopeInstance) { SwingModifier.weight(1f) }.layoutConstraint(CONSTRAINT)

        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent { Row { Label("placed", modifier = declared) } }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "incompatible layout families" in message,
            "the refusal should name each kind declared: $message",
        )
        assertTrue(
            "untyped layout constraint" in message,
            "the refusal should identify raw parent data: $message",
        )
        assertTrue(
            "linear parent data" in message,
            "the refusal should identify the scope parent data: $message",
        )
    }

    @Test
    fun aRowsLayoutManagerRefusesAConstraintOfAnotherKind() {
        val row =
            JPanel(rowPolicyLayout())

        val failure = assertFailsWith<IllegalArgumentException> { row.add(CONSTRAINT, JLabel("dropped")) }

        assertTrue(
            "can carry no layout constraint" in failure.message.orEmpty(),
            "the manager should refuse a constraint it reads nothing of: ${failure.message}",
        )
    }

    private companion object {
        /** A constraint no row or column understands, which is every constraint there is. */
        const val CONSTRAINT = "North"

        // Wider and taller than the fixture child asks for, so both the stretch and the alignment show.
        const val ALONG_EXTENT = 150
        const val ACROSS_EXTENT = 100
    }
}
