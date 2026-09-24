package com.example.composeup.architecture.data

import com.example.composeup.architecture.domain.NewsRepository
import com.example.composeup.architecture.domain.NewsSnapshot
import com.example.composeup.architecture.domain.RefreshStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

class InMemoryNewsRepository(private val source: NewsSource) : NewsRepository {
    private val store = MutableStateFlow(NewsSnapshot())
    override val news = store.asStateFlow()
    private val refreshMutex = Mutex()

    override suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        val previousStatus = store.value.refreshStatus
        try {
            store.update { it.copy(refreshStatus = RefreshStatus.Loading) }
            val incoming = source.fetchNews().associateBy { it.id }
            store.update { current ->
                val merged = incoming.mapValues { (id, article) ->
                    article.copy(isBookmarked = current.articlesById[id]?.isBookmarked ?: false)
                }
                current.copy(
                    articlesById = current.articlesById + merged,
                    feedIds = incoming.keys.toList(),
                    refreshStatus = RefreshStatus.Ready,
                )
            }
        } catch (cancelled: CancellationException) {
            store.update { it.copy(refreshStatus = previousStatus) }
            throw cancelled
        } catch (_: IOException) {
            store.update { it.copy(refreshStatus = RefreshStatus.Failed) }
        } finally {
            refreshMutex.unlock()
        }
    }

    override fun toggleBookmark(articleId: String) {
        store.update { current ->
            val article = current.articlesById[articleId] ?: return@update current
            current.copy(
                articlesById = current.articlesById +
                    (articleId to article.copy(isBookmarked = !article.isBookmarked)),
            )
        }
    }
}
