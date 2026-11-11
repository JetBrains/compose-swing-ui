// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.todo

import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.application

/**
 * The runnable entry point. Window creation and close behavior are plain plumbing; the composable UI
 * knows nothing about windows, so the same composable runs in tests with no main at all.
 */
fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Compose Swing UI - To-do",
        ) {
            ReactiveTaskListScreen()
        }
    }
