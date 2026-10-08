package org.jetbrains.compose.swing.animation

import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** The [ContainerHeightCheck] compares a container with the height its leaf prefers, wherever the slot put the leaf. */
class ContainerHeightCheckTest {
    private fun panelPreferring(
        width: Int,
        height: Int,
        laidOutAs: Dimension? = null,
    ) = JPanel().apply {
        preferredSize = Dimension(width, height)
        laidOutAs?.let { size = it }
    }

    @Test
    fun `a container preferring less than its leaf prefers is a problem though the slot stretched the leaf`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(260, 100))
        val container = panelPreferring(260, 50)

        val problems = ContainerHeightCheck.problems(container, leaf)

        assertEquals(listOf("the container prefers 50 and the content prefers 100 tall"), problems)
    }

    @Test
    fun `a container preferring the height its leaf prefers is no problem though the slot stretched the leaf`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(260, 60))
        val container = panelPreferring(260, 100)

        assertEquals(emptyList(), ContainerHeightCheck.problems(container, leaf))
    }

    @Test
    fun `a container preferring other than the height its ratio takes from the leaf's width is a problem`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(260, 130))
        val container = panelPreferring(260, 100)

        val problems = ContainerHeightCheck.problemsAtRatio(container, leaf)

        assertEquals(
            listOf("the container prefers 100 and the content, 260 wide at an aspect ratio, takes 130 tall"),
            problems,
        )
    }

    @Test
    fun `a container preferring the height its ratio takes from the leaf's width is no problem`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(261, 131))
        val container = panelPreferring(261, 131)

        assertEquals(emptyList(), ContainerHeightCheck.problemsAtRatio(container, leaf))
    }

    @Test
    fun `a container preferring other than the height its leaf at its own ratio is laid out at is a problem`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(260, 130))
        val container = panelPreferring(260, 100, laidOutAs = Dimension(260, 200))

        val problems = ContainerHeightCheck.problemsAtOwnRatio(container, leaf)

        assertEquals(listOf("the container prefers 100 and the content is 130 tall"), problems)
    }

    @Test
    fun `a leaf off its own ratio is a problem though the container prefers its height`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(260, 100))
        val container = panelPreferring(260, 100, laidOutAs = Dimension(260, 100))

        val problems = ContainerHeightCheck.problemsAtOwnRatio(container, leaf)

        assertEquals(listOf("the content is 260 wide and 100 tall, off the aspect ratio"), problems)
    }

    @Test
    fun `a container preferring the height its leaf at its own ratio is laid out at is no problem`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(260, 130))
        val container = panelPreferring(260, 130, laidOutAs = Dimension(260, 200))

        assertEquals(emptyList(), ContainerHeightCheck.problemsAtOwnRatio(container, leaf))
    }

    @Test
    fun `a container granted less than it prefers is no problem when its leaf at its own ratio takes the grant`() {
        val leaf = panelPreferring(300, 100, laidOutAs = Dimension(1200, 600))
        val container = panelPreferring(1214, 607, laidOutAs = Dimension(1214, 600))

        assertEquals(emptyList(), ContainerHeightCheck.problemsAtOwnRatio(container, leaf))
    }
}
