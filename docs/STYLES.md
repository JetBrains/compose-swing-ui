# Styles: concepts and authoring

A `SwingStyle` sets the properties of one component: its colors, its text, its sizes. This page explains what a style is, when it runs, and how to accept one in your own component and define the properties and nested-style keys it uses. Listeners, a component's place in its parent, and decoration stay in a `SwingModifier`; see [`MODIFIERS.md`](MODIFIERS.md). Building the component itself is covered in [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

<!--- INCLUDE .*styles-01.*
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.JComponent
-->

## What a style is

A style is a block that sets properties by calling setters. A setter compiles in a style when the style's scope is the setter's receiver type or a subtype of it. Several kinds of component can share one scope, and a custom scope can extend another. For example, `stars` (declared in [Declaring a property](#declaring-a-property)) compiles only in a style whose scope extends `RatingStyleScope`. A block nested inside a style cannot call the outer block's setters by accident.

```kotlin
SwingStyle<RatingStyleScope> { stars(5) }
```

<!--- CLEAR -->

The companion object `SwingStyle` is the empty style, which sets nothing. It is the default of every `style` parameter.

A component that supports styles takes a `style: SwingStyle<S> = SwingStyle` parameter next to `modifier`. `S` holds the setters that are valid for that component. A setter compiles when `S` is its receiver type or a subtype of it.

A style runs when the component is applied, on the event dispatch thread, and never in composition. It sets properties and reads state. It does not call composables or start work, and it must not throw: an exception there stops the composition (see [Failure](MODIFIERS.md#failure)).

A property the style sets is written onto the component. A property the style stops setting gets back the value the component held before the style wrote it.

## Style or modifier

A property of the component goes in the style: a color, a font, a text, an icon, a size. A modifier keeps everything that is not a property of the component:

- listeners, and anything that registers something on the component;
- parent data and layout declarations, such as a `BorderLayout` region or a weight;
- decoration and drawing;
- `key`, `testTag`, `clientProperty` and `name`, which are metadata about the component.

Each property has one public writer. Where a style and a modifier write the same property, the `style` parameter writes after the modifier, so its value is the one in effect; a `styleable` in the modifier writes at its position. A modifier that mixes both is split: the properties go to `style`, and the listeners stay in `modifier`.

## Declaring a property

A `SwingStyleProperty<V>` is a property a style can set. Build one once, as a top-level `val`, and write a setter as an extension on the scope that accepts it. A property applied to a component its descriptor does not serve is refused with an error when the style is applied.

`stylePropertyOf(descriptor)` takes the `ComponentPropertyDescriptor` that `SwingModifier.property` takes, and the same options: `restores`, `rewriteOn` and `alsoOverwrites` mean here what they mean there. A style property and a `property` declaration with the same descriptor name share what the component held before either wrote it.

```kotlin
class RatingComponent : JComponent() {
    var stars: Int = 0
        set(value) {
            field = value
            repaint()
        }
}

private val StarsProperty =
    stylePropertyOf(
        ComponentPropertyDescriptor<RatingComponent, Int>(
            name = "stars",
            read = { it.stars },
            write = { component, value -> component.stars = value },
        ),
    )

interface RatingStyleScope : ComponentStyleScope

fun RatingStyleScope.stars(count: Int): Unit = StarsProperty.provide(count)
```

<!--- KNIT example-styles-01.kt -->

`provide` is callable from any scope, so keep the property private and expose a setter. A property that several unrelated components share, such as an icon, gets one setter on the scope of each component that accepts it, all over one `SwingStyleProperty`.

### A property a descendant inherits

`styleLocalOf(descriptor)` builds a property in the same way, for a value a container passes on to its descendants. A component that sets no value takes the value of the nearest styled ancestor that set one, if the component is of a type the descriptor accepts. For a descriptor on `JComponent`, that is every `JComponent`. An ancestor that sets `null` stops the inheritance at that container: its descendants keep their own value, the one the look and feel gave them.

<!--- INCLUDE .*styles-02.*
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.styleLocalOf
import javax.swing.JComponent
-->

```kotlin
private val HintProperty =
    styleLocalOf(
        ComponentPropertyDescriptor<JComponent, String?>(
            name = "toolTipText",
            read = { it.toolTipText },
            write = { component, value -> component.toolTipText = value },
        ),
    )

fun ComponentStyleScope.hint(text: String?): Unit = HintProperty.provide(text)
```

<!--- KNIT example-styles-02.kt -->

A local a component sets applies to that component, and it also reaches the styled components declared inside it.

### A property written by an element

Use `stylePropertyOf(::CustomElement)` for a property implemented by a custom modifier element. See [Writing a custom property element](MODIFIERS.md#writing-a-custom-property-element) for its contract.

<!--- INCLUDE .*styles-03.*
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.JComponent
-->

```kotlin
private class AccessibleNameNode : SwingModifier.ComponentNode<JComponent>() {
    private var original: String? = null

    override fun onAttach() {
        original = component.accessibleContext.accessibleName
    }

    override fun onDetach() {
        component.accessibleContext.accessibleName = original
    }
}

private data class AccessibleNameElement(
    private val text: String?,
) : SwingModifier.NodeElement<JComponent, AccessibleNameNode>() {
    override val name: String get() = "accessibleName"

    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): AccessibleNameNode = AccessibleNameNode()

    override fun update(node: AccessibleNameNode) {
        node.component.accessibleContext.accessibleName = text
    }
}

private val AccessibleNameProperty = stylePropertyOf(::AccessibleNameElement)

fun ComponentStyleScope.accessibleName(text: String?): Unit = AccessibleNameProperty.provide(text)
```

<!--- KNIT example-styles-03.kt -->

## Components that take a style

To give your own component a `style` parameter, pass it to `styleable` on the modifier of its `SwingNode`. `styleable` takes a `SwingStyleKind`, which says how the component is styled:

- `scope` builds the scope a style of this kind runs against. It receives a `NestedStyleProviderScope`; delegate to it, and `provide`, `currentValue` and `applyNestedStyle` are available in your scope;
- `default` is the style applied before the caller's own. A kind that applies nested styles applies their keys there.

Declare one kind per component kind, as a top-level `val`. The style applies after the caller's `modifier`, so it wins over a modifier element that sets the same property.

The component below applies two keys, a broad one that every component can apply and its own. `Everything` is a `NestedStyleKey` over `ComponentStyleScope`, and `Ratings` one over `RatingStyleScope`; [Nested styles](#nested-styles) explains them. A kind whose default only applies nested styles can also be declared with `nestingStyleKind` instead of the `SwingStyleKind` constructor.

<!--- INCLUDE .*styles-04.*
import androidx.compose.runtime.Composable
import example.exampleStyles01.RatingComponent
import example.exampleStyles01.RatingStyleScope
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.SwingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JPanel
-->

```kotlin
val Everything: NestedStyleKey<ComponentStyleScope> = NestedStyleKey("Everything")

val Ratings: NestedStyleKey<RatingStyleScope> = NestedStyleKey("Ratings")

private val RatingKind =
    SwingStyleKind<RatingStyleScope, Unit>(
        scope = { base, _ -> object : RatingStyleScope, ComponentStyleScope by base {} },
        default = {
            applyNestedStyle(Everything)
            applyNestedStyle(Ratings)
        },
    )

@Composable
fun Rating(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<RatingStyleScope> = SwingStyle,
) {
    SwingNode(
        factory = { RatingComponent() },
        modifier = modifier.styleable(RatingKind, style),
    )
}
```

To let a component provide nested styles to the components in its content, make its scope extend `NestedStyleProviderScope`, which adds `provideNestedStyle`, and declare it with the `content` overload of `SwingNode`. A component built without a style declaration receives no nested styles from its ancestors.

```kotlin
interface SectionStyleScope : NestedStyleProviderScope

private val SectionKind =
    SwingStyleKind<SectionStyleScope, Unit>(
        scope = { base, _ -> object : SectionStyleScope, NestedStyleProviderScope by base {} },
    )

@Composable
fun Section(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SectionStyleScope> = SwingStyle,
    content:
        @Composable @SwingComposable
        () -> Unit,
) {
    SwingNode(
        factory = { JPanel() },
        modifier = modifier.styleable(SectionKind, style),
        content = content,
    )
}
```

<!--- KNIT example-styles-04.kt -->

A composable with a style parameter declares it directly after `modifier`.

### Following state the component feeds

A kind whose scope reads a value the component owns - whether it is playing, checked or selected - takes that value as its state type `T`. Pass it to `styleable` as the `state` argument, and read it in the scope. The holder's type is the kind's `T`. For a `Boolean`, use `T = Boolean` and pass `playing`.

The style reads the value inside its block, where the read is observed: a change runs the styles that read it, and only those, with no recomposition. A style that does not read it is not run.

<!--- INCLUDE .*styles-05.*
import androidx.compose.runtime.Composable
import example.exampleStyles03.accessibleName
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.SwingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JPanel
-->

```kotlin
interface PlayerStyleScope : ComponentStyleScope {
    val isPlaying: Boolean
}

private val PlayerKind =
    SwingStyleKind<PlayerStyleScope, Boolean>(
        scope = { base, playing ->
            object : PlayerStyleScope, ComponentStyleScope by base {
                override val isPlaying: Boolean get() = playing
            }
        },
    )

@Composable
fun Player(
    playing: Boolean,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<PlayerStyleScope> = SwingStyle,
) {
    SwingNode(
        factory = { JPanel() },
        modifier = modifier.styleable(PlayerKind, playing, style),
    )
}

@Composable
fun Controls(playing: Boolean) {
    Player(playing, style = { accessibleName(if (isPlaying) "Playing" else "Paused") })
}
```

<!--- KNIT example-styles-05.kt -->

## Nested styles

A component can provide a style to the descendants that apply a key, at any depth. Call `provideNestedStyle(key, style)` in the style of a component whose scope extends `NestedStyleProviderScope`. The block runs against the scope of the descendant that applies the key, so it can call every setter that descendant's scope has, and it reads the descendant's own state.

Calling `provideNestedStyle` inside a `provideNestedStyle` block is refused; provide the inner key from the component's own style instead.

<!--- INCLUDE .*styles-06.*
import androidx.compose.runtime.Composable
import example.exampleStyles01.stars
import example.exampleStyles03.accessibleName
import example.exampleStyles04.Everything
import example.exampleStyles04.Rating
import example.exampleStyles04.Ratings
import example.exampleStyles04.Section
-->

```kotlin
@Composable
fun RatedSection() {
    Section(
        style = {
            provideNestedStyle(Everything) { accessibleName("rated") }
            provideNestedStyle(Ratings) { stars(5) }
        },
    ) {
        Rating()
        Rating(style = { stars(2) })
    }
}
```

<!--- KNIT example-styles-06.kt -->

The first `Rating` shows five stars and the second two: a descendant's own style is applied after what its ancestors provided. Both have the accessible name `rated`. The `Section` itself gets neither: a provided style does not apply to the container that provides it.

- A provided style reaches a descendant only where its kind applies the key. A style whose scope does not extend the key's scope cannot apply it: the call does not compile.
- Several ancestors that provide one key all apply, farthest first, so the nearest wins for each property.
- A kind's `default` style applies its keys in the order it calls `applyNestedStyle`, and a later key wins over an earlier one. `RatingKind` applies `Everything` and then `Ratings`, so a rule for ratings wins over a rule for everything, whatever the distance between the providers and the rating.
- A provided block runs for each descendant that applies the key, not for the component that provides it, so a state the block reads reruns only the styles of those descendants. State read to build or conditionally provide a block updates the provider. Changing a provider property leaves consumers of unchanged nested blocks alone.
- A composition local read through `currentValue` inside a nested block comes from where its provider was declared. Changes there update the consumers, even when they override that local.

### A key accepts only its own scope

A key declared for `RatingStyleScope` accepts, from `provideNestedStyle`, a `SwingStyle<RatingStyleScope>` and nothing narrower. A `SwingStyle<RatingStyleScope>` provided for `Everything` does not compile.

A style kind can apply a key declared for its own scope or for any scope it extends. A `RatingStyleScope` can apply `Everything`, and a `SectionStyleScope` cannot apply `Ratings`.

## Resolution order and precedence

A component's properties take their values in this order, and the last write of a property wins:

1. the component's default style, in the order it is written. Where the default style applies a key, the styles ancestors provided for that key apply at that point, farthest provider first;
2. the style the caller passes;
3. each style local that an ancestor set, that the component has, and that the component's own style did not set: the value of the nearest ancestor that set it. A value of `null` is not applied.

The component's own style therefore always wins over inheritance. A property the caller's style stops setting, while an ancestor still publishes it, takes the inherited value, not its original one.

A style reaches everything composed inside the component that declares it, as in androidx. The cells a list renders, a spinner editor, a tab header, a popup or context menu, a secondary window, a window menu bar, a tray menu and a `setContent(parent = ...)` composition do not receive it: declare the shared style again at their root.

## When a style runs

A style runs again, and only then, when:

- the component attaches;
- the component declares an unequal style, a different `style` instance, a different kind or a different state holder, on a later pass;
- Snapshot state or a dynamic composition local read by the style changes, or an inherited style value changes.
- the component, or a component above it, moves under another parent, or a style is added above it;
- a static composition local where the component was declared changes its value, or a local starts or stops being provided there. A modifier that reads composition locals follows the same rule: see [Node capabilities](MODIFIERS.md#node-capabilities).

A recomposition that declares an equal style does not run it, and neither does a frame. Two styles are equal when the same `kind` and the same state instance come with an equal `style`. A style that is not equal runs again. A property whose value did not change can still be written again, so that the declaration order keeps holding; a setter must accept being called again with the value it already wrote. To keep a style equal across recompositions, keep it in a top-level `val` or `remember` it. Read the state inside the style, as in the `Player` example above, rather than reading it in composition and capturing the value: the style then runs on a change of the state, with no recomposition and no new instance.

## Restore

When a style stops setting a property, or the component is released, reused or deactivated, the property's restore policy determines its restored value. By default, this is its value before styling.

<!--- INCLUDE .*styles-07.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import example.exampleStyles01.stars
import example.exampleStyles04.Rating
-->

```kotlin
@Composable
fun CompactRating() {
    val compact = remember { mutableStateOf(false) }
    Rating(style = { if (compact.value) stars(1) })
}
```

<!--- KNIT example-styles-07.kt -->

When a modifier and a style set the same property, its original value is restored after both stop setting it, subject to its restore policy. See [Modifiers](MODIFIERS.md) for `restores`, `rewriteOn` and `alsoOverwrites`.
