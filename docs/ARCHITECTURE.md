# Architecture

Compose Swing UI is a Compose-runtime binding over Swing. Layout, measurement, and painting stay
with Swing: your composition produces real `java.awt.Component`s, sized and placed by Swing's layout
pass and painted by the look-and-feel. What the library adds is Compose's composition model -
composition and recomposition, snapshot state, effects, and a frame clock - driving a live AWT
component tree on the Event Dispatch Thread (EDT).

This document describes the concepts that shape the binding. The KDoc on the public API is the
reference for individual functions; the source tree is the map of where things live.

---

## Mounting a composition

A composition is mounted onto an existing Swing container with `container.setContent { ... }`.
There are matching entry points for windows and for menu bars, and a high-level `application`
entry point that owns a whole app lifecycle.

Mounting always happens on the EDT, because composition and the AWT mutations it drives must run
on that thread. A mount looks for a composition already hosted above it in the Swing tree, starting
with the container itself, and joins that one, sharing its scope and `CompositionLocal`s. Failing
that, it joins the composition scope belonging to its window. Every mount nests into something, so
two containers given content under one window recompose together, on that window's recomposer and
its frame clock. A container that is in no window yet is mounted the moment it is added to one.

A mount can also be given the composition it nests into: `window.compositionContext()` is a window's
own scope. A scope of its own comes from a runtime created for a component with
`SwingRecomposer.create(component)`, whose `compositionContext` the mount takes as its parent and
whose disposal the caller owns - an integration-level entry point, opted into with
`@OptIn(InternalSwingUiApi::class)`.
`container.setContent(parent) { ... }` then composes on the call, whatever the
container is attached to, which is what serves a container built to be read rather than shown. Such
a container joins the composition of the window it is in should it later be added to one, so a
window's content still recomposes on one recomposer and one frame clock.

Content reads a `LifecycleOwner` through `LocalLifecycleOwner`, shared by everything that content
hosts - popups, menus, overlays. A mount resolves that owner from where its container hangs in the
Swing tree: it takes the one a mount at or above it published there, and only where that walk answers
nothing does it get an owner of its own following its container. A `Window` or `Dialog` composed in it
is a top-level window of its own and always gets one of its own, since being attached, minimized or
focused are facts about a single window. An owner answers for the content it follows, so a mount that
resolved one reports where that content stands rather than where its own container hangs, and
disposing that mount leaves the owner live - only the mount an owner was made for ends it. Attachment
to the Swing tree, minimization of the window, and that window's keyboard focus move an owner between
`CREATED`, `STARTED`, and `RESUMED`; the KDoc on `setContent` is the reference for which is which.

Mounting returns a handle. Disposing it tears that content down.

---

## Driving recomposition

A root composition recomposes on the EDT, so recomposition and the AWT changes that follow it
share the one thread Swing allows for component work. Inside effects and frame callbacks you can
therefore read and write both Compose state and Swing components directly, without hopping threads.

When snapshot state is written - from an effect, a coroutine, a background thread, or a Swing
listener callback - the change is observed at the level of the composition that owns the affected
nodes, and the scopes that read the changed state are scheduled to recompose. Reading and writing
snapshot state (`mutableStateOf`, `derivedStateOf`, `snapshotFlow`, `produceState`, and the rest)
behaves as it does on any Compose target.

---

## Pacing with a frame clock

Each top-level composition is paced by its own frame clock running at a display-like cadence on the
EDT. `withFrameNanos`, the animation APIs built on it, and recomposition are all driven by this
clock. The clock advances only while something is waiting for a frame, so an idle window does no
per-frame work while an animating one advances at a steady rate. Time-based work in separate
windows is independent.

---

## Applying changes to the AWT tree

As a composition changes, the runtime emits structural operations - insert, remove, move, clear -
that are applied to the backing container. Child order in the AWT tree is kept aligned with
composition order so index-based operations always address the intended component.

Swing does not lay out or repaint added, removed, or moved children on its own: a mutated container
needs an explicit layout-and-repaint pass to make the change visible. Every container touched
during a change pass therefore gets one such pass once the pass completes, so a change that touches
a container many times still costs a single layout. The menu tree follows the same model through
its own applier.

---

## Placing children with explicit constraints

Some layouts need to know *where* a child belongs - a `BorderLayout` needs a region (`NORTH`,
`CENTER`, ...), and other constraint-based layouts need their own constraint objects. Composition
order does not express that intent: conditionals, movable content, and reordering all change a
child's index without changing where the author meant to place it. Placement is therefore explicit
and parent-driven rather than inferred from index.

The node carries the constraint its container provided until the applier places the component. The
default, when no container declares one, is "add by position."

A `CompositionLocal` carries the intended constraint from a container down to the children it
composes. `BorderPanel` is the canonical slot-based container. Its regions are declared through a
receiver DSL, each region a single-child slot that provides its constraint to the child it composes.
Declaring a region adds its child in the right place, redeclaring it replaces the child, and dropping
a region removes the child. The same mechanism extends to other constraint-based layouts, and to
hosts whose children are installed through dedicated setters (such as a scroll pane's viewport,
headers, and corners) rather than a generic add.

The provision is public, which is what makes a container over a layout manager the library does not
wrap possible at all: the container supplies the manager, hosts arbitrary content, and wraps each
child it composes in the placement that child is added under. The value is untyped, matching what a
container takes; a manager's author wraps it in a typed DSL for their own callers.

Because the constraint is provided at the position the child is composed at, and not carried by the
child, it describes the parent-child relationship exactly as long as that relationship lasts: content
moved to another container by movable content is placed by the container it arrives in, and the
placement it was composed under before stays behind with the container that declared it.

A constraint that changes while its component is already attached re-registers the component with its
parent's manager rather than re-adding it, so placement follows state without the component losing
its position in the container, its focus, or its native resources.

A custom container that consumes an incoming constraint for its own placement starts its children
from the default baseline, so a nested constraint-based layout is free to provide its own
constraints to its own children.

---

## The node lifecycle and listeners

Each node in a composition wraps a Swing component and carries the per-node state the runtime needs
to place, update, and tear it down. A node is recyclable: when content is conditionally shown and
hidden, or replaced by structurally identical content in the same slot, the runtime can reuse the
existing backing component from a clean baseline instead of allocating a new one.

Reuse is why listener lifecycle matters. A listener that calls back into composition state must be
attached for exactly the node's current lifetime: it is detached when the node is released, reused,
or deactivated, so it never fires into a composition that has moved on. Listeners the host
application attaches directly to a component are never touched. A node installs a single stable
listener that always sees current composition state, rather than re-attaching one on every
recomposition.

Listeners are exposed through the modifier system. A typed lambda for a specific event and a raw
listener share one installation path, so the behavior is the same regardless of which form a caller
uses.

---

## Styling and configuration with modifiers

`SwingModifier` is the Compose-shaped way to configure a component: appearance, layout hints,
keyboard and interaction wiring, data transfer, accessibility, and listeners are expressed as
modifier elements chained onto a component. A passed `modifier` is the base of the chain and a
component's own elements chain onto it, following the Compose convention. Elements that share a slot
are last-wins, so where a component declares a property itself, its own value stands - what a
component means you to decide is a parameter, not a modifier element.

Closed sets of Swing integer (and a few string) constants - scrollbar policies, orientations,
selection modes, and the like - are exposed as typed constant sets. A parameter that takes one of
these accepts exactly the values the wrapped Swing API expects, so an unintended value is flagged
in the IDE while the value passed at runtime is the plain Swing constant, with no translation layer.

Where an element takes over behavior a widget already has, it takes over only what it declares. Data
transfer is the clearest case: a component's export and its import are separate, and the modifiers
that configure them share one transfer handler over the component's own. Declaring `draggable` or
`clipboard` decides what the component exports; declaring `dropTarget` or `clipboard` decides what it
imports, and every drop and paste that reaches it. Whichever direction is left undeclared is left to
the component, so a widget that ships an import of its own - a text component's paste - still performs
it beside a declared export, and the actions a source offers stay the ones it offers on its own beside
a declared import. The last element to leave takes the shared handler with it and puts the original
back.

---

## Effects and snapshot state

Because this is a Compose-runtime binding, the effect and state APIs behave to their usual Compose
contracts, with a few target-specific guarantees worth relying on:

- **Effects run on the EDT.** `LaunchedEffect`, `DisposableEffect`, and `SideEffect` execute on the
  Event Dispatch Thread, so inside them you can touch Swing components and Compose state directly. A
  `DisposableEffect`'s `onDispose` runs when its node leaves the composition or before the effect
  re-runs for changed keys, giving a precise place to set up and tear down resources tied to a piece
  of UI.

- **Snapshot state works out of the box.** State written from a listener callback, a coroutine, or a
  background thread is observed and recomposes the scopes that read it. Prefer the snapshot APIs over
  hand-wiring Swing listeners to state when you want derived or asynchronously produced values to
  stay in sync.

---

## Shapes for state the user can change

Part of what a component holds, the user changes themselves: a scroll position, a selection, the order
of a table's columns, which component holds the keyboard. Three shapes carry that, and what the state
is decides which one a component uses.

- **A declared value with a change callback** is the ordinary shape. The composition holds the value
  and passes it in, and the component reports the user's change back through a callback beside it - a
  text field's `value` and `onValueChange`, a table's `selectedRowIndices` and `onSelectionChange`, its
  `columnLayout` and `onColumnLayoutChange`. The declared value is applied on every recomposition, so
  the state the composition holds is what is on screen. Where declaring is optional the value is
  nullable: leaving it out leaves that aspect to the widget, while the callback still reports what the
  user did with it.

- **A hoistable state holder** - an `XState` type with a matching `rememberXState` factory - carries
  state that is a group of related values rather than one, that includes values the component reports
  and no caller declares, or that has to outlive the content it belongs to. A scroll position comes with
  the viewport metrics that bound it and the largest position worth scrolling to; an internal frame's
  geometry comes with whether it stands on the desktop as its icon; a window's placement comes with its
  extended state. The properties a caller can declare are two-way: assigning to one drives the live
  component, and the user's own action writes the new value back into the holder, where whatever reads
  it recomposes. A value only the component can report is read-only and follows the live component the
  same way. A holder carries one only where the component has something to report that no caller can
  declare, so a holder whose state a caller can declare in full is two-way throughout; where a holder
  does carry one, reading it is the only way to see that value, since no callback reports it. A holder
  is passed in as a parameter, beside or in place of the plain declared value, so a caller hoists one
  only where they want what it observes.

- **An imperative handle** carries an interaction that is an event and leaves no value behind. A
  `FocusRequester` moves the keyboard when the application decides to - a validation failure, a
  toolbar action, a shortcut - and a `ClipboardHandle` copies, cuts or pastes when a menu item asks,
  without the user pressing the shortcut. A request that has happened leaves nothing for a
  later recomposition to re-apply, so instead of a declared value it is a handle the caller holds,
  calls, and binds to a component with a modifier. A handle names the component it drives, so what it
  can do is what that component can do. Where a component already hands out a state holder, its
  gestures ride on that holder instead of on a handle of their own: a `ListState` reveals a row of its
  list and a `TreeState` a node of its tree, a `ScrollState` reveals a region of the content it
  scrolls, and a `FormattedValueState` commits an edit the user typed but never entered.

In short: a value the composition can name is declared, with a callback beside it; a group of values
the component itself keeps and reports is a state holder; an action with no value is a handle, or a
call on the holder that already carries the component's state. For how a component of your own takes
a value in and reports a change out, see
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

---

## A state change, end to end

Consider a button whose label reflects a counter:

```kotlin
var count by remember { mutableStateOf(0) }
Button(text = "Clicks: $count", onClick = { count++ })
```

1. The user clicks. Swing fires the button's listener on the EDT and the current `onClick` runs
   `count++`.
2. Writing the state is observed and the scope that read `count` is scheduled to recompose.
3. On the next frame the invalidated scope re-executes and recomputes the label; the stable listener
   is left in place.
4. The change is applied to the live button, and its container is marked for layout.
5. Once the change pass completes, that container is laid out and repainted once, and the new label
   is on screen.

If a slot had appeared or disappeared instead - a conditional inside `BorderPanel` - the applied
change would be an insert or remove in the correct region, and the layout-and-repaint pass is what
makes the structural change visible.

---

## Why Swing needs an explicit applier

The same Compose runtime drives very different targets, and the differences concentrate in how
changes reach the screen:

| Concern | Swing (this library) | Compose Multiplatform | DOM (Compose HTML) | Terminal (Mosaic) |
| --- | --- | --- | --- | --- |
| Backing tree | `java.awt.Container` widgets | `LayoutNode`s, a tree the toolkit owns outright | live DOM nodes | an in-memory node tree |
| Who lays out | Swing `LayoutManager`s | Compose UI's own measure-and-layout pass | the browser's reflow engine | the target's own layout pass |
| Making changes visible | an explicit layout-and-repaint pass per touched container | the owner is told changes ended, and the next frame draws the scene onto a canvas | mutating the DOM reflows and repaints automatically | the target re-runs layout and renders a frame |
| Placement | explicit constraints, because layout managers need region/constraint information | `Modifier` and layout composables | CSS and element order | the target's own modifier/layout system |
| Threading | the EDT | a frame-driven render loop, on the EDT where the desktop target hosts it | the single-threaded JS event loop | the target's render loop |

Swing reflows neither on mutation, the way the DOM does, nor on its own schedule, the way a target
that owns its render loop does. That is the fact the binding is built around: changes to the tree
are paired with an explicit layout-and-repaint pass, and placement is carried explicitly because the
layout managers that do the work need real constraint information.

Compose Multiplatform is the closest comparison and the sharpest contrast. On the desktop it runs on
the very same thread this library does, and it still needs an applier of its own, because what a
node *is* differs: it owns its node tree, its layout pass and its drawing, so a change becomes
visible by scheduling a frame. Here the widgets, their layout and their painting belong to Swing, so
a change becomes visible by asking Swing to lay out and repaint. Neither owns the other's half.

---

For a step-by-step guide to building your own component on top of `SwingNode`, see
[`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).
