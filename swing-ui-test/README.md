# swing-ui-test

The test harness for Compose Swing UI. It runs compositions off-screen and deterministically — the
harness root is never shown, and nothing sleeps — so component behavior can be asserted in plain
`@Test` methods. Content that composes `Window { }` / `Dialog { }` realizes real top-level peers,
and the harness finds and asserts on those too (a display is required; tests declare it with a JUnit
assumption, `Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), …)`).

## Usage

Add the module as `testImplementation`, then write tests with `runComposeSwingTest`:

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

The harness offers finders (`onNodeWithText`, `onNodeWithTag`, `onNodeOfType<T>()`, …), window
finders (`onWindow`, `onWindowWithTitle`, `onAllWindows`), composable matchers (`hasText`,
`isSelected`, `hasAnyAncestor`, combined with `and`/`or`/`!`), relative navigation (`onParent`,
`onChildren`, `onSiblings`, …), assertions (`assertExists`, `assertIsDisplayed`, `assertTextEquals`,
`assert(matcher)`, …), actions (`performClick`, `performTextInput`, …), and golden-image screenshot
comparison. See
[`../docs/TESTING-COMPONENTS.md`](../docs/TESTING-COMPONENTS.md) for the full guide; the API itself
is documented in KDoc.

## Related

- [`../docs/TESTING-COMPONENTS.md`](../docs/TESTING-COMPONENTS.md) — how to write component tests.
- [`../swing-ui/README.md`](../swing-ui/README.md) — the core library.
