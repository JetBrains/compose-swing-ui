// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import java.awt.Component
import java.awt.Container
import java.awt.event.ActionListener
import java.awt.event.AdjustmentListener
import java.awt.event.ItemListener
import java.beans.VetoableChangeListener
import java.nio.ByteBuffer
import java.util.Collections
import java.util.EventListener
import java.util.EventListenerProxy
import java.util.IdentityHashMap
import java.util.zip.CRC32C
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JProgressBar
import javax.swing.JScrollBar
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.event.CaretListener
import javax.swing.event.CellEditorListener
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkListener
import javax.swing.event.InternalFrameListener
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionListener
import javax.swing.event.MenuDragMouseListener
import javax.swing.event.MenuKeyListener
import javax.swing.event.MenuListener
import javax.swing.event.MouseInputListener
import javax.swing.event.PopupMenuListener
import javax.swing.event.RowSorterListener
import javax.swing.event.TableColumnModelListener
import javax.swing.event.TableModelListener
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeModelListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.event.UndoableEditListener
import javax.swing.text.JTextComponent
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

/**
 * A stable hash of the values visible in a Swing tree. Call on the EDT.
 *
 * A benchmark trial records it at the initial values and after the alternative, and checks within that trial that the
 * tree returns to the initial digest after two changes and ends on the digest its number of changes implies. The
 * memory measurement reports the digests of both arms as metrics. Digests are not compared across library versions.
 *
 * @param host the root of the tree to hash, including the root itself.
 */
fun componentDigest(host: Component): Long = ComponentDigest().apply { visit(host) }.value

/**
 * Feeds the structure, bounds, style and widget values of a tree into one checksum.
 *
 * Every string is length-prefixed so that adjacent values cannot merge into the same bytes, and a null differs from an
 * empty string. Model contents are hashed in full, not only the selected part, because a toggle may change an
 * unselected row.
 */
private class ComponentDigest {
    private val checksum = CRC32C()

    /** The checksum of everything visited so far. */
    val value: Long
        get() = checksum.value

    fun visit(component: Component) {
        add(component.javaClass.name)
        add(component.name)
        add(component.isEnabled.toString())
        add(component.isVisible.toString())
        add(component.x.toString())
        add(component.y.toString())
        add(component.width.toString())
        add(component.height.toString())
        addWidgetValue(component)
        add(component.foreground?.rgb?.toString())
        add(component.background?.rgb?.toString())
        add(component.font?.name)
        add(component.font?.size?.toString())
        add(component.font?.style?.toString())
        val children = (component as? Container)?.components.orEmpty()
        add(children.size.toString())
        children.forEach(::visit)
    }

    private fun addWidgetValue(component: Component) {
        addTextValue(component)
        addNumericValue(component)
        addSelectionValue(component)
    }

    private fun addTextValue(component: Component) {
        when (component) {
            is JLabel -> {
                add(component.text)
            }

            is AbstractButton -> {
                add(component.text)
                add(component.isSelected.toString())
            }

            is JTextComponent -> {
                add(component.text)
            }
        }
    }

    private fun addNumericValue(component: Component) {
        when (component) {
            is JSlider -> {
                add(component.value.toString())
            }

            is JProgressBar -> {
                add(component.value.toString())
            }

            is JScrollBar -> {
                add(component.value.toString())
            }

            is JSpinner -> {
                add(component.value.toString())
            }
        }
    }

    private fun addSelectionValue(component: Component) {
        when (component) {
            is JComboBox<*> -> {
                add(component.selectedIndex.toString())
                add(component.selectedItem?.toString())
            }

            is JList<*> -> {
                add(component.selectedIndices.joinToString(","))
                add(component.model.size.toString())
                repeat(component.model.size) { add(component.model.getElementAt(it)?.toString()) }
            }

            is JTable -> {
                add(component.selectedRows.joinToString(","))
                add(component.selectedColumns.joinToString(","))
                add(component.rowCount.toString())
                add(component.columnCount.toString())
                repeat(component.rowCount) { row ->
                    repeat(component.columnCount) { column -> add(component.getValueAt(row, column)?.toString()) }
                }
            }

            is JTree -> {
                add(component.selectionRows?.joinToString(","))
                component.model.root?.let { addTreeNode(component.model, TreePath(it)) }
                component.selectionPaths?.forEach { path ->
                    add(path.pathCount.toString())
                    path.path.forEach { add(it.toString()) }
                }
            }
        }
    }

    private fun addTreeNode(
        model: TreeModel,
        path: TreePath,
    ) {
        val node = path.lastPathComponent
        add(node.toString())
        val children = model.getChildCount(node)
        add(children.toString())
        repeat(children) { addTreeNode(model, path.pathByAddingChild(model.getChild(node, it))) }
    }

    private fun add(text: String?) {
        val bytes = text?.toByteArray(Charsets.UTF_8)
        val length = bytes?.size ?: -1
        checksum.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(length).array())
        bytes?.let(checksum::update)
    }
}

/**
 * Every listener registered on one component, by identity.
 *
 * Listeners that compare equal are still distinct objects, and a replacement must count as a creation, so identity is
 * the key. A named property listener comes back wrapped in a fresh proxy on each call, so the wrapper is unwrapped to
 * its registered target.
 */
internal fun componentListeners(component: Component): Set<EventListener> {
    val registered = Collections.newSetFromMap(IdentityHashMap<EventListener, Boolean>())
    registered.addAll(component.componentListeners)
    registered.addAll(component.focusListeners)
    registered.addAll(component.hierarchyListeners)
    registered.addAll(component.hierarchyBoundsListeners)
    registered.addAll(component.keyListeners)
    registered.addAll(component.mouseListeners)
    registered.addAll(component.mouseMotionListeners)
    registered.addAll(component.mouseWheelListeners)
    registered.addAll(component.inputMethodListeners)
    registered.addAll(component.propertyChangeListeners)
    if (component is Container) registered.addAll(component.containerListeners)
    if (component is JComponent) {
        registered.addAll(component.ancestorListeners)
    }
    listenerTypes.forEach { type -> registered.addAll(component.getListeners(type)) }
    // Named property listeners are returned wrapped in a fresh proxy per call; identity needs the registered target.
    val listeners = Collections.newSetFromMap(IdentityHashMap<EventListener, Boolean>())
    registered.mapTo(listeners) { listener ->
        generateSequence(listener) { (it as? EventListenerProxy<*>)?.listener }.last()
    }
    return listeners
}

/** The listener types that have no public getter on [Component] or [JComponent]. */
private val listenerTypes: List<Class<out EventListener>> =
    listOf(
        ActionListener::class.java,
        AdjustmentListener::class.java,
        ItemListener::class.java,
        VetoableChangeListener::class.java,
        CaretListener::class.java,
        CellEditorListener::class.java,
        ChangeListener::class.java,
        DocumentListener::class.java,
        HyperlinkListener::class.java,
        InternalFrameListener::class.java,
        ListDataListener::class.java,
        ListSelectionListener::class.java,
        MenuDragMouseListener::class.java,
        MenuKeyListener::class.java,
        MenuListener::class.java,
        MouseInputListener::class.java,
        PopupMenuListener::class.java,
        RowSorterListener::class.java,
        TableColumnModelListener::class.java,
        TableModelListener::class.java,
        TreeExpansionListener::class.java,
        TreeModelListener::class.java,
        TreeSelectionListener::class.java,
        TreeWillExpandListener::class.java,
        UndoableEditListener::class.java,
    )
