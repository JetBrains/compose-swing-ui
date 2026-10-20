package org.jetbrains.compose.swing.samples.agent.harness

import org.jetbrains.annotations.NonNls

/** The tools the app shows in their own way; an agent may call any other, which shows under its own name. */
@NonNls
internal object ToolNames {
    const val PLAN = "plan"
    const val READ = "read"
    const val EDIT = "edit"
}
