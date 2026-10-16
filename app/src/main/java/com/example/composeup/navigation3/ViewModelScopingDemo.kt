package com.example.composeup.navigation3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * ② 把 ViewModel 的作用域限制在 NavEntry。
 *
 * 默认情况下 ViewModel 绑定到最近的 `ViewModelStoreOwner`（通常是 Activity），
 * 于是所有目的地共用一个实例。加上 `rememberViewModelStoreNavEntryDecorator()` 后，
 * **每个 NavEntry 都拥有独立的 ViewModelStore**：在 entry 内容里 `viewModel()` 拿到的实例，
 * 会按该 NavEntry 的 `contentKey` 隔离——
 *  - 同一个 key（如 DetailKey("1")）来回进出：复用同一个 VM，计数保留；
 *  - 不同实例（DetailKey("1") vs DetailKey("2")）：各自独立的 VM；
 *  - key 被 pop 出栈：对应 VM 触发 onCleared。
 *
 * 本示例把 VM 的 created / cleared / 计数变化打进日志，直观展示「作用域 = NavEntry」。
 */
@Composable
internal fun ViewModelScopingDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(HomeKey)
    val log = rememberDemoLog()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenArea {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = {
                    if (backStack.size > 1) backStack.removeLastOrNull() else onExit()
                },
                // 关键：两个装饰器。SaveableStateHolder 负责状态保存，
                // ViewModelStore 装饰器负责给每个 NavEntry 一个独立 ViewModelStore。
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        Pane("Home · 选择要打开的详情", color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("每个 Detail(id) 是独立 NavEntry，拥有独立 VM。", style = MaterialTheme.typography.bodySmall)
                            ButtonRow {
                                DemoButton("打开 Detail(#1)") { backStack.add(DetailKey("1")) }
                                DemoButton("打开 Detail(#2)") { backStack.add(DetailKey("2")) }
                            }
                        }
                    }
                    entry<DetailKey> { key ->
                        // viewModel() 会自动 scope 到当前 NavEntry（靠上面的 ViewModelStore 装饰器）。
                        // 相同 contentKey 复用同一实例，无需手动传 key 参数。
                        val vm: CounterViewModel = viewModel(
                            factory = CounterViewModel.Factory(key.id) { line -> log(line) },
                        )
                        DetailPane(key = key, vm = vm, onBack = { backStack.removeLastOrNull() })
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
            SectionTitle("怎么验证作用域")
            Note(
                "1. 打开 Detail(#1)，点几次「+1」把计数加上去；\n" +
                    "2. 返回 Home，再打开 Detail(#1)——计数还在（同一 contentKey 复用同一 VM）；\n" +
                    "3. 打开 Detail(#2)——它是另一个 NavEntry，计数从 0 开始（独立 VM）；\n" +
                    "4. 把某个 Detail 彻底 pop 出栈——日志出现该 VM 的 onCleared。",
            )

            SectionTitle("关键代码")
            CodeBlock(
                """
                NavDisplay(
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        // 给每个 NavEntry 一个独立 ViewModelStore
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        entry<DetailKey> { key ->
                            // 自动 scope 到该 NavEntry 的 contentKey，
                            // 相同 key 复用、pop 出栈即 onCleared
                            val vm: CounterViewModel = viewModel(factory = ...)
                        }
                    },
                )
                """.trimIndent(),
            )

            SectionTitle("VM 生命周期日志（最新在上）")
            LogBox(lines = log)
        }
    }
}

/** 详情页内容：显示 VM 身份、计数，可自增，可返回。 */
@Composable
private fun DetailPane(key: DetailKey, vm: CounterViewModel, onBack: () -> Unit) {
    Pane(
        title = "Detail #${key.id}",
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text("VM 实例 = ${vm.tag}", style = MaterialTheme.typography.bodySmall)
        Text("count = ${vm.count}", style = MaterialTheme.typography.titleMedium)
        ButtonRow {
            DemoButton("+1") { vm.increment() }
            DemoOutlinedButton("返回 Home") { onBack() }
        }
    }
}

/**
 * 计数 ViewModel。构造与销毁都通过 [onEvent] 打日志，
 * 以便观察它随 NavEntry 生灭的作用域行为。
 */
private class CounterViewModel(
    val id: String,
    private val onEvent: (String) -> Unit,
) : ViewModel() {

    var count by mutableIntStateOf(0)
        private set

    /** 用 hashCode 末四位当作可读的实例标识，方便区分「是不是同一个 VM」。 */
    val tag: String = "CounterVM#$id@${Integer.toHexString(hashCode()).takeLast(4)}"

    init {
        onEvent("✅ VM created：$tag")
    }

    fun increment() {
        count++
        onEvent("Detail #$id count → $count")
    }

    override fun onCleared() {
        onEvent("🧹 VM onCleared：$tag（该 NavEntry 已出栈）")
    }

    class Factory(
        private val id: String,
        private val onEvent: (String) -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CounterViewModel(id, onEvent) as T
    }
}
