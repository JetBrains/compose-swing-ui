package org.jetbrains.compose.swing.samples.agent.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** A stored session with its settled transcript. */
internal data class SessionRecord(
    val id: String,
    val title: String,
    val createdOn: LocalDate,
    val agent: String,
    val model: String,
    val folder: String,
    val turns: List<TurnRecord> = emptyList(),
    val alwaysAllowedTools: List<String> = emptyList(),
)

internal data class TurnRecord(
    val id: String,
    val prompt: String,
    val items: List<StoredItem>,
)

/**
 * The sessions, their turns and their settled items. Sessions list the newest first. Writes are suspend functions,
 * and a caller that needs them in order starts the next after the previous returns.
 */
internal class SessionRepository(
    database: AgentDatabase,
) {
    private val dao = database.sessionDao()
    private val transcript = database.transcriptDao()
    private val creating = Mutex()

    /** Every session, newest first, as stored now. */
    suspend fun sessions(): List<SessionRecord> = records(dao.sessions())

    /** Stores [session] as the newest one. Its turns and items are added with [addTurn] and [addItems]. */
    suspend fun create(session: SessionRecord) {
        creating.withLock {
            dao.insert(
                SessionEntity(
                    id = session.id,
                    ordinal = dao.newestOrdinal() + 1,
                    title = session.title,
                    createdOn = session.createdOn.toString(),
                    agent = session.agent,
                    model = session.model,
                    folder = session.folder,
                ),
            )
        }
    }

    suspend fun rename(
        id: String,
        title: String,
    ) = dao.rename(id, title)

    /** Records the agent and model the session's latest prompt ran on. */
    suspend fun setAgent(
        id: String,
        agent: String,
        model: String,
    ) = dao.setAgent(id, agent, model)

    /** Removes the session with its turns, items and allowed tools. */
    suspend fun delete(id: String) = dao.delete(id)

    /** Stores a turn as the session's turn number [position], counted from zero. */
    suspend fun addTurn(
        sessionId: String,
        turnId: String,
        position: Int,
        prompt: String,
    ) = transcript.insert(TurnEntity(turnId, sessionId, position, prompt))

    /** Stores [items] as the turn's items from index [firstPosition] on. */
    suspend fun addItems(
        turnId: String,
        firstPosition: Int,
        items: List<StoredItem>,
    ) = transcript.insert(
        items.mapIndexed {
            i,
            item,
            ->
            ItemEntity(turnId = turnId, position = firstPosition + i, payload = item.toPayload())
        },
    )

    suspend fun clearTurns(sessionId: String) = transcript.deleteTurns(sessionId)

    suspend fun allowTool(
        sessionId: String,
        tool: String,
    ) = transcript.insert(AllowedToolEntity(sessionId, tool))

    private suspend fun records(sessions: List<SessionEntity>): List<SessionRecord> {
        val items = transcript.items().groupBy { it.turnId }
        val turns = transcript.turns().groupBy { it.sessionId }
        val tools = transcript.allowedTools().groupBy { it.sessionId }
        return sessions.map { session ->
            SessionRecord(
                id = session.id,
                title = session.title,
                createdOn = LocalDate.parse(session.createdOn),
                agent = session.agent,
                model = session.model,
                folder = session.folder,
                turns =
                    turns[session.id].orEmpty().map { turn ->
                        TurnRecord(turn.id, turn.prompt, items[turn.id].orEmpty().map { storedItem(it.payload) })
                    },
                alwaysAllowedTools = tools[session.id].orEmpty().map { it.tool },
            )
        }
    }
}
