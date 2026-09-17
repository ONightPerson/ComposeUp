package com.example.composeup.navigation3.impl

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.composeup.navigation3.api.*

/**
 * 组装并暴露所有的路由映射 EntryProvider 关系树。
 * 利用 entryProvider DSL 注册每个 NavKey 对应的 UI Content。
 */
fun appNav3EntryProvider(
    backStack: MutableList<androidx.navigation3.runtime.NavKey>,
    resultStore: ResultStore
) = entryProvider {

    // 1. 注册主页，附加分栏标志 Metadata
    entry<HomeKey>(metadata = CustomListDetailStrategy.listPane()) { _: HomeKey ->
        HomeScreen(backStack = backStack, resultStore = resultStore)
    }

    // 2. 注册详情页，附加分栏标志 Metadata
    entry<DetailKey>(metadata = CustomListDetailStrategy.detailPane()) { key: DetailKey ->
        DetailScreen(key = key, backStack = backStack)
    }

    // 3. 注册设置页，为其配置独立的垂直滑入/弹出定制动画元数据
    entry<SettingsKey>(
        metadata = androidx.navigation3.runtime.metadata {
            // 前进进入时：垂直向上推入
            put(NavDisplay.TransitionKey) {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(500)
                ) togetherWith ExitTransition.KeepUntilTransitionsFinished
            }

            // 返回退出时：垂直向下退出
            put(NavDisplay.PopTransitionKey) {
                EnterTransition.None togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(500)
                )
            }

            // 预测性侧滑返回时：保持一致
            put(NavDisplay.PredictivePopTransitionKey) {
                EnterTransition.None togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(500)
                )
            }
        }
    ) { _: SettingsKey ->
        SettingsScreen(backStack = backStack, resultStore = resultStore)
    }

    // 4. 注册对话框弹窗
    entry<DialogKey> { _: DialogKey ->
        DialogContent(backStack = backStack)
    }
}
