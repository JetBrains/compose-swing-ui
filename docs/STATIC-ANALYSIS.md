# Static analysis

Use the rule ID in a Detekt finding to find the examples and decide how to fix the issue. The headings group rules by rule-set ID for configuration. See the [module README](../swing-ui-detekt/README.md) to add the rules to a build.

The examples that call `SwingNode` or `MenuNode` require opt-in to `SwingComponentLibraryApi`.

## Compose Swing UI core rules

Rule-set ID `compose-swing-core`.

### ComposableSwingModifierFactory

This rule reports `@Composable` factories with an explicit `SwingModifier` return type. Values the factory remembers follow the call site and are shared if the chain reaches several components. A context menu and its component need the same anchor. Remember it in their caller and pass it to a regular modifier builder and the menu. Use a `SwingModifier.ComponentNode` for state private to the attached component. See [Writing a builder](MODIFIERS.md#writing-a-builder).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
-->

```kotlin
@Composable
private fun SwingModifier.statusMenuAnchor(): SwingModifier =
    testTag("status").popupAnchor(rememberPopupAnchor())

@Composable
private fun StatusBanner(onCopyStatus: () -> Unit) {
    val anchor = rememberPopupAnchor()
    // The label binds a different anchor from the one the menu uses.
    Label("Ready", modifier = SwingModifier.statusMenuAnchor())
    ContextMenu(anchor) { MenuItem("Copy status", onClick = onCopyStatus) }
}
```

<!--- KNIT example-static-analysis-01.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.PopupAnchor
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
-->

```kotlin
private fun SwingModifier.statusMenuAnchor(anchor: PopupAnchor): SwingModifier =
    testTag("status").popupAnchor(anchor)

@Composable
private fun StatusBanner(onCopyStatus: () -> Unit) {
    val anchor = rememberPopupAnchor()
    Label("Ready", modifier = SwingModifier.statusMenuAnchor(anchor))
    ContextMenu(anchor) { MenuItem("Copy status", onClick = onCopyStatus) }
}
```

<!--- KNIT example-static-analysis-02.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierComposed

This rule reports a `composed` result in a regular modifier factory. Each call creates an unequal element, which prevents a composable that receives it from skipping. The builder also runs separately for every component it reaches. Chain existing builders directly when they already express the behavior. A custom `ComponentNode` can hold per-component state and read composition locals.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.composed
import java.awt.Color
-->

```kotlin
fun SwingModifier.statusBadge(): SwingModifier = composed {
    // Every badge builds the same decoration and tag.
    background(Color(0xC4E5FF), RoundedCornerShape(6f)).testTag("status-badge")
}
```

<!--- KNIT example-static-analysis-03.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import java.awt.Color
-->

```kotlin
fun SwingModifier.statusBadge(): SwingModifier =
    background(Color(0xC4E5FF), RoundedCornerShape(6f)).testTag("status-badge")
```

<!--- KNIT example-static-analysis-04.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierMissing

By default, this rule reports public `Unit` composables that emit Swing content and take no modifier. A caller needs to be able to place the component, identify it in a test, or attach a listener. Add `modifier: SwingModifier = SwingModifier` and pass it to the root component.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable
fun SyncStatus(message: String) {
    Column {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-05.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun SyncStatus(message: String, modifier: SwingModifier = SwingModifier) {
    // The caller's placement and test tags reach the whole status group.
    Column(modifier) {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-06.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierNaming

Name the only modifier parameter `modifier`. The same name lets callers use a named argument across components. If a signature has several parameters of recognized modifier types, the naming check expects names ending in `Modifier`. [`MultipleSwingModifierParameters`](#multipleswingmodifierparameters) checks how many `SwingModifier` parameters a signature accepts.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun SyncStatus(message: String, statusModifier: SwingModifier = SwingModifier) {
    Column(statusModifier) {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-07.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
// Callers use modifier = ... here and on the library's components.
@Composable
fun SyncStatus(message: String, modifier: SwingModifier = SwingModifier) {
    Column(modifier) {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-08.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierNodeInspectableProperties

A concrete `SwingModifier.InspectableElement` carrying non-callback constructor values must describe itself. By default the inspector shows only the class name, so values such as the message the caller supplied are hidden. Override `declaredValues` with the values a reader can use to diagnose the component. Override `name` if the class name is unclear. Either override satisfies the rule, including an inherited override. The bundled configuration excludes test sources. See [Reading the chain a component carries](INSPECTING-COMPOSITIONS.md#reading-the-chain-a-component-carries).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
internal data class StatusBadge(
    val text: String,
) : SwingModifier.Element,
    SwingModifier.InspectableElement
```

<!--- KNIT example-static-analysis-09.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
internal data class StatusBadge(
    val text: String,
) : SwingModifier.Element,
    SwingModifier.InspectableElement {
    // Show the badge text alongside the element in the inspector.
    override val declaredValues: Map<String, Any?> get() = mapOf("text" to text)
}
```

<!--- KNIT example-static-analysis-10.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierNotUsedAtRoot

This rule reports a caller modifier applied inside another component. Parent layout data belongs on the direct child. Putting it on a label inside a panel can leave the panel in the wrong place. Pass the caller's chain to the root component and append any modifiers the wrapper adds.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun SyncStatus(message: String, modifier: SwingModifier = SwingModifier) {
    Column {
        Label("Sync status")
        // The column's parent cannot see layout constraints on this label.
        Label(message, modifier = modifier)
    }
}
```

<!--- KNIT example-static-analysis-11.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun SyncStatus(message: String, modifier: SwingModifier = SwingModifier) {
    Column(modifier) {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-12.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierReused

The rule reports the same modifier parameter applied more than once along one execution path. A listener in that chain can be installed on both the root and a child. Appending a child-specific modifier still carries the caller's chain. Pass it once to the root and start a fresh chain for any child-specific behavior.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
-->

```kotlin
@Composable
fun StatusBanner(text: String, onDismiss: () -> Unit, modifier: SwingModifier = SwingModifier) {
    Panel(modifier = modifier) {
        // A listener in the caller's chain can attach to both components.
        Label(text, modifier = modifier.testTag("status-message"))
        Button("Dismiss", onClick = onDismiss)
    }
}
```

<!--- KNIT example-static-analysis-13.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
-->

```kotlin
@Composable
fun StatusBanner(text: String, onDismiss: () -> Unit, modifier: SwingModifier = SwingModifier) {
    Panel(modifier = modifier) {
        Label(text, modifier = SwingModifier.testTag("status-message"))
        Button("Dismiss", onClick = onDismiss)
    }
}
```

<!--- KNIT example-static-analysis-14.kt -->

</td>
</tr>
</tbody>
</table>

### ModifierShouldBeForwardedToEveryBranch

When branches render alternate roots, they should honor the same placement, listeners, and test tags. This rule reports a branch that drops a modifier another branch forwards, when that branch's component can accept it. Pass the chain to each applicable root so customizations persist when the displayed component changes.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun SaveStatus(failed: Boolean, onRetry: () -> Unit, modifier: SwingModifier = SwingModifier) {
    if (failed) {
        // A test tag in the modifier disappears when a save fails.
        Button("Retry", onClick = onRetry)
    } else {
        Label("Saved", modifier = modifier)
    }
}
```

<!--- KNIT example-static-analysis-15.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun SaveStatus(failed: Boolean, onRetry: () -> Unit, modifier: SwingModifier = SwingModifier) {
    if (failed) {
        Button("Retry", onClick = onRetry, modifier = modifier)
    } else {
        Label("Saved", modifier = modifier)
    }
}
```

<!--- KNIT example-static-analysis-16.kt -->

</td>
</tr>
</tbody>
</table>

### MultipleSwingModifierParameters

The rule reports additional `SwingModifier` parameters in a composable or composable function type. A modifier applies to the root the caller places in its layout. Exposing child modifiers on the same signature makes the caller depend on which children the component currently owns. Expose a slot to let callers declare and modify the child directly, or split the component into separately reusable composables. When migrating a call to a slot, move the same child, callback, and modifier into the slot.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
-->

```kotlin
@Composable
fun StatusBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    retryButtonModifier: SwingModifier = SwingModifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8)) {
        Label(message)
        Button("Retry", onClick = onRetry, modifier = retryButtonModifier)
    }
}

@Composable
fun SyncStatus(message: String, onRetry: () -> Unit) {
    StatusBanner(
        message = message,
        onRetry = onRetry,
        modifier = SwingModifier.testTag("sync-status"),
        retryButtonModifier = SwingModifier.testTag("retry-sync"),
    )
}
```

<!--- KNIT example-static-analysis-17.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
-->

```kotlin
@Composable
fun StatusBanner(
    message: String,
    modifier: SwingModifier = SwingModifier,
    action: @Composable () -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8)) {
        Label(message)
        action()
    }
}

@Composable
fun SyncStatus(message: String, onRetry: () -> Unit) {
    StatusBanner(
        message = message,
        modifier = SwingModifier.testTag("sync-status"),
    ) {
        // This tag stays with the button if the banner changes its layout.
        Button("Retry", onClick = onRetry, modifier = SwingModifier.testTag("retry-sync"))
    }
}
```

<!--- KNIT example-static-analysis-18.kt -->

</td>
</tr>
</tbody>
</table>

### MultipleSwingNodeCalls

This rule reports additional calls to `SwingNode` or `MenuNode` in a composable, including calls in different branches. Use the library's composable when it already wraps the widget. When you implement a new wrapper, give it responsibility for creating and updating one widget. A layout can then arrange the widget composables.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JLabel
import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@OptIn(SwingComponentLibraryApi::class)
@Composable
fun StatusBanner() {
    Column {
        SwingNode(factory = { JLabel("Ready") })
        SwingNode(factory = { JLabel("Online") })
    }
}
```

<!--- KNIT example-static-analysis-19.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable
fun StatusBanner() {
    Column {
        // Label already owns the creation and updates of its JLabel.
        Label("Ready")
        Label("Online")
    }
}
```

<!--- KNIT example-static-analysis-20.kt -->

</td>
</tr>
</tbody>
</table>

### PreviewAnnotationNaming

Use a name beginning with `Preview` for a reusable Swing preview annotation. At the use site, the prefix identifies the annotation as a rendering configuration. A configuration can set a width or bundle several rendering variants.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
@Preview(width = 280)
annotation class CompactStatus
```

<!--- KNIT example-static-analysis-21.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
// The annotation reads as a preview configuration at the use site.
@Preview(width = 280)
annotation class PreviewCompactStatus
```

<!--- KNIT example-static-analysis-22.kt -->

</td>
</tr>
</tbody>
</table>

### PreviewNaming

This optional rule is off by default. Enable it for a consistent way to recognize preview entry points when browsing composables. It reports names that fail the configured `previewNamingStrategy`. The default strategy is `suffix`, which requires names ending in `Preview`. Use `prefix` to require it at the start or `anywhere` to allow it anywhere in the name.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
@Preview
@Composable
private fun StatusBanner() {
    Label("Connected")
}
```

<!--- KNIT example-static-analysis-23.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
// The default naming strategy looks for the Preview suffix.
@Preview
@Composable
private fun StatusBannerPreview() {
    Label("Connected")
}
```

<!--- KNIT example-static-analysis-24.kt -->

</td>
</tr>
</tbody>
</table>

### PreviewNeedsComposableAnnotation

This rule reports a Swing preview function that lacks Compose Runtime's `@Composable`, including a function marked with a reusable preview annotation. Swing previews show widgets declared in a composition. Add `@Composable` and call the composable you want to render.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.tooling.Preview
import javax.swing.JLabel
-->

```kotlin
@Preview
private fun ConnectionStatusPreview() {
    // This label sits outside the preview composition.
    JLabel("Connected")
}
```

<!--- KNIT example-static-analysis-25.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
@Preview
@Composable
private fun ConnectionStatusPreview() {
    Label("Connected")
}
```

<!--- KNIT example-static-analysis-26.kt -->

</td>
</tr>
</tbody>
</table>

### PreviewPublic

The rule reports a public Swing preview composable. A public preview exposes a tooling entry point to application callers. Make the preview private. If application code calls the function, move its preview annotation to a separate private composable that calls it with sample data.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
@Preview
@Composable
fun StatusBannerPreview() {
    Label(text = "Connected")
}
```

<!--- KNIT example-static-analysis-27.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
// Sample data stays in a tooling entry point outside the public API.
@Preview
@Composable
private fun StatusBannerPreview() {
    Label(text = "Connected")
}
```

<!--- KNIT example-static-analysis-28.kt -->

</td>
</tr>
</tbody>
</table>

### PreviewSignature

The preview renderer calls a top-level function without arguments. This rule reports previews nested inside other declarations, extension receivers, context parameters, and any value parameters, including ones with defaults. Put the annotation on a parameterless top-level composable and supply sample values to the component from there.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
@Preview
@Composable
private fun ConnectionStatusPreview(status: String = "Connected") {
    Label(status)
}
```

<!--- KNIT example-static-analysis-29.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.tooling.Preview
-->

```kotlin
@Preview
@Composable
private fun ConnectionStatusPreview() {
    // Supply the sample value inside the zero-argument entry point.
    Label("Connected")
}
```

<!--- KNIT example-static-analysis-30.kt -->

</td>
</tr>
</tbody>
</table>

### SwingComponentInComposableSignature

The composition owns the Swing components it creates. Passing a component through a composable signature gives callers mutable access outside that lifecycle. In this example, changing the input label's text in Swing does not notify Compose. Pass the data to display and report input events through callbacks.

The rule checks component types in composable signatures, including types nested in generic arguments and callback parameters. Use `SwingNode` when implementing a wrapper. Reserve `@SwingComponentLibraryApi` for APIs meant for component authors. Its exemption applies to the annotated declaration and to members of an annotated class. A declaration that needs a Swing component for another reason, such as `Dialog`'s `owner` naming a window outside the composition, suppresses the rule with a comment that explains the exception. See [What the composition owns](CUSTOM-COMPONENTS.md#what-the-composition-owns).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import javax.swing.JLabel
-->

```kotlin
@Composable
fun SyncStatus(source: JLabel) {
    Column {
        Label("Sync status")
        // Swing edits to source.text do not trigger recomposition.
        Label(source.text)
    }
}
```

<!--- KNIT example-static-analysis-31.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable
fun SyncStatus(message: String) {
    Column {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-32.kt -->

</td>
</tr>
</tbody>
</table>

### SwingModifierFactoryExtensionFunction

A modifier factory should extend the chain the caller already built. A standalone factory starts a fresh chain and makes the caller combine it manually. The rule checks publicly reachable factories that declare a `SwingModifier` return type. Declare an extension and append its behavior to the receiver.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
// Callers must append this result to any modifier chain they already have.
fun syncStatusBadge(onEnter: () -> Unit): SwingModifier =
    SwingModifier.onHover(onEnter = onEnter).testTag("sync-status")
```

<!--- KNIT example-static-analysis-33.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
fun SwingModifier.syncStatusBadge(onEnter: () -> Unit): SwingModifier =
    onHover(onEnter = onEnter).testTag("sync-status")
```

<!--- KNIT example-static-analysis-34.kt -->

</td>
</tr>
</tbody>
</table>

### SwingModifierFactoryReturnType

Expose the resulting chain as `SwingModifier` so callers can combine it with other modifiers and the factory can change its implementation independently. The rule reports an externally visible modifier factory that explicitly exposes a different return type. Keep any concrete modifier types private and return `SwingModifier`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
class StatusBadgeModifier(base: SwingModifier, onEnter: () -> Unit) :
    SwingModifier by base.onHover(onEnter = onEnter).testTag("sync-status")

// Returning StatusBadgeModifier makes the concrete modifier type part of the public API.
fun SwingModifier.statusBadge(onEnter: () -> Unit): StatusBadgeModifier = StatusBadgeModifier(this, onEnter)
```

<!--- KNIT example-static-analysis-35.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
fun SwingModifier.statusBadge(onEnter: () -> Unit): SwingModifier =
    onHover(onEnter = onEnter).testTag("sync-status")
```

<!--- KNIT example-static-analysis-36.kt -->

</td>
</tr>
</tbody>
</table>

### SwingModifierThen

Calling a modifier factory already appends its elements to the receiver. Passing that result back to the same chain's `then` can install each existing listener twice. The rule reports a factory declared in the same file and called on the implicit receiver inside `then`. Call the factory directly. When you need a separate chain to pass to `then`, start that chain from `SwingModifier`. See [Order and merge](MODIFIERS.md#order-and-merge).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import java.awt.Color
-->

```kotlin
private fun SwingModifier.badgeBackground(): SwingModifier =
    background(Color(0xC4E5FF), RoundedCornerShape(6f))

// badgeBackground() already includes the receiver, so then adds that chain twice.
fun SwingModifier.statusBadge(): SwingModifier = this.then(badgeBackground()).testTag("status-badge")
```

<!--- KNIT example-static-analysis-37.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import java.awt.Color
-->

```kotlin
private fun SwingModifier.badgeBackground(): SwingModifier =
    background(Color(0xC4E5FF), RoundedCornerShape(6f))

fun SwingModifier.statusBadge(): SwingModifier = badgeBackground().testTag("status-badge")
```

<!--- KNIT example-static-analysis-38.kt -->

</td>
</tr>
</tbody>
</table>

### SwingModifierUnreferencedReceiver

A caller expects earlier elements to survive a factory call. If the factory starts from the empty `SwingModifier`, the chain it returns drops earlier elements such as a layout constraint or a listener. The rule reports a modifier factory that starts its chain from `SwingModifier` and leaves out the receiver. Build on `this` so the new elements follow the caller's existing elements. See [Writing a modifier builder](MODIFIERS.md#writing-a-builder).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
fun SwingModifier.statusBadge(onEnter: () -> Unit): SwingModifier =
    // Starting with the empty chain drops listeners and layout data from the receiver.
    SwingModifier.onHover(onEnter = onEnter).testTag("status-badge")
```

<!--- KNIT example-static-analysis-39.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
fun SwingModifier.statusBadge(onEnter: () -> Unit): SwingModifier =
    this.onHover(onEnter = onEnter).testTag("status-badge")
```

<!--- KNIT example-static-analysis-40.kt -->

</td>
</tr>
</tbody>
</table>

### SwingModifierWithoutDefault

A caller should be able to omit the modifier or supply a chain of their own. With a nonempty parameter default, supplying a modifier also replaces the default chain. In the example, passing a modifier silently removes the hover handler. The rule checks externally reachable composables for a missing or nonempty `SwingModifier` default. Default to `SwingModifier` and add the component's own behavior to the chain inside the composable.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
@Composable
fun StatusBanner(
    onEnter: () -> Unit,
    // A supplied modifier replaces this hover handler.
    modifier: SwingModifier = SwingModifier.onHover(onEnter = onEnter),
) {
    Label(text = "Connected", modifier = modifier)
}
```

<!--- KNIT example-static-analysis-41.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onHover
-->

```kotlin
@Composable
fun StatusBanner(
    onEnter: () -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    Label(text = "Connected", modifier = modifier.onHover(onEnter = onEnter))
}
```

<!--- KNIT example-static-analysis-42.kt -->

</td>
</tr>
</tbody>
</table>

### SwingNodeFactoryReadsDeclaredValue

A node runs its factory when it creates the Swing component. A label built with `JLabel(text)` keeps the first text if the factory is the only place that reads it. The rule reports a parameter read in a `SwingNode` or `MenuNode` factory without a corresponding update or enclosing composition key. Apply changing properties in `update`. Use an enclosing Compose `key` when the input requires a new widget and its old widget state may be discarded. For a factory-installed callback invoked later, read the latest callback from `rememberUpdatedState` when it runs. See [Writing components](CUSTOM-COMPONENTS.md).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JLabel
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
@SwingComposable
fun StatusBanner(text: String) {
    // Changing text later leaves this label showing its initial value.
    SwingNode(factory = { JLabel(text) })
}
```

<!--- KNIT example-static-analysis-43.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JLabel
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
@SwingComposable
fun StatusBanner(text: String) {
    SwingNode(factory = { JLabel() }, update = { set(text) { this.text = it } })
}
```

<!--- KNIT example-static-analysis-44.kt -->

</td>
</tr>
</tbody>
</table>

### SwingNodeFactoryReusesComponent

Create a fresh component each time a `SwingNode` or `MenuNode` factory runs. Attaching a shared panel to one parent can take it away from another. Reusing the panel after a node is recreated can also carry widget state into the new node. The rule reports direct references returned by factory lambdas when the component is declared outside the lambda. A helper that creates a fresh component on each call is safe. To show a component that the caller builds and keeps, use `movableComponentOf`. See [Showing a component the caller owns](CUSTOM-COMPONENTS.md#showing-a-component-the-caller-owns). If the parent already owns the part you want to configure, use `ExistingSwingNode`. See [Configuring a component its parent owns](CUSTOM-COMPONENTS.md#configuring-a-component-its-parent-owns).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JPanel
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
private val statusBadge = JPanel()

@SwingComposable
@Composable
fun StatusBanner() {
    // Two nodes can try to attach this same panel to different parents.
    SwingNode(factory = { statusBadge })
}
```

<!--- KNIT example-static-analysis-45.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JPanel
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@SwingComposable
@Composable
fun StatusBanner() {
    SwingNode(factory = { JPanel() })
}
```

<!--- KNIT example-static-analysis-46.kt -->

</td>
</tr>
</tbody>
</table>

### SwingNodeUpdateConditionalCall

Keep `set`, `update`, and `declare` in the same order on every pass. If a call disappears, the next call can compare its input against the previous value for a different property. In this example, start with text `"Connecting"` and a tooltip `"Connected"`. Then clear the tooltip and change the text to `"Connected"`. The text update can be skipped because it now occupies the old tooltip's position and sees the same value there.

The rule reports updater calls that a branch, loop, or early return can skip. Use direct statements in the node's `update` block and put the condition into the value each call receives. Passing the nullable tooltip to `set` makes clearing it an explicit property update.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JLabel
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
fun StatusBanner(message: String, details: String?) {
    SwingNode(
        factory = { JLabel() },
        update = {
            // Skipping this call can leave the old text on screen.
            if (details != null) set(details) { toolTipText = it }
            set(message) { text = it }
        },
    )
}
```

<!--- KNIT example-static-analysis-47.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JLabel
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
fun StatusBanner(message: String, details: String?) {
    SwingNode(
        factory = { JLabel() },
        update = {
            // Clearing the tooltip keeps the text update in the same position.
            set(details) { toolTipText = it }
            set(message) { text = it }
        },
    )
}
```

<!--- KNIT example-static-analysis-48.kt -->

</td>
</tr>
</tbody>
</table>

### SwingNodeUpdateRepeatsInvalidation

Swing setters such as `JLabel.setFont` already request layout when the new value calls for it. A second `revalidate` can do redundant invalidation and scheduling work. The rule reports extra `revalidate`, `invalidate`, or `repaint` calls in an updater when the setters in that block already request the same work. Keep requests needed by other setters. For example, changing `JTextField.columns` invalidates the field and may still need a `revalidate` to request layout. See [Writing components](CUSTOM-COMPONENTS.md).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import java.awt.Font
import javax.swing.JLabel
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
fun StatusBanner(textFont: Font) {
    SwingNode(factory = { JLabel() }, update = {
        set(textFont) {
            font = it
            // The font setter already requests layout.
            revalidate()
        }
    })
}
```

<!--- KNIT example-static-analysis-49.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import java.awt.Font
import javax.swing.JLabel
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
fun StatusBanner(textFont: Font) {
    SwingNode(factory = { JLabel() }, update = { set(textFont) { font = it } })
}
```

<!--- KNIT example-static-analysis-50.kt -->

</td>
</tr>
</tbody>
</table>

### SwingNodeUpdateStoresCallback

A composable's callback should follow the component's participation in the composition. A listener registered in a component's constructor can still call the stored callback if the Swing component outlives its node. The rule reports a callback assigned in a node updater and read by a listener the component registers on itself at construction. Attach the listener through a modifier so it reads the latest callback and detaches with the node. See [Attaching a listener](MODIFIERS.md#attaching-a-listener).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JButton
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
class ReconnectPeer : JButton("Reconnect") {
    var onReconnect: () -> Unit = {}

    // This listener can still call onReconnect after the component leaves the composition.
    init { addActionListener { onReconnect() } }
}

@Composable
fun ReconnectButton(onReconnect: () -> Unit) {
    SwingNode(factory = { ReconnectPeer() }, update = { set(onReconnect) { this.onReconnect = it } })
}
```

<!--- KNIT example-static-analysis-51.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JButton
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
fun ReconnectButton(onReconnect: () -> Unit) {
    SwingNode(factory = { JButton("Reconnect") }, modifier = SwingModifier.actionListener { onReconnect() })
}
```

<!--- KNIT example-static-analysis-52.kt -->

</td>
</tr>
</tbody>
</table>

### SwingNodeUpdateWritesComponentPart

The rule reports node updates that write to children built by the Swing container in source. Declare ordinary children as composables in a library container so each child can receive its own modifier and style. When wrapping an existing Swing widget that owns its internal children, update it through the widget's API and let it manage those children. See [What the composition owns](CUSTOM-COMPONENTS.md#what-the-composition-owns).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.compose.swing.node.SwingNode
-->

```kotlin
@Composable
fun StatusBanner(text: String) {
    SwingNode(
        factory = { StatusPanel() },
        update = {
            // The message label has no composition node.
            set(text) { messageLabel.text = it }
        },
    )
}

private class StatusPanel : JPanel() {
    val messageLabel = JLabel()

    init {
        add(JLabel("Sync status"))
        add(messageLabel)
    }
}
```

<!--- KNIT example-static-analysis-53.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
-->

```kotlin
@Composable
fun StatusBanner(text: String) {
    Panel {
        Label("Sync status")
        Label(text)
    }
}
```

<!--- KNIT example-static-analysis-54.kt -->

</td>
</tr>
</tbody>
</table>

### SwingStyleWithoutDefault

Callers should be able to omit the style and still get the component's own appearance. A nonempty parameter default disappears as soon as a caller supplies an unrelated property such as a font. The rule checks externally reachable composables for a missing or nonempty `SwingStyle` default. Use the empty `SwingStyle` for the parameter and combine the component's own style before the caller's. The caller then overrides the properties they set. See [Reusing a style](STYLES.md#reusing-a-style).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
-->

```kotlin
@Composable
fun StatusBanner(
    // Supplying a style that only sets the font also drops the blue foreground.
    style: SwingStyle<LabelStyleScope> = SwingStyle { foreground(Color.BLUE) },
) {
    Label(text = "Connected", style = style)
}
```

<!--- KNIT example-static-analysis-55.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.then
import java.awt.Color
-->

```kotlin
@Composable
fun StatusBanner(style: SwingStyle<LabelStyleScope> = SwingStyle) {
    Label(text = "Connected", style = connectionStyle then style)
}

private val connectionStyle = SwingStyle<LabelStyleScope> { foreground(Color.BLUE) }
```

<!--- KNIT example-static-analysis-56.kt -->

</td>
</tr>
</tbody>
</table>

## Compose Swing UI content rules

Rule-set ID `compose-swing-content`.

### ComposableAnnotationNaming

Mark a custom composable target annotation with a name ending in `Composable`. At the use site, the suffix distinguishes a target annotation from an annotation for the component itself. The rule reports a `@ComposableTargetMarker` annotation without that suffix.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.ComposableTargetMarker
-->

```kotlin
@ComposableTargetMarker(description = "Status Banner")
// At the use site, @StatusBanner can look like a component annotation.
annotation class StatusBanner
```

<!--- KNIT example-static-analysis-57.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.ComposableTargetMarker
-->

```kotlin
@ComposableTargetMarker(description = "Status Banner")
annotation class StatusBannerComposable
```

<!--- KNIT example-static-analysis-58.kt -->

</td>
</tr>
</tbody>
</table>

### ComposableNestingDepth

Layout code gets harder to scan when several levels of containers hide the parts a caller cares about. Extract a nested region into a composable named for what it displays. This rule is off by default. It reports nested Swing content emitters when their depth exceeds `composableNestingDepthThreshold`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
-->

```kotlin
@Composable
fun SettingsSummary(message: String) {
    Column {
        Label("Settings")
        Row {
            Label("Sync")
            Column {
                Label("Current status")
                Row { Label(message) }
            }
        }
    }
}
```

<!--- KNIT example-static-analysis-59.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
-->

```kotlin
@Composable
fun SettingsSummary(message: String) {
    Column {
        Label("Settings")
        Row {
            Label("Sync")
            // The sync region keeps its own layout when extracted.
            SyncDetails(message)
        }
    }
}

@Composable
private fun SyncDetails(message: String) {
    Column {
        Label("Current status")
        Row { Label(message) }
    }
}
```

<!--- KNIT example-static-analysis-60.kt -->

</td>
</tr>
</tbody>
</table>

### ComposableParamOrder

Callers should be able to pass the required arguments and then a `SwingModifier` in the same position across components. Put required parameters first, then the defaulted modifier, then the other defaulted parameters. The check keeps any existing trailing lambda last. It allows an optional event callback to stay earlier in the parameter list when reordering would make it trailing.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(
    title: String,
    // This option pushes the modifier out of its usual argument position.
    withPrefix: Boolean = false,
    modifier: SwingModifier = SwingModifier,
) {
    Label(text = if (withPrefix) "Status: $title" else title, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-61.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(
    title: String,
    modifier: SwingModifier = SwingModifier,
    withPrefix: Boolean = false,
) {
    Label(text = if (withPrefix) "Status: $title" else title, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-62.kt -->

</td>
</tr>
</tbody>
</table>

### ConditionHoist

A layout with a single conditional child can stay in its parent when the child is absent. Put the condition around the layout if both should appear and disappear together. The rule reports this pattern on plain layouts such as `Column` and `Row` with no other content or layout arguments.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable fun Greeting(showGreeting: Boolean) {
    // The parent still sees an empty column when the greeting is hidden.
    Column {
        if (showGreeting) {
            Label("Hello")
        }
    }
}
```

<!--- KNIT example-static-analysis-63.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable fun Greeting(showGreeting: Boolean) {
    if (showGreeting) {
        Column {
            Label("Hello")
        }
    }
}
```

<!--- KNIT example-static-analysis-64.kt -->

</td>
</tr>
</tbody>
</table>

### ContentEmitterReturningValues

A function that emits Swing content and returns a result forces callers to add a component just to get the result. The rule reports that mixed contract. Return `Unit` from the component and expose the value independently, so callers can place the content and use the result where each is needed.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun SearchHint(query: String): Boolean {
    val canSearch = query.length >= 3
    Label(if (canSearch) "Ready to search" else "Enter at least 3 characters")
    // Getting this result also adds a label to the parent.
    return canSearch
}
```

<!--- KNIT example-static-analysis-65.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
fun canSearch(query: String): Boolean = query.length >= 3

@Composable
fun SearchHint(query: String) {
    Label(if (canSearch(query)) "Ready to search" else "Enter at least 3 characters")
}
```

<!--- KNIT example-static-analysis-66.kt -->

</td>
</tr>
</tbody>
</table>

### ContentSlotReused

Separate calls to `content()` have separate remembered state. If the search slot remembers the query, switching between the row and column clears it. The rule reports repeated calls to the content slot. Keep a single invocation when only the placement changes, or remember a movable slot when the parent layout changes. Pass the current content lambda to the movable slot so it can also display updated content.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
-->

```kotlin
@Composable
fun SearchPanel(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) {
        Column {
            Label("Search")
            content()
        }
    } else {
        Row {
            Label("Search")
            content()
        }
    }
}
```

<!--- KNIT example-static-analysis-67.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
-->

```kotlin
@Composable
fun SearchPanel(compact: Boolean, content: @Composable () -> Unit) {
    // The slot's remembered state moves with its content.
    val movableSlot = remember { movableContentOf<@Composable () -> Unit> { slot -> slot() } }
    if (compact) {
        Column {
            Label("Search")
            movableSlot(content)
        }
    } else {
        Row {
            Label("Search")
            movableSlot(content)
        }
    }
}
```

<!--- KNIT example-static-analysis-68.kt -->

</td>
</tr>
</tbody>
</table>

### ContentTrailingLambda

Put the main content slot last so callers can write children in a trailing lambda after the component arguments. The rule reports a composable lambda named `content` followed by another parameter.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable
fun StatusBanner(content: @Composable () -> Unit, message: String) {
    Column {
        Label(text = message)
        content()
    }
}
```

<!--- KNIT example-static-analysis-69.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable
// Callers can write StatusBanner("Saved") { ... }.
fun StatusBanner(message: String, content: @Composable () -> Unit) {
    Column {
        Label(text = message)
        content()
    }
}
```

<!--- KNIT example-static-analysis-70.kt -->

</td>
</tr>
</tbody>
</table>

### DefaultsVisibility

Callers who want to reuse a component's default, such as its text, need access to the declaration that supplies it. The rule reports an `XDefaults` class or object that is less visible than a corresponding composable `X` that references it. Match the visibility of the most visible overload that uses those defaults.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
// Callers can use the banner but cannot reuse its default text.
private object StatusBannerDefaults {
    val text = "Saved"
}

@Composable
fun StatusBanner(text: String = StatusBannerDefaults.text, modifier: SwingModifier = SwingModifier) {
    Label(text, modifier)
}
```

<!--- KNIT example-static-analysis-71.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
object StatusBannerDefaults {
    val text = "Saved"
}

@Composable
fun StatusBanner(text: String = StatusBannerDefaults.text, modifier: SwingModifier = SwingModifier) {
    Label(text, modifier)
}
```

<!--- KNIT example-static-analysis-72.kt -->

</td>
</tr>
</tbody>
</table>

### LambdaParameterEventTrailing

Call syntax such as `DismissibleNotice("Sync failed") { ... }` makes a click handler look like child content. The rule reports a required trailing event lambda named `on...` when the composable emits Swing content and accepts a modifier. Move a required event callback before the modifier. Give an optional callback a default when the component can work without it.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
// DismissibleNotice("Sync failed") { ... } supplies a click handler.
@Composable
fun DismissibleNotice(message: String, modifier: SwingModifier = SwingModifier, onDismiss: () -> Unit) {
    Row(modifier) {
        Label(message)
        Button("Dismiss", onClick = onDismiss)
    }
}
```

<!--- KNIT example-static-analysis-73.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun DismissibleNotice(message: String, onDismiss: () -> Unit, modifier: SwingModifier = SwingModifier) {
    Row(modifier) {
        Label(message)
        Button("Dismiss", onClick = onDismiss)
    }
}
```

<!--- KNIT example-static-analysis-74.kt -->

</td>
</tr>
</tbody>
</table>

### MultipleEmitters

A Swing layout receives each top-level component as a separate child. If a banner emits two labels, the caller cannot place or decorate the banner as a single child. The rule reports multiple top-level emitters on the same path. Put the labels in one root container and let the caller customize that root, or expose the pieces separately for callers to arrange.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner() {
    // The parent has to place each label separately.
    Label("Saved")
    Label("Autosave on")
}
```

<!--- KNIT example-static-analysis-75.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(modifier: SwingModifier = SwingModifier) {
    // The caller can place the whole banner with one modifier.
    Column(modifier) {
        Label("Saved")
        Label("Autosave on")
    }
}
```

<!--- KNIT example-static-analysis-76.kt -->

</td>
</tr>
</tbody>
</table>

### MutableParams

A mutable collection can change in place while the caller keeps passing the same instance. Compose cannot observe those edits, so the list shown on screen can stay stale. The rule reports inherently mutable collection parameters such as `MutableList`. Pass a read-only snapshot and publish a new list when its contents change. Use observable state for edits the UI must track.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(messages: MutableList<String>, modifier: SwingModifier = SwingModifier) {
    // Edits to the list can leave the displayed text stale.
    Label(messages.joinToString(), modifier)
}
```

<!--- KNIT example-static-analysis-77.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
// Pass a new snapshot when messages change.
fun StatusBanner(messages: List<String>, modifier: SwingModifier = SwingModifier) {
    Label(messages.joinToString(), modifier)
}
```

<!--- KNIT example-static-analysis-78.kt -->

</td>
</tr>
</tbody>
</table>

### ParameterNaming

Use `on` followed by a present-tense verb so callback names read consistently at the call site. The rule reports past-tense event names such as `onDismissed`. Use `onDismiss`, or allow a name your API needs to retain through `allowedLambdaParameterNames`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun DismissibleNotice(message: String, onDismissed: () -> Unit, modifier: SwingModifier = SwingModifier) {
    Row(modifier) {
        Label(message)
        Button("Dismiss", onClick = onDismissed)
    }
}
```

<!--- KNIT example-static-analysis-79.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun DismissibleNotice(message: String, onDismiss: () -> Unit, modifier: SwingModifier = SwingModifier) {
    Row(modifier) {
        Label(message)
        // The caller decides whether to remove the notice.
        Button("Dismiss", onClick = onDismiss)
    }
}
```

<!--- KNIT example-static-analysis-80.kt -->

</td>
</tr>
</tbody>
</table>

### UnstableCollections

Recreating an equivalent `List` can still make Compose recompose a caller's child, because a standard collection type gives the compiler no immutability guarantee. This opt-in rule reports unstable collection parameters. Pass a Kotlinx immutable collection or an `@Immutable` value that satisfies the annotation contract. Here the immutable value keeps only the rendered text, so later edits to the input list cannot affect it.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable fun StatusBanner(messages: List<String>) {
    Label(text = messages.joinToString())
}
```

<!--- KNIT example-static-analysis-81.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Immutable
class StatusMessages(messages: List<String>) {
    // Later edits to the input list leave this value unchanged.
    val text: String = messages.joinToString()
}

@Composable fun StatusBanner(messages: StatusMessages) {
    Label(text = messages.text)
}
```

<!--- KNIT example-static-analysis-82.kt -->

</td>
</tr>
</tbody>
</table>

## Compose Swing UI component rules

Rule-set ID `compose-swing-components`.

### UnheldColumnComparator

The table treats a different comparator instance as a change to the sort order. Rebuilding the comparator each time the column is declared can make an already sorted table sort its rows again. The rule reports a fresh comparator in a column declaration. Hold one instance in a top-level or member value. If the ordering depends on a changing option, use `remember` keyed on that option so the table sorts again when the order changes.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
-->

```kotlin
@Composable
fun StatusBanner(rows: List<String>) {
    Table(rows = rows, columns = {
        // A fresh comparator can sort the rows again.
        column("Status", comparator = compareBy<Any?> { it.toString() }) { it }
    })
}
```

<!--- KNIT example-static-analysis-83.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
-->

```kotlin
private val statusOrder: Comparator<Any?> = compareBy<Any?> { it.toString() }

@Composable
fun StatusBanner(rows: List<String>) {
    Table(rows = rows, columns = {
        column("Status", comparator = statusOrder) { it }
    })
}
```

<!--- KNIT example-static-analysis-84.kt -->

</td>
</tr>
</tbody>
</table>

## Compose Swing UI animation rules

Rule-set ID `compose-swing-animation`.

### UnusedAnimatedContentTargetState

During an `AnimatedContent` or `Crossfade` transition, the outgoing and incoming content both need their own state. Reading the outer target can show the new label in the outgoing component as it leaves. An `AnimatedContent` key that ignores the state can also treat distinct states as the same content and skip the transition. The rule reports a content or content key lambda that ignores its state parameter. Use the parameter to render the content and compute its key.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
enum class SyncState(val text: String) {
    Idle("Up to date"),
    Syncing("Syncing..."),
}

@Composable
fun StatusBanner(state: SyncState) {
    // The outgoing label also reads the new state.
    AnimatedContent(state) { Label(state.text) }
}
```

<!--- KNIT example-static-analysis-85.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
enum class SyncState(val text: String) {
    Idle("Up to date"),
    Syncing("Syncing..."),
}

@Composable
fun StatusBanner(state: SyncState) {
    AnimatedContent(state) { Label(it.text) }
}
```

<!--- KNIT example-static-analysis-86.kt -->

</td>
</tr>
</tbody>
</table>

### UnusedTransitionTargetState

A transition asks its value lambda what value belongs to each state. Capturing the outer target can give both states the same value, such as returning the visible opacity for both the hidden and visible state. The rule reports a transition value lambda that ignores its state parameter. Map that parameter to the value. Keep constant values outside the transition.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.updateTransition
-->

```kotlin
@Composable
fun statusOpacity(visible: Boolean): State<Float> {
    val transition = updateTransition(visible)
    // Both ends of the transition read the same visibility.
    return transition.animateFloat { if (visible) 1f else 0f }
}
```

<!--- KNIT example-static-analysis-87.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.updateTransition
-->

```kotlin
@Composable
fun statusOpacity(visible: Boolean): State<Float> {
    val transition = updateTransition(visible)
    return transition.animateFloat { if (it) 1f else 0f }
}
```

<!--- KNIT example-static-analysis-88.kt -->

</td>
</tr>
</tbody>
</table>

## Compose runtime rules

Rule-set ID `compose-runtime`.

### AutoboxingStateValueProperty

Reports access through the generic `value` property on specialized state such as `IntState`. On the JVM, that access boxes the primitive even though the state stores it unboxed. Keep the specialized type at the call site and read or write its primitive property, such as `intValue`. Property delegation with `by` also uses the specialized accessor when the delegate has that type.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.mutableIntStateOf
-->

```kotlin
class StatusBanner {
    private val unreadCount = mutableIntStateOf(0)

    fun hasUnread(): Boolean {
        // The read boxes the count before the comparison unboxes it.
        val count = unreadCount.value
        return count > 0
    }
}
```

<!--- KNIT example-static-analysis-89.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.mutableIntStateOf
-->

```kotlin
class StatusBanner {
    private val unreadCount = mutableIntStateOf(0)

    fun hasUnread(): Boolean {
        val count = unreadCount.intValue
        return count > 0
    }
}
```

<!--- KNIT example-static-analysis-90.kt -->

</td>
</tr>
</tbody>
</table>

### ComposableLambdaParameterNaming

Name a sole composable slot with no value parameters `content` in a `Unit` composable. The shared name makes the main slot recognizable at a named call site. This check is off by default.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
@Composable
fun StatusBanner(details: @Composable () -> Unit) {
    Column {
        Label("Sync status")
        details()
    }
}
```

<!--- KNIT example-static-analysis-91.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
-->

```kotlin
// A named call reads as StatusBanner(content = { ... }).
@Composable
fun StatusBanner(content: @Composable () -> Unit) {
    Column {
        Label("Sync status")
        content()
    }
}
```

<!--- KNIT example-static-analysis-92.kt -->

</td>
</tr>
</tbody>
</table>

### ComposableLambdaParameterPosition

Put the sole composable slot last in a `Unit` composable. That lets callers write the child declarations in a trailing lambda so they can read the layout in the order it appears. This check is off by default.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
-->

```kotlin
@Composable
fun StatusBanner(content: @Composable () -> Unit, message: String) {
    Panel {
        Label(message)
        content()
    }
}
```

<!--- KNIT example-static-analysis-93.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
-->

```kotlin
// Callers can write StatusBanner("Ready") { ... }.
@Composable
fun StatusBanner(message: String, content: @Composable () -> Unit) {
    Panel {
        Label(message)
        content()
    }
}
```

<!--- KNIT example-static-analysis-94.kt -->

</td>
</tr>
</tbody>
</table>

### ComposableNaming

Start names of `Unit` composables with an uppercase letter. At a call site, the name describes content or an effect that can enter and leave the composition. Start names of value-returning composables with a lowercase letter so the call reads as a computation.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun statusBanner(message: String) {
    Label(message)
}
```

<!--- KNIT example-static-analysis-95.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
// The call declares UI that can enter and leave the composition.
@Composable
fun StatusBanner(message: String) {
    Label(message)
}
```

<!--- KNIT example-static-analysis-96.kt -->

</td>
</tr>
</tbody>
</table>

### CompositionLocalAllowlist

Reports composition local declarations outside `allowedCompositionLocals`. A caller reading the signature cannot tell which ancestor needs to provide a local. Pass component inputs such as a banner's text as parameters so a caller can supply them directly. For values shared across a subtree, such as a theme, define a default or require a provider and add the local to the allowlist.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
private val LocalStatusText = compositionLocalOf { "" }

@Composable
fun StatusBanner() {
    // Without a provider, the banner displays empty text.
    Label(LocalStatusText.current)
}
```

<!--- KNIT example-static-analysis-97.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner(text: String) {
    Label(text)
}
```

<!--- KNIT example-static-analysis-98.kt -->

</td>
</tr>
</tbody>
</table>

### CompositionLocalNaming

Start a composition local's name with `Local` and name the value it provides. A read such as `LocalStatusTone.current` then tells a caller to look for a provider in the enclosing composition.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.compositionLocalOf
-->

```kotlin
val StatusTone = compositionLocalOf { "info" }
```

<!--- KNIT example-static-analysis-99.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.compositionLocalOf
-->

```kotlin
// The name signals that the active tone comes from the composition.
val LocalStatusTone = compositionLocalOf { "info" }
```

<!--- KNIT example-static-analysis-100.kt -->

</td>
</tr>
</tbody>
</table>

### CoroutineCreationDuringComposition

Reports `launch`, `async`, or `launchIn` during composition. A coroutine started in the body can run before composition succeeds. An unrelated recomposition can also start a second copy, so a banner's timeout may fire twice. Start work tied to the composable in `LaunchedEffect`. Key it on the inputs that should restart the work. Compose cancels it when the key changes or the effect leaves composition. For work triggered by a click, launch from the event callback using a scope from `rememberCoroutineScope`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner(message: String, onTimeout: () -> Unit) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    val scope = rememberCoroutineScope()
    // An unrelated recomposition can start another timer for the same message.
    scope.launch {
        delay(5_000L)
        currentOnTimeout()
    }
    Label(message)
}
```

<!--- KNIT example-static-analysis-101.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner(message: String, onTimeout: () -> Unit) {
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(message) {
        delay(5_000L)
        currentOnTimeout()
    }
    Label(message)
}
```

<!--- KNIT example-static-analysis-102.kt -->

</td>
</tr>
</tbody>
</table>

### FlowOperatorInvokedInComposition

Reports Flow transformations such as `map` during composition. Recreating the transformed flow on recomposition can restart collection even if the source stays the same. A cold flow may repeat the upstream work. Remember the transformed flow with the source and any inputs that change the transformation as keys. If an effect owns collection, create the transformation inside that effect.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner(statuses: Flow<String>) {
    // A new transformed flow can make the collector repeat upstream work.
    val label by statuses.map { it.uppercase() }.collectAsState(initial = "")
    Label(label)
}
```

<!--- KNIT example-static-analysis-103.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner(statuses: Flow<String>) {
    val upper = remember(statuses) { statuses.map { it.uppercase() } }
    val label by upper.collectAsState(initial = "")
    Label(label)
}
```

<!--- KNIT example-static-analysis-104.kt -->

</td>
</tr>
</tbody>
</table>

### FrequentlyChangingValue

Reports reads of properties or calls to functions marked `@FrequentlyChangingValue` during composition. A banner may only need to know whether progress has reached 100%, but a direct read subscribes it to every progress change. Use `derivedStateOf` when the UI depends on a result that changes less often. To observe snapshot state outside composition, collect `snapshotFlow` in an effect. For a value needed only during painting, read it in the [draw block](FOUNDATION.md#drawing-with-canvas-and-drawscope).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
class StatusModel {
    @get:FrequentlyChangingValue
    var progress by mutableIntStateOf(0)
}

@Composable
fun StatusBanner(model: StatusModel) {
    // Changing progress from 50 to 51 can recompose the same "Working" label.
    val isDone = model.progress >= 100
    Label(if (isDone) "Complete" else "Working")
}
```

<!--- KNIT example-static-analysis-105.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
class StatusModel {
    @get:FrequentlyChangingValue
    var progress by mutableIntStateOf(0)
}

@Composable
fun StatusBanner(model: StatusModel) {
    val isDone by remember(model) { derivedStateOf { model.progress >= 100 } }
    Label(if (isDone) "Complete" else "Working")
}
```

<!--- KNIT example-static-analysis-106.kt -->

</td>
</tr>
</tbody>
</table>

### InvalidReadOnlyComposable

Reports a read-only composable that calls a composable function or getter without `@ReadOnlyComposable`. The annotation promises that a function only reads from the composition, which lets other read-only functions call it. It fits helpers that read a composition local. A function that declares a component such as `Label` needs ordinary `@Composable`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@ReadOnlyComposable
@Composable
fun StatusBanner() {
    // Emitting a label requires a composable that can write to the composition.
    Label("Ready")
}
```

<!--- CLEAR -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
-->

```kotlin
@Composable
fun StatusBanner() {
    Label("Ready")
}
```

<!--- KNIT example-static-analysis-read-01.kt -->

</td>
</tr>
</tbody>
</table>

### LambdaParameterInRestartableEffect

Reports callback parameters invoked or passed to other calls inside `LaunchedEffect`, `DisposableEffect`, `produceState`, `LifecycleStartEffect`, or `LifecycleResumeEffect` unless they are keys. A collector that outlasts a recomposition can keep sending new results to an old handler. Use `rememberUpdatedState` when a new handler should leave the subscription running. Read the current callback when each result arrives. Use a callback as a key when replacing it should restart the work.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
-->

```kotlin
@Composable
fun ObserveStatus(statuses: Flow<String>, onStatus: (String) -> Unit) {
    LaunchedEffect(statuses) {
        // Changing only onStatus leaves this collector using the previous handler.
        statuses.collect { onStatus(it) }
    }
}
```

<!--- KNIT example-static-analysis-107.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
-->

```kotlin
@Composable
fun ObserveStatus(statuses: Flow<String>, onStatus: (String) -> Unit) {
    val currentOnStatus by rememberUpdatedState(onStatus)
    LaunchedEffect(statuses) {
        statuses.collect { currentOnStatus(it) }
    }
}
```

<!--- KNIT example-static-analysis-108.kt -->

</td>
</tr>
</tbody>
</table>

### MissingNonRestartableComposable

Reports a `Unit` composable function or getter that only forwards constants or existing values to another `Unit` composable. A forwarding helper rarely needs to recompose on its own. Use `@NonRestartableComposable` for this shape to remove the wrapper's separate restart and skip boundary. Reconsider the annotation if the wrapper gains its own state reads or other substantial work. Preview functions are exempt by default. Set `ignoresPreviews` to `false` to check them.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(text: String, modifier: SwingModifier = SwingModifier) {
    Label(text, modifier)
}
```

<!--- KNIT example-static-analysis-109.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
// Reconsider this annotation if the wrapper starts reading its own state.
@Composable
@NonRestartableComposable
fun StatusBanner(text: String, modifier: SwingModifier = SwingModifier) {
    Label(text, modifier)
}
```

<!--- KNIT example-static-analysis-110.kt -->

</td>
</tr>
</tbody>
</table>

### MissingReadOnlyComposable

Reports non-overriding composable functions and getters that can be marked `@ReadOnlyComposable`, such as a helper that only reads a composition local. Mark them `@ReadOnlyComposable` so they can be called from a read-only context. Reserve the annotation for code that only reads the composition.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalCount = compositionLocalOf { 0 }
-->

```kotlin
@Composable
fun currentCount(): Int = LocalCount.current
```

<!--- KNIT example-static-analysis-111.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

val LocalCount = compositionLocalOf { 0 }
-->

```kotlin
// Read-only composables can call this helper.
@ReadOnlyComposable
@Composable
fun currentCount(): Int = LocalCount.current
```

<!--- KNIT example-static-analysis-112.kt -->

</td>
</tr>
</tbody>
</table>

### MutableCollectionMutableState

A message added to a plain mutable list can leave the log showing its old contents. The rule reports mutable collections stored in `MutableState` because in-place edits leave the state value unchanged. Replace the value with a new read-only list, or store the messages in a `SnapshotStateList` if you need to edit them in place.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.mutableStateOf
-->

```kotlin
class ConnectionLog {
    val messages = mutableStateOf(mutableListOf("Connecting"))

    fun onConnected() {
        // The list changes in place, so no state write triggers recomposition.
        messages.value.add("Connected")
    }
}
```

<!--- KNIT example-static-analysis-113.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.mutableStateOf
-->

```kotlin
class ConnectionLog {
    val messages = mutableStateOf(listOf("Connecting"))

    fun onConnected() {
        messages.value = messages.value + "Connected"
    }
}
```

<!--- KNIT example-static-analysis-114.kt -->

</td>
</tr>
</tbody>
</table>

### MutableStateAutoboxing

Reports `mutableStateOf` with structural equality for a non-null `Int`, `Long`, `Float`, or `Double`. Use the matching factory, such as `mutableIntStateOf`, to avoid boxing values when state is read and written. Use generic state when the value can be null or needs a custom equality policy.

The rule also reports certain lists, sets, and maps of `Int`, `Long`, and `Float` passed directly from a parameter. Use a primitive-backed AndroidX collection when appropriate. Assign a new collection when its contents change so Compose can observe the update.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
-->

```kotlin
class InboxState {
    // Generic state stores this Int as a boxed value.
    val unreadCount = mutableStateOf(0)
}
```

<!--- KNIT example-static-analysis-115.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
-->

```kotlin
class InboxState {
    val unreadCount = mutableIntStateOf(0)
}
```

<!--- KNIT example-static-analysis-116.kt -->

</td>
</tr>
</tbody>
</table>

### MutableStateParam

A composable that receives `MutableState` can overwrite the caller's value directly. The rule reports these parameters. Pass the current value and an event callback so the caller can validate or reject a proposed edit before writing it. Use a state holder with named properties and operations when a component manages several related values. See [component state](COMPONENT-STATE.md).

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun ProfileNameField(name: MutableState<String>, modifier: SwingModifier = SwingModifier) {
    Column(modifier) {
        Label("Display name")
        // Each edit writes directly to the caller's state.
        TextField(value = name.value, onValueChange = { name.value = it }, columns = 24)
    }
}
```

<!--- KNIT example-static-analysis-117.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun ProfileNameField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    Column(modifier) {
        Label("Display name")
        TextField(value = name, onValueChange = onNameChange, columns = 24)
    }
}
```

<!--- KNIT example-static-analysis-118.kt -->

</td>
</tr>
</tbody>
</table>

### OpaqueUnitKey

In the example, validation runs on the current messages while the remembered sort can keep showing the old list. The validation call returns `Unit`, so the key never changes. The rule reports these implicit `Unit` keys. Key the sort on `messages`. Pass `Unit` explicitly for an effect that should run once per entry.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
-->

```kotlin
@Composable
fun rememberOrderedMessages(messages: List<String>): List<String> =
    // forEach returns Unit, so even a new list leaves the key unchanged.
    remember(messages.forEach { require(it.isNotBlank()) }) {
        messages.sorted()
    }
```

<!--- KNIT example-static-analysis-119.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
-->

```kotlin
@Composable
fun rememberOrderedMessages(messages: List<String>): List<String> =
    remember(messages) {
        messages.forEach { require(it.isNotBlank()) }
        messages.sorted()
    }
```

<!--- KNIT example-static-analysis-120.kt -->

</td>
</tr>
</tbody>
</table>

### ProduceStateDoesNotAssignValue

A completed title lookup leaves the UI showing `Loading` until the producer assigns the result to `value`. The rule reports `produceState` blocks that leave the returned state unchanged. Key the producer on the inputs that determine its work so a new document cancels the old lookup and starts a new one. Use an effect such as `LaunchedEffect` when the work produces no state to display.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
-->

```kotlin
@Composable
fun rememberDocumentTitle(documentId: String, loadTitle: suspend (String) -> String): State<String> =
    produceState("Loading", documentId, loadTitle) {
        // The title is discarded, so the state stays at "Loading".
        loadTitle(documentId)
    }
```

<!--- KNIT example-static-analysis-121.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
-->

```kotlin
@Composable
fun rememberDocumentTitle(documentId: String, loadTitle: suspend (String) -> String): State<String> =
    produceState("Loading", documentId, loadTitle) {
        value = loadTitle(documentId)
    }
```

<!--- KNIT example-static-analysis-122.kt -->

</td>
</tr>
</tbody>
</table>

### RememberContentMissing

Switching between a row and a column should preserve the editor's draft. Recreating the movable content loses its identity and can discard the draft on recomposition. The rule reports movable content created during composition without `remember`. Remember the movable content once and pass any changing inputs when you invoke it.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun EditorDock(horizontal: Boolean, modifier: SwingModifier = SwingModifier) {
    // Recomposition creates a new slot and can discard the draft.
    val editor = movableContentOf {
        var draft by remember { mutableStateOf("") }
        TextField(value = draft, onValueChange = { draft = it })
    }
    if (horizontal) {
        Row(modifier) { editor() }
    } else {
        Column(modifier) { editor() }
    }
}
```

<!--- KNIT example-static-analysis-123.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun EditorDock(horizontal: Boolean, modifier: SwingModifier = SwingModifier) {
    val editor = remember {
        movableContentOf {
            var draft by remember { mutableStateOf("") }
            TextField(value = draft, onValueChange = { draft = it })
        }
    }
    if (horizontal) {
        Row(modifier) { editor() }
    } else {
        Column(modifier) { editor() }
    }
}
```

<!--- KNIT example-static-analysis-124.kt -->

</td>
</tr>
</tbody>
</table>

### RememberInComposition

Recreating a `ListState` can reset the user's selection on an unrelated recomposition. The rule reports calls to a constructor, function, or getter marked `@RememberInComposition` when they occur outside a remembering scope during composition. Use a remembering factory such as `rememberListState`, construct the value inside `remember`, or pass an existing value from the caller.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.ListState
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun FilterList(filters: List<String>, modifier: SwingModifier = SwingModifier) {
    // Recomposition can reset the selection to the first filter.
    val selection = ListState(setOf(0))
    ListBox(filters, modifier = modifier, state = selection)
}
```

<!--- KNIT example-static-analysis-125.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.rememberListState
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun FilterList(filters: List<String>, modifier: SwingModifier = SwingModifier) {
    val selection = rememberListState(initialSelectedIndices = setOf(0))
    ListBox(filters, modifier = modifier, state = selection)
}
```

<!--- KNIT example-static-analysis-126.kt -->

</td>
</tr>
</tbody>
</table>

### RememberMissing

Recreating an editor's state restores the initial text and can discard the edit that triggered recomposition. The rule reports Compose state created during composition without `remember`. Create the state with `remember` for the lifetime of that editor, or let the caller own it and pass the current value with a callback.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusEditor(modifier: SwingModifier = SwingModifier) {
    // The edit can be replaced with "Saved" on the next recomposition.
    var message by mutableStateOf("Saved")
    TextField(value = message, onValueChange = { message = it }, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-127.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusEditor(modifier: SwingModifier = SwingModifier) {
    var message by remember { mutableStateOf("Saved") }
    TextField(value = message, onValueChange = { message = it }, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-128.kt -->

</td>
</tr>
</tbody>
</table>

### RememberReturnType

Reports a `remember` block that returns `Unit`. Publishing from that block can update an external object even if composition is abandoned. Publish the current value in `SideEffect` to update the object after each successful recomposition. Use `remember` when you need the calculation's result on later recompositions.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
-->

```kotlin
class StatusSink(var value: String)

@Composable
fun PublishStatus(status: String, sink: StatusSink) {
    // An abandoned composition can still write to the sink.
    remember { sink.value = status }
}
```

<!--- KNIT example-static-analysis-129.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
-->

```kotlin
class StatusSink(var value: String)

@Composable
fun PublishStatus(status: String, sink: StatusSink) {
    SideEffect { sink.value = status }
}
```

<!--- KNIT example-static-analysis-130.kt -->

</td>
</tr>
</tbody>
</table>

### RememberSaveableSaverParameter

Reports a `Saver` passed to `rememberSaveable` as the only input. Inputs reset the remembered value when they change. With a positional saver argument, Compose still uses the default saver, which may reject a value that requires conversion. Name the `saver` argument to use the custom conversion.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
-->

```kotlin
class Status(val message: String)
private val statusSaver = Saver<Status, String>(save = { it.message }, restore = ::Status)

// This passes statusSaver as a reset key and leaves the default saver active.
@Composable
fun rememberStatus(): Status = rememberSaveable(statusSaver) { Status("Ready") }
```

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
-->

```kotlin
class Status(val message: String)
private val statusSaver = Saver<Status, String>(save = { it.message }, restore = ::Status)

@Composable
fun rememberStatus(): Status = rememberSaveable(saver = statusSaver) { Status("Ready") }
```

<!--- CLEAR -->

</td>
</tr>
</tbody>
</table>

### RetainRememberObserver

Reports a call to `retain` whose declared result type implements `RememberObserver` without `RetainObserver`. Retaining such an object throws. Use `remember` when the object needs the remember callbacks. If the object also needs to survive between compositions, implement `RetainObserver` to handle the retain lifecycle, including when to pause work and when to release resources.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.retain.retain
-->

```kotlin
class StatusPoller : RememberObserver {
    override fun onRemembered() {}
    override fun onForgotten() {}
    override fun onAbandoned() {}
}

@Composable
fun StatusBanner() {
    // This poller has no retain callbacks, so retaining it throws.
    val poller = retain { StatusPoller() }
}
```

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
-->

```kotlin
class StatusPoller : RememberObserver {
    override fun onRemembered() {}
    override fun onForgotten() {}
    override fun onAbandoned() {}
}

@Composable
fun StatusBanner() {
    val poller = remember { StatusPoller() }
}
```

<!--- CLEAR -->

</td>
</tr>
</tbody>
</table>

### RetainUnitType

Reports a `retain` block that returns `Unit`. Running an action inside the calculation can make it happen before composition succeeds. Use `SideEffect(Unit)` for a one-time notification on successful entry, or `RetainedEffect` for work with cleanup tied to the lifetime of a retained value.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import java.awt.Toolkit
-->

```kotlin
@Composable
fun AnnounceEntry() {
    // The beep can run before composition commits.
    retain { Toolkit.getDefaultToolkit().beep() }
}
```

<!--- CLEAR -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.awt.Toolkit
-->

```kotlin
@Composable
fun AnnounceEntry() {
    SideEffect(Unit) { Toolkit.getDefaultToolkit().beep() }
}
```

<!--- KNIT example-static-analysis-retain-01.kt -->

</td>
</tr>
</tbody>
</table>

### RetainingDoNotRetainType

Reports a `retain` value or key of a type marked `@DoNotRetain`. Keys stay alive along with the retained value, so either can keep a shorter-lived resource alive after it should be released. Use `remember` when the object belongs to the current composition. For retained state, use a stable identifier as the key and keep references to shorter-lived resources out of the retained value.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.DoNotRetain
import androidx.compose.runtime.retain.retain
-->

```kotlin
@DoNotRetain("The editor owns this session.")
class EditorSession(val documentId: String)

class PreviewCache(val documentId: String)

@Composable
fun retainPreviewCache(session: EditorSession): PreviewCache =
    // Retaining this key can keep the editor session alive.
    retain(session) { PreviewCache(session.documentId) }
```

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.DoNotRetain
import androidx.compose.runtime.retain.retain
-->

```kotlin
@DoNotRetain("The editor owns this session.")
class EditorSession(val documentId: String)

class PreviewCache(val documentId: String)

@Composable
fun retainPreviewCache(session: EditorSession): PreviewCache {
    val documentId = session.documentId
    return retain(documentId) { PreviewCache(documentId) }
}
```

<!--- CLEAR -->

</td>
</tr>
</tbody>
</table>

### StaleRememberUpdatedStateInRemember

Reading the value from `rememberUpdatedState` while creating a remembered action can capture the original callback. Later compositions update the state while the action keeps calling the original callback. The rule reports eager reads in `remember`, `rememberSaveable`, and `retain` when the result stays cached as the source changes.

If the result must keep its identity, read the current callback when the action runs. If the result represents a calculation that should change with an input, use that input as a key.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
-->

```kotlin
@Composable
fun rememberDismissAction(onDismiss: () -> Unit): () -> Unit {
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    return remember { latestOnDismiss }
}
```

<!--- KNIT example-static-analysis-131.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
-->

```kotlin
@Composable
fun rememberDismissAction(onDismiss: () -> Unit): () -> Unit {
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    // Read the current callback when the stable action runs.
    return remember { { latestOnDismiss() } }
}
```

<!--- KNIT example-static-analysis-132.kt -->

</td>
</tr>
</tbody>
</table>

### StateFlowValueCalledInComposition

A label that reads `StateFlow.value` shows the value available on that composition pass. When the flow emits again, the read itself gives Compose no reason to update the label. The rule reports these reads during composition. Use `collectAsState()` to collect the flow while the composable is in the composition and expose the latest value as Compose state.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(status: StateFlow<String>, modifier: SwingModifier = SwingModifier) {
    // A later flow emission alone leaves the label unchanged.
    Label(text = status.value, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-133.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(status: StateFlow<String>, modifier: SwingModifier = SwingModifier) {
    val message by status.collectAsState()
    Label(text = message, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-134.kt -->

</td>
</tr>
</tbody>
</table>

### StateParam

A banner needs the message it displays. The caller can own the message, read its current value in composition, and pass a `String`. This lets the same banner display a constant or a value from any state source. The rule reports `State` parameters that couple the banner's API to how its input is stored.

When a component edits caller-owned state, give it a callback that reports the requested change. The caller still decides how to apply it.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun StatusBanner(message: State<String>, modifier: SwingModifier = SwingModifier) {
    Column(modifier) {
        Label("Sync status")
        Label(message.value)
    }
}
```

<!--- KNIT example-static-analysis-135.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
// Callers can pass a constant or read state before calling the banner.
@Composable
fun StatusBanner(message: String, modifier: SwingModifier = SwingModifier) {
    Column(modifier) {
        Label("Sync status")
        Label(message)
    }
}
```

<!--- KNIT example-static-analysis-136.kt -->

</td>
</tr>
</tbody>
</table>

### UnnecessaryComposable

The rule reports `@Composable` functions and getters that can run without composition. Remove the annotation from helpers such as a title formatter that only uses its arguments. A Swing listener or a unit test can then call the formatter directly. Keep it when a composable slot or an overridden declaration requires it.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
-->

```kotlin
@Composable
fun buildWindowTitle(project: String): String = "$project - Build status"
```

<!--- KNIT example-static-analysis-137.kt -->

</td>
<td markdown="block">

```kotlin
// A Swing listener can call the formatter directly.
fun buildWindowTitle(project: String): String = "$project - Build status"
```

<!--- KNIT example-static-analysis-138.kt -->

</td>
</tr>
</tbody>
</table>

### UnnecessaryLaunchedEffect

Use a keyed `SideEffect` to publish a status with a synchronous call. It runs after a successful composition when the effect enters or a key changes. The rule reports a `LaunchedEffect` whose work needs no coroutine when the runtime provides a keyed `SideEffect` overload.

Keep `LaunchedEffect` for work that suspends or needs cancellation, such as collecting a flow. Some synchronous APIs depend on coroutine scheduling. Use `allowedCallReceiverTypes` or `allowedCallNames` to keep those calls in `LaunchedEffect`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
-->

```kotlin
@Composable
fun PublishStatus(status: String, publish: (String) -> Unit) {
    LaunchedEffect(status, publish) {
        publish(status)
    }
}
```

<!--- KNIT example-static-analysis-139.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
-->

```kotlin
@Composable
fun PublishStatus(status: String, publish: (String) -> Unit) {
    // Publish after a successful composition on entry or when either key changes.
    SideEffect(status, publish) {
        publish(status)
    }
}
```

<!--- KNIT example-static-analysis-140.kt -->

</td>
</tr>
</tbody>
</table>

### VarsWithoutStateBacking

A button click can increment a regular local counter while the displayed count stays stale. Another recomposition can create a new counter starting at zero. The rule reports mutable locals in composable scopes that lack observable state backing.

Use a state delegate such as `var count by state` when the caller supplies the state. For a local counter, use `remember { mutableIntStateOf(0) }` so the value survives recomposition and writes update the label. Move temporary mutation for a calculation into a regular function.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun Counter(modifier: SwingModifier = SwingModifier) {
    // A click can change this counter without refreshing the button text.
    var count = 0
    Button(count.toString(), onClick = { count++ }, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-141.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
-->

```kotlin
@Composable
fun Counter(modifier: SwingModifier = SwingModifier) {
    var count by remember { mutableIntStateOf(0) }
    Button(count.toString(), onClick = { count++ }, modifier = modifier)
}
```

<!--- KNIT example-static-analysis-142.kt -->

</td>
</tr>
</tbody>
</table>

## AndroidX annotation rules

Rule-set ID `androidx-annotations`.

### ArgumentOutsideDeclaredRange

Range and size annotations tell callers what a parameter accepts. The rule checks known values or declared ranges against `@IntRange` and `@FloatRange` and checks known sizes against `@Size`. It reports out-of-bounds arguments and numeric ranges that no value can satisfy. Test sources are exempt.

Pass a value within the declared bounds or correct the annotation if the API accepts a different range. Check user-provided data at runtime before calling an API that expects a bounded value.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.annotation.IntRange
-->

```kotlin
fun progressText(@IntRange(from = 0, to = 100) percent: Int): String = "$percent%"
// The annotation lets Detekt flag a call that would return "120%".
fun taskStatus(): String = progressText(120)
```

<!--- KNIT example-static-analysis-143.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.annotation.IntRange
-->

```kotlin
fun progressText(@IntRange(from = 0, to = 100) percent: Int): String = "$percent%"
fun taskStatus(): String = progressText(85)
```

<!--- KNIT example-static-analysis-144.kt -->

</td>
</tr>
</tbody>
</table>

### RestrictedApi

The rule reports references outside the library, test, or subclass scope named by `@RestrictTo`. A helper reserved for subclasses can assume those callers maintain the object's invariants. A regular caller should use the supported operation, which in this example checks a status before storing it. Detekt checks `LIBRARY_GROUP` and `LIBRARY_GROUP_PREFIX` at the declaring library boundary, like `LIBRARY`.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.annotation.RestrictTo
-->

```kotlin
open class StatusHistory {
    private val entries = mutableListOf<String>()

    fun record(status: String) {
        require(status.isNotBlank())
        appendUnchecked(status)
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    fun appendUnchecked(status: String) {
        entries.add(status)
    }
}

class StatusReporter {
    fun reportReady(history: StatusHistory) {
        // Calling the helper directly bypasses the validation in record.
        history.appendUnchecked("Ready")
    }
}
```

<!--- KNIT example-static-analysis-145.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.annotation.RestrictTo
-->

```kotlin
open class StatusHistory {
    private val entries = mutableListOf<String>()

    fun record(status: String) {
        require(status.isNotBlank())
        appendUnchecked(status)
    }

    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    fun appendUnchecked(status: String) {
        entries.add(status)
    }
}

class StatusReporter {
    fun reportReady(history: StatusHistory) {
        history.record("Ready")
    }
}
```

<!--- KNIT example-static-analysis-146.kt -->

</td>
</tr>
</tbody>
</table>

### VisibleForTests

`@VisibleForTesting` can let tests inspect how a value is stored while production callers use the public operation. The rule reports production references outside the intended visibility named by `otherwise`. The default is private to the declaring class or file. `PACKAGE_PRIVATE` allows the package, `PROTECTED` allows the declaring class and subclasses, and `NONE` reserves the declaration for tests.

Use the supported operation in production so the stored representation can change. If production callers need the underlying member, make it part of the intended API.

<table>
<thead>
<tr><th scope="col">&#x274C; Reported</th><th scope="col">&#x2705; Instead</th></tr>
</thead>
<tbody>
<tr>
<td markdown="block">

<!--- INCLUDE
import androidx.annotation.VisibleForTesting
-->

```kotlin
class StatusStore(status: String) {
    @VisibleForTesting
    val rawStatus: String = status

    fun displayStatus(): String = rawStatus.trim().ifEmpty { "Idle" }
}

fun statusText(store: StatusStore): String = store.rawStatus
```

<!--- KNIT example-static-analysis-147.kt -->

</td>
<td markdown="block">

<!--- INCLUDE
import androidx.annotation.VisibleForTesting
-->

```kotlin
class StatusStore(status: String) {
    @VisibleForTesting
    val rawStatus: String = status

    fun displayStatus(): String = rawStatus.trim().ifEmpty { "Idle" }
}

// Production callers get "Idle" when the stored status is blank.
fun statusText(store: StatusStore): String = store.displayStatus()
```

<!--- KNIT example-static-analysis-148.kt -->

</td>
</tr>
</tbody>
</table>
