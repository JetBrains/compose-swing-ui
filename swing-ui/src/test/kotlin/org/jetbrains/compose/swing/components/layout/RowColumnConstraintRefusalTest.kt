package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A row or column places a child by the arrangement and alignment it is declared with, and by `weight()`
 * / `align()` on the child's own modifier. There is no layout constraint for a child to carry there, so a
 * chain declaring one is refused with a message naming what to declare instead.
 */
class RowColumnConstraintRefusalTest {
    @Test
    fun aChildCarryingALayoutConstraintIsRefusedByARow() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Row {
                        Label(text = "placed", modifier = SwingModifier.layoutConstraint(CONSTRAINT))
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            CONSTRAINT in message,
            "the refusal should print the constraint the child was added with: $message",
        )
        assertTrue(
            "weight()" in message && "align()" in message,
            "the refusal should name the declarations that do place a child in a row: $message",
        )
    }

    @Test
    fun aChildCarryingALayoutConstraintIsRefusedByAColumn() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Column {
                        Label(text = "placed", modifier = SwingModifier.layoutConstraint(CONSTRAINT))
                    }
                }
            }

        assertTrue(
            "arrangement and alignment" in failure.message.orEmpty(),
            "the refusal should name what a column does place its children by: ${failure.message}",
        )
    }

    private companion object {
        /** A constraint no row or column understands, which is every constraint there is. */
        const val CONSTRAINT = "North"
    }
}
