// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview

@Preview
@Composable
internal fun PanelLayoutsSection() {
    SectionColumn {
        SectionHeading("Panel layouts")
        FlowLayoutCard()
        BorderCompassCard()
        BorderOrientationCard()
        LinearLayoutCard()
        BoxFillersCard()
        GridLayoutCard()
        GridBagLayoutCard()
        CardDeckCard()
    }
}
