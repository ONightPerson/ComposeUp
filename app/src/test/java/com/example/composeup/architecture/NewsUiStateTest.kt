package com.example.composeup.architecture

import com.example.composeup.architecture.domain.NewsArticle
import com.example.composeup.architecture.domain.NewsSnapshot
import com.example.composeup.architecture.domain.RefreshStatus
import com.example.composeup.architecture.ui.NewsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsUiStateTest {
    private val a = article("a", true)
    private val b = article("b", false)
    private val retained = article("retained", true)
    private val snapshot = NewsSnapshot(mapOf("a" to a, "b" to b, "retained" to retained),
        listOf("b", "missing", "a"), RefreshStatus.Ready)

    @Test
    fun listAndDetailsUseTheSameEntitiesAndCountOnlyFeedBookmarks() {
        val state = NewsUiState(snapshot, selectedArticleId = "a")
        assertEquals(listOf(b, a), state.visibleArticles)
        assertSame(state.visibleArticles.last(), state.selectedArticle)
        assertEquals(1, state.bookmarkCount)
        val offFeed = state.copy(selectedArticleId = "retained")
        assertSame(retained, offFeed.selectedArticle)
    }

    @Test
    fun filteringAndUnbookmarkingUpdateListCountAndDetailsTogether() {
        val filtered = NewsUiState(snapshot, bookmarkedOnly = true, selectedArticleId = "a")
        assertEquals(listOf(a), filtered.visibleArticles)
        assertSame(a, filtered.selectedArticle)
        assertEquals(1, filtered.bookmarkCount)
        val unbookmarked = a.copy(isBookmarked = false)
        val updated = filtered.copy(news = snapshot.copy(articlesById = snapshot.articlesById +
            ("a" to unbookmarked)))
        assertTrue(updated.visibleArticles.isEmpty())
        assertEquals(0, updated.bookmarkCount)
        assertSame(unbookmarked, updated.selectedArticle)
        assertEquals(listOf(b, unbookmarked), updated.copy(bookmarkedOnly = false).visibleArticles)
    }

    @Test
    fun emptyFeedAndMissingSelectionHaveNoDerivedArticles() {
        val empty = NewsUiState()
        assertTrue(empty.visibleArticles.isEmpty())
        assertEquals(0, empty.bookmarkCount)
        assertNull(empty.selectedArticle)
        assertNull(NewsUiState(snapshot, selectedArticleId = "missing").selectedArticle)
        val retainedOnly = NewsUiState(snapshot.copy(feedIds = emptyList()), true, "retained")
        assertTrue(retainedOnly.visibleArticles.isEmpty())
        assertEquals(0, retainedOnly.bookmarkCount)
        assertSame(retained, retainedOnly.selectedArticle)
    }

    @Test
    fun loadingAndErrorFlagsAreDerivedWithoutHidingCachedContent() {
        for (status in RefreshStatus.entries) {
            val state = NewsUiState(snapshot.copy(refreshStatus = status))
            assertEquals(status == RefreshStatus.Idle || status == RefreshStatus.Loading, state.isLoading)
            assertEquals(status == RefreshStatus.Failed, state.hasError)
            assertEquals(listOf(b, a), state.visibleArticles)
        }
    }

    private fun article(id: String, bookmarked: Boolean) =
        NewsArticle(id, id, "summary", emptyList(), "category", "author", "today", bookmarked)
}
