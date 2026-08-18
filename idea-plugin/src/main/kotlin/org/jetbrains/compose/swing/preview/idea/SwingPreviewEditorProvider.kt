package org.jetbrains.compose.swing.preview.idea

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puts the preview beside the source, in the editor, rather than in a window of its own.
 *
 * A preview belongs to the file being read: it is what that code looks like, so it is shown where the
 * code is, with the platform's own Code / Split / Design switch deciding how much of each is visible.
 */
internal class SwingPreviewEditorProvider :
    AsyncFileEditorProvider,
    DumbAware {
    /**
     * Whether this editor is offered for [file]: a Kotlin file in a project that has the annotation on
     * some module's classpath.
     *
     * Deliberately not a question about the file's own text. An editor is chosen once, when the file
     * opens, and never reconsidered, so a file that gains its first `@Preview` while open would keep
     * the editor it was given and show no preview until it was closed and opened again. What the file
     * says decides how much of the editor the preview takes, not whether the editor is this one.
     */
    override fun accept(
        project: Project,
        file: VirtualFile,
    ): Boolean =
        file.isValid &&
            file.extension == "kt" &&
            project.service<SwingPreviewAvailability>().previewsArePossible()

    override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
    ): FileEditor =
        withContext(Dispatchers.EDT) {
            val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
            SwingPreviewSplitEditor(
                textEditor,
                SwingPreviewEditor(project, file, document, editorCoroutineScope),
                layoutFor(document),
            )
        }

    override fun createEditor(
        project: Project,
        file: VirtualFile,
    ): FileEditor = TextEditorProvider.getInstance().createEditor(project, file)

    override fun getEditorTypeId(): String = "compose-swing-preview-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * How much of the editor a file gets on opening: both halves where its text names the package the
 * annotation lives in - which a file that writes `@Preview` does, however it imports it - and the source
 * alone otherwise, so a file with nothing to preview looks like a plain editor.
 *
 * Read from the text rather than resolved, because this decides what the editor looks like as it opens.
 * It can only be too cautious: a file that reaches a preview through an annotation of its own names
 * neither, and has its preview half revealed once the file has been resolved.
 */
internal fun layoutFor(document: Document?): TextEditorWithPreview.Layout =
    if (document != null && document.charsSequence.contains(PREVIEW_PACKAGE)) {
        TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
    } else {
        TextEditorWithPreview.Layout.SHOW_EDITOR
    }
