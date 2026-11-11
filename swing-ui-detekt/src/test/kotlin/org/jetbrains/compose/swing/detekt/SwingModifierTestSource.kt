// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

private const val LIBRARY_SWING_MODIFIER_IMPORT =
    "import org.jetbrains.compose.swing.modifier.SwingModifier"

internal fun sourceWithLibrarySwingModifierImport(source: String): String =
    if (
        source.lineSequence().any { it.importsSwingModifierName() } ||
        source.contains("import org.jetbrains.compose.swing.modifier.*") ||
        source.contains("package org.jetbrains.compose.swing.modifier")
    ) {
        source
    } else {
        source.replaceFirst("package sample", "package sample\n\n$LIBRARY_SWING_MODIFIER_IMPORT")
    }

private fun String.importsSwingModifierName(): Boolean {
    val directive = trim().removePrefix("import ")
    if (directive == trim()) return false
    val path = directive.substringBefore(" as ")
    val alias = directive.substringAfter(" as ", missingDelimiterValue = "")
    val importedName = alias.ifEmpty { path.substringAfterLast('.') }
    return path == "org.jetbrains.compose.swing.modifier.SwingModifier" || importedName == "SwingModifier"
}
