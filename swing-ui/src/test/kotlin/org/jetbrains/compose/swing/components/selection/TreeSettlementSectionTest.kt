package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tree settles two declarations - the nodes it shows selected and the nodes it shows open - through one
 * write of both its mirrors, and that write is reported as a settlement like every other one. Without it
 * the busiest screen a tree stands on would be work no trace can account for.
 */
class TreeSettlementSectionTest : TracedTest() {
    /** A value tree: each entry yields its [children], and its [name] is what the row renders. */
    private data class Branch(
        val name: String,
        val children: List<Branch> = emptyList(),
    )

    private val sample =
        Branch(
            "root",
            listOf(
                Branch("fruit", listOf(Branch("apple"), Branch("pear"))),
                Branch("veg", listOf(Branch("carrot"))),
            ),
        )

    @Test
    fun applyingADeclaredSelectionSettlesBothMirrorsThroughOneNestedWrite() = runComposeSwingTest {
        var selected by mutableStateOf(setOf(listOf(0)))
        setContent {
            Tree(
                root = sample,
                children = { it.children },
                label = { it.name },
                selectedPaths = selected,
                onSelectionChange = { selected = it },
            )
        }
        awaitIdle()
        tracer.clear()

        selected = setOf(listOf(1))
        awaitIdle()

        val settlements = tracer.sections.filter { it.name == "settle" }
        assertEquals(
            2,
            settlements.size,
            "the tree settles both of its mirrors through one write, so a selection change should report " +
                "one settlement per mirror and no more: ${tracer.sections}",
        )
        assertEquals(
            listOf(listOf("apply"), listOf("apply", "settle")),
            settlements.map { it.enclosing },
            "the two mirrors are settled through one nested write, so the change pass should report one " +
                "settlement inside the other rather than two beside each other: ${tracer.sections}",
        )
    }
}
