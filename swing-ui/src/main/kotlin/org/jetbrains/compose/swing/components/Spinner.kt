@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.constants.CalendarField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.BorderLayout
import java.util.Calendar
import java.util.Date
import javax.swing.AbstractSpinnerModel
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SpinnerModel
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.DocumentFilter

private class SpinnerValueChannel(
    private val mirror: MirrorState<Any?>,
    private val onValueChange: (Any?) -> Unit,
) {
    fun settledOn(value: Any?) {
        // A model over an empty sequence (ListSpinnerModel) settles on null: there is no selection to
        // report, not a selection of nothing.
        if (value != null) onValueChange(value)
    }

    val listener: ChangeListener =
        ChangeListener { event ->
            mirror.report((event.source as JSpinner).value) { current ->
                // A model over an empty sequence settles on null: there is no selection to report.
                if (current != null) onValueChange(current)
            }
        }
}

@Composable
private fun rememberSpinnerValueChannel(
    mirror: MirrorState<Any?>,
    onValueChange: (Any?) -> Unit,
): SpinnerValueChannel {
    val callback = rememberUpdatedState(onValueChange)
    return remember(mirror) {
        SpinnerValueChannel(mirror) { callback.value(it) }
    }
}

/**
 * A composable wrapper for JSpinner stepping through numbers, over a `SpinnerNumberModel` built from
 * [min], [max] and [step].
 *
 * A `null` [min] or [max] leaves that side unbounded. Tightening either past the current [value] does
 * not move the value, so the spinner can hold one outside its own range until the next value it takes.
 *
 * @param value the current value.
 * @param onValueChange callback invoked with the value the user changes the spinner to - a step, a
 *   scroll, or a committed edit - and with the value the spinner is left on where it cannot hold
 *   [value]; applying a [value] the spinner can hold is not itself reported.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param min the smallest selectable value, or `null` for none.
 * @param max the largest selectable value, or `null` for none.
 * @param step the amount a step changes the value by.
 * @param format the pattern the spinner formats and parses its value with - a `DecimalFormat` pattern;
 *   `null` formats it the way the locale does. A new pattern rebuilds the spinner's own editor around
 *   it.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   a composition of its own joined to the caller's, so it reads the same state and locals the caller
 *   does; `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - that composition recomposes rather than being rebuilt, so characters typed
 *   but not committed stand.
 * @throws IllegalArgumentException if both a [format] and an [editor] are declared, or if [value]
 *   falls outside [min]..[max].
 * @see javax.swing.JSpinner
 */
@Composable
public fun Spinner(
    value: Number,
    onValueChange: (Number) -> Unit,
    modifier: SwingModifier = SwingModifier,
    min: Number? = null,
    max: Number? = null,
    step: Number = 1,
    format: String? = null,
    editor: (@Composable () -> Unit)? = null,
) {
    val mirror = rememberMirrorState<Any?>(value)
    val channel = rememberSpinnerValueChannel(mirror) { onValueChange(it as Number) }

    // The general SpinnerNumberModel constructor takes Comparable minimum/maximum. Number is not itself
    // Comparable, but every concrete Number a caller passes (Int, Double, Long, ...) is Comparable at
    // runtime, so the star-projected cast of the bounds is sound.
    val model = remember { SpinnerNumberModel(value, min as Comparable<*>?, max as Comparable<*>?, step) }

    SpinnerNode(
        model = model,
        modifier = modifier.changeListener(channel.listener),
        format = format,
        editor = editor,
    ) {
        set(min) { mirror.write { model.minimum = it as Comparable<*>? } }
        set(max) { mirror.write { model.maximum = it as Comparable<*>? } }
        set(step) { mirror.write { model.stepSize = it } }
        declare(value, mirror, JSpinner::getValue, JSpinner::setValue) { settled -> channel.settledOn(settled) }
    }
}

/**
 * A composable wrapper for JSpinner stepping through dates by [calendarField], over a
 * `SpinnerDateModel` built from [start] and [end].
 *
 * A `null` [start] or [end] leaves that side unbounded. Tightening either past the current [value]
 * does not move the value, so the spinner can hold one outside its own range until the next value it
 * takes.
 *
 * @param value the current value.
 * @param onValueChange callback invoked with the value the user changes the spinner to - a step, a
 *   scroll, or a committed edit - and with the value the spinner is left on where it cannot hold
 *   [value]; applying a [value] the spinner can hold is not itself reported.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param start the earliest selectable date, or `null` for none.
 * @param end the latest selectable date, or `null` for none.
 * @param calendarField the `Calendar` field a step moves the value by.
 * @param format the pattern the spinner formats and parses its value with - a `SimpleDateFormat`
 *   pattern; `null` formats it the way the locale does. A new pattern rebuilds the spinner's own
 *   editor around it.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   a composition of its own joined to the caller's, so it reads the same state and locals the caller
 *   does; `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - that composition recomposes rather than being rebuilt, so characters typed
 *   but not committed stand.
 * @throws IllegalArgumentException if both a [format] and an [editor] are declared, or if [value]
 *   falls outside [start]..[end].
 * @see javax.swing.JSpinner
 */
@Composable
public fun Spinner(
    value: Date,
    onValueChange: (Date) -> Unit,
    modifier: SwingModifier = SwingModifier,
    start: Date? = null,
    end: Date? = null,
    @CalendarField calendarField: Int = Calendar.DAY_OF_MONTH,
    format: String? = null,
    editor: (@Composable () -> Unit)? = null,
) {
    val mirror = rememberMirrorState<Any?>(value)
    val channel = rememberSpinnerValueChannel(mirror) { onValueChange(it as Date) }
    val model = remember { SpinnerDateModel(value, start, end, calendarField) }

    SpinnerNode(
        model = model,
        modifier = modifier.changeListener(channel.listener),
        format = format,
        editor = editor,
    ) {
        set(start) { mirror.write { model.start = it } }
        set(end) { mirror.write { model.end = it } }
        set(calendarField) { mirror.write { model.calendarField = it } }
        declare(value, mirror, JSpinner::getValue, JSpinner::setValue) { settled -> channel.settledOn(settled) }
    }
}

/**
 * A composable wrapper for JSpinner stepping through [items], one to the next without wrapping at
 * either end.
 *
 * An empty [items] is allowed: the spinner then holds no value and steps neither forward nor back
 * until items arrive. Assigning a new [items] moves the selection to its head.
 *
 * @param items the values the spinner steps through, in order.
 * @param value the current value, or `null` for no selection - the state an empty [items] leaves the
 *   spinner in, and the one to declare while a list is still loading. Declaring `null` against an
 *   [items] that does hold values settles the spinner on the head and reports it through
 *   [onValueChange], since a spinner over items always shows one of them.
 * @param onValueChange callback invoked with the value the user changes the spinner to - a step, or a
 *   committed edit landing on one of [items], and with the value the spinner is left on where it cannot
 *   hold [value]; applying a [value] the spinner can hold is not itself
 *   reported.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   a composition of its own joined to the caller's, so it reads the same state and locals the caller
 *   does; `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - that composition recomposes rather than being rebuilt, so characters typed
 *   but not committed stand.
 * @see javax.swing.JSpinner
 */
@Composable
public fun <T : Any> Spinner(
    items: List<T>,
    value: T?,
    onValueChange: (T) -> Unit,
    modifier: SwingModifier = SwingModifier,
    editor: (@Composable () -> Unit)? = null,
) {
    val mirror = rememberMirrorState<Any?>(value)
    val declaredItems = rememberDeclaredList(items)

    // Every value the channel carries comes from the ListSpinnerModel below, which reports only what its
    // own items hold - the caller's List<T>, copied - so the model can hand back nothing that is not a T.
    // The type is lost only because Swing's SpinnerModel types its value as Any?.
    @Suppress("UNCHECKED_CAST")
    val channel = rememberSpinnerValueChannel(mirror) { onValueChange(it as T) }
    val model = remember { ListSpinnerModel(declaredItems).also { it.setIfHeld(value) } }

    SpinnerNode(
        model = model,
        modifier = modifier.changeListener(channel.listener),
        format = null,
        editor = editor,
    ) {
        set(declaredItems) { mirror.write { model.items = it } }
        declare(
            value,
            mirror,
            JSpinner::getValue,
            write = { declared -> model.setIfHeld(declared) },
        ) { settled -> channel.settledOn(settled) }
    }
}

/**
 * A `JSpinner` over a caller-owned [model], driven by a raw [changeListener] rather than by a value the
 * spinner reports back. The [changeListener] is attached as-is and removed on the same instance; pass a
 * stable instance (e.g. `remember {}`) to avoid churn. Swapping the [model] instance installs the new
 * model verbatim.
 *
 * @param model the model the spinner renders.
 * @param changeListener the listener notified when the spinner's value changes.
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param format the pattern the spinner formats and parses its values with - a `DecimalFormat` pattern
 *   over a number model, a `SimpleDateFormat` pattern over a date model; `null` formats them the way the
 *   locale does. A new pattern rebuilds the spinner's own editor around it.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   a composition of its own joined to the caller's, so it reads the same state and locals the caller
 *   does; `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - that composition recomposes rather than being rebuilt, so characters typed
 *   but not committed stand.
 * @throws IllegalArgumentException if both a [format] and an [editor] are declared, or if a [format] is
 *   declared over a model that is neither a number's nor a date's.
 * @see javax.swing.JSpinner
 */
@Composable
public fun Spinner(
    model: SpinnerModel,
    changeListener: ChangeListener,
    modifier: SwingModifier = SwingModifier,
    format: String? = null,
    editor: (
        @Composable
        () -> Unit
    )? = null,
) {
    SpinnerNode(
        model = model,
        modifier = modifier.changeListener(changeListener),
        format = format,
        editor = editor,
    )
}

/**
 * The `JSpinner` node both [Spinner] overloads render. [modifier] already carries every listener the
 * spinner needs, the caller's own raw listener included where a raw overload is driving it.
 */
@Composable
private fun SpinnerNode(
    model: SpinnerModel,
    modifier: SwingModifier,
    format: String?,
    editor: (
        @Composable
        () -> Unit
    )?,
    updateBlock: SwingNodeUpdater<JSpinner>.() -> Unit = {},
) {
    // Both name the surface the spinner edits through, and a composed editor renders the value itself, so
    // a pass declaring both has no answer to which of them the spinner is to show.
    require(format == null || editor == null) {
        "Spinner takes either a format or an editor, not both, but was declared with the format " +
            "\"$format\" and an editor"
    }
    // A pattern is a number's or a date's, and only those two models have a standard editor to build
    // around one. Naming the mismatch where the declaration is beats letting an editor reject the model
    // under it deep in the pass that installs it.
    require(format == null || model is SpinnerNumberModel || model is SpinnerDateModel) {
        "Spinner format \"$format\" applies to a number or a date model, but the model is a " +
            "${model.javaClass.name}; declare an editor of your own to render it"
    }
    val parentContext = rememberCompositionContext()
    // The panel a composed editor renders into, remembered here so the same instance is handed both to
    // the modifier below, which installs it as the spinner's editor, and to the editor composition beside
    // the node, which fills it.
    val editorPanel = if (editor != null) remember { JPanel(BorderLayout()).apply { isOpaque = false } } else null
    SwingNode<JSpinner>(
        factory = { SpinnerComponent(model) },
        update = {
            set(model) { this.model = it }
            // A `JSpinner` editor is built for the model it edits, so the model is part of what the
            // editor is derived from: swapping the model rebuilds the editor around the new one even
            // where the format stands. A composed editor is not derived from any of them and is
            // installed by the editor composition instead, so it withholds the write here.
            set(EditorDeclaration(model, format, editor != null)) { declaration ->
                if (!declaration.composed) this.editor = (this as SpinnerComponent).declaredEditor(declaration)
            }
            applyModifier(if (editorPanel != null) modifier.spinnerEditor(editorPanel) else modifier)
            this.updateBlock()
        },
    )
    if (editor != null && editorPanel != null) {
        SpinnerEditorComposition(editorPanel, parentContext, editor)
    }
}

/**
 * A [SpinnerModel] over a list of [items], stepping from one item to the next without wrapping around at
 * either end. An empty list is legal: the model then holds no value and offers no neighbor in either
 * direction, which is what a spinner reads to render nothing and refuse to step.
 *
 * This widens [javax.swing.SpinnerListModel] on the one point a list arriving asynchronously needs
 * widened: an empty [items] is allowed rather than rejected, so [getValue] answers `null` while there is
 * nothing to show instead of never being empty in the first place. [setValue] otherwise keeps that
 * model's own contract, throwing for a value [items] does not hold rather than absorbing it silently -
 * the editor a spinner over items shows relies on that exception to revert an edit it cannot resolve to
 * one of them.
 */
private class ListSpinnerModel<T>(
    items: List<T>,
) : AbstractSpinnerModel() {
    private var index = 0

    /**
     * The items the spinner steps through. Assigning a new list moves the selection to its head.
     *
     * Every read the model answers - value, neighbors, the index a written value lands on - comes from
     * this list, so it must be one no caller holds: what the model reports is then always what the
     * spinner was last told, and a read during paint touches no caller state.
     */
    var items: List<T> = items
        set(value) {
            field = value
            index = 0
            fireStateChanged()
        }

    override fun getValue(): Any? = items.getOrNull(index)

    override fun setValue(value: Any?) {
        // A spinner over no items renders null and hands that same null back through its editor.
        if (items.isEmpty() && value == null) return
        val selected = items.indexOfFirst { it == value }
        require(selected >= 0) { "\"$value\" is not one of the spinner's items" }
        if (selected != index) {
            index = selected
            fireStateChanged()
        }
    }

    override fun getNextValue(): Any? = items.getOrNull(index + 1)

    override fun getPreviousValue(): Any? = items.getOrNull(index - 1)

    /**
     * The first item at or after the one held whose text starts with [prefix], searched forwards and
     * wrapping past the end of [items], or `null` where none does and where [items] is empty. This is
     * what [javax.swing.SpinnerListModel] answers its editor with, matched the same way: case-sensitive,
     * skipping an item that is `null`.
     */
    fun findNextMatch(prefix: String): Any? {
        var candidate = index
        repeat(items.size) {
            val item = items[candidate]
            if (item != null && item.toString().startsWith(prefix)) return item
            candidate = (candidate + 1) % items.size
        }
        return null
    }
}

/**
 * Writes [value] to this model, or - given `null` over a non-empty [items] - the head, which is what a
 * spinner over items always shows once it has any: `null` only ever means no selection has been declared,
 * never that the spinner is to show nothing while items sit right there. A [value] neither [items] nor
 * that fallback holds is left alone: [setValue] throws for one, so a caller that can offer only what a
 * pass declares checks first rather than catching the model's own exception.
 */
private fun ListSpinnerModel<*>.setIfHeld(value: Any?) {
    val target = value ?: items.firstOrNull()
    if (target == null || items.any { it == target }) setValue(target)
}

/**
 * The editor a spinner over a [ListSpinnerModel] shows itself through: an editable field whose formatter
 * resolves committed text back to one of the model's items, so - unlike the read-only field a
 * [JSpinner.DefaultEditor] otherwise builds for a model it does not recognize - a typed edit reaches the
 * model rather than only sitting in the field.
 */
private class ItemsEditor(
    spinner: JSpinner,
) : JSpinner.DefaultEditor(spinner) {
    init {
        textField.isEditable = true
        textField.formatterFactory = DefaultFormatterFactory(ItemsFormatter(spinner))
    }
}

/**
 * Resolves text to whichever of [spinner]'s items renders as it, and back. [spinner]'s model is read
 * live on every resolution rather than captured once, since the items a spinner over items shows can
 * change while an edit is in progress.
 */
private class ItemsFormatter(
    private val spinner: JSpinner,
) : JFormattedTextField.AbstractFormatter() {
    private val filter = Filter()

    override fun stringToValue(text: String?): Any? =
        (spinner.model as ListSpinnerModel<*>).items.firstOrNull { it?.toString() == text } ?: text

    override fun valueToString(value: Any?): String = value?.toString().orEmpty()

    override fun getDocumentFilter(): DocumentFilter = filter

    /**
     * Completes text typed at the end of the field to the first item starting with it, and selects the
     * completed tail so the next keystroke replaces it - what the editor a bare
     * [javax.swing.SpinnerListModel] builds does, and what makes a distinguishing prefix enough to
     * commit. An edit anywhere but the end, and a prefix no item starts with, reach the document as
     * they stand.
     */
    private inner class Filter : DocumentFilter() {
        override fun replace(
            bypass: FilterBypass,
            offset: Int,
            length: Int,
            text: String?,
            attributes: AttributeSet?,
        ) {
            val completion = completionFor(bypass, offset, length, text)
            if (completion == null) {
                super.replace(bypass, offset, length, text, attributes)
                return
            }
            bypass.remove(0, offset + length)
            bypass.insertString(0, completion, null)
            formattedTextField?.select(offset + text.orEmpty().length, completion.length)
        }

        override fun insertString(
            bypass: FilterBypass,
            offset: Int,
            string: String?,
            attributes: AttributeSet?,
        ): Unit = replace(bypass, offset, 0, string, attributes)

        /**
         * The item text [text] completes to, or `null` for an edit not to complete. The edit has to land
         * at the end of the field: completing one in the middle would take the text after it away.
         */
        private fun completionFor(
            bypass: FilterBypass,
            offset: Int,
            length: Int,
            text: String?,
        ): String? {
            if (text == null || offset + length != bypass.document.length) return null
            val prefix = bypass.document.getText(0, offset) + text
            return (spinner.model as ListSpinnerModel<*>).findNextMatch(prefix)?.toString()
        }
    }
}

/**
 * Composes [content] into [panel], which the modifier chain installs as the spinner's editor for as long
 * as an editor is declared.
 *
 * The composition joins [parentContext], so the editing surface reads the state and the
 * [androidx.compose.runtime.CompositionLocal]s the spinner's own caller does. [content] flows in through
 * [rememberUpdatedState]: a fresh lambda each recomposition recomposes it rather than rebuilding it, so an
 * edit in progress is not thrown away.
 */
@Composable
private fun SpinnerEditorComposition(
    panel: JPanel,
    parentContext: CompositionContext,
    content:
        @Composable
        () -> Unit,
) {
    val current = rememberUpdatedState(content)
    DisposableEffect(panel) {
        val handle =
            panel.setContentAsInteropHost(parentContext) {
                current.value()
            }
        onDispose {
            handle.dispose()
        }
    }
}

/**
 * What the component a spinner edits its value through is derived from: the model that value belongs to,
 * the pattern the spinner's own editor formats it with, and whether an editor is composed instead. A
 * [composed] editor is the editor composition's to install; with neither it nor a [format], the editor is
 * the one the spinner builds for its model.
 */
private data class EditorDeclaration(
    val model: SpinnerModel,
    val format: String?,
    val composed: Boolean,
)

/**
 * The editor [declaration] names for this spinner: its own built around a declared pattern, or - with no
 * pattern - the one it builds for its model unaided. A pattern is only ever declared over a number or a
 * date model, whose standard editors are the two that read one.
 */
private fun SpinnerComponent.declaredEditor(declaration: EditorDeclaration): JComponent {
    val format = declaration.format
    return when {
        format == null -> defaultEditor()
        declaration.model is SpinnerDateModel -> JSpinner.DateEditor(this, format)
        else -> JSpinner.NumberEditor(this, format)
    }
}

/**
 * Installs [panel] as the spinner's editor while this chain applies it, restoring the spinner's own
 * editor when it leaves.
 */
private fun SwingModifier.spinnerEditor(panel: JPanel): SwingModifier = this then SpinnerEditorElement(panel)

/**
 * The [SwingModifier.NodeElement] backing [spinnerEditor].
 *
 * [panel] is compared by identity, so this is not a data class: it is the editor the node installs on
 * the component, not a value read back from it, and a panel that looks equal to another is still a
 * different one to hand the spinner over to.
 */
private class SpinnerEditorElement(
    private val panel: JPanel,
) : SwingModifier.NodeElement<SpinnerComponent, SpinnerEditorElement.Node>() {
    override fun equals(other: Any?): Boolean = other is SpinnerEditorElement && panel === other.panel

    override fun hashCode(): Int = System.identityHashCode(panel)

    override val targetType: Class<SpinnerComponent> get() = SpinnerComponent::class.java

    override fun create(): Node = Node(panel)

    // The panel comes from a `remember` with no keys, so it is the same instance for as long as this
    // element occupies its slot; an update call would have nothing new to push.
    override fun update(node: Node): Unit = Unit

    /**
     * Installs [panel] as the spinner's editor on attach, and gives the spinner back its own editor on
     * detach - unless a later editor has since taken [panel]'s place.
     */
    class Node(
        private val panel: JPanel,
    ) : SwingModifier.Node<SpinnerComponent>() {
        override fun onAttach() {
            component.editor = panel
        }

        override fun onDetach() {
            if (component.editor === panel) component.editor = component.defaultEditor()
        }
    }
}

/**
 * A `JSpinner` that hands out the editor it builds for its own model, so a spinner that has been shown
 * through an editor of someone else's can be given that one back.
 */
private class SpinnerComponent(
    model: SpinnerModel,
) : JSpinner(model) {
    fun defaultEditor(): JComponent = createEditor(this.model)

    // JSpinner falls back to a read-only field for a model it does not recognize, which a
    // ListSpinnerModel is: it stands in for javax.swing.SpinnerListModel without being one.
    override fun createEditor(model: SpinnerModel): JComponent =
        if (model is ListSpinnerModel<*>) ItemsEditor(this) else super.createEditor(model)
}
