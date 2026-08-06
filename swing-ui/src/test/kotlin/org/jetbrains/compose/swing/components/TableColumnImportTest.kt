package org.jetbrains.compose.swing.components

import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A table's columns are declared the same way from any package. Both forms of `column` are extensions,
 * so the single import a caller writes for the name brings both into scope, and the class-taking form
 * never stands in for the one that derives the class from the value extractor.
 *
 * These tests live outside the package that declares `column` on purpose: inside it the extensions are
 * in scope without an import, which is the one place the caller's view of the name cannot be observed.
 */
class TableColumnImportTest {
    private data class Person(
        val name: String,
        val age: Int,
    )

    @Test
    fun aColumnTakesItsClassFromTheValueExtractor() = runComposeSwingTest {
        setContent {
            Table(rows = listOf(Person("Ada", 36))) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val model = onNodeOfType<JTable>().fetch().model
        assertEquals("Name", model.getColumnName(0), "column 0 header")
        assertEquals("Ada", model.getValueAt(0, 0), "cell (0,0) value")
        assertEquals(String::class.java, model.getColumnClass(0), "a column of names holds strings")
        assertEquals(Int::class.javaObjectType, model.getColumnClass(1), "a column of ages holds integers")
    }

    @Test
    fun aColumnHoldsTheClassItDeclares() = runComposeSwingTest {
        setContent {
            Table(rows = listOf(Person("Ada", 36))) {
                column("Age", Number::class.java) { it.age }
            }
        }

        val model = onNodeOfType<JTable>().fetch().model
        assertEquals(36, model.getValueAt(0, 0), "cell (0,0) value")
        assertEquals(Number::class.java, model.getColumnClass(0), "the declared class, not the value's own")
    }
}
