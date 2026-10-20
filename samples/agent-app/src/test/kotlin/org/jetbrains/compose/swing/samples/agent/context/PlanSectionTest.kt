package org.jetbrains.compose.swing.samples.agent.context

import com.formdev.flatlaf.FlatClientProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.harness.PiRpcAgent
import org.jetbrains.compose.swing.samples.agent.openSession
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.junit.jupiter.api.io.TempDir
import java.awt.font.TextAttribute
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JLabel
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PlanSectionTest {
    @TempDir
    lateinit var folder: Path

    @Test
    fun aPlanToolCallFromTheHarnessFillsThePlanSection() =
        runAgentTest(rootSize = paneSize) {
            val extensions = mutableListOf<Path>()
            val script = folder.resolve("fake.sh")
            Files.write(script, checkNotNull(javaClass.getResourceAsStream("/pi-rpc/fake.sh")).use { it.readBytes() })
            val agent =
                PiRpcAgent({ "sh" }, folder.resolve("data"), start = { line, directory ->
                    extensions += line.windowed(2).filter { it[0] == "-e" }.map { Path.of(it[1]) }
                    ProcessBuilder(listOf("sh", "$script", "${folder.resolve("fake.log")}", "plan") + line)
                        .directory(directory.toFile())
                        .start()
                })
            try {
                val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent))
                assertTrue(state.send("Make a plan", folder))
                withTimeout(10.seconds) { state.runner.runningTurn?.join() }
                showContextPane(ContextInput(plan = state.openSession.plan))
                onNodeWithText("Read the code").assertIsDisplayed()
                onNodeWithText("Run the tests").assertIsDisplayed()
                assertEquals(listOf(true, false), state.openSession.plan.map { it.isDone })
                val planExtension = extensions.single { it.fileName.toString() == "plan.ts" }
                assertEquals(
                    checkNotNull(javaClass.getResourceAsStream("/pi/plan.ts")).use { it.readBytes().decodeToString() },
                    planExtension.readText(),
                )
            } finally {
                agent.close()
            }
        }

    @Test
    fun planRowsReflectWhichStepsAreDone() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(plan = samplePlan))
            val colors = readAgentTheme().colors
            val strike = { step: String ->
                onNodeWithText(step).fetch<JLabel>().font.attributes[TextAttribute.STRIKETHROUGH]
            }

            assertEquals(TextAttribute.STRIKETHROUGH_ON, strike("Read the theme setup"))
            assertEquals(colors.mutedText, onNodeWithText("Read the theme setup").fetch<JLabel>().foreground)
            assertNull(strike("Persist the preference"))
            assertEquals(colors.text, onNodeWithText("Persist the preference").fetch<JLabel>().foreground)
        }

    @Test
    fun finishingAStepStrikesItThroughAndMovesTheCurrentPillToTheNextStep() =
        runAgentTest(rootSize = paneSize) {
            val input = ContextInput(plan = samplePlan)
            showContextPane(input)
            val colors = readAgentTheme().colors
            val step = { text: String -> onNodeWithText(text).fetch<JLabel>() }
            val styleClass = { text: String -> step(text).getClientProperty(FlatClientProperties.STYLE_CLASS) }
            assertEquals(StyleClass.CurrentStep.id, styleClass("Persist the preference"))
            assertNull(styleClass("Run the tests"))

            input.plan = samplePlan.map { if (it.text == "Persist the preference") it.copy(isDone = true) else it }
            awaitIdle()

            assertEquals(colors.mutedText, step("Persist the preference").foreground)
            val strike = step("Persist the preference").font.attributes[TextAttribute.STRIKETHROUGH]
            assertEquals(TextAttribute.STRIKETHROUGH_ON, strike)
            assertNull(styleClass("Persist the preference"))
            assertEquals(StyleClass.CurrentStep.id, styleClass("Run the tests"))
        }
}
