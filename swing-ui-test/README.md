# Module swing-ui-test

The test harness for Compose Swing UI. It runs compositions off-screen and deterministically — the
harness root is never shown, and nothing sleeps — so component behavior can be asserted in plain
`@Test` methods. Content that composes `Window { }` / `Dialog { }` realizes real top-level peers, and
the harness finds and asserts on those too.

## Usage

Add the module as `testImplementation`, then write tests with `runComposeSwingTest`:

```kotlin
@Test
fun saveButtonIsEnabled() = runComposeSwingTest {
    setContent { Button("Save", onClick = {}) }
    onNodeWithText("Save").assertIsEnabled()
}
```

See [`docs/TESTING-COMPONENTS.md`](../docs/TESTING-COMPONENTS.md) for the guide to finders, matchers, assertions, actions, and screenshot comparison. The API is documented in KDoc.

## Related

- [`docs/TESTING-COMPONENTS.md`](../docs/TESTING-COMPONENTS.md) - how to write component tests.
- [`swing-ui/README.md`](../swing-ui/README.md) - the core library.
