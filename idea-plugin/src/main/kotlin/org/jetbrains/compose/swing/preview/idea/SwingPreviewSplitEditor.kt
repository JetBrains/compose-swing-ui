package org.jetbrains.compose.swing.preview.idea

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview

/** The name the platform shows this editor's tab and its layout actions under. */
internal const val EDITOR_NAME = "Compose Swing Preview"

/**
 * A file's source with its previews beside it.
 *
 * How much of the editor each half takes belongs to the file being read, and the platform's own
 * defaults keep both of those application-wide: without a layout of its own an editor opens on
 * whichever one was last chosen in any file this editor opened, and without a key of its own the
 * divider's position is shared with every other split editor that did not ask for one. Either turns one
 * file's arrangement into every file's.
 */
internal class SwingPreviewSplitEditor(
    textEditor: TextEditor,
    preview: SwingPreviewEditor,
    layout: Layout,
) : TextEditorWithPreview(textEditor, preview, EDITOR_NAME, layout = layout) {
    override val splitterProportionKey: String
        get() = "$EDITOR_NAME.SplitterProportion"
}
