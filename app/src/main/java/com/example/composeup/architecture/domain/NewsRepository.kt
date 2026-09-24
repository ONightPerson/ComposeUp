package com.example.composeup.architecture.domain

import kotlinx.coroutines.flow.StateFlow

interface NewsRepository {
    val news: StateFlow<NewsSnapshot>

    suspend fun refresh()

    fun toggleBookmark(articleId: String)
}
