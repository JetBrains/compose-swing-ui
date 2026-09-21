// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.accessibility

import javax.accessibility.AccessibleContext

/**
 * The accessible name this context was given, or `null` where it is deriving one.
 *
 * @see explicitOrNull for how the two are told apart.
 */
internal fun AccessibleContext.declaredAccessibleName(): String? =
    explicitOrNull({ accessibleName }, { accessibleName = it })

/**
 * The accessible description this context was given, or `null` where it is deriving one.
 *
 * @see explicitOrNull for how the two are told apart.
 */
internal fun AccessibleContext.declaredAccessibleDescription(): String? =
    explicitOrNull({ accessibleDescription }, { accessibleDescription = it })

/**
 * The string [read] answers only because it was set, or `null` where the context derives one instead.
 *
 * A context offers no way to ask which of the two it just answered. Writing `null` and reading back
 * does: a derived answer survives that write, a string that was set does not, and is put straight back.
 *
 * The write reaches whoever listens to this context on the accessible name or description. A context
 * fires nothing until something asks it to listen, and at the point a declaration attaches there is
 * rarely anything listening yet.
 *
 * A string that was set and reads the same as the derived one survives the write, so it is answered
 * `null`. Putting that `null` back leaves the context deriving, which answers that string only while
 * what it derives from stands.
 */
private inline fun explicitOrNull(
    read: () -> String?,
    write: (String?) -> Unit,
): String? {
    val answered = read() ?: return null
    write(null)
    val wasSet = read() != answered
    if (wasSet) write(answered)
    return answered.takeIf { wasSet }
}
