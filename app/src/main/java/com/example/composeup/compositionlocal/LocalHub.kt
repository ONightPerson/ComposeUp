package com.example.composeup.compositionlocal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class LocalTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    Localization(
        title = "① 动态多语言切换",
        summary = "不依赖 Context 配置，通过 CompositionLocal 实现应用内即时翻译切换。",
        keywords = "staticCompositionLocalOf · AppStrings",
    ),
    Permission(
        title = "② 全局权限控制器",
        summary = "在根部统一管理 Launcher，通过隐式传递让深层 UI 也能轻松发起权限请求。",
        keywords = "compositionLocalOf · PermissionHandler",
    ),
    DesignSystem(
        title = "③ 自定义设计系统扩展",
        summary = "扩展 MaterialTheme，提供自定义的间距 (Spacing) 等设计维度。",
        keywords = "AppTheme.spacing · ReadOnlyComposable",
    ),
    Analytics(
        title = "④ 埋点统计装饰器",
        summary = "利用装饰器模式，让嵌套 UI 自动合并 Section 信息到埋点上下文中。",
        keywords = "Decorator · TrackerContext",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<LocalTopic?>(null) }
    val topics = remember { LocalTopic.entries.toList() }

    val current = selected
    if (current == null) {
        Surface(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "CompositionLocal 专家级应用",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "CompositionLocal 用于解决「属性钻取」问题，适合在 UI 树中隐式传递跨切面逻辑（如主题、翻译、控制器）。\n" +
                                "· compositionLocalOf: 适合频繁变化的值，局部重组；\n" +
                                "· staticCompositionLocalOf: 适合极少变化的值，性能更好但变化时全树重组。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    LocalRow(
                        title = topic.title,
                        subtitle = topic.summary + "\n\n" + topic.keywords,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { selected = topic },
                    )
                }
            }
        }
        return
    }

    BackHandler { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { selected = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val itemModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (current) {
            LocalTopic.Localization -> LocalizationDemo(itemModifier)
            LocalTopic.Permission -> PermissionDemo(itemModifier)
            LocalTopic.DesignSystem -> DesignSystemDemo(itemModifier)
            LocalTopic.Analytics -> AnalyticsDemo(itemModifier)
        }
    }
}
