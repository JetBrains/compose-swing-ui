package org.jetbrains.compose.swing.samples.agent.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** The id of the one row of [SettingsRecord] and [WorkspaceRecord]. */
internal const val SINGLE_ROW_ID = 0

/** A conversation. [ordinal] orders the list: a larger one is newer and shows first. */
@Entity(tableName = "session")
internal data class SessionEntity(
    @PrimaryKey val id: String,
    val ordinal: Long,
    val title: String,
    val createdOn: String,
    val agent: String,
    val model: String,
    @ColumnInfo(defaultValue = "''") val folder: String,
)

/** One prompt of a session; [position] is its index among the session's turns. */
@Entity(
    tableName = "turn",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
internal data class TurnEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val prompt: String,
)

/** One settled transcript item: a [StoredItem] as JSON, at [position] among the turn's items. */
@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(
            entity = TurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("turnId")],
)
internal data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: String,
    val position: Int,
    val payload: String,
)

/** A tool the user allowed with "Always" in a session. */
@Entity(
    tableName = "allowed_tool",
    primaryKeys = ["sessionId", "tool"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class AllowedToolEntity(
    val sessionId: String,
    val tool: String,
)

/** The user's appearance, language and agent choices, as the names and text of the values they select. */
@Entity(tableName = "settings")
internal data class SettingsRecord(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val themeMode: String,
    val language: String,
    val isRightToLeft: Boolean,
    val isReduceMotion: Boolean,
    @ColumnInfo(defaultValue = "'pi'") val piExecutable: String,
)

/**
 * What the window shows: side panes, the active session, and the picked agent and model. A field is `null` until
 * the app first saves it, because the pane layout and the selection save separately.
 */
@Entity(
    tableName = "workspace",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["activeSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("activeSessionId")],
)
internal data class WorkspaceRecord(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val isSessionsVisible: Boolean?,
    val sessionsWidth: Int?,
    val isContextVisible: Boolean?,
    val contextWidth: Int?,
    val activeSessionId: String?,
    val agent: String?,
    val model: String?,
)
