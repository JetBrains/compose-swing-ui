package org.jetbrains.compose.swing.samples.agent.data

import java.nio.file.Path

/** The file the app's [AgentDatabase] lives in. */
internal fun databaseFile(
    osName: String = System.getProperty("os.name"),
    userHome: String = System.getProperty("user.home"),
    environment: Map<String, String> = System.getenv(),
): Path = appDataFolder(osName, userHome, environment).resolve("agent.db")

/**
 * The operating system's per-user folder for application data, with the app's own folder in it: `Application
 * Support` on macOS, `%APPDATA%` on Windows and `$XDG_DATA_HOME` elsewhere, each with the platform's usual fallback.
 */
internal fun appDataFolder(
    osName: String,
    userHome: String,
    environment: Map<String, String>,
): Path {
    val home = Path.of(userHome)
    val base =
        when {
            osName.startsWith("Mac") -> {
                home.resolve("Library").resolve("Application Support")
            }

            osName.startsWith("Windows") -> {
                environment["APPDATA"]?.takeIf { it.isNotBlank() }?.let(Path::of)
                    ?: home.resolve("AppData").resolve("Roaming")
            }

            else -> {
                environment["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() }?.let(Path::of)
                    ?: home.resolve(".local").resolve("share")
            }
        }
    return base.resolve("Compose Swing Agent")
}
