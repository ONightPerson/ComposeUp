package com.example.composeup.navigation3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

/** 装饰器通过 CompositionLocal 注入给每个 NavEntry 内容的「标签」。 */
private val LocalEntryTag = compositionLocalOf<String> { "（未被装饰）" }

/**
 * ⑧ 自定义 NavEntryDecorator：给每个 NavEntry 统一包裹逻辑。
 *
 * `NavEntryDecorator` 用一个 composable 包裹栈里的**每一个** NavEntry，即「装饰」它的内容。
 * 它有两个回调：
 *  - **decorate**：对每个进入组合的 NavEntry 调用，参数就是该 [androidx.navigation3.runtime.NavEntry]。
 *    可以在这里创建以 contentKey 为键的状态、用 [CompositionLocalProvider] 注入依赖、包裹 UI 或触发副作用；
 *    **必须调用 `entry.Content()`** 才能真正渲染内容。
 *  - **onPop**：当某个 NavEntry 被移出栈且离开组合时调用，参数是它的 contentKey，用来清理相关状态。
 *
 * 这适合「对每个目的地都要做同一件事」：日志 / 埋点、给每个 entry 建 ViewModelStore、
 * 注入依赖、清理资源。反例：只给单个目的地传依赖，或依赖作用域大于整个栈——那应直接在创建 NavEntry 时传。
 *
 * 本示例写一个 [LoggingNavEntryDecorator]：进入时打日志并注入 contentKey，离开时打日志。
 * ⚠️ 除非你的装饰器自己提供 SaveableStateProvider，否则要把
 * `rememberSaveableStateHolderNavEntryDecorator()` 放在装饰器列表**第一个**。
 */
@Composable
internal fun NavEntryDecoratorDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(DecHomeKey)
    val log = rememberDemoLog()

    // 装饰器不是 composable，用 remember 创建，并把日志回调捕获进去。
    val loggingDecorator = remember {
        LoggingNavEntryDecorator<NavKey> { line -> log(line) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenArea(height = 240.dp) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
                entryDecorators = listOf(
                    // 默认装饰器放第一个：负责 rememberSaveable 的状态保存
                    rememberSaveableStateHolderNavEntryDecorator(),
                    // 我们的自定义装饰器
                    loggingDecorator,
                ),
                entryProvider = entryProvider {
                    entry<DecHomeKey> {
                        // 内容通过 CompositionLocal 读到装饰器注入的 tag
                        val tag = LocalEntryTag.current
                        Pane("Home（被装饰）", color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("注入的 tag = $tag", style = MaterialTheme.typography.bodySmall)
                            ButtonRow {
                                DemoButton("打开 Page A") { backStack.add(DecPageKey("A")) }
                                DemoButton("打开 Page B") { backStack.add(DecPageKey("B")) }
                            }
                        }
                    }
                    entry<DecPageKey> { key ->
                        val tag = LocalEntryTag.current
                        Pane("Page ${key.id}（被装饰）", color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("注入的 tag = $tag", style = MaterialTheme.typography.bodySmall)
                            ButtonRow { DemoOutlinedButton("返回（触发 onPop）") { backStack.removeLastOrNull() } }
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
            SectionTitle("自定义装饰器怎么写")
            CodeBlock(
                """
                class LoggingNavEntryDecorator<T : Any>(
                    onEvent: (String) -> Unit,
                ) : NavEntryDecorator<T>(
                    decorate = { entry ->
                        onEvent("decorate: ${'$'}{entry.contentKey} 进入组合")
                        // 注入依赖，并且必须调用 entry.Content() 才会真正渲染
                        CompositionLocalProvider(
                            LocalEntryTag provides entry.contentKey.toString(),
                        ) { entry.Content() }
                    },
                    onPop = { contentKey ->
                        onEvent("onPop: ${'$'}contentKey 离开组合，清理其状态")
                    },
                )

                NavDisplay(
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(), // 放第一个
                        remember { LoggingNavEntryDecorator<NavKey>(::log) },
                    ),
                    /* ... */
                )
                """.trimIndent(),
            )
            Note(
                "试一试：打开 Page A → 观察 decorate 日志；返回 → 观察 onPop 日志；" +
                    "反复进出，看每个 contentKey 的进入/离开配对出现。需要保存状态的装饰器可参考官方 " +
                    "rememberSaveableStateHolderNavEntryDecorator 的源码实现。",
            )
            SectionTitle("装饰器回调日志（最新在上）")
            LogBox(lines = log)
        }
    }
}

/**
 * 一个把「进入 / 离开组合」打日志、并注入 contentKey 的自定义装饰器。
 *
 * [onEvent] 用普通构造参数（而非属性）被两个 lambda 捕获，避免在 super() 调用期间引用成员。
 */
private class LoggingNavEntryDecorator<T : Any>(
    onEvent: (String) -> Unit,
) : NavEntryDecorator<T>(
    decorate = { entry ->
        onEvent("🟢 decorate：${entry.contentKey} 进入组合")
        CompositionLocalProvider(LocalEntryTag provides entry.contentKey.toString()) {
            entry.Content()
        }
    },
    onPop = { contentKey ->
        onEvent("🔴 onPop：$contentKey 离开组合（清理其状态）")
    },
)

// ---- 本示例私有的 key ----
@Serializable private data object DecHomeKey : NavKey
@Serializable private data class DecPageKey(val id: String) : NavKey
