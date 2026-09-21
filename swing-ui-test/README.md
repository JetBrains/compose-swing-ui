# Module swing-ui-test

The test harness for Compose Swing UI. It runs compositions off-screen and deterministically, so component behavior can be asserted in plain `@Test` methods. Content that composes `Window { }` / `Dialog { }` realizes real top-level peers, and the harness finds and asserts on those with `onWindow`. Realizing them requires a display, so declare that with a JUnit assumption and headless runs skip the test.

## Usage

Add the module as `testImplementation`, then write tests with `runComposeSwingTest`:

```kotlin
@Test
fun saveButtonIsEnabled() = runComposeSwingTest {
    setContent { Button("Save", onClick = {}) }
    onNodeWithText("Save").assertIsEnabled()
}
```

To wait for state or effects, call `awaitIdle` or `waitUntil`. See [Testing components](../docs/TESTING-COMPONENTS.md) for the guide. The API is documented in KDoc.

## Related

- [Testing components](../docs/TESTING-COMPONENTS.md) - how to write component tests.
- [`swing-ui-core`](../swing-ui-core/README.md) - the core library.
