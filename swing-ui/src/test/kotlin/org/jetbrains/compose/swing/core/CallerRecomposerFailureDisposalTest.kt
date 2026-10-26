// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

/** [RecomposerFailureDisposalContractTest] for a recomposer a caller obtained through [SwingRecomposer.create]. */
class CallerRecomposerFailureDisposalTest : RecomposerFailureDisposalContractTest() {
    override val owner = RecomposerOwner.Caller
}
