@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.annotations.HorizontalScrollbarPolicy
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.VerticalScrollbarPolicy
import org.jetbrains.compose.swing.components.style.ScrollPanes
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.border.Border

/**
 * A scrolling view onto content larger than the room it has - a `JScrollPane`, with the viewport it
 * scrolls, its scroll bars, and the headers and corners that stay put beside them declared as content.
 *
 * The pane builds its viewport and both scroll bars itself; [ScrollPaneScope.Viewport],
 * [ScrollPaneScope.VerticalScrollbar] and [ScrollPaneScope.HorizontalScrollbar] configure them, and the
 * content of `Viewport` is the view the pane scrolls. The row header, the column header and each of the
 * four corners are regions a child fills by naming one through [ScrollPaneScope]. A child declaring none
 * of these is refused, and so are two declarations of one part or one region:
 *
 * ```
 * ScrollPane {
 *     Viewport { LongList() }
 *     ColumnTitles(modifier = SwingModifier.columnHeader())
 *     CornerBadge(modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
 * }
 * ```
 *
 * Set a fixed viewport size with `style = { preferredSize(...) }`.
 *
 * The scroll position is hoistable state, so the application can read it, drive it, and follow the
 * user's scrolling:
 *
 * ```
 * val scroll = rememberScrollState()
 * Button(text = "To the bottom", onClick = { scroll.y = scroll.maxY })
 * ScrollPane(state = scroll) {
 *     Viewport { LongList() }
 * }
 * ```
 *
 * How far the pane scrolls per arrow button and per page is declared on the viewport - see
 * [ScrollPaneScope.Viewport].
 *
 * @param modifier the [SwingModifier] applied to the underlying `JScrollPane`
 * @param style the [SwingStyle] applied to the underlying `JScrollPane`
 * @param state the pane's two-way scroll position; see [ScrollState]. Left out, the pane gets a state
 *   of its own, which nothing outside it reads or drives
 * @param verticalScrollbar the vertical scrollbar policy; by default the bar is there only while the
 *   content is taller than the viewport
 * @param horizontalScrollbar the horizontal scrollbar policy; by default the bar is there only while
 *   the content is wider than the viewport
 * @param viewportBorder the border drawn around the viewport, inside the pane's own border and outside
 *   the scrolled content; `null` leaves the border to the installed look and feel, and withdrawing a
 *   declared border hands the look and feel's own back
 * @param wheelScrollingEnabled whether the mouse wheel scrolls the pane; `true` by default
 * @param content the composable content of the pane; see [ScrollPaneScope]
 * @see javax.swing.JScrollPane
 */
@Composable
public fun ScrollPane(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ContainerStyleScope> = SwingStyle,
    state: ScrollState = rememberScrollState(),
    @VerticalScrollbarPolicy verticalScrollbar: Int = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
    @HorizontalScrollbarPolicy horizontalScrollbar: Int = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    viewportBorder: Border? = null,
    wheelScrollingEnabled: Boolean = true,
    content: @Composable ScrollPaneScope.() -> Unit,
) {
    ScrollPaneNode(
        factory = { JScrollPane(null as Component?, verticalScrollbar, horizontalScrollbar) },
        modifier = modifier,
        style = style,
        state = state,
        verticalScrollbar = verticalScrollbar,
        horizontalScrollbar = horizontalScrollbar,
        viewportBorder = viewportBorder,
        wheelScrollingEnabled = wheelScrollingEnabled,
        content = content,
    )
}

/**
 * A [ScrollPane] on the `JScrollPane` that [factory] builds, such as a subclass with viewport, scroll bars
 * or corners of its own.
 *
 * [factory] runs once for each node and returns a new component on every call. A changed capture is not
 * applied, as with [SwingNode]: to build another component, wrap the call in `key(...)`. What the other
 * parameters declare is written over what the factory set.
 *
 * A corner or header the factory built stands until a child fills that region, and is back in place when
 * the child leaves. Declaring [ScrollPaneScope.Viewport] content replaces a view the factory installed.
 *
 * A scroll bar of the pane's own comes from overriding `createVerticalScrollBar` and
 * `createHorizontalScrollBar`, since withdrawing an increment builds a new one.
 *
 * @param factory builds the component this composable drives.
 * @param modifier the [SwingModifier] applied to the underlying `JScrollPane`
 * @param style the [SwingStyle] applied to the underlying `JScrollPane`
 * @param state the pane's two-way scroll position; see [ScrollState]. Left out, the pane gets a state
 *   of its own, which nothing outside it reads or drives
 * @param verticalScrollbar the vertical scrollbar policy; by default the bar is there only while the
 *   content is taller than the viewport
 * @param horizontalScrollbar the horizontal scrollbar policy; by default the bar is there only while
 *   the content is wider than the viewport
 * @param viewportBorder the border drawn around the viewport, inside the pane's own border and outside
 *   the scrolled content; `null` leaves the factory's border, and withdrawing a declared border restores
 *   that border
 * @param wheelScrollingEnabled whether the mouse wheel scrolls the pane; `true` by default
 * @param content the composable content of the pane; see [ScrollPaneScope]
 * @see javax.swing.JScrollPane
 */
@InternalSwingUiApi
@Composable
public fun ScrollPane(
    factory: () -> JScrollPane,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ContainerStyleScope> = SwingStyle,
    state: ScrollState = rememberScrollState(),
    @VerticalScrollbarPolicy verticalScrollbar: Int = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
    @HorizontalScrollbarPolicy horizontalScrollbar: Int = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    viewportBorder: Border? = null,
    wheelScrollingEnabled: Boolean = true,
    content: @Composable ScrollPaneScope.() -> Unit,
) {
    ScrollPaneNode(
        factory = factory,
        modifier = modifier,
        style = style,
        state = state,
        verticalScrollbar = verticalScrollbar,
        horizontalScrollbar = horizontalScrollbar,
        viewportBorder = viewportBorder,
        wheelScrollingEnabled = wheelScrollingEnabled,
        content = content,
    )
}

@Composable
private inline fun ScrollPaneNode(
    crossinline factory: () -> JScrollPane,
    modifier: SwingModifier,
    style: SwingStyle<ContainerStyleScope>,
    state: ScrollState,
    verticalScrollbar: Int,
    horizontalScrollbar: Int,
    viewportBorder: Border?,
    wheelScrollingEnabled: Boolean,
    crossinline content: @Composable ScrollPaneScope.() -> Unit,
) {
    val scope = remember { ScrollPaneScopeImpl() }

    SwingNode(
        factory = { factory().also(scope::bind) },
        modifier =
            modifier
                .scrollStateBinding(state)
                .declaredViewportBorder(viewportBorder)
                .styleable(ScrollPaneKind, style),
        update = {
            set(verticalScrollbar) { verticalScrollBarPolicy = it }
            set(horizontalScrollbar) { horizontalScrollBarPolicy = it }
            set(wheelScrollingEnabled) { isWheelScrollingEnabled = it }
        },
        childPlacement = ScrollPaneRegions,
        content = { scope.Content(content) },
    )
}

/**
 * The `ScrollPane.viewportBorder` key cannot answer for this border. A look and feel installs it onto
 * the pane itself, and one built on [javax.swing.plaf.synth.SynthLookAndFeel] installs it from a style
 * of its own and publishes no key at all. The border is read off the pane and written back from there.
 *
 * `JScrollPane.setViewportBorder` only fires a property change, and no look and feel acts on it. The
 * write asks for both halves itself: the pane's layout takes the viewport in by the border's insets, and
 * the border is drawn in the room that leaves.
 */
private val ViewportBorderProperty =
    ComponentPropertyDescriptor<JScrollPane, Border?>(
        name = "viewportBorder",
        read = { it.viewportBorder },
        write = { pane, value ->
            pane.viewportBorder = value
            pane.revalidate()
            pane.repaint()
        },
    )

private fun SwingModifier.declaredViewportBorder(border: Border?): SwingModifier =
    if (border == null) this else property(ViewportBorderProperty, border)

/** [ContainerStyleScope] for ScrollPane styles. No component state is supplied. */
private val ScrollPaneKind =
    nestingStyleKind<ContainerStyleScope, Unit>(
        { base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} },
    ) {
        applyNestedStyle(ScrollPanes)
    }
