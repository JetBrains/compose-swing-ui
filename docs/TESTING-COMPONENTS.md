# Testing components with the swing-ui-test harness

The `:swing-ui-test` harness runs a composition off-screen and deterministically, then lets you find
components — and the top-level windows the composition realizes — assert their state, and drive
interactions through them. This guide shows how to write behavioral tests — tests that exercise
state → recomposition → visible change through the public API.

<!--- INCLUDE .*fragment.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.test.*
import org.jetbrains.compose.swing.test.interaction.*
import javax.swing.*

fun example() = runComposeSwingTest {
----- SUFFIX .*fragment.*
}
----- INCLUDE .*case.*
import androidx.compose.runtime.*
import org.jetbrains.compose.swing.components.*
import org.jetbrains.compose.swing.components.button.*
import org.jetbrains.compose.swing.test.*
import kotlin.test.*
-->

## Setup

Add the harness to the dependencies of the module under test:

```kotlin
dependencies {
    /* ... */
    testImplementation(project(":swing-ui-test"))
}
```

<!--- CLEAR -->

Then write a plain `@Test` method whose body is a `runComposeSwingTest { … }` block. Inside the block you
call `setContent { … }` to mount your composable, and the harness, finders, assertions, and actions
are all in scope.

```kotlin
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.components.button.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.test.Test

class CounterTest {
    @Test
    fun clickingIncrements() = runComposeSwingTest {
        var clicks by mutableStateOf(0)
        setContent {
            Button(text = "Clicks: $clicks", onClick = { clicks++ })
        }
        onNodeWithText("Clicks: 0").performClick()
        onNodeWithText("Clicks: 1").assertExists()
    }
}
```

<!--- KNIT example-testing-01.kt -->

`setContent` waits for the composition to settle before returning, so by the next line the tree
reflects the initial state. After an action that writes Compose state, the harness settles again
before the following assertion runs — no sleeps, no manual pumping.

## Finding components

Single-node finders return a `SwingNodeInteraction`:

- `onNodeWithText(text)` — match a component by its displayed text.
- `onNodeWithTag(tag)` — match by test tag (see *Test tags* below).
- `onNodeWithName(name)` — match by component name.
- `onNodeOfType<T>()` — match the single component of a given Swing type.
- `onRoot()` — the composition root.
- `onNode(matcher)` — match with a `SwingMatcher` (e.g. `hasText`, `hasTestTag`, `hasName`,
  `isEnabled`, `isSelected`, `isEditable`, composed with `and`, `or` and `!`).

Multi-node finders return a `SwingNodeInteractionCollection`:

- `onAllNodesWithText(text)`, `onAllNodesWithTag(tag)`, `onAllNodesOfType<T>()`, `onAllNodes(matcher)`.

Narrow a collection with `filter(matcher)` or `filterToOne(matcher)`, assert its size with
`assertCountEquals(n)`, assert over its members with `assertAll(matcher)` / `assertAny(matcher)`, and
target one match with `[index]`, `onFirst()`, or `onLast()` — each returns a handle that re-resolves
against the live tree on every use:

```kotlin
onAllNodesWithText("row")[1].assertIsEnabled()
onAllNodesWithTag("item").onLast().assertTextEquals("newest")
onAllNodesOfType<JCheckBox>().assertAll(SwingMatcher.isEnabled())
```

<!--- KNIT example-testing-fragment-01.kt -->

### Structure

Where a component sits in the tree is expressed by matchers — `hasParent`, `hasAnyChild`,
`hasAnySibling`, `hasAnyAncestor`, `hasAnyDescendant`, each taking a `SwingMatcher` — so a query is
scoped to a subtree by describing it rather than by holding a component:

```kotlin
onAllNodesOfType<JLabel>().filter(SwingMatcher.hasAnyAncestor(SwingMatcher.hasTestTag("editor")))
```

<!--- KNIT example-testing-fragment-02.kt -->

From an interaction you can also step to the nodes around it with `onParent()`, `onChild()`,
`onChildren()`, `onChildAt(index)`, `onSibling()`, `onSiblings()`, `onAncestors()` and
`onDescendants()`. A step is as lazy as the query it extends, and `onAncestors()` stops at the root
the query searches:

```kotlin
onNodeWithTag("editor").onDescendants().filter(SwingMatcher.isOfType<JLabel>()).assertCountEquals(2)
onNodeWithText("Save").onParent().assert(SwingMatcher.isEnabled())
```

<!--- KNIT example-testing-fragment-03.kt -->

### Test tags

A test tag is a stable handle that survives label and layout changes — prefer it over matching on
displayed text when the text is dynamic. Attach one with the `testTag` modifier:

```kotlin
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag

TextField(value = name, onValueChange = { name = it }, modifier = SwingModifier.testTag("name-field"))
```

<!--- CLEAR -->

```kotlin
onNodeWithTag("name-field").performTextInput("Ada")
```

<!--- KNIT example-testing-fragment-04.kt -->

## Asserting state

Assertions are available on a `SwingNodeInteraction`; each returns the interaction so they chain,
except `assertDoesNotExist()`, which ends the chain:

- `assertExists()` / `assertDoesNotExist()`
- `assertIsDisplayed()` — assert the layout gave the component real bounds.
- `assertIsVisible()` / `assertIsNotVisible()` — assert the component is shown, i.e. neither it nor an
  ancestor up to the query's root is hidden.
- `assertTextEquals(text)`
- `assertIsEnabled()` / `assertIsNotEnabled()`
- `assertLayoutConstraint(expected)` — assert the placement the parent's layout manager holds the
  child under: a `BorderLayout` region, or a `GridBagConstraints` (compared field by field). Any other
  manager is named in the failure, including a `CardLayout` — a deck reports nothing per card, and
  what matters about it (the declared card is the one on show) is asserted with `assertIsVisible()` /
  `assertIsNotVisible()`.
- `assertIsFocusOwner()` / `assertIsNotFocusOwner()` — assert which component holds focus.
- `assert(matcher)` — assert any `SwingMatcher`, including a composed or structural one.

```kotlin
onNodeWithTag("submit")
    .assertIsDisplayed()
    .assertIsEnabled()
    .assertTextEquals("Submit")
```

<!--- KNIT example-testing-fragment-05.kt -->

A check that is not one of the named assertions is still asserted through the node: `assert(matcher)`
takes any `SwingMatcher`, so the failure message keeps the finder's context:

```kotlin
onNodeOfType<JCheckBox>().assert(SwingMatcher.isSelected())
```

<!--- KNIT example-testing-fragment-06.kt -->

`fetch<T>()` hands back the live component itself. Its purpose is the comparison only the component
can answer: that the widget settled on the value the composition declared, or that the same instance
survived a recomposition:

```kotlin
import javax.swing.JList

val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
assertEquals(2, list.selectedIndex, "the declared selection is the one the widget holds")
```

<!--- CLEAR -->

Prefer an assertion or a matcher wherever one covers the property, and reach for `fetch<T>()` where
the Swing side of the contract is itself the subject.

### Menus

A menu is not part of the component tree its invoker lives in, so there is no node to find for it.
Reach it through the component that holds it — a window's `jMenuBar`, a component's
`componentPopupMenu` — with the typed `fetch<T>()`, then read its content through `JMenu`'s own
`itemCount` and `getItem(index)`, which reports a separator as `null`:

```kotlin
import javax.swing.JFrame

val fileMenu = onWindowWithTitle("Editor").fetch<JFrame>().jMenuBar.getMenu(0)
val items = (0 until fileMenu.itemCount).map { fileMenu.getItem(it)?.text }
assertEquals(listOf("New", null, "Open"), items)
```

<!--- CLEAR -->

Only the menu's own level is read this way: a submenu appears as its own item, named by its `text`,
and what it drops down is read the same way, off the `JMenu` that item is.

## Driving interactions

Actions are available on a `SwingNodeInteraction`:

- `performClick()` — click the component.
- `performTextInput(text)` — append text to a text component.
- `performTextReplacement(text)` — replace a text component's contents.
- `performFocusGained()` / `performFocusLost()` — deliver a focus notification to the component.
- `performTabClick(index)` — click a tab of a tabbed pane.

```kotlin
onNodeWithTag("amount").performTextReplacement("42")
onNodeWithText("Save").performClick()
```

<!--- KNIT example-testing-fragment-07.kt -->

### Tabs

A tabbed pane's strip is drawn by the look and feel rather than built from child components, so there
is no node to find for a tab. `performTabClick(index)` aims a real click at it instead: writing the
pane's selected index directly would be the composition's own write, not a user's, and a wrapper that
tells the two apart could not be tested that way. A tab the strip does not currently show has no
position to click; the action says so rather than landing on nothing.

```kotlin
onNodeOfType<JTabbedPane>().performTabClick(2)
```

<!--- KNIT example-testing-fragment-08.kt -->

### Focus

A focus notification and focus ownership are two different things off-screen. `performFocusGained()`
and `performFocusLost()` deliver a notification to the component without a display. `assertIsFocusOwner()`
needs real ownership, which only a realized, focused window can grant — never the harness root.

Whether a window becomes focused is the window system's decision, not the test's. A process the window
system declines to activate still shows and lays out its windows; none of them ever becomes focused.
Wait for the window to report itself focused, and skip the test with a JUnit assumption when it never
does, so such an environment reports SKIPPED instead of failing. Hold the same assumption over every
later wait, too: focus can be taken away again once granted, and a window that is no longer the focused
one is the environment withdrawing what ownership needs. A focused window whose keyboard went to a
component the test did not expect is a different case — a real failure, asserted plainly.

```kotlin
onNodeWithTag("amount").performFocusLost()
onNodeWithTag("amount").assertTextEquals("42.00")
```

<!--- KNIT example-testing-fragment-09.kt -->

## Callback failures

A callback that throws while a wrapper is writing to its widget - settling a value the widget clamped to
its own grid or range, for instance - is contained instead of failing the test on the spot. The write
finishes and the composition keeps working, so a test may go on writing state, recomposing and asserting
after the throw. Left untaken, the failure still fails the test at teardown, naming the callback.

A test that provokes one on purpose takes it with `takeCallerFailures()` and asserts on it directly; what
it takes no longer fails the test:

```kotlin
@Test
fun aThrowingCallbackIsContainedAndReported() = runComposeSwingTest {
    var max by mutableIntStateOf(100)
    setContent {
        Slider(value = 50, onValueChange = { error("boom") }, max = max)
    }

    // Narrowing the range below the declared value forces the slider to clamp it on the spot, which is
    // the wrapper writing its own settled value back - exactly where a callback failure is contained.
    max = 30
    awaitIdle()

    val failures = takeCallerFailures()
    assertEquals(1, failures.size)
    assertEquals("boom", failures.single().message)
}
```

<!--- KNIT example-testing-case-01.kt -->

## Testing windows and dialogs

Content that composes `Window { }` or `Dialog { }` realizes a real top-level peer, which needs a
display: start such a test with a JUnit assumption,
`Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), …)`, so it reports SKIPPED where there is
no display and runs everywhere else.

Window finders resolve against every window currently realized in the test JVM, whether or not it
is shown:

- `onWindow()` — the single realized window.
- `onWindowWithTitle(title)` — match by window title.
- `onWindow(matcher)` / `onAllWindows(matcher)` — match with a `SwingMatcher` (e.g. `hasTitle`).

A `SwingWindowInteraction` offers `assertExists()` / `assertDoesNotExist()`, `assertIsVisible()` /
`assertIsNotVisible()`, the typed `fetch<T>()` for the realized `JFrame`/`JDialog`, and the node
finders scoped to that window's content pane:

```kotlin
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment

@Test
fun settingsWindowShowsItsContent() = runComposeSwingTest {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    setContent {
        Window(onCloseRequest = {}, title = "Settings") {
            Button(text = "Apply", onClick = { })
        }
    }
    val window = onWindowWithTitle("Settings")
    window.assertIsVisible()
    window.onNodeWithText("Apply").assertIsEnabled()
}
```

<!--- KNIT example-testing-case-02.kt -->

A dialog show is applied on its own event-dispatch turn; the idle gate drains it, so after a state
change plus `awaitIdle()` the realized dialog already reflects the declared visibility.

## Waiting on external timing

Composition state changes settle automatically, so most tests need no waiting. When a condition
genuinely depends on timing outside the composition (a coroutine driven by wall-clock, an external
callback), use `waitUntil { … }`; use `awaitIdle()` to settle the composition explicitly when you
have written state outside of an action.

### Telling a widget's own report apart from a recomposition

`awaitIdle()` settles the composition, so by the time it returns a widget's callback has fired *and* a
recomposition has applied whatever the callback wrote. When a test has to tell those two apart, use
`awaitEventsDelivered()`: it dispatches the notifications already queued on the event dispatch thread
and produces no frame, so anything the tree shows afterwards was put there by a widget rather than by
the composition. Compose state the delivered callbacks wrote stays pending until the next
`awaitIdle()`.

```kotlin
awaitEventsDelivered()
assertEquals(1, reportedByTheWidget)
onNodeOfType<JLabel>().assertTextEquals("not recomposed yet")
awaitIdle()
onNodeOfType<JLabel>().assertTextEquals("recomposed")
```

### Driving frames by hand

`awaitIdle()` reaches a settled composition by sending it frames, so anything driven by
`withFrameNanos` — an animation, most of all — has already run to completion by the time a test looks
at it. `mainClock` hands that decision to the test:

```kotlin
mainClock.autoAdvance = false
setContent { /* ... */ }

mainClock.advanceTimeBy(150.milliseconds)
awaitIdle()
onNodeOfType<JLabel>().assertTextEquals("halfway")
```

With `autoAdvance` off, `awaitIdle()` — and `waitUntil { … }` with it — drains pending recomposition,
snapshot and event-dispatch work but produces no frame of its own, so it returns on exactly the state
a frame would have moved past.
`advanceTimeByFrame()` sends one frame; `advanceTimeBy(duration)` steps in whole frames until
`currentTime` has advanced by at least `duration`, or delivers the whole span as a single frame with
`ignoreFrameDuration = true`; `advanceTimeUntil { … }` sends frames until a condition holds, failing
once it has advanced past its `timeout` of composition time. `frameDuration` is one frame of a 60Hz
display — the step every frame the harness sends advances composition time by, not the host display's
refresh rate.

The clock governs the test's own off-screen composition. Content composed under a real `Window` or
`Dialog` recomposes on that window's recomposer, paced by its display, and is unaffected.

### What an unrealized tree does not do

The harness root is never attached to a window, so nothing composed in it is ever *displayable*, and
the Swing wiring that happens on `addNotify` does not run. The visible case is a `Table` inside a
`ScrollPane`: `JTable` installs its own header on the enclosing scroll pane from `addNotify`, so
off-screen there is no `columnHeader` view to find and no header to click. The table's sorting,
selection and column model all behave normally — only the header component is absent. A test that
needs it should compose the content under a `Window { }`, which realizes a real peer.

## Screenshot comparison

The harness can capture a component (or the whole root) to an image and compare it against a stored
golden by structural similarity.

```kotlin
import org.jetbrains.compose.swing.test.screenshot.assertImageAgainstGolden

onNodeWithTag("chart").assertImageAgainstGolden("chart-default")
```

`assertImageAgainstGolden(goldenIdentifier)` is available on both a `SwingNodeInteraction` (captures
the matched component) and on the test itself (captures the root). A `threshold` parameter controls
how strict the structural-similarity match is. To compare two captured images without a golden file,
capture with `captureToImage()` and use `assertImageMatches(expected)`.

`captureToImages()` on a `SwingNodeInteractionCollection` captures every match at once, returning one
image per matched component sized to its own bounds, in depth-first pre-order — the same order as the
collection's other accessors:

```kotlin
val images = onAllNodesOfType<JButton>().captureToImages()
```

## Related

- [`../swing-ui-test/README.md`](../swing-ui-test/README.md) — the harness module.
- [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md) — building the components you are testing.
