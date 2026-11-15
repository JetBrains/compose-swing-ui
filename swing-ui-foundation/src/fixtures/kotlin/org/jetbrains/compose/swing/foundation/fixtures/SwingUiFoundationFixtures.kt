// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures

import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.AbsoluteOffsetFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.AbsolutePaddingFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.AspectRatioFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.BoxEmptyFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.BoxFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.ClipToBoundsFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.ColumnFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.DefaultMinSizeFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.FillMaxHeightFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.FillMaxSizeFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.FillMaxWidthFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.HeightFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.HeightInFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.LayoutFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.LayoutParentDataProtocolFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.OffsetFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.OnPlacedFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.OnSizeChangedFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.PaddingFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RequiredHeightFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RequiredHeightInFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RequiredSizeFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RequiredSizeInFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RequiredWidthFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RequiredWidthInFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.RowFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.SizeFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.SizeInFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.WidthFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.WidthInFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.WrapContentHeightFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.WrapContentSizeFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.WrapContentWidthFixture
import org.jetbrains.compose.swing.foundation.fixtures.layout.ZIndexFixture

/** Every fixture of swing-ui-foundation, for consumers that run all of them, such as the benchmark suites. */
public val swingUiFoundationFixtures: List<ComposableFixture<*, *>> =
    listOf(
        CanvasFixture,
        AbsoluteOffsetFixture,
        AbsolutePaddingFixture,
        AspectRatioFixture,
        BoxEmptyFixture,
        BoxFixture,
        ClipToBoundsFixture,
        ColumnFixture,
        DefaultMinSizeFixture,
        FillMaxHeightFixture,
        FillMaxSizeFixture,
        FillMaxWidthFixture,
        HeightFixture,
        HeightInFixture,
        LayoutFixture,
        LayoutParentDataProtocolFixture,
        OffsetFixture,
        OnPlacedFixture,
        OnSizeChangedFixture,
        PaddingFixture,
        RequiredHeightFixture,
        RequiredHeightInFixture,
        RequiredSizeFixture,
        RequiredSizeInFixture,
        RequiredWidthFixture,
        RequiredWidthInFixture,
        RowFixture,
        SizeFixture,
        SizeInFixture,
        WidthFixture,
        WidthInFixture,
        WrapContentHeightFixture,
        WrapContentSizeFixture,
        WrapContentWidthFixture,
        ZIndexFixture,
    )
