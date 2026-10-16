package com.example.composeup.navigation3

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
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

/**
 * ⑤ 模块化导航：api/impl 拆分与组合。
 *
 * 当 App 变大，把所有 key 和 entryProvider 堆在一个文件里会失控。官方推荐按功能拆分：
 *  - 每个功能建 **api** 与 **impl** 两个子模块；
 *  - **api** 只放导航 key（其它模块依赖它就能导航过来，但不依赖具体实现）；
 *  - **impl** 放 entryProvider builder 与可导航内容；
 *  - 主 App 只负责把各功能的 builder **组合**进一个 `entryProvider`（直接调用，或用 DI 多绑定注入一个 Set）。
 *
 * 本示例用两个「假装独立模块」的 builder（[featureAEntries] / [featureBEntries]）演示这种去中心化注册：
 * 主 NavDisplay 不认识任何具体 key，只是把各模块贡献的 entry 拼起来。
 */
@Composable
internal fun ModularizationDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(ModHomeKey)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenArea(height = 280.dp) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
                // 关键：一个 entryProvider，把各功能模块贡献的 builder 依次拼装。
                entryProvider = entryProvider {
                    modHomeEntries(backStack)
                    featureAEntries(backStack)
                    featureBEntries(backStack)
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("模块怎么拆")
            Note(
                "· api 模块：只放 key（如 FeatureAHomeKey / FeatureADetailKey）——别的功能靠它导航过来；\n" +
                    "· impl 模块：放 EntryProviderScope 的扩展 builder（featureAEntries）与页面内容；\n" +
                    "· 主 App：只写一个 entryProvider，把各 builder 拼起来，不感知任何具体 key。",
            )
            SectionTitle("直接组合 vs DI 多绑定")
            CodeBlock(
                """
                // impl 模块暴露一个 EntryProviderScope 的扩展 builder
                fun EntryProviderScope<NavKey>.featureAEntries(backStack: SnapshotStateList<NavKey>) {
                    entry<FeatureAHomeKey> { /* ... */ }
                    entry<FeatureADetailKey> { key -> /* ... */ }
                }

                // 方式一：主 App 直接调用（小项目够用）
                NavDisplay(
                    entryProvider = entryProvider {
                        featureAEntries(backStack)
                        featureBEntries(backStack)
                    },
                )

                // 方式二：DI 多绑定（大项目），各模块把 builder 贡献进一个 Set
                @Inject lateinit var builders: Set<EntryProviderScope<NavKey>.() -> Unit>
                NavDisplay(entryProvider = entryProvider { builders.forEach { it() } })
                """.trimIndent(),
            )
        }
    }
}

// ==========================================================================
// 主 App 自己的入口页
// ==========================================================================
@Serializable private data object ModHomeKey : NavKey

private fun EntryProviderScope<NavKey>.modHomeEntries(backStack: NavBackStack<NavKey>) {
    entry<ModHomeKey> {
        Pane("主 App · 功能模块入口", color = MaterialTheme.colorScheme.primaryContainer) {
            Text("下面两个按钮分别进入「功能 A / 功能 B」两个独立模块。", style = MaterialTheme.typography.bodySmall)
            ButtonRow {
                DemoButton("进入 Feature A") { backStack.add(FeatureAHomeKey) }
                DemoButton("进入 Feature B") { backStack.add(FeatureBHomeKey) }
            }
        }
    }
}

// ==========================================================================
// 模拟「Feature A」模块：api（key） + impl（builder + 内容）
// ==========================================================================
// ---- api ----
@Serializable private data object FeatureAHomeKey : NavKey
@Serializable private data class FeatureADetailKey(val id: String) : NavKey

// ---- impl ----
private fun EntryProviderScope<NavKey>.featureAEntries(backStack: NavBackStack<NavKey>) {
    entry<FeatureAHomeKey> {
        Pane("Feature A · 首页", color = MaterialTheme.colorScheme.tertiaryContainer) {
            ButtonRow {
                DemoButton("A 详情 #1") { backStack.add(FeatureADetailKey("1")) }
                DemoButton("A 详情 #2") { backStack.add(FeatureADetailKey("2")) }
                DemoOutlinedButton("返回主 App") { backStack.removeLastOrNull() }
            }
        }
    }
    entry<FeatureADetailKey> { key ->
        Pane("Feature A · 详情 #${key.id}", color = MaterialTheme.colorScheme.tertiaryContainer) {
            ButtonRow { DemoOutlinedButton("返回") { backStack.removeLastOrNull() } }
        }
    }
}

// ==========================================================================
// 模拟「Feature B」模块：api（key） + impl（builder + 内容）
// ==========================================================================
// ---- api ----
@Serializable private data object FeatureBHomeKey : NavKey
@Serializable private data class FeatureBItemKey(val title: String) : NavKey

// ---- impl ----
private fun EntryProviderScope<NavKey>.featureBEntries(backStack: NavBackStack<NavKey>) {
    entry<FeatureBHomeKey> {
        Pane("Feature B · 首页", color = MaterialTheme.colorScheme.secondaryContainer) {
            ButtonRow {
                DemoButton("B 条目 · Kotlin") { backStack.add(FeatureBItemKey("Kotlin")) }
                DemoButton("B 条目 · Compose") { backStack.add(FeatureBItemKey("Compose")) }
                DemoOutlinedButton("返回主 App") { backStack.removeLastOrNull() }
            }
        }
    }
    entry<FeatureBItemKey> { key ->
        Pane("Feature B · ${key.title}", color = MaterialTheme.colorScheme.secondaryContainer) {
            ButtonRow { DemoOutlinedButton("返回") { backStack.removeLastOrNull() } }
        }
    }
}
