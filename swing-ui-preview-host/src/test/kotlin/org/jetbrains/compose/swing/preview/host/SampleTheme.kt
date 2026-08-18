package org.jetbrains.compose.swing.preview.host

import com.formdev.flatlaf.FlatPropertiesLaf

/**
 * A theme of the kind a project defines for itself: FlatLaf's properties format, read from a resource.
 *
 * A preview names its look and feel by class, and the class is instantiated with no arguments, so a
 * theme that is a file rather than a class needs exactly this: a subclass that knows where its own
 * properties are. That is the whole of what a project has to write to preview under its own theme.
 */
class SampleTheme :
    FlatPropertiesLaf(
        "Sample",
        SampleTheme::class.java.getResourceAsStream("SampleTheme.properties")
            ?: error("SampleTheme.properties is missing from the test resources"),
    ) {
    companion object {
        /** The `Panel.background` the properties file states, as a preview renders it. */
        const val BACKGROUND: Int = 0x123456
    }
}
