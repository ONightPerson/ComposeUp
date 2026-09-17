package com.example.composeup.navigation3.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * 自定义双栏并排并列呈现的 Scene 场景。
 */
class CustomListDetailScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val listEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(listEntry, detailEntry)

    override val content: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧分栏占 40% 比例
            Box(modifier = Modifier.weight(0.4f)) {
                listEntry.Content()
            }
            // 右侧分栏占 60% 比例
            Box(modifier = Modifier.weight(0.6f)) {
                detailEntry.Content()
            }
        }
    }
}

/**
 * 自定义双栏分栏自适应渲染策略。
 * 当开启大屏模式 (isWideScreen = true)，且回退栈尾部是详情页，而前面包含主页时，
 * 触发该策略，实现并排同屏渲染。
 */
class CustomListDetailStrategy<T : Any>(private val isWideScreen: Boolean) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        // 如果不是宽屏，返回 null，交由系统的 SinglePaneSceneStrategy 降级处理为单页
        if (!isWideScreen) return null

        val detailEntry = entries.lastOrNull()?.takeIf { (it.metadata as Map<*, *>).containsKey(DetailPaneKey) } ?: return null
        val listEntry = entries.findLast { (it.metadata as Map<*, *>).containsKey(ListPaneKey) } ?: return null

        return CustomListDetailScene(
            key = listEntry.contentKey, // 以 list 的 contentKey 作为 sceneKey，防止详情切换时整屏闪烁动画
            previousEntries = entries.dropLast(1),
            listEntry = listEntry,
            detailEntry = detailEntry
        )
    }

    object ListPaneKey : NavMetadataKey<Boolean>
    object DetailPaneKey : NavMetadataKey<Boolean>

    companion object {
        fun listPane() = metadata {
            put(ListPaneKey, true)
        }

        fun detailPane() = metadata {
            put(DetailPaneKey, true)
        }
    }
}
