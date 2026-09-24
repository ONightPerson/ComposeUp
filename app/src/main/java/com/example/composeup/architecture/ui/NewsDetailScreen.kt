package com.example.composeup.architecture.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NewsDetailScreen(
    state: NewsUiState,
    onEvent: (NewsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val article = state.selectedArticle
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (state.isLoading) {
            if (article == null) NewsLoading() else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.hasError) {
            NewsError(onRetry = { onEvent(NewsEvent.Refresh) })
        }
        if (article == null) {
            if (!state.isLoading && !state.hasError) {
                Text("这篇新闻已不存在", style = MaterialTheme.typography.titleLarge)
                Text("详情只保存新闻 ID；恢复页面后，需要从仓库重新查找文章。")
                Button(onClick = { onEvent(NewsEvent.Back) }) { Text("返回新闻主页") }
            }
            return@Column
        }
        Text(article.category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(article.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "${article.author}\n${article.publishedAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { onEvent(NewsEvent.ToggleBookmark(article.id)) }) {
            Text(if (article.isBookmarked) "已收藏 · 点击取消" else "收藏这篇新闻")
        }
        Text(article.summary, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        article.paragraphs.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodyLarge)
        }
        ArchitectureNote(
            "归一化状态\nselectedArticleId = ${article.id}\n" +
                "详情 = articlesById[selectedArticleId]\n" +
                "收藏修改的是同一条实体，不向首页回传整篇文章。",
        )
    }
}
