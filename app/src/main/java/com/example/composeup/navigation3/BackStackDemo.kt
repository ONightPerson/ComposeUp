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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

/**
 * ① 管理返回栈。
 *
 * 核心心智模型：**在 Navigation 3 里，你拥有返回栈**。它就是一个 `SnapshotStateList<NavKey>`，
 * 装的是 key（内容的引用）而不是内容。前进 = 往栈尾 add 一个 key，后退 = removeLastOrNull。
 * `NavDisplay` 观察这个栈，用 `entryProvider` 把栈顶 key 解析成 `NavEntry` 渲染出来。
 *
 * 本示例把「看不见的栈」画成 [BackStackView]，并把每次 push/pop 打进 [LogBox]，
 * 让你在点按钮时直观看到栈与界面的联动。
 */
@Composable
internal fun BackStackDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    // rememberNavBackStack 返回的栈可跨配置变更 / 进程死亡存活（要求 key 是 @Serializable NavKey）。
    val backStack = rememberNavBackStack(HomeKey)
    val log = rememberDemoLog()

    Column(modifier = modifier.fillMaxSize()) {
        // 上半区：NavDisplay 渲染当前栈顶对应的 NavEntry。
        ScreenArea {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = {
                    // 系统返回 / 预测性返回：栈里还有内容就 pop，否则退出到话题列表。
                    if (backStack.size > 1) {
                        val popped = backStack.last()
                        backStack.removeLastOrNull()
                        log("pop ← $popped")
                    } else {
                        onExit()
                    }
                },
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        Pane("Home 主页", color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("这是栈底。点下方按钮 push 新页面。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    entry<DetailKey> { key ->
                        Pane("Detail 详情 #${key.id}", color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("DetailKey(id = \"${key.id}\")", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    entry<SettingsKey> {
                        Pane("Settings 设置", color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("SettingsKey", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
            )
        }

        // 下半区：操作 + 栈可视化 + 代码 + 日志。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("操作返回栈")
            ButtonRow {
                DemoButton("push Detail(#${backStack.detailCount() + 1})") {
                    val key = DetailKey(id = (backStack.detailCount() + 1).toString())
                    backStack.add(key)
                    log("push → $key")
                }
                DemoButton("push Settings") {
                    backStack.add(SettingsKey)
                    log("push → SettingsKey")
                }
                DemoOutlinedButton("pop（removeLastOrNull）") {
                    val popped = backStack.removeLastOrNull()
                    log(if (popped != null) "pop ← $popped" else "栈已空，忽略")
                }
                DemoOutlinedButton("直接改写栈") {
                    // 你拥有栈，所以可以任意增删：这里一次性重置为 Home + 两个 Detail。
                    backStack.clear()
                    backStack.add(HomeKey)
                    backStack.add(DetailKey("A"))
                    backStack.add(DetailKey("B"))
                    log("rewrite → [Home, Detail(A), Detail(B)]")
                }
            }

            BackStackView(keys = backStack.toList())

            SectionTitle("关键代码")
            CodeBlock(
                """
                // key 装的是引用，不是内容；@Serializable + NavKey 才能被保存
                @Serializable data object HomeKey : NavKey
                @Serializable data class DetailKey(val id: String) : NavKey

                // 你持有返回栈（可跨配置变更/进程死亡存活）
                val backStack = rememberNavBackStack(HomeKey)

                backStack.add(DetailKey("1"))   // push：前进
                backStack.removeLastOrNull()    // pop：后退

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<HomeKey> { /* ... */ }
                        entry<DetailKey> { key -> /* ... */ }
                    },
                )
                """.trimIndent(),
            )

            SectionTitle("运行日志（最新在上）")
            LogBox(lines = log)
            Note(
                "要点：NavDisplay 默认只显示栈顶那一个 NavEntry（SinglePaneSceneStrategy）。" +
                    "栈是你的普通 List，所以 push/pop/清空/重排都随你——库只负责把它渲染出来。",
            )
        }
    }
}

/** 统计栈里已有多少个 DetailKey，用来给下一个 push 编号。 */
private fun List<NavKey>.detailCount(): Int = count { it is DetailKey }
