package org.jetbrains.compose.swing.preview.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.atomic.AtomicReference

/**
 * Whether this project can hold a preview at all: whether the annotation is on any module's classpath.
 *
 * This is what an editor is offered on, rather than what the file being opened says, because an editor
 * is chosen once when a file opens and never reconsidered. Deciding on the text would mean a file that
 * gains its first `@Preview` while open keeps the editor it was given, and the preview would appear only
 * after closing and reopening it.
 *
 * The answer only changes when the project's dependencies do, so it is resolved once the indexes can
 * answer and dropped when the roots change.
 */
@Service(Service.Level.PROJECT)
internal class SwingPreviewAvailability(
    private val project: Project,
) {
    private val known = AtomicReference<Boolean?>(null)

    /**
     * Must be called under a read action; the first call per project the indexes can answer resolves a
     * class.
     *
     * True while they cannot answer, and that answer is not kept. The one-shot editor choice this
     * serves makes "no" the answer that cannot be taken back: a file opened while the project is being
     * indexed would keep a plain editor for as long as it stays open. Saying yes to a project that turns
     * out to hold no previews costs it a preview half that stays closed.
     */
    fun previewsArePossible(): Boolean = known.get() ?: resolve()?.also { known.compareAndSet(null, it) } ?: true

    /** Null while the indexes this reads are still being built, which is no answer rather than no. */
    private fun resolve(): Boolean? =
        try {
            JavaPsiFacade
                .getInstance(project)
                .findClass("$PREVIEW_PACKAGE.Preview", GlobalSearchScope.allScope(project)) != null
        } catch (stillIndexing: IndexNotReadyException) {
            null
        }

    /** Forgets the answer, so the next question resolves it again. */
    fun forget() {
        known.set(null)
    }

    /** Forgets the answer when the project's dependencies change, which is the only thing that alters it. */
    internal class Invalidator(
        private val project: Project,
    ) : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
            project.service<SwingPreviewAvailability>().forget()
        }
    }
}
