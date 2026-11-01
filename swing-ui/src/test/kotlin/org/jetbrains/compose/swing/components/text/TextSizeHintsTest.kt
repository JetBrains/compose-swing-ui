package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JTextArea
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
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
            TextField(value = "", onValueChange = {}, columns = 28)
        }

        onNodeOfType<JTextField>().assertProperty(28) { columns }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fieldCases")
    fun aFieldFollowsItsColumns(content: @Composable (Int) -> Unit) = runComposeSwingTest {
        var columns by mutableIntStateOf(8)
        setContent { content(columns) }

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(8, field.columns, "the field should be built with the columns it is given")
        val widthBefore = field.preferredSize.width
        columns = 28
        awaitIdle()
        assertEquals(28, field.columns, "the field should follow its columns")
        // The size a parent would give the field is derived from the hint, so it moves with it.
        assertTrue(field.preferredSize.width > widthBefore, "the field should ask for a wider size")
    }

    @Test
    fun anAreaIsBuiltWithTheRowsAndColumnsItIsGiven() = runComposeSwingTest {
        setContent {
            TextArea(value = "", onValueChange = {}, rows = 12, columns = 28)
        }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(12, "rows") { rows }
        area.assertProperty(28, "columns") { columns }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaCases")
    fun anAreaFollowsItsRowsAndColumns(content: @Composable (Int, Int) -> Unit) = runComposeSwingTest {
        var rows by mutableIntStateOf(2)
        var columns by mutableIntStateOf(8)
        setContent { content(rows, columns) }

        val area = onNodeOfType<JTextArea>()
        val sizeBefore = area.fetch().preferredSize
        rows = 12
        columns = 28
        awaitIdle()
        area.assertProperty(12, "rows") { this.rows }
        area.assertProperty(28, "columns") { this.columns }
        // Only growth is asserted: an area's preferred size is the larger of its hint and its content.
        area.assertProperty(true, "the area should ask for a taller size") { preferredSize.height > sizeBefore.height }
        area.assertProperty(true, "the area should ask for a wider size") { preferredSize.width > sizeBefore.width }
    }

    companion object {
        @JvmStatic
        fun fieldCases(): List<Named<@Composable (Int) -> Unit>> = listOf(
            Named.of("TextField", { columns -> TextField(value = "", onValueChange = {}, columns = columns) }),
            Named.of("state-driven TextField", { columns ->
                TextField(state = rememberDocumentState(), columns = columns)
            }),
            Named.of("FormattedTextField", { columns ->
                FormattedTextField(value = null, onValueChange = {}, columns = columns)
            }),
            Named.of("PasswordField", { columns ->
                PasswordField(value = CharArray(0), onValueChange = {}, columns = columns)
            }),
        )

        @JvmStatic
        fun areaCases(): List<Named<@Composable (Int, Int) -> Unit>> = listOf(
            Named.of("TextArea", { rows, columns ->
                TextArea(value = "", onValueChange = {}, rows = rows, columns = columns)
            }),
            Named.of("state-driven TextArea", { rows, columns ->
                TextArea(state = rememberDocumentState(), rows = rows, columns = columns)
            }),
        )
    }

    @Test
    fun aReactivatedFieldAdoptsTheColumnsItWasGivenWhileParked() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var columns by mutableIntStateOf(8)
        setContent {
            ReusableContentHost(active) {
                TextField(value = "", onValueChange = {}, columns = columns)
            }
        }
        val parked = onNodeOfType<JTextField>().fetch()

        active = false
        awaitIdle()
        columns = 28
        active = true
        awaitIdle()

        val reactivated = onNodeOfType<JTextField>().fetch()
        assertNotSame(parked, reactivated, "reactivation builds a fresh component rather than reusing the parked one")
        assertEquals(28, reactivated.columns, "the fresh component carries the hint the composition holds")
    }
}
