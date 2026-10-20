package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// Reading the frames of the pi RPC protocol: one JSON object per line.

internal fun parseFrame(line: String): JsonObject? =
    try {
        Json.parseToJsonElement(line) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

internal fun JsonObject.models(): List<AgentModel> =
    ((obj("data")?.get("models")) as? JsonArray).orEmpty().mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id = model.text("id") ?: return@mapNotNull null
        AgentModel("${model.text("provider")}/$id", (model["contextWindow"] as? JsonPrimitive)?.intOrNull)
    }

internal fun JsonObject.options() =
    (this["options"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun JsonObject.obj(key: String) = this[key] as? JsonObject

internal fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject?.count(key: String) = (this?.get(key) as? JsonPrimitive)?.intOrNull ?: 0

internal fun JsonObject.planInput(): String =
    (this["steps"] as? JsonArray)
        .orEmpty()
        .mapNotNull { element ->
            val step = element as? JsonObject ?: return@mapNotNull null
            val text = step.text("text") ?: return@mapNotNull null
            "[${if (step["done"] == JsonPrimitive(true)) "x" else " "}] $text"
        }.joinToString("\n")
