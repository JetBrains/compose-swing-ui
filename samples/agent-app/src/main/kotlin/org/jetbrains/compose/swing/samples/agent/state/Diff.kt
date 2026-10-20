package org.jetbrains.compose.swing.samples.agent.state

/** What a line of a unified diff is, read from its first character. */
internal enum class DiffLineKind { FileHeader, Hunk, Added, Removed, Context }

/** One line of a unified diff without its marker, with the line numbers it has in the old and new file. */
internal data class DiffLine(
    val kind: DiffLineKind,
    val text: String,
    val oldNumber: Int? = null,
    val newNumber: Int? = null,
)

/** Classifies each line of a unified [diff] and numbers it from the hunk header above it. */
internal fun diffLines(diff: String): List<DiffLine> {
    val hunkStart = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)""")
    var old = 0
    var new = 0
    var inHunk = false
    return diff.lines().map { line ->
        when {
            line.startsWith("@@") -> {
                hunkStart.find(line)?.let {
                    old = it.groupValues[1].toInt()
                    new = it.groupValues[2].toInt()
                }
                inHunk = true
                DiffLine(DiffLineKind.Hunk, line)
            }

            // Only the lines before the first hunk name files; inside one, "---" is a removed "--".
            !inHunk -> {
                DiffLine(DiffLineKind.FileHeader, line)
            }

            line.startsWith("+") -> {
                DiffLine(DiffLineKind.Added, line.drop(1), newNumber = new++)
            }

            line.startsWith("-") -> {
                DiffLine(DiffLineKind.Removed, line.drop(1), oldNumber = old++)
            }

            else -> {
                DiffLine(DiffLineKind.Context, line.drop(1), old++, new++)
            }
        }
    }
}

/** How many lines a diff adds and removes. */
internal data class DiffStats(
    val added: Int,
    val removed: Int,
)

internal fun diffStats(diff: String): DiffStats {
    val lines = diffLines(diff)
    return DiffStats(lines.count { it.kind == DiffLineKind.Added }, lines.count { it.kind == DiffLineKind.Removed })
}
