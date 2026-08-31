@file:JvmMultifileClass
@file:JvmName("TextComponentsKt")

package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.ContentType
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.modifier.listener.hyperlinkListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.EditorKit
import javax.swing.text.html.HTMLDocument

/**
 * Rendered markup: [markup] is source written in the language [contentType] names, and the
 * `JEditorPane` shows what the kit registered for that content type makes of it, so
 * `EditorPane("<h1>Report</h1>", { href -> open(href) }, contentType = "text/html")` renders a
 * heading rather than the characters that spell one.
 *
 * The pane renders and does not report: it holds no text the caller does not declare, and the user
 * cannot type into it. To let the user author rich text, drive a pane with a [DocumentState] through
 * the [state][EditorPane] overload, which owns the document and reports what is typed into it.
 *
 * A link the user activates is reported to [onLinkActivate] as the raw `href`, and nothing is opened -
 * where the link leads is the caller's to decide. [baseUrl] is the HTML document's base: what relative
 * references in the source, `href` and `<img src>` alike, resolve against, and without one they resolve
 * against nothing. A base reaches only a content type that renders into an HTML document, a kit whose
 * model holds no base having nothing to resolve against in the first place.
 *
 * @param markup the source, written in [contentType]'s language, the pane renders
 * @param onLinkActivate callback invoked with the raw `href` of a link the user activates
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param contentType the MIME type naming the kit that parses and renders [markup] (a [ContentType]
 *   MIME string); `text/plain` by default, which shows the source as written
 * @param baseUrl the location relative references in HTML [markup] resolve against; `null` by default
 * @throws IllegalArgumentException if no editor kit is registered for [contentType].
 * @see EditorPane the [DocumentState]-driven overload, for text the user authors
 * @see javax.swing.JEditorPane
 */
@Composable
public fun EditorPane(
    markup: @Nls String,
    onLinkActivate: (String) -> Unit,
    modifier: SwingModifier = SwingModifier,
    @ContentType contentType: String = "text/plain",
    baseUrl: URL? = null,
) {
    RenderedPaneNode(
        markup = markup,
        modifier =
            modifier.hyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) onLinkActivate(event.description)
            },
        contentType = contentType,
        baseUrl = baseUrl,
    )
}

/**
 * An [EditorPane] driven by a raw [HyperlinkListener] instead of an `onLinkActivate` lambda. The
 * listener hears every link event the pane publishes - entered and left as well as activated - and
 * reaches the resolved `URL` and the source element behind each; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param markup the source, written in [contentType]'s language, the pane renders
 * @param hyperlinkListener the listener notified of link events
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param contentType the MIME type naming the kit that parses and renders [markup] (a [ContentType]
 *   MIME string); `text/plain` by default, which shows the source as written
 * @param baseUrl the location relative references in HTML [markup] resolve against; `null` by default
 * @throws IllegalArgumentException if no editor kit is registered for [contentType].
 * @see EditorPane the [DocumentState]-driven overload, for text the user authors
 * @see javax.swing.JEditorPane
 */
@Composable
public fun EditorPane(
    markup: @Nls String,
    hyperlinkListener: HyperlinkListener,
    modifier: SwingModifier = SwingModifier,
    @ContentType contentType: String = "text/plain",
    baseUrl: URL? = null,
) {
    RenderedPaneNode(
        markup = markup,
        modifier = modifier.hyperlinkListener(hyperlinkListener),
        contentType = contentType,
        baseUrl = baseUrl,
    )
}

/**
 * The `JEditorPane` node both rendering [EditorPane] overloads render; [modifier] already carries the
 * pane's link wiring.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun RenderedPaneNode(
    markup: @Nls String,
    modifier: SwingModifier,
    @ContentType contentType: String,
    baseUrl: URL?,
) {
    // The kit is held across recompositions so the pane can tell the one it already has from a new one:
    // the registry clones per call, and a pane asked for its content type answers with what its kit calls
    // itself, which need not be the type it was registered under - `application/rtf` reports `text/rtf`.
    val kit = remember(contentType) { editorKitFor(contentType) }
    SwingNode(
        factory = { JEditorPane() },
        modifier = modifier,
        update = {
            // A JEditorPane publishes link events only while it is not editable, so this pane is fixed
            // non-editable.
            set(false) { this.isEditable = it }
            set(RenderedSource(kit, baseUrl, markup)) { source ->
                // Installing a kit brings its own empty document with it, so the base and the source are
                // written into whichever document the pane is left holding. The three are pushed together
                // for that reason: a change to any one of them re-renders all three.
                if (this.editorKit !== source.kit) this.editorKit = source.kit
                (document as? HTMLDocument)?.base = source.baseUrl
                this.text = source.markup
            }
        },
    )
}

/**
 * The source a rendered [EditorPane] shows: the kit reading its language, base location, and markup.
 *
 * Equality compares [baseUrl] by its external form rather than as a `URL`: `URL.equals` resolves the
 * host to compare addresses, a blocking DNS lookup this class must not trigger on every
 * recomposition's equality check. The kit is compared by identity, not structurally - the registry
 * clones a fresh kit per call, so two renders of the same content type never share an instance to
 * compare equal by.
 */
private class RenderedSource(
    val kit: EditorKit,
    val baseUrl: URL?,
    val markup: @Nls String,
) {
    private val baseHref = baseUrl?.toExternalForm()

    override fun equals(other: Any?): Boolean =
        other is RenderedSource && kit === other.kit && baseHref == other.baseHref && markup == other.markup

    override fun hashCode(): Int = 31 * (31 * System.identityHashCode(kit) + baseHref.hashCode()) + markup.hashCode()
}

/**
 * An [EditorPane] driven by a [DocumentState]. The pane renders the state's own document, so text
 * typed into the pane and edits made through the state are the same content, and the caret is kept
 * two-way with [DocumentState.selection]. The state is the single source of truth; there is no
 * `onValueChange`.
 *
 * The pane renders the state's document through the editor kit the state was built with, so markup is
 * rendered rather than shown as the characters that spell it:
 * ```
 * EditorPane(state = rememberDocumentState("<h1>Report</h1>", contentType = "text/html"))
 * ```
 * A state that adopted a document without naming a kit is rendered through the pane's own, plain text.
 *
 * With [editable] the pane is Swing's rich-text editor: the user authors the rendered content, and what
 * they write is the state's own text. Hyperlinks in an HTML document are live only while the pane is
 * not editable.
 *
 * @param state the hoistable text state the pane renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type into the pane; `true` by default
 * @see javax.swing.JEditorPane
 */
@Composable
public fun EditorPane(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    SwingNode(
        factory = { JEditorPane() },
        modifier = modifier.documentStateBinding(state),
        update = {
            // Install the kit before binding the document: a kit brings its own default document with
            // it, so whichever goes in second is what the pane is left holding. Update blocks run in
            // recorded order and the modifier is applied last, so the binding element installs the
            // state's document over the kit's default once the kit is in place.
            set(state.editorKit) { kit ->
                if (kit == null) this.contentType = "text/plain" else this.editorKit = kit
            }
            set(editable) { this.isEditable = it }
        },
    )
}

/**
 * Editable text carrying graphical attributes - fonts, colors, embedded icons and components - over
 * the styled document a `JTextPane` holds.
 *
 * The binding is reactive in both directions - [value] is pushed onto the pane, and edits the user
 * makes are reported through [onValueChange].
 *
 * This pane is strictly controlled: text the pane settles on that [onValueChange] does not answer with
 * a matching [value] is settled back onto the declared value on the very next pass, so the pane never
 * ends up holding text the caller has not adopted. A callback that filters a keystroke rather than
 * adopting it leaves the caret where that keystroke would have gone.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * pane with the [DocumentState] overload ([TextPane]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text
 * @param onValueChange callback invoked with the pane's new text when the pane is edited; applying
 *   [value] is not itself reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type into the pane; `true` by default
 * @see TextPane the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextPane
 */
@Composable
public fun TextPane(
    value: @Nls String,
    onValueChange: (@Nls String) -> Unit,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    val mirror = rememberMirrorState(value)
    TextPaneNode(
        value = value,
        mirror = mirror,
        modifier = modifier.onTextEdit(mirror, onValueChange),
        editable = editable,
    )
}

/**
 * A [TextPane] driven by a raw [DocumentListener] instead of an `onValueChange` lambda. The listener
 * observes the document the pane currently holds and follows it across a document swap; pass a stable
 * instance (e.g. `remember {}`) to avoid churn. Being attached as-is, it observes every change to that
 * document, including the one that applies [value].
 *
 * This pane is strictly controlled: text the pane settles on that is not followed by [value] moving to
 * match is settled back onto the declared value on the very next pass, so the pane never ends up
 * holding text the caller has not adopted.
 *
 * For incremental editing over a shared `Document`, undo/redo, or observing the text as a flow, drive the
 * pane with the [DocumentState] overload ([TextPane]) and a [DocumentState] from `rememberDocumentState`.
 *
 * @param value the current text
 * @param documentListener the listener notified of document edits
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type into the pane; `true` by default
 * @see TextPane the [DocumentState]-driven overload for large or complex editors
 * @see javax.swing.JTextPane
 */
@Composable
public fun TextPane(
    value: @Nls String,
    documentListener: DocumentListener,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    val mirror = rememberMirrorState(value)
    TextPaneNode(
        value = value,
        mirror = mirror,
        modifier = modifier.documentListener(documentListener).textMirror(mirror),
        editable = editable,
    )
}

/**
 * The `JTextPane` node both [TextPane] overloads render. [value] is settled through [declareText].
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun TextPaneNode(
    value: @Nls String,
    mirror: MirrorState<String>,
    modifier: SwingModifier,
    editable: Boolean,
) {
    SwingNode(
        factory = { JTextPane() },
        modifier = modifier,
        update = {
            declareText(value, mirror)
            set(editable) { this.isEditable = it }
        },
    )
}

/**
 * A [TextPane] driven by a [DocumentState]. The pane renders the state's own document, so text typed
 * into the pane and edits made through the state are the same content, and the caret is kept two-way
 * with [DocumentState.selection]. The state is the single source of truth; there is no
 * `onValueChange`.
 *
 * A `JTextPane` renders a styled document, so [state] must wrap a `StyledDocument` - the model
 * `rememberDocumentState(contentType = "text/rtf")` builds.
 *
 * @param state the hoistable text state, over a `StyledDocument`, the pane renders and drives.
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type into the pane; `true` by default
 * @see javax.swing.JTextPane
 */
@Composable
public fun TextPane(
    state: DocumentState,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = true,
) {
    SwingNode(
        factory = { JTextPane() },
        modifier = modifier.documentStateBinding(state),
        update = {
            set(editable) { this.isEditable = it }
        },
    )
}
