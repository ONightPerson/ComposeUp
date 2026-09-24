package com.example.composeup.architecture

import com.example.composeup.architecture.data.InMemoryNewsRepository
import com.example.composeup.architecture.data.NewsSource
import com.example.composeup.architecture.domain.NewsArticle
import com.example.composeup.architecture.domain.NewsSnapshot
import com.example.composeup.architecture.domain.ObserveNewsUseCase
import com.example.composeup.architecture.domain.RefreshNewsUseCase
import com.example.composeup.architecture.domain.RefreshStatus
import com.example.composeup.architecture.domain.ToggleBookmarkUseCase
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryNewsRepositoryTest {
    @Test(timeout = 5_000)
    fun refreshDeduplicatesInFirstSeenOrderAndUseCasesShareTheStore() = runBlocking {
        val source = Source()
        val repository = InMemoryNewsRepository(source)
        val observe = ObserveNewsUseCase(repository)
        val refresh = RefreshNewsUseCase(repository)
        assertEquals(NewsSnapshot(), observe().first())
        source.result.complete(listOf(article("b"), article("a"), article("b", "latest", true)))
        refresh()
        val snapshot = observe().first()
        assertEquals(listOf("b", "a"), snapshot.feedIds)
        assertEquals(mapOf("b" to article("b", "latest"), "a" to article("a")), snapshot.articlesById)
        assertEquals(RefreshStatus.Ready, snapshot.refreshStatus)
        ToggleBookmarkUseCase(repository)("b")
        source.result = CompletableDeferred(listOf(article("c")))
        refresh()
        assertEquals(listOf("c"), observe().first().feedIds)
        assertEquals(snapshot.articlesById + ("b" to article("b", "latest", true)) +
            ("c" to article("c")), observe().first().articlesById)
        assertEquals(2, source.calls)
    }

    @Test(timeout = 5_000)
    fun refreshPreservesCachedContentAndLatestBookmarkChanges() = runBlocking {
        val source = Source()
        val repository = InMemoryNewsRepository(source)
        source.result.complete(listOf(article("a"), article("b")))
        repository.refresh()
        repository.toggleBookmark("b")
        val cached = repository.news.value
        source.result = CompletableDeferred()
        val refresh = launch { repository.refresh() }
        yield()
        assertEquals(cached.copy(refreshStatus = RefreshStatus.Loading), repository.news.value)
        repository.toggleBookmark("a")
        repository.toggleBookmark("b")
        source.result.complete(listOf(article("a", "updated"), article("b", "updated", true)))
        refresh.join()
        assertEquals(mapOf("a" to article("a", "updated", true), "b" to article("b", "updated")),
            repository.news.value.articlesById)
        assertEquals(RefreshStatus.Ready, repository.news.value.refreshStatus)
    }

    @Test(timeout = 5_000)
    fun firstAndCachedFailuresKeepDataAndAllowRetry() = runBlocking {
        for (withCache in listOf(false, true)) {
            val source = Source()
            val repository = InMemoryNewsRepository(source)
            if (withCache) {
                source.result.complete(listOf(article("a")))
                repository.refresh()
                repository.toggleBookmark("a")
            }
            val before = repository.news.value
            source.result = CompletableDeferred<List<NewsArticle>>().apply {
                completeExceptionally(IOException("offline"))
            }
            repository.refresh()
            assertEquals(before.copy(refreshStatus = RefreshStatus.Failed), repository.news.value)
            source.result = CompletableDeferred(listOf(article("a", "retried")))
            repository.refresh()
            assertEquals(NewsSnapshot(mapOf("a" to article("a", "retried", withCache)),
                listOf("a"), RefreshStatus.Ready), repository.news.value)
            assertEquals(if (withCache) 3 else 2, source.calls)
        }
    }

    @Test(timeout = 5_000)
    fun cancellationRestoresPreviousStatusPreservesChangesAndReleasesLock() = runBlocking {
        for (status in listOf(RefreshStatus.Idle, RefreshStatus.Ready, RefreshStatus.Failed)) {
            val source = Source()
            val repository = InMemoryNewsRepository(source)
            if (status != RefreshStatus.Idle) {
                if (status == RefreshStatus.Ready) source.result.complete(listOf(article("a")))
                else source.result.completeExceptionally(IOException("offline"))
                repository.refresh()
            }
            source.result = CompletableDeferred()
            var cancellation: CancellationException? = null
            val refresh = launch {
                try { repository.refresh() } catch (error: CancellationException) {
                    cancellation = error
                    throw error
                }
            }
            yield()
            assertEquals(RefreshStatus.Loading, repository.news.value.refreshStatus)
            repository.toggleBookmark("a")
            val during = repository.news.value
            refresh.cancel()
            refresh.join()
            assertNotNull(cancellation)
            assertEquals(during.copy(refreshStatus = status), repository.news.value)
            source.result = CompletableDeferred(listOf(article("a")))
            repository.refresh()
            assertEquals(RefreshStatus.Ready, repository.news.value.refreshStatus)
            assertEquals(status == RefreshStatus.Ready, repository.news.value.articlesById["a"]?.isBookmarked)
        }
    }

    @Test(timeout = 5_000)
    fun overlappingRefreshReturnsImmediatelyWithoutAnotherRequest() = runBlocking {
        val source = Source()
        val repository = InMemoryNewsRepository(source)
        val first = launch { repository.refresh() }
        yield()
        val duplicate = launch { repository.refresh() }
        yield()
        assertTrue(duplicate.isCompleted)
        assertEquals(1, source.calls)
        assertEquals(RefreshStatus.Loading, repository.news.value.refreshStatus)
        source.result.complete(listOf(article("a")))
        first.join()
        duplicate.join()
        assertEquals(RefreshStatus.Ready, repository.news.value.refreshStatus)
        assertEquals(listOf("a"), repository.news.value.feedIds)
    }

    @Test(timeout = 5_000)
    fun unknownIdsAreNoOpsAndBookmarkToggleIsReversible() = runBlocking {
        val source = Source()
        val repository = InMemoryNewsRepository(source)
        val empty = repository.news.value
        repository.toggleBookmark("missing")
        assertSame(empty, repository.news.value)
        source.result.complete(listOf(article("a")))
        repository.refresh()
        val cached = repository.news.value
        repository.toggleBookmark("")
        repository.toggleBookmark("missing")
        assertSame(cached, repository.news.value)
        repository.toggleBookmark("a")
        assertTrue(repository.news.value.articlesById.getValue("a").isBookmarked)
        repository.toggleBookmark("a")
        assertEquals(cached, repository.news.value)
    }

    @Test(timeout = 5_000)
    fun emptyRefreshIsReadyAndRetainsPreviouslyStoredEntities() = runBlocking {
        for (withCache in listOf(false, true)) {
            val source = Source()
            val repository = InMemoryNewsRepository(source)
            if (withCache) {
                source.result.complete(listOf(article("a")))
                repository.refresh()
                repository.toggleBookmark("a")
            }
            val before = repository.news.value
            source.result = CompletableDeferred(emptyList())
            repository.refresh()
            assertEquals(before.copy(feedIds = emptyList(), refreshStatus = RefreshStatus.Ready),
                repository.news.value)
        }
    }

    private class Source : NewsSource {
        var result = CompletableDeferred<List<NewsArticle>>()
        var calls = 0
        override suspend fun fetchNews(): List<NewsArticle> {
            calls++
            return result.await()
        }
    }

    private fun article(id: String, title: String = id, bookmarked: Boolean = false) =
        NewsArticle(id, title, "summary", listOf("body"), "category", "author", "today", bookmarked)
}
