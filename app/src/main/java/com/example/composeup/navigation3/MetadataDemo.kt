package com.example.composeup.navigation3

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

/**
 * ④ 配置 metadata：给 NavEntry 挂类型安全的元信息。
 *
 * metadata 本质是 `Map<String, Any>`，库用它在 **NavEntry / Scene / NavDisplay** 之间传递信息。
 * 直接写字符串 key 容易拼错、类型不安全，所以库提供了 **metadata DSL + [NavMetadataKey]**：
 * 用 `metadata { put(SomeKey, value) }` 写，用 `map[SomeKey]` / `map.contains(SomeKey)` 读，
 * 值的类型由 NavMetadataKey 的泛型锁死。
 *
 * 最经典的用途就是**为单个目的地覆盖默认转场动画**：NavDisplay 定义了
 * `NavDisplay.TransitionKey` / `PopTransitionKey` / `PredictivePopTransitionKey` 三个 metadata key，
 * 往某个 entry 的 metadata 里 put 进去，就能让「同一个栈、不同页面」用不同的进出场动画。
 * 本示例三个页面分别是：淡入淡出、竖向滑入、默认（无 metadata）。
 */
@Composable
internal fun MetadataDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(MetaHomeKey)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenArea(height = 260.dp) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
                entryProvider = entryProvider {
                    entry<MetaHomeKey> {
                        Pane("metadata 驱动的转场", color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("点不同页面，观察进入/返回动画的差异。", style = MaterialTheme.typography.bodySmall)
                            ButtonRow {
                                DemoButton("淡入淡出页") { backStack.add(FadePageKey) }
                                DemoButton("竖向滑入页") { backStack.add(SlidePageKey) }
                                DemoButton("默认动画页") { backStack.add(DefaultPageKey) }
                            }
                        }
                    }

                    // 用 metadata DSL 覆盖转场：淡入淡出
                    entry<FadePageKey>(
                        metadata = metadata {
                            put(NavDisplay.TransitionKey) {
                                fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                            }
                            put(NavDisplay.PopTransitionKey) {
                                fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                            }
                        },
                    ) {
                        DemoPage("FadePage（fadeIn/fadeOut）", MaterialTheme.colorScheme.tertiaryContainer) {
                            backStack.removeLastOrNull()
                        }
                    }

                    // 竖向滑入 / 滑出，并演示 PredictivePop 也一并配置
                    entry<SlidePageKey>(
                        metadata = metadata {
                            put(NavDisplay.TransitionKey) {
                                slideInVertically(tween(450)) { it } togetherWith
                                    ExitTransition.KeepUntilTransitionsFinished
                            }
                            put(NavDisplay.PopTransitionKey) {
                                EnterTransition.None togetherWith slideOutVertically(tween(450)) { it }
                            }
                            put(NavDisplay.PredictivePopTransitionKey) {
                                EnterTransition.None togetherWith slideOutVertically(tween(450)) { it }
                            }
                        },
                    ) {
                        DemoPage("SlidePage（slideInVertically）", MaterialTheme.colorScheme.secondaryContainer) {
                            backStack.removeLastOrNull()
                        }
                    }

                    // 不配置 metadata：使用 NavDisplay 的默认转场
                    entry<DefaultPageKey> {
                        DemoPage("DefaultPage（无 metadata，默认动画）", MaterialTheme.colorScheme.surfaceVariant) {
                            backStack.removeLastOrNull()
                        }
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("metadata DSL 怎么写")
            CodeBlock(
                """
                // 自定义 key：约定作为「读取方」类的嵌套 object，值类型由泛型锁死
                class MySceneStrategy<T : Any> : SceneStrategy<T> {
                    object BadgeKey : NavMetadataKey<String>
                }

                // 写：metadata { put(Key, value) }，可用条件逻辑
                entry<FadePageKey>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            fadeIn() togetherWith fadeOut()
                        }
                        if (condition) put(MySceneStrategy.BadgeKey, "NEW")
                    },
                ) { /* ... */ }

                // 读：contains / get（[] 需 import androidx.navigation3.runtime.get）
                val hasBadge = metadata.contains(MySceneStrategy.BadgeKey)
                val badge: String? = metadata[MySceneStrategy.BadgeKey]
                """.trimIndent(),
            )
            Note(
                "要点：NavDisplay 内置的 TransitionKey / PopTransitionKey / PredictivePopTransitionKey " +
                    "都是 NavMetadataKey；Scene.metadata 默认返回其最后一个 NavEntry 的 metadata。" +
                    "本示例的自定义 BadgeKey 用法见「⑦ Scenes」——SceneStrategy 正是靠读 metadata 决定布局。",
            )
        }
    }
}

/** 统一的示例页外观：标题 + 一个返回按钮。 */
@Composable
private fun DemoPage(title: String, color: androidx.compose.ui.graphics.Color, onBack: () -> Unit) {
    Pane(title, color = color) {
        ButtonRow { DemoOutlinedButton("返回") { onBack() } }
    }
}

// ---- 本示例私有的 key ----
@Serializable private data object MetaHomeKey : NavKey
@Serializable private data object FadePageKey : NavKey
@Serializable private data object SlidePageKey : NavKey
@Serializable private data object DefaultPageKey : NavKey
