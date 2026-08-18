package org.jetbrains.compose.swing.preview.host

import org.jetbrains.compose.swing.tooling.PreviewEnvironment
import javax.swing.UIManager

/**
 * The environment this module's own previews are rendered under, registered in `META-INF/services` the
 * way a project registers its own. It installs a look and feel no JVM starts with, so that a rendering
 * taken under it can be told apart from one taken without it.
 */
class NimbusTestEnvironment : PreviewEnvironment {
    override fun prepare() {
        UIManager.setLookAndFeel(NIMBUS)
    }

    companion object {
        const val NIMBUS: String = "javax.swing.plaf.nimbus.NimbusLookAndFeel"
    }
}
