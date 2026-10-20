package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.harness.ToolNames
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.AssistantText
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Permission
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Thinking
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.ToolCall
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem.Usage
import java.time.LocalDate

/**
 * Two finished conversations, so the app looks lived-in at start and shows every kind of transcript item. They are
 * sample data, in English like a real agent's output, not UI text.
 */
internal fun seedSessions(today: LocalDate = LocalDate.now()): List<Session> =
    listOf(darkModeSession(today), flakyTestSession(today.minusDays(1)))

private fun darkModeSession(day: LocalDate): Session {
    val turn = Turn("Add a dark mode toggle to the settings screen.", darkModeWork() + darkModeCheck())
    return Session("seed-dark-mode", "Add a dark mode toggle", day, "Demo", "demo-fast").apply { turns += turn }
}

/** Thinking, a plan, a read and an edit with its diff. */
private fun darkModeWork(): List<TranscriptItem> =
    listOf(
        Thinking(
            "The settings screen already reads colors from a Theme object. A toggle needs a persisted " +
                "preference and a live switch.",
            isDone = true,
        ),
        ToolCall(
            "p1",
            ToolNames.PLAN,
            "[x] Read the theme setup\n[x] Add the toggle to Settings\n[x] Run the tests",
            output = "Plan updated",
            status = ToolStatus.Done,
        ),
        ToolCall(
            "r1",
            ToolNames.READ,
            "src/ui/Theme.kt",
            output =
                """
                object Theme {
                    var dark by mutableStateOf(false)
                    val colors get() = if (dark) DarkColors else LightColors
                }
                """.trimIndent(),
            status = ToolStatus.Done,
        ),
        ToolCall(
            "e1",
            ToolNames.EDIT,
            "src/ui/Settings.kt",
            output =
                """
                --- a/src/ui/Settings.kt
                +++ b/src/ui/Settings.kt
                @@ -12,3 +12,8 @@
                 Column {
                -    Text("Appearance")
                +    Row {
                +        Text("Dark mode")
                +        Switch(checked = dark, onCheckedChange = onDarkChange)
                +    }
                 }
                """.trimIndent(),
            status = ToolStatus.Done,
        ),
    )

/** An allowed permission, the shell command it allowed, and a Markdown answer with code and a table. */
private fun darkModeCheck(): List<TranscriptItem> =
    listOf(
        Permission("q1", "bash", "./gradlew test", PermissionDecision.Allow),
        ToolCall(
            "b1",
            "bash",
            "./gradlew test",
            output = "SettingsTest > toggle switches the theme PASSED\n\nBUILD SUCCESSFUL in 14s",
            status = ToolStatus.Done,
        ),
        AssistantText(
            """
            ## Dark mode is in

            Added a **Dark mode** switch to *Settings*. It writes the preference and the theme follows at once:

            ```kotlin
            Switch(checked = dark, onCheckedChange = onDarkChange)
            ```

            | Check | Result |
            | --- | --- |
            | `./gradlew test` | 42 passed |
            | Contrast | AA in both themes |
            """.trimIndent(),
        ),
        Usage(inputTokens = 18_420, outputTokens = 2_310),
    )

private fun flakyTestSession(day: LocalDate): Session {
    val turn =
        Turn(
            "SyncTest fails now and then on CI. Can you fix it?",
            listOf(
                Thinking("A test that fails one run in ten usually races a timer.", isDone = true),
                ToolCall(
                    "p1",
                    ToolNames.PLAN,
                    "[x] Reproduce the failure\n[x] Drive the virtual clock\n[ ] Watch CI for a week",
                    output = "Plan updated",
                    status = ToolStatus.Done,
                ),
                ToolCall("r1", ToolNames.READ, "src/test/SyncTest.kt", output = "delay(100)", status = ToolStatus.Done),
                Permission("q1", "bash", "./gradlew test --tests SyncTest", PermissionDecision.AllowAlways),
                ToolCall(
                    "b1",
                    "bash",
                    "./gradlew test --tests SyncTest",
                    output = "SyncTest > syncs after reconnect FAILED\n    AssertionError at SyncTest.kt:44",
                    status = ToolStatus.Failed,
                ),
                ToolCall(
                    "e1",
                    ToolNames.EDIT,
                    "src/test/SyncTest.kt",
                    output =
                        """
                        --- a/src/test/SyncTest.kt
                        +++ b/src/test/SyncTest.kt
                        @@ -40,2 +40,2 @@
                        -    delay(100)
                        +    advanceUntilIdle()
                        """.trimIndent(),
                    status = ToolStatus.Done,
                ),
                AssistantText(
                    """
                    The test waited on a real `delay`, so it depended on the machine's speed. It now drives the
                    virtual clock:

                    ```kotlin
                    advanceUntilIdle()
                    ```

                    | Run | Before | After |
                    | --- | --- | --- |
                    | Local | 1 in 10 failed | 0 in 100 |
                    | CI | 1 in 6 failed | not run yet |
                    """.trimIndent(),
                ),
                Usage(inputTokens = 9_870, outputTokens = 940),
            ),
        )
    return Session("seed-flaky-test", "Fix the flaky sync test", day, "Demo", "demo-smart").apply {
        turns += turn
        alwaysAllowedTools += "bash"
    }
}
