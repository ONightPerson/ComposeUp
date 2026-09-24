package com.example.composeup.architecture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.composeup.architecture.domain.ObserveNewsUseCase
import com.example.composeup.architecture.domain.RefreshNewsUseCase
import com.example.composeup.architecture.domain.ToggleBookmarkUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NewsViewModel(
    observeNews: ObserveNewsUseCase,
    private val refreshNews: RefreshNewsUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val bookmarkedOnly = savedStateHandle.getStateFlow(BOOKMARKED_ONLY, false)
    private val selectedArticleId = savedStateHandle.getStateFlow<String?>(SELECTED_ARTICLE_ID, null)

    val uiState = combine(observeNews(), bookmarkedOnly, selectedArticleId) { news, onlySaved, id ->
        NewsUiState(news = news, bookmarkedOnly = onlySaved, selectedArticleId = id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NewsUiState(
            bookmarkedOnly = bookmarkedOnly.value,
            selectedArticleId = selectedArticleId.value,
        ),
    )

    init {
        refresh()
    }

    fun onEvent(event: NewsEvent) {
        when (event) {
            NewsEvent.Refresh -> refresh()
            is NewsEvent.SetBookmarkedOnly -> savedStateHandle[BOOKMARKED_ONLY] = event.enabled
            is NewsEvent.OpenArticle -> savedStateHandle[SELECTED_ARTICLE_ID] = event.articleId
            is NewsEvent.ToggleBookmark -> toggleBookmark(event.articleId)
            NewsEvent.Back -> savedStateHandle[SELECTED_ARTICLE_ID] = null
        }
    }

    private fun refresh() {
        viewModelScope.launch { refreshNews() }
    }

    private companion object {
        const val BOOKMARKED_ONLY = "news.bookmarkedOnly"
        const val SELECTED_ARTICLE_ID = "news.selectedArticleId"
    }
}
