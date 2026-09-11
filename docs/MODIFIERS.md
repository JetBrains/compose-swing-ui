# Modifiers: concepts and authoring

A `SwingModifier` gives a component its colors, borders, listeners and its place in its parent. This
page explains how a modifier is applied, and how to accept one in your own component and write your own
elements. Building the component itself is covered in [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

<!--- INCLUDE .*modifier.*
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JComponent
-->

## What a modifier is

A modifier is an immutable, ordered list of elements. The empty modifier is `SwingModifier` itself - the
companion object - and it is the default of every `modifier` parameter. A builder is an extension
function that appends one element with `then`: `SwingModifier.foreground(Color.RED).lineBorder(Color.GRAY)`
is the empty modifier, then a foreground, then a border. `a then b` applies `a` and then `b`; `then`
with the empty modifier on either side returns the other side. `foldIn` visits the elements in that
order.

Elements come in three kinds. A property element, such as a background, writes one value. An additive
element, such as a listener, installs something. A parent declaration is read by the component's parent
instead: see [Parent declarations](#parent-declarations).

Being immutable, a modifier can be built inline, hoisted, held across passes, shared between components
and passed on. Two modifiers built the same way from equal elements are equal.

## Order and merge

Property elements apply in the order the modifier declares them. Additive elements apply after every
property element, in their own declared order.

- **Last wins.** A later property element of the same type replaces an earlier one and takes its place
  in the order. Where two different elements write the same widget property - a whole geometry and one
  axis of it - the later declaration stands.
- **Additive elements accumulate.** Two listeners from the same builder both install and both fire.
- **After the component's own properties.** The modifier is applied after the `update` block of
  `SwingNode`, so an element overrides a value that `update` sets for the same property.

## Matching across passes

Each time the composable that declares a component recomposes, it declares the component's modifier
again; this page calls that one pass. A modifier equal to the one applied last writes nothing.
Otherwise each element is matched to a slot and judged on its own.

### Equality and skipping

Your `NodeElement` must implement `equals` and `hashCode` to compare what it declares, not rely on
instance equality. Every pass builds a fresh element, so an element equal only to itself is unequal on
every pass: its `update` runs each time, and the composable that takes the modifier cannot skip. Use a
`data class` to compare the declared values. An element equal to the one its slot applied last is
skipped, so a modifier built inline needs no `remember`.

Compare a field that the node registers (a listener, a binding, a slot) with `===`, and avoid a
`data class` there: an `equals` on the registered object could make two different registrations look
equal, and the node would keep the old one. An element's equality must match what its node does in
`update`.

Make the whole element equal only to itself (`this === other`) only for a write that must be redone on
every pass. Declare an element that carries nothing as an `object`: the slot gets the same instance
every pass and applies it once.

A property element is updated even when equal on a pass where a property element declared before it
wrote, where it or one declared before it changed place, or where a property element left the
modifier. That keeps the later declaration standing where two elements write the same property. An
additive element equal to the one its slot holds is always skipped.

### Keyed and additive slots

A property element - `additive` left at its default `false` - takes the slot of its `key`, which
defaults to the element's class, so elements of different types never collide. Declared under the same
key on the next pass, it keeps that slot and its node. Override `key` only when several instances of the
*same* type must coexist as independent slots, such as a client property keyed by its property name.

An additive element has no key and is paired by position instead: the k-th additive element of the
modifier takes the slot of the k-th additive element the modifier applied last. Only additive elements
are counted, so a property element entering or leaving shifts no additive slot.

Set `inheritable = true` only on a non-additive property that makes sense across a whole subtree, such
as a color or a font. Only such an element may be provided to descendants as a
[component default](ARCHITECTURE.md#component-defaults);
a default applies to each descendant whose component the element targets.

### What stands and what is recreated

- An element matched to a slot built by an element of its own class keeps that slot's node. Where it is
  unequal to the element the slot held, its `update` runs against that node; the node is not created
  again, and a listener it installed is not reattached.
- An element matched to a slot built by an element of another class - a conditional modifier changing
  shape, or one key declared through two kinds of element - replaces it with a node of its own. In a
  keyed slot the old node detaches first, putting back what it wrote, so the new node captures what the
  component held before that key was declared. In an additive or parent-layout slot the new node
  attaches first, while the old one is still in place, and the old node detaches after.
- The node of a slot left without an element detaches, and a new element takes a new slot.

## Node lifecycle

A node is the stateful object an element creates for one component: a `ComponentNode` for an element
that writes to the component, or a `ParentLayoutNode` for a declaration its parent reads (see
[Parent declarations](#parent-declarations)).

A node is created by its element's `create()`, once per slot. Its callbacks then run in this order:

1. `onAttach()`, once;
2. the element's `update(node)`, right after, and again on each later pass that hands the slot an
   unequal element;
3. `onReset()`, only when the component is reused for different content or deactivated;
4. `onDetach()`, once, as the node detaches.

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
another parent with `movableContent` keeps its nodes. Once every element that wrote a property has left
the modifier, the property is back to the value it had before the modifier wrote it, unless an element
declares a narrower `restores` policy.

### Rebuilding with `key`

Use `SwingModifier.key(vararg keys)` when the look and feel derives a property from one of your writes
and must derive it again even though no declaration changed: writing the same value again fires no
property change, but a rebuild restores and re-applies, which does.

The keys are compared by `equals`. When one changes, or the modifier starts or stops declaring keys,
every component node of the modifier detaches, putting back what it captured, and attaches again,
capturing what it finds then; whatever such a node binds - a caret, a document, an installed listener -
is built again with it. Parent-layout nodes stay attached and keep their state.

The keys belong to the modifier rather than to a place in it: `key(t).background(c)` and
`background(c).key(t)` say the same thing. `key(a).key(b)` says what `key(a, b)` does, and the same
declaration repeated counts once.

## Parent declarations

Some elements are read by the component's immediate parent rather than applied to the component: where
it sits in a `BorderLayout`, its `GridBagConstraints`, a weight in a `Row`. Each such declaration names
the family of parents that understands it, and one placed under any other parent fails with an error
naming that family. Writing a declaration of your own, and what the parent receives, is covered in
[Parent data and layout modifiers](CUSTOM-CONTAINERS.md#parent-data-and-layout-modifiers).

## Node capabilities

`ComponentNode` and `ParentLayoutNode` both extend `SwingModifier.Node`. An attached node has these
capabilities, and each of them fails on a node that is not attached:

- `coroutineScope` runs on the composition's own effect context, so `withFrameNanos` inside it follows
  the window's frame clock. The scope is cancelled after `onDetach()`.
- A node that also implements `ObserverModifierNode` can call `observeReads { ... }`. Once a snapshot
  state read inside the block changes, `onObservedReadsChanged()` is called once, on the event dispatch
  thread. Call `observeReads` again from there to keep observing. A detached node is never called.
- Any node can call `observeReads(onChanged) { ... }` to record a set of reads under a callback of its
  own. Reads are grouped by the `onChanged` instance: a later call with the same instance replaces that
  set, and leaves the sets recorded under other instances alone. Pass a stable instance, such as a
  top-level `val`.
- A node that also implements `CompositionLocalConsumerModifierNode` reads, through
  `currentValueOf(local)`, any composition local in scope where its component was declared. When a static
  local changes value, or a local starts or stops being provided, where the component was declared, every
  such node reacts, whether or not it read that local: a `ComponentNode` element's `update` runs again,
  after the node's `update` block as every modifier write does, and the parent measures, places and paints
  the component of a `ParentLayoutNode` again. A dynamic local read in a `ComponentNode` element's
  `update` runs that `update` again when its value changes; to follow a dynamic local's value in a
  `ParentLayoutNode`, read it inside `observeReads`. A read in `onAttach` is taken once.

`visitDeclaredNodes { ... }` visits, in declaration order, the additive `ComponentNode`s and the
`ParentLayoutNode`s of the node's modifier. A component that implements `DeclaredNodesListener`
receives that list after each pass that changes it, so it can paint or lay itself out through those
nodes.

## Failure

A throw out of an element's `create()` or `update(node)`, or a node's `onAttach()`, ends the recomposer
that drives the content, as any throw while changes are applied does. What it leaves the modifier's
nodes in is fixed:

- A node whose `onAttach()` threw never runs `onDetach()`.
- A node whose `onAttach()` returned runs `onDetach()` exactly once when it detaches, even where its
  element's `update` then threw.
- An additive or parent-layout slot whose replacement node could not be created keeps the node it had.
  A replacement that attached and then threw in `update` takes the slot, and the node it replaced
  detaches.

## Compared with AndroidX `Modifier`

A `SwingModifier` differs from `androidx.compose.ui.Modifier` in these ways:

- **Nodes write onto a live component.** The Swing component measures and paints itself; a component
  node sets properties on it and puts back what it wrote when it leaves, as its element's `restores`
  policy says.
- **One value per property.** A property element replaces an earlier one under the same key, where
  an AndroidX `Modifier` keeps both and runs both; additive elements accumulate (see
  [Keyed and additive slots](#keyed-and-additive-slots)).
- **Bound to a component type.** An element names the component type it targets and is refused on any
  other, as a parent declaration is under a parent of another family.
- **`key` rebuilds.** A changed key detaches and attaches again every component node of the
  modifier (see [Rebuilding with `key`](#rebuilding-with-key)).
- **Matched by kind.** An AndroidX `Modifier` chain is diffed as one list. Here a property element is
  matched by its key, and additive and parent-layout elements each by position among their own kind, so
  an element inserted before others of its kind moves each of them one slot along: a slot's node is
  updated with the element it now gets, or replaced where that element's class differs.
- **Attach order.** A modifier attaching as a whole attaches its parent-layout nodes first, then its
  property nodes, then its additive nodes, where an AndroidX `Modifier` attaches head to tail.

## Styling with a `modifier: SwingModifier` parameter

Give your component a `modifier: SwingModifier = SwingModifier` parameter and hand it to the node:

```kotlin
@Composable
public fun MyWidget(
    /* state + callbacks */
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { /* ... */ },
        modifier = modifier,
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
after the caller's, as [Order and merge](#order-and-merge) says.

## Writing a builder

The simplest builder chains builders that already exist. It extends `this`, so the elements the caller
declared before it stay in the modifier; a builder that starts again from `SwingModifier` drops them.

<!--- INCLUDE .*custom-modifier-01.*
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import java.awt.Color
-->

```kotlin
public fun SwingModifier.card(): SwingModifier = lineBorder(Color.GRAY).background(Color.WHITE)
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
through `currentValueOf` (see [Writing a custom property element](#writing-a-custom-property-element)
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
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.composed
import org.jetbrains.compose.swing.modifier.interaction.onHover
import java.awt.Color
-->

```kotlin
public fun SwingModifier.hoverBackground(color: Color): SwingModifier =
    composed {
        var hovered by remember { mutableStateOf(false) }
        val hover = onHover(onEnter = { hovered = true }, onExit = { hovered = false })
        if (hovered) hover.background(color) else hover
    }
```

<!--- KNIT example-custom-modifier-02.kt -->
<!--- CLEAR -->

## Writing a custom property element

For a single read/write property, `SwingModifier.property` is enough: see
[A property the look and feel decides](CUSTOM-COMPONENTS.md#a-property-the-look-and-feel-decides).
Write a `NodeElement` and `ComponentNode` pair when you need a wider target type, several components
written from one element, or a registration that is not a property:

- `NodeElement<T : Component, N : ComponentNode<T>>` is the immutable description: the value to write
  and the component type it targets. Every pass builds a fresh one.
- `ComponentNode<T : Component>` holds the per-component state, such as the captured original value. It
  is created once per slot, kept across passes, and exposes the live, already-typed `component`.

An element must be a `NodeElement` or a parent declaration, and not both; an element of your own that
is neither is refused when the modifier is applied.

`targetType` is the `Class` of the element's `T`: the most general component type the property needs,
such as `Component::class.java` for any AWT component, `JComponent::class.java` for a tooltip or border,
or a widget class for a widget-only property. The node's `component` is then typed `T`, and a component
that is not a `T` is rejected when the modifier is applied.

What each callback of the pair is for (see [Node lifecycle](#node-lifecycle) for when each runs):

- `NodeElement.create()` builds the node;
- `ComponentNode.onAttach()` - **capture the component's existing value here** so it can be restored;
- `NodeElement.update(node)` - **write the new value here**, so a fresh element instance reaches the
  live node without re-creating it. A composition local read here runs it again (see
  [Node capabilities](#node-capabilities));
- `ComponentNode.onReset()` - **drop state tied to the old content here**;
- `ComponentNode.onDetach()` - **restore the captured original here**: every property your write
  changed, not only the one the element is named for - a whole geometry covers each axis it spans - and
  list them all in `NodeElement.heldProperties`. Where you cannot restore them all, declare
  `NodeElement.restores`; see `RestorePolicy` for each option.

A tooltip lives on `JComponent`, so the element targets `JComponent` and the node reads its
already-typed `component` without casting:

```kotlin
private class ToolTipNode : SwingModifier.ComponentNode<JComponent>() {
    private var original: String? = null
    var text: String? = null

    override fun onAttach() {
        original = component.toolTipText
    }

    fun apply() {
        component.toolTipText = text
    }

    override fun onDetach() {
        component.toolTipText = original
    }
}

private data class ToolTipElement(
    private val text: String?,
) : SwingModifier.NodeElement<JComponent, ToolTipNode>() {
    override val name: String get() = "toolTip"

    override val declaredValues: Map<String, Any?> get() = mapOf("text" to text)

    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): ToolTipNode = ToolTipNode()

    override fun update(node: ToolTipNode) {
        node.text = text
        node.apply()
    }
}

public fun SwingModifier.toolTip(text: String?): SwingModifier =
    this then ToolTipElement(text)
```

<!--- KNIT example-custom-modifier-03.kt -->

### Naming the element for a message and for a tool

`name` and `declaredValues` are shown in error messages and in inspection tools (see
[`INSPECTING-COMPOSITIONS.md`](INSPECTING-COMPOSITIONS.md#reading-the-chain-a-component-carries)). By
default `name` is the class name and `declaredValues` is empty; override both when the element carries
a value worth showing. `key`, not `name`, decides which slot an element takes. Leave a callback out of
`declaredValues` unless showing it says something - a lambda renders as its class.

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

### `SwingModifier.listener(callback, registration)` - a lambda over a listener the library has no builder for

Use `listener(callback, registration)` when the event source has no builder (a listener kind the
library does not ship, or one registered on a model rather than on the component) and you have a
lambda.

A registration is where a listener is registered, named by the event source it registers on. Declare
one per event source, at the top level or in a companion, and hand it to every declaration that
registers there:

<!--- INCLUDE .*custom-modifier-04.*
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
-->

```kotlin
private val ANCESTOR_EVENTS =
    CallbackRegistration<JComponent, (AncestorEvent) -> Unit, AncestorListener>(
        adapter = { current ->
            object : AncestorListener {
                override fun ancestorAdded(event: AncestorEvent) = current()(event)

                override fun ancestorRemoved(event: AncestorEvent) = current()(event)

                override fun ancestorMoved(event: AncestorEvent) = current()(event)
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

<!--- KNIT example-custom-modifier-04.kt -->

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

<!--- INCLUDE .*custom-modifier-05.*
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

<!--- KNIT example-custom-modifier-05.kt -->

Like the typed builders, it is additive: the instance is added when the element enters the modifier and
removed when it leaves or the component is released, reused or deactivated. The add and remove functions
receive the component, typed as the registration names it, and any other component is rejected when the
modifier is applied. Register and unregister there, and change nothing the composition declares for it
(see [What the composition owns](CUSTOM-COMPONENTS.md#what-the-composition-owns)).

## Domain callbacks stay component parameters

Per-component semantic callbacks - `onClick` on a button, `onValueChange` on a slider - are parameters
of your composable function, not modifiers. A callback your component calls from `update` needs one
extra guard: see [A callback your component calls itself](COMPONENT-STATE.md#a-callback-your-component-calls-itself).
