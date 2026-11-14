// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.fixture

import androidx.annotation.RestrictTo

/** A class compiled apart from the rule tests, whose members carry library-scoped restrictions. */
public open class RestrictedLibrary {
    /** Restricted to the library. */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public open fun library() {}

    /** Restricted to the library group. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun group() {}

    /** Restricted to the library group prefix. */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public open fun prefix() {}
}
