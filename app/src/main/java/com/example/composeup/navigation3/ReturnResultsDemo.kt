package com.example.composeup.navigation3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

/**
 * ③ 返回结果：ResultEventBus 跨目的地回传数据。
 *
 * 1.2.0 起，nav3 用 `ResultEventBus` 在目的地之间回传结果。它有两种消费模型：
 *  - **事件式 [ResultEffect]**：按序处理每一条结果，适合一次性事件（弹 Snackbar、写进 VM），
 *    effect body 运行在协程里，可直接调用挂起函数；
 *  - **状态式 conflateAsState**：只保留最新值并暴露为 Compose State，适合轻量 UI 修饰（选中的滤镜/颜色）。
 *
 * 寻址方式也有两种：**显式 key**（resultKey = "..."，用于同类型多实例）与
 * **类型派生 key**（不传 key，用类型的 KClass 生成，用于领域内唯一类型）。
 * ⚠️ 发送方与接收方必须都用显式 key、或都用类型派生 key，二者不互通。
 *
 * 前提：给 NavDisplay 加 `rememberResultEventBusNavEntryDecorator()`，它会通过
 * `LocalResultEventBus` 把总线提供给每个目的地的内容。
 */
@Composable
internal fun ReturnResultsDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(ResultHomeKey)
    val log = rememberDemoLog()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenArea(height = 300.dp) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    // 提供 LocalResultEventBus 给每个 NavEntry
                    rememberResultEventBusNavEntryDecorator(),
                ),
                entryProvider = entryProvider {

                    // ---- 首页：两个入口 ----
                    entry<ResultHomeKey> {
                        Pane("选择一种回传方式", color = MaterialTheme.colorScheme.primaryContainer) {
                            ButtonRow {
                                DemoButton("事件式：选地址") { backStack.add(RideSummaryKey) }
                                DemoButton("状态式：选颜色") { backStack.add(ThemePreviewKey) }
                            }
                        }
                    }

                    // ---- 事件式：RideSummary 用 ResultEffect 接收地址 ----
                    entry<RideSummaryKey> {
                        // 用可变状态承载结果；真实项目里通常转发给 ViewModel。
                        var pickup by remember { mutableStateOf("（未选）") }
                        var destination by remember { mutableStateOf("（未选）") }

                        // ResultEffect 按 resultKey 监听；每来一条就按序执行 effect body。
                        ResultEffect<Address>(resultKey = "pickup_address") { address ->
                            pickup = address.label
                            log("ResultEffect[pickup] ← ${address.label}")
                        }
                        ResultEffect<Address>(resultKey = "destination_address") { address ->
                            destination = address.label
                            log("ResultEffect[destination] ← ${address.label}")
                        }

                        Pane("行程汇总（事件式接收）", color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("上车点：$pickup", style = MaterialTheme.typography.bodyMedium)
                            Text("目的地：$destination", style = MaterialTheme.typography.bodyMedium)
                            ButtonRow {
                                DemoButton("选上车点") { backStack.add(AddressPickerKey("pickup_address")) }
                                DemoButton("选目的地") { backStack.add(AddressPickerKey("destination_address")) }
                                DemoOutlinedButton("返回") { backStack.removeLastOrNull() }
                            }
                        }
                    }

                    // ---- 地址选择器：sendResult(显式 key) 后 pop ----
                    entry<AddressPickerKey> { key ->
                        val resultBus = LocalResultEventBus.current
                        Pane("选择地址（slot=${key.slot}）", color = MaterialTheme.colorScheme.secondaryContainer) {
                            Address.samples.forEach { address ->
                                DemoButton(address.label) {
                                    // 发送方：用显式 key 发送结果，然后返回。
                                    resultBus.sendResult(key.slot, address)
                                    log("sendResult[${key.slot}] → ${address.label}")
                                    backStack.removeLastOrNull()
                                }
                            }
                            DemoOutlinedButton("取消") { backStack.removeLastOrNull() }
                        }
                    }

                    // ---- 状态式：ThemePreview 用 conflateAsState 观察最新颜色 ----
                    entry<ThemePreviewKey> {
                        val resultBus = LocalResultEventBus.current
                        // conflateAsState 只保留最新值，并作为 Compose State 触发重组。
                        val primary by resultBus.conflateAsState<Color>(
                            resultKey = "primary_color",
                            defaultValue = Color.Gray,
                        )
                        Pane("主题预览（状态式接收）", color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(primary, RoundedCornerShape(8.dp))
                                    .padding(vertical = 16.dp),
                                color = primary,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "  当前主色 = $primary",
                                    color = Color.White,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            ButtonRow {
                                DemoButton("选主色") { backStack.add(ColorPickerKey) }
                                DemoOutlinedButton("返回") { backStack.removeLastOrNull() }
                            }
                        }
                    }

                    // ---- 颜色选择器：sendResult(显式 key) ----
                    entry<ColorPickerKey> {
                        val resultBus = LocalResultEventBus.current
                        Pane("选择主色", color = MaterialTheme.colorScheme.secondaryContainer) {
                            listOf(Color.Red, Color.Green, Color.Blue, Color.Magenta).forEach { c ->
                                DemoButton(c.toString()) {
                                    resultBus.sendResult("primary_color", c)
                                    log("sendResult[primary_color] → $c")
                                    backStack.removeLastOrNull()
                                }
                            }
                            DemoOutlinedButton("取消") { backStack.removeLastOrNull() }
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
            SectionTitle("两种消费模型")
            CodeBlock(
                """
                // 前提：装饰器提供 LocalResultEventBus
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberResultEventBusNavEntryDecorator(),
                )

                // 事件式：按序处理每条结果，可挂起
                ResultEffect<Address>(resultKey = "pickup_address") { address ->
                    viewModel.onPickupSelected(address)
                }

                // 状态式：只保留最新值，暴露为 State
                val primary by resultBus.conflateAsState<Color>(
                    resultKey = "primary_color", defaultValue = Color.Gray,
                )

                // 发送方：sendResult 后 pop
                resultBus.sendResult("pickup_address", address)
                """.trimIndent(),
            )
            Note(
                "提示：ResultEventBus 是内存总线，结果不会自动跨进程死亡保存；" +
                    "若必须存活，用 rememberSaveable / SavedStateHandle / 或把数据塞进 NavKey。",
            )
            SectionTitle("收发日志（最新在上）")
            LogBox(lines = log)
        }
    }
}

/** 领域数据：地址。 */
private data class Address(val label: String) {
    companion object {
        val samples = listOf(
            Address("公司 · 科技园 A 座"),
            Address("家 · 幸福小区 3 栋"),
            Address("机场 · T2 出发层"),
        )
    }
}

// ---- 本示例私有的 key（与共享 key 区分，避免语义混淆）----
@Serializable private data object ResultHomeKey : NavKey
@Serializable private data object RideSummaryKey : NavKey
@Serializable private data class AddressPickerKey(val slot: String) : NavKey
@Serializable private data object ThemePreviewKey : NavKey
@Serializable private data object ColorPickerKey : NavKey
