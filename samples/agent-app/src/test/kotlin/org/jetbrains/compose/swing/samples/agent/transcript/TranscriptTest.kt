package org.jetbrains.compose.swing.samples.agent.transcript

import org.jetbrains.compose.swing.samples.agent.boundsOf
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.state.PermissionDecision
import org.jetbrains.compose.swing.samples.agent.state.ToolStatus
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.AssistantText
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Failure
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Permission
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Thinking
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.ToolCall
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Usage
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import java.awt.Component
import java.awt.Dimension
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TranscriptTest {
    @Test
    fun aOneLinePromptGetsABubbleOfOneLine() =
        runAgentTest(rootSize = tallRoot) {
            showTranscript(listOf(Turn("Add a dark mode toggle to the settings screen.", emptyList())))

            val text = onNodeWithText("Add a dark mode toggle to the settings screen.").fetch<Component>()
            // The bubble is the first ancestor wider than the text it holds, whatever components sit between.
            val bubble = generateSequence(text.parent) { it.parent }.first { it.width > text.width }
            assertTrue(bubble.height <= text.height + 40, "a one-line prompt got a ${bubble.height} px bubble")
        }

    @Test
    fun aTurnShowsItsPromptThenItsItemsInOrder() =
        runAgentTest(rootSize = tallRoot) {
            val turn =
                Turn(
                    "Fix the build",
                    listOf(
                        Thinking("Look at the log first.", isDone = true),
                        ToolCall(
                            "t1",
                            "bash",
                            "./gradlew build",
                            output = "BUILD SUCCESSFUL",
                            status = ToolStatus.Done,
                        ),
                        AssistantText("The build passes now."),
                        Usage(inputTokens = 1_200, outputTokens = 300),
                    ),
                )
            showTranscript(listOf(turn))

            val tops =
                listOf(
                    "Fix the build",
                    "Look at the log first.",
                    "./gradlew build",
                    "The build passes now.",
                    message(English.locale, "transcript.usage", 1_200, 300),
                ).map { boundsOf(it).y }
            assertEquals(tops.sorted(), tops, "the turn's parts are not stacked in the order they arrived: $tops")
        }

    @Test
    fun anItemArrivingDuringARunningTurnRisesIntoPlace() =
        runAgentTest(rootSize = tallRoot) {
            val turn = Turn("Explain the fix", listOf(ToolCall("r1", "read", "src/Sync.kt")), isRunning = true)
            showTranscript(listOf(turn))
            mainClock.autoAdvance = false

            turn.items += AssistantText("The test raced a timer.")
            awaitIdle()
            val tops =
                List(6) {
                    mainClock.advanceTimeByFrame()
                    awaitIdle()
                    boundsOf("The test raced a timer.").y
                }
            mainClock.advanceTimeBy(2.seconds)
            awaitIdle()
            val settled = boundsOf("The test raced a timer.").y

            assertTrue(tops.any { it > settled }, "the new item stood in place from its first frame: $tops, $settled")
            onNodeWithText("The test raced a timer.", substring = true).assertIsDisplayed()
        }

    @Test
    fun theHistoryOfASessionIsShownInPlaceWithoutAnimating() =
        runAgentTest(rootSize = tallRoot) {
            mainClock.autoAdvance = false
            showTranscript(listOf(Turn("Seeded prompt", listOf(AssistantText("Seeded answer.")))))
            mainClock.advanceTimeByFrame()
            awaitIdle()
            val first = boundsOf("Seeded answer.").y

            mainClock.advanceTimeBy(2.seconds)
            awaitIdle()
            assertEquals(first, boundsOf("Seeded answer.").y, "a seeded item moved after it was first shown")
        }

    @Test
    fun scrollingUpOffersAJumpBackToTheLatest() =
        runAgentTest(rootSize = Dimension(800, 600)) {
            val turn = Turn("Write a long answer", List(40) { AssistantText("Paragraph $it of the answer.") })
            showTranscript(listOf(turn))
            onNodeWithTag("jump-to-latest").assertDoesNotExist()
            assertTrue(isAtBottom(), "the transcript did not open at its newest content")

            onNodeWithTag("transcript").performMouseWheel(rotation = -30)
            awaitIdle()
            onNodeWithTag("jump-to-latest").assertIsDisplayed()

            onNodeWithTag("jump-to-latest").performClick()
            awaitIdle()
            assertTrue(isAtBottom(), "the jump did not bring the newest content into view")
            onNodeWithTag("jump-to-latest").assertDoesNotExist()

            turn.items += AssistantText("A late addition.")
            awaitIdle()
            assertTrue(isAtBottom(), "content arriving while the reader followed it did not scroll into view")
        }

    @Test
    fun scrollingUpWhileContentStreamsHoldsThePosition() =
        runAgentTest(rootSize = Dimension(800, 600)) {
            val turn = Turn("Write a long answer", List(40) { AssistantText("Paragraph $it of the answer.") })
            showTranscript(listOf(turn))
            mainClock.autoAdvance = false
            turn.items += AssistantText("Streamed 0")
            repeat(3) {
                awaitIdle()
                mainClock.advanceTimeByFrame()
            }

            onNodeWithTag("transcript").performMouseWheel(rotation = -30)
            val held = scrollValue()
            repeat(6) {
                turn.items += AssistantText("Streamed ${it + 1}")
                awaitIdle()
                mainClock.advanceTimeByFrame()
                awaitIdle()
            }
            mainClock.advanceTimeBy(2.seconds)
            awaitIdle()

            assertEquals(held, scrollValue(), "content arriving after the reader scrolled up moved the position")
            onNodeWithTag("jump-to-latest").assertIsDisplayed()
        }

    @Test
    fun aFailedTurnShowsWhatWentWrong() =
        runAgentTest(rootSize = tallRoot) {
            val turn =
                Turn("Fix the build", listOf(Failure("bad config", exitCode = 3), Failure("claude is not on the PATH")))
            showTranscript(listOf(turn))

            onNodeWithText(message(English.locale, "transcript.error.exitCode", 3)).assertIsDisplayed()
            onAllNodes(SwingMatcher.hasText("bad config")).assertCountEquals(1)
            onNodeWithText(message(English.locale, "transcript.error")).assertIsDisplayed()
            onAllNodes(SwingMatcher.hasText("claude is not on the PATH")).assertCountEquals(1)
        }

    @Test
    fun theTypingIndicatorWaitsWhileTheUserDecides() =
        runAgentTest(rootSize = tallRoot) {
            val permission = Permission("q1", "bash", "./gradlew test")
            val turn = Turn("Run the tests", listOf(permission), isRunning = true)
            showTranscript(listOf(turn))
            mainClock.autoAdvance = false
            awaitIdle()
            mainClock.advanceTimeBy(1.seconds)
            awaitIdle()
            onNodeWithText("Run the tests").assertIsDisplayed()
            typingIndicators().assertCountEquals(0)

            permission.decide(PermissionDecision.Allow)
            awaitIdle()
            mainClock.advanceTimeBy(1.seconds)
            awaitIdle()
            typingIndicators().assertCountEquals(1)
        }

    private fun ComposeSwingTest.typingIndicators() =
        onAllNodes(SwingMatcher.hasAccessibleName(message(English.locale, "transcript.typing")))

    private fun ComposeSwingTest.scrollValue(): Int =
        onNodeWithTag("transcript").fetch<JScrollPane>().verticalScrollBar.value

    private fun ComposeSwingTest.isAtBottom(): Boolean =
        onNodeWithTag("transcript").fetch<JScrollPane>().verticalScrollBar.run { value + visibleAmount == maximum }

    private companion object {
        val tallRoot = Dimension(900, 1000)
    }
}
