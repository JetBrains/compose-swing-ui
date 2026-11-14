// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.fixture

import androidx.annotation.RestrictTo

/** A class compiled apart from the rule tests, restricted as a whole while its members carry no annotation. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public open class RestrictedLibraryParent {
    /** Inherits the restriction of the class. */
    public open fun method() {}

    /** Inherits the restriction of the class. */
    public open val value: Int get() = 0
}
