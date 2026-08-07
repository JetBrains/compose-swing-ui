package org.jetbrains.compose.swing.samples.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Alignment
import org.jetbrains.compose.swing.components.layout.Arrangement
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.Row
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.onAccept
import org.jetbrains.compose.swing.modifier.layout.preferredSize

// A single SnapshotStateList of tasks drives the whole screen: the summary, the progress bar, and
// the rows.
@Composable
internal fun ReactiveTaskList(modifier: SwingModifier = SwingModifier) {
    val tasks = remember { initialTasks().toMutableStateList() }
    var nextId by remember { mutableIntStateOf(tasks.size + 1) }

    val total by remember { derivedStateOf { tasks.size } }
    val done by remember { derivedStateOf { tasks.count { it.done } } }

    BorderPanel(modifier = modifier, hgap = 0, vgap = ROW_GAP) {
        Column(SwingModifier.north(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            SampleTitle("Reactive task list")
            Caption(
                "One state list drives the summary, the progress bar, and the rows. " +
                    "Edit, add, complete, or remove a task and watch the whole screen follow.",
            )
            SummaryRow(done = done, total = total)
            AddTaskRow(onAdd = { title -> tasks += Task(id = nextId++, title = title) })
        }
        TaskRows(
            tasks = tasks,
            modifier = SwingModifier.center(),
            onToggle = { id, checked -> tasks.replaceById(id) { it.copy(done = checked) } },
            onRename = { id, title -> tasks.replaceById(id) { it.copy(title = title) } },
            onRemove = { id -> tasks.removeAll { it.id == id } },
        )
    }
}

@Composable
private fun ColumnScope.SummaryRow(
    done: Int,
    total: Int,
) {
    Card("Progress") {
        Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP), verticalAlignment = Alignment.CenterVertically) {
            // Fixed-width label so the bar to its right never shifts as the counts change.
            ValueLabel(text = "$done of $total done", width = 120)
            ProgressBar(
                value = done,
                min = 0,
                max = total.coerceAtLeast(1),
            )
        }
    }
}

@Composable
private fun ColumnScope.AddTaskRow(onAdd: (String) -> Unit) {
    Card("Add a task") {
        var draft by remember { mutableStateOf("") }
        val canAdd by remember { derivedStateOf { draft.isNotBlank() } }

        fun commit() {
            if (draft.isNotBlank()) {
                onAdd(draft.trim())
                draft = ""
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ROW_GAP), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                columns = 28,
                modifier = SwingModifier.accessibleName("New task").onAccept { commit() },
            )
            Button(
                text = "Add",
                onClick = { commit() },
                modifier = SwingModifier.enabled(canAdd),
            )
        }
    }
}

@Composable
private fun TaskRows(
    tasks: List<Task>,
    onToggle: (Int, Boolean) -> Unit,
    onRename: (Int, String) -> Unit,
    modifier: SwingModifier = SwingModifier,
    onRemove: (Int) -> Unit,
) {
    // Rows are added to and removed from the Swing tree as the list changes, not merely shown or hidden.
    ScrollPane(modifier = modifier.preferredSize(0, 320)) {
        Column(SwingModifier.viewport(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            if (tasks.isEmpty()) {
                Card("Tasks") {
                    Label("No tasks yet - add one above.")
                }
            } else {
                tasks.forEach { task ->
                    // Keyed by the stable task id so a row keeps its identity (and any in-progress
                    // edit) when other rows are added, removed, or reordered around it.
                    key(task.id) {
                        TaskRow(
                            task = task,
                            onToggle = { checked -> onToggle(task.id, checked) },
                            onRename = { title -> onRename(task.id, title) },
                            onRemove = { onRemove(task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TaskRow(
    task: Task,
    onToggle: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
) {
    // The title takes the weight, so the toggle stays at the leading edge and Remove at the trailing
    // edge no matter how wide the window is.
    Row(
        modifier = SwingModifier.testTag(taskRowTag(task.id)).fillWidth(),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            text = "",
            checked = task.done,
            onCheckedChange = onToggle,
            modifier = SwingModifier.testTag(taskToggleTag(task.id)),
        )
        TextField(
            value = task.title,
            onValueChange = onRename,
            columns = 28,
            modifier = SwingModifier.weight(1f).testTag(taskTitleTag(task.id)),
        )
        Button(
            text = "Remove",
            onClick = onRemove,
            modifier = SwingModifier.testTag(taskRemoveTag(task.id)),
        )
    }
}

private fun SnapshotStateList<Task>.replaceById(
    id: Int,
    transform: (Task) -> Task,
) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index])
}

internal data class Task(
    val id: Int,
    val title: String,
    val done: Boolean = false,
)

private fun initialTasks(): List<Task> =
    listOf(
        Task(id = 1, title = "Compose a reactive screen over Swing", done = true),
        Task(id = 2, title = "Drive the list from a single state source", done = true),
        Task(id = 3, title = "Recompose the summary and progress bar"),
        Task(id = 4, title = "Keep the layout still as state changes"),
    )

internal fun taskRowTag(id: Int): String = "task-row-$id"

internal fun taskToggleTag(id: Int): String = "task-toggle-$id"

internal fun taskTitleTag(id: Int): String = "task-title-$id"

internal fun taskRemoveTag(id: Int): String = "task-remove-$id"
