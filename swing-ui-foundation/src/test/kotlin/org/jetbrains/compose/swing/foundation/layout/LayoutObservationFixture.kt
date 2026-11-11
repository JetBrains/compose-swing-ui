// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.test.assertEquals

/**
 * Composes [content] as the content of a showing window titled [WINDOW_TITLE], and returns once the window
 * stands still: a window system resizes a decorated window once it reports the window's insets, and that
 * lays the window out again.
 */
internal suspend fun ComposeSwingTest.setWindowContent(content: @Composable () -> Unit) {
    setContent {
        Window(
            onCloseRequest = {},
            state = WindowState(size = Dimension(400, 400)),
            title = WINDOW_TITLE,
        ) { content() }
    }
    awaitIdle()
    onWindowWithTitle(WINDOW_TITLE).awaitWindowSystemIdle()
}

/** The container under test in the window [setWindowContent] composed. */
internal fun ComposeSwingTest.windowContainer(): JComponent =
    onWindowWithTitle(WINDOW_TITLE).onNodeWithTag(CONTAINER_TAG).fetch<JComponent>()

/**
 * This component and every container above it, up to its validate root: the window above that root is
 * never validated by a `revalidate()`, so it stays invalid once anything below it was.
 */
internal fun Component.withAncestors(): Sequence<Component> =
    generateSequence(this) { if (it is Container && it.isValidateRoot) null else it.parent }

internal fun Component.isValidUpToTheValidateRoot(): Boolean = withAncestors().all { it.isValid }

/** The area of this component waiting to be painted. */
internal fun JComponent.dirtyRegion(): Rectangle = RepaintManager.currentManager(this).getDirtyRegion(this)

/** [expected] is in [owner]'s own coordinates. */
internal fun ComposeSwingTest.assertDirtyRegionsDuring(
    owner: JComponent,
    expected: List<Rectangle>,
    message: String,
    change: () -> Unit,
) {
    val asked =
        withRecordedRepaints {
            change()
            it
        }
    assertEquals(expected, asked.dirtyRegionsOf(owner), message)
}

/** The title of the window a test that needs a showing hierarchy composes. */
internal const val WINDOW_TITLE = "layout-observation"
