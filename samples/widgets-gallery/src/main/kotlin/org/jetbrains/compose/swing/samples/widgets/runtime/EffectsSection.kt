package org.jetbrains.compose.swing.samples.widgets.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading

// Compose effects running over the Swing recomposer: coroutine-backed LaunchedEffect, lifecycle-aware
// DisposableEffect (its onDispose fires when the child leaves composition), and snapshot-derived
// derivedStateOf - proof that the coroutine and snapshot bridges are wired to the Swing frame clock.
@Composable
internal fun EffectsSection() {
    SectionColumn {
        SectionHeading("Effects")
        TickerCard()
        DisposableCard()
        DerivedStateCard()
    }
}

@Composable
private fun ColumnScope.TickerCard() {
    ExampleCard("LaunchedEffect (delay loop)") {
        var ticks by remember { mutableIntStateOf(0) }
        var running by remember { mutableStateOf(true) }

        if (running) {
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000L)
                    ticks++
                }
            }
        }

        Label("Elapsed seconds: $ticks")
        FlowPanel {
            Button(if (running) "Pause" else "Resume", onClick = { running = !running })
            Button("Reset", onClick = { ticks = 0 })
        }
    }
}

@Composable
private fun ColumnScope.DisposableCard() {
    ExampleCard("DisposableEffect (onDispose log)") {
        var present by remember { mutableStateOf(true) }
        var log by remember { mutableStateOf("Child has not left composition yet.") }

        CheckBox(
            text = "Keep child in composition",
            checked = present,
            onCheckedChange = { present = it },
        )

        if (present) {
            ManagedChild(onDispose = { log = "Child left composition." })
        }

        Label(log)
    }
}

@Composable
private fun ManagedChild(onDispose: () -> Unit) {
    DisposableEffect(Unit) {
        onDispose { onDispose() }
    }
    Label("Child is alive.")
}

@Composable
private fun ColumnScope.DerivedStateCard() {
    ExampleCard("derivedStateOf") {
        var amount by remember { mutableIntStateOf(50) }
        val label by remember {
            derivedStateOf {
                when {
                    amount < 33 -> "Low"
                    amount < 66 -> "Medium"
                    else -> "High"
                }
            }
        }

        FlowPanel {
            Label("Amount: $amount")
            Slider(value = amount, onValueChange = { amount = it }, min = 0, max = 100)
        }
        Label("Derived level: $label")
    }
}
