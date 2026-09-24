package com.example.composeup.architecture.ui

import com.example.composeup.architecture.domain.NewsArticle
import com.example.composeup.architecture.domain.NewsSnapshot
import com.example.composeup.architecture.domain.RefreshStatus

data class NewsUiState(
    val news: NewsSnapshot = NewsSnapshot(),
    val bookmarkedOnly: Boolean = false,
    val selectedArticleId: String? = null,
) {
    val visibleArticles: List<NewsArticle>
        get() = news.feedIds.mapNotNull(news.articlesById::get)
            .filter { !bookmarkedOnly || it.isBookmarked }

    val selectedArticle: NewsArticle?
        get() = selectedArticleId?.let(news.articlesById::get)

    val bookmarkCount: Int
        get() = news.feedIds.count { news.articlesById[it]?.isBookmarked == true }

    val isLoading: Boolean
        get() = news.refreshStatus == RefreshStatus.Idle ||
            news.refreshStatus == RefreshStatus.Loading

    val hasError: Boolean
        get() = news.refreshStatus == RefreshStatus.Failed
}

sealed interface NewsEvent {
    data object Refresh : NewsEvent
    data class SetBookmarkedOnly(val enabled: Boolean) : NewsEvent
    data class OpenArticle(val articleId: String) : NewsEvent
    data class ToggleBookmark(val articleId: String) : NewsEvent
    data object Back : NewsEvent
}
