# Modifiers: concepts and authoring

A `SwingModifier` gives a component its listeners, decoration and its place in its parent. The component's
own properties, such as its colors and fonts, are set by its [style](STYLES.md). This page explains how a
modifier is applied, and how to accept one in your own component. Building the component itself is covered in
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

<!--- INCLUDE .*modifier.*
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JComponent
-->

## What a modifier is

A modifier is an immutable, ordered list of elements. The empty modifier is `SwingModifier` itself - the
companion object - and it is the default of every `modifier` parameter. A builder is an extension
function that appends one element with `then`. In this example, `north()` places the search field at the top of the panel, and `onFocus` shows a hint when the field gains focus.

<!--- INCLUDE .*modifier-placement-01.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.interaction.onFocus
-->

```kotlin
@Composable
fun SearchPanel(query: String, updateQuery: (String) -> Unit, showSearchHint: () -> Unit) {
    Panel(PanelLayout.Border()) {
        TextField(
            value = query,
            onValueChange = updateQuery,
            modifier = SwingModifier.north().onFocus(onGained = showSearchHint),
        )
    }
}
```

<!--- KNIT example-modifier-placement-01.kt -->
<!--- CLEAR -->

`a then b` combines the elements of `a` followed by those of `b`. Combining with the empty modifier on either side returns the other side. `foldIn` visits the elements in declaration order.

A property element, such as a client property, writes one value. An additive
element, such as a listener, installs something. A parent declaration is read by the component's parent
instead: see [Parent declarations](#parent-declarations).

A property of the component itself - a color, a font, an icon, a size - is not a modifier element.
The component's `style` sets it, and a style writes after the modifier; see
[Style or modifier](STYLES.md#style-or-modifier).

A modifier is immutable, and two modifiers built the same way from equal elements are equal, so one built
in place on every pass matches the previous pass. Reuse is a builder function: an extension that appends
the elements. [Scoped modifiers](FOUNDATION.md#scoped-modifiers) shows one that declares a scope.

## Order and merge

Property elements apply in the order the modifier declares them. Additive elements apply after every
property element, in their own declared order.

- **Last wins.** A later property element of the same type replaces an earlier one and takes its place
  in the order. Where two different elements write the same widget property - a whole geometry and one
  axis of it - the later declaration stands.
- **Additive elements accumulate.** Two listeners from the same builder both install and both fire.
- **After the component's own properties.** The modifier is applied after the `update` block of
  `SwingNode`, so an element overrides a value that `update` sets for the same property.
- **A style writes after the modifier.** Where a [style](STYLES.md) passed as the `style` parameter and a modifier
  element write the same widget property, the style's value stands. A `styleable` in the modifier writes at its
  position.

## Updating across passes

Each time the composable that declares a component recomposes, it declares the component's modifier
again; this page calls that one pass. A modifier equal to the one applied last writes nothing.
Otherwise each element is compared with the element that held its place on the last pass, and only a
changed element writes.

### Equality and skipping

Your `NodeElement` must implement `equals` and `hashCode` to compare what it declares, not rely on
instance equality. Every pass builds a fresh element, so an element equal only to itself is unequal on
every pass: its `update` runs each time, and the composable that takes the modifier cannot skip. Use a
`data class` to compare the declared values. An element equal to its counterpart from the last pass is
skipped, so a modifier built inline needs no `remember`.

Compare a field that the node registers (a listener or a binding) with `===`, and avoid a
`data class` there: an `equals` on the registered object could make two different registrations look
equal, and the node would keep the old one. An element's equality must match what its node does in
`update`.

Make the whole element equal only to itself (`this === other`) only for a write that must be redone on
every pass. Declare an element that carries nothing as an `object`: the same instance is
reused every pass and applies once.

A property element is written again, even when equal, on a pass where an earlier property element wrote,
where it or an earlier element moved place, or where any property element left the modifier. That keeps the later declaration standing where two elements write the same
property. An additive element equal to its counterpart is always skipped.

### Keyed and additive elements

A property element - `additive` left at its default `false` - is identified by its `key`, which
defaults to the element's class, so elements of different types never collide. Declared under the same
key on the next pass, it updates the same node. Override `key` only when several instances of the
*same* type must coexist independently, such as a client property keyed by its property name.

An additive element has no key and is identified by its position among the additive elements: the k-th
additive element of the modifier takes over from the k-th one of the last pass. A property element
entering or leaving does not change those positions.

Set `inheritable = true` only on a non-additive property that makes sense across a whole subtree, such
as a color or a font. Of the property elements, only such an element may be provided to descendants as a
[component default](ARCHITECTURE.md#component-defaults);
a default applies to each descendant whose component the element targets. An inheritable parent-layout
declaration can be a component default too, and applies to every component; see
[Parent declarations](#parent-declarations).

A `styleable` may appear more than once. An empty
style clears that declaration's contribution. Removing a later declaration restores the earlier declaration's value
for overlapping properties. An equal style is not run again unless a state it reads changes. When a style runs, a
property whose value did not change can still be written again, so that the declaration order keeps holding; see
[When a style runs](STYLES.md#when-a-style-runs).

### What is kept and what is replaced

- An element that takes the place of an element of its own class keeps that element's node. Where the two
  are unequal, its `update` runs against that node; the node is not created again, and a listener it
  installed is not reattached.
- An element that takes the place of an element of another class - a conditional modifier changing
  shape, or one key declared through two kinds of element - gets a new node. Under a key, the old node
  detaches first, putting back what it wrote, so the new node captures what the component held before
  that key was declared. For an additive or parent-layout element, the new node attaches first, while the
  old one is still in place, and the old node detaches after.
- The node of an element that leaves the modifier detaches, and a new element gets a new node.

## Node lifecycle

A node is the stateful object an element creates for one component: a `ComponentNode` for an element
that writes to the component, or a `ParentLayoutNode` for a declaration its parent reads (see
[Parent declarations](#parent-declarations)).

A node is created by its element's `create()`, once per slot. Its callbacks then run in this order:

1. `onAttach()`, as the node attaches;
2. the element's `update(node)`, right after, and again on each later pass that hands the slot an
   unequal element;
3. `onReset()`, only when the component is reused for different content or deactivated;
4. `onDetach()`, as the node detaches.

A node is attached from before `onAttach()` runs until after `onDetach()` returns, and `isAttached` says
so. The [node capabilities](#node-capabilities) are available only in between.

A modifier's nodes attach as a whole on its first pass and detach as a whole when the component is
released, reused or deactivated; a `key` change does the same for its component nodes. While any of them
runs `onAttach()` or `onDetach()`, every node of the modifier is attached, so a node may measure or
paint its component whatever its place in the modifier. A node entering or leaving a modifier that stays
attached attaches or detaches on its own. Property nodes detach in the reverse of the order the modifier
declared them last, so a node putting back what it captured finds every node declared before it still
attached.

A reused component creates every node again from the next modifier applied to it; a component moved to
another parent with `movableContent` keeps its nodes. A move within one composition runs no callback.
Moved into another composition, such as out of content set with `setContent` into the composition around
it, the nodes of every component in the moved content detach as a whole from the one and attach as a whole
to the other: each runs `onDetach()`, then `onAttach()` and its element's `update(node)` again. A node keeps
its fields across the move, so a node that skips a write equal to the last one it made must forget that
write in `onDetach()`, or the `update(node)` after the move skips it. Detaching drops the reads a node
observed and the work it requested there, so a node observes again from `onAttach()`, as on its first
attach. The nodes of a deactivated component are already detached and run
nothing. Once every element that wrote a property has left the modifier, the property is back to the value
it had before the modifier wrote it, unless an element declares a narrower `restores` policy.

### Rebuilding with `key`

Use `SwingModifier.key(vararg keys)` when the look and feel derives a property from one of your writes
and must derive it again even though no declaration changed: writing the same value again fires no
property change, but a rebuild restores and re-applies, which does.

The keys are compared by `equals`. When one changes, or the modifier starts or stops declaring keys,
every component node of the modifier detaches, putting back what it captured, and attaches again,
capturing what it finds then; whatever such a node binds - a caret, a document, an installed listener -
is built again with it. Parent-layout nodes stay attached and keep their state.

Keys apply to the whole modifier regardless of where they appear. `key(a).key(b)` is equivalent to `key(a, b)`. Repeating the same declaration counts once.

## Parent declarations

Some elements are read by the component's immediate parent rather than applied to the component: where
it sits in a `BorderLayout`, its `GridBagConstraints`, a weight in a `Row`. Each such declaration names
the family of parents that understands it, and one placed under any other parent is refused with an error
naming that family. An inheritable declaration, one that can be given as a
[component default](ARCHITECTURE.md#component-defaults), is left out under such a parent instead, whether
a default provides it or the component declares it. An inheritable declaration a style property can write is left out under such
a parent instead, as Foundation's
[`paintOutsets`](FOUNDATION.md#paint-outsets-taken-from-the-insets) is under a Swing parent. Writing
a declaration of your own, and what the parent receives, is covered in
[Parent data and layout modifiers](CUSTOM-CONTAINERS.md#parent-data-and-layout-modifiers).

## Node capabilities

`ComponentNode` and `ParentLayoutNode` both extend `SwingModifier.Node`. An attached node has these
capabilities, and each of them fails on a node that is not attached. `observeReads` and
`requestAfterValidation` also fail off the Event Dispatch Thread:

- `coroutineScope` runs on the composition's own effect context, so `withFrameNanos` inside it follows
  the window's frame clock. The scope is cancelled after `onDetach()`.
- A node that also implements `ObserverModifierNode` can call `observeReads { ... }`. Once a snapshot
  state read inside the block changes, `onObservedReadsChanged()` is called once, on the event dispatch
  thread. Call `observeReads` again from there to keep observing. A detached node is never called.
- Any node can call `observeReads(onChanged) { ... }` to record a set of reads under a callback of its
  own. Reads are grouped by the `onChanged` instance: a later call with the same instance replaces that
  set, and leaves the sets recorded under other instances alone. Pass a stable instance, such as a
  top-level `val`.
- `requestAfterValidation(action)` defers work while the component tree lock is held. Reuse a stable
  callback to coalesce requests; detaching the node cancels pending work.
- A node that also implements `CompositionLocalConsumerModifierNode` reads, through
  `currentValueOf(local)`, any composition local in scope where its component was declared. When a static
  local changes value, or a local starts or stops being provided, where the component was declared, every
  such node reacts, whether or not it read that local: a `ComponentNode` element's `update` runs again,
  after the node's `update` block as every modifier write does, and the parent measures, places and paints
  the component of a `ParentLayoutNode` again. A dynamic local read in a `ComponentNode` element's
  `update` runs that `update` again when its value changes; to follow a dynamic local's value in a
  `ParentLayoutNode`, read it inside `observeReads`. A read in `onAttach` is taken once per attach.

`visitDeclaredNodes { ... }` visits, in declaration order, the additive `ComponentNode`s and the
`ParentLayoutNode`s of the node's modifier. A component that implements `DeclaredNodesListener`
receives its `SwingComponentNode` and that list after each modifier pass that attaches, detaches or moves
one of those nodes, or writes one with a new element whose node has `shouldAutoInvalidate`, and after a
composition-local refresh that rewrites such a node, so it can paint or lay itself out through those nodes.
`ComponentNode.shouldAutoInvalidate` is `false` by default because component nodes write or repaint their own
component. Override it when the component's `DeclaredNodesListener` must reread this node's state. A
`ParentLayoutNode` defaults to `true`, so its parent measures the component again after an update; return `false`
when the node requests its own layout invalidation. Structural changes always hand over the node list.
Call the extension `SwingComponentNode.invalidateLayout()` when such a node changes the component's measured size. It
invalidates immediately and defers revalidation until the component tree lock is released.
`swing-ui-foundation` paints a component through such nodes; see
[Writing a decorator or draw node](FOUNDATION.md#writing-a-decorator-or-draw-node).

A component that implements `SwingComponentNodeListener` is told each `SwingComponentNode` holding it as the node
attaches and detaches. Two kinds of node hold it: the node a composition declares for it, and the root node of the
content `setContent` mounts on it. A `SwingComponentNode` is typed by its component, and the listener types the node
it is told with `node.asNodeOf(this)`. On an attached node, the extensions `observeReads(onChanged) { ... }` and
`requestAfterValidation(action)` work as the node capabilities above do, so a component can follow state without a
modifier node; both throw on a node that is not attached. The extension `invalidateLayout()` does nothing on such a
node.

A listener that throws from `onAttached` has the throw reported to the thread's uncaught-exception handler, and the
node stays attached, as if the call had returned. A listener that throws from `onDetached` has the throw reported the
same way, and the node detaches as if the call had returned: the reads and pending requests recorded under the node
are dropped. A node that moves to another composition, or is reused, after such a throw is still told it attached
again.

## Failure

A throw out of an element's `create()` or `update(node)`, or a node's `onAttach()`, ends the recomposer
that drives the content, as any throw while changes are applied does. A throw out of a node's `onReset()` or
`onDetach()` does not: it is reported to the thread's uncaught-exception handler, and the node resets or
detaches as if the callback had returned. What it leaves the modifier's nodes in is fixed:

- A node whose `onAttach()` threw is detached at once: its `onDetach()` runs, its `coroutineScope` is
  cancelled and its observed reads are cleared. `onDetach()` must therefore tolerate an `onAttach()` that
  did not finish. A throw from that `onDetach()` is suppressed on the `onAttach()` failure, not reported.
- A node whose `onAttach()` returned runs `onDetach()` exactly once when it detaches, even where its
  element's `update` then threw.
- An additive or parent-layout slot whose replacement node could not be created keeps the node it had.
  A replacement that attached and then threw in `update` takes the slot, and the node it replaced
  detaches.

## Compared with AndroidX `Modifier`

A `SwingModifier` differs from `androidx.compose.ui.Modifier` in these ways:

- **Style versus modifier.** As in AndroidX, where a `Style` sets the properties of a component and a
  `Modifier` keeps layout, drawing and input, a `SwingStyle` sets the properties of the Swing component and a
  `SwingModifier` keeps listeners, parent data, decoration and metadata. A style setter the component does
  not offer is a compile error. A modifier is untyped, so an element its component cannot take fails when
  it is applied. See
  [Style or modifier](STYLES.md#style-or-modifier).
- **Properties are written onto the live component.** The Swing component measures and paints itself.
  A modifier sets properties on it and puts back what it wrote when the element leaves, as the
  element's `restores` policy says.
- **One value per property.** A property element replaces an earlier one under the same key, where
  an AndroidX `Modifier` keeps both and runs both; additive elements accumulate (see
  [Keyed and additive elements](#keyed-and-additive-elements)).
- **Bound to a component type.** An element names the component type it targets and is refused on any
  other, as a parent declaration is under a parent of another family.
- **`key` rebuilds.** A changed key detaches and attaches again every component node of the
  modifier (see [Rebuilding with `key`](#rebuilding-with-key)).
- **Matched by kind.** An AndroidX `Modifier` chain is diffed as one list. Here a property element is
  matched by its key, and additive and parent-layout elements each by position among their own kind, so
  an element inserted before others of its kind moves each of them one place along: its node is
  updated with the element it now gets, or replaced where that element's class differs.
- **No head-to-tail attach order.** The nodes of a modifier attach together, so during `onAttach()` and
  `onDetach()` every node of the modifier is attached, whatever its place in the modifier (see
  [Node lifecycle](#node-lifecycle)).

## Styling with a `style` parameter

Give your component a `modifier: SwingModifier = SwingModifier` parameter and a `style` parameter next to it,
and hand both to the node. The style declares the component's properties and the modifier its place and
listeners:

```kotlin
@Composable
public fun MyWidget(
    /* state + callbacks */
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<MyWidgetStyleScope> = SwingStyle,
) {
    SwingNode(
        factory = { /* ... */ },
        modifier = modifier.styleable(MyWidgetKind, style),
        update = {
            set(/* ... */) { /* ... */ }
        },
    )
}
```

<!--- CLEAR -->

Always pass `modifier` to the node. A child's place in its parent (a `BorderLayout` region,
`GridBagConstraints`, a cell in your own layout) is declared on its modifier, so a component that drops
the parameter cannot be placed: every container adds it by index. Add your component's own elements
after the caller's, as [Order and merge](#order-and-merge) says. Put `styleable` last to give the style precedence.
[Components that take a style](STYLES.md#components-that-take-a-style) explains how to write the scope and
the kind.

## Writing a builder

The simplest builder chains builders that already exist. It extends `this`, so the elements the caller
declared before it stay in the modifier; a builder that starts again from `SwingModifier` drops them.

<!--- INCLUDE .*custom-modifier-01.*
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.shadow
import java.awt.Color
-->

```kotlin
public fun SwingModifier.card(): SwingModifier =
    shadow(radius = 8, color = Color(0, 0, 0, 96)).background(Color.WHITE, RectangleShape)
```

<!--- KNIT example-custom-modifier-01.kt -->
<!--- CLEAR -->

Where a builder needs a value the caller holds or shares - a handle, an anchor, a renderer - split it in
two: a `remember*` composable that creates the value, and a plain builder that takes it.
`rememberPopupAnchor()` with `popupAnchor(anchor)`, and `rememberListItemRenderer { }` with
`listItemRenderer(renderer)`, are both that pair.

State private to each component the modifier reaches, such as whether that component is hovered, belongs
on a `ComponentNode`, created once per component: it holds that state in its fields, runs coroutines in
its `coroutineScope` and, implementing `CompositionLocalConsumerModifierNode`, reads composition locals
through `currentValueOf` (see [A property written by an element](STYLES.md#a-property-written-by-an-element)
and [Node capabilities](#node-capabilities)).

`composed` is not recommended; use it only when the modifier can only be built by calling composables.
What its factory returns is applied where `composed` stands in the modifier, and a `remember` inside it
keeps one value per component. The factory runs a composition for every component the modifier reaches,
and each `composed` call creates an element unequal to the previous pass's, so the composable taking it
cannot skip:

<!--- INCLUDE .*custom-modifier-02.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.composed
import org.jetbrains.compose.swing.modifier.interaction.onHover
import java.awt.Color
-->

```kotlin
public fun SwingModifier.hoverBackground(color: Color): SwingModifier =
    composed {
        var hovered by remember { mutableStateOf(false) }
        val hover = onHover(onEnter = { hovered = true }, onExit = { hovered = false })
        if (hovered) hover.background(color, RectangleShape) else hover
    }
```

<!--- KNIT example-custom-modifier-02.kt -->
<!--- CLEAR -->

## Writing an element of your own

A property of a component is written by a style, not by a modifier element. To write one from an element
of your own, see [A property written by an element](STYLES.md#a-property-written-by-an-element). For a
single read/write property, `stylePropertyOf` over a descriptor is enough: see
[Declaring a property](STYLES.md#declaring-a-property). For a property that several unrelated component
types declare, see [A property several components share](STYLES.md#a-property-several-components-share).
A property that a component declares through one of its own parameters stays `SwingModifier.property`: see
[A property the look and feel decides](CUSTOM-COMPONENTS.md#a-property-the-look-and-feel-decides).

## Attaching a listener

### Typed instance builders - attach an existing listener object

To attach an existing Swing/AWT listener **object** as-is, use the typed instance builders, one per
listener interface and named after it - `mouseListener`, `actionListener`, `documentListener`. Each is
additive, mirroring Swing's `addXxxListener`, and owns the lifecycle: the instance is added on install
and removed on detach, reset or reuse. Pass a remembered instance: a different instance on a later pass
removes the old listener and adds the new one. A builder whose event source only some components have -
`documentListener` needs a `JTextComponent` - rejects any other component at apply. See
[`ARCHITECTURE.md`](ARCHITECTURE.md#the-node-lifecycle-and-listeners) for listener lifetimes and why
listeners the host app attached are untouched.

```kotlin
val onMove = remember { object : MouseAdapter() { override fun mouseMoved(e: MouseEvent) { /* ... */ } } }
SwingModifier.name("canvas").mouseMotionListener(onMove)
```

<!--- CLEAR -->

### Lambda overloads - write the handler at the call site

Every builder above also takes a lambda, and a lambda selects that overload rather than the instance
one. The library builds the listener and reads the lambda when the event fires, so declaring a fresh
lambda on every pass registers nothing again and needs no `remember`:

```kotlin
SwingModifier.actionListener { event -> println(event.actionCommand) }
```

<!--- CLEAR -->

Where the listener interface has a single method, the lambda is that method's. For
`propertyChangeListener` bound to a property name, declaring a different name moves the registration to
that property.

Where it has several methods there are two overloads: one lambda that every method of the interface
calls, and one parameter per method for a caller that tells them apart.

```kotlin
SwingModifier
    .documentListener { println("the document changed") }
    .mouseListener(onMouseClicked = { println("clicked") }, onMouseExited = { println("left") })
```

<!--- CLEAR -->

A method you leave out does nothing. The single-lambda overload runs once per method, so one mouse
interaction reaches it more than once. `treeWillExpandListener`'s lambdas answer with a boolean
instead: returning `false` leaves the node as it was.

A throw from a lambda is reported to the thread's uncaught-exception handler where Swing raises the event
inside a write, so the write finishes and the remaining listeners run. That covers the action, item,
change, property-change, document, container, selection and tree-expansion lambdas, among others. The
mouse, key, focus, component and hyperlink lambdas are reached only by a gesture or a posted event, and
Swing's event pump handles their throw.

### `SwingModifier.listener(callback, registration)` - a lambda over a listener the library has no builder for

Use `listener(callback, registration)` when the event source has no builder (a listener kind the
library does not ship, or one registered on a model rather than on the component) and you have a
lambda.

A registration is where a listener is registered, named by the event source it registers on. Declare
one per event source, at the top level or in a companion, and hand it to every declaration that
registers there:

<!--- INCLUDE .*custom-modifier-03.*
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
-->

```kotlin
private val ANCESTOR_EVENTS =
    CallbackRegistration<JComponent, (AncestorEvent) -> Unit, AncestorListener>(
        adapter = { callback ->
            object : AncestorListener {
                override fun ancestorAdded(event: AncestorEvent) = callback.report { it(event) }

                override fun ancestorRemoved(event: AncestorEvent) = callback.report { it(event) }

                override fun ancestorMoved(event: AncestorEvent) = callback.report { it(event) }
            }
        },
        registration =
            ListenerRegistration(
                "ancestorListener",
                { component, listener -> component.addAncestorListener(listener) },
                { component, listener -> component.removeAncestorListener(listener) },
            ),
    )

public fun SwingModifier.onAncestorChange(onChange: (AncestorEvent) -> Unit): SwingModifier =
    listener(onChange, ANCESTOR_EVENTS)
```

<!--- KNIT example-custom-modifier-03.kt -->

It registers the listener `adapter` builds and hands that listener the latest `callback` every time an
event fires, so a `callback` written at the call site needs no `remember`.

The registration is what identifies where the listener sits. One with no `key` is the same registration
only when it is the same object, so hold it in a `val`: one built afresh inside the call is a new
registration on every pass and re-registers the listener each time. Declaring a *different* registration
moves the listener to it. Where the add/remove pair closes over something that varies between call
sites - the name of a bound property, say - wrap it in a key type of the site's own and pass that as
`key`: two registrations with equal keys are the same registration.

### `SwingModifier.listener(instance, registration)` - the last resort

Use `listener(instance, registration)` only when you hold a listener object for an event source that
has no builder.

<!--- INCLUDE .*custom-modifier-04.*
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.JMenu
import javax.swing.event.MenuListener
-->

```kotlin
private val MENU_EVENTS =
    ListenerRegistration<JMenu, MenuListener>(
        "menuListener",
        { menu, listener -> menu.addMenuListener(listener) },
        { menu, listener -> menu.removeMenuListener(listener) },
    )

public fun SwingModifier.menuListener(instance: MenuListener): SwingModifier = listener(instance, MENU_EVENTS)
```

<!--- KNIT example-custom-modifier-04.kt -->

Like the typed builders, it is additive: the instance is added when the element enters the modifier and
removed when it leaves or the component is released, reused or deactivated. The add and remove functions
receive the component, typed as the registration names it, and any other component is rejected when the
modifier is applied. Register and unregister there, and change nothing the composition declares for it
(see [What the composition owns](CUSTOM-COMPONENTS.md#what-the-composition-owns)).

## Domain callbacks stay component parameters

Per-component semantic callbacks - `onClick` on a button, `onValueChange` on a slider - are parameters
of your composable function, not modifiers. A callback your component calls from `update` needs one
extra guard: see [A callback your component calls itself](COMPONENT-STATE.md#a-callback-your-component-calls-itself).
