package org.jetbrains.compose.swing.samples.agent.state

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextTokensTest {
    @Test
    fun `the context is what the latest turn carried, not a sum over the turns`() {
        val session = Session("s", "Title", LocalDate.of(2026, 10, 20), "Pi", "p/m")
        assertEquals(0, session.contextTokens, "before any turn")

        session.turns += Turn("first", listOf(TranscriptItem.Usage(inputTokens = 1_000, outputTokens = 50)))
        session.turns += Turn("second", listOf(TranscriptItem.Usage(inputTokens = 1_200, outputTokens = 80)))
        session.turns += Turn("running", isRunning = true)

        assertEquals(1_200, session.contextTokens)
        assertEquals(2_330, session.totalTokens, "the cost still adds up")
    }
}
