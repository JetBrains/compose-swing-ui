// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationList
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * The size an `androidx.annotation.Size` annotation requires: an exact [value], a [min] and a [max], and a [multiple].
 * An argument the annotation leaves unset takes the annotation's own default; an unset [value] is `null`.
 */
internal class SizeRequirement(
    private val value: Long?,
    private val min: Long,
    private val max: Long,
    private val multiple: Long,
) {
    fun violationMessage(size: Long): String? {
        val requirement =
            when {
                value != null && size != value -> "exactly $value"
                size < min -> "at least $min"
                size > max -> "at most $max"
                multiple > 0 && size % multiple != 0L -> "a multiple of $multiple"
                else -> return null
            }
        return "Size $size is outside the declared size, which requires $requirement. " +
            "Pass a value of the declared size."
    }

    companion object {
        fun of(declaration: KaAnnotationList): SizeRequirement? {
            val annotation =
                declaration.annotationOf(ClassId.topLevel(FqName("androidx.annotation.Size"))) ?: return null

            fun number(name: String) = (annotation.constantArgument(name)?.value as? Number)?.toLong()
            return SizeRequirement(
                number("value")?.takeUnless { it == -1L },
                number("min") ?: Long.MIN_VALUE,
                number("max") ?: Long.MAX_VALUE,
                number("multiple") ?: 1,
            )
        }
    }
}
