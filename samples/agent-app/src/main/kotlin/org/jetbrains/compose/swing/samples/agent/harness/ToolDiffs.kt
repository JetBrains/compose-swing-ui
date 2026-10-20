package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The unified diff that turns [old] into [new]; [old] is `null` for a file that did not exist. The lines both texts
 * share at the start and at the end are left out.
 */
internal fun changeDiff(
    old: String?,
    new: String,
): String {
    val before = old?.let(::contentLines).orEmpty()
    val after = contentLines(new)
    val head = before.zip(after).takeWhile { (a, b) -> a == b }.size
    val tail =
        before
            .drop(head)
            .asReversed()
            .zip(after.drop(head).asReversed())
            .takeWhile { (a, b) -> a == b }
            .size
    val removed = before.subList(head, before.size - tail)
    val added = after.subList(head, after.size - tail)
    val header = "@@ -${range(head, removed.size)} +${range(head, added.size)} @@"
    return (listOf(header) + removed.map { "-$it" } + added.map { "+$it" }).joinToString("\n")
}

private fun contentLines(text: String): List<String> =
    if (text.isEmpty()) emptyList() else text.lines().let { if (text.endsWith('\n')) it.dropLast(1) else it }

private fun range(
    head: Int,
    count: Int,
) = "${if (count == 0) head else head + 1},$count"

/** The diff of the replacements an `edit` call asks for, named in its `edits` or in the call itself; else `null`. */
internal fun replacementsDiff(args: JsonObject): String? {
    val edits = (args["edits"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(args)
    val diffs =
        edits.mapNotNull { edit ->
            val old = edit.text("oldText")
            val new = edit.text("newText")
            if (old != null && new != null) changeDiff(old, new) else null
        }
    return diffs.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** The text of [file], or `null` when it cannot be read. */
internal fun readOrNull(file: Path): String? =
    try {
        Files.readString(file)
    } catch (_: IOException) {
        null
    }
