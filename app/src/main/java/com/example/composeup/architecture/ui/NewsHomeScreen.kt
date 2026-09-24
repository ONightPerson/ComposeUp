package com.example.composeup.architecture.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.composeup.architecture.domain.NewsArticle

@Composable
fun NewsHomeScreen(
    state: NewsUiState,
    onEvent: (NewsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val articles = state.visibleArticles
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "introduction") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今日阅读", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "本地演示新闻 · 无真实网络请求 · 收藏仅保存在内存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ArchitectureNote("事件 → ViewModel → UseCase → Repository → StateFlow → UI\n点击文章进入详情，在任一页面收藏，另一页面自动同步。")
            }
        }
        item(key = "filter") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = !state.bookmarkedOnly,
                    onClick = { onEvent(NewsEvent.SetBookmarkedOnly(false)) },
                    label = { Text("全部新闻") },
                )
                FilterChip(
                    selected = state.bookmarkedOnly,
                    onClick = { onEvent(NewsEvent.SetBookmarkedOnly(true)) },
                    label = { Text("仅看收藏 (${state.bookmarkCount})") },
                )
            }
        }
        if (state.isLoading) {
            item(key = "loading") {
                if (state.news.feedIds.isEmpty()) {
                    NewsLoading()
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (state.hasError) {
            item(key = "error") { NewsError(onRetry = { onEvent(NewsEvent.Refresh) }) }
        }
        if (articles.isEmpty() && !state.isLoading && !state.hasError) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (state.bookmarkedOnly) "还没有收藏的新闻" else "暂无新闻")
                    TextButton(onClick = {
                        onEvent(if (state.bookmarkedOnly) NewsEvent.SetBookmarkedOnly(false) else NewsEvent.Refresh)
                    }) {
                        Text(if (state.bookmarkedOnly) "浏览全部新闻" else "重新加载")
                    }
                }
            }
        }
        items(articles, key = { "article-${it.id}" }) { article ->
            NewsCard(
                article = article,
                onOpen = { onEvent(NewsEvent.OpenArticle(article.id)) },
                onBookmark = { onEvent(NewsEvent.ToggleBookmark(article.id)) },
            )
        }
    }
}

@Composable
private fun NewsCard(article: NewsArticle, onOpen: () -> Unit, onBookmark: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    article.category,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                BookmarkButton(article, onBookmark)
            }
            Text(article.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(article.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${article.author} · ${article.publishedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
