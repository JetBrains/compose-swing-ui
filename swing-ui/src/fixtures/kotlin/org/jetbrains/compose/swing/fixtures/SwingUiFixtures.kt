// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

import org.jetbrains.compose.swing.fixtures.components.LabelFixture

/** Every fixture of swing-ui, for consumers that run all of them, such as the benchmark suites. */
public val swingUiFixtures: List<ComposableFixture<*, *>> =
    listOf(
        LabelFixture,
    )
