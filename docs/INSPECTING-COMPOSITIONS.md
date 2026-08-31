# Inspecting a mounted composition

This library can answer what a composition holds for a component and where it declared it, and can tell
you as compositions come and go.

## Turning inspection on

```kotlin
isDebugInspectorInfoEnabled = true
```

<!--- CLEAR -->

`org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled` is `false` by default, and off is
where an application leaves it. Turning it on is all this library asks: no handle has to be kept.

Set it on the Event Dispatch Thread - it publishes on Swing components and drives recomposition, both
of which belong to that thread.

Turning it on does two things:

- Every composition this library mounts from then on records where each component was declared, as it
  composes, and publishes itself on the container its content is rooted at.
- Every composition already mounted **re-inserts its content**, so what is already on screen records it
  too. The runtime records source information only as content is inserted, so the content has to be built
  afresh for that information to exist at all. That re-insertion is delivered on a later recomposition
  pass, not by the time the assignment returns.

Re-insertion is not free of consequence. State hoisted above the mounted content survives it - a text
field keeps what the user typed, because that value lives in the state the content reads - while
everything `remember`ed inside the content is discarded and computed again, and the components are
rebuilt.

Turning it off reverses both: every composition builds its content afresh once more, this time without
recording, and withdraws what it published. A composition answers for what it declared only while the
switch is on.

The switch is read by each composition when its own pass runs, not when the assignment returns. Turning
it on and off again within one turn of the event loop therefore leaves everything as it was.

Content mounted under a context a caller captured with `rememberCompositionContext()` is reached on the
next pass it takes for any reason, rather than on one the switch brings about. Content a window drives is
reached at once.

## Asking a component about its composition

```kotlin
val group = someButton.findDeclaringGroup()
```

<!--- CLEAR -->

`Component.findDeclaringGroup()` answers with the group that declared this component, or `null` where no
composition did - a component built by hand, and one whose composition has not recorded yet.

The separate question of which composition a component hosts or stands in is
`Component.findCompositionData()`, which answers whether or not that composition declared it. The walk
starts at the component, so a host is answered with the composition it hosts rather than with the one
that declared it.

The declaring answer is a group rather than the whole composition because a `CompositionGroup` is a
`CompositionData` too, so nothing is lost by it:

| What you want | Where to read it |
|---------------|------------------|
| The component | the group's `node`, a `SwingComponentNode` |
| The values it was built from | the group's `data` |
| A stable handle on it across walks | the group's `identity`, an anchor `CompositionData.find` gives the group back for |
| What that component declared in turn | descend `compositionGroups` from the group |

The search starts at the component and walks up its Swing ancestors, and the nearest composition that
declared it wins. So a component declared inside a nested `setContent` is answered by that content
composition rather than by the one around it, and a component that hosts a composition of its own is
still answered with the group that declared it - the composition it carries never declared its own host.

A composition rooted at a `java.awt.Container` that is no `JComponent` publishes nothing, having no
client-property bag, and the components it declared are not found; where two compositions are rooted at
the same component, only the more recently mounted one is reachable. Call it on the Event Dispatch
Thread: a slot table read off the thread that recomposes it races with recomposition.

The answer reads the slot table as it stands, and the composition's next recomposition retires it -
descending a group held across one fails. Read what you need while the answer is fresh, then ask again;
`identity`, read while it is fresh, is what carries across.

The component a group hands over is the live one, handed over for inspection: the composition still owns
every property a declared parameter governs, so writing one from a tool is replaced on the next pass that
applies it. Read the tree; do not drive it.

## Reading the chain a component carries

A group's node is a `SwingComponentNode`, and `modifier` on it is the chain the composition last
declared for the component:

```kotlin
val node = someButton.findDeclaringGroup()?.node as? SwingComponentNode
val chain = node?.modifier?.foldIn(emptyList<String>()) { named, element -> named + element.name }
```

<!--- CLEAR -->

Walk it with `SwingModifier.foldIn`, and read two things off each element:

| What you want | Where to read it |
|---------------|------------------|
| What the element is called | `name`, such as `background` or `mouseListener` |
| What it declares | `declaredValues`, the values under the names they are declared by |

Both are display only. `key` is what tells one slot from another, so two elements reporting the same
name still occupy their own slots.

The chain is the whole declaration, placement included: an element saying where the component sits in
its parent stands in it beside the ones saying what it looks like. It is what the composition declared
last, whether or not that pass had anything to write, so it never lags the composition - and it holds
what a caller passed followed by what the widget's own composable declared, in that order.

Reaching the node is a slot table read and needs the switch above, but the chain itself is held on the
node rather than in the table, so no re-insertion has to have happened for it to be complete.

An element the library does not ship reports whatever it overrides `name` and `declaredValues` with;
see [`CUSTOM-MODIFIERS.md`](CUSTOM-MODIFIERS.md#naming-the-element-for-a-message-and-for-a-tool).

## Attaching a composition stack trace to a throwable

```kotlin
val failure = IllegalStateException("painting this component failed")
someButton.attachComposeStackTrace(failure)
```

<!--- CLEAR -->

Where a component was declared is not read off its group: the group holding a component is a node group,
and the Compose compiler writes source information onto the groups enclosing it.
`Component.attachComposeStackTrace(throwable)` attaches it as a suppressed exception on the throwable,
rendered with the rest of the trace:

```
java.lang.IllegalStateException: painting this component failed
    at ...
    Suppressed: androidx.compose.runtime.tooling.DiagnosticComposeException: Composition stack when thrown:
    at SwingNode(SwingNode.kt:58)
    at Button(Button.kt:44)
    at OrderForm(OrderForm.kt:31)
```

It returns whether it attached one. Attaching needs the application to have called
`Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)`; the library never sets
it, because it is process-wide state the application owns. `Component.findDeclaringGroup()` answers in
full without it.

`false` means there was nothing to attach: the application has not enabled that mode, no composition this
library mounted declared the component, the composition has not recorded source information yet - the
re-insertion the switch asks for lands on a later pass - the throwable already carries a composition
stack trace, or the composition recorded no source information at all.

The rendered text is the whole of the payload. The exception carrying it is a Compose runtime internal.

## What it costs

Before the switch is ever turned on, a mounted composition costs one read of it per pass, and nothing per
component. Nothing is published on any component, nothing is recorded, and no composition's slot table is
held alive by inspection.

Once the switch has been turned on, recording source and parameter information costs composition time and
memory in every composition in the process, and turning it on rebuilds the content of everything already
mounted. It is a tool's switch, not an application's.

## Being told when compositions start and end

`findDeclaringGroup` answers a question you ask. The other direction - being told as compositions come
and go - is the Compose runtime's own `Recomposer.observe(CompositionRegistrationObserver)`, which is
`@ExperimentalComposeRuntimeApi`. What you need is the right recomposer.

`Component.findRecomposer()` reads it, from the component itself or from anywhere below it. It reads
what is already there and starts nothing, so it may be asked of any component without changing it.

- Content mounted by `setContent` on a window, or on any container inside it, is driven by that
  window's recomposer, and every component below the window answers with it.
- Content mounted under a recomposer its caller created and passed as the `parent` of a `setContent`
  answers with that recomposer, whether or not the container hangs under a window.
- A window, dialog or tray declared inside `application { }` joins the **application's** composition
  rather than driving one of its own, and `ApplicationScope.recomposer` is the one to register on.
  `findRecomposer()` answers `null` there, as it does for a component no composed content reaches.

Registering on a recomposer reports the content compositions nested inside its own as well.

## A worked example

Turning inspection on, naming the composable that declared a component, and watching a window's
compositions come and go:

<!--- INCLUDE .*inspecting-01.*
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import org.jetbrains.compose.swing.core.findRecomposer
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.tooling.attachComposeStackTrace
import org.jetbrains.compose.swing.tooling.findDeclaringGroup
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import java.awt.Component
import java.awt.Window
import java.io.PrintWriter
import java.io.StringWriter
-->

```kotlin
/** Records where every composition declares its components, for the rest of the process. */
fun startInspecting() {
    Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)
    isDebugInspectorInfoEnabled = true
}

/** The values [component] was built from, or `null` if no composition declared it. */
fun argumentsOf(component: Component): List<Any?>? =
    component.findDeclaringGroup()?.data?.toList()

/** What [component]'s modifier chain declares, by the name of each element that declares it. */
fun chainOf(component: Component): Map<String, Map<String, Any?>> {
    val node = component.findDeclaringGroup()?.node as? SwingComponentNode ?: return emptyMap()
    return node.modifier.foldIn(emptyMap()) { declared, element ->
        declared + (element.name to element.declaredValues)
    }
}

/**
 * Where [component] was declared, rendered as the Compose runtime writes it, or `null` when no composition
 * declared it or the runtime is in no diagnostic stack trace mode.
 */
fun declarationOf(component: Component): String? {
    val carrier = Throwable()
    if (!component.attachComposeStackTrace(carrier)) return null
    val rendered = StringWriter()
    carrier.suppressedExceptions.single().printStackTrace(PrintWriter(rendered))
    return rendered.toString()
}

/**
 * Reports every composition mounted under [window] while the returned handle is not disposed, or `null`
 * where the window drives no composition of its own.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
fun watchCompositionsOf(
    window: Window,
    report: (String) -> Unit,
): CompositionObserverHandle? =
    window.findRecomposer()?.observe(
        object : CompositionRegistrationObserver {
            override fun onCompositionRegistered(composition: ObservableComposition) {
                report("started $composition")
            }

            override fun onCompositionUnregistered(composition: ObservableComposition) {
                report("disposed $composition")
            }
        },
    )
```

<!--- KNIT example-inspecting-01.kt -->
