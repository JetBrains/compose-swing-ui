// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Finding

/** The `Composable` annotation of the Compose runtime. */
internal const val COMPOSABLE_STUBS = """
package androidx.compose.runtime

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPE,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class Composable
"""

/** The source each finding points at, read off the offsets because the PSI is gone once the lint returns. */
internal fun List<Finding>.textsIn(source: String): List<String> =
    map { source.substring(it.entity.location.text.start, it.entity.location.text.end) }

/** The text a finding covers, with ` #n` appended when the text occurs more than once: its n-th occurrence. */
internal fun positionOf(
    source: String,
    start: Int,
    end: Int,
): String {
    val text = source.substring(start, end)
    val starts =
        generateSequence(source.indexOf(text)) {
            source.indexOf(text, it + 1).takeIf { next ->
                next >= 0
            }
        }
    return if (starts.count() > 1) "$text #${starts.indexOf(start) + 1}" else text
}
