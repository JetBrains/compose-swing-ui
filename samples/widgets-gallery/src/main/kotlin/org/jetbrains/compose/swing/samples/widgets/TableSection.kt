package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension
import javax.swing.ListSelectionModel

private data class Person(
    val name: String,
    val role: String,
    val age: Int,
)

// Table: typed rows projected through declared columns, with controlled single-row selection echoed
// into a Label. The Age column is editable - a committed edit flows through onCellEdit back into the
// backing list, so the next composition shows the new value.
@Composable
internal fun TableSection() {
    SectionColumn {
        SectionHeading("Table")
        SelectableTableCard()
    }
}

@Composable
private fun SelectableTableCard() {
    ExampleCard("Table with selection & editable column") {
        var people by remember {
            mutableStateOf(
                listOf(
                    Person("Ada Lovelace", "Engineer", 28),
                    Person("Alan Turing", "Researcher", 41),
                    Person("Grace Hopper", "Architect", 37),
                    Person("Edsger Dijkstra", "Engineer", 33),
                ),
            )
        }
        var selection by remember { mutableStateOf(setOf(0)) }

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 140))) {
            content {
                Table(
                    rows = people,
                    selectedRowIndices = selection,
                    onSelectionChange = { selection = it },
                    selectionMode = ListSelectionModel.SINGLE_SELECTION,
                ) {
                    column("Name") { it.name }
                    column("Role") { it.role }
                    column(
                        header = "Age",
                        isEditable = true,
                        onCellEdit = { _, rowIndex, age ->
                            if (age != null) {
                                people =
                                    people.mapIndexed { index, person ->
                                        if (index == rowIndex) person.copy(age = age) else person
                                    }
                            }
                        },
                    ) { it.age }
                }
            }
        }

        val selected = selection.firstOrNull()?.let { people.getOrNull(it) }
        Label("Selected: ${selected?.let { "${it.name} (age ${it.age})" } ?: "none"}")
        Label("Double-click an Age cell to edit it.")
    }
}
