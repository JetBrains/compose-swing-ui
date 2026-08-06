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
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.BorderLayout
import java.util.Calendar
import java.util.Date
import javax.swing.AbstractSpinnerModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SpinnerModel
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener

private class SpinnerValueChannel(
    private val applied: AppliedValue<Any?>,
    private val onValueChange: (Any?) -> Unit,
) {
    fun settledOn(value: Any?) {
        // A model over an empty sequence (ListSpinnerModel) settles on null: there is no selection to
        // report, not a selection of nothing.
        if (value != null) onValueChange(value)
    }

    val listener: ChangeListener =
        ChangeListener { event ->
            val current = (event.source as JSpinner).value
            if (applied.observed(current) && current != null) {
                onValueChange(current)
            }
        }
}

@Composable
private fun rememberSpinnerValueChannel(
    applied: AppliedValue<Any?>,
    onValueChange: (Any?) -> Unit,
): SpinnerValueChannel {
    val callback = rememberUpdatedState(onValueChange)
    return remember(applied) {
        SpinnerValueChannel(applied) { callback.value(it) }
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
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param onValueChange callback invoked with the value the user changes the spinner to - a step, a
 *   scroll, or a committed edit - and with the value the spinner is left on where it cannot hold
 *   [value]; applying a [value] the spinner can hold is not itself reported.
 * @param min the smallest selectable value, or `null` for none.
 * @param max the largest selectable value, or `null` for none.
 * @param step the amount a step changes the value by.
 * @param format the pattern the spinner formats and parses its value with - a `DecimalFormat` pattern;
 *   `null` formats it the way the locale does. A new pattern rebuilds the spinner's own editor around
 *   it.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   an island of the enclosing composition, so it reads the same state and locals the caller does;
 *   `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - the island recomposes rather than being rebuilt, so characters typed but
 *   not committed stand.
 * @throws IllegalArgumentException if both a [format] and an [editor] are declared, or if [value]
 *   falls outside [min]..[max].
 * @see javax.swing.JSpinner
 */
@Composable
public fun Spinner(
    value: Number,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Number) -> Unit = {},
    min: Number? = null,
    max: Number? = null,
    step: Number = 1,
    format: String? = null,
    editor: (@Composable () -> Unit)? = null,
) {
    val applied = rememberAppliedValue<Any?>(value)
    val channel = rememberSpinnerValueChannel(applied) { onValueChange(it as Number) }

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
        set(min) { applied.write { model.minimum = it as Comparable<*>? } }
        set(max) { applied.write { model.maximum = it as Comparable<*>? } }
        set(step) { applied.write { model.stepSize = it } }
        declare(value, applied, JSpinner::getValue, JSpinner::setValue) { settled -> channel.settledOn(settled) }
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
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param onValueChange callback invoked with the value the user changes the spinner to - a step, a
 *   scroll, or a committed edit - and with the value the spinner is left on where it cannot hold
 *   [value]; applying a [value] the spinner can hold is not itself reported.
 * @param start the earliest selectable date, or `null` for none.
 * @param end the latest selectable date, or `null` for none.
 * @param calendarField the `Calendar` field a step moves the value by.
 * @param format the pattern the spinner formats and parses its value with - a `SimpleDateFormat`
 *   pattern; `null` formats it the way the locale does. A new pattern rebuilds the spinner's own
 *   editor around it.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   an island of the enclosing composition, so it reads the same state and locals the caller does;
 *   `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - the island recomposes rather than being rebuilt, so characters typed but
 *   not committed stand.
 * @throws IllegalArgumentException if both a [format] and an [editor] are declared, or if [value]
 *   falls outside [start]..[end].
 * @see javax.swing.JSpinner
 */
@Composable
public fun Spinner(
    value: Date,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Date) -> Unit = {},
    start: Date? = null,
    end: Date? = null,
    @CalendarField calendarField: Int = Calendar.DAY_OF_MONTH,
    format: String? = null,
    editor: (@Composable () -> Unit)? = null,
) {
    val applied = rememberAppliedValue<Any?>(value)
    val channel = rememberSpinnerValueChannel(applied) { onValueChange(it as Date) }
    val model = remember { SpinnerDateModel(value, start, end, calendarField) }

    SpinnerNode(
        model = model,
        modifier = modifier.changeListener(channel.listener),
        format = format,
        editor = editor,
    ) {
        set(start) { applied.write { model.start = it } }
        set(end) { applied.write { model.end = it } }
        set(calendarField) { applied.write { model.calendarField = it } }
        declare(value, applied, JSpinner::getValue, JSpinner::setValue) { settled -> channel.settledOn(settled) }
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
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param onValueChange callback invoked with the value the user changes the spinner to - a step, or a
 *   committed edit landing on one of [items]; applying a [value] the spinner can hold is not itself
 *   reported.
 * @param editor the editing surface the spinner shows in place of its own, composed into the spinner as
 *   an island of the enclosing composition, so it reads the same state and locals the caller does;
 *   `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - the island recomposes rather than being rebuilt, so characters typed but
 *   not committed stand.
 * @see javax.swing.JSpinner
 */
@Composable
public fun <T : Any> Spinner(
    items: List<T>,
    value: T?,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (T) -> Unit = {},
    editor: (@Composable () -> Unit)? = null,
) {
    val applied = rememberAppliedValue<Any?>(value)

    // Every value the channel carries comes from the ListSpinnerModel below, which reports only what its
    // own items hold - the caller's List<T> - so the model can hand back nothing that is not a T. The
    // type is lost only because Swing's SpinnerModel types its value as Any?.
    @Suppress("UNCHECKED_CAST")
    val channel = rememberSpinnerValueChannel(applied) { onValueChange(it as T) }
    val model = remember { ListSpinnerModel(items).also { it.setValue(value) } }

    SpinnerNode(
        model = model,
        modifier = modifier.changeListener(channel.listener),
        format = null,
        editor = editor,
    ) {
        set(items) { applied.write { model.items = it } }
        declare(value, applied, JSpinner::getValue, JSpinner::setValue) { settled -> channel.settledOn(settled) }
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
 *   an island of the enclosing composition, so it reads the same state and locals the caller does;
 *   `null` leaves the editor the one the spinner builds for its own model. A fresh lambda each
 *   recomposition is fine - the island recomposes rather than being rebuilt, so characters typed but
 *   not committed stand.
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
    // The spinner the editor island installs itself on. The island is composed beside the node rather
    // than inside its update, so it reaches the component the same way a tab header reaches its pane.
    val spinner = remember { arrayOfNulls<SpinnerComponent>(1) }
    SwingNode<JSpinner>(
        factory = { SpinnerComponent(model) },
        update = {
            // The island reaches the spinner through the remembered cell, so the cell is filled from the
            // update block rather than the factory: a recycled component is not built again, while the
            // cell beside it is a fresh one every time the content is composed or reused.
            set(spinner) { it[0] = this as SpinnerComponent }
            set(model) { this.model = it }
            // A `JSpinner` editor is built for the model it edits, so the model is part of what the
            // editor is derived from: swapping the model rebuilds the editor around the new one even
            // where the format stands. A composed editor is not derived from any of them and is
            // installed by the island instead, so it withholds the write here.
            set(EditorDeclaration(model, format, editor != null)) { declaration ->
                if (!declaration.composed) this.editor = (this as SpinnerComponent).declaredEditor(declaration)
            }
            applyModifier(modifier)
            this.updateBlock()
        },
    )
    if (editor != null) {
        SpinnerEditorIsland(spinner, parentContext, editor)
    }
}

/**
 * A [SpinnerModel] over a list of [items], stepping from one item to the next without wrapping around at
 * either end. An empty list is legal: the model then holds no value and offers no neighbour in either
 * direction, which is what a spinner reads to render nothing and refuse to step.
 *
 * This widens [javax.swing.SpinnerListModel] on the two points that model is strict about, because a
 * list arriving asynchronously is a normal frame in a declarative API: an empty [items] is allowed
 * rather than rejected, so [getValue] answers `null` while there is nothing to show instead of never
 * being empty in the first place; and [setValue] silently ignores a value absent from [items] instead
 * of throwing.
 */
private class ListSpinnerModel<T>(
    items: List<T>,
) : AbstractSpinnerModel() {
    private var index = 0

    /** Assigning a new list moves the selection to its head. */
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
        if (selected < 0) return
        if (selected != index) {
            index = selected
            fireStateChanged()
        }
    }

    override fun getNextValue(): Any? = items.getOrNull(index + 1)

    override fun getPreviousValue(): Any? = items.getOrNull(index - 1)
}

/**
 * Composes [content] into a panel the spinner shows as its editor, for as long as an editor is declared.
 *
 * The island joins [parentContext], so the editing surface reads the state and the
 * [androidx.compose.runtime.CompositionLocal]s the spinner's own caller does. [content] flows in through
 * [rememberUpdatedState]: a fresh lambda each recomposition recomposes the island rather than rebuilding
 * it, so an edit in progress is not thrown away. Withdrawing the declaration gives the spinner back
 * whichever editor the pass that withdrew it names - its own built around a declared format, or the one
 * it builds for its model unaided.
 */
@Composable
private fun SpinnerEditorIsland(
    spinner: Array<SpinnerComponent?>,
    parentContext: CompositionContext,
    content:
        @Composable
        () -> Unit,
) {
    val current = rememberUpdatedState(content)
    // A `JSpinner` lays its editor out across the whole area beside the arrows, which is what the center
    // region gives whatever the caller composes.
    val host = remember { JPanel(BorderLayout()).apply { isOpaque = false } }
    DisposableEffect(host) {
        val component = spinner[0]
        component?.editor = host
        val handle =
            host.setContentAsInteropHost(parentContext) {
                current.value()
            }
        onDispose {
            handle.dispose()
            // Only where this panel is still what the spinner shows: the same pass that withdrew the
            // declaration may have named a format instead, and the editor built around it is installed
            // while the node's values are applied - before the island is torn down here.
            component?.let { if (it.editor === host) it.editor = it.defaultEditor() }
        }
    }
}

/**
 * What the component a spinner edits its value through is derived from: the model that value belongs to,
 * the pattern the spinner's own editor formats it with, and whether an editor is composed instead. A
 * [composed] editor is the island's to install; with neither it nor a [format], the editor is the one the
 * spinner builds for its model.
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
 * A `JSpinner` that hands out the editor it builds for its own model, so a spinner that has been shown
 * through an editor of someone else's can be given that one back.
 */
private class SpinnerComponent(
    model: SpinnerModel,
) : JSpinner(model) {
    fun defaultEditor(): JComponent = createEditor(this.model)
}
