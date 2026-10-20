package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

/**
 * An offline agent that plays a scripted turn chosen by English keywords in the prompt: a failing build for
 * "fail", an explanation for questions, and a code change otherwise. Like a real agent's output, what it writes
 * is English in every app language.
 *
 * The code change asks for permission before it runs a shell command, and the turn waits for the answer.
 */
internal class DemoAgent : Agent {
    override val displayName: @NonNls String = "Demo"

    override suspend fun listModels(): List<AgentModel> = PACES.keys.map(::AgentModel)

    override val isAvailable: Boolean = true

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> {
        val pace = requireNotNull(PACES[model]) { "Unknown demo model: $model" }
        return flow { Script(this, pace).play(prompt) }
    }
}

/** How long the demo waits before each streamed word and before each tool step. */
private class Pace(
    val wordMs: Long,
    val stepMs: Long,
)

/** The demo's models, by id: they differ only in how fast they play. */
private val PACES =
    mapOf(
        "demo-fast" to Pace(wordMs = 12, stepMs = 150),
        "demo-smart" to Pace(wordMs = 30, stepMs = 400),
    )

private enum class Scenario {
    CodeChange,
    Explanation,
    Failure,
    ;

    companion object {
        fun of(prompt: String): Scenario {
            val words = prompt.lowercase().split(Regex("[^\\p{L}]+"))
            val explanationWords = setOf("explain", "what", "why", "how")
            val requestsExplanation = prompt.trimEnd().endsWith('?') || words.any { it in explanationWords }
            return when {
                "fail" in words -> Failure
                requestsExplanation -> Explanation
                else -> CodeChange
            }
        }
    }
}

/** Plays one turn's events into [out] at [pace], counting the tokens they would cost. */
private class Script(
    private val out: FlowCollector<AgentEvent>,
    private val pace: Pace,
) {
    private var inputTokens = 0
    private var outputTokens = 0

    suspend fun play(prompt: String) {
        inputTokens += prompt.tokenCount()
        when (Scenario.of(prompt)) {
            Scenario.CodeChange -> codeChange()
            Scenario.Explanation -> explanation()
            Scenario.Failure -> failure()
        }
        out.emit(AgentEvent.Usage(inputTokens, outputTokens))
    }

    suspend fun think(text: String) = stream(text) { AgentEvent.Thinking(it) }

    suspend fun say(text: String) = stream(text) { AgentEvent.Text(it) }

    suspend fun call(
        id: String,
        tool: String,
        input: String,
    ) {
        delay(pace.stepMs)
        outputTokens += input.tokenCount()
        out.emit(AgentEvent.ToolCall(id, tool, input))
    }

    suspend fun result(
        id: String,
        output: String,
        isError: Boolean = false,
    ) {
        delay(pace.stepMs * 2)
        inputTokens += output.tokenCount()
        out.emit(AgentEvent.ToolResult(id, output, isError))
    }

    suspend fun ask(
        id: String,
        tool: String,
        detail: String,
    ): Boolean {
        delay(pace.stepMs)
        val request = AgentEvent.PermissionRequest(id, tool, detail)
        out.emit(request)
        return request.decision.await()
    }

    private suspend fun stream(
        text: String,
        event: (String) -> AgentEvent,
    ) {
        for (chunk in Regex("\\S+\\s*").findAll(text)) {
            delay(pace.wordMs)
            outputTokens += 1
            out.emit(event(chunk.value))
        }
    }
}

/** Counts one token per word, which is close enough for a meter. */
private fun String.tokenCount() = trim().split(Regex("\\s+")).size

private suspend fun Script.codeChange() {
    think(
        """
        |The request touches how colors are applied, so I should read the theme code first.
        |Then I will add a second palette and a way to switch, and confirm it with the tests.
        """.trimMargin(),
    )
    plan(
        "plan-1",
        """
        |[ ] Read the current theme
        |[ ] Add a dark palette
        |[ ] Add a toggle that applies it
        |[ ] Run the tests
        """.trimMargin(),
    )
    readTheme()
    editTheme()
    plan(
        "plan-2",
        """
        |[x] Read the current theme
        |[x] Add a dark palette
        |[x] Add a toggle that applies it
        |[ ] Run the tests
        """.trimMargin(),
    )
    val approved = ask("perm-1", "bash", "./gradlew test")
    runTests(approved)
    say(codeChangeSummary(approved))
}

/** States the plan [steps]; like any tool call, the plan gets a result. */
private suspend fun Script.plan(
    id: String,
    steps: String,
) {
    call(id, ToolNames.PLAN, steps)
    result(id, "Plan updated")
}

private suspend fun Script.readTheme() {
    call("read-1", ToolNames.READ, "src/main/kotlin/Theme.kt")
    result(
        "read-1",
        """
        |package notes.ui
        |
        |import java.awt.Color
        |import javax.swing.UIManager
        |
        |/** The colors the editor paints with. */
        |data class Palette(val background: Color, val foreground: Color, val accent: Color)
        |
        |object Theme {
        |    val light = Palette(Color(0xFAFAFA), Color(0x1F2328), Color(0x2F6FEB))
        |
        |    var current: Palette = light
        |        private set
        |
        |    fun apply(palette: Palette) {
        |        current = palette
        |        UIManager.put("Panel.background", palette.background)
        |        UIManager.put("Label.foreground", palette.foreground)
        |    }
        |}
        """.trimMargin(),
    )
    think("Only one palette exists and apply() already pushes colors into UIManager, so a second palette is enough.")
}

private suspend fun Script.editTheme() {
    call("edit-1", ToolNames.EDIT, "src/main/kotlin/Theme.kt")
    result(
        "edit-1",
        """
        |--- a/src/main/kotlin/Theme.kt
        |+++ b/src/main/kotlin/Theme.kt
        |@@ -8,13 +8,17 @@
        |
        | object Theme {
        |     val light = Palette(Color(0xFAFAFA), Color(0x1F2328), Color(0x2F6FEB))
        |+    val dark = Palette(Color(0x1E1F22), Color(0xDFE1E5), Color(0x548AF7))
        |
        |     var current: Palette = light
        |         private set
        |
        |+    fun toggle() = apply(if (current == light) dark else light)
        |+
        |     fun apply(palette: Palette) {
        |         current = palette
        |         UIManager.put("Panel.background", palette.background)
        |         UIManager.put("Label.foreground", palette.foreground)
        |+        UIManager.put("Component.accentColor", palette.accent)
        |     }
        | }
        """.trimMargin(),
    )
}

private suspend fun Script.runTests(approved: Boolean) {
    call("bash-1", "bash", "./gradlew test")
    if (approved) {
        result(
            "bash-1",
            """
            |> Task :compileKotlin
            |> Task :test
            |
            |ThemeTest > toggle swaps the palette PASSED
            |ThemeTest > apply updates the Swing defaults PASSED
            |ThemeTest > dark colors stay readable PASSED
            |
            |BUILD SUCCESSFUL in 6s
            """.trimMargin(),
        )
    } else {
        result("bash-1", "Not run: permission to execute ./gradlew test was denied.", isError = true)
    }
}

private fun codeChangeSummary(approved: Boolean): String {
    val verdict = if (approved) "42 passed" else "not run, permission denied"
    return """
        |## Dark mode is in
        |
        |I added a **dark palette** and a toggle that applies it at runtime.
        |
        |- `Theme.dark` holds the new colors
        |- `Theme.toggle()` switches between the two palettes
        |- `apply` now also sets the accent color through `UIManager`
        |
        || Check | Result |
        || --- | --- |
        || `./gradlew test` | $verdict |
        || Accent color | follows the palette |
        |
        |Wire it to a menu item like this:
        |
        |```kotlin
        |JMenuItem("Dark mode").addActionListener { Theme.toggle() }
        |```
        |
        |Updating the look and feel afterwards repaints open windows.
        """.trimMargin()
}

private suspend fun Script.explanation() {
    think(
        """
        |This is a question, so no tools are needed.
        |A short answer with one example should be enough.
        """.trimMargin(),
    )
    say(
        """
        |## How the theme is applied
        |
        |The editor never hard-codes a color. It reads everything from `UIManager`, so a **look and feel**
        |change reaches every open window.
        |
        |1. `Theme.apply` writes the palette into `UIManager`
        |2. `SwingUtilities.updateComponentTreeUI` asks each component to re-read its defaults
        |3. Painting picks up the new colors on the next frame
        |
        |```kotlin
        |val background = UIManager.getColor("Panel.background")
        |```
        |
        |Colors computed once and stored in a field are the usual cause of a window that stays on the old theme.
        """.trimMargin(),
    )
}

private suspend fun Script.failure() {
    think("The build is red, so I should reproduce it before touching anything.")
    call("bash-1", "bash", "./gradlew build")
    result(
        "bash-1",
        """
        |> Task :compileKotlin FAILED
        |e: src/main/kotlin/Theme.kt:17:55 Unresolved reference: accentColour
        |
        |FAILURE: Build failed with an exception.
        |
        |* What went wrong:
        |Execution failed for task ':compileKotlin'.
        |> Compilation error. See log for more details
        |
        |BUILD FAILED in 3s
        """.trimMargin(),
        isError = true,
    )
    think("One unresolved reference. The property is called accent, so the extra spelling is a typo.")
    say(
        """
        |## The build fails
        |
        |`Theme.kt` line 17 refers to `accentColour`, which does not exist. `Palette` calls it `accent`.
        |
        |Rename the reference and run `./gradlew build` again. I stopped here **without changing any file**.
        """.trimMargin(),
    )
}
