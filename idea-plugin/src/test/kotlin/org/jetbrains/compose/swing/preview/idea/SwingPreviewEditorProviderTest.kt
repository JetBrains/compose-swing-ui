package org.jetbrains.compose.swing.preview.idea

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Measures which files are offered this editor, and how much of the editor each one opens with.
 *
 * Both are decided once, as the file opens, and neither is asked again while it stays open: a file
 * declined here is read in a plain editor until it is closed and opened afresh, whatever is written into
 * it meanwhile. That is what makes each of these worth pinning.
 */
class SwingPreviewEditorProviderTest : BasePlatformTestCase() {
    private val provider = SwingPreviewEditorProvider()

    override fun setUp() {
        super.setUp()
        // The fixture's project outlives a test method, and so would an answer cached against it.
        project.service<SwingPreviewAvailability>().forget()
    }

    fun testDeclinesAProjectThatCannotHoldAPreview() {
        val file = kotlinFile()

        assertFalse(runReadActionBlocking { provider.accept(project, file) })
    }

    fun testOffersAProjectTheAnnotationIsOnTheClasspathOf() {
        addPreviewAnnotation()
        val file = kotlinFile()

        assertTrue(runReadActionBlocking { provider.accept(project, file) })
    }

    fun testDeclinesAFileThatIsNotKotlin() {
        addPreviewAnnotation()
        val file = myFixture.addFileToProject("Sample.java", "class Sample {}").virtualFile

        assertFalse(runReadActionBlocking { provider.accept(project, file) })
    }

    fun testOffersAProjectItCannotYetDecideAbout() {
        val file = kotlinFile()

        val whileIndexing =
            DumbModeTestUtils.computeInDumbModeSynchronously(project) {
                runReadActionBlocking { provider.accept(project, file) }
            }

        assertTrue(whileIndexing)
        assertFalse(
            "An answer the indexes could not give must not become the answer",
            runReadActionBlocking { provider.accept(project, file) },
        )
    }

    fun testOpensAFileThatNamesTheAnnotationWithBothHalves() {
        val document =
            EditorFactory.getInstance().createDocument(
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview
                fun sample() = Unit
                """.trimIndent(),
            )

        assertEquals(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, layoutFor(document))
    }

    fun testOpensAFileWithNothingToPreviewAsAPlainEditor() {
        val document = EditorFactory.getInstance().createDocument("fun sample() = Unit")

        assertEquals(TextEditorWithPreview.Layout.SHOW_EDITOR, layoutFor(document))
    }

    private fun kotlinFile(): VirtualFile = myFixture.addFileToProject("Sample.kt", "fun sample() = Unit").virtualFile

    private fun addPreviewAnnotation() {
        myFixture.addFileToProject(
            "tooling/Preview.kt",
            """
            package org.jetbrains.compose.swing.tooling

            @Repeatable
            @Retention(AnnotationRetention.RUNTIME)
            @Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
            annotation class Preview(
                val name: String = "",
                val widthPx: Int = -1,
                val heightPx: Int = -1,
                val lookAndFeel: String = "",
            )
            """.trimIndent(),
        )
    }
}
