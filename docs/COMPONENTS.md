# Components

This is the catalogue of what Compose Swing UI ships: every component family, grouped the way you
reach for one, with the parameters that decide how it behaves. The KDoc on each function is the
per-parameter reference. The concepts behind the binding are in
[`ARCHITECTURE.md`](ARCHITECTURE.md), and building a component of your own is
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

Two things hold for everything below.

**Every parameter is reapplied on recomposition.** Pass state to a component and the widget follows
that state for the component's whole life.

**Every default matches the widget's own.** A component that leaves a parameter unset behaves
exactly as the freshly constructed Swing widget does, so the defaults quoted here are Swing's, not
this library's. A parameter with no single correct default - `CheckBox`'s `checked`, or a
`ComboBox` built over `items` rather than a `ComboBoxModel` - is required instead.

Cross-cutting configuration - colours, fonts, borders, sizes, tooltips, accessibility, keyboard
bindings, data transfer, raw listeners - arrives as a `SwingModifier` chain passed as `modifier`,
described in
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md#styling-with-a-modifier-swingmodifier-parameter).

---

## How a component takes state

Three shapes appear throughout the catalogue. Which one a component uses tells you where its state
lives.

**A value and a callback.** `TextField(value, onValueChange = ...)`, `CheckBox(text, checked,
onCheckedChange = ...)`, `TabbedPane(selectedIndex, onSelectedIndexChange = ...)`. You own the state;
the component renders what you pass and reports what the user did. A value you push in is reflected
without echoing back through the callback, so adopting a reported change cannot loop.

**A hoisted state holder.** `DocumentState`, `FormattedValueState`, `SpinnerState`, `WindowState`,
`DialogState`, `InternalFrameState`, `ScrollState`. The holder owns the value, is snapshot-observable,
and is **two-way**: assigning to it drives the widget, and the user's own gesture writes back into it.
See [Hoisted state](#hoisted-state).

**A raw Swing model.** `ComboBox(model)`, `ListBox(model)`, `Table(model)`, `Tree(model)`,
`Spinner(model, changeListener)`. When you already have a `ComboBoxModel`, `TableModel` or
`TreeModel`, hand it over and the component renders it. The model stays yours.

Most components that take a lambda callback also offer an overload taking the corresponding raw Swing
listener instead, for when you already hold a listener object: `ActionListener` (the buttons, the
menu items, `ComboBox`), `ChangeListener` (`Slider`, `Spinner`, `TabbedPane`), `DocumentListener`
(`TextField`, `TextArea`, `TextPane`, `EditorPane`, `PasswordField`), `ListSelectionListener`
(`ListBox`, `Table`), `TableColumnModelListener` (a `Table`'s column layout),
`TreeSelectionListener` and `TreeExpansionListener` (`Tree`), `PropertyChangeListener`
(`FormattedTextField`, `SplitPane`) and `InternalFrameListener` (`internalFrame`). The KDoc on each
overload says which events it carries.

### Declaring a selection, or leaving it alone

`ListBox`, `Table` and `Tree` take their selection (and a `Tree` its expansion) as a nullable
parameter, and the two cases differ.

Declare it - `selectedIndices = mine` - and the selection is the composition's state, reapplied on
every pass: it survives new items, and a user change your callback does not adopt is undone. Leave it
`null` and the selection belongs to the user alone: never imposed, and carried across new items; where
the new items are too few to hold it, what falls outside them leaves and the callback reports what is
left.

Either way the callback reports the user's changes only, once per settled change - dragging across
rows produces one call at the end, and rendering fresh items produces none. A `ComboBox` is always
controlled instead: its `selectedIndex` names the chosen item, and `-1` names none.

---

## Text

| Component | What it is |
| --- | --- |
| `Label` | A text label over `JLabel`. Alignment, icon and text position come from the modifier chain. |
| `TextField` | One line of editable text over `JTextField`. |
| `PasswordField` | A field over `JPasswordField` whose value is a `CharArray` rather than a `String`. |
| `FormattedTextField` | A field over `JFormattedTextField` that parses and formats a typed value through an `AbstractFormatterFactory`. |
| `TextArea` | Multi-line plain text over `JTextArea`. |
| `TextPane` | Styled multi-line text over `JTextPane`. |
| `EditorPane` | Markup over `JEditorPane`, rendered through the editor kit its `contentType` names. |

`TextField`, `TextArea`, `TextPane` and `PasswordField` each come in three forms: a `value` plus
`onValueChange`, a `value` plus a `DocumentListener`, and a [`DocumentState`](#documentstate) that
owns the document outright.

The `value` forms are strictly controlled. The component holds what the composition declares: an edit
the caller does not adopt - one `onValueChange` is not answered with a matching `value` - is settled
back onto the declared text on the very next pass, so the component never stands on text the caller
has not taken. `FormattedTextField` holds its committed value the same way.

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
count. `editable` defaults to `true`, and `FormattedTextField`'s `focusLostBehavior` to
`JFormattedTextField.COMMIT_OR_REVERT`.

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
    contentType = "text/html",
    onLinkActivate = { href -> open(href) },
)
```

---

## Buttons and choices

| Component | What it is |
| --- | --- |
| `Button` | A push button over `JButton`, with `onClick`. |
| `ToggleButton` | A button over `JToggleButton` that stays in, with `pressed`/`onPressedChange`. |
| `CheckBox` | A checkbox over `JCheckBox`, with `checked`/`onCheckedChange`. |
| `RadioButton` | One radio button over `JRadioButton`, with `selected`/`onSelect`. |
| `RadioGroup` | A set of mutually exclusive radio buttons declared as `option(...)` calls, selected by index. |
| `ComboBox` | A drop-down over `JComboBox`, optionally editable, optionally rendering each item as a composable cell. |
| `Slider` | A slider over `JSlider`, with ticks and a label table. |
| `Spinner` | A stepper over `JSpinner`, driven by a [`SpinnerState`](#spinnerstate) or a raw `SpinnerModel`. |
| `ProgressBar` | A determinate or indeterminate bar over `JProgressBar`. |
| `Separator` | A divider over `JSeparator` between the items of any container. |

`Button` reports a click; the two-state controls take the state in and report the state the user asked
for, so a toggle the caller does not adopt goes back where it was.

```kotlin
var wrap by remember { mutableStateOf(false) }
var pinned by remember { mutableStateOf(false) }

Button("Save", modifier = SwingModifier.icon(saveIcon), onClick = ::save)
CheckBox("Word wrap", checked = wrap, onCheckedChange = { wrap = it })
ToggleButton("Pin", pressed = pinned, onPressedChange = { pinned = it })
```

A `RadioGroup` owns the button group, so you declare the options and the selected index rather than
wiring exclusivity yourself. Individual `RadioButton`s remain available for a layout the group's own
axis does not cover.

```kotlin
var theme by remember { mutableStateOf(0) }
RadioGroup(selectedIndex = theme, onSelectionChange = { theme = it }) {
    option("Light")
    option("Dark")
    option("Follow system")
}
```

An editable `ComboBox` has two outputs: `onSelectionChange` for a choice from the list, and
`onValueCommit` for text typed into the editor. `itemContent` replaces the rendered cell with a
composable, which receives the item and, through its scope, the row's `index`, `isSelected` and
`cellHasFocus`. `editable` defaults to `false` and `maximumRowCount` - the rows the popup shows
before scrolling - to `8`.

```kotlin
val presets = listOf("Small", "Medium", "Large")
var selected by remember { mutableStateOf(0) }
var typed by remember { mutableStateOf("") }

ComboBox(
    items = presets,
    selectedIndex = selected,
    onSelectionChange = { selected = it },
    editable = true,
    onValueCommit = { typed = it },
) { item ->
    Label(if (isSelected) "> $item" else item)
}
```

`Slider` and `ProgressBar` both range over `0`..`100` by default and are horizontal, as is
`Separator`. A slider's `labels` map declares the label table that `paintLabels` then paints; a
progress bar's `stringPainted` alone gives a percentage readout, and `string` overrides the text it
paints.

```kotlin
var zoom by remember { mutableStateOf(100) }

Slider(
    value = zoom,
    onValueChange = { zoom = it },
    min = 50,
    max = 200,
    majorTickSpacing = 50,
    paintTicks = true,
    paintLabels = true,
)
ProgressBar(value = zoom, min = 50, max = 200, stringPainted = true)
Separator(orientation = SwingConstants.HORIZONTAL)
```

---

## Lists and tables

| Component | What it is |
| --- | --- |
| `ListBox` | A list over `JList`: items in, selected indices out, optionally with a composable cell per row. |
| `Table` | A grid over `JTable` whose rows are data and whose columns are declarations. |
| `Tree` | A tree over `JTree` built from a root and a children function, addressed by index paths. |

All three take a `selectionMode` from `ListSelectionModel`/`TreeSelectionModel` and report selection
as the general multi-select shape, so one component covers every mode. `ListBox` and `Table` default
to `ListSelectionModel.MULTIPLE_INTERVAL_SELECTION`, `Tree` to
`TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION`. A `ListBox`'s `visibleRowCount` defaults to `8`.

```kotlin
val languages = listOf("Kotlin", "Java", "Scala")
var selection by remember { mutableStateOf(listOf(0)) }

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

A `Table`'s columns are declared with a header, a value extractor over the row type, and optionally
in-place editing. A committed cell edit arrives at `onCellEdit` with the row, its index and the new
value; the displayed value changes when the next composition supplies fresh `rows`. Put a table in a
`ScrollPane` to scroll it and to show its column header.

```kotlin
var selection by remember { mutableStateOf(emptyList<Int>()) }

ScrollPane {
    content {
        Table(
            rows = people,
            selectedRowIndices = selection,
            onSelectionChange = { selection = it },
        ) {
            column("Name", isEditable = true, onCellEdit = { row, _, value -> rename(row, value) }) { it.name }
            column("Age") { it.age }
        }
    }
}
```

A `Tree` is built from your own node type: a `root`, a `children` function, and a `label` for the
text of a node. Selection and expansion are both expressed as index paths - a `List<Int>` per node,
walked from the root - so they mean the same thing across two compositions of the same shape.
`rootVisible` defaults to `true`.

```kotlin
var expanded by remember { mutableStateOf(listOf(emptyList<Int>())) }

Tree(
    root = fileTree,
    children = { it.children },
    label = { it.label },
    expandedPaths = expanded,
    onExpansionChange = { expanded = it },
)
```

---

## Containers and layout

Swing lays everything out, so a container is a layout manager plus the children you declare into it.
Two kinds appear here: containers whose children are a plain content lambda, and containers whose
children go into named slots declared through a receiver DSL.

| Component | What it is |
| --- | --- |
| `BoxPanel` | A single-axis stack over `BoxLayout`. |
| `Row`, `Column` | `BoxPanel` fixed to the horizontal and vertical axis. |
| `FlowPanel` | A wrapping strip over `FlowLayout`. |
| `GridPanel` | Equal-sized cells over `GridLayout`. |
| `BorderPanel` | Four edges and a filling centre over `BorderLayout`, as named slots. |
| `GridBagPanel` | Cell-by-cell placement over `GridBagLayout`, one `item(...)` per child. |
| `CardPanel` | A deck over `CardLayout`, one card visible at a time, addressed by key. |
| `TabbedPane` | Tabs over `JTabbedPane`, each declared with `tab(...)`. |
| `SplitPane` | Two sides and a draggable divider over `JSplitPane`. |
| `ScrollPane` | Scrollable content plus header and corner slots over `JScrollPane`. |
| `ToolBar` | A bar of controls over `JToolBar`; `ToolBarSeparator` divides its groups. |
| `LayeredPane` | Children stacked on integer depth layers over `JLayeredPane`. |
| `DesktopPane` | Floating internal frames over `JDesktopPane`. |

A slot hosts exactly one child. Declaring a slot adds its child, redeclaring it replaces the child,
and dropping the declaration removes it - so showing and hiding part of a layout is composing and
not composing it.

`BorderPanel` offers two families of edge: the absolute compass (`north`, `south`, `east`, `west`)
and the orientation-aware one (`pageStart`, `pageEnd`, `lineStart`, `lineEnd`), which resolve
against the panel's `ComponentOrientation`. `center` is shared. Use one family per edge. Its
`hgap`/`vgap` default to `0`.

```kotlin
BorderPanel {
    pageStart { Label("Title") }
    center { Body() }
    pageEnd { Label("Status") }
}
```

`Row` and `Column` are the two axes of `BoxPanel` you reach for most; `FlowPanel` centres its
children and gaps them by `5` pixels, and `GridPanel` starts as a single row that grows a column per
child, with no gaps.

```kotlin
Column {
    Row {
        Button("Back", onClick = ::open)
        Button("Forward", onClick = ::open)
    }
    FlowPanel(hgap = 8, vgap = 4) { Body() }
    GridPanel(rows = 2, cols = 2, hgap = 4, vgap = 4) { Body() }
}
```

`GridBagPanel`'s `item` takes one parameter per `GridBagConstraints` field, under the field's own
name and with its own default, so a grid-bag layout written against Swing carries over field for
field.

```kotlin
var name by remember { mutableStateOf("") }
GridBagPanel {
    item(gridx = 0, gridy = 0, anchor = GridBagConstraints.LINE_END, insets = Insets(4, 4, 4, 4)) {
        Label("Name:")
    }
    item(gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL) {
        TextField(name, onValueChange = { name = it })
    }
}
```

`CardPanel` shows the card whose key equals its `selectedCard`, so switching pages is a state write.

```kotlin
var step by remember { mutableStateOf("details") }
CardPanel(selectedCard = step) {
    card("details") { Body() }
    card("payment") { Body() }
}
Button("Next", onClick = { step = "payment" })
```

A `TabbedPane` tab carries a title, icon, tooltip and enabled flag, and can take over what the tab
strip renders for it with a `header` composable - the title and icon still name it. A tab's identity
is positional: state its body remembers belongs to the position rather than to the declaration, so
hoist anything that must outlive an insertion above the pane. `tabPlacement` defaults to
`JTabbedPane.TOP` and `tabLayoutPolicy` to `JTabbedPane.WRAP_TAB_LAYOUT`.

```kotlin
var tab by remember { mutableStateOf(0) }
TabbedPane(selectedIndex = tab, onSelectedIndexChange = { tab = it }) {
    tab(title = "Source") { Body() }
    tab(title = "Console", header = { Label("Console", modifier = SwingModifier.icon(saveIcon)) }) { Body() }
}
```

A `SplitPane`'s `first` side is the left or the top depending on `orientation`, which defaults to
`JSplitPane.HORIZONTAL_SPLIT`. `dividerLocation` is declared and `onDividerLocationChange` reports
where the user dragged it; `resizeWeight` decides which side keeps extra space, and
`oneTouchExpandable` and `dividerSize` shape the divider itself.

```kotlin
var divider by remember { mutableStateOf(240) }
SplitPane(
    orientation = JSplitPane.HORIZONTAL_SPLIT,
    dividerLocation = divider,
    onDividerLocationChange = { divider = it },
    resizeWeight = 0.3,
    oneTouchExpandable = true,
) {
    first { Body() }
    second { Body() }
}
```

A `ScrollPane` has four slots: `content`, `rowHeader`, `columnHeader` and `corner(key)`. Both
scrollbar policies default to as-needed.
Its scroll position is hoisted into a [`ScrollState`](#scrollstate).

```kotlin
ScrollPane(horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
    columnHeader { Label("Rows") }
    content { Body() }
    corner(JScrollPane.UPPER_TRAILING_CORNER) { Label("#") }
}
```

A `ToolBar` is horizontal and floatable by default.

```kotlin
ToolBar(floatable = false, rollover = true) {
    Button("Open", onClick = ::open)
    Button("Save", onClick = ::save)
    ToolBarSeparator()
    Button("Run", onClick = ::open)
}
```

A `LayeredPane` stacks children on integer depths - higher layers paint above lower ones, and within
one layer later declarations paint above earlier ones. It lays nothing out, so each child carries
its own bounds.

```kotlin
LayeredPane {
    layer(JLayeredPane.DEFAULT_LAYER) { Body() }
    layer(JLayeredPane.PALETTE_LAYER) {
        Label("Floating", modifier = SwingModifier.bounds(16, 16, 120, 24))
    }
}
```

A `DesktopPane` hosts internal frames. Each frame declares a title, its window controls, and either
plain `bounds` - where it starts, and wherever the user then leaves it - or an
[`InternalFrameState`](#internalframestate), which makes its geometry and its window state two-way.
The close control is controlled: activating it calls `onClose`, and the frame closes when you stop
declaring it. Every control is off by default, matching a fresh `JInternalFrame`.

A frame keeps the window it was realized as - and the position the user dragged it to - as long as its
declarations name one identity: the `InternalFrameState` it was declared with, or the `key` you give a
plain-`bounds` frame. Give a key to every frame in a list you add to and remove from, so that removing
one leaves the frames declared after it where the user put them.

```kotlin
val palette = rememberInternalFrameState(Rectangle(24, 24, 320, 200))
var frames by remember { mutableStateOf(1) }

Label("Palette at ${palette.x}, ${palette.y}, ${palette.width} x ${palette.height}")
Button("Send home", onClick = { palette.bounds = Rectangle(24, 24, 320, 200) })
DesktopPane {
    internalFrame(
        title = "Palette",
        state = palette,
        controls = InternalFrameControls(closable = true, resizable = true),
        onClose = { frames = 0 },
    ) {
        Body()
    }
}
```

To place children under a layout manager of your own, `SwingConstraint` carries the placement for a
container you write yourself - see
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md#placing-children-under-constraints).

---

## Windows and dialogs

| Entry point | What it is |
| --- | --- |
| `application { }` | Runs a Compose application until its last composition ends, blocking the caller. |
| `awaitApplication { }` | The suspending form, for an application started from a coroutine. |
| `CoroutineScope.launchApplication { }` | The same, launched as a `Job` in a scope you own. |
| `Window` | A top-level frame over `JFrame`, geometry hoisted into a [`WindowState`](#windowstate). |
| `Dialog` | A dialog over `JDialog`, geometry hoisted into a [`DialogState`](#dialogstate). |

All three entry points take the same `ApplicationScope` content and give it `exitApplication()`.
Windows and dialogs composed inside one run as part of that application's composition, so
application-scope state and `CompositionLocal`s flow into their content.

A window's content is given the window as its scope, and what the window carries besides that content
is declared there: `MenuBar { }` is the one such declaration, so it can only be written where there is
a window to carry the bar.

A window exists while it is composed: opening one is composing it, closing it is not composing it,
and `onCloseRequest` is where you flip the state that decides. `title` starts empty, `resizable` is
`true`, `alwaysOnTop` is `false`, `undecorated` is `false`, and a dialog's `modality` is
`ModalityType.MODELESS`. A `Dialog` composed inside a window's content takes that window as its
owner.

```kotlin
application {
    val state = rememberWindowState(position = WindowPosition.CenteredOnScreen, size = Dimension(900, 600))

    Window(onCloseRequest = ::exitApplication, state = state, title = "Editor") {
        Column {
            Label("${state.width} x ${state.height} at ${state.position}")
            Button("Centre", onClick = { state.position = WindowPosition.CenteredOnScreen })
            Button("Widen", onClick = { state.width += 80 })
        }
    }
}
```

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
            center { Label("Delete the selection?") }
            pageEnd { Button("OK", onClick = { asking = false }) }
        }
    }
}
```

To mount a composition into a Swing window or container you already own, use `setContent` - see
[`ARCHITECTURE.md`](ARCHITECTURE.md#mounting-a-composition).

---

## Menus

| Component | What it is |
| --- | --- |
| `Menu` | A menu over `JMenu` holding further menu content; nest it for a submenu. |
| `MenuItem` | A command over `JMenuItem`, with an `accelerator` and `onClick`. |
| `CheckBoxMenuItem` | A checkable item over `JCheckBoxMenuItem`. |
| `RadioButtonMenuItem` | One exclusive item over `JRadioButtonMenuItem`. |
| `RadioButtonMenuGroup` | A set of mutually exclusive menu items, selected by index. |
| `MenuSeparator` | A divider between menu items. |
| `MenuBar` | The menu bar of a window, declared in that window's content. |

Menu content is composed like any other content: `MenuBar { }` in the content of a `Window` or a
`Dialog` declares that window's menu bar, `JMenuBar.setContent { }` composes a bar you own yourself,
and the same tree serves a context menu (the `contextMenu` modifier) and a [`Tray`](#system-tray)
popup. An item's label, checked flag and enabled state follow composition state like any other
component's. An `accelerator` is a `KeyStroke` that fires the item without opening the menu; a
mnemonic is the `mnemonic` modifier.

```kotlin
val bar = JMenuBar()
bar.setContent {
    var wordWrap by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(0) }

    Menu("File") {
        MenuItem("Open", accelerator = KeyStroke.getKeyStroke("control O"), onClick = ::open)
        MenuSeparator()
        MenuItem("Save", accelerator = KeyStroke.getKeyStroke("control S"), onClick = ::save)
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

---

## System tray

`Tray` registers a system-tray icon for as long as it is composed. Its popup is a menu tree,
composed fresh each time the icon is asked for one, so the items reflect current state. Activating
the icon runs `onAction`. `imageAutoSize` defaults to `false`, painting the image at its own size;
`java.awt.SystemTray.isSupported()` tells you whether the platform has a tray at all.

```kotlin
application {
    var paused by remember { mutableStateOf(false) }

    Tray(image = trayImage, tooltip = "Indexer") {
        CheckBoxMenuItem("Paused", checked = paused, onCheckedChange = { paused = it })
        MenuSeparator()
        MenuItem("Quit", onClick = ::exitApplication)
    }
}
```

---

## Hoisted state

Some values are awkward to pass down and report back one at a time: the whole content of an editor,
where the user dragged a window. For those, the library hands you a state holder to hoist next to
your own state.

A state holder is:

- **the owner of the value.** It holds the document, the spinner's model, the geometry. There is no
  second copy to keep in sync and no round-trip per keystroke or per pixel.
- **snapshot-observable.** Reading a property inside a composable - or in a `snapshotFlow` collector -
  subscribes to later changes, so the reader recomposes when the value changes for any reason.
- **two-way.** Assigning to a property drives the widget; the user's own gesture writes the new value
  back into the same property. Both directions go through one place, which is what makes a live
  readout as easy as a control.

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
    Button("Undo", modifier = SwingModifier.enabled(note.canUndo), onClick = note::undo)
}
```

### `SpinnerState`

Owns the `SpinnerModel` a `Spinner` renders, so a step taken through the spinner and a value written
through the state are the same content. `value` is the observable property.

`rememberSpinnerState(initialValue, min, max, step)` builds a numeric spinner - a `null` bound is
open on that side, and `step` defaults to `1`. The bounds are declarative: changing one updates the
spinner in place. `rememberSpinnerState(items, initialSelectedIndex)` builds one that steps through
a list instead. `model` is the model itself.

```kotlin
val count = rememberSpinnerState(initialValue = 3, min = 0, max = 10)
Row {
    Spinner(count)
    Label("Count is ${count.value}")
}
```

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
`PlatformDefault`, `CenteredOnScreen`, `CenteredOnOwner`. A request carries no coordinates of its
own, and the placement it resolves to is written back as an `Absolute`, so reading `position` after
the window is shown always gives concrete coordinates.

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

The position outlives the content it was reached in, so a pane that leaves the composition and comes back
comes back where the user left it.

---

## Anything else

The catalogue is not the boundary of what you can compose. Any Swing `Component` can be hosted
directly with `SwingNode`, any menu component with `MenuNode`, and a property no builder covers can be
written as a modifier element of your own. [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md) is the guide
to all three.
