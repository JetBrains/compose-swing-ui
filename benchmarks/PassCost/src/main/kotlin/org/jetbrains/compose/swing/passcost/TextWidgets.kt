package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.JTextComponent
import javax.swing.text.NumberFormatter

/**
 * A text field settled on the text [text] holds, taking edits through the callback overload - the shape
 * a caller reaches for, so the pass pays for the reporting the overload wires as well as the write.
 */
@Composable
internal fun DeclaredTextField(
    text: State<String>,
    onCompose: () -> Unit,
) {
    onCompose()
    TextField(value = text.value, onValueChange = {})
}

/** A password field settled on the characters [characters] holds, taking edits through the callback. */
@Composable
internal fun DeclaredPasswordField(
    characters: State<CharArray>,
    onCompose: () -> Unit,
) {
    onCompose()
    PasswordField(value = characters.value, onValueChange = {})
}

/**
 * A formatted field settled on the value [value] holds, over a formatter of its own.
 *
 * A formatter is installed on one field at a time and uninstalls itself from the field it was on as the
 * next field takes it, so a factory shared between the fields of one tree would leave every field but
 * the last without one. Holding the factory across passes is what keeps a pass paying for the value
 * alone: a factory of a new identity is installed again, and installing one regenerates the characters.
 */
@Composable
internal fun DeclaredFormattedTextField(
    value: State<Any?>,
    onCompose: () -> Unit,
) {
    onCompose()
    val factory =
        remember {
            DefaultFormatterFactory(NumberFormatter().apply { valueClass = Int::class.javaObjectType })
        }
    FormattedTextField(value = value.value, formatterFactory = factory, onValueChange = {})
}

/** A text area settled on the text [text] holds, taking edits through the callback. */
@Composable
internal fun DeclaredTextArea(
    text: State<String>,
    onCompose: () -> Unit,
) {
    onCompose()
    TextArea(value = text.value, onValueChange = {})
}

/** A text pane settled on the text [text] holds, taking edits through the callback. */
@Composable
internal fun DeclaredTextPane(
    text: State<String>,
    onCompose: () -> Unit,
) {
    onCompose()
    TextPane(value = text.value, onValueChange = {})
}

/** An editor pane rendering the markup [markup] holds through the plain kit, which reports nothing. */
@Composable
internal fun RenderingEditorPane(
    markup: State<String>,
    onCompose: () -> Unit,
) {
    onCompose()
    EditorPane(markup = markup.value, onLinkActivate = {}, contentType = "text/plain")
}

/**
 * A text field the user edits and the caller adopts: [text] is declared onto the field, and an edit the
 * field reports is written straight back into [text], so the next pass declares what the field already
 * holds.
 *
 * That is the shape a controlled field takes while someone types into it, and it settles without writing
 * anything: the declaration moved, and the widget is already on it.
 *
 * [adopt] is what the caller does with an edit the field reports; [captured] collects the fields as they
 * attach, so the arm's driver can edit them the way a user does.
 */
@Composable
internal fun AdoptingTextField(
    text: State<String>,
    adopt: (String) -> Unit,
    captured: CapturedFields,
    onCompose: () -> Unit,
) {
    onCompose()
    TextField(
        value = text.value,
        onValueChange = adopt,
        modifier = remember(captured) { SwingModifier.capturing(captured) },
    )
}

/**
 * The fields an arm's driver edits, collected as they attach. It is read by index rather than iterated,
 * so a driver reaching every field allocates nothing.
 */
@Stable
internal class CapturedFields {
    private val fields = ArrayList<JTextComponent>()

    val size: Int get() = fields.size

    operator fun get(index: Int): JTextComponent = fields[index]

    fun attached(field: JTextComponent) {
        fields += field
    }

    fun detached(field: JTextComponent) {
        fields -= field
    }
}

/** Records the component the chain is applied to in [captured] for as long as the element stands. */
private class CapturingNode(
    private val captured: CapturedFields,
) : SwingModifier.Node<JTextComponent>() {
    override fun onAttach() {
        captured.attached(component)
    }

    override fun onDetach() {
        captured.detached(component)
    }
}

/**
 * Collects the components of the chain it is on into one list. Two elements collecting into the same
 * list are the same declaration, so a chain rebuilt around it is adopted and no field is collected twice.
 */
private class CapturingElement(
    private val captured: CapturedFields,
) : SwingModifier.NodeElement<JTextComponent, CapturingNode>() {
    override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

    override fun create(): CapturingNode = CapturingNode(captured)

    override fun update(node: CapturingNode): Unit = Unit

    override fun equals(other: Any?): Boolean = other is CapturingElement && captured === other.captured

    override fun hashCode(): Int = System.identityHashCode(captured)
}

private fun SwingModifier.capturing(captured: CapturedFields): SwingModifier = then(CapturingElement(captured))
