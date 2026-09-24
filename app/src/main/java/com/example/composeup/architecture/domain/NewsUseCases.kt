package com.example.composeup.architecture.domain

import kotlinx.coroutines.flow.Flow

class ObserveNewsUseCase(private val repository: NewsRepository) {
    operator fun invoke(): Flow<NewsSnapshot> = repository.news
}

class RefreshNewsUseCase(private val repository: NewsRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class ToggleBookmarkUseCase(private val repository: NewsRepository) {
    operator fun invoke(articleId: String) = repository.toggleBookmark(articleId)
}
