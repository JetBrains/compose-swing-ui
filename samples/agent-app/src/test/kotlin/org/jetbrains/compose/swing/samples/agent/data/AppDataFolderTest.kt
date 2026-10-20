package org.jetbrains.compose.swing.samples.agent.data

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDataFolderTest {
    private fun file(
        os: String,
        environment: Map<String, String> = emptyMap(),
    ) = databaseFile(os, "/home/ann", environment)

    @Test
    fun `macOS stores under Application Support`() {
        assertEquals(Path.of("/home/ann/Library/Application Support/Compose Swing Agent/agent.db"), file("Mac OS X"))
    }

    @Test
    fun `Windows stores under APPDATA, or its default`() {
        assertEquals(Path.of("/roam/Compose Swing Agent/agent.db"), file("Windows 11", mapOf("APPDATA" to "/roam")))
        assertEquals(Path.of("/home/ann/AppData/Roaming/Compose Swing Agent/agent.db"), file("Windows 11"))
    }

    @Test
    fun `Linux stores under XDG_DATA_HOME, or its default`() {
        assertEquals(Path.of("/xdg/Compose Swing Agent/agent.db"), file("Linux", mapOf("XDG_DATA_HOME" to "/xdg")))
        assertEquals(
            Path.of("/home/ann/.local/share/Compose Swing Agent/agent.db"),
            file(
                "Linux",
                mapOf("XDG_DATA_HOME" to " "),
            ),
        )
    }
}
