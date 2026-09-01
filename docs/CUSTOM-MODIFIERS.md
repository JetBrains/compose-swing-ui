# Modifiers for a custom component

Colors, borders, placements and listeners reach a component through a `SwingModifier` chain rather
than through parameters of its own. This document is the `modifier` parameter a component takes, the
`NodeElement`/`Node` pair behind a property the library does not ship, the four shapes a listener is
attached in, and what stays an ordinary parameter instead. Building the component itself is
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

<!--- INCLUDE .*modifier.*
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JComponent
-->

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
- **Items** - `listItemRenderer`, which renders the items of a `JList` or a `JComboBox`, either through
  a composable cell or through a renderer the caller already has.
- **Metadata** - `name`, `toolTip`, `clientProperty`, `testTag`.
- **Interaction** - `enabled`, `focusable`, `focusTraversalIndex`, `orderedFocusTraversal`,
  `defaultButton`, `onHover`, `onFocus`, `onPointerEvent`, `onAccept`, `focusRequester`, `initialFocus`,
  `inputVerifier`, `verifyInputWhenFocusTarget`, `documentFilter`, `caretUpdatePolicy` (requires a
  `JTextComponent` whose caret is a `DefaultCaret`), and `popupAnchor`, which names the component a
  `ContextMenu` or `PopupMenu` declared beside it opens over, plus the listener builders
  (`mouseListener`, `keyListener`, ...; see *Attaching a listener* below).
- **Keyboard** - `onKeyEvent`, `onKeyStroke`.
- **Data transfer** - `draggable`, `dropTarget`, `onExportDone`, `clipboard`.
- **Accessibility** - `accessibleName`, `accessibleDescription`, `mnemonic`, and the `labelFor` /
  `labelTarget` pair that captions one component with another.

Chain them: `SwingModifier.foreground(Color.RED).lineBorder(Color.GRAY).onHover { ... }`. The framework
diffs the chain across recompositions, applies new/changed elements, and **restores the original
value** of any element that is removed from the chain.

When your component contributes its own elements (for example, the binding listener that drives its
callback), chain them **onto** the caller's `modifier` - `modifier.yourElement(...)`.

A modifier builder is a plain function, never `@Composable`. A chain is data: the caller builds it,
hoists it, passes it on, and holds it across passes, none of which a composable value can do. Where a
builder needs something remembered - a handle, a renderer, a listener that carries state - split it in
two: a `remember*` composable that creates the value, and a plain builder that takes it.
`rememberPopupAnchor()` with `popupAnchor(anchor)`, and `rememberListItemRenderer { }` with
`listItemRenderer(renderer)`, are both that pair.

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
- `NodeElement.update(node)` runs on attach, and on a chain change that hands the slot an element unequal
  to the one it holds - **write the new value here**, so a fresh element instance (a new value on
  recomposition) reaches the live node without re-creating it;
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

The registration is what identifies where the listener sits. One with no `key` is the same registration
only when it is the same object, so hold it in a `val`: one built afresh inside the call is a new
registration on every pass and re-registers the listener each time. Declaring a *different* registration
moves the listener to it. Where the add/remove pair closes over something that varies between call
sites - the name of a bound property, say - wrap it in a key type of the site's own and pass that as
`key`: two registrations with equal keys are the same registration.

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
  `focusRequester`, `initialFocus`, `verifyInputWhenFocusTarget`, `documentFilter` and `popupAnchor`.

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

**What it costs.** This is the one place in the everyday API where you name a component type, and holding
the component is what makes it a last resort. The composition owns the values it declares for a component
and owns its child list: it writes a declared value again when the declaration changes, settles one the
user can also change on the pass after it moves, and puts back what a modifier wrote once that modifier
leaves the chain. Code that takes the component and writes a property the composition declares, or adds
and removes children, becomes a second manager of the same thing - the widget holds a value nothing
declares until the next write or settlement overwrites it, or the two managers corrupt each other's
bookkeeping.

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
does: `declare`'s `onSettled` and anything a write through `mirror.write { }` provokes are contained
and reported, and the pass finishes. Where your component calls the caller's code itself during a pass,
catch `Throwable` around that call and hand it to `Thread.currentThread().uncaughtExceptionHandler`,
which is where Swing leaves an exception raised under its own pump - the caller sees the failure in the
place they already look for one, and the composition survives. Catch every type: what the caller's code
throws is theirs to choose, and whichever type went unnamed would be the one that ends the composition.
Catch only their code - what your component requires of a declaration is yours to state, and those
failures have to reach the caller as the failures they are.
