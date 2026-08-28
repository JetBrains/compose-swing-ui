# Components

This is the catalog of what Compose Swing UI ships: every component family, grouped the way you
reach for one, with the parameters that decide how it behaves. The KDoc on each function is the
per-parameter reference. The concepts behind the binding are in
[`ARCHITECTURE.md`](ARCHITECTURE.md), and building a component of your own is
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

<!--- INCLUDE .*content.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.components.*
import org.jetbrains.compose.swing.components.button.*
import org.jetbrains.compose.swing.components.layout.*
import org.jetbrains.compose.swing.components.selection.*
import org.jetbrains.compose.swing.components.text.*
import org.jetbrains.compose.swing.modifier.*
import org.jetbrains.compose.swing.modifier.interaction.*
import org.jetbrains.compose.swing.modifier.layout.*
import org.jetbrains.compose.swing.window.*
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.Insets
import javax.swing.*

@Composable
fun Example() {
----- SUFFIX .*content.*
}
----- INCLUDE .*app.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.components.*
import org.jetbrains.compose.swing.components.button.*
import org.jetbrains.compose.swing.components.layout.*
import org.jetbrains.compose.swing.window.*
import java.awt.Dimension

fun main() =
-->

Two things hold for everything below.

**Every parameter is reapplied on recomposition.** Pass state to a component and the widget follows
that state for the component's whole life.

**Every default matches the widget's own.** A component that leaves a parameter unset behaves
exactly as the freshly constructed Swing widget does, so the defaults quoted here are Swing's, not
this library's. A parameter with no single correct default - `CheckBox`'s `checked`, or a
`ComboBox` built over `items` rather than a `ComboBoxModel` - is required instead.

Cross-cutting configuration - colors, fonts, borders, sizes, tooltips, accessibility, keyboard
bindings, data transfer, raw listeners - arrives as a `SwingModifier` chain passed as `modifier`,
described in
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md#styling-with-a-modifier-swingmodifier-parameter).

---

## How a component takes state

Which shape a component uses tells you where its state lives. The declared value and the hoistable
holder are described in [`ARCHITECTURE.md`](ARCHITECTURE.md#shapes-for-state-the-user-can-change).

**A value and a callback.** `TextField(value, onValueChange = ...)`, `CheckBox(text, checked,
onCheckedChange = ...)`, `TabbedPane(selectedIndex, onSelectedIndexChange = ...)`.

**A hoisted state holder.** `DocumentState`, `FormattedValueState`, `WindowState`, `DialogState`,
`InternalFrameState`, `ScrollState`, `ListState`, `TableState`, `TreeState`. See
[Hoisted state](#hoisted-state).

**A raw Swing model.** `ComboBox(model)`, `ListBox(model)`, `Table(model)`, `Tree(model)`,
`Spinner(model, changeListener)`, `Slider(model)`, `ProgressBar(model)`. When you already have a
`ComboBoxModel`, `TableModel`, `TreeModel` or `BoundedRangeModel`, hand it over and the component
renders it. The model stays yours - a `BoundedRangeModel` you hand to both `Slider` and `ProgressBar`
is what lets a bar track a slider without either owning the value.

Most components that take a lambda callback also offer an overload taking the corresponding raw Swing
listener instead, for when you already hold a listener object: `ActionListener` (the buttons, the
menu items, `ComboBox`), `ChangeListener` (`Slider`, `Spinner`, `TabbedPane`), `DocumentListener`
(`TextField`, `TextArea`, `TextPane`, `PasswordField`), `HyperlinkListener` (`EditorPane`),
`ListSelectionListener` (`ListBox`, `Table`), `TableColumnModelListener` (a `Table`'s column layout),
`RowSorterListener` (a `Table`'s sort order), `TreeSelectionListener`, `TreeExpansionListener` and
`TreeWillExpandListener` (`Tree`), `PropertyChangeListener` (`FormattedTextField`, `SplitPane`) and
`InternalFrameListener` (`InternalFrame`). The KDoc on each overload says which events it carries.

`javax.swing.Action` extends `ActionListener`, so an `Action` you already own is one of those listeners
and the raw-listener overloads take it as it is: `Button("Save", actionListener = save)`,
`MenuItem("Save", actionListener = save)`.

The same listeners are also modifier elements, which is how you hear events from a component the
catalog does not wrap. `actionListener` reaches everything that publishes an action event - an
`AbstractButton`, a `JTextField`, a `JComboBox`, a `JFileChooser`, and the AWT `Button`, `TextField`
and `List` - and `changeListener` everything that publishes a change event - a `JSlider`, a `JSpinner`,
a `JTabbedPane`, a `JProgressBar`, an `AbstractButton`, a `JViewport` and a `JColorChooser`, which
publishes through the `selectionModel` it holds when the listener is installed.

### Declaring a selection, or leaving it alone

`ListBox`, `Table` and `Tree` take their selection (and a `Tree` its expansion) as a nullable
parameter, and the two cases differ.

Declare it - `selectedIndices = mine` - and the selection is the composition's state, reapplied on
every pass: it survives new items, and a user change your callback does not adopt is undone. Leave it
`null` and the selection belongs to the user alone: never imposed, and carried across new items; where
the new items are too few to hold it, what falls outside them leaves and the callback reports what is
left.

A `ListBox`, a `Table` and a `Tree` also take a `ListState`, a `TableState` or a `TreeState` in place
of those parameters, which holds the same facets as two-way state, reveals a row and reads back what
the widget shows besides; see [Lists and tables](#lists-and-tables).

Either way the callback reports the user's changes only, once per settled change - dragging across
rows produces one call at the end, and rendering fresh items produces none. A `ComboBox` is always
controlled instead: its `selectedItem` names the chosen item, `null` names none, and so does an item
the current `items` do not contain. The selection names an item and not a position, so items that
compare equal are one and the same selection.

---

## Text

| Component            | What it is                                                                                                      |
|----------------------|-----------------------------------------------------------------------------------------------------------------|
| `Label`              | A text label over `JLabel`. Alignment, icon and text position come from the modifier chain.                     |
| `TextField`          | One line of editable text over `JTextField`.                                                                    |
| `PasswordField`      | A field over `JPasswordField` whose value is a `CharArray` rather than a `String`.                              |
| `FormattedTextField` | A field over `JFormattedTextField` that parses and formats a typed value through an `AbstractFormatterFactory`. |
| `TextArea`           | Multi-line plain text over `JTextArea`.                                                                         |
| `TextPane`           | Styled multi-line text over `JTextPane`.                                                                        |
| `EditorPane`         | Markup over `JEditorPane`, rendered through the editor kit its `contentType` names.                             |

`TextField`, `TextArea`, `TextPane` and `PasswordField` each come in three forms: a `value` plus
`onValueChange`, a `value` plus a `DocumentListener`, and a [`DocumentState`](#documentstate) that
owns the document outright.

The `value` forms are strictly controlled, settled like every other
[declared value](ARCHITECTURE.md#shapes-for-state-the-user-can-change). `FormattedTextField` holds its
committed value the same way. For `TextField`, `TextArea` and `TextPane`, a callback that filters a
keystroke rather than adopting it leaves the caret where that keystroke would have gone.

Reach for the hoisted `DocumentState` form where you do not need the text on every keystroke: the
state owns the document, so there is no un-adopted edit to settle at all, and it is also what gives
you incremental edits over a large document, undo/redo, and the text as observable state.
`TextField(value, onValueChange)` is for the caller who genuinely drives the value.

`EditorPane` comes in two: a `markup` string it renders and reports nothing back from, and a
[`DocumentState`](#documentstate) for text the user authors. Its `contentType` names the editor kit
that parses the markup and defaults to `"text/plain"`. A rendered pane is not editable - it holds only
what you declare - and reports an activated link to `onLinkActivate` as the raw `href` without opening
anything; pass a `baseUrl` for relative `href`s and `<img src>`s to resolve against, or a
`HyperlinkListener` in place of the lambda to hear hover events and reach the resolved `URL`.

`columns` and `rows` default to `0`, which sizes the field from its content rather than a column
count. `editable` defaults to `true`, `TextArea`'s `tabSize` defaults to `8`, and
`FormattedTextField`'s `focusLostBehavior` to `JFormattedTextField.COMMIT_OR_REVERT`.

`FormattedTextField` also takes an `onEditValidChange` callback, reporting whether the in-progress
edit currently parses. Its hoisted form, a [`FormattedValueState`](#formattedvaluestate) from
`rememberFormattedValueState`, carries that as observable `isEditValid` beside the committed `value`
the field drives, and its `commit()` lets a caller outside the field - a dialog's OK button - take the
pending edit where it stands. A state-driven field has no `onValueChange` and no `onEditValidChange`:
the state is the single source of truth.

```kotlin
var query by remember { mutableStateOf("") }
var notes by remember { mutableStateOf("") }
var amount: Any? by remember { mutableStateOf<Any?>(0.0) }
val secret = rememberDocumentState()

TextField(value = query, onValueChange = { query = it }, columns = 24)
TextArea(value = notes, onValueChange = { notes = it }, rows = 6, columns = 40, lineWrap = true)
PasswordField(state = secret, echoChar = '*', columns = 16)
FormattedTextField(
    value = amount,
    onValueChange = { amount = it },
    formatterFactory = DefaultFormatterFactory(NumberFormatter(DecimalFormat("#,##0.00"))),
    columns = 12,
)
EditorPane(
    markup = "<h1>Report</h1><p>See the <a href=\"/q3\">details</a>.</p>",
    onLinkActivate = { href -> open(href) },
    contentType = "text/html",
)
```

<!--- CLEAR -->

---

## Buttons and choices

| Component      | What it is                                                                                              |
|----------------|---------------------------------------------------------------------------------------------------------|
| `Button`       | A push button over `JButton`, with `onClick`.                                                           |
| `ToggleButton` | A button over `JToggleButton` that stays in, with `selected`/`onSelectedChange`.                        |
| `CheckBox`     | A checkbox over `JCheckBox`, with `checked`/`onCheckedChange`.                                          |
| `RadioButton`  | One radio button over `JRadioButton`, with `selected`/`onSelectedChange`.                               |
| `RadioGroup`   | A set of mutually exclusive radio buttons declared as `option(...)` calls, selected by index.           |
| `ComboBox`     | A drop-down over `JComboBox`, optionally editable, optionally rendering each item as a composable cell. |
| `Slider`       | A slider over `JSlider`, with ticks and a label table.                                                  |
| `Spinner`      | A stepper over `JSpinner` over a number, a date or a list of items, or a raw `SpinnerModel`.            |
| `ProgressBar`  | A determinate or indeterminate bar over `JProgressBar`.                                                 |
| `Separator`    | A divider over `JSeparator` between the items of any container.                                         |

`Button` reports a click; the two-state controls - `CheckBox`, `ToggleButton` and a standalone
`RadioButton` - take the state in and report the state the user asked for, in both directions, so a
toggle the caller does not adopt goes back where it was.

```kotlin
var wrap by remember { mutableStateOf(false) }
var pinned by remember { mutableStateOf(false) }

Button("Save", onClick = ::save, modifier = SwingModifier.icon(saveIcon))
CheckBox("Word wrap", checked = wrap, onCheckedChange = { wrap = it })
ToggleButton("Pin", selected = pinned, onSelectedChange = { pinned = it })
```

<!--- CLEAR -->

A `RadioGroup` owns the button group, so you declare the options and the selected index rather than
wiring exclusivity yourself. The index is the composition's state on every pass, so a pick the caller
does not adopt goes back to the declared option - including the option the group cleared without it
being clicked, which a grouped button loses in silence. `RadioButtonMenuGroup` behaves the same way in
a menu. Individual `RadioButton`s remain available for a layout the group's own axis does not cover.

```kotlin
var theme by remember { mutableStateOf(0) }
RadioGroup(selectedIndex = theme, onSelectionChange = { theme = it }) {
    option("Light")
    option("Dark")
    option("Follow system")
}
```

<!--- KNIT example-components-content-01.kt -->

An editable `ComboBox` has two outputs: `onSelectionChange` for a choice from the list, and
`onValueCommit` for text typed into the editor. `itemContent` replaces the rendered cell with a
composable, which receives the item and, through its scope, the row's `index`, `isSelected` and
`cellHasFocus`. `editable` defaults to `false` and `maximumRowCount` - the rows the popup shows
before scrolling - to `8`.

```kotlin
val presets = listOf("Small", "Medium", "Large")
var selected by remember { mutableStateOf<String?>(presets.first()) }
var typed by remember { mutableStateOf("") }

ComboBox(
    items = presets,
    selectedItem = selected,
    onSelectionChange = { selected = it },
    editable = true,
    onValueCommit = { typed = it },
) { item ->
    Label(if (isSelected) "> $item" else item)
}
```

<!--- KNIT example-components-content-02.kt -->

`Slider` and `ProgressBar` both range over `0`..`100` by default and are horizontal, as is
`Separator`. A slider's `labels` map declares the label table that `paintLabels` then paints; a
progress bar's `stringPainted` alone gives a percentage readout, and `string` overrides the text it
paints. A drag reaches `onValueChange` a value at a time, for every value it passes through;
`onValueSettled` hears only the value the drag is released on, which is the one to act on where that
work is too expensive to repeat per step.

```kotlin
var zoom by remember { mutableStateOf(100) }

Slider(
    value = zoom,
    onValueChange = { zoom = it },
    onValueSettled = { println("settled on $it") },
    min = 50,
    max = 200,
    majorTickSpacing = 50,
    paintTicks = true,
    paintLabels = true,
)
ProgressBar(value = zoom, min = 50, max = 200, stringPainted = true)
Separator(orientation = SwingConstants.HORIZONTAL)
```

<!--- KNIT example-components-content-03.kt -->

`Slider` and `ProgressBar` also take a caller-owned `BoundedRangeModel` in place of `value`. The model
owns the range; the library renders it and never writes to it, so mutating the model repaints the
widgets without a recomposition. Handing the same instance to both is what lets a bar track a slider
without either of them owning the value:

```kotlin
val range = remember { DefaultBoundedRangeModel(30, 0, 0, 100) }

Slider(model = range)
ProgressBar(model = range)
```

<!--- KNIT example-components-content-04.kt -->

A `Spinner` shows its value through an editor, and two parameters decide which one. `format` is the
pattern the spinner's own editor renders and parses with - a `DecimalFormat` pattern over a number
model, a `SimpleDateFormat` pattern over a date one - and `null` formats the value the way the locale
does. `editor` replaces that editor with a composable, composed into the spinner as a composition of the
enclosing composition, so the editing surface reads the same state and composition locals the call
site does. A fresh lambda each pass recomposes it rather than rebuilding it, so characters
typed but not committed stand. Declaring both is refused: each names what the spinner shows.

```kotlin
var hour by remember { mutableStateOf(9) }

Spinner(hour, onValueChange = { hour = it.toInt() }, min = 0, max = 23, format = "00")
Spinner(hour, onValueChange = { hour = it.toInt() }, min = 0, max = 23) { Label("$hour o'clock") }
```

<!--- KNIT example-components-content-05.kt -->

---

## Lists and tables

| Component | What it is                                                                                                                          |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------|
| `ListBox` | A list over `JList`: items in, selected indices out, optionally with a composable cell per row.                                     |
| `Table`   | A grid over `JTable` whose rows are data and whose columns are declarations.                                                        |
| `Tree`    | A tree over `JTree` built from a root and a children function, addressed by index paths, optionally with a composable node per row. |

All three take a `selectionMode` from `ListSelectionModel`/`TreeSelectionModel` and report selection
as the general multi-select shape, so one component covers every mode. `ListBox` and `Table` default
to `ListSelectionModel.MULTIPLE_INTERVAL_SELECTION`, `Tree` to
`TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION`. A `ListBox`'s `visibleRowCount` defaults to `8`, a
`Tree`'s to `20`, and a `Tree`'s `toggleClickCount` - the clicks that open or close a node, `0` for
neither - to `2`. `Table` and `Tree` both take a `rowHeight`, which defaults to `null` and leaves the
height to the installed look and feel; a `Tree`'s `0` asks each node's rendering how tall it wants to
be, which is what lets a composable node size itself.

```kotlin
val languages = listOf("Kotlin", "Java", "Scala")
var selection by remember { mutableStateOf(setOf(0)) }

ListBox(
    items = languages,
    selectedIndices = selection,
    onSelectionChange = { selection = it },
    selectionMode = ListSelectionModel.SINGLE_SELECTION,
    visibleRowCount = 4,
) { item ->
    Label(if (isSelected) "* $item" else item)
}
```

<!--- KNIT example-components-content-06.kt -->

A composable cell - a `ListBox` or `ComboBox` `itemContent`, a column's `cellContent`, a `Tree`'s
`nodeContent` - composes **one** component, and that component is what the widget renders the row
with: it is bounded at the row and laid out there. Compose several components into a panel and the row
is that panel, arranged by its layout - a cell composing several components on its own is refused,
since only one of them could be the row. Everything inside is ordinary Swing: gaps, alignment and a
fixed slot for a leading glyph are the panel's layout to decide, and its background is painted over
the row's unless `opaque(false)` says otherwise.

```kotlin
ComboBox(items = languages, selectedItem = selected, onSelectionChange = { selected = it }) { language ->
    BorderPanel(modifier = SwingModifier.opaque(false)) {
        Label(language.glyph, modifier = SwingModifier.west().preferredSize(24, 24))
        Label(language.name)
    }
}
```

<!--- CLEAR -->

A list measures each row by what its cell asks for. A tree does the same where its `rowHeight` is `0`,
and otherwise gives every row the one height. A table never does: every one of its rows is the same
height, whatever its cells ask for, so a cell taller than the text a table sizes its rows for is what
`rowHeight` is there for.

A `ComboBox` renders its selected item in a display area whose height its look and feel fixes,
whatever the cell asks for, so a cell taller than that is cut off there. Check a tall cell against the
look and feel the application actually runs under, since each one sets that height for itself.

A `Table`'s columns are declared with a header and a value extractor over the row type. A column is
**typed**: the class the extractor returns is what the table renders and edits the column's cells as,
so a `Boolean` column draws a checkbox and hands `onCellEdit` a `Boolean`, and an `Int` column hands
it an `Int`. Where the class is not the extractor's own, declare it with the overload that takes a
`columnClass`. Put a table in a `ScrollPane` to scroll it and to show its column header.

A row is always named by its index into `rows` - the model's own row space, the space
`TableColumnLayout.modelIndices` names columns in - and never by the position it is drawn at. Sorting
and filtering move where a row is shown and leave the row each index names alone, so a selection, a
cell edit and a column layout all keep meaning what they meant.

Editing is per column and, where you want it, per row: `isEditable` opens a whole column, and
`isCellEditable` answers for one row of it. A committed edit arrives at `onCellEdit` with the row, its
index and the new value; the displayed value changes when the next composition supplies fresh `rows`.

Sorting is off until `sortable` turns it on, as it is on a bare `JTable`. With it on, a click on a
column header sorts by that column, `sortKeys` declares the order the rows are in and `onSortChange`
reports the order a click leaves them in, and `rowFilter` decides which of them are shown at all. Each
column brings its own `isSortable` and `comparator` to that sorting. A declared order is the
composition's state and is re-applied on every pass, so a header click the caller does not adopt does
not stand; left undeclared, the order is the user's alone and is never imposed.

The sizing surface is the same shape. `columnLayout` declares the order and the preferred widths of
the columns and `onColumnLayoutChange` reports where a header drag or a divider drag left them; a
column's own `minWidth` and `maxWidth` bound every width it can be left at, a drag's as much as a
declaration's; `autoResizeMode` decides how the columns share out a change to the table's width; and
`fillsViewportHeight` stretches the table to the viewport showing it rather than to the rows it holds.

```kotlin
var selection by remember { mutableStateOf(emptySet<Int>()) }
var order by remember { mutableStateOf(listOf(SortKey(1, SortOrder.ASCENDING))) }

ScrollPane {
    Table(
        rows = people,
        modifier = SwingModifier.viewport(),
        selectedRowIndices = selection,
        onSelectionChange = { selection = it },
        sortable = true,
        sortKeys = order,
        onSortChange = { order = it },
        rowHeight = 28,
    ) {
        column("Name", isEditable = true, onCellEdit = { row, _, value -> rename(row, value) }) { it.name }
        column("Age", isCellEditable = { row, _ -> row.isDraft }) { it.age }
        column("Owner", cellContent = { row -> FlowPanel { Label(row.avatar); Label(row.owner) } }) { it.owner }
    }
}
```

<!--- CLEAR -->

A `Tree` is built from your own node type: a `root`, a `children` function, and a `label` for the
text of a node. Selection and expansion are both a set of index paths - a `List<Int>` of child
positions per node, walked from the root - so they mean the same thing across two compositions of the
same shape. `rootVisible` defaults to `true`.

`nodeContent` renders a node as a composable of its own, against a `TreeNodeScope` carrying the node's
`row`, `isSelected`, `isExpanded`, `isLeaf` and `hasFocus`. `label` keeps its meaning either way: it
is the node's text where the tree renders the text itself, and its accessible text in both cases.
Give a tree with composable nodes a `rowHeight` of `0` so each node is measured by what it composes.

`isEditable` lets the user edit a node's text in place, and a committed edit hands `onNodeEdit` the
value edited, its index path and what was entered. Editing is a report and never a mutation: the row
goes on showing what the data says until a later composition supplies data that says otherwise, and
the tree `root` and `children` describe is never written to.

`onWillExpand` is asked before a node opens - whether the user opened it or a declared expansion did -
and returning `false` leaves it closed. Together with `hasChildren` that is also how children are
loaded lazily: a value `children` yields nothing for is a leaf with no handle to click, and declaring
`hasChildren` lets such a value call itself a branch all the same, so the user can ask for children
the data does not hold yet and `onWillExpand` - or `onExpansionChange` - is where fetching them starts.

```kotlin
var expanded by remember { mutableStateOf(setOf(emptyList<Int>())) }

Tree(
    root = fileTree,
    children = { it.children },
    label = { it.label },
    hasChildren = { it.isDirectory },
    expandedPaths = expanded,
    onExpansionChange = { expanded = it },
    onWillExpand = { node, _ ->
        loadChildren(node)
        true
    },
    rowHeight = 0,
) { node ->
    FlowPanel { Label(node.icon); Label(node.label) }
}
```

<!--- CLEAR -->

A `ListState` holds what one `ListBox` has selected, a `TableState` what one `Table` has selected, and
a `TreeState` what one `Tree` has selected and open. Each also carries the gesture that brings one row
into view when the application decides to - a row just added, a search hit, a node a load has just
filled in. Hoist one with `rememberListState()`, `rememberTableState()` or `rememberTreeState()` and
pass it as `state`; a widget driven by a state takes no selection or expansion parameter and no
`onSelectionChange`/`onExpansionChange`, since the state is where those facets live.

`revealIndex(index)` reveals a `ListBox` row, `revealRow(rowIndex)` a `Table` row, and
`revealPath(path)` a `Tree` node, opening every ancestor hiding it. Each call answers whether it
reached anything. Revealing is a gesture and not a declaration: it scrolls where it is called and
leaves nothing behind, so no later pass scrolls back and where the user scrolls afterwards stands. A
state drives one component at a time - passing it to a second moves it there.

A row is revealed once the list holds it, so reveal from an effect that runs after the composition
declaring it, not from the callback that declared it.

```kotlin
val state = rememberListState()

Button("Add", onClick = { items = items + Item() })
LaunchedEffect(items) { state.revealIndex(items.lastIndex) }
ScrollPane {
    ListBox(items = items, state = state, modifier = SwingModifier.viewport())
}
```

<!--- CLEAR -->

Each state also reads back what its widget currently shows, rather than what the composition declared:
see [`ListState`](#liststate), [`TableState`](#tablestate) and [`TreeState`](#treestate).

To reveal a region of a scroll pane's content rather than a row of a widget, use
[`ScrollState.revealRect`](#scrollstate).

---

## Containers and layout

Swing lays everything out, so a container is a layout manager plus the children you declare into it.
Children are written plainly in a container's content, and where a child sits inside its container is
part of what that child declares: a container gives its content a scope, and the scope's modifier
builders - `north()`, `item(...)`, `tab(...)` - append the placement to that child's own `modifier`.
A builder is only callable where its scope is the receiver, so a region belongs to the container that
offers it and cannot be named anywhere else.

| Component       | What it is                                                                                                         |
|-----------------|--------------------------------------------------------------------------------------------------------------------|
| `BoxPanel`      | A single-axis stack over `BoxLayout`.                                                                              |
| `Row`, `Column` | A single-axis stack holding each child at the size it prefers, with its leftover space placed by an `Arrangement`. |
| `FlowPanel`     | A wrapping strip over `FlowLayout`.                                                                                |
| `GridPanel`     | Equal-sized cells over `GridLayout`.                                                                               |
| `BorderPanel`   | Four edges and a filling center over `BorderLayout`, each named by the child in it.                                |
| `GridBagPanel`  | Cell-by-cell placement over `GridBagLayout`, each child naming its cell with `item(...)`.                          |
| `CardPanel`     | A deck over `CardLayout`, one card visible at a time, addressed by key.                                            |
| `TabbedPane`    | Tabs over `JTabbedPane`, each child the body of the tab it declares with `tab(...)`.                               |
| `SplitPane`     | Two sides and a draggable divider over `JSplitPane`.                                                               |
| `ScrollPane`    | A scrolled viewport plus header and corner regions over `JScrollPane`.                                             |
| `ToolBar`       | A bar of controls over `JToolBar`; `ToolBarSeparator` divides its groups.                                          |
| `LayeredPane`   | Children stacked on integer depth layers over `JLayeredPane`.                                                      |
| `DesktopPane`   | Floating internal frames over `JDesktopPane`.                                                                      |

Showing and hiding part of a layout is composing and not composing it: emitting a child adds it where
it declares it belongs, dropping the child takes it out, and declaring a different placement moves it
without costing the component it already has.

Where a region holds a single child, what a second child naming it costs is the region's own: a
`BorderPanel` region is taken by the last child registered in it and the panel lays nothing out for the
first, while a `SplitPane` side and a `CardPanel` card are refused to a second child, naming the side
or the card. That refusal is over what the composition declares once its changes have reached the
components, so a child replacing another on a side or a card is a single occupant throughout, whichever
order the two changes reach the container in. A `BorderPanel` child that names no region occupies
`center`, the region a `BorderLayout` registers a constraintless child under.

`BorderPanel` offers two families of edge: the absolute compass (`north`, `south`, `east`, `west`)
and the orientation-aware one (`pageStart`, `pageEnd`, `lineStart`, `lineEnd`), which resolve
against the panel's `ComponentOrientation`. `center` is shared. Use one family per edge. Its
`hgap`/`vgap` default to `0`.

```kotlin
BorderPanel {
    Label("Title", modifier = SwingModifier.pageStart())
    Body()
    Label("Status", modifier = SwingModifier.pageEnd())
}
```

<!--- CLEAR -->

`Row` and `Column` are the two single-axis stacks you reach for most. Along its axis, a child keeps
the size it prefers, and the space the container has left over is placed by an `Arrangement`
(`Top`, `Bottom`, `Start`, `End`, `Center`, `SpaceBetween`, `SpaceAround`, `SpaceEvenly`,
`spacedBy(gap)`, `aligned(...)`). Across the axis, a child sits where an `Alignment` puts it, or
takes the whole cross extent in its place. Spacing and every other measure here is in pixels. An
`Arrangement` of your own is handed the children's sizes and the positions to write in two arrays the
container owns and reuses on its next layout pass, so read and write them within the call and keep
neither.

A child claims a share of the leftover space with `weight`, names its own cross-axis placement
with `align`, or takes the whole cross extent with `fillWidth` / `fillHeight` in place of both its
own `align` and the container's cross-axis alignment, capped by an explicit `maximumSize` where it
declares one - through the `RowScope` / `ColumnScope` its content is written in, modifier
extensions so children stay plain:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8), horizontalAlignment = Alignment.Start) {
    Row(modifier = SwingModifier.fillWidth(), horizontalArrangement = Arrangement.End) {
        Button("Back", onClick = ::open)
        Button("Forward", onClick = ::open)
    }
    FlowPanel(modifier = SwingModifier.weight(1f), hgap = 8, vgap = 4) { Body() }
    Label("Status", modifier = SwingModifier.align(Alignment.CenterHorizontally))
}
```

<!--- CLEAR -->

A weighted child takes its share of what is left after every child that claims none has taken the
size it prefers, in proportion to the weights; `weight(w, fill = false)` lets it settle for the size
it prefers and leaves the rest to the arrangement. An explicit `maximumSize` caps that share.

`BoxPanel` is the direct `BoxLayout` wrapper, and a `ToolBar` lays its controls out the same way:
each shares its leftover space out among the children that have room between the size they prefer and
their maximum size, in proportion to that room. `Glue` is empty space with the most room of all, so it
takes the largest share, and `Strut`, `RigidArea` and `Spacer` (a `RigidArea` square) are the fixed
gaps between items. `FlowPanel` centers its children and gaps them by `5` pixels, and `GridPanel`
starts as a single row that grows a column per child, with no gaps.

`GridBagPanel`'s `item` takes one parameter per `GridBagConstraints` field, under the field's own
name and with its own default, so a grid-bag layout written against Swing carries over field for
field.

```kotlin
var name by remember { mutableStateOf("") }
GridBagPanel {
    Label(
        "Name:",
        modifier =
            SwingModifier.item(
                gridx = 0,
                gridy = 0,
                anchor = GridBagConstraints.LINE_END,
                insets = Insets(4, 4, 4, 4),
            ),
    )
    TextField(
        name,
        onValueChange = { name = it },
        modifier =
            SwingModifier.item(gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL),
    )
}
```

<!--- KNIT example-components-content-07.kt -->

A child that declares no `item` is laid out under `GridBagConstraints`' own defaults, which place it
relative to the child before it.

`CardPanel` shows the card whose key equals its `selectedCard`, so switching pages is a state write. A
card holds one child, and two children naming the same card are refused. A child that declares no
card is the card keyed by the empty string, shown by `selectedCard = ""`. The key names the card, not
the child: a child that declares a new key moves to that card keeping its position among its siblings,
and its composition identity is the place it is written at, as any child's is.

```kotlin
var step by remember { mutableStateOf("details") }
CardPanel(selectedCard = step) {
    Details(modifier = SwingModifier.card("details"))
    Payment(modifier = SwingModifier.card("payment"))
}
Button("Next", onClick = { step = "payment" })
```

<!--- CLEAR -->

Every child of a `TabbedPane` is one tab's body and declares that tab with `tab(...)`, which carries the
tab's title, icon, tooltip, enabled flag, mnemonic and colors, and can take over what the tab strip
renders with a `header` composable - the title and icon still name the tab, for accessibility and as
the values recomposition writes. A tab keeps what its body remembers, and the components that body was
realized as, for as long as the child holding the declaration keeps its composition identity: a body
written at a place of its own keeps its tab however the tabs around it come and go, and bodies written
at one place - a loop over the open documents - are told apart by the position they arrive in until
`key` gives each one of its own, so an unkeyed body is handed the tab that used to stand in its
position. `tabPlacement` defaults to `JTabbedPane.TOP` and `tabLayoutPolicy` to
`JTabbedPane.WRAP_TAB_LAYOUT`.

```kotlin
var tab by remember { mutableStateOf(0) }
TabbedPane(selectedIndex = tab, onSelectedIndexChange = { tab = it }) {
    Source(modifier = SwingModifier.tab("Source"))
    Console(
        modifier =
            SwingModifier.tab(
                title = "Console",
                header = { Label("Console", modifier = SwingModifier.icon(saveIcon)) },
            ),
    )
    documents.forEach { document ->
        key(document.id) {
            Editor(document, modifier = SwingModifier.tab(document.name))
        }
    }
}
```

<!--- CLEAR -->

A `SplitPane`'s `first` side is the left or the top depending on `orientation`, which defaults to
`JSplitPane.HORIZONTAL_SPLIT`. `dividerLocation` is declared and `onDividerLocationChange` reports
where the user dragged it; `resizeWeight` decides which side keeps extra space, and
`oneTouchExpandable` and `dividerSize` shape the divider itself. `continuousLayout` lays the two sides
out as the divider is dragged rather than once it is released. Each of those three leaves the choice to
the look and feel while it is `null`, and settles at the look and feel's answer if it is withdrawn after
being declared.

```kotlin
var divider by remember { mutableStateOf(240) }
SplitPane(
    orientation = JSplitPane.HORIZONTAL_SPLIT,
    dividerLocation = divider,
    onDividerLocationChange = { divider = it },
    resizeWeight = 0.3,
    oneTouchExpandable = true,
) {
    Navigator(modifier = SwingModifier.first())
    Editor(modifier = SwingModifier.second())
}
```

<!--- CLEAR -->

A `ScrollPane` holds nothing but its regions, so every child declares one: `viewport()`, `rowHeader()`,
`columnHeader()` or `corner(...)`. `viewport()` also carries how far the pane scrolls per arrow button
and per page, and whether the content is laid out at the viewport's own width or height - each `null`
by default, which leaves the answer to content that gives one of its own, as a table, list, tree or
text area does. Both scrollbar policies default to as-needed. `viewportBorder` draws a border around the
viewport, inside the pane's own border and outside the scrolled content, and leaves it to the look and
feel while it is `null`; `wheelScrollingEnabled` decides whether the mouse wheel scrolls the pane at all.
The scroll position is hoisted into a [`ScrollState`](#scrollstate).

```kotlin
ScrollPane(horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
    Label("Rows", modifier = SwingModifier.columnHeader())
    Body(modifier = SwingModifier.viewport())
    Label("#", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
}
```

<!--- CLEAR -->

A `ToolBar` is horizontal and floatable by default. `floating` declares whether the bar stands in a
window of its own, and `onFloatingChange` reports where the user dragged it - or hands back the docked
state a bar that cannot float settles for.

```kotlin
ToolBar(floatable = false, rollover = true) {
    Button("Open", onClick = ::open)
    Button("Save", onClick = ::save)
    ToolBarSeparator()
    Button("Run", onClick = ::open)
}
```

<!--- CLEAR -->

A `LayeredPane` stacks children on integer depths - higher layers paint above lower ones, and within
one layer the children stack in the order the composition declares them, the first of them on top. A
child that declares no layer stands on `JLayeredPane.DEFAULT_LAYER`. It lays nothing out, so each
child carries its own bounds.

```kotlin
LayeredPane {
    Body(modifier = SwingModifier.bounds(0, 0, 400, 300))
    Label("Floating", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER).bounds(16, 16, 120, 24))
}
```

<!--- CLEAR -->

A `DesktopPane` hosts internal frames. Each frame declares a title, its window controls, and either
plain `bounds` - where it starts, and wherever the user then leaves it - or an
[`InternalFrameState`](#internalframestate), which makes its geometry and its window state two-way.
The close control is controlled: activating it calls `onClose`, and the frame closes when you stop
declaring it. Every control is off by default, matching a fresh `JInternalFrame`.

A frame keeps the window it was realized as, and the position the user dragged it to, for as long as
the composition declares it in the same place - the same identity rule a `TabbedPane` tab keeps its
body by, earlier in this section. Frames declared from one place, as a loop over a list declares
them, are told apart by the position they arrive in, so wrap each one in `key`; a frame's
`InternalFrameState` serves as the key. Without it, removing a frame hands every frame after it its
predecessor's window, and the positions the user chose are lost. One state drives one frame: a second
frame taking a state another frame already holds is refused.

```kotlin
val palette = rememberInternalFrameState(Rectangle(24, 24, 320, 200))
var open by remember { mutableStateOf(true) }

Label("Palette at ${palette.x}, ${palette.y}, ${palette.width} x ${palette.height}")
Button("Send home", onClick = { palette.bounds = Rectangle(24, 24, 320, 200) })
DesktopPane {
    if (open) {
        InternalFrame(
            title = "Palette",
            state = palette,
            onClose = { open = false },
            controls = InternalFrameControls(closable = true, resizable = true),
        ) {
            Body()
        }
    }
}
```

<!--- CLEAR -->

To place children under a layout manager of your own, `layoutConstraint` is the builder a container you
write yourself names its own placements over - see
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md#placing-children-under-constraints).

---

## Windows and dialogs

| Entry point                            | What it is                                                                              |
|----------------------------------------|-----------------------------------------------------------------------------------------|
| `application { }`                      | Runs a Compose application until its last composition ends, blocking the caller.        |
| `awaitApplication { }`                 | The suspending form, for an application started from a coroutine.                       |
| `CoroutineScope.launchApplication { }` | The same, launched as a `Job` in a scope you own.                                       |
| `Window`                               | A top-level frame over `JFrame`, geometry hoisted into a [`WindowState`](#windowstate). |
| `Dialog`                               | A dialog over `JDialog`, geometry hoisted into a [`DialogState`](#dialogstate).         |

All three entry points take the same `ApplicationScope` content and give it `exitApplication()`.
Windows and dialogs composed inside one run as part of that application's composition, so
application-scope state and `CompositionLocal`s flow into their content.

A window's content is given the window as its scope, and what the window carries besides that content
is declared there: `MenuBar { }` and `GlassPane { }` are those declarations, so each can only be
written where there is a window to carry it.

`GlassPane { }` is the sheet above everything else in the window: it covers the whole window, it is
transparent where its content paints nothing, and while it is shown the window's mouse events reach
it - a drag-and-drop hint, a progress veil, anything drawn over the window rather than in it.
The content fills the pane, so a layout composable inside it places what the overlay is made of. The
pane is over the window while the declaration is composed, and the window carries the glass pane it
carried before once the declaration leaves, so an overlay that comes and goes is an `if` around the
call. A window carries one glass pane, so one declaration serves a window.

```kotlin
Window(onCloseRequest = ::exitApplication) {
    var loading by remember { mutableStateOf(true) }

    if (loading) {
        GlassPane {
            GridBagPanel {
                ProgressBar(value = 0, indeterminate = true)
            }
        }
    }
    Button("Done", onClick = { loading = false })
}
```

<!--- CLEAR -->

A window exists while it is composed: opening one is composing it, closing it is not composing it,
and `onCloseRequest` is where you flip the state that decides. `title` starts empty, `resizable` is
`true`, `alwaysOnTop` is `false`, `undecorated` is `false`, and a dialog's `modality` is
`ModalityType.MODELESS`. A `Dialog` composed inside a window's content takes that window as its
owner - the window its modality blocks and the one `WindowPosition.CenteredOnOwner` centers it on -
and `owner` names a different window to be owned by instead.

```kotlin
application {
    val state = rememberWindowState(position = WindowPosition.CenteredOnScreen, size = Dimension(900, 600))

    Window(onCloseRequest = ::exitApplication, state = state, title = "Editor") {
        Column {
            Label("${state.width} x ${state.height} at ${state.position}")
            Button("Center", onClick = { state.position = WindowPosition.CenteredOnScreen })
            Button("Widen", onClick = { state.width += 80 })
        }
    }
}
```

<!--- KNIT example-components-app-01.kt -->

```kotlin
var asking by remember { mutableStateOf(false) }

Button("Delete", onClick = { asking = true })
if (asking) {
    Dialog(
        onCloseRequest = { asking = false },
        state = rememberDialogState(size = Dimension(320, 160)),
        title = "Confirm",
        modality = java.awt.Dialog.ModalityType.APPLICATION_MODAL,
    ) {
        BorderPanel {
            Label("Delete the selection?")
            Button("OK", onClick = { asking = false }, modifier = SwingModifier.pageEnd())
        }
    }
}
```

<!--- KNIT example-components-content-08.kt -->

To mount a composition into a Swing window or container you already own, use `setContent` - see
[`ARCHITECTURE.md`](ARCHITECTURE.md#mounting-a-composition).

Content reads the window it lives in as `LocalWindow`, whether that window was composed or is one you
own and mounted into, so a `Dialog` finds its owner without being told and a menu item's callback can
reach the window its bar hangs off. Read it where plain Swing wants a parent window - `JFileChooser`,
`JOptionPane`, `toFront()`. It is `null` wherever the content stands in no window at all: under a bare
`application { }` scope that has created no window, and in content composed under a composition of your
own before its container has been added anywhere. Such content reads the window it is added to from the
moment it arrives there.

A file chooser and a message box are calls rather than components: each blocks where it is called and
returns an answer, so they stay plain Swing. `JFileChooser` and `JOptionPane` work as they are from a
callback, and the library ships no wrapper for them - where a plain Swing class already works well
from composable code, it stays plain Swing. What such a call wants from the composition is a parent
window, and that is what `LocalWindow.current` is: read it while composing, use it where the call
takes an owner.

```kotlin
val window = LocalWindow.current
var path by remember { mutableStateOf<String?>(null) }

Row {
    Button(
        "Open...",
        onClick = {
            val chooser = JFileChooser()
            if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                path = chooser.selectedFile.path
            }
        },
    )
    Button(
        "Close",
        onClick = {
            val answer =
                JOptionPane.showConfirmDialog(
                    window,
                    "Close $path without saving?",
                    "Close",
                    JOptionPane.YES_NO_OPTION,
                )
            if (answer == JOptionPane.YES_OPTION) path = null
        },
        modifier = SwingModifier.enabled(path != null),
    )
}
```

<!--- KNIT example-components-content-09.kt -->

---

## Menus

| Component              | What it is                                                                          |
|------------------------|-------------------------------------------------------------------------------------|
| `Menu`                 | A menu over `JMenu` holding further menu content; nest it for a submenu.            |
| `MenuItem`             | A command over `JMenuItem`, with an `accelerator` and `onClick`.                    |
| `CheckBoxMenuItem`     | A checkable item over `JCheckBoxMenuItem`, with `checked`/`onCheckedChange`.        |
| `RadioButtonMenuItem`  | One exclusive item over `JRadioButtonMenuItem`, with `selected`/`onSelectedChange`. |
| `RadioButtonMenuGroup` | A set of mutually exclusive menu items, selected by index.                          |
| `MenuSeparator`        | A divider between menu items, over `JPopupMenu.Separator`.                          |
| `MenuBar`              | The menu bar of a window, declared in that window's content.                        |

Menu content is composed like any other content: `MenuBar { }` in the content of a `Window` or a
`Dialog` declares that window's menu bar, `JMenuBar.setContent { }` composes a bar you own yourself,
and the same tree serves a context menu (the `contextMenu` modifier) and a [`Tray`](#system-tray)
popup. A `contextMenu` becomes the component's own popup menu, so the pointer gesture and the keyboard
binding a look and feel gives a context menu both open it; the target must therefore be a `JComponent`,
and the last `contextMenu` in a chain owns it. An item's label, checked flag and enabled state follow
composition state like any other component's, and a checkable item takes its state the way the
two-state controls do: what the composition declares is what the item shows, so a choice the caller
does not adopt goes back where it was. An `accelerator` is a `KeyStroke` that fires the item without
opening the menu; a mnemonic is the `mnemonic` modifier.

```kotlin
val bar = JMenuBar()
bar.setContent {
    var wordWrap by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(0) }

    Menu("File") {
        MenuItem("Open", onClick = ::open, accelerator = KeyStroke.getKeyStroke("control O"))
        MenuSeparator()
        MenuItem("Save", onClick = ::save, accelerator = KeyStroke.getKeyStroke("control S"))
    }
    Menu("View") {
        CheckBoxMenuItem("Word wrap", checked = wordWrap, onCheckedChange = { wordWrap = it })
        MenuSeparator()
        RadioButtonMenuGroup(selectedIndex = theme, onSelectionChange = { theme = it }) {
            option("Light")
            option("Dark")
        }
    }
}
frame.jMenuBar = bar
```

<!--- CLEAR -->

There is no command type, so one command reached from two surfaces - a menu item and a toolbar button
that go gray together - is a shared value and a shared modifier. Both surfaces read the one state and
apply the one chain, so enabling and disabling them is a single state write.

```kotlin
Window(onCloseRequest = ::exitApplication) {
    var dirty by remember { mutableStateOf(false) }
    val whenDirty = SwingModifier.enabled(dirty)

    MenuBar {
        Menu("File") {
            MenuItem(
                "Save",
                onClick = ::save,
                modifier = whenDirty,
                accelerator = KeyStroke.getKeyStroke("control S"),
            )
        }
    }
    ToolBar {
        Button("Save", onClick = ::save, modifier = whenDirty)
    }
}
```

<!--- CLEAR -->

---

## Drawing

`Canvas` hands you the raw `Graphics2D` of a blank surface, plus its current pixel width and height.
Snapshot state read inside the draw lambda, at paint time, is observed: when it changes the surface
repaints. Size the surface with the preferred-size modifier.

```kotlin
var radius by remember { mutableStateOf(24) }
Column {
    Canvas(modifier = SwingModifier.preferredSize(Dimension(200, 200))) { g, width, height ->
        g.fillOval(width / 2 - radius, height / 2 - radius, radius * 2, radius * 2)
    }
    Slider(value = radius, onValueChange = { radius = it }, min = 4, max = 80)
}
```

<!--- KNIT example-components-content-10.kt -->

---

## System tray

`Tray` registers a system-tray icon for as long as it is composed. Its popup is a menu tree,
composed fresh each time the icon is asked for one, so the items reflect current state. Activating
the icon runs `onAction`. `imageAutoSize` defaults to `false`, painting the image at its own size;
`java.awt.SystemTray.isSupported()` tells you whether the platform has a tray at all.

```kotlin
application {
    var paused by remember { mutableStateOf(false) }

    Tray(image = trayImage, onAction = { paused = !paused }, tooltip = "Indexer") {
        CheckBoxMenuItem("Paused", checked = paused, onCheckedChange = { paused = it })
        MenuSeparator()
        MenuItem("Quit", onClick = ::exitApplication)
    }
}
```

<!--- CLEAR -->

---

## Hoisted state

Some values are awkward to pass down and report back one at a time: the whole content of an editor,
where the user dragged a window. For those, the library hands you a state holder to hoist next to
your own state - the owner of the value (the document, the geometry), read as
snapshot state and two-way like every
[hoistable state holder](ARCHITECTURE.md#shapes-for-state-the-user-can-change).

Create one with its `remember*` factory. The initial values seed the holder on first composition and
are not re-read afterwards: to change the value later, assign to the property.

### `DocumentState`

Owns the `Document` a text component renders. Because the state and the component share one
document, an edit made through the state is what the component displays, and text the user types is
what the state reports. `text` is materialized on demand, so typing a character does not pay for a
read of the whole document.

- `text` - the content, readable and assignable; assigning applies only the changed span.
- `edit { }` - a batch of `insert`/`replace`/`delete`/`append`/`setText` calls committed as one
  change, with `placeCaretAtEnd()` and `selectAll()` to place the caret afterwards.
- `undo()`, `redo()`, `canUndo`, `canRedo` - one `edit { }` block, or one assignment to `text`, is one
  undoable step.

Create it with `rememberDocumentState(initialText)`, or `rememberDocumentState(document)` to adopt a
document you already have. Pass it to `TextField`, `TextArea`, `TextPane`, `EditorPane` or
`PasswordField` as `state`.

A `contentType` names the language the document is written in, and the editor kit registered for it
both builds the document and reads `initialText` as source: `"text/html"` gives an `HTMLDocument`
holding parsed markup, `"text/rtf"` the styled model a `TextPane` requires, and the default
`"text/plain"` a plain document holding the text as characters. An `EditorPane` renders the state
through that same kit. Pass `kit` instead when the kit is configured - a style sheet of your own, a
custom parser - or alongside `document` to name the language of a document you are adopting.

```kotlin
val report = rememberDocumentState("<h1>Report</h1><p>Q3 was <b>strong</b>.</p>", contentType = "text/html")
EditorPane(state = report, editable = false)
```

<!--- KNIT example-components-content-11.kt -->

```kotlin
val note = rememberDocumentState("Dear ")

Column {
    TextField(state = note, columns = 32)
    Label("${note.text.length} characters")
    Button(
        "Sign off",
        onClick = {
            note.edit {
                append("\n\nRegards")
                placeCaretAtEnd()
            }
        },
    )
    Button("Undo", onClick = note::undo, modifier = SwingModifier.enabled(note.canUndo))
}
```

<!--- KNIT example-components-content-12.kt -->

### `FormattedValueState`

Owns the value a `FormattedTextField` renders, typed as the field's formatter produces it - an `Int`,
a `Date`, a `String`. A value the field commits, whether the user pressed Enter or the field lost the
focus, is written back into the state, and the state is where the value lives, so a state-driven field
takes no `onValueChange` and no `onEditValidChange`.

- `value` - the committed value, readable and assignable; assigning it re-renders the field's
  characters, which replaces characters the user has typed but not committed.
- `isEditValid` - whether the characters the field currently shows parse. It follows what the user
  types rather than what the field commits, so a part-typed edit reports `false` while `value` stands
  where the last commit left it - which is what gates a form's save button.
- `commit()` - takes the field's current text as its value and answers whether it was taken. It is how
  a caller outside the field - a dialog's OK button - takes an edit the user typed but never entered;
  the typed text stays either way.

Create it with `rememberFormattedValueState(initialValue)` and pass it to `FormattedTextField` as
`state`. A state renders one field at a time - declaring it on a second moves it there.

### `WindowState`

Owns a `Window`'s geometry: `position`, `width`, `height`, `size`, and `extendedState`
(`Frame.MAXIMIZED_BOTH`, `Frame.ICONIFIED`, `Frame.NORMAL`). Assigning moves, resizes, maximizes,
minimizes or restores the window; the user dragging, resizing or maximizing it writes the new value
back. `rememberWindowState(position, size, extendedState)` creates one, and a `null` initial `size`
sizes the window to its content.

`position` is a `WindowPosition`: either `Absolute(x, y)` or a placement request -
`PlatformDefault`, `CenteredOnScreen`, `CenteredOnOwner`, `CenteredOn(window)`. A request carries no
coordinates of its own, and the placement it resolves to is written back as an `Absolute`, so reading
`position` after the window is shown always gives concrete coordinates.

`CenteredOn(window)` centers on any window - `LocalWindow.current` is the window the declaring content
sits in - on the bounds that window holds as the position is applied, so the centered window stands
where it was put once the other one moves on.

### `DialogState`

The same for a `Dialog`: `position`, `width`, `height` and `size`, created with
`rememberDialogState(position, size)`.

### `InternalFrameState`

The same for one internal frame of a `DesktopPane`: `x`, `y`, `width`, `height` and `bounds`, plus the
frame's two window states, `iconified` and `maximized`. Created with
`rememberInternalFrameState(bounds, iconified, maximized)`. Declare a frame with a state instead of
plain `bounds` to make all of it two-way - see [`DesktopPane`](#containers-and-layout).

A maximized frame stands on the whole desktop, and the geometry the state carries is where restoring
it returns it to, so a frame comes back where the user left it however it was maximized. A frame can
be iconified and maximized at once: its icon restores to a frame that fills the desktop.

### `ScrollState`

Owns where a `ScrollPane` is scrolled to: `x` and `y` are the view coordinates shown at the viewport's
leading and top edge. Assigning one scrolls the pane; the user scrolling it - by wheel, scrollbar or
keyboard - writes the new position back. `rememberScrollState(x, y)` creates one, and it reaches the pane
as `ScrollPane`'s `state`.

The metrics the position moves within are read-only and follow both the content and the pane's size:
`extentWidth` and `extentHeight` are the visible part of the content, `viewWidth` and `viewHeight` the
whole of it, and `maxX` and `maxY` the largest position that still shows content - so `state.y =
state.maxY` scrolls to the bottom of whatever is currently there. Each is `0` while no pane renders the
state.

`canScrollForwardX`, `canScrollBackwardX`, `canScrollForwardY` and `canScrollBackwardY` answer whether an
axis has anywhere left to scroll, for a caller that offers a way to scroll further and wants it disabled
at the end. The forward pair is `false` once the position stands at `maxX` or `maxY`, so content the
viewport shows whole can be scrolled forward nowhere; the backward pair is `false` while the position
stands at `0`. A reader of one of them stands still until the answer itself changes, rather than
following every scrolled pixel.

The position outlives the content it was reached in, so a pane that leaves the composition and returns
comes back where the user left it.

`revealRect(rect)` scrolls to a region of the content instead of to a coordinate, for a caller that knows
where something is but not where the pane has to stand to show it. The rectangle is in the content's own
coordinates, whatever the pane is currently scrolled to, and the call answers whether a pane with content
was there to scroll. Wherever it lands is reported back through `x` and `y`, like the user's own
scrolling.

### `ListState`

Owns what one `ListBox` has selected. `selectedIndices` is two-way: assigning it selects those rows,
and the user selecting others - by click, drag or keyboard - writes them back. The rows it names are
the composition's own and are re-applied on every pass, so a list driven by a state never stands on a
selection the state does not hold, and a row the items do not reach goes on being named here until
items that reach it show it selected again.

`revealIndex(index)` brings a row into view when the application decides to - a row just added, a
search hit - and answers whether the list held one to reveal. Revealing is a gesture rather than a
declaration: it scrolls where it is called and leaves nothing behind, so no later pass scrolls back and
where the user scrolls afterwards stands.

`itemCount` and `shownSelectedIndices` answer for the list rather than for the declaration: how many
items it shows, and which rows it has selected. The two differ where the list cannot stand on what was
declared - a selection mode narrower than the declaration keeps only part of a selection, and an index
the items do not reach has no row to be selected at. Neither is snapshot state, so reading one
subscribes to nothing; a composable that has to follow the user reads `selectedIndices`. An unbound
state reports no items and nothing selected.

Create it with `rememberListState(initialSelectedIndices)` and pass it to `ListBox` as `state`, in
place of `selectedIndices` and `onSelectionChange`. A state drives one list at a time - passing it to a
second moves it there. See [Lists and tables](#lists-and-tables).

### `TableState`

The same for one `Table`, over row indices. `selectedRowIndices` is two-way and re-applied on every
pass, and an index names a row in the model's own row space and never the position the row is drawn
at, so sorting and filtering move where a row is shown and leave the index naming it alone.

`revealRow(rowIndex)` brings a row into view and answers whether the table held one to reveal. A row
hidden by a row filter has nowhere to be shown, so revealing it reaches nothing.

`rowCount` and `shownSelectedRowIndices` are the same read-back over the table: how many rows it
shows - its model's rows, less the ones a row filter hides - and which rows it has selected, named by
model index like `selectedRowIndices`. A row a filter hides has nowhere to be shown, so it is not
among them.

Create it with `rememberTableState(initialSelectedRowIndices)` and pass it to `Table` as `state`, in
place of `selectedRowIndices` and `onSelectionChange`. A state drives one table at a time.

### `TreeState`

The same for one `Tree`, over index paths from the root: `[]` is the root, `[0]` its first child, and
`[0, 2]` that child's third child. `selectedPaths` and `expandedPaths` are both two-way and both
re-applied on every pass, so a state starting on the empty expansion opens nothing - start it on
`setOf(emptyList())` for a tree that opens on its root.

`revealPath(path)` brings a node into view, opening every ancestor that hides it, and answers whether
the tree held such a node. The ancestors it opens arrive in `expandedPaths` like the user's own
opening.

`rowCount`, `shownSelectedPaths` and `isExpanded(path)` are the same read-back over the tree: how many
rows it shows, which nodes it has selected, and whether one node shows its children below it. Closing
a node takes the selection over from the descendants it hides, so what the tree reports selected is
the closed node itself; a node the tree's current structure does not have is in none of the three
answers.

Create it with `rememberTreeState(initialSelectedPaths, initialExpandedPaths)` and pass it to `Tree` as
`state`, in place of `selectedPaths`/`onSelectionChange` and `expandedPaths`/`onExpansionChange`. A
state drives one tree at a time.

---

## Anything else

The catalog is not the boundary of what you can compose. Any Swing `Component` can be hosted
directly with `SwingNode`, any menu component with `MenuNode`, and a property no builder covers can be
written as a modifier element of your own. [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md) is the guide
to all three.
