package org.jetbrains.compose.swing.samples.agent.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentDatabaseTest {
    @TempDir
    lateinit var folder: Path

    @Test
    fun `a developer database from a higher version is recreated with the final schema`() =
        runBlocking {
            val file = folder.resolve("agent.db")
            val driver = BundledSQLiteDriver()
            driver.open("$file").use {
                it.execSQL("CREATE TABLE obsolete (value TEXT)")
                it.execSQL("PRAGMA user_version = 2")
            }
            val database = AgentDatabase.open(file)
            try {
                assertNull(SettingsRepository(database).settings())
                val settings =
                    SettingsRecord(
                        themeMode = "Dark",
                        language = "English",
                        isRightToLeft = false,
                        isReduceMotion = false,
                        piExecutable = "/path/to/pi",
                    )
                SettingsRepository(database).save(settings)
                assertEquals(settings, SettingsRepository(database).settings())
            } finally {
                database.close()
            }
            driver.open("$file").use { connection ->
                connection.prepare("PRAGMA user_version").use {
                    it.step()
                    assertEquals(1, it.getInt(0))
                }
                connection.prepare("SELECT count(*) FROM sqlite_master WHERE name = 'obsolete'").use {
                    it.step()
                    assertEquals(0, it.getInt(0))
                }
                connection.prepare("SELECT piExecutable FROM settings").use {
                    it.step()
                    assertEquals("/path/to/pi", it.getText(0))
                }
            }
        }
}
