package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.harness.ToolNames
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private fun sessionOf(vararg items: TranscriptItem) =
    Session("s", "Session", LocalDate.of(2026, 10, 20), "Demo", "demo-fast").apply {
        turns +=
            Turn("Prompt", items.toList())
    }

private fun call(
    tool: String,
    input: String,
    output: String? = null,
) = TranscriptItem.ToolCall(tool + input.hashCode(), tool, input, output, ToolStatus.Done)

class ContextFactsTest {
    @Test
    fun thePlanIsTheLatestPlanCallsSteps() {
        val session =
            sessionOf(
                call(ToolNames.PLAN, "[ ] Read\n[ ] Edit"),
                call(ToolNames.PLAN, "[x] Read\nnot a step\n[ ] Edit"),
            )

        assertEquals(listOf(PlanItem("Read", isDone = true), PlanItem("Edit", isDone = false)), session.plan)
    }

    @Test
    fun aSessionWithoutAPlanHasNoSteps() {
        assertEquals(emptyList(), sessionOf(call(ToolNames.READ, "src/A.kt")).plan)
    }

    @Test
    fun touchedFilesCountTheLinesTheirDiffsAddAndRemoveInFirstTouchedOrder() {
        val diff = "--- a/q.sql\n+++ b/q.sql\n@@ -1,3 +1,3 @@\n--- the old view\n+-- the new view\n+select 2\n select 1"
        val session =
            sessionOf(
                call(ToolNames.READ, "q.sql"),
                call(ToolNames.READ, "README.md"),
                call(ToolNames.EDIT, "q.sql", diff),
                call("bash", "./gradlew test"),
            )

        assertEquals(listOf(TouchedFile("q.sql", 2, 1), TouchedFile("README.md", 0, 0)), session.touchedFiles)
    }

    @Test
    fun theDiffCountsAgreeWithTheDiffLinesACardShows() {
        val diff = "--- a/q.sql\n+++ b/q.sql\n@@ -1,2 +1,1 @@\n--- drop the old view\n select 1"
        val lines = diffLines(diff)

        assertEquals(
            listOf(DiffLine(DiffLineKind.Removed, "-- drop the old view", oldNumber = 1)),
            lines.filter {
                it.kind == DiffLineKind.Removed
            },
        )
        assertEquals(listOf(TouchedFile("q.sql", 0, 1)), sessionOf(call(ToolNames.EDIT, "q.sql", diff)).touchedFiles)
    }
}
