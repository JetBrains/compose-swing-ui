# Defining a custom Swing component in Compose

Compose Swing UI ships wrappers for the common widgets (`Button`, `TextField`, `Slider`, ...), but
real applications host many bespoke Swing components. Wrapping your own component uses the same
public `SwingNode` API every built-in wrapper is built on. This guide shows how.

<!--- INCLUDE .*modifier.*
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JComponent
-->

## The mental model

A composable component is a function that wraps a single Swing `Component`. You describe the
component once and let Compose keep it in sync with state:

```kotlin
@Composable
public fun MyWidget(/* state + callbacks */) {
    SwingNode(
        factory = { /* create the Swing component */ },
        update = { /* reactively push state onto it */ },
        onRelease = { /* optional cleanup */ },
    )
}
```

<!--- CLEAR -->

- `factory` runs **once**, when the node enters the composition. Build (and do one-time
  configuration of) your Swing component here. Whatever it reads from the composable body, it reads
  once: a layout manager, model or value constructed there is rebuilt on every later composition while
  the component keeps the first one, so anything the component is to go on using is either built inside
  `factory` or `remember`ed outside it. The component's own layout manager is the usual one to get
  wrong - a container that later hands its children placements computed against a freshly built layout
  is describing a layout nothing is laid out by.
- `update` runs on the first composition and on **every recomposition**. Inside it you declare which
  pieces of state map onto which component properties; the framework only re-applies the ones that
  actually changed. It is not a composable scope: `@DisallowComposableCalls` on it makes a composable
  call inside it - `remember` included - a compile-time error, so read state in the composable body and
  pass the value in.
- `onRelease` runs **once**, when the node leaves the composition for good.

`SwingNode` is `inline` and `reified` on the component type, so inside `update` the component is
available as the strongly-typed `this`.

## What the composition owns

Two things about every component belong to the composition rather than to your code:

- **the value of any property it declares** - a modifier applies a property through a node that first
  records what the component had, and puts that back when the modifier leaves the chain;
- **the children of any container it builds** - insertion, order and removal are the applier's.

So a component your composition built is not a component to reach into. Writing a property the
composition also writes means the next recomposition asserts what the composition declares and your
write is gone; adding or removing a child behind the applier's back leaves the two disagreeing about
what the container holds. Neither failure is loud - the value simply reverts, or the tree drifts.

The only component your code constructs is the one its own `factory` returns. A container's children
arrive as composables the container emits, and the applier turns each into a component and places it;
a child your code instantiates has no node behind it, so nothing updates it, reuses it or releases it.

Structural change is the applier's too. A container re-composes the children this composition declares
and the applier settles the difference: a child that has gone leaves, a new one arrives, one that moved
is carried to its new position with the component it already had. Which child is which is the place in
the caller's code that declared it, so an `if` around one leaves the components its siblings already
have, and everything those components hold, alone. Children written at one place - a loop over a list -
share that place, and are told apart by the position they arrive in until `key` gives each an identity
of its own. Key a child by what identifies it among its siblings rather than by where it sits in the
loop that emits it: children keyed by position are the same children whatever the declarations did, so
an insertion hands every component to the declaration that used to follow it, along with the state it
had. If what the caller declared carries nothing that tells one sibling from another, the key is the
caller's to supply. There is no rebuild to perform and no previous structure to compare against -
holding one and re-adding children from it costs the applier the identity it was tracking.

Emitting is declaring, not doing. The content a container composes runs on every pass, so work done
there lands again each time: a layout that is asked for another sub-grid while children are being
emitted accumulates one per composition. Whatever a child needs allocated on something that outlives
it belongs in a `remember` inside that child's own `key` block, where it lives and dies with the child,
and is given back from a `DisposableEffect` there. Inside the block is the part that is easy to miss: a
`remember` sitting in the emitting loop but outside the child's `key` is identified by its position in
that loop, whatever you pass as its keys, so it survives the child it belongs to and is re-run for a
child it does not. What the container has to say about a child, the child says for itself: the
placement it declares on its own modifier.

This is why the API you write for others should not hand out the component either. Take data,
callbacks, value types (`Icon`, `Border`, `Color`, `Insets`, `KeyStroke`) and models
(`ListModel`, `TableModel`, `Document`) - the composition manages none of *their* internals, so there
is nothing to race. Keeping the component out of your signatures is what lets the composition stay the
single writer.

The same rule explains where a property belongs. One that only your component has is a parameter of
your component. One that many unrelated components have is a modifier, so it reaches all of them
without every wrapper repeating it - and so a caller composes it with everything else in one chain.

## The `SwingNode` signatures

There are two overloads. The leaf overload wraps a component that has no composable children:

```kotlin
@Composable
@SwingComposable
public inline fun <reified T : Component> SwingNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    noinline onRelease: (T.() -> Unit)? = null,
    hostsSubcompositions: Boolean = false,
    childPlacement: ChildPlacement = ChildPlacement.Indexed,
)
```

<!--- CLEAR -->

The container overload additionally hosts composable `content` as children - use it when your
component is a `java.awt.Container` (e.g. a custom `JPanel`) that should contain further
composables:

```kotlin
@Composable
@SwingComposable
public inline fun <reified T : Component> SwingNode(
    noinline factory: () -> T,
    crossinline update: @DisallowComposableCalls SwingNodeUpdater<T>.() -> Unit = {},
    noinline onRelease: (T.() -> Unit)? = null,
    hostsSubcompositions: Boolean = false,
    childPlacement: ChildPlacement = ChildPlacement.Indexed,
    crossinline content: @Composable @SwingComposable () -> Unit,
)
```

<!--- CLEAR -->

`childPlacement` says how the component holds the children `content` emits. The default,
`ChildPlacement.Indexed`, adds them to the container by index and lets its layout manager place them.
A component that instead shows one child per region of its own - a `JScrollPane`'s viewport, row
header, column header and corners, reached through `setViewportView` and friends rather than through
`Container.add` - declares `ChildPlacement.Slots("SwingModifier.viewport()", ...)`; one that holds any
number of them in order, as a `JTabbedPane` holds pages, declares
`ChildPlacement.OrderedSlots("SwingModifier.tab(title)")`. Each name is the call that fills the region,
written exactly as a caller of your container writes it, because a refusal prints those names and a
caller acts on them by typing them. Under either placement every child names the region it fills,
through `SwingModifier.slot(name, attachment)` - and a child that names none, or one that names a
region of a container that has none, is refused as it arrives, naming the component and the calls that
would place it.

### Writing a `SlotAttachment`

A `SlotAttachment` installs one child into one region and returns the action that removes it again. It
takes the host container, the child, and the child's position among the host's slot children - `0` for
a region holding a single child. Give the caller a modifier builder per region rather than the region's
name, so the name stays yours to change:

```kotlin
private const val HEADER_REGION: String = "SwingModifier.header()"
private const val BODY_REGION: String = "SwingModifier.body()"

private fun edgeAttachment(
    edge: String,
    region: String,
) = SlotAttachment { host, component, _ ->
    val panel = host as? BannerPanel ?: error("$region fills a BannerPanel, but it is held by a $host.")
    panel.add(component, edge)
    return@SlotAttachment { panel.remove(component) }
}

public object BannerScope {
    public fun SwingModifier.header(): SwingModifier =
        slot(HEADER_REGION, edgeAttachment(BorderLayout.NORTH, HEADER_REGION))

    public fun SwingModifier.body(): SwingModifier =
        slot(BODY_REGION, edgeAttachment(BorderLayout.CENTER, BODY_REGION))
}

@Composable
public fun Banner(
    modifier: SwingModifier = SwingModifier,
    content: @Composable @SwingComposable BannerScope.() -> Unit,
) {
    SwingNode(
        factory = { BannerPanel() },
        update = { applyModifier(modifier) },
        childPlacement = ChildPlacement.Slots(HEADER_REGION, BODY_REGION),
        content = { BannerScope.content() },
    )
}
```

<!--- CLEAR -->

Two rules the framework holds you to. The uninstall action must remove the child **by identity** - the
positions of a host's children shift as siblings come and go, so an index captured at install time can
name another child by the time uninstall runs. And a component that declares your region while sitting
in someone else's container reaches your attachment with that container as `host`, so check the type
and refuse by naming the region's own call, which is the text the caller acts on.

Uninstall is also where a region gives back the space it took. `setRowHeaderView(null)` on a scroll
pane leaves an empty header viewport still claiming layout space, so the library's own row-header
attachment clears the whole header instead - `if (pane.rowHeader?.view === component) pane.setRowHeader(null)`,
identity-checked so a child already replaced by another does not clear the new one.

`hostsSubcompositions` defaults to `false`; you only set it for a custom container whose internal
children run their own `setContent` - see *Hosting nested compositions* below.

### `@SwingComposable`: keeping Swing and `compose.ui` apart

`@SwingComposable` marks Swing-target content so the compiler can tell it apart from `compose.ui`'s
own `@UiComposable`: calling a foreign-applier composable (e.g. `androidx.compose.material.Text`)
inside a Swing composition - or a Swing composable inside a `compose.ui` composition - is then a
compile-time error with a "Swing Composable vs UI Composable" message, instead of compiling silently
and failing at runtime.

The compiler infers a composable's target from what it calls, so an ordinary component or container
built on `SwingNode` - every wrapper in this guide included - needs no annotation of its own. The one
place it cannot infer is a `content`/slot lambda **parameter** you forward to `SwingNode` by value
rather than composing inline (see *A container example* below) - type that parameter
`@Composable @SwingComposable () -> Unit`, matching `SwingNode`'s own signature above, so the types
line up at the call.

## Inside `update`: `set` for properties, `SwingModifier` for styling and listeners

The `update` block runs with a `SwingNodeUpdater<T>` receiver. Its core tool is `set`; for styling
and lifecycle-safe listeners you apply a `SwingModifier` chain.

### `set(value) { ... }` - reactive property updates

```kotlin
set(value) { /* this: T */ this.someProperty = it }
```

<!--- CLEAR -->

`set` records `value` and runs the block (with the component as `this` and `value` as `it`) on the
first composition, then again **only when `value` changes** between recompositions. This is the
idiomatic way to push one piece of state onto one Swing property. Call `set` once per property you
want kept in sync.

A call is matched with the value it declared last pass by where it sits among the others, so an
`update` block makes the same calls in the same order on every pass. A `set` inside a conditional is a
bug - state the condition in the value, not in whether the call happens. The same holds for `declare`
(see *Properties the user can also move*), which makes one such call of its own.

`set` compares this pass's declaration only against the last one, so it is the right tool for a property
only the composition writes. See *Properties the user can also move* below for a property the widget
itself can also change.

### `update(value) { ... }` - for what the constructor already applied

```kotlin
SwingNode(
    factory = { JTextField(columns) },
    update = {
        update(columns) {
            this.columns = it
            revalidate()
        }
    },
)
```

<!--- CLEAR -->

`update` behaves like `set` but skips the first composition, so use it - and only it - for a value the
`factory` passed to the constructor. `set` would write that value a second time on the composition
that just created the component.

Reaching for the constructor does not excuse you from writing the value: a parameter consumed only
there is honored once and then silently ignored, which reads at the call site as reactive state and
is not. `update` reaches it on every pass that declares a different value; the constructor runs
exactly once, when the node is built, and not again for as long as that same node lives.

A property that changes the size a component asks for needs a layout pass to go with it. Several
Swing setters only invalidate - `JTextField.setColumns` and `JTextArea.setRows` among them - and
nothing in the update pass asks for a layout on their behalf, so call `revalidate()` after the write.

### `init { ... }` - one-time setup after creation

```kotlin
SwingNode(
    factory = { JTextField() },
    update = {
        set(text) { this.text = it }
        init { selectAll() } // declared after `set`, so `text` is applied and there is something to select
    },
)
```

<!--- CLEAR -->

`init` runs once, on the pass that builds the component - reach for it when a one-time setup step
needs a value `set`/`update` just computed, one the `factory` cannot see. The blocks run in the order
the `update` lambda declares them, so declare `init` after the blocks whose values it reads.

## Properties the user can also move

`set` and `update` both assume the composition is the property's only writer. Some widget properties have
a second writer: the widget itself, through the user's own interaction - a checkbox the user clicks, a
divider the user drags, a selection the user picks. For those, what the composition declares and what the
widget currently holds can diverge at any moment, and neither `set` nor `update` notices - a declaration
that repeats the value it declared last pass is skipped, so the widget stays wherever the user left it.

### The mechanism

Three pieces work together:

- `rememberAppliedValue(declared)` remembers an `AppliedValue<V>` seeded with the first declaration. It is
  a `State<V>` mirroring what the widget currently holds, so reading `applied.value` while composing
  depends on the user moving the widget the same way a read of any other state depends on that state.
- `applied.observed(published)` is called from the widget's own listener with the value the widget just
  published. It updates the mirror and answers whether the move is news for the caller - `true` for a
  move the user made, `false` for a value that only arrived because the wrapper's own write to the widget
  just produced it. Call it for every value the widget publishes, in the order it publishes them.
- `declare(value, applied, read, write)` in the `update` block settles the widget on `value`: it writes
  through `applied` wherever `read()` does not already answer with it, and keeps the mirror in step with
  whatever the widget ends up holding. Unlike `set`, it also depends on the widget's mirrored value, so it
  runs again on the pass that follows a move away from the declaration - the pass that settles the two
  sides against each other.
- `applied.settle { ... }` is for a write the widget does not simply accept. Installing a row filter makes
  a table drop the selected rows it hides. Declaring a tree's open nodes and its selection separately
  lets the tree resolve the pair its own way, since a node is only selectable while its ancestors are
  open. In both, what the widget ends up holding is not what you wrote, and `declare` cannot see it: it
  reads back only the one property it wrote. So wrap the write yourself and say what the widget was left
  holding - `answered(value)` for a value you read back, or `unchanged()` where this property did not
  move. Saying neither throws. Say nothing at all and the mirror goes on claiming the widget holds what
  you asked for: the next pass finds no difference, nothing puts the declaration back, and the user's own
  move of that property is never reported.

The pieces belong to one node. Remember the `AppliedValue` in the component's own body, beside the
`SwingNode` it settles, and call `declare` exactly once per pass: what a declaration is compared against
lives on the mirror rather than in the composition, so a mirror shared between nodes, or remembered above
the node it settles, goes on answering for a widget that is no longer there - and the widget built in its
place keeps its constructor's value instead of the standing declaration. `declare` also makes one `set`
call, so a `declare` inside a conditional shifts every later slot in the `update` block, exactly as a
conditional `set` does.

### A worked example

`CheckBox` is built exactly this way - a `checked` in, an `onCheckedChange` out - over the two-way
`isSelected` property:

```kotlin
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.rememberAppliedValue
import javax.swing.JCheckBox

@Composable
fun MyCheckBox(
    text: String,
    checked: Boolean,
    modifier: SwingModifier = SwingModifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    // The mirror this component settles `checked` through, seeded with the first declaration.
    val applied = rememberAppliedValue(checked)
    SwingNode(
        factory = { JCheckBox() },
        update = {
            set(text) { this.text = it }
            // Settles `checked` against the box whenever either side has moved, rather than only when
            // this pass's declaration differs from the last one.
            declare(checked, applied, JCheckBox::isSelected, JCheckBox::setSelected)
            // The box publishes its new value for every toggle, its own and the user's alike.
            // `observed` answers which is which by value: a toggle that lands on the declaration is
            // the declaration arriving, not a move to report. The lambda is read when the event
            // fires, so writing it here holds nothing across compositions.
            applyModifier(
                modifier.actionListener { event ->
                    val selected = (event.source as JCheckBox).isSelected
                    if (applied.observed(selected)) onCheckedChange(selected)
                },
            )
        },
    )
}
```

<!--- KNIT example-custom-01.kt -->

### The component is fully controlled

Once a property is bound this way, the component is **fully controlled**: a change the caller does not
adopt never stands. Clicking the box flips `isSelected` immediately - that click is a real move, and the
box shows it - and `observed` reports it as news. But if `onCheckedChange` does not feed a new `checked`
back in, the mirror updating is itself what schedules the next pass, and on that pass `declare` finds the
box holding a value other than the one this composition still declares and writes the declaration back
over it, undoing the click. If the caller wants the user's move to stick, adopting it in the callback -
folding it into whatever state `checked` is computed from - is what makes it stick; a caller that ignores
the callback is choosing, on every single pass, to leave the widget wherever the composition likes.

### `onSettled` - when the widget answers a write with something else

`declare` takes an optional `onSettled` block, invoked when the widget does not end up holding what was
asked for - a value clamped to a model's range, an index or path a widget no longer has. It receives
whatever the widget actually settled on, called only after the widget has been left alone, so what it
reports is final. It defaults to doing nothing because most two-way properties admit every value the
composition could declare for them - a checkbox is either selected or not, and both values always take -
so there is nothing to report.

## The applicability boundary: not every user-movable property is `declare`'s

`declare` re-asserts the declaration on every pass. That is right for a property that holds a genuine
value - a selection index, a selected path, a checkbox's state - and wrong for one whose declared value is
a sentinel or a request rather than a value to hold, or one the widget only resolves later, at layout
time.

`JSplitPane`'s `dividerLocation` is the case in point: a negative offset does not mean "the divider sits at
that offset", it means "derive the divider's position from the two sides' preferred sizes" - a derivation
that only happens once the pane is laid out. Re-asserting such a declaration on every pass would fight the
user on every single recomposition, not only the one after they moved the divider.

For a property like this, apply on change instead of declaring it: compare against the widget's current
value yourself, and make the write inside `applied.write { }` so it still marks itself as the wrapper's
own and the listener does not mistake it for a move to report:

```kotlin
set(dividerLocation) { location ->
    if (this.dividerLocation != location) {
        applied.write { this.dividerLocation = location }
    }
}
```

<!--- CLEAR -->

The `applied` here is the same `AppliedValue` its listener calls `observed` on - `write` is what lets the
two share one mirror without fighting the user the way re-asserting the declaration on every pass would.

## Writing a state holder

A declared value with a callback beside it does not carry every kind of state. Where it does not - a
group of values that move together, a value the component reports and no caller could declare, a model
the widget already owns - the shape is a hoistable state holder: an `XState` class the caller keeps
next to their own state, with a `rememberXState` factory beside it.
[`ARCHITECTURE.md`](ARCHITECTURE.md#shapes-for-state-the-user-can-change) says which state takes which
shape; what follows is what building one takes.

### The model keeps the value, the holder makes it observable

A widget with a model of its own - a spinner's `SpinnerModel`, a text component's `Document`, a range
widget's `BoundedRangeModel` - already holds the value and already announces every change to it. A
holder over such a widget owns that model and hands it to the widget, and what it adds is the one thing
the model does not have: a reader composing against it recomposes when the value moves. So the holder
keeps no second copy of the value to write and reconcile; it keeps whatever makes a read of the model
into a snapshot read.

Two shapes do that, and what the value costs to produce decides which:

- **A mirror** - a snapshot-state field refreshed from the model's own change notification, read
  straight back out by the property's getter. `ScrollState` mirrors a viewport's position and its
  metrics as six ints this way. Reach for it wherever the value is small and fixed-size, which is
  nearly always.
- **A generation counter** - a single `mutableIntStateOf` the change listener bumps and nothing else
  reads for its own sake. Each derived property reads the counter first, which registers the
  subscription, then computes its answer from the model. `DocumentState` carries `text`, `canUndo` and
  `canRedo` this way: a keystroke bumps one int and invalidates whoever was reading, and the document
  is walked only when a caller actually asks for its content. Reach for it where mirroring would
  materialize something large on every change whether or not anything reads it - the whole text of a
  document is the case in point - and pay for the extra indirection only there.

The two are not exclusive within one holder. `DocumentState` runs a counter for its text and mirrors
its selection directly, because a selection is a small fixed-size value and re-deriving it would buy
nothing.

### A worked example: a range holder

```kotlin
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

class RangeState internal constructor(
    val model: BoundedRangeModel,
) : RememberObserver {
    // The mirror: refreshed for every change the model announces, so reading `value` while composing
    // subscribes to the user moving the widget as much as to a write made through this holder.
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
        // The model may have moved between construction and this holder being remembered.
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

<!--- KNIT example-custom-02.kt -->

### What the widget settles on is what the holder reports

A write through a holder is a request, and the model answers it. A value can come back clamped to a
bound, snapped to a step, refused outright; a caret offset past the end of a document lands at the end.
Read the value back out of the model after the write and mirror **that**, never the value that was
asked for - the `observedValue = model.value` above rather than `observedValue = value`. A holder that
mirrors the request claims a value the widget does not have, and nothing later corrects it: a model
settles some writes without announcing anything for the change listener to carry.

Where the value the model hands back is an object the model steps **in place** - the same instance,
mutated - a structurally compared mirror cannot tell that from no change at all, and every reader
stands on a value the model has already moved past. Declare the mirror
`mutableStateOf(model.value, neverEqualPolicy())` there and every refresh invalidates its readers
regardless of what they would have compared to. A holder over a model the caller wrote has to reckon
with that, since nothing constrains what such a model hands back or whether it steps that value in
place. What keeps the policy from recomposing on every pass is the model and the guard
together: a refresh only ever follows a change the model announced, and a model announces the changes
it actually made, while the guard on the setter keeps the holder from pushing a value the model already
holds and provoking an announcement of its own. Reach for the policy and you need the guard with it.

### Constraints are declarations, and are written onto the model

What the model holds is the holder's; what bounds it stays something the caller declares. A range's
`min` and `max`, a spinner's step, the items a list model steps through, are parameters of the
`remember` factory, and a later change to one of them is written onto the live model from a
`SideEffect` - as `rememberRangeState` above does - rather than by building a new model. Rebuilding
would take the current value and every listener with it, the widget's and the holder's own, and hand
the caller a holder driving a model nothing renders.

That is also what the two `remember` calls in the factory say. A model the holder owns is remembered
without keys, so a later declaration reaches it through the `SideEffect` and never rebuilds it. A model
the caller supplies is remembered on its identity instead - `remember(model) { RangeState(model) }` -
so handing over a different model builds the holder that drives it, and the widget switches to
rendering it.

Writing a constraint is not writing the value, and what a tightened bound does to a value already
outside it is the model's business rather than the holder's: write the bound and let the model answer.
A `SpinnerNumberModel` leaves the value where it is, outside the range that was just declared, while a
`DefaultBoundedRangeModel` pulls it into range and announces the move. Either way the mirror is
refreshed from what the model announced, so the holder reports whichever happened - which is the same
rule as for a write, arriving from the other side.

### The holder gives back what it took

A holder listening to a model is listening to something that can outlive it. A document or model the
caller supplied is the caller's, and it stays alive - and reachable - after the holder that listened to
it is gone. Implement `RememberObserver`: attach in `onRemembered`, detach in `onForgotten`, and
delegate `onAbandoned` to `onForgotten`, so a holder created for a composition that never applied lets
go too.
Without it a discarded holder stays reachable from the live model and its listener keeps firing, on
state nothing reads any more. A listener attached in `onRemembered` also wants the value re-read
there, since the model is free to move between the holder being constructed and being remembered.

Reaching the component that renders the holder is a separate binding, and `set` is the wrong channel
for it: the binding has to end exactly when the component stops rendering the holder - the node
released or deactivated - and that is a modifier node's lifecycle. Bind through an element of your own,
in the registering shape of *Writing a custom property
element* below: attach the holder in `update`, release it in `onDetach`, and compare the element by
identity so a holder that only looks like the bound one is still a different holder to give the
component over to.

### Only the inputs are state

A holder keeps snapshot state for what it is told - the mirrored value, the metrics read off the
widget - and for nothing that follows from those. Anything computable from them is computed when it is
read. A getter that reads the state it derives from subscribes its reader to that state on every read,
so it can neither go stale nor need keeping in sync; a second field written from the setter that fed
the first can do both, and a reader of it never learns that its inputs moved. `ScrollState`'s `maxX` is
that getter - the view's width less the visible extent, floored at zero - and it follows the pane with
no third field to invalidate.

`derivedStateOf` earns its place where the derivation **narrows**: where many changes of the inputs
produce few changes of the result. `ScrollState`'s `canScrollForwardY` is `y < maxY` over values that
move on every scrolled pixel and every resize, while the answer itself changes only when an end is
reached or left. Wrapping it caches the result and holds its readers still until the answer changes,
so a toolbar button that only wants to know whether there is anywhere left to scroll is not recomposed
by scrolling. Where the result changes about as often as its inputs do, or where the property only
re-exposes state under another name - a `bounds` over `x`, `y`, `width` and `height` - a plain getter
is right, and `derivedStateOf` would buy a cache and a subscription for nothing.

## Styling with a `modifier: SwingModifier` parameter

Visual and interaction concerns that are common across components - colors, fonts, borders, tooltips,
focus, hover - are expressed as a `SwingModifier` chain rather than ad-hoc `set` calls. Give your
component a `modifier: SwingModifier = SwingModifier` parameter and apply it **last** in `update` via
`applyModifier`, so caller-supplied modifiers compose on top of your own defaults:

```kotlin
@Composable
public fun MyWidget(
    /* state + callbacks */
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { /* ... */ },
        update = {
            set(/* ... */) { /* ... */ }
            applyModifier(modifier) // last
        },
    )
}
```

<!--- CLEAR -->

The parameter and that call are not a courtesy to callers who want a border. Where a child sits in its
parent - a `BorderLayout` region, a `GridBagConstraints`, a cell in a manager of your own - is declared
on the child's own chain, and `applyModifier` is the channel through which that declaration reaches the
node, before the applier attaches the component. A component whose `update` never applies its modifier
therefore cannot be placed at all: it goes into every container by index and no scope a container offers
can move it.

Built-in modifier builders are extension functions on `SwingModifier`, grouped by concern:

- **Appearance** - `foreground`, `background`, `font`, `border` and the `lineBorder` / `emptyBorder`
  pair that declares one by its values, `opaque`, `cursor`, `highlights` (marks up ranges of a text
  component, requires a `JTextComponent` target).
- **Content placement** - `icon`, `iconTextGap`, `horizontalAlignment`, `verticalAlignment`,
  `horizontalTextPosition`, `verticalTextPosition`, `margin`: where a component's icon, text and
  content sit inside it. Each reaches every kind of component that declares the property - `icon`,
  the text positions and `iconTextGap` reach a label, a button and a menu item alike, `margin` reaches
  a button and a text component.
- **Button painting** - `borderPainted`, `contentAreaFilled`, `focusPainted`: which parts of a button
  its look and feel paints.
- **Text colors** - `caretColor`, `selectionColor`, `selectedTextColor`, `disabledTextColor`, for a
  text component.
- **Layout** - `preferredSize`, `minimumSize`, `maximumSize`, `size`, `width`, `height`, `location`,
  `x`, `y`, `bounds`, `alignmentX`, `alignmentY`, `visible`, `componentOrientation`.
- **Metadata** - `name`, `toolTip`, `clientProperty`, `testTag`.
- **Interaction** - `enabled`, `focusable`, `focusTraversalIndex`, `orderedFocusTraversal`,
  `defaultButton`, `onHover`, `onFocus`, `onPointerEvent`, `onAccept`, `focusRequester`, `initialFocus`,
  `inputVerifier`, `verifyInputWhenFocusTarget`, `documentFilter`, `caretUpdatePolicy` (requires a
  `JTextComponent` whose caret is a `DefaultCaret`), and `contextMenu` and `popupMenu`
  (a composed menu opened by the platform popup gesture and by state respectively), plus the listener
  builders (`mouseListener`, `keyListener`, ...; see *Attaching a listener* below).
- **Keyboard** - `onKeyEvent`, `onKeyStroke`.
- **Data transfer** - `draggable`, `dropTarget`, `onExportDone`, `clipboard`.
- **Accessibility** - `accessibleName`, `accessibleDescription`, `mnemonic`, and the `labelFor` /
  `labelTarget` pair that captions one component with another.

Chain them: `SwingModifier.foreground(Color.RED).lineBorder(Color.GRAY).onHover { ... }`. The framework
diffs the chain across recompositions, applies new/changed elements, and **restores the original
value** of any element that is removed from the chain.

When your component contributes its own elements (for example, the binding listener that drives its
callback), chain them **onto** the caller's `modifier` - `modifier.yourElement(...)`.

## Writing a custom property element

When you need a styling property the built-ins do not cover, implement the public
`SwingModifier.NodeElement` and `SwingModifier.Node` pair. They split immutable description from mutable
per-component state:

- `NodeElement<T : Component, N : Node<T>>` is the **immutable description** of one chain entry. It carries
  the value to write and the component type it targets; the framework holds it as data and replaces it
  with a fresh instance on each chain change.
- `Node<T : Component>` is the **stateful counterpart**, created once per chain slot and kept across
  recompositions. It holds the captured original value and exposes the live, already-typed `component`.

The element declares its target type in two ways that must agree:

- `targetType: Class<T>` names the most general component the property needs -
  `Component::class.java` for a property every `java.awt.Component` has, `JComponent::class.java` for a
  `JComponent`-only property like a tooltip or border, a concrete widget class for a widget-specific
  one;
- the node's `component` arrives **already** typed `T`, so your `Node` body reads `component` directly
  without casting.

A node whose component is not a `T` is rejected at apply with a clear message naming the element and
the required vs. actual type.

The lifecycle, across the `NodeElement`/`Node` pair:

- `NodeElement.create()` builds the `Node` once, when the element first enters the chain;
- `Node.onAttach()` runs once, right after the component is injected - **capture the component's
  existing value here** so it can be restored;
- `NodeElement.update(node)` runs on attach and on every chain change - **write the new value here**, so a
  fresh element instance (a new value on recomposition) reaches the live node without re-creating it;
- `Node.onDetach()` runs once, when the element is dropped from the chain or the node is released or
  deactivated - **restore the captured original here**.

**`equals` and `hashCode` are abstract**, so the element has to state its own equality and the
compiler rejects one that does not. A slot whose incoming element equals the one it already applied
does nothing, so an element that compares by value is applied once and then skipped for as long as
its value stands - which is what lets you build the chain inline in the composable body without a
`remember`. An element that answers `this === other` is unequal to any other instance, so a chain that
builds a fresh one each pass is re-applied each pass; that is the right answer for an element carrying
nothing, and for one whose write has to be redone whatever the declaration says, but for a plain value
it is the difference between a frame of work and none. Declare such an element as an `object` and the
chain hands the slot the same instance every pass, so it is applied once - right where the write is
idempotent and there is nothing to redo.

Compare a value structurally and a callback by identity. Where the node writes a value, structural
equality is what you want, and a `data class` says it in one word. Where the node **registers**
something - installs a listener, attaches a binding, claims a slot - compare that thing with `===`
instead, and do not use a `data class`: its `==` would call an `equals` the caller may have
overridden (a Kotlin function reference has one), so two objects the node treats as different could
compare equal, the element would skip, and the node would never swap the registration. The element's
equality has to agree with what its node does on update.

A property element is **keyed and last-wins**: its `key` defaults to the element's class, so two
elements of different types never collide. Leave `additive` at its default `false` for a property
(one value wins); a listener instead sets `additive = true` so two of the same builder both install
(see *Attaching a listener* below). Override `key` only when several instances of the *same* type must
coexist as independent slots (e.g. keyed by a property name).

### When several unrelated components declare the same property

An element names one `targetType`, which is enough whenever the components sharing a property also
share a class that declares it. Some properties are not like that: an icon is declared separately by
`JLabel` and by `AbstractButton`, and the class between them declares neither accessor.

For those, accept the widest type the property could appear on and choose the accessors from what the
component turns out to be. Two rules make it behave:

- route the read and the write through the **same** choice, or a value captured through one accessor
  is restored through another;
- reject a component no accessor serves, naming the kinds that are served, so a caller learns the same
  thing a target-type mismatch would have told them.

Reach for this only when the property really is declared in more than one place. A property with one
declaring class stays an ordinary single-target element.

A tooltip lives on `JComponent`, so the element targets `JComponent` via `targetType`; the node reads
its already-typed `component` without casting:

```kotlin
private class ToolTipNode : SwingModifier.Node<JComponent>() {
    private var original: String? = null
    var text: String? = null

    override fun onAttach() {
        original = component.toolTipText // capture the pre-modifier value
    }

    fun apply() {
        component.toolTipText = text // write the latest value
    }

    override fun onDetach() {
        component.toolTipText = original // restore on removal/reuse
    }
}

// A data class: the element is one value, so two declaring the same text are the same declaration
// and the slot is left as it stands.
private data class ToolTipElement(
    private val text: String?,
) : SwingModifier.NodeElement<JComponent, ToolTipNode>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): ToolTipNode = ToolTipNode()

    override fun update(node: ToolTipNode) {
        node.text = text
        node.apply()
    }
}

public fun SwingModifier.toolTip(text: String?): SwingModifier =
    then(ToolTipElement(text))
```

<!--- KNIT example-custom-modifier-01.kt -->

The built-in `toolTip` builder is the same property shape; the code above is the public `NodeElement`/`Node`
path you write for a property the library does not ship.

For a property every component has (no `JComponent`-only access), target `Component` instead -
`NodeElement<Component, ...>` with `targetType = Component::class.java` - and the node's `component` arrives
typed as `java.awt.Component`.

## Attaching a listener

### Typed instance builders - attach an existing listener object

To attach an existing Swing/AWT listener **object** as-is, use the typed instance builders. Each takes
the listener instance, expresses the target component type, and owns the lifecycle: the same instance
is added on install and removed on detach/reset/reuse (AWT removes by identity), so pass a stable
instance and leave both the add and the remove to the builder. See
[`ARCHITECTURE.md`](ARCHITECTURE.md#the-node-lifecycle-and-listeners) for listener lifetimes and why
listeners the host app attached are untouched.

```kotlin
val onMove = remember { object : MouseAdapter() { override fun mouseMoved(e: MouseEvent) { /* ... */ } } }
SwingModifier.name("canvas").mouseMotionListener(onMove)
```

<!--- CLEAR -->

The builders, by listener type: `mouseListener`, `mouseMotionListener`, `mouseWheelListener`,
`keyListener`, `focusListener`, `componentListener`, `hierarchyListener`, `containerListener`
(requires a `Container` target), `propertyChangeListener` (unbound, or bound to a property name),
`actionListener` (for a component that fires action events - an `AbstractButton`, a `JTextField`, or
a `JComboBox`), `documentListener` (requires a
`JTextComponent` target; it observes the component's `Document`), and `caretListener` (requires a
`JTextComponent` target; it observes the component's caret, so it survives a document swap).

The same instance-builder contract also covers widget- and model-specific listeners:
`changeListener` (for a component that fires change events, such as `JSlider`, `JSpinner`,
`JTabbedPane`, `JProgressBar`, `AbstractButton`, or `JViewport`), `listSelectionListener` (requires a
`JList` target), `treeSelectionListener` and `treeExpansionListener` (both require a `JTree` target),
`adjustmentListener` (requires a scrollbar target - `JScrollBar` or `java.awt.Scrollbar`),
`internalFrameListener` (requires a `JInternalFrame` target), and `hyperlinkListener` (requires a
`JEditorPane` target).

Each builder is **additive** (no key): two of the same builder both install and both fire, mirroring
Swing's `addXxxListener`. Pass a **stable** instance - `remember { ... }` it. A fresh object on each
recomposition is a new instance, which detaches the old one and attaches the new (a correct but
wasteful `remove`/`add` round-trip). A handler written at the call site is not that case: it selects
the lambda overload below instead.

### Lambda overloads - write the handler at the call site

Every builder above also takes a lambda, and a lambda selects that overload rather than the instance
one. The library builds the listener and reads the lambda when the event fires, so declaring a fresh
lambda on every recomposition registers nothing again and needs no `remember`:

```kotlin
SwingModifier.actionListener { event -> println(event.actionCommand) }
```

Where the listener interface has a single method, the lambda is that method's: `actionListener`,
`itemListener`, `changeListener`, `caretListener`, `adjustmentListener`, `hyperlinkListener`,
`listSelectionListener`, `treeSelectionListener`, `hierarchyListener`, `mouseWheelListener`, and
`propertyChangeListener` both unbound and bound to a property name - where declaring a different name
moves the registration to that property.

Where it has several methods there are two overloads: one lambda that every method of the interface
calls, and one parameter per method for a caller that tells them apart.

```kotlin
SwingModifier
    .documentListener { println("the document changed") }
    .mouseListener(onMouseClicked = { println("clicked") }, onMouseExited = { println("left") })
```

A method left undeclared reports nowhere rather than inheriting another method's lambda. The
single-lambda overload runs once per method, so one mouse interaction reaches it more than once.
`treeWillExpandListener`'s lambdas answer with a boolean instead: returning `false` leaves the node as
it was.

### `SwingModifier.listener(callback, registration)` - a lambda over a listener the library has no builder for

The lambda overloads above are all built on one seam, and it is public. Reach for it when the event
source you want has no builder - a listener kind the library does not ship, or one whose add/remove
pair lives on a *model* rather than on the component - and what you have to run is a lambda.

A registration is where a listener is registered. Declare one per event source, at the top level or in
a companion, and hand it to every declaration that registers there:

```kotlin
private val SOME_VALUES =
    CallbackRegistration<MySlider, (Int) -> Unit, SomeListener>(
        adapter = { current -> SomeListener { event -> current()(event.value) } },
        registration =
            ListenerRegistration(
                { component, listener -> component.model.addSomeListener(listener) },
                { component, listener -> component.model.removeSomeListener(listener) },
            ),
    )

SwingModifier.listener(onValueChange, SOME_VALUES)
```

<!--- CLEAR -->

It registers the listener `adapter` builds and hands that listener the latest `callback` every time an
event fires, so a `callback` written at the call site needs no `remember`.

The registration is what identifies where the listener sits, and it is compared by identity - which is
why it has to be held in a `val`. One built afresh inside the call is a different registration on every
pass, and re-registers the listener each time; declaring a *different* registration is how a listener is
moved from one event source to another. Where the add/remove pair closes over something that varies -
the name of a bound property, say - keep one registration per value of it rather than closing over the
value at the call site.

### `SwingModifier.listener(instance, registration)` - the last resort

**Reach for something else first.** Three families cover the everyday cases, and all three are safer
than the seam below:

- the typed instance builders above, when you already hold a listener object for an event source that
  has one;
- the lambda overloads above, and the callback modifiers `onHover`, `onFocus`, `onPointerEvent`,
  `onKeyEvent`, `onKeyStroke`, `onAccept` and `inputVerifier`, when what you have is a lambda. These
  read the callback **live**, so a fresh lambda on every recomposition costs nothing and needs no
  `remember`;
- the declaration modifiers, for behavior that is not a callback at all: `focusable`,
  `focusRequester`, `initialFocus`, `verifyInputWhenFocusTarget`, `documentFilter` and `contextMenu`.

**Where the seam is genuinely right:** you hold a listener **object** for an event source that has no
builder. A lambda for such a source goes to the callback overload above; this overload takes the
listener `instance` plus the matching `attach`/`detach` pair:

```kotlin
private val SOME_EVENTS =
    ListenerRegistration<MyType, SomeListener>(
        { component, listener -> component.addSomeListener(listener) },  // already typed MyType
        { component, listener -> component.removeSomeListener(listener) },
    )

val myListener = remember { SomeListener { /* read state off the event */ } }
SwingModifier.listener(myListener, SOME_EVENTS)
```

<!--- CLEAR -->

The seam is reified on the target component type `T`, which the registration names, so the pair
receives the component already typed and a node whose component is not a `T` is rejected at apply with
a clear error. There is no `key` parameter - like the typed builders, it is **additive**.

The same `instance` is added once through the registration when the element enters the chain and
removed when it leaves or the node is released/reused. Supplying a *different* instance
(reference inequality) on a later recomposition detaches the old one and attaches the new, so pass a
**stable** instance - `remember { ... }` it. A handler whose callback changes between recompositions
belongs on the callback overload above instead: the library reads that callback when the event fires, so
it stays current without anything being re-attached.

**What it costs.** This is the one place in the everyday API where you name a component type, and
holding the component is what makes it a last resort. The composition owns the values it declares for a
component and owns its child list: it re-asserts a declared value on every recomposition, and it puts
back what a modifier wrote once that modifier leaves the chain. Code that takes the component and writes
a property the composition declares, or adds and removes children, becomes a second manager of the same
thing - the write is undone by the next recomposition, or the two managers corrupt each other's
bookkeeping. Adding and removing a **listener** is safe, which is what the seam exists for; anything
else through it is yours to get right.

## Domain callbacks stay component parameters

Per-component semantic callbacks - `onClick` on a button, `onValueChange` on a slider - are **not**
modifiers. They remain ordinary parameters of your composable function; callers just pass
`onClick = { ... }`.

### Calling one from a pass costs more than calling one from a gesture

Where a callback runs decides what a throw out of it costs. A callback a widget invokes under a gesture
Swing dispatched costs nothing to leave alone: Swing hands the exception to the event pump, which
reports it and carries on dispatching, and matching that is the whole of what a wrapper owes there.

A callback reached from the pass that applies the composition's changes is the other case - a report of
what a widget settled on, a listener that a write of your own provokes before that write returns, a
callback called straight out of `update`. Recomposition is the pump there, and it does not carry on: a
throw reaching it ends that composition for good, and the window it drives stops answering state for
the rest of its life.

So contain what the caller supplied, at the edge their code sits behind. The two-way binding already
does: `declare`'s `onSettled` and anything a write through `applied.write { }` provokes are contained
and reported, and the pass finishes. Where your component calls the caller's code itself during a pass,
catch `Throwable` around that call and hand it to `Thread.currentThread().uncaughtExceptionHandler`,
which is where Swing leaves an exception raised under its own pump - the caller sees the failure in the
place they already look for one, and the composition survives. Catch every type: what the caller's code
throws is theirs to choose, and whichever type went unnamed would be the one that ends the composition.
Catch only their code - what your component requires of a declaration is yours to state, and those
failures have to reach the caller as the failures they are.

## A worked example: wrapping `JSpinner`

Here is a complete, compilable wrapper for `JSpinner`, mirroring how `TextField`/`Slider` are built
- a `value` in, an `onValueChange` out:

```kotlin
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

@Composable
fun MySpinner(
    value: Int,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Int) -> Unit = {},
    min: Int = 0,
    max: Int = 100,
    step: Int = 1,
) {
    SwingNode(
        factory = { JSpinner(SpinnerNumberModel()) },
        update = {
            // Reactive property updates: each block re-runs only when its value changes.
            set(min) { numberModel.minimum = it }
            set(max) { numberModel.maximum = it }
            set(step) { numberModel.stepSize = it }
            set(value) { if (this.value != it) this.value = it }
            // The component chains its own element onto the caller's modifier; the changeListener
            // builder owns the listener's lifecycle.
            applyModifier(modifier.changeListener { event -> onValueChange((event.source as JSpinner).value as Int) })
        },
    )
}

// A `JSpinner` keeps the range and the step on its model, so that is where those parameters land.
private val JSpinner.numberModel: SpinnerNumberModel get() = model as SpinnerNumberModel
```

<!--- KNIT example-custom-03.kt -->

The `if (this.value != it)` guard in the `value` setter prevents a feedback loop where applying the
incoming state would itself fire the change listener.

### A container example

For a custom container, use the `content` overload and create a `Container` in the factory; children
emitted by `content` are added by the framework's applier:

```kotlin
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.border.TitledBorder

@Composable
fun TitledGroup(
    title: String,
    modifier: SwingModifier = SwingModifier,
    content: @Composable @SwingComposable () -> Unit,
) {
    SwingNode(
        factory = { JPanel(FlowLayout()).apply { border = TitledBorder("") } },
        update = {
            set(title) { (this.border as TitledBorder).title = it }
            applyModifier(modifier)
        },
        content = content,
    )
}
```

<!--- KNIT example-custom-04.kt -->

A container takes a `modifier` and applies it for the same reason a leaf does, and for one more: a
container is itself a child of whatever holds it, so the group above is placed in a `BorderPanel`
region or a `GridBagPanel` cell only because its `update` block ends where it does.

### Placing children under constraints

A child declares where it sits inside its parent on its own `SwingModifier`, with `layoutConstraint`.
The value is whatever the enclosing container's layout manager understands - a `BorderLayout` region, a
`GridBagConstraints`, a cell in a manager of your own - and it is handed over the way
`Container.add(Component, Object)` hands it over, so a `LayoutManager2` receives it as-is and a manager
that takes no constraints places the child by index.

What a container supplies is the layout manager, and a scope whose modifier builders name the
placements that manager understands. The scope is a public sealed interface whose builders are declared
as extensions on `SwingModifier`, so each one is callable only where that scope is in receiver
position - inside the container's own content - and an internal object or class implements them.
`RowScope.weight` is the worked precedent for that shape; over a manager of your own, each builder
appends the value the manager understands with `layoutConstraint`:

```kotlin
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JPanel

/** The placements a mosaic offers, and where the constraint's type belongs. */
sealed interface MosaicScope {
    /** Places the child in the cell at [row] and [column]. */
    fun SwingModifier.cell(row: Int, column: Int): SwingModifier
}

// A cell reaches the manager on the child's own chain, so the scope holds nothing and one instance
// serves every mosaic. MosaicCell is a data class, so a cell rebuilt from the same arguments is the
// cell the child already sits in.
private object MosaicScopeImpl : MosaicScope {
    override fun SwingModifier.cell(row: Int, column: Int): SwingModifier =
        layoutConstraint(MosaicCell(row, column))
}

@Composable
fun MosaicPanel(
    modifier: SwingModifier = SwingModifier,
    content: @Composable MosaicScope.() -> Unit,
) {
    SwingNode(
        factory = { JPanel(MosaicLayout()) },
        update = { applyModifier(modifier) },
        content = { MosaicScopeImpl.content() },
    )
}

MosaicPanel {
    Label("Title", modifier = SwingModifier.cell(row = 0, column = 0))
    Label("Body", modifier = SwingModifier.cell(row = 1, column = 0))
}
```

Changed is decided by `equals`. A chain is rebuilt on every pass, so the placement in it is compared
against the one the child already sits under, and only a difference re-registers the child: the
framework takes it out of the layout manager and adds it back under the new value, then revalidates
the parent. A value that compares by value therefore costs nothing to rebuild, while one that compares
by identity - a bare `GridBagConstraints`, a class of your own that never overrode `equals` - is a new
instance on every pass and puts every child through that removal and re-add on every composition. What
that costs is the manager's, not the framework's: a `LayoutManager2` that keeps anything derived from
the placements it has been handed - a grid, a set of measured column widths, a row cache - discards and
rebuilds it each time, because a re-registration reaches it as the same
`removeLayoutComponent`/`addLayoutComponent` pair a real structural change does. Give a placement of
your own a value `equals`, as `GridBagPanel` does for the constraints its items declare.

Derive that value from the declaration rather than from a running count of the children before it,
too: a placement computed from how many siblings came first changes for every later child the moment
one is inserted, and every one of them is then re-registered. This is advice about the placement value.
The `key` a child is declared under is a separate question, answered in *What the composition owns*.

A scope like this keeps the constraint's type inside your container - `MosaicScope` is the whole
placement API your callers see, and `BorderPanel`'s regions and `GridBagPanel`'s items are the same
shape over a fixed, nameable set of placements.

A scope is worth writing only where the placements are worth naming. Where a layout manager answers
for a child that declares nothing - `BorderLayout` places one at `CENTER`, `JLayeredPane` on
`DEFAULT_LAYER`, `CardLayout` under the empty name - a container over it takes a plain
`@Composable () -> Unit` content and lets the manager do the placing. That is the usual case: no
scope, no constraint. `layoutConstraint` stays available to a caller who does need to name a
placement.

Three rules shape a container of this kind, and every container that places children this library ships
follows all three:

- **The content block is `@Composable`, and the children written in it compose where the container's
  own children belong.** Declare it `content: @Composable XScope.() -> Unit` and hand it to `SwingNode`
  as `content = { XScopeImpl.content() }`. Beyond making every child a node the applier places, this is
  what gives each child the identity described in *What the composition owns*: a child composed where
  the caller wrote it is told from its siblings by that place. An ordinary block that records composable
  lambdas for the container to invoke afterwards hands every one of them to the single place the
  container invokes them from, so they share it and are left to be told apart by order. Composed
  directly, `if (showEmail) EmailField()` followed by `PhoneField()` leaves the phone field's component
  and everything it holds alone when the email field appears, with no `key` from anyone.
- **There is no collection phase.** A scope's builders return a chain and the children compose as they
  are written, so there is no list of pending declarations, no `ScopeImpl().apply(block)`, and no
  `forEach` over what a block gathered. That also settles whether to `remember` the scope: one holding
  nothing is an object shared by every instance of the container, as `MosaicScopeImpl` above and
  `BorderPanelScope`'s implementation are, and one holding something the container owns - a layout's
  placement table, a mirror the container settles a value through - is remembered alongside the
  container, as `RowScope`'s and `ScrollPaneScope`'s implementations are.
- **A container that has to validate its children runs each check where its answer is whole.** A rule
  about one child on its own is answered as that node arrives, the first moment the container knows the
  child is really there: a `tab` declared by a component that some other container holds is refused as
  that component is attached. A rule over the whole set of children - one child per side, one per card -
  is answered once the pass has settled, because a pass may hold two children in one place while it
  runs: a replacement need not wait for the child it replaces to go, and only what remains at the end is
  what the composition declares. A container reaching its children through regions leaves that to the
  framework, which holds a `ChildPlacement.Slots` host to one child per region once the pass settles, as
  `SplitPane` does for its two sides; one placing them under constraints runs its own check on the
  event-dispatch turn after the change pass, once a parked node's deactivation has run too, as
  `CardPanel` does over the cards its deck holds. Either way the set a check runs against is what the
  container actually holds - its own children, its layout's own records - rather than a list a block
  gathered.

A scope's members are `SwingModifier` builders wherever the child is the caller's own component. Where
the container is what realizes the child instead - `DesktopPane`, whose every child is a
`JInternalFrame` built around the content the declaration carries - the scope's member is a
`@Composable` function taking that content, since there is no component of the caller's to hang a
modifier on.

Two things such a container should not do. It should not spend a component on a child: wrapping every
child in a panel of your own to carry something - a border, an alignment - puts a real container with a
real layout between the caller's component and yours, and a `modifier` meant for the child is then
applied to that wrapper instead of to the component the caller wrote. It also changes what the layout
above sees: a parent measures and inspects the children it holds, so a gap policy that treats a nested
panel differently from a control, or an alignment that lines a label up against the field beside it,
reads the wrapper you inserted instead of the component, and the same components laid out through your
container stop matching the ones laid out without it. And it should not accept a declaration it does
not apply: a parameter a scope takes and never writes onto anything is silence - either it reaches the
component or it leaves the signature.

A placement travels no further than the node whose chain declares it, which is what lets a scope's
builders name placements only their own container understands.

### When the placement is not something the caller states

The placements above are values the caller writes: a region name, a cell, a pair of grid coordinates.
A manager of your own may instead derive a child's placement from the children around it - a form whose
column count is its widest row's, a group whose leading gap depends on whether another group precedes
it, a band that divides its width among whatever ended up in it. Nothing changes about how the
placement travels. What changes is that the answer cannot be computed as each child arrives, because
it needs every declaration, and that your manager cannot hold on to what it derived, because the next
composition may change it.

Two things carry a manager like that. The first is that the component array *is* the declared
structure. Every child is added at its composition index - a constrained child through the
three-argument `Container.add(Component, Object, int)`, precisely so that applying a placement does not
cost the array its order - and later removals and moves address that same array. A manager is therefore
free to read `parent.components`, and the constraints it registered for them, as the whole structure
the caller declared, in the order they declared it, and to derive from all of it when it is asked to
measure or to lay out. Deriving at `addLayoutComponent` time is the part that does not work: the child
arriving knows nothing about the ones still to come.

The second is that what the placement carries is the child's part in that structure - which row this
control belongs to, how deep its group is - rather than the coordinates the manager will work out from
it. That keeps the placement something a single declaration can produce on its own, so it compares
equal to the last composition's whenever the declaration did not change and only the children whose
part really moved are re-registered. A placement carrying the derived coordinate would change for every
child the moment any one of them changed, and put the whole container through a remove-and-re-add on
every pass.

## Building a custom shared hierarchy

A composite several screens reuse - a titled card, a labeled form, a frame with a fixed header and
footer - is a composable that emits the containers it needs and offers its placements as a scope
receiver. Everything the pattern needs is public: the built-in containers, the container `SwingNode`
overload for a Swing container of your own, and `layoutConstraint` for the placements a scope names.

The scope interface is the whole API callers see. Each of its builders names one region of the
composite, over the constraint the container underneath understands:

```kotlin
sealed interface FramedScope {
    /** Places the child across the top of the frame, above its body. */
    fun SwingModifier.header(): SwingModifier

    /** Places the child in the frame's body, filling what the header and footer leave. */
    fun SwingModifier.body(): SwingModifier

    /** Places the child across the bottom of the frame, below its body. */
    fun SwingModifier.footer(): SwingModifier
}

private object FramedScopeImpl : FramedScope {
    override fun SwingModifier.header(): SwingModifier = layoutConstraint(BorderLayout.NORTH)

    override fun SwingModifier.body(): SwingModifier = layoutConstraint(BorderLayout.CENTER)

    override fun SwingModifier.footer(): SwingModifier = layoutConstraint(BorderLayout.SOUTH)
}

@Composable
fun Framed(
    title: String,
    modifier: SwingModifier = SwingModifier,
    content: @Composable FramedScope.() -> Unit,
) {
    val border = remember(title) { TitledBorder(title) }
    BorderPanel(modifier = modifier.border(border)) {
        FramedScopeImpl.content()
    }
}

Framed(title = "Payment") {
    Label("Card details", modifier = SwingModifier.header())
    PaymentForm(modifier = SwingModifier.body())
    Button("Pay", onClick = ::pay, modifier = SwingModifier.footer())
}
```

The composite owns no node of its own, and it does not have to. A placement rides the child's chain
until the container actually holding that child reads it, so the `BorderLayout` constraint
`FramedScopeImpl` builds is honored by the `BorderPanel` inside `Framed` although the caller never sees
that panel. That is what makes a composite of built-in containers a plain composable function rather
than a wrapper that has to forward anything.

Four things make a composite of this shape behave:

- **Hold the scope to its three rules** - a `@Composable` content block, children composed directly,
  and each check run where its answer is whole (see *Placing children under constraints*). A
  composite's regions are placements like any other, so nothing about them is special.
- **Keep the composite composable-shaped** - state in, callbacks out, and a `modifier` parameter
  chained onto the outermost container it emits, so a caller styles the composite the way they style
  any component.
- **`remember` the value objects whose type compares by identity** - `remember(title) { TitledBorder(title) }`,
  as above. A `Border` or `Icon` built in the composable body is a different instance on every
  recomposition, and neither overrides `equals`, so the chain element sees a changed value on every
  pass and re-applies: `JComponent.setBorder` repaints for any instance that is not the one it already
  holds, and relayouts when the insets differ. Handing the same instance back makes the re-applied write
  a no-op. A value with structural equality - a `Font`, `Color`, `Insets` - needs no `remember`: an
  equal instance already compares equal to the chain element applied last time and the write is skipped.
- **Let each container place its own children.** Nothing carries a placement past the node that
  declared it, so a composite nests inside another - a `GridBagPanel` in the `body` region above - with
  neither knowing about the other.

Reach for `SwingNode` here only when the Swing container itself is yours; a composite assembled from
built-in containers is an ordinary composable function.

## Hosting nested compositions: `hostsSubcompositions`

Both `SwingNode` overloads take an opt-in `hostsSubcompositions: Boolean = false`. Leave it `false`
(the default) unless your custom component, internally, drives its **own** `setContent` against one of
its children - for example, a Swing container that manages tabs, popups, or split panes by calling
`setContent` on sub-panels it creates itself.

Set it `true` so those nested `setContent` calls **join this node's own composition**, sharing its
`CompositionLocal`s along with the recomposer and scope around it. Without the flag such a call joins
whatever its place in the Swing tree resolves to - the island above it, or the composition its window
shares - so it recomposes with everything else there but sees none of the `CompositionLocal`s this node
stands under.

```kotlin
SwingNode(
    factory = { TabbedPanel() }, // a JComponent that runs setContent on its own tab panels
    hostsSubcompositions = true,
)
```

When `hostsSubcompositions = true`, the factory component **must** be a `javax.swing.JComponent`; a
bare `java.awt.Component` host throws `IllegalStateException` at apply.

## `onRelease` for cleanup

If your component holds a resource that must be released - a timer, a native handle, a registration
on a shared bus - release it in `onRelease`. It runs once, when the node leaves the composition for
good (the typed component is `this`):

```kotlin
SwingNode(
    factory = { ExpensiveComponent() },
    update = { /* ... */ },
    onRelease = { dispose() }, // this: ExpensiveComponent
)
```

`onRelease` is for your own resources. Listeners installed via `SwingModifier.listener` are detached
automatically - you do not need to remove them in `onRelease`.
