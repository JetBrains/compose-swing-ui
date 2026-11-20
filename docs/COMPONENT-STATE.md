# State a component shares with its user

A Swing widget the user can change is a second writer of its own properties. This document is how a component of your own keeps such a property in step with what the composition declares, and how to carry state that a declared value with a callback beside it cannot. See [Writing components](CUSTOM-COMPONENTS.md) for building the component itself.

## Properties the user can also change

`set` and `update` both assume the composition is the property's only writer. Some widget properties have a second writer: the widget itself, through the user's own interaction - a checkbox the user clicks, a divider the user drags, a selection the user picks. For those, what the composition declares and what the widget currently holds can diverge at any moment, and neither `set` nor `update` notices - a declaration that repeats the value it declared last pass is skipped, so the widget stays wherever the user left it.

### The mechanism

These pieces work together. For why settling works this way, see [Architecture](ARCHITECTURE.md).

- `rememberMirrorState(initial)` remembers a `MirrorState<V>` seeded with `initial`, what the widget holds on the first pass: usually the first declaration, but `false` for a `ToolBar`'s `floating`, because a bar is docked when it is built. It is a `State<V>` mirroring what the widget currently holds, so reading `mirror.value` while composing subscribes to the user changing the widget. Where a body needs that subscription but not the value, call `mirror.subscribe()` instead of reading a value it discards.
- `mirror.report(published) { ... }` is called from the widget's own listener with the value the widget just published. It updates the mirror, runs the block for a change the user made - not for a value that only arrived because the wrapper's own write to the widget just produced it - and then settles the composition on the caller's answer. See *When a change settles* below for which changes are reported.
- `mirror.observed(published)` is the same without the settling: it updates the mirror and answers whether the change is news, leaving the pass to arrive from the event queue a few cycles later. Use it where settling inside the event would be wrong - see below. Call it for every value the widget publishes, in the order it publishes them.
- `mirror.report { ... }` - the one-argument form, taking no value - runs the block and settles for a change `observed` already took on an earlier channel.
- `declare(value, mirror, read, write)` in the `update` block settles the widget on `value`: it writes through `mirror` wherever `read()` does not already answer with it, and keeps the mirror in step with whatever the widget ends up holding. Unlike `set`, it runs again on the pass that follows a change away from the declaration.
- `declare` also takes a `write` of two arguments, `(held, declared)`, where assigning the whole value would destroy what the rest of it anchors: writing a document's text moves the caret to its end, so a text component writes back only the span that changed. `held` is what the settlement already read, so that write costs no second read.
- `mirror.settle { ... }` is for a write the widget does not simply accept. Installing a row filter makes a table drop the selected rows it hides. Declaring a tree's open nodes and its selection separately lets the tree resolve the pair its own way, since a node is only selectable while its ancestors are open. In both, what the widget ends up holding is not what you wrote, and `declare` cannot see it: it reads back only the one property it wrote. So wrap the write yourself and say what the widget was left holding - `answered(value)` for a value you read back, or `unchanged()` where this property did not move. Saying neither throws. Say nothing at all and the mirror goes on claiming the widget holds what you asked for: the next pass finds no difference, nothing puts the declaration back, and the user's own change to that property is never reported.

### When a change settles

`report` gets the declaration back on the widget before the repaint that change asked for is served, and the caller's block runs first.

Report a discrete interaction that way: a click, a selection, a step. Two kinds of change should use `observed` instead:

- **A continuous gesture** - a drag, a resize. It reports a change per step, and the value one step lands on is replaced by the next well inside a display refresh interval, so there is nothing visible to correct and batching the steps is worth more than the pass.
- **A change mirrored on one channel and reported on another.** A toggle publishes an item event before it publishes its action event, so a mirror riding the item channel has already recorded the change by the time the action channel reports it. Settling on the earlier channel would put the declaration back before the caller heard about the change at all. Mirror there with `observed`, and settle on the reporting channel: `actionListener<JToggleButton> { mirror.report { onSelectedChange(isSelected) } }`.

Neither choice leaves a change the user makes showing before it is settled: the runtime listens for the events a declared value changes on - key, mouse, motion, input method, and the focus loss a formatted field commits its text on - and queues its frame ahead of the repaint the change provokes, whether or not a wrapper reports it. `report` settles inside the event, so that queued frame finds nothing owed and stands down.

That still leaves `report` doing work the runtime cannot. A change the runtime is handed no event for is settled only by the pass that follows it: a change a timer or a caller makes with no event in hand, and a drop, which Swing dispatches without offering it to toolkit listeners. So report a discrete interaction wherever the component can, and reach for `observed` where one of the two reasons above applies.

A mirror reports through a node, and `declare` states that for the declaration it settles - so a component built the way above needs nothing more. A component that settles a mirror some other way - applying two declarations the widget resolves together, or reading back a property no declaration is written through - applies it itself with `applyMirror(mirror)` in the `update` block, and makes the write inside `mirror.settle`. A mirror that reports without either fails at the first change rather than quietly taking the slower path.

The pieces belong to one node. Remember the `MirrorState` in the component's own body, beside the `SwingNode` it settles, and call `declare` exactly once per pass: what a declaration is compared against lives on the mirror rather than in the composition, so a mirror shared between nodes, or remembered above the node it settles, goes on answering for a widget that is no longer there - and the widget built in its place keeps its constructor's value instead of the standing declaration. `declare` also makes one `set` call, so a `declare` inside a conditional shifts every later slot in the `update` block, exactly as a conditional `set` does.

### A worked example

This is the shape for a widget whose change arrives and reports on one channel - a checkbox outside a button group, `checked` in and `onCheckedChange` out over the two-way `isSelected` property:

<!--- INCLUDE .*custom-state-01.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.JCheckBox
-->

```kotlin
@Composable
fun MyCheckBox(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    // The mirror this component settles `checked` through, seeded with the first declaration.
    val mirror = rememberMirrorState(checked)
    SwingNode(
        factory = { JCheckBox() },
        // The box publishes its new value for every toggle, its own and the user's alike.
        // `report` tells which is which by value - a toggle that lands on the declaration is the
        // declaration arriving, not a change - and settles the composition once the caller has had
        // it. The lambda is read when the event fires, so writing it here holds nothing
        // across compositions.
        modifier =
            modifier.actionListener { event ->
                mirror.report((event.source as JCheckBox).isSelected, onCheckedChange)
            },
        update = {
            set(text) { this.text = it }
            // Settles `checked` against the box whenever either side has changed, rather than only when
            // this pass's declaration differs from the last one.
            declare(checked, mirror, JCheckBox::isSelected, JCheckBox::setSelected)
        },
    )
}
```

<!--- KNIT example-custom-state-01.kt -->

### The component is fully controlled

Once a property is bound this way, the component is **fully controlled**: a change the caller does not adopt never stands. Clicking the box flips `isSelected` immediately - that click is a real change, and the box shows it - and `report` hands it to `onCheckedChange` as news. But if `onCheckedChange` does not feed a new `checked` back in, the next pass finds the box holding a value other than the one this composition still declares, and writes the declaration back over it, undoing the click. Adopting the change in the callback - folding it into whatever state `checked` is computed from - is what makes it stick.

### `onSettled` - when the widget answers a write with something else

`declare` takes an optional `onSettled` block, invoked when the widget does not end up holding what was asked for - a value clamped to a model's range, an index or path a widget no longer has. It receives whatever the widget actually settled on, called only after the widget has been left alone, so what it reports is final. It defaults to doing nothing because most two-way properties admit every value the composition could declare for them - a checkbox is either selected or not, and both values always take - so there is nothing to report.

## The applicability boundary: not every user-movable property is `declare`'s

`declare` re-asserts the declaration on every pass. That is right for a property that holds a genuine value - a selection index, a selected path, a checkbox's state - and wrong for one whose declared value is a sentinel or a request rather than a value to hold, or one the widget only resolves later, at layout time.

`JSplitPane`'s `dividerLocation` is the case in point: a negative offset does not mean "the divider sits at that offset", it means "derive the divider's position from the two sides' preferred sizes" - a derivation that only happens once the pane is laid out. Re-asserting such a declaration on every pass would fight the user on every single recomposition, not only the one after they moved the divider.

For a property like this, apply on change instead of declaring it: compare against the widget's current value yourself, and make the write inside `mirror.write { }` so it still marks itself as the wrapper's own and the listener does not mistake it for a change to report:

```kotlin
set(dividerLocation) { location ->
    if (this.dividerLocation != location) {
        mirror.write { this.dividerLocation = location }
    }
}
```

<!--- CLEAR -->

The `mirror` here is the same `MirrorState` its listener calls `observed` on - `write` is what lets the two share one mirror without fighting the user the way re-asserting the declaration on every pass would.

A request can also have no callback. `InternalFrame`'s plain `bounds` are applied when they change and then left to the user, so recomposition keeps the user's later moves.

## Taking a value in and reporting a change out

When your component takes a controlled value directly, require the value and its change callback. The declared value stays in effect until the caller adopts an interaction by supplying the new value.

The API for user-editable state depends on who owns the value. See [Shapes for state the user can change](ARCHITECTURE.md#shapes-for-state-the-user-can-change).

1. **Declared** - the composition owns the value. The parameter is required, and the callback that reports the user moving it is required immediately after it: `CheckBox`'s `checked` and `onCheckedChange`, `Slider`'s `value` and `onValueChange`, `TabbedPane`'s `selectedIndex` and `onSelectedIndexChange`.
2. **Optional state holder.** Leave `state` at `null` to let Swing keep the selection. Pass a `ListState` to `ListBox`, a `TableState` to `Table`, or a `TreeState` to `Tree` to set or observe the selection. A given state makes the selection the composition's: it is applied on every pass, and the state receives the user's changes. `ScrollPane` instead defaults `state` to `rememberScrollState()`, so the pane always has a `ScrollState`, and passing one lets the caller read and move it.
3. **Model-owned** - the caller hands the widget a model, which takes the value out of the composition altogether. The model holds it, the widget renders whatever it holds, and nothing is declared over it, so there is no half for a callback to pair with. The callback is then only a report of where the model has got to, and stays optional after `modifier`, as `Slider(model, ...)` and `ComboBox(model, ...)` keep it.
4. **Defaulted to a real value** - `selected: Boolean = false` - is none of the three, and is the defect the first three rule out. It declares a value the user can move while letting the caller leave out the callback that reports the move, and the widget is then frozen as above.

Put a required value and its callback before `modifier`. Place optional state and callbacks after `modifier`. The `ComposableParamOrder` check accounts for trailing-lambda call syntax when it checks the parameter order.

```kotlin
// Declared: `checked` is the composition's, so it and its callback lead.
@Composable
public fun CheckBox(
    text: @Nls String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
)

// Undeclared: leaving `state` out leaves the selection with the list, as Swing keeps it, and the state
// follows `modifier` and `style`.
@Composable
public fun <T> ListBox(
    items: List<T>,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ListBoxStyleScope> = SwingStyle,
    state: ListState? = null,
    // ...
)

// Model-owned: the model holds the value, so the callback only reports it.
@Composable
public fun Slider(
    model: BoundedRangeModel,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    onValueChange: (Int) -> Unit = {},
    // ...
)
```

<!--- CLEAR -->

For a `ComboBox` over `items`, the nullable `selectedItem` and its callback are required. Pass `selectedItem = null` to declare an empty selection.

A sentinel default is a different thing from a real value. `SplitPane`'s `dividerLocation: Int = -1` and `ToolBar`'s `floating: Boolean = false` are what the bare widget holds: `JSplitPane` reports `-1` until a location is set, and `JToolBar` keeps no floating property of its own - the look and feel's `BasicToolBarUI` holds it, and a bar not yet in a window has nothing to float beside. Matching the widget outranks writing the sentinel as `null`, and boxing an `Int` or a `Boolean` for it would buy nothing. Carrying a default, both sit after `modifier` beside their optional callback. The divider's sentinel is also a request the pane resolves at layout time rather than a value to re-assert, which is the applicability boundary above; a tool bar's `floating` is a value, and is settled every pass.

A callback reporting something other than a declared value - a settle, a commit, a validity flag, a veto - is supplementary the same way: optional, after `modifier`, as `Slider`'s `onValueSettled`, `ComboBox`'s `onValueCommit`, `FormattedTextField`'s `onEditValidChange` and `Tree`'s `onWillExpand` are.

A callback that reports an action rather than a state - `Button`'s `onClick`, `MenuItem`'s `onClick`, `Tray`'s `onAction`, `EditorPane`'s `onLinkActivate`, `InternalFrame`'s `onClose` - pairs with nothing, and is still required: a button whose click goes nowhere is as inert as a checkbox that cannot be ticked. `onClose` is the sharpest case: an internal frame's close control is controlled - the frame never closes itself - so a frame declared without `onClose` shows a close button that does nothing. Being required, an action callback leads: `Button(text, onClick, modifier = ...)`.

The trailing position never holds a callback. It is the slot the caller fills with what the component shows: a composable content block, as `TabbedPane` and `SplitPane` trail with; a scope builder, as `RadioGroup` does; or a draw block - `Canvas(modifier = ..., onDraw)` trails with `onDraw` because what it paints is content, not a report. A modifier is the exception: it has no content slot to reserve, so `SwingModifier.onKeyStroke { }` and `SwingModifier.actionListener { }` keep the trailing lambda, the way `Modifier.clickable { }` does.

A callback can be the switch for what it reports. A table column is editable exactly when it sets `onCellEdit`, so a read-only column declares no handler and every edit the table accepts reaches one. A callback that only a sibling flag, defaulting to off, can reach stays optional: `Tree`'s `onNodeEdit` sits behind `isEditable`.

### A callback your component calls itself

When your component calls a callback while recomposition applies a declaration - from `update`, say - and the callback throws, the exception stops the recomposer, and no content that depends on it updates again. Make that one call inside `report { }` on the `update` receiver (`SwingNodeUpdater.report`). It sends the exception, unchanged, to the thread's uncaught-exception handler, which is where Swing reports a listener's exception, and returns normally, so the pass goes on applying. Keep the component's own checks outside the block, so their failures still reach the caller.

A listener adapter in a `CallbackRegistration` calls the caller's callback the same way, through `DeclaredCallback.report { }` on the adapter's `callback` parameter, where Swing raises the event inside a write: a property change, a child insertion, a model write. The callback it passes to the block is the one the modifier declares right now. A listener that only a gesture or a posted event reaches may read `callback.current` directly, and Swing's event pump handles its throw; so does a veto, whose throw must reach Swing.

The library already does this for `declare`'s `onSettled` and for a listener that a write inside `mirror.write { }` triggers.

## Writing a state holder

A hoistable state holder is an `XState` class the caller keeps next to their own state, with a `rememberXState` factory beside it. See [Shapes for state the user can change](ARCHITECTURE.md#shapes-for-state-the-user-can-change) to choose the shape.

A factory parameter can seed the holder, constrain its live model, or select the model it wraps. For example, the coordinates passed to `rememberScrollState` seed its position once. Use the holder to move the pane after that. A constraint such as a range bound changes the existing model. Supplying a different model gives the caller a holder for that model.

### The model keeps the value, the holder makes it observable

A widget with a model of its own - a spinner's `SpinnerModel`, a text component's `Document`, a range widget's `BoundedRangeModel` - already holds the value and already announces every change to it. A holder over such a widget owns that model and hands it to the widget, and what it adds is the one thing the model does not have: a reader composing against it recomposes when the value changes. So the holder keeps no second copy of the value to write and reconcile; it keeps whatever makes a read of the model into a snapshot read.

Two shapes do that, and what the value costs to produce decides which:

- **A mirror** - a snapshot-state field refreshed from the model's own change notification, read straight back out by the property's getter. `ScrollState` mirrors a viewport's position and its metrics this way. Reach for it wherever the value is small and fixed-size, which is nearly always.
- **A generation counter** - a single `mutableIntStateOf` the change listener bumps and nothing else reads for its own sake. Each derived property reads the counter first, which registers the subscription, then computes its answer from the model. `DocumentState` carries `text`, `canUndo` and `canRedo` this way: a keystroke bumps one int and invalidates whoever was reading, and the document is walked only when a caller actually asks for its content. Reach for it where mirroring would materialize something large on every change whether or not anything reads it - the whole text of a document is the case in point - and pay for the extra indirection only there.

The two are not exclusive within one holder. `DocumentState` runs a counter for its text and mirrors its selection directly, because a selection is a small fixed-size value and re-deriving it would buy nothing.

### A worked example: a range holder

<!--- INCLUDE .*custom-state-02.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import javax.swing.BoundedRangeModel
import javax.swing.DefaultBoundedRangeModel
import javax.swing.event.ChangeListener
-->

```kotlin
class RangeState internal constructor(
    val model: BoundedRangeModel,
) : RememberObserver {
    // The mirror: refreshed for every change the model announces, so reading `value` while composing
    // subscribes to the user changing the widget as much as to a write made through this holder.
    private var observedValue by mutableStateOf(model.value)

    private val changeListener = ChangeListener { observedValue = model.value }

    var value: Int
        get() = observedValue
        set(value) {
            // Push only a value the model does not already hold.
            if (model.value != value) {
                model.value = value
                // Re-read: what the model settled on is what this reports.
                observedValue = model.value
            }
        }

    override fun onRemembered() {
        model.addChangeListener(changeListener)
        // The model may have changed between construction and this holder being remembered.
        observedValue = model.value
    }

    override fun onForgotten() {
        model.removeChangeListener(changeListener)
    }

    override fun onAbandoned(): Unit = onForgotten()
}

@Composable
fun rememberRangeState(
    initialValue: Int,
    min: Int = 0,
    max: Int = 100,
): RangeState {
    val model = remember { DefaultBoundedRangeModel(initialValue, 0, min, max) }
    SideEffect {
        if (model.minimum != min) model.minimum = min
        if (model.maximum != max) model.maximum = max
    }
    return remember { RangeState(model) }
}
```

<!--- KNIT example-custom-state-02.kt -->

### What the widget settles on is what the holder reports

A write through a holder is a request, and the model answers it. A value can come back clamped to a bound, snapped to a step, refused outright; a caret offset past the end of a document lands at the end. Read the value back out of the model after the write and mirror **that**, never the value that was asked for - the `observedValue = model.value` above rather than `observedValue = value`. A holder that mirrors the request claims a value the widget does not have, and nothing later corrects it: a model settles some writes without announcing anything for the change listener to carry.

Where the value the model hands back is an object the model steps **in place** - the same instance, mutated - a structurally compared mirror cannot tell that from no change at all, and every reader stands on a value the model has already stepped past. Declare the mirror `mutableStateOf(model.value, neverEqualPolicy())` there and every refresh invalidates its readers regardless of what they would have compared to. A holder over a model the caller wrote has to reckon with that, since nothing constrains what such a model hands back or whether it steps that value in place. What keeps the policy from recomposing on every pass is the model and the guard together: a refresh only ever follows a change the model announced, and a model announces the changes it actually made, while the guard on the setter keeps the holder from pushing a value the model already holds and provoking an announcement of its own. Reach for the policy and you need the guard with it.

### Constraints are declarations, and are written onto the model

What the model holds is the holder's; what bounds it stays something the caller declares. A range's `min` and `max`, a spinner's step, the items a list model steps through, are parameters of the `remember` factory, and a later change to one of them is written onto the live model from a `SideEffect` - as `rememberRangeState` above does - rather than by building a new model. Rebuilding would take the current value and every listener with it, the widget's and the holder's own, and hand the caller a holder driving a model nothing renders.

That is also what the two `remember` calls in the factory say. A model the holder owns is remembered without keys, so a later declaration reaches it through the `SideEffect` and never rebuilds it. A model the caller supplies is remembered on its identity instead - `remember(model) { RangeState(model) }` - so handing over a different model builds the holder that drives it, and the widget switches to rendering it.

Writing a constraint is not writing the value, and what a tightened bound does to a value already outside it is the model's business rather than the holder's: write the bound and let the model answer. A `SpinnerNumberModel` leaves the value where it is, outside the range that was just declared, while a `DefaultBoundedRangeModel` pulls it into range and announces the change. Either way the mirror is refreshed from what the model announced, so the holder reports whichever happened - which is the same rule as for a write, arriving from the other side.

### The holder gives back what it took

A holder listening to a model is listening to something that can outlive it. A document or model the caller supplied is the caller's, and it stays alive - and reachable - after the holder that listened to it is gone. Implement `RememberObserver`: attach in `onRemembered`, detach in `onForgotten`, and delegate `onAbandoned` to `onForgotten`, so a holder created for a composition that never applied lets go too. Without it a discarded holder stays reachable from the live model and its listener keeps firing, on state nothing reads any more. A listener attached in `onRemembered` also wants the value re-read there, since the model is free to change between the holder being constructed and being remembered.

Reaching the component that renders the holder is a separate binding, and `set` is the wrong channel for it: the binding has to end exactly when the component stops rendering the holder - the node released or deactivated - and that is a modifier node's lifecycle. Bind through an element of your own, the pair [A property written by an element](STYLES.md#a-property-written-by-an-element) describes: attach the holder in `update`, release it in `onDetach`, and compare the element by identity, as [Equality and skipping](MODIFIERS.md#equality-and-skipping) asks of anything a node registers, so a holder that only looks like the bound one is still a different holder to give the component over to.

### Only the inputs are state

A holder keeps snapshot state for what it is told - the mirrored value, the metrics read off the widget - and for nothing that follows from those. Anything computable from them is computed when it is read. A getter that reads the state it derives from subscribes its reader to that state on every read, so it can neither go stale nor need keeping in sync; a second field written from the setter that fed the first can do both, and a reader of it never learns that its inputs changed. `ScrollState`'s `maxX` is that getter - the view's width less the visible extent, floored at zero - and it follows the pane with no third field to invalidate.

`derivedStateOf` earns its place where the derivation **narrows**: where many changes of the inputs produce few changes of the result. `ScrollState`'s `canScrollForwardY` is `y < maxY` over values that move with every scroll step and every resize, while the answer itself changes only when an end is reached or left. Wrapping it caches the result and holds its readers still until the answer changes, so a toolbar button that only wants to know whether there is anywhere left to scroll is not recomposed by scrolling. Where the result changes about as often as its inputs do, or where the property only re-exposes state under another name - a `bounds` over `x`, `y`, `width` and `height` - a plain getter is right, and `derivedStateOf` would buy a cache and a subscription for nothing.
