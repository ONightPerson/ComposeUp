package com.example.composeup.navigation3

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

/**
 * 本包收录的「Jetpack Navigation 3」示例。
 *
 * Navigation 3（nav3）是 Jetpack 官方的新一代 Compose 导航库，核心理念是
 * **「返回栈归你所有」**：你自己持有一个 `SnapshotStateList<NavKey>`，库通过 `NavDisplay`
 * 观察它、把 key 解析成 `NavEntry` 并渲染。围绕这个内核，官方文档拆出了一系列进阶能力，
 * 本模块把它们逐个做成可交互示例，串成「从一个返回栈出发，逐步长出完整导航系统」的主线：
 *
 * ① 管理返回栈 —— 一切的地基：create / push / pop / NavDisplay。
 * ② ViewModel 作用域限制在 NavEntry —— 用装饰器让 VM 随某个 key 生灭。
 * ③ 返回结果 —— ResultEventBus 跨目的地回传数据（事件式 / 状态式）。
 * ④ 配置 metadata —— 给 NavEntry 挂类型安全的元信息，驱动动画、分栏。
 * ⑤ 模块化导航 —— api/impl 拆分，用 entryProvider builder 组合各功能模块。
 * ⑥ 支持 deeplinks —— UriDeepLinkMatcher / StaticKey / 合成返回栈 / 自定义 matcher。
 * ⑦ Scenes 与 scene decorators —— 自定义布局（对话框、双栏），并用装饰器二次加工 Scene。
 * ⑧ 自定义 NavEntryDecorator —— 给每个 NavEntry 统一包裹逻辑（日志、依赖注入、清理）。
 */
private enum class Nav3Topic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    BackStack(
        title = "① 管理返回栈：你持有栈，NavDisplay 负责渲染",
        summary = "nav3 里返回栈装的不是内容而是 key（引用）。本示例用 rememberNavBackStack 建栈，" +
            "演示 add 前进（push）、removeLastOrNull 后退（pop）、直接改写栈，" +
            "并实时把栈内容画成一摞卡片，配 NavDisplay 把 key 解析成 NavEntry 显示。",
        keywords = "NavKey · rememberNavBackStack · NavBackStack · add · removeLastOrNull · NavDisplay · entryProvider",
    ),
    ViewModelScoping(
        title = "② 把 ViewModel 作用域限制在 NavEntry",
        summary = "默认 VM 绑定到 Activity，多个目的地会共用一个实例。加上 " +
            "rememberViewModelStoreNavEntryDecorator 后，每个 NavEntry 拥有独立 ViewModelStore，" +
            "viewModel() 按 contentKey 隔离：同一个 key 复用、不同实例各自独立、pop 出栈即清空。",
        keywords = "rememberViewModelStoreNavEntryDecorator · viewModel() · ViewModelStore · contentKey · onCleared",
    ),
    ReturnResults(
        title = "③ 返回结果：ResultEventBus 跨目的地回传",
        summary = "1.2.0 起用 ResultEventBus 回传结果。本示例演示两种消费模型：" +
            "ResultEffect（事件式，按序处理一次性结果，可挂起）与 conflateAsState（状态式，只保留最新值）；" +
            "以及显式 key 与类型派生 key 两种寻址方式。",
        keywords = "ResultEventBus · sendResult · ResultEffect · conflateAsState · LocalResultEventBus · resultKey",
    ),
    Metadata(
        title = "④ 配置 metadata：给 NavEntry 挂类型安全元信息",
        summary = "metadata 本质是 Map<String, Any>，库用它在 NavEntry / Scene / NavDisplay 之间传递信息。" +
            "本示例用 metadata DSL + NavMetadataKey 类型安全地读写，并据此为某个目的地覆盖默认转场动画" +
            "（NavDisplay.TransitionKey），实现「同一栈、不同页面、不同动画」。",
        keywords = "metadata DSL · NavMetadataKey · put · contains · get · NavDisplay.TransitionKey",
    ),
    Modularization(
        title = "⑤ 模块化导航：api/impl 拆分与组合",
        summary = "把每个功能的 key 放进 api、entryProvider 与内容放进 impl，主 App 只做组合。" +
            "本示例模拟两个功能模块各自暴露一个 EntryProviderScope builder，" +
            "主 NavDisplay 用一个 entryProvider 把它们拼起来，展示去中心化的注册方式。",
        keywords = "api/impl · EntryProviderScope · entryProvider builder · 组合 · 解耦",
    ),
    DeepLinks(
        title = "⑥ 支持 deeplinks：匹配与自定义 matchers",
        summary = "1.2.0 起用 DeepLinkRequest + DeepLinkMatcher。本示例可交互输入 URI，" +
            "演示 UriDeepLinkMatcher 按模式抽取参数、StaticKeyDeepLinkMatcher 匹配固定链接、" +
            "withBackStack 合成返回栈，以及自定义 TelDeepLinkMatcher 处理 tel: 这类 opaque URI。",
        keywords = "DeepLinkRequest · UriDeepLinkMatcher · StaticKeyDeepLinkMatcher · withBackStack · 自定义 DeepLinkMatcher · maxOrNull",
    ),
    Scenes(
        title = "⑦ Scenes 与 scene decorators：自定义布局与二次加工",
        summary = "Scene 是渲染一个或多个 NavEntry 的布局单元，由 SceneStrategy 计算。本示例演示：" +
            "DialogSceneStrategy 把目的地弹成对话框、自定义双栏 Scene，以及用 SceneDecoratorStrategy " +
            "给算出来的 Scene 统一加一个顶栏（scene decorators 的第二阶段加工）。",
        keywords = "Scene · SceneStrategy · DialogSceneStrategy · 自定义 Scene · SceneDecoratorStrategy · OverlayScene",
    ),
    NavEntryDecorator(
        title = "⑧ 自定义 NavEntryDecorator：给每个 NavEntry 包裹逻辑",
        summary = "NavEntryDecorator 用 composable 包裹栈里每个 NavEntry。本示例写一个日志装饰器：" +
            "decorate 在 entry 进入组合时触发（并调用 entry.Content()）、onPop 在离开时清理，" +
            "再用 CompositionLocalProvider 给内容注入依赖，直观看到装饰器的生命周期。",
        keywords = "NavEntryDecorator · decorate · onPop · entry.Content() · CompositionLocalProvider · entryDecorators",
    ),
}

/**
 * 「Jetpack Navigation 3」示例的入口页。
 *
 * 外层是话题列表，点进去看具体示例；示例内部用系统返回键或左上角箭头回到列表。
 * 与 flow 模块一致：这里用 rememberSaveable 记住选中的话题，各 Demo 内部各自持有
 * 独立的返回栈与 NavDisplay。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Nav3Hub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<Nav3Topic?>(null) }
    val topics = remember { Nav3Topic.entries.toList() }

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
                            text = "Jetpack · Navigation 3",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "Navigation 3 的核心理念是「返回栈归你所有」：你持有一个 SnapshotStateList<NavKey>，\n" +
                                "NavDisplay 观察它、把 key 解析成 NavEntry 并渲染。这一组示例串成一条主线：\n" +
                                "· ① 从一个返回栈出发 —— create / push / pop / NavDisplay；\n" +
                                "· ② 给每个目的地独立状态 —— ViewModel 作用域限制在 NavEntry；\n" +
                                "· ③ 目的地之间回传数据 —— ResultEventBus；\n" +
                                "· ④ 给 NavEntry 挂元信息 —— 类型安全的 metadata；\n" +
                                "· ⑤ 代码规模化 —— 模块化 api/impl 与组合；\n" +
                                "· ⑥ 从外部链接进入 —— deeplinks 匹配与自定义 matcher；\n" +
                                "· ⑦ 自定义布局 —— Scenes 与 scene decorators；\n" +
                                "· ⑧ 统一包裹逻辑 —— 自定义 NavEntryDecorator。\n" +
                                "下面 8 个示例逐个拆解，点击任意一项进入可交互演示。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    Nav3Row(
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

    val exit: () -> Unit = { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = exit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回话题列表",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (current) {
            Nav3Topic.BackStack -> BackStackDemo(contentModifier, exit)
            Nav3Topic.ViewModelScoping -> ViewModelScopingDemo(contentModifier, exit)
            Nav3Topic.ReturnResults -> ReturnResultsDemo(contentModifier, exit)
            Nav3Topic.Metadata -> MetadataDemo(contentModifier, exit)
            Nav3Topic.Modularization -> ModularizationDemo(contentModifier, exit)
            Nav3Topic.DeepLinks -> DeepLinksDemo(contentModifier, exit)
            Nav3Topic.Scenes -> ScenesDemo(contentModifier, exit)
            Nav3Topic.NavEntryDecorator -> NavEntryDecoratorDemo(contentModifier, exit)
        }
    }
}
