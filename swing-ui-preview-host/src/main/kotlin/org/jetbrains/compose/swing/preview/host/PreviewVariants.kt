package org.jetbrains.compose.swing.preview.host

import org.jetbrains.compose.swing.tooling.Preview
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method

/** Annotation packages a preview can never be reached through, and that a walk need not enter. */
private val UNWALKED_PACKAGES = listOf("java.", "javax.", "kotlin.", "kotlinx.")

/**
 * Every way [method] asks to be rendered, in the order the source states them.
 *
 * A composable states them by repeating the annotation, or by carrying an annotation class that
 * repeats it, or by carrying one that carries another that does - so this is a walk of the annotations
 * reachable from the method rather than a look at the method's own.
 *
 * Returns empty where nothing on the method leads to a [Preview].
 */
internal fun previewsOf(method: Method): List<Preview> {
    val previews = mutableListOf<Preview>()
    collectPreviews(method, visited = mutableSetOf(), into = previews)
    return previews
}

/**
 * Walks [element]'s annotations depth-first, in declaration order, adding every [Preview] it reaches.
 *
 * [visited] holds the annotation classes already walked, so an annotation class that carries itself, or
 * a diamond of them, terminates. A [Preview] itself is never marked visited: the same one may be
 * reached by more than one path, and each reaching is a rendering the source asked for.
 */
private fun collectPreviews(
    element: AnnotatedElement,
    visited: MutableSet<Class<*>>,
    into: MutableList<Preview>,
) {
    for (annotation in element.annotations) {
        val type = annotation.annotationClass.java
        when {
            annotation is Preview -> {
                into += annotation
            }

            else -> {
                val contained = containedPreviews(annotation)
                when {
                    contained.isNotEmpty() -> into += contained
                    UNWALKED_PACKAGES.any { type.name.startsWith(it) } -> Unit
                    visited.add(type) -> collectPreviews(type, visited, into)
                }
            }
        }
    }
}

/**
 * The previews inside a repeated annotation's container, or empty where [annotation] is not one.
 *
 * Repeating an annotation compiles to a single container annotation holding them all, so a walk that
 * only recognized [Preview] itself would see one unrecognized annotation instead of the set. The
 * container is identified by its shape rather than by its name, which the compiler chooses.
 */
private fun containedPreviews(annotation: Annotation): List<Preview> {
    val value =
        annotation.annotationClass.java.declaredMethods
            .firstOrNull { it.name == "value" }
    return if (value?.returnType == Array<Preview>::class.java) {
        @Suppress("UNCHECKED_CAST")
        (value.invoke(annotation) as Array<Preview>).toList()
    } else {
        emptyList()
    }
}
