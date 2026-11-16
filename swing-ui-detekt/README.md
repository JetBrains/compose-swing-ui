# Module swing-ui-detekt

Detekt rules for Compose Swing UI and the Compose runtime. Add `swing-ui-detekt` to `detektPlugins` to load the rules.

[Static analysis](../docs/STATIC-ANALYSIS.md) explains what each public rule reports and shows a better pattern.

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

Use the guide's rule-set IDs as top-level keys in your Detekt YAML. Put each rule ID under its rule set.

Run `detektMain`, `detektTest`, or the type-resolving task for your source set to include rules that need type information.

Set `failOnSeverity` to `Warning` to make warnings fail the build.

## Related

- [`swing-ui`](../swing-ui/README.md) - the core library.
- [Writing components](../docs/CUSTOM-COMPONENTS.md) - designing and writing a component the rules apply to.
