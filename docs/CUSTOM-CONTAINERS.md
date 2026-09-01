# Containers and item cells

A container declares its children rather than adding them, and offers the placements its layout
manager understands as a scope. This document is writing such a container, the shared hierarchies
assembled from them, hosting a nested composition, and rendering a component's items with a
composable cell. Building a leaf component is [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

## A container example

For a custom container, use the `content` overload and create a `Container` in the factory; children
emitted by `content` are added by the framework's applier:

<!--- INCLUDE .*custom-container-01.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.border.TitledBorder
-->

```kotlin
@Composable
fun TitledGroup(
    title: String,
    modifier: SwingModifier = SwingModifier,
    content: @Composable @SwingComposable () -> Unit,
) {
    SwingNode(
        factory = { JPanel(FlowLayout()).apply { border = TitledBorder("") } },
        update = {
            set(title) { (this.border as TitledBorder).title = it }
            applyModifier(modifier)
        },
        content = content,
    )
}
```

<!--- KNIT example-custom-container-01.kt -->

A container takes a `modifier` and applies it for the same reason a leaf does, and for one more: a
container is itself a child of whatever holds it, so the group above is placed in a `BorderPanel`
region or a `GridBagPanel` cell only because its `update` block ends where it does.

## Placing children under constraints

A child declares where it sits inside its parent on its own `SwingModifier`, with `layoutConstraint`.
The value is whatever the enclosing container's layout manager understands - a `BorderLayout` region, a
`GridBagConstraints`, a cell in a manager of your own - and it is handed over the way
`Container.add(Component, Object)` hands it over, so a `LayoutManager2` receives it as-is and a manager
that takes no constraints places the child by index.

What a container supplies is the layout manager, and a scope whose modifier builders name the
placements that manager understands. The scope is a public sealed interface whose builders are declared
as extensions on `SwingModifier`, so each one is callable only where that scope is in receiver
position - inside the container's own content - and an internal object or class implements them.
`RowScope.weight` is the worked precedent for that shape; over a manager of your own, each builder
appends the value the manager understands with `layoutConstraint`:

```kotlin
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JPanel

/** The placements a mosaic offers, and where the constraint's type belongs. */
sealed interface MosaicScope {
    /** Places the child in the cell at [row] and [column]. */
    fun SwingModifier.cell(row: Int, column: Int): SwingModifier
}

// A cell reaches the manager on the child's own chain, so the scope holds nothing and one instance
// serves every mosaic. MosaicCell is a data class, so a cell rebuilt from the same arguments is the
// cell the child already sits in.
private object MosaicScopeImpl : MosaicScope {
    override fun SwingModifier.cell(row: Int, column: Int): SwingModifier =
        layoutConstraint(MosaicCell(row, column))
}

@Composable
fun MosaicPanel(
    modifier: SwingModifier = SwingModifier,
    content: @Composable MosaicScope.() -> Unit,
) {
    SwingNode(
        factory = { JPanel(MosaicLayout()) },
        update = { applyModifier(modifier) },
        content = { MosaicScopeImpl.content() },
    )
}

MosaicPanel {
    Label("Title", modifier = SwingModifier.cell(row = 0, column = 0))
    Label("Body", modifier = SwingModifier.cell(row = 1, column = 0))
}
```

Changed is decided by `equals`. A chain is rebuilt on every pass, so the placement in it is compared
against the one the child already sits under, and only a difference re-registers the child: the
framework takes it out of the layout manager and adds it back under the new value, then revalidates
the parent. A value that compares by value therefore costs nothing to rebuild, while one that compares
by identity - a bare `GridBagConstraints`, a class of your own that never overrode `equals` - is a new
instance on every pass and puts every child through that removal and re-add on every composition. What
that costs is the manager's, not the framework's: a `LayoutManager2` that keeps anything derived from
the placements it has been handed - a grid, a set of measured column widths, a row cache - discards and
rebuilds it each time, because a re-registration reaches it as the same
`removeLayoutComponent`/`addLayoutComponent` pair a real structural change does. Give a placement of
your own a value `equals`, as `GridBagPanel` does for the constraints its items declare.

Derive that value from the declaration rather than from a running count of the children before it,
too: a placement computed from how many siblings came first changes for every later child the moment
one is inserted, and every one of them is then re-registered. This is advice about the placement value.
The `key` a child is declared under is a separate question, answered in *What the composition owns*
in [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md).

A scope like this keeps the constraint's type inside your container - `MosaicScope` is the whole
placement API your callers see, and `BorderPanel`'s regions and `GridBagPanel`'s items are the same
shape over a fixed, nameable set of placements.

A scope is worth writing only where the placements are worth naming. Where a layout manager answers
for a child that declares nothing - `BorderLayout` places one at `CENTER`, `JLayeredPane` on
`DEFAULT_LAYER`, `CardLayout` under the empty name - a container over it takes a plain
`@Composable () -> Unit` content and lets the manager do the placing. That is the usual case: no
scope, no constraint. `layoutConstraint` stays available to a caller who does need to name a
placement.

Three rules shape a container of this kind, and every container that places children this library ships
follows all three:

- **The content block is `@Composable`, and the children written in it compose where the container's
  own children belong.** Declare it `content: @Composable XScope.() -> Unit` and hand it to `SwingNode`
  as `content = { XScopeImpl.content() }`. Beyond making every child a node the applier places, this is
  what gives each child the identity described in *What the composition owns* in
  [`CUSTOM-COMPONENTS.md`](CUSTOM-COMPONENTS.md): a child composed where the caller wrote it is told
  from its siblings by that place. An ordinary block that records composable lambdas for the container
  to invoke afterwards hands every one of them to the single place the container invokes them from, so
  they share it and are left to be told apart by order. Composed directly,
  `if (showEmail) EmailField()` followed by `PhoneField()` leaves the phone field's component and
  everything it holds alone when the email field appears, with no `key` from anyone.
- **There is no collection phase.** A scope's builders return a chain and the children compose as they
  are written, so there is no list of pending declarations, no `ScopeImpl().apply(block)`, and no
  `forEach` over what a block gathered. That also settles whether to `remember` the scope: one holding
  nothing is an object shared by every instance of the container, as `MosaicScopeImpl` above and
  `BorderPanelScope`'s implementation are, and one holding something the container owns - a layout's
  placement table, a mirror the container settles a value through - is remembered alongside the
  container, as `RowScope`'s and `ScrollPaneScope`'s implementations are.
- **A container that has to validate its children runs each check where its answer is whole.** A rule
  about one child on its own is answered as that node arrives, the first moment the container knows the
  child is really there: a `tab` declared by a component that some other container holds is refused as
  that component is attached. A rule over the whole set of children - one child per side, one per card -
  is answered once the pass has settled, because a pass may hold two children in one place while it
  runs: a replacement need not wait for the child it replaces to go, and only what remains at the end is
  what the composition declares. A container reaching its children through regions leaves that to the
  framework, which holds a `ChildPlacement.Slots` host to one child per region once the pass settles, as
  `SplitPane` does for its two sides; one placing them under constraints runs its own check on the
  event-dispatch turn after the change pass, once a parked node's deactivation has run too, as
  `CardPanel` does over the cards its deck holds. Either way the set a check runs against is what the
  container actually holds - its own children, its layout's own records - rather than a list a block
  gathered.

A scope's members are `SwingModifier` builders wherever the child is the caller's own component. Where
the container is what realizes the child instead - `DesktopPane`, whose every child is a
`JInternalFrame` built around the content the declaration carries - the scope's member is a
`@Composable` function taking that content, since there is no component of the caller's to hang a
modifier on.

Two things such a container should not do. It should not spend a component on a child: wrapping every
child in a panel of your own to carry something - a border, an alignment - puts a real container with a
real layout between the caller's component and yours, and a `modifier` meant for the child is then
applied to that wrapper instead of to the component the caller wrote. It also changes what the layout
above sees: a parent measures and inspects the children it holds, so a gap policy that treats a nested
panel differently from a control, or an alignment that lines a label up against the field beside it,
reads the wrapper you inserted instead of the component, and the same components laid out through your
container stop matching the ones laid out without it. And it should not accept a declaration it does
not apply: a parameter a scope takes and never writes onto anything is silence - either it reaches the
component or it leaves the signature.

A placement travels no further than the node whose chain declares it, which is what lets a scope's
builders name placements only their own container understands.

## When the placement is not something the caller states

The placements above are values the caller writes: a region name, a cell, a pair of grid coordinates.
A manager of your own may instead derive a child's placement from the children around it - a form whose
column count is its widest row's, a group whose leading gap depends on whether another group precedes
it, a band that divides its width among whatever ended up in it. Nothing changes about how the
placement travels. What changes is that the answer cannot be computed as each child arrives, because
it needs every declaration, and that your manager cannot hold on to what it derived, because the next
composition may change it.

Two things carry a manager like that. The first is that the component array *is* the declared
structure. Every child is added at its composition index - a constrained child through the
three-argument `Container.add(Component, Object, int)`, precisely so that applying a placement does not
cost the array its order - and later removals and moves address that same array. A manager is therefore
free to read `parent.components`, and the constraints it registered for them, as the whole structure
the caller declared, in the order they declared it, and to derive from all of it when it is asked to
measure or to lay out. Deriving at `addLayoutComponent` time is the part that does not work: the child
arriving knows nothing about the ones still to come.

The second is that what the placement carries is the child's part in that structure - which row this
control belongs to, how deep its group is - rather than the coordinates the manager will work out from
it. That keeps the placement something a single declaration can produce on its own, so it compares
equal to the last composition's whenever the declaration did not change and only the children whose
part really moved are re-registered. A placement carrying the derived coordinate would change for every
child the moment any one of them changed, and put the whole container through a remove-and-re-add on
every pass.

## Building a custom shared hierarchy

A composite several screens reuse - a titled card, a labeled form, a frame with a fixed header and
footer - is a composable that emits the containers it needs and offers its placements as a scope
receiver. Everything the pattern needs is public: the built-in containers, the container `SwingNode`
overload for a Swing container of your own, and `layoutConstraint` for the placements a scope names.

The scope interface is the whole API callers see. Each of its builders names one region of the
composite, over the constraint the container underneath understands:

```kotlin
sealed interface FramedScope {
    /** Places the child across the top of the frame, above its body. */
    fun SwingModifier.header(): SwingModifier

    /** Places the child in the frame's body, filling what the header and footer leave. */
    fun SwingModifier.body(): SwingModifier

    /** Places the child across the bottom of the frame, below its body. */
    fun SwingModifier.footer(): SwingModifier
}

private object FramedScopeImpl : FramedScope {
    override fun SwingModifier.header(): SwingModifier = layoutConstraint(BorderLayout.NORTH)

    override fun SwingModifier.body(): SwingModifier = layoutConstraint(BorderLayout.CENTER)

    override fun SwingModifier.footer(): SwingModifier = layoutConstraint(BorderLayout.SOUTH)
}

@Composable
fun Framed(
    title: String,
    modifier: SwingModifier = SwingModifier,
    content: @Composable FramedScope.() -> Unit,
) {
    val border = remember(title) { TitledBorder(title) }
    BorderPanel(modifier = modifier.border(border)) {
        FramedScopeImpl.content()
    }
}

Framed(title = "Payment") {
    Label("Card details", modifier = SwingModifier.header())
    PaymentForm(modifier = SwingModifier.body())
    Button("Pay", onClick = ::pay, modifier = SwingModifier.footer())
}
```

The composite owns no node of its own, and it does not have to. A placement rides the child's chain
until the container actually holding that child reads it, so the `BorderLayout` constraint
`FramedScopeImpl` builds is honored by the `BorderPanel` inside `Framed` although the caller never sees
that panel. That is what makes a composite of built-in containers a plain composable function rather
than a wrapper that has to forward anything.

Four things make a composite of this shape behave:

- **Hold the scope to its three rules** - a `@Composable` content block, children composed directly,
  and each check run where its answer is whole (see *Placing children under constraints*). A
  composite's regions are placements like any other, so nothing about them is special.
- **Keep the composite composable-shaped** - state in, callbacks out, and a `modifier` parameter
  chained onto the outermost container it emits, so a caller styles the composite the way they style
  any component.
- **`remember` the value objects whose type compares by identity** - `remember(title) { TitledBorder(title) }`,
  as above. A `Border` or `Icon` built in the composable body is a different instance on every
  recomposition, and neither overrides `equals`, so the chain element sees a changed value on every
  pass and re-applies: `JComponent.setBorder` repaints for any instance that is not the one it already
  holds, and relayouts when the insets differ. Handing the same instance back makes the re-applied write
  a no-op. A value with structural equality - a `Font`, `Color`, `Insets` - needs no `remember`: an
  equal instance already compares equal to the chain element applied last time and the write is skipped.
- **Let each container place its own children.** Nothing carries a placement past the node that
  declared it, so a composite nests inside another - a `GridBagPanel` in the `body` region above - with
  neither knowing about the other.

Reach for `SwingNode` here only when the Swing container itself is yours; a composite assembled from
built-in containers is an ordinary composable function.

## Hosting nested compositions: `hostSubcompositions`

Declare `hostSubcompositions` in the update block for a custom component that, internally, drives its
**own** `setContent` against one of its children - for example, a Swing container that manages tabs,
popups, or split panes by calling `setContent` on sub-panels it creates itself.

Those nested `setContent` calls then **join this node's own composition**, sharing its
`CompositionLocal`s along with the recomposer and scope around it. Without it such a call joins
whatever its place in the Swing tree resolves to - the content composition above it, or the one its window
shares - so it recomposes with everything else there but sees none of the `CompositionLocal`s this node
stands under.

Read the enclosing context where the node is declared, and hand it over:

```kotlin
val parentContext = rememberCompositionContext()
SwingNode(
    factory = { TabbedPanel() }, // a JComponent that runs setContent on its own tab panels
    update = { hostSubcompositions(parentContext) },
)
```

Pass `null` to host nothing, which is what a node that never declares it does.

The component **must** be a `javax.swing.JComponent`; a bare `java.awt.Component` host throws
`IllegalStateException` at apply.

## Rendering items with a composable cell

A component that shows a list of items asks a `ListCellRenderer` for one row at a time.
`rememberListItemRenderer` builds a renderer that stamps a composable there and
`SwingModifier.listItemRenderer` installs it: one reused composition is stamped for every row, and the
component it composes is painted and measured as the row.

<!--- CLEAR -->
<!--- INCLUDE .*custom-container-02.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.selection.ListItemScope
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Dimension
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import kotlin.reflect.KClass
-->

```kotlin
// A combo box that never shrinks below its preferred width, so a long item is not clipped.
class WideComboBox<T> : JComboBox<T>() {
    override fun getMinimumSize(): Dimension = preferredSize
}

@Composable
fun <T : Any> CustomComboBox(
    items: List<T>,
    itemType: KClass<T>,
    modifier: SwingModifier = SwingModifier,
    itemContent: @Composable ListItemScope.(item: T) -> Unit,
) {
    val cells = rememberListItemRenderer(itemType, itemContent)
    val declaredItems = items.toList()
    SwingNode(
        factory = { WideComboBox<T>() },
        update = {
            set(declaredItems) { model = DefaultComboBoxModel(Vector(it)) }
            applyModifier(modifier.listItemRenderer(cells))
        },
    )
}
```

<!--- KNIT example-custom-container-02.kt -->

The component is the caller's own, so a subclass with rendering or sizing behavior the built-in
`ComboBox` does not have is a wrapper of your own rather than a parameter on the library's.

The item type is a parameter because this wrapper is generic. Written directly -
`rememberListItemRenderer<Person> { }` - the call site supplies that type on its own; inside a generic
function it is erased by then, which is what the overload taking it is for.

The items are copied in the composable body rather than handed to `set` as they are. `set` compares
what it is given against what the last pass gave it, and a list the caller mutates in place compares
equal to itself, so such a mutation would re-apply nothing. The copy gives each pass its own value to
compare against, and reading the items while composing is what makes this composition one of their
readers - so an in-place mutation of a snapshot list invalidates it.

A selection this component reports and accepts is the two-way property of
[`COMPONENT-STATE.md`](COMPONENT-STATE.md), declared with `rememberMirrorState` and `declare` exactly
as it is there.

The scope a cell composes against - `ListItemScope` - names the row: `index`, `isSelected` and
`cellHasFocus`, the three values a `ListCellRenderer` is handed.

A cell is display-only. One composition serves every row, so state remembered inside it belongs to no
particular row, and a cell that composes more than one component is refused - put them in a panel and
compose that.

`listItemRenderer` also takes a renderer written against the Swing interface:
`modifier.listItemRenderer(MyExistingRenderer())`.
