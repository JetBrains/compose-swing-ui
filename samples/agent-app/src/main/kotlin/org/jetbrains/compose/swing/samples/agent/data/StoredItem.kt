package org.jetbrains.compose.swing.samples.agent.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A settled transcript item as it is stored. The stored form names enum values as text, so it does not depend on
 * the state layer, and each kind carries only the fields it has.
 */
@Serializable
internal sealed interface StoredItem {
    @Serializable
    @SerialName("text")
    data class AssistantText(
        val text: String,
    ) : StoredItem

    @Serializable
    @SerialName("thinking")
    data class Thinking(
        val text: String,
    ) : StoredItem

    @Serializable
    @SerialName("toolCall")
    data class ToolCall(
        val id: String,
        val tool: String,
        val input: String,
        val output: String?,
        val status: String,
    ) : StoredItem

    @Serializable
    @SerialName("permission")
    data class Permission(
        val id: String,
        val tool: String,
        val detail: String,
        val decision: String?,
    ) : StoredItem

    @Serializable
    @SerialName("error")
    data class Failure(
        val detail: String,
        val exitCode: Int?,
    ) : StoredItem

    @Serializable
    @SerialName("usage")
    data class Usage(
        val inputTokens: Int,
        val outputTokens: Int,
    ) : StoredItem
}

// Unknown keys are skipped, so a payload an older build wrote with fewer fields, or a newer one with more, loads.
private val json = Json { ignoreUnknownKeys = true }

internal fun StoredItem.toPayload(): String = json.encodeToString(StoredItem.serializer(), this)

internal fun storedItem(payload: String): StoredItem = json.decodeFromString(StoredItem.serializer(), payload)
