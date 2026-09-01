@file:JvmName("ToolingKt")

package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionErrorContext
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.LocalCompositionErrorContext
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JComponent

/**
 * Client property key under which a composition open to inspection publishes its slot table, on the
 * component its content is rooted at. Written only while [isDebugInspectorInfoEnabled] is on, and
 * cleared when the composition behind it is disposed.
 */
private val COMPOSITION_DATA_KEY: Key<CompositionData> = Key("org.jetbrains.compose.swing.compositionData")

/** Publishes [data] on this component, or clears what is published when passed `null`. */
internal fun JComponent.publishCompositionData(data: CompositionData?) {
    this[COMPOSITION_DATA_KEY] = data
}

/** The slot table published on this component, or `null` where none is. */
internal fun JComponent.publishedCompositionData(): CompositionData? = this[COMPOSITION_DATA_KEY]

/**
 * Whether the compositions this library mounts record where each component was declared, which is what
 * [findDeclaringGroup], [findCompositionData] and [attachComposeStackTrace] answer from. `false` by
 * default.
 *
 * Every composition reads this, so turning it on reaches the ones already running as well as the ones
 * mounted afterwards. Each rebuilds its content afresh and records as it composes, because the runtime
 * writes source information only as content is inserted. That rebuild lands on a later recomposition
 * pass, so a composition answers once it has recomposed rather than by the time this returns, and it
 * discards everything `remember`ed inside the content while state hoisted above it survives.
 *
 * Turning it off reverses both: each composition builds its content afresh once more, this time without
 * recording, and withdraws what it published. A composition answers for what it declared only while this
 * is on.
 *
 * A composition reads this when its own pass runs, not when the assignment returns, so turning it on and
 * off again within one turn of the event loop leaves everything as it was.
 *
 * Content mounted under a context a caller captured with `rememberCompositionContext()` is reached on
 * the next pass it takes for any reason, rather than on one this brings about: what such a composition
 * reads of this stands above its content, and invalidating that reaches the composition around it rather
 * than the composition itself. Content a window drives is reached at once.
 *
 * [attachComposeStackTrace] needs one thing more of the application: the Compose runtime hands out what a
 * trace is built from only while it is in a diagnostic stack trace mode, which
 * `Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)` puts it in. That is the
 * application's to set - it is process-wide - so this switch neither sets it nor puts it back.
 * [findCompositionData] answers in full without it.
 *
 * Must be set on the Event Dispatch Thread.
 */
public var isDebugInspectorInfoEnabled: Boolean = false
    set(value) {
        checkEventDispatchThread()
        if (value == field) return
        field = value
        // Each composition is invalidated whole rather than left to observe this. A composition mounted
        // under a context a caller captured is not reached by an observed read of the switch, by
        // invalidating the scope that reads it, or by a static local above it - measured, all three -
        // because what reads the switch stands at the top of that composition. Invalidating the
        // composition itself is what the runtime offers for reaching every scope in it.
        InspectionGate.followAll(value)
    }

/**
 * Whether one composition records where it declared each component, following
 * [isDebugInspectorInfoEnabled] for as long as that composition is mounted.
 *
 * A composition reads its own gate above its content, so the switch writing one invalidates that whole
 * composition rather than a single scope in it - which is what reaches a composition mounted under a
 * context a caller captured, and a scope-level invalidation does not.
 *
 * A gate registers itself and is held weakly, so a composition that ends takes its gate with it and
 * nothing has to be told.
 */
internal class InspectionGate {
    private val recording = mutableStateOf(isDebugInspectorInfoEnabled)

    /** Read this from above the content it governs, never from a scope inside it. */
    val isRecording: Boolean
        get() = recording.value

    init {
        gates.add(this)
    }

    private fun follow(value: Boolean) {
        recording.value = value
    }

    companion object {
        private val gates: MutableSet<InspectionGate> = Collections.newSetFromMap(WeakHashMap())

        /** Points every live composition's gate at [value]. */
        fun followAll(value: Boolean) {
            for (gate in gates.toList()) gate.follow(value)
        }
    }
}

/**
 * Records where each component of the composition around this call was declared, and publishes its slot
 * table on [host], for as long as this call stands in that composition.
 *
 * The caller decides when it stands: it is declared while [isDebugInspectorInfoEnabled] is on and gone
 * when it goes off, and the caller keys its content on the same answer so the pass that follows inserts
 * that content afresh. Re-insertion is what lets a composer that has been told to record do so for
 * content that was already built.
 */
@Composable
internal fun InspectedContent(
    host: JComponent?,
    inspecting: Boolean,
    content: @Composable () -> Unit,
) {
    val recording = host?.takeIf { inspecting }
    if (recording != null) InspectedEffect(recording)
    // Keyed on the answer: building afresh discards everything the content remembered; state hoisted
    // above it survives.
    key(recording != null) { content() }
}

@Composable
private fun InspectedEffect(host: JComponent) {
    val composer = currentComposer
    // Asked during the pass rather than from an effect: a group already in the slot table can never gain
    // source information, so this has to take hold before the content below is inserted, and an effect
    // runs only once the pass has applied.
    //
    // Asked once and no more, because asking resets the slot table's source information: a second call
    // would throw away everything recorded so far and leave a trace naming no file. The one time it is
    // asked again is when this call stands afresh after the switch went off and on, and the content it
    // records for is re-inserted on that same pass.
    remember { composer.collectParameterInformation() }
    val data = composer.compositionData
    DisposableEffect(Unit) {
        host.publishCompositionData(data)
        // Only this composition's own publication is withdrawn, so a teardown can never blind a live
        // composition that published on the same host after it.
        onDispose { if (host.publishedCompositionData() === data) host.publishCompositionData(null) }
    }
}

/**
 * The group that declared this component, or `null` where no composition this library mounted declared
 * it - a component built by hand, and one whose composition has not recorded yet.
 *
 * [CompositionGroup.node] holds the [SwingComponentNode] for the component. The group itself does not
 * carry the declaration site; [attachComposeStackTrace] is what leads back to the declaring code.
 *
 * The search starts at this component and walks up its Swing ancestors, and the nearest composition that
 * declared it wins: a component declared inside a nested `setContent` composition is answered by that
 * composition rather than by the one around it, and a component that hosts a content composition of its
 * own is still answered with the group that declared it.
 *
 * The answer reads the composition's slot table as it stands, and the next recomposition of that
 * composition retires it: descending a group held across one fails. Read what you need while the answer
 * is fresh and ask again afterwards, holding [CompositionGroup.identity] in between.
 *
 * A composition answers only once it has recorded - one mounted while [isDebugInspectorInfoEnabled] was
 * on, or one the switch reached afterwards.
 *
 * Must be called on the Event Dispatch Thread.
 */
public fun Component.findDeclaringGroup(): CompositionGroup? {
    checkEventDispatchThread()
    return publishedCompositionData().firstNotNullOfOrNull { it.declarationOf(this) }
}

/**
 * The data of the nearest composition this component hosts or stands in, or `null` where there is none.
 * It answers whether or not that composition declared the component, so a component built by hand and
 * added to composed content is answered with the composition it stands in.
 *
 * The walk starts at this component, so one that hosts a content composition of its own is answered with
 * that composition - the one it carries is nearer than the one that declared it. Use
 * [findDeclaringGroup] for the other question: which group declared a component.
 *
 * A composition rooted at a [java.awt.Container] that is no [JComponent] is never found, neither for the
 * components it declared nor for the ones standing in it.
 *
 * The same freshness rule as [findDeclaringGroup] applies: the next recomposition of that composition
 * retires the answer.
 *
 * Must be called on the Event Dispatch Thread.
 */
public fun Component.findCompositionData(): CompositionData? {
    checkEventDispatchThread()
    return publishedCompositionData().firstOrNull()
}

/**
 * Attaches the composition stack trace of this component's declaration to [throwable] as a suppressed
 * exception, and reports whether it did. The trace names the composable that declared the component, the
 * calls that led there and the file and line of each.
 *
 * The composition it names is the one [findDeclaringGroup] answers from.
 *
 * `false` means no trace was attached, for any of:
 * - the application has not put the Compose runtime into a diagnostic stack trace mode, which
 *   `Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)` puts it in and is the
 *   application's to set rather than this library's;
 * - no composition this library mounted declared the component;
 * - the composition has not recorded source information yet - the re-insertion
 *   [isDebugInspectorInfoEnabled] asks of an already-mounted composition lands on a later recomposition
 *   pass;
 * - [throwable] already carries a composition stack trace;
 * - the composition recorded no source information at all, which is what a build the Compose compiler
 *   emits none for leaves;
 * - the composition's content provides no [androidx.compose.runtime.CompositionLocal], so the runtime
 *   recorded no scope for it and there is nothing to build a trace from.
 *
 * A failure raised while the trace is being built is suppressed onto [throwable] in the trace's place,
 * and the return stays `false`.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param throwable the throwable the trace is suppressed onto, in flight or newly built - it is the one
 *   the trace travels with, so attach to what is thrown rather than to a copy.
 * @return whether this call attached a trace.
 */
public fun Component.attachComposeStackTrace(throwable: Throwable): Boolean {
    checkEventDispatchThread()
    val attachable =
        publishedCompositionData().firstNotNullOfOrNull { data ->
            data.errorContextOrNull()?.let { errorContext ->
                data.declarationOf(this)?.node?.let { node -> errorContext to node }
            }
        } ?: return false
    val (errorContext, node) = attachable
    return with(errorContext) { throwable.attachComposeStackTrace(node) }
}

/** Every composition published on this component or on one of its Swing ancestors, nearest first. */
private fun Component.publishedCompositionData(): Sequence<CompositionData> =
    generateSequence(this) { it.parent }.mapNotNull { (it as? JComponent)?.publishedCompositionData() }

/**
 * What the Compose runtime builds a composition stack trace for a node of this composition from, or
 * `null` where this composition records none.
 *
 * The runtime provides it as [LocalCompositionErrorContext] at the root of a composition that records
 * source information, and only while the application has put it in a diagnostic stack trace mode. It is
 * read back off the slot table rather than held anywhere, so what answers here is what the composition
 * has now, and a composition whose content records no scope at all answers `null`.
 */
private fun CompositionData.errorContextOrNull(): CompositionErrorContext? =
    localMaps().firstNotNullOfOrNull { it[LocalCompositionErrorContext] }

/** Every [CompositionLocalMap] the composer wrote into this data, outermost first. */
private fun CompositionData.localMaps(): Sequence<CompositionLocalMap> =
    compositionGroups.asSequence().flatMap { group ->
        group.data.asSequence().filterIsInstance<CompositionLocalMap>() + group.localMaps()
    }

/**
 * The group under this data whose node holds [component], or `null` when none does. A composition nests
 * far deeper than the declaring code shows, so the search runs with no depth cap.
 */
private fun CompositionData.declarationOf(component: Component): CompositionGroup? =
    compositionGroups.firstNotNullOfOrNull { group ->
        group.takeIf { (it.node as? SwingComponentNode)?.component === component }
            ?: group.declarationOf(component)
    }
