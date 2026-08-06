package org.jetbrains.compose.swing.samples.widgets.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading

// The form-input controls - Spinner, Slider, ProgressBar, ToggleButton, FormattedTextField - plus the
// documentFilter seam. Every card binds its control to live state echoed by an adjacent Label.
@Composable
internal fun FormInputsSection() {
    SectionColumn {
        SectionHeading("Form inputs")
        IntSpinnerCard()
        DoubleSpinnerCard()
        ListSpinnerCard()
        DateSpinnerCard()
        FormatSpinnerCard()
        EditorSpinnerCard()
        SharedRangeModelCard()
        ToggleButtonCard()
        NumberFieldCard()
        FormattedValueStateCard()
        MaskFieldCard()
        DigitsOnlyCard()
        AcceptCard()
        InputVerifierCard()
        DocumentStateCard()
    }
}
