package org.jetbrains.compose.swing.samples.agent.shell

import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.sessions.SessionsPane
import org.jetbrains.compose.swing.samples.agent.sessions.rememberToday
import org.jetbrains.compose.swing.samples.agent.state.Session
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test

class DayGroupingTest {
    @Test
    fun todayBecomesYesterdayAtMidnightWithoutASessionChange() =
        runAgentTest {
            val clock = MidnightClock()
            val session = Session("id", "Title", LocalDate.of(2026, 10, 20), "pi", "model")
            setContent {
                Column {
                    SessionsPane(
                        listOf(session),
                        session.id,
                        {},
                        { _, _ -> },
                        {},
                        {},
                        {},
                        today = rememberToday(clock),
                    )
                }
            }
            onNodeWithText(message(English.locale, "sessions.today")).assertExists()
            clock.now = Instant.parse("2026-10-21T00:00:00Z")
            waitUntil { onAllNodesWithText(message(English.locale, "sessions.yesterday")).fetchAll().isNotEmpty() }
            onNodeWithText(message(English.locale, "sessions.today")).assertDoesNotExist()
        }

    private class MidnightClock : Clock() {
        var now: Instant = Instant.parse("2026-10-20T23:59:59.999Z")

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)

        override fun instant(): Instant = now
    }
}
