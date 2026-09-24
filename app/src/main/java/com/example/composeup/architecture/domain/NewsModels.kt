package com.example.composeup.architecture.domain

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val paragraphs: List<String>,
    val category: String,
    val author: String,
    val publishedAt: String,
    val isBookmarked: Boolean = false,
)

enum class RefreshStatus { Idle, Loading, Ready, Failed }

data class NewsSnapshot(
    val articlesById: Map<String, NewsArticle> = emptyMap(),
    val feedIds: List<String> = emptyList(),
    val refreshStatus: RefreshStatus = RefreshStatus.Idle,
)
