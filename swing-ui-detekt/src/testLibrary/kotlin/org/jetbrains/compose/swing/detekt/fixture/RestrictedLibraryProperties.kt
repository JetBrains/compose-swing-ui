// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.fixture

import androidx.annotation.RestrictTo

/** A value type held by the properties of [RestrictedLibraryProperties]. */
public class RestrictedLibraryValue

/** A class compiled apart from the rule tests, with a subclass-scoped restriction at each property use-site. */
public open class RestrictedLibraryProperties {
    /** No use-site target: the annotation has no property target, so Kotlin places it on the backing field. */
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    public val plain: RestrictedLibraryValue = RestrictedLibraryValue()

    /** Restricted on the setter only. */
    @set:RestrictTo(RestrictTo.Scope.SUBCLASSES)
    public var setterOnly: RestrictedLibraryValue? = RestrictedLibraryValue()

    /** Restricted on the getter only. */
    @get:RestrictTo(RestrictTo.Scope.SUBCLASSES)
    public var getterOnly: RestrictedLibraryValue? = RestrictedLibraryValue()
}
