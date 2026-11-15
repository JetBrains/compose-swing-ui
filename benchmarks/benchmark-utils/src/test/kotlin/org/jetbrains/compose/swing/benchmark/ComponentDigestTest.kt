// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollBar
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Tests that [componentDigest] changes with every value of a Swing tree it covers and with nothing else. */
class ComponentDigestTest {
    @Test
    fun `equivalent trees have the same digest`() =
        runSwingTest {
            fun tree() =
                JPanel().apply {
                    add(JLabel("Label"))
                    add(JButton("Button"))
                }
            assertEquals(componentDigest(tree()), componentDigest(tree()))
        }

    @Test
    fun `component order and class are part of the digest`() =
        runSwingTest {
            val panel =
                JPanel().apply {
                    add(JLabel("A"))
                    add(JLabel("B"))
                }
            val initial = componentDigest(panel)
            panel.setComponentZOrder(panel.getComponent(0), 1)
            val reordered = componentDigest(panel)
            assertNotEquals(initial, reordered)
            panel.remove(0)
            panel.add(object : JLabel("B") {}, 0)
            assertNotEquals(reordered, componentDigest(panel))
        }

    @Test
    fun `shared component properties change the digest`() =
        runSwingTest {
            val label = JLabel("Before")
            val changes: List<Pair<String, () -> Unit>> =
                listOf(
                    "enabled" to { label.isEnabled = false },
                    "visible" to { label.isVisible = false },
                    "foreground" to { label.foreground = Color.RED },
                    "background" to { label.background = Color.BLUE },
                    "fontFamily" to { label.font = Font(Font.MONOSPACED, label.font.style, label.font.size) },
                    "fontSize" to { label.font = label.font.deriveFont(18f) },
                    "fontStyle" to { label.font = label.font.deriveFont(Font.ITALIC) },
                    "text" to { label.text = "After" },
                    "name" to { label.name = "Named" },
                    "location" to { label.setLocation(2, 3) },
                    "size" to { label.setSize(40, 50) },
                )
            changes.forEach { (name, change) ->
                val before = componentDigest(label)
                change()
                assertNotEquals(before, componentDigest(label), name)
            }
        }

    @Test
    fun `widget values change the digest`() =
        runSwingTest {
            val button = JCheckBox("Before")
            val text = JTextField("Before")
            val slider = JSlider()
            val progress = JProgressBar()
            val scroll = JScrollBar()
            val spinner = JSpinner()
            val list = JList(arrayOf("A", "B"))
            val tree = JTree()
            val panel =
                JPanel().apply {
                    listOf(button, text, slider, progress, scroll, spinner, list, tree).forEach(::add)
                }
            val changes: List<Pair<String, () -> Unit>> =
                listOf(
                    "buttonText" to { button.text = "After" },
                    "buttonSelected" to { button.isSelected = true },
                    "fieldText" to { text.text = "After" },
                    "sliderValue" to { slider.value = 60 },
                    "progressValue" to { progress.value = 30 },
                    "scrollValue" to { scroll.value = 20 },
                    "spinnerValue" to { spinner.value = 10 },
                    "listSelection" to { list.selectedIndex = 1 },
                    "treeSelection" to { tree.setSelectionRow(1) },
                )
            changes.forEach { (name, change) ->
                val before = componentDigest(panel)
                change()
                assertNotEquals(before, componentDigest(panel), name)
            }
        }

    @Test
    fun `selection content changes the digest at the same indices`() =
        runSwingTest {
            val combo = JComboBox(arrayOf("A", "B"))
            val listModel = DefaultListModel<String>().apply { addElement("A") }
            val list = JList(listModel).apply { selectedIndex = 0 }
            val table =
                JTable(1, 1).apply {
                    setValueAt("A", 0, 0)
                    setRowSelectionInterval(0, 0)
                    setColumnSelectionInterval(0, 0)
                }
            val root = DefaultMutableTreeNode("A")
            val tree = JTree(DefaultTreeModel(root)).apply { setSelectionRow(0) }
            val panel =
                JPanel().apply {
                    add(combo)
                    add(list)
                    add(table)
                    add(tree)
                }
            val changes: List<Pair<String, () -> Unit>> =
                listOf(
                    "comboIndex" to { combo.selectedIndex = 1 },
                    "comboItem" to { combo.model.selectedItem = "C" },
                    "listElement" to { listModel.set(0, "B") },
                    "tableCell" to { table.setValueAt("B", 0, 0) },
                    "treeNode" to { root.setUserObject("B") },
                )
            changes.forEach { (name, change) ->
                val before = componentDigest(panel)
                change()
                assertNotEquals(before, componentDigest(panel), name)
            }
        }

    @Test
    fun `unselected list values change the digest`() =
        runSwingTest {
            val model =
                DefaultListModel<String>().apply {
                    addElement("A")
                    addElement("B")
                }
            val list = JList(model).apply { selectedIndex = 0 }
            val before = componentDigest(list)
            model.set(1, "C")
            assertNotEquals(before, componentDigest(list))
        }

    @Test
    fun `unselected table cells change the digest`() =
        runSwingTest {
            val table =
                JTable(2, 2).apply {
                    setRowSelectionInterval(0, 0)
                    setColumnSelectionInterval(0, 0)
                }
            val before = componentDigest(table)
            table.setValueAt("Changed", 1, 1)
            assertNotEquals(before, componentDigest(table))
        }

    @Test
    fun `unselected tree nodes change the digest`() =
        runSwingTest {
            val child = DefaultMutableTreeNode("Before")
            val root = DefaultMutableTreeNode("Root").apply { add(child) }
            val tree = JTree(DefaultTreeModel(root)).apply { setSelectionRow(0) }
            val before = componentDigest(tree)
            child.setUserObject("After")
            assertNotEquals(before, componentDigest(tree))
        }
}
