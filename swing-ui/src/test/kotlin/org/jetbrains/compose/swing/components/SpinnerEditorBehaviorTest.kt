package org.jetbrains.compose.swing.components

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.text.DecimalFormat
import java.util.Calendar
import java.util.GregorianCalendar
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SpinnerListModel
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for what a [Spinner] shows its value through: the format its own editor renders and
 * parses with, and an editor declared in place of that one.
 */
class SpinnerEditorBehaviorTest {
    private fun defaultNumberPattern(): String =
        (JSpinner(SpinnerNumberModel()).editor as JSpinner.NumberEditor).format.toPattern()

    @Test
    fun withNothingDeclaredTheSpinnerShowsTheEditorItBuildsForItsModel() = runComposeSwingTest {
        setContent { Spinner(value = 1.5, onValueChange = {}, step = 0.5) }

        val editor =
            assertIs<JSpinner.NumberEditor>(
                onNodeOfType<JSpinner>().fetch().editor,
                "a number model is shown through the spinner's own number editor",
            )
        assertEquals(defaultNumberPattern(), editor.format.toPattern(), "the pattern is the spinner's own")
        assertEquals(
            (JSpinner(SpinnerNumberModel()).editor as JSpinner.NumberEditor).textField.horizontalAlignment,
            editor.textField.horizontalAlignment,
            "the field is aligned the way the look and feel aligns a spinner's own",
        )
    }

    @Test
    fun aNumberFormatIsThePatternTheSpinnerRendersWith() = runComposeSwingTest {
        setContent { Spinner(value = 1.5, onValueChange = {}, step = 0.5, format = "#0.00") }

        val editor =
            assertIs<JSpinner.NumberEditor>(
                onNodeOfType<JSpinner>().fetch().editor,
                "a formatted number model is shown through a number editor",
            )
        assertEquals("#0.00", editor.format.toPattern(), "the declared pattern is the editor's own")
        assertEquals(
            DecimalFormat("#0.00").format(1.5),
            editor.textField.text,
            "the value is rendered through the declared pattern",
        )
    }

    @Test
    fun aDateFormatIsThePatternTheSpinnerRendersWith() = runComposeSwingTest {
        val march15 = GregorianCalendar(2024, Calendar.MARCH, 15).time
        setContent { Spinner(value = march15, onValueChange = {}, format = "yyyy-MM-dd") }

        val editor =
            assertIs<JSpinner.DateEditor>(
                onNodeOfType<JSpinner>().fetch().editor,
                "a formatted date model is shown through a date editor",
            )
        assertEquals("yyyy-MM-dd", editor.format.toPattern(), "the declared pattern is the editor's own")
        assertEquals("2024-03-15", editor.textField.text, "the date is rendered through the declared pattern")
    }

    @Test
    fun changingTheFormatAcrossRecompositionIsHonored() = runComposeSwingTest {
        var format by mutableStateOf("#0.00")
        setContent { Spinner(value = 1.5, onValueChange = {}, step = 0.5, format = format) }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals(
            DecimalFormat("#0.00").format(1.5),
            (spinner.editor as JSpinner.NumberEditor).textField.text,
            "the value starts out rendered through the original pattern",
        )

        format = "#0.0000"
        awaitIdle()

        val editor = spinner.editor as JSpinner.NumberEditor
        assertEquals("#0.0000", editor.format.toPattern(), "the new pattern rebuilds the spinner's own editor")
        assertEquals(
            DecimalFormat("#0.0000").format(1.5),
            editor.textField.text,
            "the value is re-rendered through the new pattern",
        )
    }

    @Test
    fun clearingTheFormatRestoresTheSpinnersOwnPattern() = runComposeSwingTest {
        var format by mutableStateOf<String?>("#0.0000")
        setContent { Spinner(value = 1.5, onValueChange = {}, step = 0.5, format = format) }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals(
            "#0.0000",
            (spinner.editor as JSpinner.NumberEditor).format.toPattern(),
            "the declared pattern is the editor's own to begin with",
        )

        format = null
        awaitIdle()

        assertEquals(
            defaultNumberPattern(),
            (spinner.editor as JSpinner.NumberEditor).format.toPattern(),
            "clearing the format puts the spinner's own pattern back",
        )
    }

    @Test
    fun aDeclaredEditorIsTheSurfaceTheSpinnerShows() = runComposeSwingTest {
        var value by mutableStateOf<Number>(3)
        setContent {
            Spinner(value = value, onValueChange = { value = it }) { Label("value $value") }
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        val host = spinner.editor
        assertEquals("value 3", host.firstLabelText(), "what the editor composes is what the spinner shows")

        value = 4
        awaitIdle()

        assertSame(host, spinner.editor, "a step recomposes the editor composition rather than rebuilding it")
        assertEquals("value 4", host.firstLabelText(), "and that composition renders the value it now reads")
    }

    /** The text of the first [JLabel] anywhere beneath this component. */
    private fun Container.firstLabelText(): String? = components.firstNotNullOfOrNull { child ->
        (child as? JLabel)?.text ?: (child as? Container)?.firstLabelText()
    }

    @Test
    fun withdrawingAComposedEditorForAFormatShowsTheFormattedEditor() = runComposeSwingTest {
        var composed by mutableStateOf(true)
        setContent {
            Spinner(
                value = 1.5,
                onValueChange = {},
                step = 0.5,
                format = if (composed) null else "#0.00",
                editor = if (composed) ({ Label("mine") }) else null,
            )
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("mine", spinner.editor.firstLabelText(), "the composed editor is what the spinner shows")

        composed = false
        awaitIdle()

        val editor =
            assertIs<JSpinner.NumberEditor>(
                spinner.editor,
                "withdrawing the composed editor for a format shows the spinner's own editor",
            )
        assertEquals("#0.00", editor.format.toPattern(), "and that editor renders through the declared pattern")
    }

    @Test
    fun withdrawingAComposedEditorPutsTheSpinnersOwnEditorBack() = runComposeSwingTest {
        var composed by mutableStateOf(true)
        setContent {
            Spinner(
                value = 1.5,
                onValueChange = {},
                step = 0.5,
                editor = if (composed) ({ Label("mine") }) else null,
            )
        }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertEquals("mine", spinner.editor.firstLabelText(), "the composed editor is what the spinner shows")

        composed = false
        awaitIdle()

        val editor =
            assertIs<JSpinner.NumberEditor>(
                spinner.editor,
                "withdrawing the composed editor gives the spinner the editor it builds for its model",
            )
        assertEquals(defaultNumberPattern(), editor.format.toPattern(), "with the spinner's own pattern")
    }

    @Test
    fun aReactivatedSpinnerShowsItsComposedEditorAgain() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                var value by mutableStateOf<Number>(3)
                Spinner(value = value, onValueChange = { value = it }) { Label("value $value") }
            }
        }
        awaitIdle()

        val parked = onNodeOfType<JSpinner>().fetch()
        assertEquals("value 3", parked.editor.firstLabelText(), "the composed editor is what the spinner shows")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertNotSame(parked, spinner, "reactivation builds a fresh spinner rather than reusing the parked one")
        assertEquals(
            "value 3",
            spinner.editor.firstLabelText(),
            "the fresh spinner shows the editor its declaration composes, not the one it builds itself",
        )
    }

    @Test
    fun theRawListenerOverloadRendersThroughItsOwnFormat() = runComposeSwingTest {
        val model = SpinnerNumberModel(1.5, null, null, 0.5)
        setContent { Spinner(model = model, changeListener = ChangeListener {}, format = "#0.00") }

        val editor =
            assertIs<JSpinner.NumberEditor>(
                onNodeOfType<JSpinner>().fetch().editor,
                "the model-driven overload is shown through a number editor too",
            )
        assertEquals("#0.00", editor.format.toPattern(), "the declared pattern is the editor's own")
    }

    @Test
    fun swappingTheModelRebuildsTheEditorForItsKind() = runComposeSwingTest {
        val number = SpinnerNumberModel(5, 0, 10, 1)
        val dates = SpinnerDateModel()
        var numeric by mutableStateOf(true)
        setContent { Spinner(if (numeric) number else dates, changeListener = ChangeListener {}) }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        assertIs<JSpinner.NumberEditor>(spinner.editor, "a number model is shown through a number editor")

        numeric = false
        awaitIdle()

        assertIs<JSpinner.DateEditor>(spinner.editor, "swapping in a date model rebuilds the editor around it")
    }

    @Test
    fun swappingTheModelAfterClearingAFormatRebuildsTheEditor() = runComposeSwingTest {
        val number = SpinnerNumberModel(5, 0, 10, 1)
        val dates = SpinnerDateModel()
        var format by mutableStateOf<String?>("#0.00")
        var numeric by mutableStateOf(true)
        setContent { Spinner(if (numeric) number else dates, changeListener = ChangeListener {}, format = format) }
        awaitIdle()

        val spinner = onNodeOfType<JSpinner>().fetch()
        format = null
        awaitIdle()
        assertIs<JSpinner.NumberEditor>(spinner.editor, "clearing the pattern puts the spinner's own editor back")

        numeric = false
        awaitIdle()

        assertIs<JSpinner.DateEditor>(spinner.editor, "swapping in a date model still rebuilds the editor around it")
    }

    @Test
    fun declaringBothAFormatAndAnEditorIsRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Spinner(
                        value = 1.5,
                        onValueChange = {},
                        step = 0.5,
                        format = "#0.00",
                        editor = { Label("mine") },
                    )
                }
            }

        assertTrue(
            "either a format or an editor" in failure.message.orEmpty(),
            "the two ways of naming what the spinner shows are reported as the conflict they are: " +
                "${failure.message}",
        )
    }

    @Test
    fun aFormatOverAModelThatReadsNeitherPatternIsRefused() = runComposeSwingTest {
        val model = SpinnerListModel(listOf("red", "green"))
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent { Spinner(model = model, changeListener = ChangeListener {}, format = "#0.00") }
            }

        assertTrue(
            "a number or a date model" in failure.message.orEmpty(),
            "the model that cannot read the pattern is named: ${failure.message}",
        )
    }
}
