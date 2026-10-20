package org.jetbrains.compose.swing.samples.agent.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
internal interface SessionDao {
    @Query("SELECT * FROM session ORDER BY ordinal DESC")
    suspend fun sessions(): List<SessionEntity>

    @Query("SELECT COALESCE(MAX(ordinal), 0) FROM session")
    suspend fun newestOrdinal(): Long

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("UPDATE session SET title = :title WHERE id = :id")
    suspend fun rename(
        id: String,
        title: String,
    )

    @Query("UPDATE session SET agent = :agent, model = :model WHERE id = :id")
    suspend fun setAgent(
        id: String,
        agent: String,
        model: String,
    )

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
internal interface TranscriptDao {
    @Query("SELECT * FROM turn ORDER BY sessionId, position")
    suspend fun turns(): List<TurnEntity>

    @Query("SELECT * FROM item ORDER BY turnId, position")
    suspend fun items(): List<ItemEntity>

    @Query("SELECT * FROM allowed_tool ORDER BY sessionId, tool")
    suspend fun allowedTools(): List<AllowedToolEntity>

    @Insert
    suspend fun insert(turn: TurnEntity)

    @Insert
    suspend fun insert(items: List<ItemEntity>)

    @Query("DELETE FROM turn WHERE sessionId = :sessionId")
    suspend fun deleteTurns(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(allowedTool: AllowedToolEntity)
}

@Dao
internal interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = $SINGLE_ROW_ID")
    suspend fun current(): SettingsRecord?

    @Upsert
    suspend fun save(settings: SettingsRecord)
}

@Dao
internal interface WorkspaceDao {
    @Query("SELECT * FROM workspace WHERE id = $SINGLE_ROW_ID")
    suspend fun current(): WorkspaceRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(workspace: WorkspaceRecord)

    @Query(
        "UPDATE workspace SET isSessionsVisible = :isSessionsVisible, sessionsWidth = :sessionsWidth, " +
            "isContextVisible = :isContextVisible, contextWidth = :contextWidth WHERE id = $SINGLE_ROW_ID",
    )
    suspend fun updatePanes(
        isSessionsVisible: Boolean,
        sessionsWidth: Int,
        isContextVisible: Boolean,
        contextWidth: Int,
    )

    @Query(
        "UPDATE workspace SET activeSessionId = :activeSessionId, agent = :agent, model = :model " +
            "WHERE id = $SINGLE_ROW_ID",
    )
    suspend fun updateSelection(
        activeSessionId: String?,
        agent: String,
        model: String,
    )
}
