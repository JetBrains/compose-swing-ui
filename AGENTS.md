# AI Agent Instructions

Compose Swing UI — a Compose runtime over Swing. `@Composable` wrappers, modifiers and state holders
drive real `javax.swing` components, published as the `swing-ui`, `swing-ui-test` and
`swing-ui-animation` modules. The public API is not frozen: design each API the way it should be
rather than preserving an earlier shape.

## Quality rules

- **Quality first.** Fix the code — never relax, disable or exclude a ktlint, detekt, Android Lint,
  ABI or coverage rule to make a build pass.
- **Suppressions are narrow and explained.** A suppression carries a comment stating the invariant
  that makes it safe, not the rule it avoids.
- **No deprecated APIs** unless explicitly requested. Prefer the standard library or existing library
  functionality over a custom implementation.
- **Verify every change.** Add or update tests for changed behavior, then run the gates.
- **Do not commit until the change has been reviewed.**

The code style itself — explicit API mode, KDoc, the EDT rule — is `CONTRIBUTING.md`'s to state; read
it there rather than here, so the two files never drift apart on the same rule.

## Skills

Task procedures live in `.agents/skills/`, each in its own directory as a `SKILL.md`. They are
surfaced to each tool through the `.claude/skills`, `.codex/skills` and `.junie/skills` symlinks.

## Reference

- `CONTRIBUTING.md` — prerequisites, the build, the quality gates and the code style.
- `docs/ARCHITECTURE.md` — how the runtime works.
- `docs/CUSTOM-COMPONENTS.md` — the worked guide to writing a component.
- `docs/TESTING-COMPONENTS.md` — the test harness.
