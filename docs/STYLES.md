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

To style a composable that does not take a `style` parameter, put `SwingModifier.styleable { ... }` in its modifier. The block offers every `Component` and `JComponent` property plus `provideNestedStyle`. To keep a style in a variable, type it `SwingStyle<ContainerStyleScope>`. For an AWT component outside the `JComponent` hierarchy, use the overload that takes a `SwingStyleKind`.

Multiple `styleable` declarations are allowed in a modifier and apply in modifier order. Each declaration applies its kind's default, then its own style. A later declaration wins when it sets the same property; removing it reveals the earlier declaration's value. Removing the final declaration restores the value held before styling. Ordinary modifier elements between style declarations keep their place between them.

A component's `style` parameter is applied after its modifier declarations. Each declaration provides its nested styles and locals to descendants, not to sibling declarations on the same component. Providers declared at the same component are applied in declaration order; later providers win for overlapping nested properties and locals. A parent-layout declaration from a style follows the component's modifier parent-layout declarations; other keyed modifier elements keep their declared order.

A style runs when the component is applied, on the event dispatch thread, and never in composition. It sets properties and reads state. It does not call composables or start work, and it must not throw: an exception there stops the composition (see [Failure](MODIFIERS.md#failure)).

A property the style sets is written onto the component. A property the style stops setting gets back the value the component held before the style wrote it.

## Style or modifier

A property of the component goes in the style: a color, a font, a text, an icon, a size. A modifier keeps everything that is not a property of the component:

- listeners, and anything that registers something on the component;
- parent data and layout declarations, such as a `BorderLayout` region or a weight;
- decoration and drawing;
- `key`, `testTag`, `clientProperty` and `name`, which are metadata about the component.

Each property has one public writer. Where a style and a modifier write the same property, the `style` parameter writes after the modifier, so its value is the one in effect; a `styleable` in the modifier writes at its position. A modifier that mixes both is split: the properties go to `style`, and the listeners stay in `modifier`.

Some names exist in both and mean different things:

- `background(color)` is the style setter that writes the component's Swing `background`, the one a look and feel honors. `SwingModifier.background(color, shape)` and `background(brush, shape)` in `swing-ui-foundation` are decoration: they paint under the content and need a shape.
- `width`, `height` and `size` are style setters that set the component's actual size. Inside the content of a layout composable, for example `Box`, `SwingModifier.width(w)` is the layout constraint instead: it constrains the measured size and sets nothing on the component. Outside a layout scope it does not compile.

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

Keep the property private and expose only the setter, because `provide` can be called from any scope. Declare the setter as an extension on the [type scope](#type-scopes) of the components that accept it, so it compiles only in their styles.

### A property several components share

An element names one component type, which is enough whenever the components that share a property also share a class that declares it. Some properties are not like that: `iconTextGap` is declared separately by `JLabel` and by `AbstractButton`, and the class between them declares neither accessor.

Build the descriptor with one `accessor` per component type. A value read through one accessor is restored through that same accessor. The descriptor refuses a component no accessor serves, with a message that names the types it serves.

Then declare the setter once on the scope of each component that accepts the property, all over the one `SwingStyleProperty`. This is a union setter. A style of a component outside the union does not compile:

<!--- INCLUDE .*styles-union-01.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor.Companion.accessor
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.AbstractButton
import javax.swing.JLabel
-->

```kotlin
private val GapProperty =
    stylePropertyOf(
        ComponentPropertyDescriptor(
            "iconTextGap",
            accessor<JLabel, Int>(read = { it.iconTextGap }, write = { label, gap -> label.iconTextGap = gap }),
            accessor<AbstractButton, Int>(read = { it.iconTextGap }, write = { button, gap -> button.iconTextGap = gap }),
        ),
    )

fun LabelStyleScope.gap(pixels: Int): Unit = GapProperty.provide(pixels)

fun ButtonStyleScope.gap(pixels: Int): Unit = GapProperty.provide(pixels)

@Composable
fun Gaps() {
    Label("Label", style = { gap(4) })
    Button("Button", onClick = {}, style = { gap(4) })
    // TextField("", onValueChange = {}, style = { gap(4) }) does not compile
}
```

<!--- KNIT example-styles-union-01.kt -->

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

A property that is more than a read and a write - a border, a caret, a listener-backed value - is written by a `NodeElement`. `stylePropertyOf { value -> element }` takes a function that builds the element for a value. The element decides the property's key, equality, target type and restore, as it does in a modifier. An element-backed property takes no restore options, because the element's `restores` policy says them.

Write a `NodeElement` and `ComponentNode` pair when you need a wider target type, several components written from one element, or a registration that is not a property:

- `NodeElement<T : Component, N : ComponentNode<T>>` is the immutable description: the value to write and the component type it targets. It implements `equals` and `hashCode` over what it declares; see [Equality and skipping](MODIFIERS.md#equality-and-skipping).
- `ComponentNode<T : Component>` holds the per-component state, such as the captured original value. It exposes the already-typed `component`.

An element must be a `NodeElement` or a parent declaration, and not both; an element of your own that is neither is refused when it is applied.

`targetType` is the `Class` of the element's `T`: the most general component type the property needs, such as `Component::class.java` for any AWT component, `JComponent::class.java` for an accessible name or a border, or a widget class for a widget-only property. The node's `component` is then typed `T`, and a component that is not a `T` is rejected when the element is applied.

What each callback of the pair is for (see [Node lifecycle](MODIFIERS.md#node-lifecycle) for when each runs):

- `NodeElement.create()` builds the node;
- `ComponentNode.onAttach()` - **capture the component's existing value here** so it can be restored;
- `NodeElement.update(node)` - **write the new value here**. A composition local read here runs it again (see [Node capabilities](MODIFIERS.md#node-capabilities));
- `ComponentNode.onReset()` - **drop state tied to the old content here**;
- `ComponentNode.onDetach()` - **restore the captured original here**: every property your write changed, not only the one the element is named for - a whole geometry covers each axis it spans - and list them all in `NodeElement.heldProperties`. Where you cannot restore them all, declare `NodeElement.restores`; see `RestorePolicy` for each option.

The element below targets `JComponent`, so the node reads its already-typed `component` without casting:

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

    override val declaredValues: Map<String, Any?> get() = mapOf("text" to text)

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

`name` and `declaredValues` are shown in error messages and in inspection tools (see [`INSPECTING-COMPOSITIONS.md`](INSPECTING-COMPOSITIONS.md#reading-the-chain-a-component-carries)). By default `name` is the class name and `declaredValues` is empty; override both when the element carries a value worth showing. `key`, not `name`, decides which slot an element takes. Leave a callback out of `declaredValues` unless showing it says something - a lambda renders as its class.

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

To let a component provide nested styles to the components in its content, make its scope extend `NestedStyleProviderScope`, which adds `provideNestedStyle`, and declare it with the `content` overload of `SwingNode`.

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

Type the `style` parameter as `SwingStyle<S>` with the component's own scope `S`, not a sub-interface of it. A style written for a scope passes to every component whose scope extends it: one written for `ButtonStyleScope` passes to a `Button`, a `CheckBox` and a `RadioButton` alike.

### Following state the component feeds

A kind whose scope reads a value the component owns - whether it is playing, checked or selected - takes that value as its state type `T`. Pass it to `styleable` as the `state` argument, and read it in the scope. The holder's type is the kind's `T`. For a `Boolean`, use `T = Boolean` and pass `playing`.

Changing the plain `playing` input recomposes the caller and runs the style again, even if the style does not read `isPlaying`. For updates without recomposition, pass a remembered snapshot-state holder as `T` and read its value inside the style. A change to snapshot state runs only the styles that read it.

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

## Type scopes

A component's scope offers the setters of its own Swing type and of every Swing supertype. Some scopes are shared: several component types use them.

See the KDoc for each scope and its setters.

Each component's style parameter names the scope it takes. `ComponentStyleScope` and `SwingComponentStyleScope` are separate because a style applied through `AllComponents` also reaches a component that is not a `JComponent`, where setting `border` would fail.

Every scope interface can be extended: a third-party button's scope extends `ButtonStyleScope` and applies `Buttons`, the same way a library button does. A scope that a component feeds a state to declares one read-only value for it, such as `isChecked`; see [State blocks](#state-blocks).

A setter valid on unrelated types is declared once on each type's scope, as in [A property several components share](#a-property-several-components-share).

### Reusing a style

A style is a value that can be kept in a `val` and combined. `first then second` runs `first` and then `second`, so where both set a property, `second` wins. `SwingStyle(first, second, third)` combines several in the same order. A style written for a scope also combines with styles of a scope below it.

```kotlin
val Card = SwingStyle<ComponentStyleScope> { background(card) }

@Composable
fun CardLabel(text: String, style: SwingStyle<LabelStyleScope> = SwingStyle) {
    Label(text, style = Card then style)
}

Label("Title", style = Card then { foreground(ink) })
```

Inside a block, `apply(style)` runs another style at that point.

A style that takes arguments is a builder function: an extension on the narrowest scope that accepts all its setters, called from any style of that scope or of a scope below it.

```kotlin
fun ComponentStyleScope.surface(fill: Color, ink: Color) {
    background(fill)
    foreground(ink)
}

Panel(style = { surface(card, ink) }) { /* ... */ }
Label("Title", style = { surface(card, ink) })
```

<!--- CLEAR -->

## State blocks

A component declared with a `checked` or `selected` value, such as `CheckBox` or `ToggleButton`, lets its style set properties for that state. A block runs while the component is declared in that state:

```kotlin
CheckBox(
    "Bold when checked",
    checked = agreed,
    onCheckedChange = { agreed = it },
    style = { checked { font(bold) } },
)
```

<!--- CLEAR -->

`checked { }` is available in `CheckableStyleScope` and `selected { }` in `SelectableStyleScope`. Each is named after the parameter the composable takes. The value is the one the caller declared, never the widget's own `isSelected`: a click the caller declines changes nothing, and no style runs for it. Changing the declared Boolean input recomposes the caller and runs the component's style again, even if it does not read `isChecked` or `isSelected`. Snapshot state read inside a style updates only the styles that read it, without recomposition.

A block also works in a nested style, and it runs with the state of the descendant that applies it, not the provider's. This style bolds only the boxes that are checked:

```kotlin
Panel(style = { provideNestedStyle(CheckBoxes) { checked { font(bold) } } }) {
    CheckBox("a", a, { a = it })
    CheckBox("b", b, { b = it })
}
```

<!--- CLEAR -->

- The look and feel honors the font, the foreground, the tooltip and the per-state icons set in a block. FlatLaf replaces the foreground of a selected button in a toolbar. A `background` set in a block compiles, but the look and feel may paint over it.
- `checked { }` in the style of a `Label` or a `Button`, and `selected { }` in the style of a `CheckBox`, do not compile.
- A style written for `ButtonStyleScope` still passes where a `CheckableStyleScope` is expected, and only a block that reads the state needs the narrower scope.
- A style has a state block only when its scope implements `CheckableStyleScope` or `SelectableStyleScope`. A scope that also extends `NestedStyleProviderScope` can have one too; the blocks of the nested styles it provides run with the state of the descendants.
- There are no blocks for pressed, rollover or focus, and none for disabled. Every look and feel paints those states itself and would paint over a write, so Swing's per-state slots, such as `pressedIcon` and `disabledTextColor`, are ordinary setters. `enabled` is itself a style property, so a `disabled { }` block would read the value the same style is still writing.

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

- For a key other than `AllComponents`, a provided style reaches a descendant when the descendant's kind applies that key. The kind's scope must extend the key's scope.
- Several ancestors that provide one key all apply, farthest first, so the nearest wins for each property.
- A kind's `default` style applies its keys in the order it calls `applyNestedStyle`, and a later key wins over an earlier one. `RatingKind` applies `Everything` and then `Ratings`, so a rule for ratings wins over a rule for everything, whatever the distance between the providers and the rating.
- A provided block runs for each descendant that applies the key, not for the component that provides it, so a state the block reads reruns only the styles of those descendants.
- A composition local read through `currentValue` inside a nested block comes from where its provider was declared. Changes there update the consumers, even when they override that local.

### Keys of the library components

Every component receives a style that an ancestor provides for `AllComponents`, whatever its kind and whether or not it declares a style, before its kind's default. A component receives a style provided for another key only when its kind applies that key. The library's components apply the key of their kind, so a style provided for a kind wins over one provided for `AllComponents`, whatever the distance between the two providers. `AllComponents` styles set only what every component has.

A custom component applies its own keys through its `SwingStyleKind`, with `applyNestedStyle`.

Choose a key whose scope offers the setters the shared style needs. The key's KDoc describes its scope.

A block provided for a key offers the setters of the key's scope. An existing style written for that scope or for one of its supertypes can be provided too; a style written for a narrower scope does not compile.

### Styling a section of library components

```kotlin
Panel(
    style = {
        provideNestedStyle(AllComponents) { enabled(false) }
        provideNestedStyle(Labels) { background(card) }
    },
) {
    Label("disabled, on the card color")
    Button("disabled; its face keeps the look and feel's color", onClick = {})
    Label("own style wins", style = { enabled(true) })
}
```

<!--- CLEAR -->

- A provided style reaches every component of that kind below the provider, at any depth. It does not apply to the provider itself: the `Panel` above stays enabled.
- A descendant's own style wins over every nested style and over its component's default, so the last `Label` stays enabled. So does any component under a provider of its kind key that writes `enabled(true)`, however far that provider is: `provideNestedStyle(Buttons) { enabled(true) }` on any ancestor keeps the buttons of a section that `AllComponents` disabled.
- A nearer provider of the same key writes over a farther one per property, and nothing masks a provider. To undo a farther value, a nearer provider writes the opposite one.
- `Label(style = { provideNestedStyle(Labels) { } })` does not compile: a `Label` has no content to style.
- A nested block that reads state, such as `provideNestedStyle(Labels) { background(if (dark) a else b) }`, recomposes nothing when the state changes. Only the components the block styles are updated.

### A key accepts only its own scope

A key declared for `RatingStyleScope` accepts, from `provideNestedStyle`, a `SwingStyle<RatingStyleScope>` and nothing narrower. A `SwingStyle<RatingStyleScope>` provided for `Everything` does not compile.

A style kind can apply a key declared for its own scope or for any scope it extends. A `RatingStyleScope` can apply `Everything`, and a `SectionStyleScope` cannot apply `Ratings`.

## Resolution order and precedence

The last write of a property wins. Values are declared in this order:

1. the component's default style, in the order it is written. Where the default style applies a key, the styles ancestors provided for that key apply at that point, farthest provider first;
2. the style the caller passes;
3. each style local that an ancestor set, that the component has, and that the component's own style did not set: the value of the nearest ancestor that set it. A value of `null` is not applied.

The component's own style therefore always wins over inheritance. A property the caller's style stops setting, while an ancestor still publishes it, takes the inherited value, not its original one.

A nested style counts as the component's own style, so it beats an inherited value. A `foreground` provided for `AllComponents` by a far ancestor wins over the `foreground` that a nearer container publishes for inheritance. Every property declared with `styleLocalOf` is inherited. Among the built-in setters these are `foreground`, `font` and `componentOrientation`. Every other property reaches a descendant only through a key.

A style reaches everything composed inside the component that declares it, as in androidx. The cells a list renders, a spinner editor, a tab header, a popup or context menu, a secondary window, a window menu bar, a tray menu and a `setContent(parent = ...)` composition do not receive it: declare the shared style again at their root.

## When a style runs

A style runs again, and only then, when:

- the component attaches;
- the component declares an unequal style, a different `style` instance, a different kind or a different state holder, on a later pass;
- a snapshot state the style reads changes, including the styles, keys and locals a styled ancestor publishes, or a dynamic composition local the style reads through `currentValue`;
- the component, or a component above it, moves under another parent, or a style is added above it;
- a static composition local where the component was declared changes its value, or a local starts or stops being provided there. A modifier that reads composition locals follows the same rule: see [Node capabilities](MODIFIERS.md#node-capabilities).

A recomposition that declares an equal style does not run it, and neither does a frame. Two styles are equal when the same `kind` and the same state instance come with an equal `style`. A style that is not equal runs again. A property whose value did not change can still be written again, so that the declaration order keeps holding; a setter must accept being called again with the value it already wrote. To keep a style equal across recompositions, keep it in a top-level `val` or `remember` it. Read snapshot state inside the style to update it without recomposition or a new style instance. The `Player` example instead takes a plain Boolean input: changing it recomposes the caller and runs the style again, even when the style instance is unchanged and does not read `isPlaying`.

## Restore

The first time a style sets a property, the component's value for it is captured. A property that leaves the style is set back to that captured value, and so is every property of the style when the component is released, reused or deactivated. The captured value is the one the component showed before the style wrote it, not a default that the style knows.

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

While `compact` is `true` the rating shows one star. When it turns `false`, `stars` is no longer set, and the component gets back the value it held before the style wrote it. Nothing recomposed to do this: the style read `compact`, so the change ran it.

The restore options of the property govern it, as they do for `property` in a modifier: `restores` names the value the property held when the style found it, and `rewriteOn` and `alsoOverwrites` cover a look and feel that derives other properties from the write. A property that a modifier and a style both write gets its original back once neither writes it. Where both rewrite on one property, the style writes last, so its value is the one in effect.

## Properties that do not cascade

Among the built-in setters, only `foreground`, `font` and `componentOrientation` are style locals, and a descendant inherits them. Everything else reaches a descendant through a nested style that names the kinds it styles:

- **`enabled`.** A disabled section is `provideNestedStyle(AllComponents) { enabled(false) }`. It disables every descendant but not the container that provides it, and a descendant whose own style says `enabled(true)` stays enabled.
- **`cursor`.** Set it on the container: AWT shows the container's cursor over every child that sets none. A text component keeps its own text cursor, because its look and feel installs one.
- **`background`.** A card surface names the kinds it colors: `provideNestedStyle(Labels) { background(card) }`. Buttons, text fields and lists keep their look-and-feel faces unless the style names `Buttons` and the others: a look and feel such as FlatLaf paints an explicitly set background as the face of a button, so a background for every component would recolor them.

## A third-party widget

A widget outside the library has the same means as a library component, through the public API only. For the `Rating` above it has:

1. a `ComponentPropertyDescriptor` and a `stylePropertyOf` for each property of the widget, and a setter on its own scope: `stars` on `RatingStyleScope`;
2. a scope interface that extends the scope it builds on, so every setter of that scope also works in its style. `RatingStyleScope` extends `ComponentStyleScope`, and `stars` works nowhere else;
3. a `NestedStyleKey` for the widget kind, so an ancestor can style every rating at once: `Ratings`;
4. a `SwingStyleKind` for the widget that applies its keys: `RatingKind`;
5. a `style: SwingStyle<RatingStyleScope> = SwingStyle` parameter on the composable, declared with `modifier.styleable(RatingKind, style)`.

A container extends `NestedStyleProviderScope` in step 2 and uses the `content` overload of `SwingNode` in step 5. Nothing else is needed for the component to follow the styles of the ancestors it is composed under. A stateful kind passes its state in step 5.
