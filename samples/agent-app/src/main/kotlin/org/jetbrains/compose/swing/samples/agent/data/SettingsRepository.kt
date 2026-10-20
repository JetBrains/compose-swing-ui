package org.jetbrains.compose.swing.samples.agent.data

/** The stored settings. */
internal class SettingsRepository(
    database: AgentDatabase,
) {
    private val dao = database.settingsDao()

    /** The stored settings; `null` until the user changes one. */
    suspend fun settings(): SettingsRecord? = dao.current()

    suspend fun save(settings: SettingsRecord) = dao.save(settings)
}
