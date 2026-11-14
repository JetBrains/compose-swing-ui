# Module swing-ui-detekt

This library's own detekt rules: the contracts its API carries that neither the compiler nor the
formatter can hold.

Adding this artifact also brings the Compose rules ([mrmans0n/compose-rules]) with it, so a consumer
adds one line rather than two.

| Rule | Reports |
| --- | --- |
| `SwingModifierWithoutDefault` | a composable a caller outside the module can reach whose chain parameter has no default, or defaults to something other than the empty chain |
| `SwingStyleWithoutDefault` | a composable a caller outside the module can reach whose `SwingStyle` parameter has no default, or defaults to something other than `SwingStyle`, the empty style |
| `ComposableSwingModifierFactory` | a chain factory marked `@Composable`, which ties the value it builds to the composition that called it |
| `SwingModifierFactoryReturnType` | a factory answering with an element type instead of `SwingModifier` |
| `ModifierNodeInspectableProperties` | a modifier element that overrides neither `name` nor `declaredValues`, so it describes itself to a tool as a bare class name |
| `SwingModifierFactoryExtensionFunction` | a factory that is not an extension on `SwingModifier` |
| `SwingModifierUnreferencedReceiver` | a factory that builds a fresh chain and never reaches the one it was given |
| `SwingModifierThen` | `then` handed a factory that takes its receiver implicitly, chaining it twice |
| `SwingComponentInComposableSignature` | a composable whose signature - a parameter, receiver, context parameter, return type or type-parameter bound, function types and type arguments included - names `java.awt.Component` or a subclass, handing a raw Swing component across the Compose API |
| `MultipleSwingModifierParameters` | a composable or a `@Composable` function type with more than one `SwingModifier` parameter, where a single chain has no way to say which part of the content each one styles |
| `MultipleSwingNodeCalls` | a composable that calls `SwingNode` or `MenuNode` more than once, building its UI from raw Swing components instead of from composables that each wrap one |
| `SwingNodeFactoryReadsDeclaredValue` | a `SwingNode` or `MenuNode` factory lambda that reads a parameter of its composable, or of a lambda around the node, which the node takes only at construction, unless `update` applies it or a `key` call around the node names it; a local and a function-typed parameter of the composable that the factory invokes are not reported |
| `SwingNodeFactoryReusesComponent` | a `SwingNode` or `MenuNode` factory lambda that answers with a name, `this` or property access declared outside it, so every node it builds shares one component |
| `SwingNodeUpdateConditionalCall` | a `set`, `update` or `declare` call in the `update` lambda of `SwingNode` or `MenuNode` that a recomposition can skip: one that is not a direct statement of `update`, or one after an early return |
| `SwingNodeUpdateRepeatsInvalidation` | a `set` or `update` block of `SwingNode` or `MenuNode` that calls `revalidate`, `invalidate` or `repaint` beside writes whose JDK setters already request it |
| `SwingNodeUpdateStoresCallback` | a `set` or `update` block of `SwingNode` or `MenuNode` that stores a callback in a property of a component which registers a listener on itself while it is built, and the listener reads the stored function |
| `SwingNodeUpdateWritesComponentPart` | a `SwingNode` or `MenuNode` whose `update` writes a part of a composite component that source code assembles |
| `UnheldColumnComparator` | a table column given a comparator built where the column is declared, which sorts the rows again on every pass |
| `UnusedTransitionTargetState` | the state-to-value lambda of `animateValue`, `animateFloat`, `animateInt` or `animateColor` that never reads the state it is handed, so every state animates toward one value |
| `UnusedAnimatedContentTargetState` | an `AnimatedContent` or `Crossfade` content or content-key lambda that never reads the state it is handed, so the two states share content |

Run the following rules with the type-resolving Detekt tasks `detektMain`, `detektTest`, or `detektTestFixtures`.

- `ModifierNodeInspectableProperties`
- `SwingComponentInComposableSignature`
- `MultipleSwingModifierParameters`
- `MultipleSwingNodeCalls`
- `SwingNodeFactoryReadsDeclaredValue`
- `SwingNodeFactoryReusesComponent`
- `SwingNodeUpdateConditionalCall`
- `SwingNodeUpdateRepeatsInvalidation`
- `SwingNodeUpdateStoresCallback`
- `SwingNodeUpdateWritesComponentPart`

`SwingComponentInComposableSignature` exempts a declaration marked `SwingComponentLibraryApi` and the members of a
class marked with it. Every rule in the bundled config except `ModifierNodeInspectableProperties` runs on test sources.

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
