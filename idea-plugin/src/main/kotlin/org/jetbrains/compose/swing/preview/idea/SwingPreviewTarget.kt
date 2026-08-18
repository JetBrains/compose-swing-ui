package org.jetbrains.compose.swing.preview.idea

import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

/**
 * The package the annotation lives in. A file whose text never mentions it cannot hold a preview,
 * however the annotation is imported, which is what makes a text search a sound first filter.
 */
internal const val PREVIEW_PACKAGE = "org.jetbrains.compose.swing.tooling"

/** The annotation this plugin renders, and the container a repeated one compiles to. */
private const val PREVIEW_FQ_NAME = "$PREVIEW_PACKAGE.Preview"
private const val PREVIEW_CONTAINER_FQ_NAME = "$PREVIEW_FQ_NAME.Container"

/** Annotation packages a preview can never be reached through, and that a walk need not enter. */
private val UNWALKED_PACKAGES = listOf("java.", "javax.", "kotlin.", "kotlinx.")

/** A preview to render: the JVM name the host resolves, and the label to show it under. */
internal class SwingPreviewTarget(
    val jvmName: String,
    val label: String,
)

/**
 * True when this function is a preview this plugin can render: it takes no parameters, since nothing
 * supplies arguments to it, and a `@Preview` is reachable from its annotations.
 *
 * Reachable, not present: a composable may carry an annotation class that carries the previews, and
 * that class may carry another, so this walks the annotations rather than reading the function's own.
 * The walk resolves each annotation, so it belongs on a background thread under a read action.
 */
internal fun KtNamedFunction.isSwingPreview(): Boolean =
    valueParameters.isEmpty() && toUElementOfType<UMethod>()?.leadsToPreview() == true

/**
 * Walks the annotations reachable from this declaration, breadth-first, stopping at the first
 * `@Preview`.
 *
 * Annotation classes already walked are not walked again, so a class that carries itself, or a diamond
 * of them, terminates rather than looping.
 */
private fun UAnnotated.leadsToPreview(): Boolean {
    val visited = mutableSetOf<String>()
    val pending = ArrayDeque(listOf(this))
    while (pending.isNotEmpty()) {
        for (annotation in pending.removeFirst().uAnnotations) {
            val name = annotation.qualifiedName ?: continue
            if (name == PREVIEW_FQ_NAME || name == PREVIEW_CONTAINER_FQ_NAME) return true
            if (UNWALKED_PACKAGES.any { name.startsWith(it) } || !visited.add(name)) continue
            annotation.resolve()?.toUElementOfType<UClass>()?.let { pending.addLast(it) }
        }
    }
    return false
}

/**
 * The JVM name of this function: its declaring class and its own name, dot separated.
 *
 * A top-level function belongs to its file's facade class, whose name honors `@JvmName` and
 * `@JvmMultifileClass` - which is why it is read from the file rather than derived from the file name.
 */
internal fun KtNamedFunction.swingPreviewTarget(): SwingPreviewTarget? {
    val name = name ?: return null
    val owner = containingClassOrObject
    val ownerName =
        if (owner == null) {
            JvmFileClassUtil.getFileClassInfoNoResolve(containingKtFile).facadeClassFqName.asString()
        } else {
            owner.fqName?.asString() ?: return null
        }
    return SwingPreviewTarget(jvmName = "$ownerName.$name", label = name)
}

/**
 * Every preview in [file], in the order it declares them.
 *
 * Resolves each candidate's annotations, so it belongs on a background thread under a read action.
 */
internal fun previewsIn(file: KtFile): List<SwingPreviewTarget> =
    PsiTreeUtil
        .findChildrenOfType(file, KtNamedFunction::class.java)
        .filter { it.isSwingPreview() }
        .mapNotNull { it.swingPreviewTarget() }
