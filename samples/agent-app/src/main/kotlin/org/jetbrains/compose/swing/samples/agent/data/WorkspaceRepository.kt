package org.jetbrains.compose.swing.samples.agent.data

/**
 * The stored window state. The pane layout and the session selection each save only their own columns, so neither
 * overwrites the other's, whichever repository instance saves them.
 */
internal class WorkspaceRepository(
    database: AgentDatabase,
) {
    private val dao = database.workspaceDao()

    /** The stored workspace; `null` until something is saved. */
    suspend fun workspace(): WorkspaceRecord? = dao.current()

    /** Saves the side panes and keeps the selection. */
    suspend fun savePanes(
        isSessionsVisible: Boolean,
        sessionsWidth: Int,
        isContextVisible: Boolean,
        contextWidth: Int,
    ) {
        dao.insertIfMissing(EMPTY)
        dao.updatePanes(isSessionsVisible, sessionsWidth, isContextVisible, contextWidth)
    }

    /** Saves the selection and keeps the side panes. */
    suspend fun saveSelection(
        activeSessionId: String?,
        agent: String,
        model: String,
    ) {
        dao.insertIfMissing(EMPTY)
        dao.updateSelection(activeSessionId, agent, model)
    }

    private companion object {
        val EMPTY =
            WorkspaceRecord(
                isSessionsVisible = null,
                sessionsWidth = null,
                isContextVisible = null,
                contextWidth = null,
                activeSessionId = null,
                agent = null,
                model = null,
            )
    }
}
