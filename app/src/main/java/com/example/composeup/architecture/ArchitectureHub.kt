package com.example.composeup.architecture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.composeup.architecture.data.DemoNewsSource
import com.example.composeup.architecture.data.InMemoryNewsRepository
import com.example.composeup.architecture.domain.ObserveNewsUseCase
import com.example.composeup.architecture.domain.RefreshNewsUseCase
import com.example.composeup.architecture.domain.ToggleBookmarkUseCase
import com.example.composeup.architecture.ui.NewsDetailScreen
import com.example.composeup.architecture.ui.NewsEvent
import com.example.composeup.architecture.ui.NewsHomeScreen
import com.example.composeup.architecture.ui.NewsViewModel

private val newsViewModelFactory = viewModelFactory {
    initializer {
        val repository = InMemoryNewsRepository(DemoNewsSource())
        NewsViewModel(
            observeNews = ObserveNewsUseCase(repository),
            refreshNews = RefreshNewsUseCase(repository),
            toggleBookmark = ToggleBookmarkUseCase(repository),
            savedStateHandle = createSavedStateHandle(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchitectureHub(
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    val viewModel: NewsViewModel = viewModel(key = "architecture-news", factory = newsViewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val homeStateHolder = rememberSaveableStateHolder()
    val articleId = state.selectedArticleId
    val onBack: () -> Unit = {
        if (articleId == null) onExit() else viewModel.onEvent(NewsEvent.Back)
    }
    BackHandler(enabled = articleId != null, onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (articleId == null) "新闻 · 架构实战" else "新闻详情") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (articleId == null) "返回示例列表" else "返回新闻主页",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(NewsEvent.Refresh) },
                        enabled = !state.isLoading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新新闻")
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        if (articleId == null) {
            homeStateHolder.SaveableStateProvider("news-home") {
                NewsHomeScreen(state, viewModel::onEvent, contentModifier)
            }
        } else {
            key(articleId) {
                NewsDetailScreen(state, viewModel::onEvent, contentModifier)
            }
        }
    }
}
