package org.jetbrains.compose.swing.samples.agent.state

/** One step of the agent's plan. */
internal data class PlanItem(
    val text: String,
    val isDone: Boolean,
)

/** The steps of a `plan` call's [input], whose lines read `[ ] step` or `[x] step`; other lines are skipped. */
internal fun planSteps(input: String): List<PlanItem> {
    val step = Regex("""\[([ xX]?)]\s*(.+)""")
    return input.lines().mapNotNull { line ->
        val match = step.matchEntire(line.trim()) ?: return@mapNotNull null
        PlanItem(text = match.groupValues[2], isDone = match.groupValues[1].isNotBlank())
    }
}

/** The index of the step being worked on: the first one not done yet, or -1 when every step is done. */
internal val List<PlanItem>.currentIndex: Int get() = indexOfFirst { !it.isDone }
