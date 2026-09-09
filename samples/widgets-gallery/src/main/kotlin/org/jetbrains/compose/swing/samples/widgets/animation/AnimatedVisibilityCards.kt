package org.jetbrains.compose.swing.samples.widgets.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.EnterTransition
import org.jetbrains.compose.swing.animation.ExitTransition
import org.jetbrains.compose.swing.animation.ExperimentalAnimationApi
import org.jetbrains.compose.swing.animation.expandVertically
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.scaleIn
import org.jetbrains.compose.swing.animation.scaleOut
import org.jetbrains.compose.swing.animation.shrinkVertically
import org.jetbrains.compose.swing.animation.slideInHorizontally
import org.jetbrains.compose.swing.animation.slideOutHorizontally
import org.jetbrains.compose.swing.animation.unveilIn
import org.jetbrains.compose.swing.animation.veilOut
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import javax.swing.BoxLayout

/**
 * A panel in a 260 by 90 box it stands in the corner of, declared outside the column so the unscoped
 * [AnimatedVisibility] takes the call.
 */
@Composable
private fun BoxedPanel(
    showing: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    text: String,
) {
    AnimatedVisibility(
        visible = showing,
        modifier = SwingModifier.preferredSize(width = 260, height = 90),
        enter = enter,
        exit = exit,
    ) {
        PanelContent(text)
    }
}

@Composable
private fun PanelContent(text: String) {
    Panel(modifier = SwingModifier.lineBorder(Color.GRAY)) {
        Label(text)
    }
}

@Composable
internal fun ColumnScope.AnimatedVisibilityCard() {
    ExampleCard("AnimatedVisibility (enter / exit pairs)") {
        var showing by remember { mutableStateOf(false) }
        var pair by remember { mutableIntStateOf(0) }
        var emailUpdates by remember { mutableStateOf(true) }
        var shipped by remember { mutableStateOf(false) }
        val pairs =
            listOf(
                "Fade" to (fadeIn() to fadeOut()),
                "Slide sideways" to (slideInHorizontally() to slideOutHorizontally()),
                "Scale" to (scaleIn() to scaleOut()),
                "Expand / shrink" to (expandVertically() to shrinkVertically()),
                "Fade + scale" to ((fadeIn() + scaleIn()) to (fadeOut() + scaleOut())),
            )

        WrappedCaption(
            "Choose an entrance, then open the order. The line below moves with expand / shrink; fade, " +
                "slide, and scale keep the panel's space in place. Fade + scale combines those two effects.",
        )
        RadioGroup(
            selectedIndex = pair,
            onSelectionChange = { pair = it },
            axis = BoxLayout.X_AXIS,
        ) {
            pairs.forEach { (name, _) -> option(name) }
        }
        AnimatedVisibility(
            visible = showing,
            enter = pairs[pair].second.first,
            exit = pairs[pair].second.second,
        ) {
            OrderDetailsPanel(
                emailUpdates = emailUpdates,
                onEmailUpdatesChange = { emailUpdates = it },
                shipped = shipped,
                onShippedChange = { shipped = !shipped },
            )
        }
        Label("This line sits below the animated container.")
        Panel {
            Label(if (shipped) "Order #4821 · Shipped" else "Order #4821 · Ready to ship")
            ToggleButton(
                text = if (showing) "Hide order details" else "Show order details",
                selected = showing,
                onSelectedChange = { showing = it },
            )
        }
    }
}

@Composable
private fun OrderDetailsPanel(
    emailUpdates: Boolean,
    onEmailUpdatesChange: (Boolean) -> Unit,
    shipped: Boolean,
    onShippedChange: () -> Unit,
) {
    val detailsBorder = rememberThemePanelBorder()
    Panel(
        PanelLayout.Box(axis = BoxLayout.Y_AXIS),
        modifier =
            SwingModifier
                .background(themePanelColor())
                .opaque(true)
                .border(detailsBorder),
    ) {
        Label("2 items · Standard delivery")
        Label("Estimated delivery: Friday")
        CheckBox("Email me when it ships", checked = emailUpdates, onCheckedChange = onEmailUpdatesChange)
        Button(if (shipped) "Undo shipment" else "Mark as shipped", onClick = onShippedChange)
    }
}

@Composable
internal fun ColumnScope.AnimatedVisibilityChildrenCard() {
    ExampleCard("AnimatedVisibility (per-child transitions)") {
        var showing by remember { mutableStateOf(false) }
        var lastAction by remember { mutableStateOf("Choose an order action") }

        WrappedCaption(
            "Show the row. Each action enters and exits in its own direction while the container stays still.",
        )
        AnimatedVisibility(
            visible = showing,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            val reviewModifier =
                SwingModifier.animateEnterExit(
                    enter = fadeIn() + slideInHorizontally { -it / 2 },
                    exit = fadeOut() + slideOutHorizontally { -it / 2 },
                    label = "review-action",
                )
            val trackingModifier =
                SwingModifier.animateEnterExit(
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { it / 2 },
                    label = "tracking-action",
                )
            Row(horizontalArrangement = Arrangement.spacedBy(8)) {
                Box(modifier = reviewModifier) {
                    Button("Review order", onClick = { lastAction = "Review order" })
                }
                Box(modifier = trackingModifier) {
                    Button("View tracking", onClick = { lastAction = "View tracking" })
                }
            }
        }
        Panel {
            Button(if (showing) "Hide order actions" else "Show order actions", onClick = { showing = !showing })
            Label("Last action: $lastAction")
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ColumnScope.VeilCard() {
    ExampleCard("unveilIn / veilOut (a scrim over the content)") {
        var showing by remember { mutableStateOf(false) }
        var tint by remember { mutableIntStateOf(0) }
        var matchParentSize by remember { mutableStateOf(false) }
        val tints =
            listOf(
                "Half-transparent black" to Color(0, 0, 0, 128),
                "Opaque black" to Color.BLACK,
                "Blue tint" to Color(0x42, 0x85, 0xF4, 160),
            )

        WrappedCaption(
            "Reveal both panels to compare a clearing scrim with a fade. Try different tints or cover the full box.",
        )
        RadioGroup(
            selectedIndex = tint,
            onSelectionChange = { tint = it },
            axis = BoxLayout.X_AXIS,
        ) {
            tints.forEach { (name, _) -> option(name) }
        }
        Panel {
            BoxedPanel(
                showing,
                unveilIn(initialColor = tints[tint].second, matchParentSize = matchParentSize),
                veilOut(targetColor = tints[tint].second, matchParentSize = matchParentSize),
                "Veiled: a scrim over sharp text.",
            )
            BoxedPanel(showing, fadeIn(), fadeOut(), "Faded: the text itself goes translucent.")
        }
        Panel {
            ToggleButton(text = "Reveal both panels", selected = showing, onSelectedChange = { showing = it })
            CheckBox(
                text = "Scrim covers the whole box",
                checked = matchParentSize,
                onCheckedChange = { matchParentSize = it },
            )
        }
    }
}
