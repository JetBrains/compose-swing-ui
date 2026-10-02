package org.jetbrains.compose.swing.samples.todo

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class TaskAccessibilityTest {
    @Test
    fun taskControlsIdentifyTheirActionAndTaskAcrossRows() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            assertTaskNames(1, "Compose a reactive screen over Swing")
            assertTaskNames(3, "Recompose the summary and progress bar")
        }

    @Test
    fun renamingATaskUpdatesItsAccessibleNamesAndPreservesOtherRows() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }
            assertTaskNames(3, "Recompose the summary and progress bar")

            onNodeWithTag(taskTitleTag(3)).performTextReplacement("Write accessible tasks")

            assertTaskNames(3, "Write accessible tasks")
            assertTaskNames(1, "Compose a reactive screen over Swing")
        }
}

private fun ComposeSwingTest.assertTaskNames(
    id: Int,
    title: String,
) {
    onNodeWithTag(taskToggleTag(id)).assert(SwingMatcher.hasAccessibleName("Complete task: $title"))
    onNodeWithTag(taskTitleTag(id)).assert(SwingMatcher.hasAccessibleName("Task title"))
    onNodeWithTag(taskRemoveTag(id)).assert(SwingMatcher.hasAccessibleName("Remove task: $title"))
}
