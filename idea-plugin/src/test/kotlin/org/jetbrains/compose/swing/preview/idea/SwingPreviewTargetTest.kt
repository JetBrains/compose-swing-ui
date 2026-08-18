package org.jetbrains.compose.swing.preview.idea

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Measures which functions are recognized as previews, and the JVM name each is rendered under.
 *
 * Recognition resolves the annotations a function carries rather than reading their text, so the
 * library's own declarations are put in the fixture: a `Preview` that does not resolve is not this
 * library's, which is the distinction these cases are about.
 *
 * The name matters as much as the recognition: it is the only thing handed to the preview host, and a
 * top-level function's declaring class is a facade the compiler invents rather than anything written in
 * the source.
 */
class SwingPreviewTargetTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
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
        myFixture.addFileToProject(
            "other/Preview.kt",
            """
            package com.other.tooling

            @Target(AnnotationTarget.FUNCTION)
            annotation class Preview
            """.trimIndent(),
        )
    }

    fun `test recognizes an imported annotation`() {
        val function = firstFunction(
            """
            import org.jetbrains.compose.swing.tooling.Preview
            @Preview
            fun shown() {}
            """.trimIndent(),
        )

        assertTrue(function.isSwingPreview())
    }

    fun `test recognizes a fully qualified annotation`() {
        val function = firstFunction(
            """
            @org.jetbrains.compose.swing.tooling.Preview
            fun shown() {}
            """.trimIndent(),
        )

        assertTrue(function.isSwingPreview())
    }

    fun `test recognizes an annotation brought in by a star import`() {
        val function = firstFunction(
            """
            import org.jetbrains.compose.swing.tooling.*
            @Preview
            fun shown() {}
            """.trimIndent(),
        )

        assertTrue(function.isSwingPreview())
    }

    fun `test recognizes a repeated annotation`() {
        val function = firstFunction(
            """
            import org.jetbrains.compose.swing.tooling.Preview
            @Preview(name = "Light")
            @Preview(name = "Dark")
            fun shown() {}
            """.trimIndent(),
        )

        assertTrue(function.isSwingPreview())
    }

    fun `test recognizes a function carrying an annotation class that carries previews`() {
        val function = firstFunction(
            """
            import org.jetbrains.compose.swing.tooling.Preview

            @Preview(name = "Light")
            @Preview(name = "Dark")
            annotation class PreviewThemes

            @PreviewThemes
            fun shown() {}
            """.trimIndent(),
        )

        assertTrue(function.isSwingPreview())
    }

    fun `test recognizes annotation classes that carry other annotation classes`() {
        val function = firstFunction(
            """
            import org.jetbrains.compose.swing.tooling.Preview

            @Preview(name = "Light")
            annotation class PreviewThemes

            @PreviewThemes
            annotation class PreviewEverything

            @PreviewEverything
            fun shown() {}
            """.trimIndent(),
        )

        assertTrue(function.isSwingPreview())
    }

    fun `test terminates on annotation classes that carry each other`() {
        val function = firstFunction(
            """
            @PreviewCycleB
            annotation class PreviewCycleA

            @PreviewCycleA
            annotation class PreviewCycleB

            @PreviewCycleA
            fun shown() {}
            """.trimIndent(),
        )

        assertFalse(function.isSwingPreview())
    }

    fun `test rejects a Preview of another library`() {
        val function = firstFunction(
            """
            import com.other.tooling.Preview
            @Preview
            fun shown() {}
            """.trimIndent(),
        )

        assertFalse(function.isSwingPreview())
    }

    fun `test rejects an unannotated function`() {
        assertFalse(firstFunction("fun shown() {}").isSwingPreview())
    }

    fun `test rejects a preview that takes parameters`() {
        val function = firstFunction(
            """
            import org.jetbrains.compose.swing.tooling.Preview
            @Preview
            fun shown(label: String) {}
            """.trimIndent(),
        )

        assertFalse(function.isSwingPreview())
    }

    fun `test names a top-level preview by its file facade`() {
        val function = firstFunction(
            """
            package com.example
            import org.jetbrains.compose.swing.tooling.Preview
            @Preview
            fun shown() {}
            """.trimIndent(),
        )

        assertEquals("com.example.PreviewsKt.shown", function.swingPreviewTarget()?.jvmName)
    }

    fun `test honors a file's JvmName when naming the facade`() {
        val function = firstFunction(
            """
            @file:JvmName("Gallery")
            package com.example
            import org.jetbrains.compose.swing.tooling.Preview
            @Preview
            fun shown() {}
            """.trimIndent(),
        )

        assertEquals("com.example.Gallery.shown", function.swingPreviewTarget()?.jvmName)
    }

    fun `test names a preview in an object by its declaring class`() {
        val function = firstFunction(
            """
            package com.example
            import org.jetbrains.compose.swing.tooling.Preview
            object Gallery {
                @Preview
                fun shown() {}
            }
            """.trimIndent(),
        )

        assertEquals("com.example.Gallery.shown", function.swingPreviewTarget()?.jvmName)
    }

    fun `test collects every preview in a file, in the order the source states them`() {
        val file = configure(
            """
            package com.example
            import org.jetbrains.compose.swing.tooling.Preview

            @Preview
            fun first() {}

            fun notAPreview() {}

            object Gallery {
                @Preview
                fun nested() {}
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf("com.example.PreviewsKt.first", "com.example.Gallery.nested"),
            previewsIn(file).map { it.jvmName },
        )
    }

    fun `test collects nothing from a file that declares no preview`() {
        assertEmpty(previewsIn(configure("fun shown() {}")))
    }

    private fun firstFunction(text: String): KtNamedFunction {
        val file: PsiFile = myFixture.configureByText("Previews.kt", text)
        return PsiTreeUtil.findChildOfType(file, KtNamedFunction::class.java)
            ?: error("no function was parsed out of:\n$text")
    }

    private fun configure(text: String): KtFile = myFixture.configureByText("Previews.kt", text) as KtFile
}
