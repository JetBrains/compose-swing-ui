package org.jetbrains.compose.swing.samples.agent.data

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.inMemoryDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {
    private val repository = SettingsRepository(inMemoryDatabase())

    @Test
    fun `nothing is stored before the first save`() =
        runTest {
            assertNull(repository.settings())
        }

    @Test
    fun `the last save wins`() =
        runTest {
            repository.save(
                SettingsRecord(
                    themeMode = "Dark",
                    language = "English",
                    isRightToLeft = false,
                    isReduceMotion = false,
                    piExecutable = "pi",
                ),
            )
            val light =
                SettingsRecord(
                    themeMode = "Light",
                    language = "German",
                    isRightToLeft = true,
                    isReduceMotion = true,
                    piExecutable = "/Applications/Pi Agent/pi",
                )

            repository.save(light)

            assertEquals(light, repository.settings())
        }
}
