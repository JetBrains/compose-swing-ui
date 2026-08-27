package org.jetbrains.compose.swing.samples.todo

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageMatches
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [ReactiveTaskList], driven entirely through the public test harness. Each
 * test exercises a user gesture where it really lands (a click, a text replacement) and reads the live
 * Swing tree the recomposition produced - never a private field or composition slot.
 *
 * Components are located the way a user would identify them - by visible text, by type, or by
 * accessible name - except a specific keyed row, which is addressed by its per-row test tag because
 * rows otherwise look alike.
 *
 * The starting fixture has four tasks, two of them already done, so "2 of 4 done" is the initial state.
 */
class ReactiveTaskListTest {
    @Test
    fun theSummaryAndProgressBarReflectTheInitialState() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            onNodeWithText("2 of 4 done").assertExists()
            val progress = onNodeOfType<JProgressBar>().fetch<JProgressBar>()
            assertEquals(2, progress.value, "the progress bar mirrors the done count")
            assertEquals(4, progress.maximum, "the progress bar maximum mirrors the total count")
        }

    @Test
    fun completingATaskUpdatesTheDerivedSummaryAndProgress() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            // Toggle the third task (id 3), which starts incomplete: the derived summary and the bar move
            // together, with no manual recomputation in the sample.
            onNodeWithTag(taskToggleTag(3)).performClick()

            onNodeWithText("3 of 4 done").assertExists()
            onNodeWithText("2 of 4 done").assertDoesNotExist()
            assertEquals(3, onNodeOfType<JProgressBar>().fetch<JProgressBar>().value)
        }

    @Test
    fun theAddButtonIsDisabledUntilTheDraftIsNonBlank() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            onAddButton().assertIsNotEnabled()

            onAddField().performTextReplacement("Write docs")
            onAddButton().assertIsEnabled()
        }

    @Test
    fun addingATaskInsertsAKeyedRowAndGrowsTheTotal() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            // The fixture seeds ids 1..4, so a freshly added task takes id 5: a new keyed row appears and
            // the derived total grows from 4 to 5.
            onNodeWithTag(taskRowTag(5)).assertDoesNotExist()

            onAddField().performTextReplacement("Ship the sample")
            onAddButton().performClick()

            onNodeWithTag(taskRowTag(5)).assertExists()
            assertEquals(
                "Ship the sample",
                onNodeWithTag(taskTitleTag(5)).fetch<JTextField>().text,
                "the new row carries the typed title",
            )
            onNodeWithText("2 of 5 done").assertExists()
        }

    @Test
    fun pressingEnterInTheDraftFieldCommitsTheTaskAndClearsTheDraft() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            onAddField().performTextReplacement("Ship the sample")
            onAddField().fetch<JTextField>().postActionEvent()
            awaitIdle()

            onNodeWithTag(taskRowTag(5)).assertExists()
            onNodeWithText("2 of 5 done").assertExists()
            assertEquals(
                "",
                onAddField().fetch<JTextField>().text,
                "the draft field clears once Enter commits the task",
            )
        }

    @Test
    fun removingATaskDropsItsRowFromTheTreeAndShrinksTheTotal() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            onNodeWithTag(taskRowTag(2)).assertExists()

            // Removing the second task (a done one) drops its row entirely and recomputes the summary:
            // total 4 -> 3 and done 2 -> 1.
            onNodeWithTag(taskRemoveTag(2)).performClick()

            onNodeWithTag(taskRowTag(2)).assertDoesNotExist()
            onNodeWithText("1 of 3 done").assertExists()
        }

    @Test
    fun aTaskRowRendersToAStableBitmapScreenshotTest() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            // Screenshot testing the sample: capture a tagged subtree as a real bitmap, then assert
            // against it with `assertImageMatches`, which compares two images by structural similarity
            // rather than peeking at pixels by hand.
            val row = onNodeWithTag(taskRowTag(3)).captureToImage()

            // The render is stable: capturing the same, unchanged row again matches the first capture.
            onNodeWithTag(taskRowTag(3)).assertImageMatches(expected = row)

            // A genuinely different element (a whole different-sized subtree) does NOT match it - the
            // comparison rejects it on the size mismatch alone, so the assertion throws.
            assertFailsWith<AssertionError> {
                onNodeOfType<JProgressBar>().assertImageMatches(expected = row)
            }
        }

    @Test
    fun aTaskRowKeepsAFixedHeightRegardlessOfTaskCount() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            // The column places the height it has left over instead of pushing it into a child, so a
            // row is laid out at exactly the height it asks for however much room the list has.
            val rowWithFourTasks = onNodeWithTag(taskRowTag(3)).fetch<JPanel>()
            val heightWithFourTasks = rowWithFourTasks.height
            assertTrue(heightWithFourTasks > 0, "the row must have a real, laid-out height")
            assertEquals(
                rowWithFourTasks.preferredSize.height,
                heightWithFourTasks,
                "a row is laid out at the height it prefers",
            )

            // Removing a task leaves the layout surplus vertical space, yet the surviving row's
            // laid-out height stays exactly what it was with the fuller list.
            onNodeWithTag(taskRemoveTag(2)).performClick()

            assertEquals(
                heightWithFourTasks,
                onNodeWithTag(taskRowTag(3)).fetch<JPanel>().height,
                "a row's laid-out height does not depend on the task count",
            )
        }

    @Test
    fun aTaskRowSpansTheWidthOfTheList() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            // The row declares a cross-axis fill, so the list column hands it the whole width it has to
            // give rather than the narrower width the row's own controls ask for.
            val row = onNodeWithTag(taskRowTag(3)).fetch<JPanel>()
            val list = row.parent
            val listInsets = list.insets
            assertEquals(
                list.width - listInsets.left - listInsets.right,
                row.width,
                "a row takes the whole width the list column has to give",
            )
        }

    @Test
    fun theTaskListStaysAtItsPreferredHeightOnceTasksOverflowIt() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            val pane = onNodeOfType<JScrollPane>().fetch<JScrollPane>()
            val heightWithFourTasks = pane.height
            assertEquals(320, heightWithFourTasks, "the list is laid out at its declared preferred height")

            // Add enough tasks to overflow the fixed-height viewport.
            repeat(EXTRA_TASKS_TO_OVERFLOW_THE_LIST) { index ->
                onAddField().performTextReplacement("Task $index")
                onAddButton().performClick()
            }

            assertEquals(
                heightWithFourTasks,
                pane.height,
                "the scroll pane's own height must not grow as tasks overflow it",
            )
            assertTrue(
                pane.viewport.view.preferredSize.height > pane.height,
                "the list content must grow past the viewport once tasks overflow it",
            )
        }

    @Test
    fun renamingOneRowLeavesTheOtherRowsUntouched() =
        runComposeSwingTest {
            setContent { ReactiveTaskList() }

            val otherTitleBefore = onNodeWithTag(taskTitleTag(1)).fetch<JTextField>().text

            onNodeWithTag(taskTitleTag(3)).performTextReplacement("Renamed task")

            assertEquals(
                "Renamed task",
                onNodeWithTag(taskTitleTag(3)).fetch<JTextField>().text,
                "the edited row holds the new title",
            )
            assertEquals(
                otherTitleBefore,
                onNodeWithTag(taskTitleTag(1)).fetch<JTextField>().text,
                "editing one keyed row must not disturb another row's content",
            )
        }
}

/** The add-a-task text field, located by its accessible name rather than a test tag. */
private fun ComposeSwingTest.onAddField() = onNode(SwingMatcher.hasAccessibleName("New task"))

/** The add button, located by its visible label and button type rather than a test tag. */
private fun ComposeSwingTest.onAddButton() = onNode(SwingMatcher.hasText("Add") and SwingMatcher.isOfType<JButton>())

/** More tasks than the list's 320px preferred height can show without scrolling. */
private const val EXTRA_TASKS_TO_OVERFLOW_THE_LIST = 20
