# swing-ui-test

The test harness for Compose Swing UI. It runs compositions off-screen and deterministically — the
harness root is never shown, and nothing sleeps — so component behavior can be asserted in plain
`@Test` methods. Content that composes `Window { }` / `Dialog { }` realizes real top-level peers, and
the harness finds and asserts on those too.

## Usage

Add the module as `testImplementation`, then write tests with `runComposeSwingTest` — a plain `@Test`
method whose body is that block, calling `setContent { … }` to mount the composable under test.

See [`../docs/TESTING-COMPONENTS.md`](../docs/TESTING-COMPONENTS.md) for the full guide to finders,
matchers, assertions, actions, and screenshot comparison; the API itself is documented in KDoc.

## Related

- [`../docs/TESTING-COMPONENTS.md`](../docs/TESTING-COMPONENTS.md) — how to write component tests.
- [`../swing-ui/README.md`](../swing-ui/README.md) — the core library.
