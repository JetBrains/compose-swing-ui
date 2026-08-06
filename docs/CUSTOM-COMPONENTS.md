# Defining a custom Swing component in Compose

Compose Swing UI ships wrappers for the common widgets (`Button`, `TextField`, `Slider`, ...), but
real applications host many bespoke Swing components. Wrapping your own component is a **first-class,
supported use case** - every built-in wrapper is built exactly the same way, on top of the public
`SwingNode` API. This guide shows how.

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

- `factory` runs **once**, when the node enters the composition. Build (and do one-time
  configuration of) your Swing component here. Whatever it reads from the composable body it reads
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
That holds however the children are described - a container that offers a receiver DSL records
`@Composable` lambdas from it and emits them, which is what makes each one a child of the composition
rather than a component in a list.

Structural change is the applier's too. A container re-emits the children this composition declares and
the applier settles the difference: a child that has gone leaves, a new one arrives, one that moved is
carried to its new position with the component it already had. Emitting each child under a `key` is how
you say which is which, so a reordered or conditionally-declared child keeps its component and its
state. Key a child by what identifies it among its siblings rather than by where it sits in the loop
that emits it - children keyed by position are the same children whatever the declarations did, so an
insertion hands every component to the declaration that used to follow it, along with the state it had.
An index is not the only way to spell a position: a counter handed out while the declarations are
recorded is one too, because the recording starts from nothing on every composition and so gives the
nth declaration the nth number every time. A coordinate computed while emitting is the worst of the
three, because it moves for every later child whenever any earlier one changes shape, and not merely
when one is inserted. If what the caller declared carries nothing that tells one sibling from another,
the key is the caller's to supply.
There is no rebuild to perform and no previous structure to compare against - holding one and re-adding
children from it costs the applier the identity it was tracking.

Emitting is declaring, not doing. The content a container composes runs on every pass, so work done
there lands again each time: a layout that is asked for another sub-grid while children are being
emitted accumulates one per composition. Moving that work into the block that records the declarations
changes nothing - what a freshly built scope drops is its own declarations, never what it did to
something that outlives it. Whatever a child needs allocated on such a thing belongs in a `remember`
inside that child's own `key` block, where it lives and dies with the child, and is given back from a
`DisposableEffect` there. Inside the block is the part that is easy to miss: a `remember` sitting in the
emitting loop but outside the child's `key` is identified by its position in that loop whatever you pass
as its keys, so it survives the child it belongs to and is re-run for a child it does not. What the container has to say about a child travels with the child, as the
placement it is emitted under.

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
)
```

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
    crossinline content: @Composable @SwingComposable () -> Unit,
)
```

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

`set` records `value` and runs the block (with the component as `this` and `value` as `it`) on the
first composition, then again **only when `value` changes** between recompositions. This is the
idiomatic way to push one piece of state onto one Swing property. Call `set` once per property you
want kept in sync.

`set` compares this pass's declaration only against the last one, so it is the right tool for a property
only the composition writes. It is the **wrong** tool for a property the user can also move at the widget
itself - a checkbox the user clicks, a selection the user picks - because a declaration that repeats the
value it declared last pass is skipped, leaving the widget wherever the user's own move last put it. See
*Properties the user can also move* below for the two-way form.

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

`update` behaves like `set` but skips the first composition, so use it - and only it - for a value the
`factory` passed to the constructor. `set` would write that value a second time on the composition
that just created the component.

Reaching for the constructor does not excuse you from writing the value: a parameter consumed only
there is honoured once and then silently ignored, which reads at the call site as reactive state and
is not. It also outlives the composition that supplied it, because a node is recyclable - when a
component is reused for new content the constructor does not run again, and `update` does, so the
recycled component adopts the new value rather than keeping the old one.

A property that changes the size a component asks for needs a layout pass to go with it. Several
Swing setters only invalidate - `JTextField.setColumns` and `JTextArea.setRows` among them - and
nothing in the update pass asks for a layout on their behalf, so call `revalidate()` after the write.

## Properties the user can also move

`set` and `update` both assume the composition is the property's only writer. Some widget properties have
a second writer: the widget itself, through the user's own interaction - a checkbox the user clicks, a
divider the user drags, a selection the user picks. For those, what the composition declares and what the
widget currently holds can diverge at any moment, and neither `set` nor `update` notices - a declaration
that repeats the value it declared last pass is skipped, so the widget stays wherever the user left it.

### The mechanism

Three pieces work together:

- `rememberAppliedValue(declared)` remembers an `AppliedValue<V>` seeded with the first declaration. It
  mirrors what the widget currently holds as snapshot state, so a later read of it while composing
  depends on the user moving the widget the same way a read of any other state depends on that state.
- `applied.observed(value)` is called from the widget's own listener with the value the widget just
  published. It updates the mirror and answers whether the move is news for the caller - `true` for a
  move the user made, `false` for a value that only arrived because the wrapper's own write to the widget
  just produced it. Call it for every value the widget publishes, in the order it publishes them.
- `declare(value, applied, read, write)` in the `update` block settles the widget on `value`: it writes
  through `applied` wherever `read()` does not already answer with it, and keeps the mirror in step with
  whatever the widget ends up holding. Unlike `set`, it also depends on the widget's mirrored value, so it
  runs again on the pass that follows a move away from the declaration - the pass that settles the two
  sides against each other.

### A worked example

`CheckBox` is built exactly this way - a `checked` in, an `onCheckedChange` out - over the two-way
`isSelected` property:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ActionListener
import javax.swing.JCheckBox

@Composable
fun MyCheckBox(
    text: String,
    checked: Boolean,
    modifier: SwingModifier = SwingModifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val callback = rememberUpdatedState(onCheckedChange)
    // The mirror this component settles `checked` through, seeded with the first declaration.
    val applied = rememberAppliedValue(checked)
    // The box publishes its new value for every toggle, its own and the user's alike. `observed`
    // answers which is which by value: a toggle that lands on the declaration is the declaration
    // arriving, not a move to report.
    val listener =
        remember(applied) {
            ActionListener { event ->
                val selected = (event.source as JCheckBox).isSelected
                if (applied.observed(selected)) callback.value(selected)
            }
        }
    SwingNode(
        factory = { JCheckBox() },
        update = {
            set(text) { this.text = it }
            // Settles `checked` against the box whenever either side has moved, rather than only when
            // this pass's declaration differs from the last one.
            declare(checked, applied, JCheckBox::isSelected, JCheckBox::setSelected)
            applyModifier(modifier.actionListener(listener))
        },
    )
}
```

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

The `applied` here is the same `AppliedValue` its listener calls `observed` on - `write` is what lets the
two share one mirror without fighting the user the way re-asserting the declaration on every pass would.

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

Built-in modifier builders are extension functions on `SwingModifier`, grouped by concern:

- **Appearance** - `foreground`, `background`, `font`, `border` and the `lineBorder` / `emptyBorder`
  pair that declares one by its values, `opaque`, `cursor`.
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
  `inputVerifier`, `verifyInputWhenFocusTarget`, `documentFilter`, and `contextMenu` and `popupMenu`
  (a composed menu opened by the platform popup gesture and by state respectively), plus the typed
  instance listener builders (`mouseListener`, `keyListener`, ...; see *Attaching a listener* below).
- **Keyboard** - `onKeyEvent`, `onKeyStroke`.
- **Data transfer** - `draggable`, `dropTarget`, `onExportDone`, `clipboard`.
- **Accessibility** - `accessibleName`, `accessibleDescription`, `mnemonic`, and the `labelFor` /
  `labelTarget` pair that captions one component with another.

Chain them: `SwingModifier.foreground(Color.RED).lineBorder(Color.GRAY).onHover { ... }`. The framework
diffs the chain across recompositions, applies new/changed elements, and **restores the original
value** of any element that is removed from the chain.

When your component contributes its own elements (for example the binding listener that drives its
callback), chain them **onto** the caller's `modifier` - `modifier.yourElement(...)`.

## Writing a custom property element

When you need a styling property the built-ins do not cover, implement the public
`SwingModifier.Element` and `SwingModifier.Node` pair. They split immutable description from mutable
per-component state:

- `Element<T : Component, N : Node<T>>` is the **immutable description** of one chain entry. It carries
  the value to write and the component type it targets; the framework holds it as data and replaces it
  with a fresh instance on each chain change.
- `Node<T : Component>` is the **stateful counterpart**, created once per chain slot and kept across
  recompositions. It holds the captured original value and exposes the live, already-typed `component`.

The element declares its target type two ways that must agree:

- `targetType: Class<T>` names the most general component the property needs -
  `Component::class.java` for a property every `java.awt.Component` has, `JComponent::class.java` for a
  `JComponent`-only property like a tooltip or border, a concrete widget class for a widget-specific
  one;
- the node's `component` arrives **already** typed `T`, so your `Node` body reads `component` directly
  without casting.

A node whose component is not a `T` is rejected at apply with a clear message naming the element and
the required vs. actual type.

The lifecycle, across the `Element`/`Node` pair:

- `Element.create()` builds the `Node` once, when the element first enters the chain;
- `Node.onAttach()` runs once, right after the component is injected - **capture the component's
  existing value here** so it can be restored;
- `Element.update(node)` runs on attach and on every chain change - **write the new value here**, so a
  fresh element instance (a new value on recomposition) reaches the live node without re-creating it;
- `Node.onDetach()` runs once, when the element is dropped from the chain or the node is recycled -
  **restore the captured original here**.

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

private class ToolTipElement(
    private val text: String?,
) : SwingModifier.Element<JComponent, ToolTipNode> {
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

The built-in `toolTip` builder is the same property shape - capture in attach, write in update,
restore in detach; the code above is the public `Element`/`Node` path you write for a property the
library does not ship.

For a property every component has (no `JComponent`-only access), target `Component` instead -
`Element<Component, ...>` with `targetType = Component::class.java` - and the node's `component` arrives
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
`adjustmentListener` (requires a scrollbar target - `JScrollBar` or `java.awt.Scrollbar`), and
`internalFrameListener` (requires a `JInternalFrame` target).

Each builder is **additive** (no key): two of the same builder both install and both fire, mirroring
Swing's `addXxxListener`. Pass a **stable** instance - `remember { ... }` it. A fresh lambda or object
on each recomposition is a new instance, which detaches the old one and attaches the new (a correct
but wasteful `remove`/`add` round-trip).

### `SwingModifier.listener` - the last resort

**Reach for something else first.** Three families cover the everyday cases, and all three are safer
than the seam below:

- the typed instance builders above, when you already hold a listener object;
- the callback modifiers, when what you have is a lambda: `onHover`, `onFocus`, `onPointerEvent`,
  `onKeyEvent`, `onKeyStroke`, `onAccept` and `inputVerifier`. These read the callback **live**, so a
  fresh lambda on every recomposition costs nothing and needs no `remember`;
- the declaration modifiers, for behaviour that is not a callback at all: `focusable`,
  `focusRequester`, `initialFocus`, `verifyInputWhenFocusTarget`, `documentFilter` and `contextMenu`.

**Where the seam is genuinely right:** a listener kind the library ships no builder for, or one whose
add/remove pair lives on a *model* rather than on the component. Then drop to
`SwingModifier.listener`, the single listener seam every builder above is built on. It takes the
listener `instance` plus the matching `attach`/`detach` pair:

```kotlin
val listener = remember { SomeListener { /* read state off the event, call the live callback */ } }
SwingModifier.listener<MyType, SomeListener>(
    instance = listener,
    attach = { component, l -> component.addSomeListener(l) },   // component is already typed MyType
    detach = { component, l -> component.removeSomeListener(l) },
)
```

`listener<T, L>` is reified on the target component type `T`, so `attach`/`detach` receive the
component already typed, and a node whose component is not a `T` is rejected at apply with a clear
error. There is no `key` parameter - like the typed builders, it is **additive**.

The same `instance` is added once via `attach` when the element enters the chain and removed via
`detach` when it leaves or the node is released/reused. Supplying a *different* instance
(reference inequality) on a later recomposition detaches the old one and attaches the new, so pass a
**stable** instance - `remember { ... }` it. To keep the latest callbacks visible without re-attaching,
wrap the callback in `rememberUpdatedState` and read it from inside the remembered listener.

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

## A worked example: wrapping `JSpinner`

Here is a complete, compilable wrapper for `JSpinner`, mirroring how `TextField`/`Slider` are built
- a `value` in, an `onValueChange` out:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener

@Composable
fun MySpinner(
    value: Int,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Int) -> Unit = {},
    min: Int = 0,
    max: Int = 100,
    step: Int = 1,
) {
    // rememberUpdatedState keeps the latest callback without re-attaching the listener every recomposition.
    val callback = rememberUpdatedState(onValueChange)
    // One stable ChangeListener for the node's lifetime - remember it so the same instance is re-used.
    val listener = remember { ChangeListener { event -> callback.value((event.source as JSpinner).value as Int) } }
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
            applyModifier(modifier.changeListener(listener))
        },
    )
}

// A `JSpinner` keeps the range and the step on its model, so that is where those parameters land.
private val JSpinner.numberModel: SpinnerNumberModel get() = model as SpinnerNumberModel
```

Notice the `if (this.value != it)` guard in the `value` setter: it prevents a feedback loop where
applying the incoming state would itself fire the change listener. The listener reads the current
`onValueChange` through `rememberUpdatedState`, so the stable instance always sees the latest callback
without re-attaching.

### A container example

For a custom container, use the `content` overload and create a `Container` in the factory; children
emitted by `content` are added by the framework's applier:

```kotlin
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.annotations.SwingComposable
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.border.TitledBorder

@Composable
fun TitledGroup(
    title: String,
    content: @Composable @SwingComposable () -> Unit,
) {
    SwingNode(
        factory = { JPanel(FlowLayout()).apply { border = TitledBorder("") } },
        update = {
            set(title) { (this.border as TitledBorder).title = it }
        },
        content = content,
    )
}
```

### Placing children under constraints

A container whose layout manager needs to know *where* each child goes - a `BorderLayout` region, a
`GridBagConstraints`, a cell in a manager of your own - declares that placement for the children it
composes, with `SwingConstraint`:

```kotlin
import org.jetbrains.compose.swing.node.SwingConstraint

/** The placements your container offers, and where the constraint's type belongs. */
interface MosaicScope {
    fun cell(row: Int, column: Int, content: @Composable () -> Unit)
}

private class MosaicScopeImpl : MosaicScope {
    val cells: MutableMap<MosaicCell, @Composable () -> Unit> = LinkedHashMap()

    override fun cell(row: Int, column: Int, content: @Composable () -> Unit) {
        cells[MosaicCell(row, column)] = content
    }
}

@Composable
fun MosaicPanel(block: MosaicScope.() -> Unit) {
    val scope = MosaicScopeImpl().apply(block)
    SwingNode(
        factory = { JPanel(MosaicLayout()) },
        content = {
            scope.cells.forEach { (cell, content) ->
                key(cell) { SwingConstraint(cell) { content() } }
            }
        },
    )
}

MosaicPanel {
    cell(row = 0, column = 0) { Label("Title") }
    cell(row = 1, column = 0) { Label("Body") }
}
```

The value is whatever your manager's `addLayoutComponent` accepts - the framework hands it over the
way `Container.add(Component, Object)` does, so a `LayoutManager2` receives it as-is and a manager
that takes no constraints places the child by index. It follows composition state like any other
value: change it and the child moves within the same parent, keeping its position among its siblings.

Changed is decided by `equals`. A placement rebuilt in the composable body is compared against the one
the child already sits under, and only a difference re-registers the child: the framework takes it out
of the layout manager and adds it back under the new value, then revalidates the parent. A value that
compares by value therefore costs nothing to rebuild, while one that compares by identity - a bare
`GridBagConstraints`, a class of your own that never overrode `equals` - is a new instance on every
pass and puts every child through that removal and re-add on every composition. What that costs is the
manager's, not the framework's: a `LayoutManager2` that keeps anything derived from the placements it
has been handed - a grid, a set of measured column widths, a row cache - discards and rebuilds it each
time, because a re-registration reaches it as the same `removeLayoutComponent`/`addLayoutComponent`
pair a real structural change does. Give a placement of your own a value `equals`, as `GridBagPanel`
does for the constraints its items declare.

Derive that value from the declaration rather than from a cursor running through the emitting loop,
too: a placement computed from how many children came before it changes for every later child the
moment one is inserted, and every one of them is then re-registered. This is advice about the
placement value. The `key` a child is emitted under is a separate question, answered in *What the
composition owns* - and answered there with position as the fallback, which is why `GridBagPanel`,
whose items state their own cells, still emits them under `key(index)`: a declaration that carries
nothing to tell it from its siblings has nothing else to be keyed by.

A receiver DSL like the one above keeps the constraint's type inside your container - `MosaicScope`
is the whole placement API your callers see, and `BorderPanel`'s regions and `GridBagPanel`'s items
are the same shape over a fixed, nameable set of placements. A container that would rather not spell
its placements out takes a plain content lambda instead and lets callers wrap children in
`SwingConstraint` themselves.

Three things make a recording scope like this a declaration rather than a build, and every scope this
library ships has all three:

- **The block is an ordinary lambda, not a `@Composable` one.** Running it records placements; nothing
  is composed while it runs. The composing happens afterwards, inside `content`, where the applier is
  listening. This holds at every level: a nested block the scope offers - a group, a section, an
  indented run - is recorded the same way and is an ordinary lambda too, and only the leaf slots are
  `@Composable`.
  What an ordinary block buys is that a component a caller emits straight into it, outside any
  placement, does not compile. What it costs is call-site identity: every child now leaves one emitting
  loop inside your container, so a declaration is identified by its position among the declarations
  rather than by the place in the caller's code that made it. Insert a declaration in the middle and
  every declaration from there on is handed the child that belonged to the one that used to follow it,
  along with whatever that child held - a typed-in text, a caret, a tree's expansion - and the
  declaration left at the end is built afresh. A recorded scope therefore offers a `key` parameter per
  declaration - whatever the caller knows tells one of theirs from another, wrapped around the
  placement as it is emitted - and a container whose children hold what the user typed is a container
  whose callers will need it.
- **What a placement records is a `@Composable` lambda.** The scope holds the caller's `content`
  unevaluated and hands it back to `SwingConstraint`, so each child is composed in the container's own
  content and becomes a node. A scope that recorded components instead would have built them outside
  the composition, where nothing can update or release them. It records them in one sequence, too:
  children reach the applier in the order `content` emits them, so a scope that keeps a list per kind
  of declaration emits them grouped by kind, in an order the caller never wrote and cannot express.
- **A scope that records is built fresh each composition** - constructed and applied in the composable
  body, never `remember`ed. Its contents are then exactly what this composition declared, so a
  placement the caller stops declaring disappears, where a remembered recording scope keeps every
  placement any earlier composition ever made and grows on each pass. The rule is about accumulation,
  not about receivers: one that records nothing and only carries where in your container its
  declarations sit - a group token, an indent depth, whatever a nested block narrows for the
  declarations inside it - is an ordinary value, and `remember(group, indent) { ... }` is how to build
  it, so a recomposition does not hand its declarations a new receiver.

Marking the block `@Composable` is the other shape, and what it changes is where a stray child lands
and what identifies the ones that were placed properly. Where it lands follows where you run the
block. Run it in your own composable body - as a composite assembled from built-in containers does,
`Framed` below - and a component emitted straight into it composes in the composition around yours, so
it becomes a child of whatever encloses your container, silently, and it compiles. Pass the block into
your own `content` instead, so it runs inside your `SwingNode`, and that component reaches your own
layout manager carrying no placement, where the manager can refuse it and say so:

```text
Every component of a form belongs to a row. Emit this one inside FormRow { ... }: javax.swing.JTextField[...]
```

A runtime boundary rather than a compile-time one, but a boundary you can name. In exchange every
declaration keeps the identity of the call that made it, so `if (showEmail) FormRow("Email")` followed
by `FormRow("Phone")` leaves the phone row's component and its contents alone when the email row
appears, with no `key` from anyone. That is the whole trade: a compile-time boundary and positional
identity, against call-site identity and a boundary you write yourself. The containers this library
ships are recorded scopes, and a container placing components its caller hands it whole is usually
right to be one; a container whose own declarations wrap state-holding widgets is the case that wants
`@Composable` declarations.

The choice reaches the caller through the names, too. A recorded declaration is an ordinary function
and reads `item(...)`, `north(...)`, `cell(...)`; a `@Composable` declaration is a `Unit`-returning
composable and is PascalCase by convention, which IDE inspections enforce. Callers of such a scope
write `FormRow("Name")`, not `row("Name")` - and the container's name in front is not decoration: a
bare `Row` member takes precedence over this library's own `Row` for every call inside the block.

Two things such a scope should not do. It should not spend a component on a slot: wrapping every child
in a panel of your own to carry something - a modifier, a border, an alignment - puts a real container
with a real layout between the caller's component and yours, and a `modifier` the slot accepted is then
applied to that container instead of to the component the caller wrote. It also changes what the layout
above sees: a parent measures and inspects the children it holds, so a gap policy that treats a nested
panel differently from a control, or an alignment that lines a label up against the field beside it,
reads the wrapper you inserted instead of the component, and the same components laid out through your
container stop matching the ones laid out without it. What the container has to say about a child is
the placement it emits the child under; what the caller has to say about their own component is a
`modifier` on that component. And it should not accept a declaration it does not emit:
an unemitted slot is not an error anywhere, because the applier only ever hears about the children it
is given, so such a declaration is simply a child that is not there. A parameter the scope accepts and
never applies is the same silence one level down - either it reaches the component or it leaves the
signature.

Placement belongs to the container that composes the child: `SwingConstraint` reaches the nodes its
own content emits, and a container composed inside takes the constraint for itself and lays its own
children out under the constraints it provides them.

A placement is not per child, either. `SwingConstraint` reaches every component its content emits
directly, so one placement can cover a run of siblings, and what several components under one value
mean is the manager's to decide: a manager that reads the value as a coordinate puts them all in the
same place, one that reads it as the group they belong to lays each of them out within that group. The
examples here place one child each because each of their placements names one position; a placement
that names a region, a row or a group is free to cover everything in it.

### When the placement is not something the caller states

The placements above are values the caller writes: a region name, a cell, a pair of grid coordinates.
A manager of your own may instead derive a child's placement from the children around it - a form whose
column count is its widest row's, a group whose leading gap depends on whether another group precedes
it, a band that divides its width among whatever ended up in it. Nothing changes about how the
placement travels. What changes is that your container cannot compute the answer while it emits,
because the answer needs every declaration, and that your manager cannot hold on to what it derived,
because the next composition may change it.

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

A composite several screens reuse - a titled card, a labelled form, a frame with a fixed header and
footer - is a composable that emits the containers it needs and offers its placements as a scope
receiver. Everything the pattern needs is public: the built-in containers, the container `SwingNode`
overload for a Swing container of your own, and `SwingConstraint` for its placements.

The scope interface is the whole API callers see. Each slot is a `@Composable` lambda the scope
records, and the composite emits each one into the region it belongs in:

```kotlin
interface FramedScope {
    fun header(content: @Composable () -> Unit)
    fun body(content: @Composable () -> Unit)
    fun footer(content: @Composable () -> Unit)
}

private class FramedScopeImpl : FramedScope {
    var header: (@Composable () -> Unit)? = null
    var body: (@Composable () -> Unit)? = null
    var footer: (@Composable () -> Unit)? = null

    override fun header(content: @Composable () -> Unit) {
        header = content
    }

    override fun body(content: @Composable () -> Unit) {
        body = content
    }

    override fun footer(content: @Composable () -> Unit) {
        footer = content
    }
}

@Composable
fun Framed(
    title: String,
    modifier: SwingModifier = SwingModifier,
    block: FramedScope.() -> Unit,
) {
    val scope = FramedScopeImpl().apply(block)
    val border = remember(title) { TitledBorder(title) }
    BorderPanel(modifier = modifier.border(border)) {
        scope.header?.let { north(it) }
        scope.body?.let { center(it) }
        scope.footer?.let { south(it) }
    }
}

Framed(title = "Payment") {
    header { Label("Card details") }
    body { PaymentForm() }
    footer { Button("Pay", onClick = ::pay) }
}
```

Four things make a composite of this shape behave:

- **Hold the scope to its three rules** - an ordinary block, recording `@Composable` lambdas, into a
  scope built fresh each composition (see *Placing children under constraints*). A slot is a
  declaration the composite emits, exactly as a placement is. The ordinary block is not a choice at
  this shape: a composite has no `content` of its own to run the block inside, so a `@Composable` one
  would compose in the composition around the composite and a component emitted straight into it would
  land outside the composite altogether.
- **Keep the composite composable-shaped** - state in, callbacks out, and a `modifier` parameter
  chained onto the outermost container it emits, so a caller styles the composite the way they style
  any component.
- **`remember` the value objects the chain carries** - `remember(title) { TitledBorder(title) }`, as
  above. A `Border`, `Font` or `Icon` constructed in the composable body is a different instance on
  every recomposition, and Swing compares by identity: `JComponent.setBorder` repaints for any instance
  that is not the one it already holds, and relayouts when the insets differ. Handing the same instance
  back makes the re-applied write a no-op.
- **Let each container place its own children.** A container consumes the placement its parent gave it
  and provides its children the placements it defines, so a composite nests inside another - a
  `GridBagPanel` in the `body` slot above - with neither knowing about the other.

Reach for `SwingNode` here only when the Swing container itself is yours; a composite assembled from
built-in containers is an ordinary composable function.

## Hosting nested compositions: `hostsSubcompositions`

Both `SwingNode` overloads take an opt-in `hostsSubcompositions: Boolean = false`. Leave it `false`
(the default) unless your custom component, internally, drives its **own** `setContent` against one of
its children - for example a Swing container that manages tabs, popups, or split panes by calling
`setContent` on sub-panels it creates itself.

Set it `true` so those nested `setContent` calls **join the surrounding composition** instead of
spinning up a detached, independent one - sharing its recomposer, scope, and `CompositionLocal`s.

```kotlin
SwingNode(
    factory = { TabbedPanel() }, // a JComponent that runs setContent on its own tab panels
    hostsSubcompositions = true,
)
```

When `hostsSubcompositions = true`, the factory component **must** be a `javax.swing.JComponent`; a
bare `java.awt.Component` host throws `IllegalStateException` at apply. Keep it `false` for every
ordinary leaf or container component.

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
