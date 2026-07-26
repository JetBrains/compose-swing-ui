package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JFormattedTextField
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The size hints a text component is built with - a field's columns, an area's rows and columns - are
 * composition state like any other parameter, so they follow the value they are given for as long as
 * the component lives rather than only at the moment it is created.
 */
class TextSizeHintsTest {
    @Test
    fun aFieldIsBuiltWithTheColumnsItIsGiven() = runComposeSwingTest {
        setContent {
            TextField(value = "", columns = WIDE_COLUMNS)
        }

        assertEquals(WIDE_COLUMNS, onNodeOfType<JTextField>().fetch().columns)
    }

    @Test
    fun aFieldFollowsItsColumnsAfterTheFirstComposition() = runComposeSwingTest {
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            TextField(value = "", columns = columns)
        }

        val field = onNodeOfType<JTextField>().fetch()
        val widthBefore = field.preferredSize.width

        columns = WIDE_COLUMNS
        awaitIdle()

        assertEquals(WIDE_COLUMNS, field.columns)
        // The size a parent would give the field is derived from the hint, so it moves with it.
        assertTrue(field.preferredSize.width > widthBefore, "the field should ask for a wider size")
    }

    @Test
    fun aStateDrivenFieldFollowsItsColumns() = runComposeSwingTest {
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            TextField(state = rememberDocumentState(), columns = columns)
        }

        val field = onNodeOfType<JTextField>().fetch()
        val widthBefore = field.preferredSize.width

        columns = WIDE_COLUMNS
        awaitIdle()

        assertEquals(WIDE_COLUMNS, field.columns)
        assertTrue(field.preferredSize.width > widthBefore, "the field should ask for a wider size")
    }

    @Test
    fun anAreaIsBuiltWithTheRowsAndColumnsItIsGiven() = runComposeSwingTest {
        setContent {
            TextArea(value = "", rows = TALL_ROWS, columns = WIDE_COLUMNS)
        }

        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(TALL_ROWS, area.rows, "rows")
        assertEquals(WIDE_COLUMNS, area.columns, "columns")
    }

    @Test
    fun anAreaFollowsItsRowsAndColumnsAfterTheFirstComposition() = runComposeSwingTest {
        var rows by mutableIntStateOf(SHORT_ROWS)
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            TextArea(value = "", rows = rows, columns = columns)
        }

        val area = onNodeOfType<JTextArea>().fetch()
        val sizeBefore = area.preferredSize

        rows = TALL_ROWS
        columns = WIDE_COLUMNS
        awaitIdle()

        assertEquals(TALL_ROWS, area.rows, "rows")
        assertEquals(WIDE_COLUMNS, area.columns, "columns")
        // Only growth is asserted: an area's preferred size is the larger of its hint and its content.
        assertTrue(area.preferredSize.height > sizeBefore.height, "the area should ask for a taller size")
        assertTrue(area.preferredSize.width > sizeBefore.width, "the area should ask for a wider size")
    }

    @Test
    fun aStateDrivenAreaFollowsItsRowsAndColumns() = runComposeSwingTest {
        var rows by mutableIntStateOf(SHORT_ROWS)
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            TextArea(state = rememberDocumentState(), rows = rows, columns = columns)
        }

        val area = onNodeOfType<JTextArea>().fetch()
        val heightBefore = area.preferredSize.height
        val widthBefore = area.preferredSize.width

        rows = TALL_ROWS
        columns = WIDE_COLUMNS
        awaitIdle()

        assertEquals(TALL_ROWS, area.rows)
        assertEquals(WIDE_COLUMNS, area.columns)
        assertTrue(area.preferredSize.height > heightBefore, "the area should ask for a taller size")
        assertTrue(area.preferredSize.width > widthBefore, "the area should ask for a wider size")
    }

    @Test
    fun aFormattedFieldFollowsItsColumns() = runComposeSwingTest {
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            FormattedTextField(value = null, columns = columns)
        }

        val field = onNodeOfType<JFormattedTextField>().fetch()
        assertEquals(NARROW_COLUMNS, field.columns, "the field should be built with the columns it is given")
        val widthBefore = field.preferredSize.width

        columns = WIDE_COLUMNS
        awaitIdle()

        assertEquals(WIDE_COLUMNS, field.columns)
        assertTrue(field.preferredSize.width > widthBefore, "the field should ask for a wider size")
    }

    @Test
    fun aPasswordFieldFollowsItsColumns() = runComposeSwingTest {
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            PasswordField(value = CharArray(0), columns = columns)
        }

        val field = onNodeOfType<JPasswordField>().fetch()
        val widthBefore = field.preferredSize.width

        columns = WIDE_COLUMNS
        awaitIdle()

        assertEquals(WIDE_COLUMNS, field.columns)
        assertTrue(field.preferredSize.width > widthBefore, "the field should ask for a wider size")
    }

    @Test
    fun aReactivatedFieldAdoptsTheColumnsItWasGivenWhileParked() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var columns by mutableIntStateOf(NARROW_COLUMNS)
        setContent {
            ReusableContentHost(active) {
                TextField(value = "", columns = columns)
            }
        }
        val parked = onNodeOfType<JTextField>().fetch()

        active = false
        awaitIdle()
        columns = WIDE_COLUMNS
        active = true
        awaitIdle()

        val reactivated = onNodeOfType<JTextField>().fetch()
        assertSame(parked, reactivated, "the component should be reused")
        assertEquals(WIDE_COLUMNS, reactivated.columns, "a reused component adopts the new hint")
    }

    private companion object {
        const val NARROW_COLUMNS = 8
        const val WIDE_COLUMNS = 28
        const val SHORT_ROWS = 2
        const val TALL_ROWS = 12
    }
}
