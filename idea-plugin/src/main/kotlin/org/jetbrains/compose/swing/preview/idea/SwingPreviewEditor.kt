package org.jetbrains.compose.swing.preview.idea

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.psi.KtFile
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Long enough that a burst of triggers - a build finishing while the file opens - renders once. */
private val COALESCE = 150.milliseconds

/** Long enough that a compile follows a pause in typing rather than a keystroke. */
private val SETTLE_AFTER_TYPING = 1.seconds

/** Long enough that a render follows a divider coming to rest rather than every pixel it passes. */
private val SETTLE_AFTER_DRAGGING = 400.milliseconds

/** Turns off compiling the edited file to keep the previews current. */
private const val COMPILE_ON_EDIT_KEY = "compose.swing.preview.compile.on.edit"

/**
 * The preview half of the editor: every `@Preview` in the file, rendered from what the module has
 * been compiled to.
 *
 * A rendering is produced from compiled classes rather than from the text on screen, so an edit only
 * reaches a preview through the compiler. Editing therefore compiles the edited file once typing
 * pauses, and every compile that finishes - this one, or a build started anywhere else - renders the
 * previews again. Only the edited file is compiled, not its module, which is what keeps the round trip
 * short enough to style a component by typing at it.
 *
 * Nothing is rendered while the preview is hidden; what arrived meanwhile is rendered when it is shown
 * again, so a build with several such editors open does not spawn a host for each hidden one.
 */
@OptIn(FlowPreview::class)
internal class SwingPreviewEditor(
    private val project: Project,
    private val file: VirtualFile,
    document: Document?,
    private val scope: CoroutineScope,
) : UserDataHolderBase(),
    FileEditor {
    private val previews = SwingPreviewPanel()
    private val status = JBLabel()
    private val root = JBPanel<JBPanel<*>>(BorderLayout())
    private val refreshRequests =
        MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val compileRequests =
        MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val resizeRequests =
        MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Set when a render was asked for while hidden, and what makes being shown ask again. */
    @Volatile
    private var missedWhileHidden = false

    /** Whether the file declared a preview when it was last looked at; `null` until it has been. */
    @Volatile
    private var declaredPreviews: Boolean? = null

    /** The room the renderings on screen were laid out within; `null` until something has rendered. */
    @Volatile
    private var laidOutWithin: Int? = null

    /** The widest rendering on screen, which is what says whether that room is binding on anything. */
    @Volatile
    private var widest = 0

    init {
        root.add(toolbar(), BorderLayout.NORTH)
        root.add(previews, BorderLayout.CENTER)

        refreshRequests
            .debounce(COALESCE)
            .onStart { emit(Unit) }
            .onEach { refresh() }
            .launchIn(scope)

        compileRequests
            .debounce(SETTLE_AFTER_TYPING)
            .onEach { compileEditedFile() }
            .launchIn(scope)

        root.addHierarchyListener { event ->
            val shown = event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L
            if (shown && root.isShowing && missedWhileHidden) refreshRequests.tryEmit(Unit)
        }

        // Asked for rather than rendered here, so that every render this editor does goes through the one
        // collector and a resize settling as a build finishes does not start a second host beside it.
        resizeRequests
            .debounce(SETTLE_AFTER_DRAGGING)
            .onEach {
                val room = withContext(Dispatchers.EDT) { previews.contentWidth() }
                if (layoutWouldChange(room)) refreshRequests.tryEmit(Unit)
            }.launchIn(scope)

        previews.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    resizeRequests.tryEmit(Unit)
                }
            },
        )

        // Any build can be the one that compiled this file: a preview in one module is rendered from
        // the output of every module it depends on.
        project.messageBus.connect(this).subscribe(
            ProjectTaskListener.TOPIC,
            object : ProjectTaskListener {
                override fun finished(result: ProjectTaskManager.Result) {
                    if (!result.isAborted && !result.hasErrors()) {
                        refreshRequests.tryEmit(Unit)
                        return
                    }
                    // Nothing else would replace what starting the build put on the status line, and a
                    // build that did not finish is the reason the previews on screen are the ones they
                    // are. This is not called on the event dispatch thread.
                    scope.launch(Dispatchers.EDT) { status.text = BUILD_FAILED }
                }
            },
        )

        document?.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    status.text = OUT_OF_DATE
                    if (Registry.`is`(COMPILE_ON_EDIT_KEY, true)) compileRequests.tryEmit(Unit)
                }
            },
            this,
        )
    }

    private fun toolbar(): JComponent {
        val actions = DefaultActionGroup(BuildAndRefreshAction())
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actions, true)
        toolbar.targetComponent = previews
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(status.apply { border = JBUI.Borders.emptyLeft(GAP) }, BorderLayout.CENTER)
        }
    }

    /**
     * Compiles the edited file, and leaves rendering to the compile finishing, so an edit reaches the
     * previews the same way a build does.
     *
     * The document is committed and saved first: the compiler reads the file from disk, so an edit that
     * has not been written back would compile to the same classes over and over.
     */
    private suspend fun compileEditedFile() {
        val resolved = resolve()
        if (resolved.targets.isEmpty() || !showing()) return
        edtWriteAction {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            FileDocumentManager.getInstance().saveAllDocuments()
        }
        withContext(Dispatchers.EDT) {
            status.text = COMPILING
            ProjectTaskManager.getInstance(project).compile(file)
        }
    }

    /**
     * Renders every preview in the file from the module's existing output, without building first.
     *
     * A throw here would end the collector and leave the preview dead for the rest of the editor's
     * life, so what resolving a file can throw is shown in place of the renderings instead.
     */
    private suspend fun refresh() {
        try {
            val (module, targets) = resolve()
            if (!showing()) {
                missedWhileHidden = true
                return
            }
            missedWhileHidden = false
            when {
                targets.isEmpty() -> show(NO_PREVIEWS) { previews.showMessage(NO_PREVIEWS) }
                module == null -> show(NOT_IN_A_MODULE) { previews.showMessage(NOT_IN_A_MODULE) }
                else -> render(module, targets)
            }
        } catch (failure: Exception) {
            rethrowControlFlowException(failure)
            show(NOT_RENDERED) { previews.showFailure(failure.stackTraceToString()) }
        }
    }

    private suspend fun showing(): Boolean = withContext(Dispatchers.EDT) { root.isShowing }

    /**
     * What the file declares and which module it belongs to, and - the first look that finds a preview -
     * the preview half of the editor, revealed.
     *
     * A file opened with nothing to preview gets the source alone, so that a project's every Kotlin file
     * does not gain a preview pane it has no use for. Nothing else would ever show it again, which is why
     * the first look that finds one opens it: the look the file was opened with, for a preview the text
     * the editor opened on could not be read as declaring, or the look after the edit that added the
     * first one. Later looks leave the layout alone, so a preview half closed by hand stays closed.
     */
    private suspend fun resolve(): Resolved {
        val resolved =
            readAction {
                val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile
                Resolved(
                    ModuleUtilCore.findModuleForFile(file, project),
                    ktFile?.let { previewsIn(it) }.orEmpty(),
                )
            }
        if (resolved.targets.isNotEmpty() && declaredPreviews != true) revealPreview()
        declaredPreviews = resolved.targets.isNotEmpty()
        return resolved
    }

    private suspend fun revealPreview() =
        withContext(Dispatchers.EDT) {
            FileEditorManager
                .getInstance(project)
                .getAllEditors(file)
                .filterIsInstance<TextEditorWithPreview>()
                .filter { it.getLayout() == TextEditorWithPreview.Layout.SHOW_EDITOR }
                .forEach { it.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW) }
        }

    private class Resolved(
        val module: Module?,
        val targets: List<SwingPreviewTarget>,
    ) {
        operator fun component1(): Module? = module

        operator fun component2(): List<SwingPreviewTarget> = targets
    }

    private suspend fun render(
        module: Module,
        targets: List<SwingPreviewTarget>,
    ) {
        // Both are read where the renderings will be shown. The display's own pixel ratio, because a
        // preview rasterized at one image pixel per layout pixel would be magnified to fit the display
        // and arrive blurred; the width, because a preview is laid out within the room it has.
        val shownIn =
            withContext(Dispatchers.EDT) {
                status.text = "Rendering..."
                JBUIScale.sysScale(previews) to previews.contentWidth()
            }
        val (scale, width) = shownIn
        // Waits on a process, and the previewed code is the user's own: neither belongs on a dispatcher
        // shared with work the IDE needs back.
        val result = withContext(Dispatchers.IO) { renderPreviews(module, targets, scale, width) }
        when (result) {
            is SwingPreviewResult.Rendered -> {
                laidOutWithin = width
                widest = result.groups.flatMap { it.renderings }.maxOfOrNull { it.size.width } ?: 0
                show(rendered(result.groups)) { previews.showGroups(result.groups) }
            }

            is SwingPreviewResult.Failed ->
                show(NOT_RENDERED) { previews.showFailure(result.report) }
        }
    }

    /**
     * Whether laying the previews out within [width] would show anything different from what is on
     * screen, which is what makes a resize worth a render.
     *
     * A render costs a JVM, and dragging the divider is one resize after another. Narrowing changes
     * nothing unless something is currently wider than the new room; widening changes nothing unless
     * something was held to the old room's width and could now grow back.
     */
    private fun layoutWouldChange(width: Int): Boolean {
        val within = laidOutWithin
        return when {
            within == null || width <= 0 || width == within -> false
            width < within -> widest > width
            else -> widest >= within
        }
    }

    private fun rendered(groups: List<SwingPreviewGroup>): String {
        val count = groups.sumOf { it.renderings.size }
        return if (count == groups.size) "${groups.size} previews" else "${groups.size} previews, $count renderings"
    }

    private suspend fun show(
        state: String,
        content: () -> Unit,
    ) = withContext(Dispatchers.EDT) {
        content()
        status.text = state
    }

    /**
     * Builds the module and lets the build's own completion render the previews again, so a build the
     * user starts any other way refreshes them the same way this does.
     */
    private inner class BuildAndRefreshAction :
        AnAction(
            "Build and Refresh",
            "Build the module and render the previews in this file from what it compiles to",
            AllIcons.Actions.Compile,
        ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(event: AnActionEvent) {
            scope.launch {
                val module = readAction { ModuleUtilCore.findModuleForFile(file, project) }
                if (module == null) {
                    show(NOT_IN_A_MODULE) { previews.showMessage(NOT_IN_A_MODULE) }
                    return@launch
                }
                withContext(Dispatchers.EDT) {
                    status.text = "Building..."
                    ProjectTaskManager.getInstance(project).build(module)
                }
            }
        }
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = previews

    override fun getName(): String = "Preview"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() = Unit

    private companion object {
        const val GAP = 8
        const val NO_PREVIEWS = "Nothing in this file is annotated @Preview."
        const val NOT_IN_A_MODULE = "This file belongs to no module, so there is no classpath to render on."
        const val NOT_RENDERED = "Not rendered"
        const val OUT_OF_DATE = "Edited"
        const val COMPILING = "Compiling..."
        const val BUILD_FAILED = "Build failed - these previews are from the last one that succeeded"
    }
}
