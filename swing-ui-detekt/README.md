# Module swing-ui-detekt

Detekt rules for Compose Swing UI and the Compose runtime. Add `swing-ui-detekt` to `detektPlugins` to load the rules.

| Rule | Reports |
| --- | --- |
| `ArgumentOutsideDeclaredRange` | a value outside its parameter's declared range or a range that holds no values |
| `RestrictedApi` | a reference outside the scope declared by `@RestrictTo` |
| `VisibleForTests` | a production reference outside the visibility set by the `otherwise` argument of `@VisibleForTesting` |
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
| `PreviewNeedsComposableAnnotation` | a Swing preview function missing the Compose Runtime `@Composable` annotation |
| `PreviewSignature` | a Swing preview declared inside another declaration or requiring arguments |

Run the following rules with the type-resolving Detekt tasks `detektMain`, `detektTest`, or `detektTestFixtures`.

- `ModifierNodeInspectableProperties`
- `PreviewNeedsComposableAnnotation`
- `SwingComponentInComposableSignature`
- `MultipleSwingModifierParameters`
- `MultipleSwingNodeCalls`
- `SwingNodeFactoryReadsDeclaredValue`
- `SwingNodeFactoryReusesComponent`
- `SwingNodeUpdateConditionalCall`
- `SwingNodeUpdateRepeatsInvalidation`
- `SwingNodeUpdateStoresCallback`
- `SwingNodeUpdateWritesComponentPart`
- `ArgumentOutsideDeclaredRange`
- `RestrictedApi`
- `VisibleForTests`
- `ComposableNaming`
- `CompositionLocalNaming`
- `MutableStateAutoboxing`
- `RememberMissing`
- `ModifierShouldBeForwardedToEveryBranch`
- `ConditionHoist`
- `InvalidReadOnlyComposable`
- `MissingNonRestartableComposable`
- `MissingReadOnlyComposable`
- `StaleRememberUpdatedStateInRemember`
- `UnnecessaryComposable`
- `UnnecessaryLaunchedEffect`
- `VarsWithoutStateBacking`

The `compose-runtime` rule set also uses type resolution.

`SwingComponentInComposableSignature` exempts a declaration marked `SwingComponentLibraryApi` and the members of a
class marked with it. Every active rule in the bundled config except `ModifierNodeInspectableProperties`, `ArgumentOutsideDeclaredRange` and
`VisibleForTests` runs on test sources.

See [`META-INF/NOTICE`](src/main/resources/META-INF/NOTICE) for source attribution.

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

Use `buildUponDefaultConfig` to apply the bundled defaults alongside your own configuration.

```kotlin
detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(file("config/detekt.yml"))
}
```

Configure the Swing rules under `compose-swing`, the Compose runtime checks under `compose-runtime`, and the repository-internal rules under `compose-swing-internal`.

Detekt fails the build on errors by default. Set `failOnSeverity` to `Warning` to fail on warnings as well.

```kotlin
import dev.detekt.gradle.extensions.FailOnSeverity

detekt {
    failOnSeverity.set(FailOnSeverity.Warning)
}
```

## Related

- [`../swing-ui/README.md`](../swing-ui/README.md) - the core library.
- [`../docs/CUSTOM-COMPONENTS.md`](../docs/CUSTOM-COMPONENTS.md) - writing a component the rules apply to.
