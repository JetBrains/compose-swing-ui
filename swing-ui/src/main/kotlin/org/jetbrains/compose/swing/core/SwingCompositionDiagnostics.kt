// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.modifier.ModifierInfo
import java.awt.Component
import kotlin.coroutines.CoroutineContext

/**
 * The debug-only checks the compositions on one recomposer are held to, installed as an element of the
 * coroutine context that recomposer was built over.
 *
 * A composition reads it as it is mounted, and one nested under it reads the same element through its
 * parent's effect context - a cell renderer's composition, a menu's, a window's. Nothing installs one in
 * production.
 *
 * A test harness also learns through it which content compositions exist under the recomposer. A content
 * composition mounted under that recomposer - on a free host, a composed popup menu, a window's content
 * pane - announces itself as it is mounted. A composition that only renders on demand - a cell renderer's,
 * a tab header's - announces nothing, so what a component paints from is never a root. A root gets exactly
 * one created call and, once that call has been made, exactly one disposed call, whichever way the
 * composition ends; a root whose created call threw still gets its disposed call.
 *
 * Every call runs on the event dispatch thread. [declaring] and [restoring] run from inside the change pass
 * that provoked them, and a violation there is reported rather than thrown: a throw from inside a modifier
 * write reaches the apply phase and ends the recomposer for good.
 *
 * Marked [InternalSwingUiApi]; it may change or be removed without notice in any release.
 */
@InternalSwingUiApi
public interface SwingCompositionDiagnostics : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    /**
     * Reads [info], then invokes [write] exactly once before returning.
     *
     * [info] is the same instance for every write of one installed declaration, and for its [restoring]. Read
     * its properties before invoking [write]. An exception from [write] propagates.
     *
     * @param info the identity scoped to one installed declaration; read its properties before invoking [write].
     * @param write the declaration action to invoke exactly once before returning.
     */
    public fun declaring(
        info: ModifierInfo,
        write: () -> Unit,
    )

    /**
     * Reads [info], then invokes [restore] exactly once before returning, putting the component back where the
     * departing declaration found it.
     *
     * Read [info]'s properties before invoking [restore]. An exception from [restore] propagates.
     *
     * @param info the same identity supplied for the installed declaration.
     * @param restore the restoration action to invoke exactly once before returning.
     */
    public fun restoring(
        info: ModifierInfo,
        restore: () -> Unit,
    )

    /**
     * Announces [root], whose composition is initialized but has not composed its content yet.
     *
     * @param root the new root; the same instance is passed to [onRootForTestDisposed].
     */
    public fun onRootForTestCreated(root: RootForTest) {}

    /**
     * Withdraws [root], whose composition has ended or failed to start.
     *
     * @param root the root passed to [onRootForTestCreated].
     */
    public fun onRootForTestDisposed(root: RootForTest) {}

    /** The key this element is read off a coroutine context by. */
    @InternalSwingUiApi
    public companion object Key : CoroutineContext.Key<SwingCompositionDiagnostics>
}

/**
 * A content composition a test harness can search: the component one `setContent`-style mount is rooted on.
 *
 * Owned by the composition that implements it; a harness only reads [component] while the root is announced
 * to it and holds the root from [SwingCompositionDiagnostics.onRootForTestCreated] until
 * [SwingCompositionDiagnostics.onRootForTestDisposed].
 *
 * Marked [InternalSwingUiApi]; it may change or be removed without notice in any release.
 */
@InternalSwingUiApi
public interface RootForTest {
    /** The component this root's content is composed into. Read on the event dispatch thread. */
    public val component: Component
}

/**
 * Runs [write] - what the declaration behind [info] writes onto its component - under the checks the
 * composition is held to, or plainly where it is held to none.
 *
 * A composition outside a test names no diagnostics, and takes the `null` branch, which inlines the write
 * in place and builds no lambda for it.
 *
 * @param info the identity of the installed declaration.
 * @param write what the declaration writes onto its component.
 */
internal inline fun SwingCompositionDiagnostics?.watchWrite(
    info: ModifierInfo,
    crossinline write: () -> Unit,
) {
    if (this == null) write() else declaring(info) { write() }
}

/**
 * [watchWrite], for the departing declaration behind [info] putting its component back where it found it.
 *
 * @param info the identity of the installed declaration.
 * @param restore what the declaration does to put its component back.
 */
internal inline fun SwingCompositionDiagnostics?.watchRestore(
    info: ModifierInfo,
    crossinline restore: () -> Unit,
) {
    if (this == null) restore() else restoring(info) { restore() }
}
