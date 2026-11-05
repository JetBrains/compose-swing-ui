package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.core.SwingContentComposition
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.core.disposingOnFailure
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.RootSlotPolicy
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.checkOneChildPerRegion
import org.jetbrains.compose.swing.node.declaredName
import org.jetbrains.compose.swing.node.isLeftOutUnder
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.SwingUtilities

/**
 * A composition of Swing `content` under `parent`, with no container of its own.
 *
 * The content shares the parent's state and [CompositionLocal][androidx.compose.runtime.CompositionLocal]s
 * and recomposes when state it reads changes. [recompose] also runs a pass synchronously, on demand.
 *
 * The top-level component is in no container of this composition, so no layout reads its parent data and it
 * belongs to no region of this composition. A pass that composes a second top-level component, or a top-level
 * component that names a region or carries parent data, throws [IllegalStateException] after disposing this
 * composition, the pass on construction included. A failure thrown by the placement of a placed composition
 * does the same. Both leave the caller nothing to dispose. A top-level component that names a region whose
 * parent kind the composition's root does not match is refused while the pass applies, and leaves this
 * composition undisposed: the caller disposes it, and that disposal can throw too.
 *
 * Created by the constructor without a host, the composition places the component nowhere: [recompose]
 * returns it to its caller, the way a cell renderer returns the component a widget paints. A component leaving
 * the composition is removed from whatever container it is in.
 *
 * Must be constructed, recomposed, and disposed on the Event Dispatch Thread.
 */
public class OnDemandComposition private constructor(
    parent: CompositionContext,
    /** The node of the composition's root, which receives its layout and repaint requests. */
    private val rootHolder: CreatedNodeHolder<Container>,
    /** Installs the top-level component and returns the action that removes it. */
    private val placement: SlotAttachment,
    content:
        @Composable @SwingComposable
        () -> Unit,
) : DisposableHandle {
    /**
     * Creates a composition that places nothing: [recompose] returns its top-level component.
     *
     * Its refusals are those of [OnDemandComposition].
     *
     * @param parent the composition context this composition joins.
     * @param content content composed on construction, by [recompose], and when state it reads changes.
     */
    public constructor(
        parent: CompositionContext,
        content:
            @Composable @SwingComposable
            () -> Unit,
    ) : this(parent, CreatedNodeHolder(Container()), RemovingFromItsParent, content)

    /**
     * Creates a composition whose top-level component [placement] places.
     *
     * [placement] decides where the component goes, and [host] receives the composition's layout and repaint
     * requests wherever [placement] puts it. [placement] is called each time a pass composes a new top-level
     * component: the first pass on construction, a [recompose], or a pass that runs because state the content
     * reads changed. The action it returns runs when that component leaves the composition, including on
     * [dispose]. While [content] composes no component, none is placed. [placement] and the action it returns
     * must not call [recompose] or [dispose] on this composition.
     *
     * A pass refuses what [OnDemandComposition] lists. A failure [placement] or the action it returns throws
     * reaches the caller as thrown.
     *
     * @param parent the composition context this composition joins.
     * @param host the container handed to [placement], which receives the composition's layout and repaint
     *   requests.
     * @param placement installs the top-level component and returns the action that removes it.
     * @param content content composed on construction, by [recompose], and when state it reads changes.
     */
    public constructor(
        parent: CompositionContext,
        host: Container,
        placement: SlotAttachment,
        content:
            @Composable @SwingComposable
            () -> Unit,
    ) : this(parent, CreatedNodeHolder(host), placement, content)

    // Releases each component installed and not yet released, in the order installed.
    private val installed = LinkedHashMap<Component, () -> Unit>()

    // The top-level component most recently installed and not yet released.
    private val current: Component? get() = installed.keys.lastOrNull()

    // What the placement threw while a pass was applying, reported once that pass has been applied.
    private var placementFailure: Throwable? = null

    // Prevents recursive recomposition during applier flush.
    private var recomposing = false

    // Both constructors install the root through this attachment.
    private val rootAttachment = SlotAttachment { host, component, index -> installRoot(host, component, index) }

    private val composition: SwingContentComposition

    init {
        checkEventDispatchThread()
        rootHolder.childPlacement = ChildPlacement.Slots(content = rootAttachment)
        composition =
            SwingContentComposition.nested(
                parent,
                rootHolder,
                compositionDataHost = null,
                applierFactory = { root ->
                    SwingApplier(root, rootSlotPolicy = RootSlotPolicy { requireOneTopLevelComponent() })
                },
            )
        disposingOnFailure(::dispose) {
            composition.setContent(content)
            requireOneTopLevelComponent()
        }
    }

    /**
     * Installs [component] through the caller's placement and returns the action that releases it.
     *
     * A failure of the placement, installing or releasing, is deferred and reported by
     * [requireOneTopLevelComponent] once the pass has been applied, or by [dispose] where the release ran in
     * it. Both calls run while the pass applies, and a throw out of them would stop that pass partway and
     * reach the caller with the composition undisposed.
     *
     * @param host the container of the composition's root, which is the host of a placed composition.
     * @param component the top-level component the pass composed.
     * @param index the position the applier gives [component] among the root's content, which says nothing
     *   about where the placement puts it.
     */
    private fun installRoot(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit {
        var installation: (() -> Unit)? = null
        placementFailure = collectFailure(placementFailure) { installation = placement.install(host, component, index) }
        val release = installation ?: return {}
        val releaseComponent: () -> Unit = {
            installed -= component
            placementFailure = collectFailure(placementFailure) { release() }
        }
        installed[component] = releaseComponent
        return releaseComponent
    }

    /** Throws the placement failure deferred since the last report, if any. */
    private fun reportPlacementFailure() {
        val failure = placementFailure ?: return
        placementFailure = null
        throw failure
    }

    /**
     * Runs [update], then recomposes and applies this composition synchronously, so its Swing subtree is
     * fully materialized before this returns. The write is recorded against this composition so the
     * synchronous recompose sees the change; this takes no frame from a window recomposer.
     *
     * A call from inside another [recompose] runs neither [update] nor a recompose, and answers the
     * component the outer call is already in the middle of producing - the same component this call
     * would otherwise materialize again. Once this composition is [dispose]d, [update] does not run.
     * After disposal this returns `null`.
     *
     * A refused pass throws [IllegalStateException], as [OnDemandComposition] describes. Later calls follow the
     * disposed-composition behavior unless the refusal left this composition undisposed.
     *
     * Must be called on the Event Dispatch Thread.
     *
     * @param update writes whatever state [content] reads before this recomposes.
     * @return the one top-level component [content] composes, or `null` where it composes none.
     */
    public fun recompose(update: () -> Unit): Component? {
        checkEventDispatchThread()
        if (recomposing) return current
        recomposing = true
        try {
            composition.recomposeSynchronously(update)
            requireOneTopLevelComponent()
            return current
        } finally {
            recomposing = false
        }
    }

    /**
     * Disposes this composition. After disposal, [recompose] returns `null`.
     *
     * Disposal failures reach the caller. A failing disposal callback can leave later content callbacks
     * uncalled. Repeated disposal does not retry them.
     *
     * The top-level component leaves the composition even when a teardown in the content throws, and that
     * failure then reaches the caller.
     *
     * Must be called on the Event Dispatch Thread.
     */
    override fun dispose() {
        checkEventDispatchThread()
        val vacated = areasOfInstalledComponents()
        try {
            // A release failing in the same disposal is suppressed into the disposal's own failure.
            disposingOnFailure(::finishDisposal) { composition.dispose() }
            finishDisposal()
        } finally {
            if (vacated.isNotEmpty()) {
                rootHolder.component.revalidate()
                vacated.fastForEach { rootHolder.component.repaint(it.x, it.y, it.width, it.height) }
            }
        }
    }

    /**
     * The areas the installed components cover, in the host's coordinates. A disposal runs outside a pass,
     * so nothing else asks the host to lay out and paint again where they leave.
     */
    private fun areasOfInstalledComponents(): List<Rectangle> =
        installed.keys.mapNotNull { component ->
            component.parent?.let { SwingUtilities.convertRectangle(it, component.bounds, rootHolder.component) }
        }

    /**
     * Releases the components the disposal left in place, then reports a placement failure deferred since the
     * last report. The runtime runs the content's teardowns last to first and stops at the first that throws,
     * so one composed after the component skips its release.
     */
    private fun finishDisposal() {
        while (installed.isNotEmpty()) installed.values.first()()
        reportPlacementFailure()
    }

    /**
     * Checks, once the pass has been applied, that the content composes at most one top-level component and
     * that it names no region: the placement decides the region, so a component naming one, created or
     * claimed, is refused. A plain `index` naming where the applier installed a component says nothing here,
     * since every one of them is the root's content.
     *
     * Reuses [checkOneChildPerRegion]'s check of the root's children. When it or the region check refuses, or
     * the placement threw while the pass applied, it disposes this composition and reports the refusal.
     */
    private fun requireOneTopLevelComponent() {
        disposingOnFailure(::dispose) {
            reportPlacementFailure()
            rootHolder.unfoldedChildren.fastForEach { child ->
                check(
                    child.declaration.parentProtocol == null &&
                        child.declaration.parentLayoutElements.fastFirstOrNull {
                            !it.isLeftOutUnder(rootHolder.component)
                        } == null,
                ) {
                    "A ${child.component.declaredName} at the top level of this composition carries parent data, " +
                        "but the top-level component is in no container of this composition, so nothing reads it. " +
                        "Remove the parent data, or compose the component inside a container that accepts it."
                }
                check(child.declaredSlot == null) {
                    "A ${child.component.declaredName} at the top level of this composition names the region " +
                        "${child.declaredSlot?.name}, but the top-level component belongs to no region of this " +
                        "composition, so it names none. Remove the call that names the region, or compose the " +
                        "component inside a container that offers it."
                }
            }
            rootHolder.checkOneChildPerRegion { _, first, second ->
                "The content of this composition composes two top-level components, a ${first.declaredName} and a " +
                    "${second.declaredName}. Compose them into one container."
            }
        }
    }
}

/**
 * The placement of a composition with no host: the component stays with the caller, and a release removes it
 * from the container it is in.
 */
private val RemovingFromItsParent = SlotAttachment { _, component, _ -> { component.parent?.remove(component) } }
