# Defining a custom Swing component in Compose

Compose Swing UI ships wrappers for the common widgets (`Button`, `TextField`, `Slider`, ...), but
real applications host many bespoke Swing components. Wrapping your own component uses the same
public `SwingNode` API every built-in wrapper is built on. This guide shows how.

Three further documents carry the rest:

- [`COMPONENT-STATE.md`](COMPONENT-STATE.md) - a property the user can change as well as the
  composition, and the state holders that carry what a declared value cannot.
- [`CUSTOM-MODIFIERS.md`](CUSTOM-MODIFIERS.md) - the `modifier` parameter, writing a property
  element of your own, and attaching listeners.
- [`CUSTOM-CONTAINERS.md`](CUSTOM-CONTAINERS.md) - containers, the placements they offer their
  children, and rendering items with a composable cell.

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

### `@SwingComposable`: keeping Swing and `compose.ui` apart

`@SwingComposable` marks Swing-target content so the compiler can tell it apart from `compose.ui`'s
own `@UiComposable`: calling a foreign-applier composable (e.g. `androidx.compose.material.Text`)
inside a Swing composition - or a Swing composable inside a `compose.ui` composition - is then a
compile-time error with a "Swing Composable vs UI Composable" message, instead of compiling silently
and failing at runtime.

The compiler infers a composable's target from what it calls, so an ordinary component or container
built on `SwingNode` - every wrapper in this guide included - needs no annotation of its own. The one
place it cannot infer is a `content`/slot lambda **parameter** you forward to `SwingNode` by value
rather than composing inline (see *A container example* in
[`CUSTOM-CONTAINERS.md`](CUSTOM-CONTAINERS.md)) - type that parameter
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
(see [`COMPONENT-STATE.md`](COMPONENT-STATE.md)), which makes one such call of its own.

`set` compares this pass's declaration only against the last one, so it is the right tool for a property
only the composition writes. See [`COMPONENT-STATE.md`](COMPONENT-STATE.md) for a property the widget
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

<!--- CLEAR -->

## A worked example: wrapping `JSpinner`

Here is a complete, compilable wrapper for `JSpinner`, mirroring how `TextField`/`Slider` are built
- a `value` in, an `onValueChange` out:

<!--- INCLUDE .*custom-01.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
-->

```kotlin
@Composable
fun MySpinner(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
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
            // builder owns the listener's lifecycle. The typed overload hands the spinner over as the
            // receiver, and `this` is needed because the composable's own `value` parameter shadows it.
            applyModifier(modifier.changeListener<JSpinner> { onValueChange(this.value as Int) })
        },
    )
}

// A `JSpinner` keeps the range and the step on its model, so that is where those parameters land.
private val JSpinner.numberModel: SpinnerNumberModel get() = model as SpinnerNumberModel
```

<!--- KNIT example-custom-01.kt -->

The `if (this.value != it)` guard in the `value` setter prevents a feedback loop where applying the
incoming state would itself fire the change listener.
