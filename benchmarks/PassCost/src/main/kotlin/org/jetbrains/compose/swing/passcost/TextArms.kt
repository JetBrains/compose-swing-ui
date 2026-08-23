package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.layout.Column
import javax.swing.JEditorPane
import javax.swing.JFormattedTextField
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.text.AbstractDocument
import javax.swing.text.Document
import javax.swing.text.JTextComponent

/** Every arm measuring a text component, in the order this module reports them. */
internal fun textArms(): List<Arm> =
    listOf(
        textFieldArm(),
        adoptedEditArm(),
        passwordFieldArm(),
        formattedTextFieldArm(),
        textAreaArm(),
        textPaneArm(),
        editorPaneArm(),
    )

/**
 * Every widget a text field whose declared text changes on every pass: what a two-way text declaration
 * costs, where settling rewrites the whole document and the field answers that write through the
 * document listener the wrapper installs.
 *
 * The two texts are the interned constants [alternatingText] answers with, so every pass is a real
 * change and the driver allocates nothing; the field starts on a third text, so the first measured pass
 * writes as every later one does.
 */
private fun textFieldArm(): Arm =
    Arm(listOf(TEXT_FIELD_ARM)) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val fieldRuns = IntArray(1)
        Run(
            content = { Column { repeat(widgets) { DeclaredTextField(text) { fieldRuns[0]++ } } } },
            drive = { pass ->
                if (changing) text.value = alternatingText(pass)
                TEXT_FIELD_ARM
            },
            verify = { root, passes ->
                val fields = componentsOfType(root, JTextField::class.java)
                checkWidgets("text fields", fields.size, widgets)
                checkScopeRuns("the text field scopes", fieldRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("text field", fields.map { documentText(it) }, declaredText(changing, passes))
            },
        )
    }

/**
 * Every widget a text field the user edits and the caller adopts: the driver writes the pass's text
 * straight into every field's document, as a user typing does, the callback writes what the field
 * reports back into the declared state, and the pass that follows declares onto every field the text it
 * is already holding.
 *
 * That is what a controlled field costs while someone types into it, and it is the one settle that
 * writes nothing: the declaration moved, so the settle is due, and the widget is already where it is
 * being sent. What such a pass spends is what reading the widget back costs.
 *
 * The texts are long enough that reading one is worth more than the call that reads it, so a pass that
 * reads a document once and one that reads it twice are told apart by more than the clock's resolution.
 * They are built once, ahead of the batch, so the driver allocates nothing; the fields start on a third
 * text no pass ever writes, so a field the driver never reached carries a text no expectation names.
 */
private fun adoptedEditArm(): Arm =
    Arm(listOf(ADOPTED_EDIT_ARM)) { widgets, changing ->
        val text = mutableStateOf(UNEDITED_TEXT)
        val adopt: (String) -> Unit = { edited -> text.value = edited }
        val captured = CapturedFields()
        val fieldRuns = IntArray(1)
        Run(
            content = {
                Column { repeat(widgets) { AdoptingTextField(text, adopt, captured) { fieldRuns[0]++ } } }
            },
            drive = { pass ->
                if (changing) {
                    val edited = EDITED_TEXTS[pass % 2]
                    for (index in 0 until captured.size) {
                        captured[index].document.let { it.replaceSpan(0, it.length, edited) }
                    }
                }
                ADOPTED_EDIT_ARM
            },
            verify = { root, passes ->
                val fields = componentsOfType(root, JTextField::class.java)
                checkWidgets("adopting text fields", fields.size, widgets)
                checkWidgets("captured text fields", captured.size, widgets)
                val expected = if (changing) EDITED_TEXTS[(passes - 1) % 2] else UNEDITED_TEXT
                // Checked ahead of the documents: the driver writes those itself, so they read back what
                // it wrote whether or not a field ever reported it. The declared state moves through the
                // callback alone, so it is what says the report arrived.
                check(text.value == expected) {
                    "the declared text is left on '${text.value}', where the fields hold '$expected'"
                }
                checkApplied("adopting text field", fields.map { documentText(it) }, expected)
                // A floor rather than a count: the driver's own edit invalidates the scopes as it is
                // made, so a pass re-runs them before the frame it is measured over rather than because
                // of it. Every pass edits onto the other of the two texts, so a field whose report is
                // adopted re-runs at least once for each of them.
                val leastRuns = widgets * if (changing) 1 + passes else 1
                check(fieldRuns[0] >= leastRuns) {
                    "the adopting text field scopes ran ${fieldRuns[0]} times across $passes passes, " +
                        "where the $widgets fields run at least $leastRuns times"
                }
            },
        )
    }

/**
 * Replaces the whole of [this] document with [text], the way an edit through the component does, so the
 * wrapper hears one change rather than the removal and the insertion apiece.
 */
private fun Document.replaceSpan(
    offset: Int,
    length: Int,
    text: String,
) {
    when (this) {
        is AbstractDocument -> {
            replace(offset, length, text, null)
        }

        else -> {
            if (length > 0) remove(offset, length)
            insertString(offset, text, null)
        }
    }
}

/**
 * Every widget a password field whose declared characters change on every pass: the same two-way text
 * declaration a text field carries, over the character array a password field declares instead of a
 * string, compared by content and settled by materializing the characters into the field.
 *
 * The two arrays are built ahead of the batch and alternated, and the field starts on a third whose
 * content matches neither, so every measured pass is a real change and the driver allocates nothing.
 */
private fun passwordFieldArm(): Arm =
    Arm(listOf(PASSWORD_FIELD_ARM)) { widgets, changing ->
        val passwords = List(2) { index -> alternatingText(index).toCharArray() }
        val characters = mutableStateOf(INITIAL_TEXT.toCharArray())
        val fieldRuns = IntArray(1)
        Run(
            content = { Column { repeat(widgets) { DeclaredPasswordField(characters) { fieldRuns[0]++ } } } },
            drive = { pass ->
                if (changing) characters.value = passwords[pass % 2]
                PASSWORD_FIELD_ARM
            },
            verify = { root, passes ->
                val fields = componentsOfType(root, JPasswordField::class.java)
                checkWidgets("password fields", fields.size, widgets)
                checkScopeRuns("the password field scopes", fieldRuns[0], widgets * if (changing) 1 + passes else 1)
                val held = fields.map { String(it.password) }
                checkApplied("password field", held, declaredText(changing, passes))
            },
        )
    }

/**
 * Every widget a formatted field whose declared value changes on every pass: what a two-way declaration
 * of a typed value costs, where writing the value reinstalls the field's formatter and regenerates the
 * characters from it.
 *
 * The two values are boxed once ahead of every batch, so the driver allocates nothing, and the field
 * starts on a third, so the first measured pass writes as every later one does. What is checked is the
 * value the field committed: a field answers with the object the write gave it, so a value it carries
 * is one a write put there.
 */
private fun formattedTextFieldArm(): Arm =
    Arm(listOf(FORMATTED_FIELD_ARM)) { widgets, changing ->
        val declared = mutableStateOf<Any?>(UNSET_FORMATTED_VALUE)
        val fieldRuns = IntArray(1)
        Run(
            content = { Column { repeat(widgets) { DeclaredFormattedTextField(declared) { fieldRuns[0]++ } } } },
            drive = { pass ->
                if (changing) declared.value = FORMATTED_VALUES[pass % 2]
                FORMATTED_FIELD_ARM
            },
            verify = { root, passes ->
                val fields = componentsOfType(root, JFormattedTextField::class.java)
                checkWidgets("formatted fields", fields.size, widgets)
                checkScopeRuns("the formatted field scopes", fieldRuns[0], widgets * if (changing) 1 + passes else 1)
                val expected = if (changing) FORMATTED_VALUES[(passes - 1) % 2] else UNSET_FORMATTED_VALUE
                checkApplied("formatted field", fields.map { it.value }, expected)
            },
        )
    }

/**
 * Every widget a text area whose declared text changes on every pass: the same two-way text declaration
 * a text field carries, over the multi-line component and the plain document behind it.
 */
private fun textAreaArm(): Arm =
    Arm(listOf(TEXT_AREA_ARM)) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val areaRuns = IntArray(1)
        Run(
            content = { Column { repeat(widgets) { DeclaredTextArea(text) { areaRuns[0]++ } } } },
            drive = { pass ->
                if (changing) text.value = alternatingText(pass)
                TEXT_AREA_ARM
            },
            verify = { root, passes ->
                val areas = componentsOfType(root, JTextArea::class.java)
                checkWidgets("text areas", areas.size, widgets)
                checkScopeRuns("the text area scopes", areaRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("text area", areas.map { documentText(it) }, declaredText(changing, passes))
            },
        )
    }

/**
 * Every widget a text pane whose declared text changes on every pass: the same two-way text declaration
 * a text field carries, settled onto a styled document rather than a plain one.
 *
 * A styled document rebuilds its element structure on every rewrite, so this arm is measured on
 * [HEAVY_TREE] panes rather than the [LARGE_TREE] a field arm carries. Two sizes still separate what
 * the declaration costs per pane from what it costs the pass.
 */
private fun textPaneArm(): Arm =
    Arm(listOf(TEXT_PANE_ARM), HEAVY_TREE_SIZES) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val paneRuns = IntArray(1)
        Run(
            content = { Column { repeat(widgets) { DeclaredTextPane(text) { paneRuns[0]++ } } } },
            drive = { pass ->
                if (changing) text.value = alternatingText(pass)
                TEXT_PANE_ARM
            },
            verify = { root, passes ->
                val panes = componentsOfType(root, JTextPane::class.java)
                checkWidgets("text panes", panes.size, widgets)
                checkScopeRuns("the text pane scopes", paneRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("text pane", panes.map { documentText(it) }, declaredText(changing, passes))
            },
        )
    }

/**
 * Every widget an editor pane whose declared markup changes on every pass: what a push costs where the
 * component keeps no mirror at all.
 *
 * A rendering pane renders and does not report - the user cannot type into it - so its markup is
 * declared with a plain key rather than settled through a two-way declaration, and what that key
 * compares is the pane's whole source: the kit, the base location and the markup together. The plain
 * kit is the one measured, so what a pass shows is the push itself and not the cost of parsing a markup
 * language.
 *
 * A push rewrites the pane's whole document through its kit, so this arm is measured on [HEAVY_TREE]
 * panes rather than the [LARGE_TREE] a field arm carries.
 */
private fun editorPaneArm(): Arm =
    Arm(listOf(EDITOR_PANE_ARM), HEAVY_TREE_SIZES) { widgets, changing ->
        val markup = mutableStateOf(INITIAL_TEXT)
        val paneRuns = IntArray(1)
        Run(
            content = { Column { repeat(widgets) { RenderingEditorPane(markup) { paneRuns[0]++ } } } },
            drive = { pass ->
                if (changing) markup.value = alternatingText(pass)
                EDITOR_PANE_ARM
            },
            verify = { root, passes ->
                val panes = componentsOfType(root, JEditorPane::class.java)
                checkWidgets("editor panes", panes.size, widgets)
                checkScopeRuns("the editor pane scopes", paneRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("editor pane", panes.map { documentText(it) }, declaredText(changing, passes))
            },
        )
    }

/**
 * The value the last pass declared: the text it alternated onto where the arm changes, and the one the
 * widget was composed on for its null variant.
 */
private fun declaredText(
    changing: Boolean,
    passes: Int,
): String = if (changing) alternatingText(passes - 1) else INITIAL_TEXT

/**
 * The text [component] holds, read from its document rather than through its own `getText`: a pane
 * writes its text back out through its editor kit, which answers with source rendering to that content
 * rather than with the characters the document holds.
 */
private fun documentText(component: JTextComponent): String = component.document.let { it.getText(0, it.length) }

/** How long the text an adopted edit writes is, so that reading one costs more than calling for it. */
private const val EDITED_TEXT_LENGTH = 400

/** The two texts an adopted edit alternates between, built once so the driver allocates nothing. */
private val EDITED_TEXTS: List<String> = List(2) { index -> ('a' + index).toString().repeat(EDITED_TEXT_LENGTH) }

/** The text an adopting field starts on, so a field no edit reached carries a text no pass wrote. */
private val UNEDITED_TEXT: String = "-".repeat(EDITED_TEXT_LENGTH)

/** The two values a formatted field alternates between, boxed once so the driver allocates nothing. */
private val FORMATTED_VALUES: List<Any> = listOf(0, 1)

/** The value a formatted field starts on, so the first value written over it is a real change. */
private val UNSET_FORMATTED_VALUE: Any = -1

private const val TEXT_FIELD_ARM = "text field text changed"
private const val ADOPTED_EDIT_ARM = "text field edit adopted"
private const val PASSWORD_FIELD_ARM = "password field text changed"
private const val FORMATTED_FIELD_ARM = "formatted field value changed"
private const val TEXT_AREA_ARM = "text area text changed"
private const val TEXT_PANE_ARM = "text pane text changed"
private const val EDITOR_PANE_ARM = "editor pane markup changed"
