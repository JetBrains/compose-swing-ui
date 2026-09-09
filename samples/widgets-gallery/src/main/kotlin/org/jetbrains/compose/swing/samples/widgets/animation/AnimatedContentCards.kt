package org.jetbrains.compose.swing.samples.widgets.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
import org.jetbrains.compose.swing.animation.Crossfade
import org.jetbrains.compose.swing.animation.ExitTransition
import org.jetbrains.compose.swing.animation.SizeTransform
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.togetherWith
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.UIManager

@Composable
internal fun ColumnScope.AnimatedContentSlideCard() {
    ExampleCard("AnimatedContent (slide between states)") {
        val pages = listOf("Overview", "Details", "History", "Settings")
        var page by remember { mutableIntStateOf(0) }
        var emailUpdates by remember { mutableStateOf(true) }
        var shipped by remember { mutableStateOf(false) }
        var clipContent by remember { mutableStateOf(true) }

        WrappedCaption(
            "Step through the order pages to see directional slides and a resizing container. Change Clip " +
                "content before the next page change to allow a page to extend beyond the container.",
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val towards = if (targetState > initialState) SlideDirection.Left else SlideDirection.Right
                slideIntoContainer(towards) togetherWith slideOutOfContainer(towards) using
                    SizeTransform(clip = clipContent)
            },
        ) { shown ->
            when (shown) {
                0 -> {
                    PageBody("Overview", listOf("4 open orders", "2 need attention")) {
                        Button("Review orders", onClick = { page = 1 })
                    }
                }

                1 -> {
                    PageBody("Details", listOf("Shipment #7716", "Ready to ship · Friday delivery")) {
                        Button(
                            if (shipped) "Undo shipment" else "Mark as shipped",
                            onClick = { shipped = !shipped },
                        )
                    }
                }

                2 -> {
                    PageBody("History", listOf("Last delivery · Friday", "Carrier: Northstar Parcel")) {
                        Button("Open settings", onClick = { page = 3 })
                    }
                }

                else -> {
                    PageBody("Settings", listOf("Delivery: Standard")) {
                        CheckBox(
                            "Email shipping updates",
                            checked = emailUpdates,
                            onCheckedChange = { emailUpdates = it },
                        )
                    }
                }
            }
        }
        Panel {
            Button("Prev", onClick = { page = (page - 1).coerceAtLeast(0) })
            Button("Next", onClick = { page = (page + 1).coerceAtMost(pages.lastIndex) })
            Label("Page ${page + 1} of ${pages.size}")
            CheckBox("Clip content", checked = clipContent, onCheckedChange = { clipContent = it })
        }
    }
}

@Composable
internal fun ColumnScope.CrossfadeCard() {
    ExampleCard("Crossfade (fade between states)") {
        val panes =
            listOf(
                AnimatedPage("Summary", listOf("4 open orders", "2 need attention")),
                AnimatedPage(
                    "Orders",
                    listOf("Order 7716 · Ready · Friday", "Order 8142 · Delayed · Monday", "Order 9180 · Packed"),
                ),
                AnimatedPage("Notes", listOf("Call Northstar Parcel about shipment 8142")),
            )
        var pane by remember { mutableIntStateOf(0) }

        WrappedCaption(
            "Choose a report to fade between its contents. During the overlap, the container holds room " +
                "for both reports; afterward it fits the one that remains.",
        )
        Crossfade(targetState = pane, animationSpec = tween(durationMillis = 600)) { shown ->
            val report = panes[shown]
            PageBody(report.title, report.details)
        }
        RadioGroup(
            selectedIndex = pane,
            onSelectionChange = { pane = it },
            axis = BoxLayout.X_AXIS,
        ) {
            panes.forEach { option(it.title) }
        }
    }
}

@Composable
internal fun ColumnScope.KeepUntilTransitionsFinishedCard() {
    ExampleCard("KeepUntilTransitionsFinished (hold the content being left)") {
        var swapped by remember { mutableStateOf(false) }
        var hold by remember { mutableStateOf(false) }

        WrappedCaption(
            "Swap the panels. Turn on the hold to keep the panel being left until both transitions finish.",
        )
        AnimatedContent(
            targetState = swapped,
            transitionSpec = {
                val exit = fadeOut(tween(durationMillis = 150), targetAlpha = 0.35f)
                fadeIn(tween(durationMillis = 1200)) togetherWith
                    if (hold) exit + ExitTransition.KeepUntilTransitionsFinished else exit
            },
        ) { shown ->
            if (shown) {
                PageBody("Second panel", listOf("Shipment updates are enabled."))
            } else {
                PageBody("First panel", listOf("New order ready for review."))
            }
        }
        Panel {
            Button("Swap", onClick = { swapped = !swapped })
            CheckBox("Hold the content being left", checked = hold, onCheckedChange = { hold = it })
        }
    }
}

// Each state keeps its own content and requested size while the surrounding container transitions.
private data class AnimatedPage(
    val title: String,
    val details: List<String>,
)

@Composable
private fun PageBody(
    title: String,
    details: List<String>,
    controls: @Composable () -> Unit = {},
) {
    val panelBorder = rememberThemePanelBorder()
    val titleFont = UIManager.getFont("Label.font")?.deriveFont(Font.BOLD)
    Panel(
        PanelLayout.Box(axis = BoxLayout.Y_AXIS),
        modifier =
            SwingModifier
                .background(themePanelColor())
                .opaque(true)
                .border(panelBorder),
    ) {
        Label(title, modifier = titleFont?.let(SwingModifier::font) ?: SwingModifier)
        details.forEach { Label(it) }
        controls()
    }
}
