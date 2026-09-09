# Module swing-ui-detekt

This library's own detekt rules: the contracts its API carries that neither the compiler nor the
formatter can hold.

Adding this artifact also brings the Compose rules ([mrmans0n/compose-rules]) with it, so a consumer
adds one line rather than two.

| Rule | Reports |
| --- | --- |
| `SwingModifierWithoutDefault` | a composable a caller outside the module can reach whose chain parameter has no default, or defaults to something other than the empty chain |
| `ComposableSwingModifierFactory` | a chain factory marked `@Composable`, which ties the value it builds to the composition that called it |
| `SwingModifierFactoryReturnType` | a factory answering with an element type instead of `SwingModifier` |
| `ModifierNodeInspectableProperties` | a modifier element that overrides neither `name` nor `declaredValues`, so it describes itself to a tool as a bare class name |
| `SwingModifierFactoryExtensionFunction` | a factory that is not an extension on `SwingModifier` |
| `SwingModifierUnreferencedReceiver` | a factory that builds a fresh chain and never reaches the one it was given |
| `SwingModifierThen` | `then` handed a factory that takes its receiver implicitly, chaining it twice |
| `UnheldColumnComparator` | a table column given a comparator built where the column is declared, which sorts the rows again on every pass |
| `UnusedTransitionTargetState` | the state-to-value lambda of `animateValue`, `animateFloat`, `animateInt` or `animateColor` that never reads the state it is handed, so every state animates toward one value |
| `UnusedAnimatedContentTargetState` | an `AnimatedContent` or `Crossfade` content or content-key lambda that never reads the state it is handed, so the two states share content |

`ModifierNodeInspectableProperties` is ported from Android Lint's
`ModifierNodeInspectablePropertiesDetector` and redistributed under the Apache License, Version 2.0,
copyright The Android Open Source Project. See [`META-INF/NOTICE`](src/main/resources/META-INF/NOTICE)
for the attribution and what the port changed.

## Usage

```kotlin
plugins {
    kotlin("jvm")
    id("dev.detekt")
}

dependencies {
    implementation("org.jetbrains.compose.swing:swing-ui:<version>")
    detektPlugins("org.jetbrains.compose.swing:swing-ui-detekt:<version>")
}
```

The rules above are on as soon as the artifact is there. The Compose rules arrive with it, but detekt
reads every plugin's bundled configuration as one document, so a section two plugins both declare
would be a duplicate key - which means the Compose rules cannot be pointed at this library's types
from here. Copy this into your own `detekt.yml`:

```yaml
Compose:
  ModifierMissing:
    customModifiers: ['SwingModifier']
    contentEmitters: ['SwingNode', 'MenuNode']
  ModifierNaming:
    customModifiers: ['SwingModifier']
  ModifierNotUsedAtRoot:
    customModifiers: ['SwingModifier']
    contentEmitters: ['SwingNode', 'MenuNode']
  ModifierReused:
    customModifiers: ['SwingModifier']
    contentEmitters: ['SwingNode', 'MenuNode']
  ContentEmitterReturningValues:
    contentEmitters: ['SwingNode', 'MenuNode']
  MultipleEmitters:
    contentEmitters: ['SwingNode', 'MenuNode']
  ConditionHoist:
    contentEmitters: ['SwingNode', 'MenuNode']
  LambdaParameterEventTrailing:
    contentEmitters: ['SwingNode', 'MenuNode']
  ModifierClickableOrder:
    active: false
  ModifierComposed:
    active: false
  ModifierWithoutDefault:
    active: false
```

`SwingModifier` is the chain those rules are about, and every widget this library declares bottoms out
in `SwingNode` or `MenuNode`, so a call reaching either is what emits content. The last three read
androidx types that have no counterpart here: `Modifier.clickable`, `Modifier.composed`, and a
without-default check with no visibility threshold, which `SwingModifierWithoutDefault` replaces.

## Related

- [`../swing-ui/README.md`](../swing-ui/README.md) - the core library.
- [`../docs/CUSTOM-COMPONENTS.md`](../docs/CUSTOM-COMPONENTS.md) - writing a component the rules apply to.

[mrmans0n/compose-rules]: https://mrmans0n.github.io/compose-rules/
