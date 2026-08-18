package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleDescription
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.accessibility.labelFor
import org.jetbrains.compose.swing.modifier.accessibility.labelTarget
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.accessibility.rememberLabelTarget
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.modifier.interaction.focusTraversalIndex
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.tooling.Preview

// The accessibility modifiers: assistive-technology metadata (accessibleName/Description), label
// association (labelFor), keyboard affordances (mnemonic, defaultButton), and composition-order
// focus traversal.
@Preview
@Composable
internal fun AccessibilitySection() {
    SectionColumn {
        SectionHeading("Accessibility")
        AccessibleMetadataCard()
        LabelForCard()
        MnemonicCard()
        DefaultButtonCard()
        FocusTraversalCard()
    }
}

@Composable
private fun ColumnScope.AccessibleMetadataCard() {
    ExampleCard("accessibleName & accessibleDescription") {
        var value by remember { mutableStateOf("") }
        WrappedCaption("Screen readers announce the name + description below for this field.")
        TextField(
            value = value,
            onValueChange = { value = it },
            modifier =
                SwingModifier
                    .accessibleName("Search query")
                    .accessibleDescription("Type a term to filter the results list."),
            columns = 24,
        )
    }
}

@Composable
private fun ColumnScope.LabelForCard() {
    ExampleCard("labelFor (label captions a field)") {
        var name by remember { mutableStateOf("") }
        val usernameField = rememberLabelTarget()
        FlowPanel {
            Label(
                "Username:",
                modifier = SwingModifier.labelFor(usernameField).mnemonic('U'),
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = SwingModifier.labelTarget(usernameField),
                columns = 20,
            )
        }
    }
}

@Composable
private fun ColumnScope.MnemonicCard() {
    ExampleCard("mnemonic (Alt+S activates Save)") {
        var saves by remember { mutableIntStateOf(0) }
        FlowPanel {
            Button(
                "Save",
                onClick = { saves++ },
                modifier = SwingModifier.mnemonic('S'),
            )
            Label("Saved $saves time(s)")
        }
    }
}

@Composable
private fun ColumnScope.DefaultButtonCard() {
    ExampleCard("defaultButton (Enter activates Submit)") {
        var submits by remember { mutableIntStateOf(0) }
        FlowPanel {
            Button(
                "Submit",
                onClick = { submits++ },
                modifier = SwingModifier.defaultButton(true),
            )
            Label("Submitted $submits time(s)")
        }
    }
}

@Composable
private fun ColumnScope.FocusTraversalCard() {
    ExampleCard("focusTraversalIndex + orderedFocusTraversal (Tab order)") {
        var first by remember { mutableStateOf("") }
        var second by remember { mutableStateOf("") }
        var third by remember { mutableStateOf("") }
        WrappedCaption("Tab visits these fields bottom-to-top, following each field's index.")
        Column(modifier = SwingModifier.orderedFocusTraversal()) {
            TextField(
                value = third,
                onValueChange = { third = it },
                modifier = SwingModifier.focusTraversalIndex(3),
                columns = 20,
            )
            TextField(
                value = second,
                onValueChange = { second = it },
                modifier = SwingModifier.focusTraversalIndex(2),
                columns = 20,
            )
            TextField(
                value = first,
                onValueChange = { first = it },
                modifier = SwingModifier.focusTraversalIndex(1),
                columns = 20,
            )
        }
    }
}
