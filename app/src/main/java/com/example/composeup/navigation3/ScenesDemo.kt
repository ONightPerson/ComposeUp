package com.example.composeup.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

/**
 * ⑦ Scenes 与 scene decorators：自定义布局与二次加工。
 *
 * **Scene** 是渲染一个或多个 NavEntry 的布局单元，由 **SceneStrategy** 计算：
 * 给它当前栈解析出的 `List<NavEntry>`，它回答两个问题——「我能不能用这些 entry 组一个 Scene？」
 * （不能就返回 null，交给下一个策略；全都返回 null 时 NavDisplay 用默认 SinglePaneSceneStrategy），
 * 「能的话怎么排布？」。本示例演示三件事：
 *  1. **自定义双栏 Scene**：宽屏时把「列表 + 详情」并排渲染（靠 metadata 标记哪个是 list/detail）；
 *  2. **内置 DialogSceneStrategy**：给某个 entry 挂 `DialogSceneStrategy.dialog()` metadata，就弹成对话框；
 *  3. **scene decorator**：用 SceneDecoratorStrategy 对「算出来的 Scene」做第二阶段加工——这里统一加个顶栏。
 *
 * 切换下方「宽屏模式」开关，看双栏策略生效/失效；打开对话框，看装饰器**不会**去包裹 OverlayScene。
 */
@Composable
internal fun ScenesDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(SceneListKey)
    var wideScreen by rememberSaveable { mutableStateOf(false) }

    // 双栏策略依赖 wideScreen，切换时要重建；对话框策略无状态。
    val twoPaneStrategy = remember(wideScreen) { TwoPaneSceneStrategy<NavKey>(wideScreen) }
    val dialogStrategy = remember { DialogSceneStrategy<NavKey>() }
    val topBarDecorator = remember { TopBarSceneDecorator<NavKey>() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("宽屏模式（启用自定义双栏 Scene）", modifier = Modifier.weight(1f))
            Switch(checked = wideScreen, onCheckedChange = { wideScreen = it })
        }

        ScreenArea(height = 320.dp) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                // 场景策略：先试双栏，再试对话框，都不命中则回退默认单页
                sceneStrategies = listOf(twoPaneStrategy, dialogStrategy),
                // 场景装饰器：对算出来的 Scene 再加一层（顶栏）
                sceneDecoratorStrategies = listOf(topBarDecorator),
                entryProvider = entryProvider {
                    entry<SceneListKey>(metadata = listPane()) {
                        Pane("列表（List Pane）", color = MaterialTheme.colorScheme.primaryContainer) {
                            listOf("1", "2", "3").forEach { id ->
                                DemoButton("查看条目 #$id") {
                                    // 先移除已有详情，再加新的：宽屏下详情切换不整屏闪烁
                                    backStack.removeAll { it is SceneDetailKey }
                                    backStack.add(SceneDetailKey(id))
                                }
                            }
                            DemoOutlinedButton("打开对话框") { backStack.add(SceneDialogKey) }
                        }
                    }
                    entry<SceneDetailKey>(metadata = detailPane()) { key ->
                        Pane("详情（Detail Pane）#${key.id}", color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                if (wideScreen) "宽屏：与列表并排显示" else "窄屏：单独占满一页",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            ButtonRow { DemoOutlinedButton("返回") { backStack.removeLastOrNull() } }
                        }
                    }
                    entry<SceneDialogKey>(
                        metadata = DialogSceneStrategy.dialog(DialogProperties(usePlatformDefaultWidth = false)),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("对话框 Scene", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "由 DialogSceneStrategy 计算出的 OverlayScene，渲染在独立窗口，" +
                                        "因此 scene decorator 不会给它加顶栏。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                ButtonRow { DemoButton("关闭") { backStack.removeLastOrNull() } }
                            }
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
            SectionTitle("SceneStrategy 与 SceneDecoratorStrategy")
            CodeBlock(
                """
                // 自定义 Scene：并排渲染两个 NavEntry
                class TwoPaneScene<T : Any>(
                    override val key: Any,
                    override val previousEntries: List<NavEntry<T>>,
                    val listEntry: NavEntry<T>, val detailEntry: NavEntry<T>,
                ) : Scene<T> {
                    override val entries = listOf(listEntry, detailEntry)
                    override val content: @Composable () -> Unit = {
                        Row { Box(Modifier.weight(.4f)) { listEntry.Content() }
                              Box(Modifier.weight(.6f)) { detailEntry.Content() } }
                    }
                }

                // 策略：能组就返回 Scene，不能就返回 null 让下一个策略/默认接管
                class TwoPaneSceneStrategy<T : Any>(val wide: Boolean) : SceneStrategy<T> {
                    override fun SceneStrategyScope<T>.calculateScene(
                        entries: List<NavEntry<T>>,
                    ): Scene<T>? {
                        if (!wide) return null
                        val d = entries.lastOrNull()?.takeIf { it.metadata.contains(DetailPaneKey) } ?: return null
                        val l = entries.findLast { it.metadata.contains(ListPaneKey) } ?: return null
                        return TwoPaneScene(l.contentKey, entries.dropLast(1), l, d)
                    }
                }

                // scene decorator：第二阶段加工算出来的 Scene（这里加个顶栏）
                class TopBarSceneDecorator<T : Any> : SceneDecoratorStrategy<T> {
                    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> =
                        if (scene is OverlayScene<*>) scene else TopBarScene(scene)
                }
                """.trimIndent(),
            )
            Note(
                "要点：Scene 由「类 + key」唯一标识，变化即触发顶层动画；自定义 Scene 要正确实现 " +
                    "equals/hashCode（用 data class 最省事）。装饰 OverlayScene 没有意义（内容在独立窗口），" +
                    "所以 TopBarSceneDecorator 遇到对话框就原样返回。",
            )
        }
    }
}

// ==========================================================================
// metadata key：标记某个 NavEntry 属于列表栏还是详情栏
// ==========================================================================
private object ListPaneKey : NavMetadataKey<Boolean>
private object DetailPaneKey : NavMetadataKey<Boolean>

private fun listPane() = metadata { put(ListPaneKey, true) }
private fun detailPane() = metadata { put(DetailPaneKey, true) }

// ==========================================================================
// 自定义双栏 Scene 与其策略
// ==========================================================================
private data class TwoPaneScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val listEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(listEntry, detailEntry)
    override val content: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.4f)) { listEntry.Content() }
            Box(modifier = Modifier.weight(0.6f)) { detailEntry.Content() }
        }
    }
}

private class TwoPaneSceneStrategy<T : Any>(private val wideScreen: Boolean) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!wideScreen) return null
        val detailEntry = entries.lastOrNull()?.takeIf { it.metadata.contains(DetailPaneKey) } ?: return null
        val listEntry = entries.findLast { it.metadata.contains(ListPaneKey) } ?: return null
        // 用 list 的 contentKey 作为 sceneKey：切换详情时整屏不重新动画
        return TwoPaneScene(
            key = listEntry.contentKey,
            previousEntries = entries.dropLast(1),
            listEntry = listEntry,
            detailEntry = detailEntry,
        )
    }
}

// ==========================================================================
// scene decorator：给非 Overlay 的 Scene 统一加一个顶栏
// ==========================================================================
private class TopBarSceneDecorator<T : Any> : SceneDecoratorStrategy<T> {
    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> =
        // NavDisplay 不会装饰 OverlayScene（对话框），这里也主动跳过
        if (scene is OverlayScene<*>) scene else TopBarScene(scene)
}

private data class TopBarScene<T : Any>(
    val scene: Scene<T>,
) : Scene<T> {
    // key 从「被装饰 Scene 的类 + key」派生，保留内建动画
    override val key: Any get() = scene::class to scene.key
    override val entries: List<NavEntry<T>> get() = scene.entries
    override val previousEntries: List<NavEntry<T>> get() = scene.previousEntries
    override val metadata: Map<String, Any> get() = scene.metadata
    override val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "🧭 由 SceneDecoratorStrategy 统一加的顶栏",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) { scene.content() }
        }
    }
}

// ---- 本示例私有的 key ----
@Serializable private data object SceneListKey : NavKey
@Serializable private data class SceneDetailKey(val id: String) : NavKey
@Serializable private data object SceneDialogKey : NavKey
