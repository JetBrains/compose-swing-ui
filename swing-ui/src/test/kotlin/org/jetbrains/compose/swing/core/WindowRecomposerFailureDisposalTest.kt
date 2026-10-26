// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.ExclusiveWindowSystem

/** [RecomposerFailureDisposalContractTest] for the recomposer a window owns, which needs a display. */
@ExclusiveWindowSystem
class WindowRecomposerFailureDisposalTest : RecomposerFailureDisposalContractTest() {
    override val owner = RecomposerOwner.Window
}
