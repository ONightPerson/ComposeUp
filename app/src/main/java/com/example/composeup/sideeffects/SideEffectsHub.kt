package com.example.composeup.sideeffects

import androidx.activity.compose.BackHandler
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
 * 本包收录的「Compose 副作用（Side-effects）」示例。
 *
 * 副作用 = 发生在 composable 函数作用域**之外**的状态改变。由于 composable 的重组是
 * 「顺序不定、可能被丢弃、不可预测」的，理想情况下 composable 应当**无副作用**；
 * 但有时确实需要（弹 Snackbar、导航、埋点、订阅外部数据源……），
 * 此时必须把这些动作放进**感知 composable 生命周期**的受控环境里执行——这就是 Effect API。
 *
 * 本模块严格对照官方文章《Side-effects in Compose》
 * (developer.android.com/develop/ui/compose/side-effects) 列出的每一种 API，
 * 各做一个可交互示例，并按文章顺序编排。
 */
private enum class EffectTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    LaunchedEffect(
        title = "① LaunchedEffect：在 composable 作用域里跑 suspend",
        summary = "进入组合时启动协程、离开时取消；key 变化则取消旧协程、用新 key 重启。" +
            "本示例复刻官方「按可配置频率闪烁 alpha」的动画：改 pulseRateMs 会重启 effect。",
        keywords = "LaunchedEffect(key) · suspend · Animatable · isActive · key 变化重启",
    ),
    RememberCoroutineScope(
        title = "② rememberCoroutineScope：在回调里启动协程",
        summary = "LaunchedEffect 只能写在 composable 里；而按钮 onClick 这类**普通回调**里要启动协程，" +
            "就用 rememberCoroutineScope 拿到一个绑定组合位置的 scope，离开组合自动取消。复刻官方 Snackbar 示例。",
        keywords = "rememberCoroutineScope · scope.launch · onClick · SnackbarHostState",
    ),
    RememberUpdatedState(
        title = "③ rememberUpdatedState：effect 不重启也能拿到最新值",
        summary = "想让 effect 捕获某个值、但该值变化时**不重启** effect，就用 rememberUpdatedState 包一层。" +
            "复刻官方 LandingScreen：LaunchedEffect(true) 只随调用点生命周期，onTimeout 用最新值。",
        keywords = "rememberUpdatedState · LaunchedEffect(true) · 长生命周期 effect · 避免闭包捕获旧值",
    ),
    DisposableEffect(
        title = "④ DisposableEffect：需要清理的副作用",
        summary = "key 变化或离开组合时要「善后」的副作用（注册/注销监听器）。复刻官方示例：用 DisposableEffect " +
            "注册 LifecycleEventObserver，onDispose 里注销；切前后台即可看到 ON_START/ON_STOP 埋点。",
        keywords = "DisposableEffect(key) · onDispose · LifecycleEventObserver · rememberUpdatedState",
    ),
    SideEffect(
        title = "⑤ SideEffect：把 Compose 状态发布给非 Compose 代码",
        summary = "每次成功重组**之后**执行，用来把 Compose 状态同步给不受 Compose 管理的对象。" +
            "复刻官方 FirebaseAnalytics.setUserProperty 场景：把当前用户类型同步给分析库。",
        keywords = "SideEffect · 重组后执行 · 发布状态给外部对象 · analytics.setUserProperty",
    ),
    ProduceState(
        title = "⑥ produceState：把非 Compose 状态转成 Compose State",
        summary = "启动一个绑定组合的协程，把 Flow / LiveData / 回调 / suspend 结果推进一个返回的 State。" +
            "复刻官方 loadNetworkImage：initialValue=Loading，url 变化重启，产出 Success/Error。",
        keywords = "produceState(initial, keys) · value = · awaitDispose · 底层=remember+LaunchedEffect",
    ),
    DerivedStateOf(
        title = "⑦ derivedStateOf：把状态派生成「变化更少」的状态",
        summary = "当输入变化频率远高于 UI 真正需要更新的频率（如滚动位置），用它只在结果跨越阈值时才重组。" +
            "复刻官方 MessageList：firstVisibleItemIndex>0 才显示「回到顶部」按钮；并对比正确 / 错误用法。",
        keywords = "derivedStateOf · firstVisibleItemIndex · 类似 distinctUntilChanged · 减少重组",
    ),
    SnapshotFlow(
        title = "⑧ snapshotFlow：把 Compose 的 State 转成 Flow",
        summary = "把 State<T> 转成冷流，状态变化且新值≠旧值时发出（类似 distinctUntilChanged），" +
            "从而借用 Flow 的全部操作符。复刻官方示例：滚动越过首项时上报一次埋点事件。",
        keywords = "snapshotFlow { } · map/distinctUntilChanged/filter · collect · 埋点",
    ),
}

/**
 * 「Compose Side-effects」示例的入口页。
 *
 * 外层是话题列表，点进去看具体示例；示例内部用系统返回键或左上角箭头回到列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideEffectsHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<EffectTopic?>(null) }
    val topics = remember { EffectTopic.entries.toList() }

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
                            text = "Compose 进阶 · Side-effects",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "副作用是发生在 composable 作用域之外的状态改变。由于重组不可预测、可被丢弃，" +
                                "composable 应尽量无副作用；确有必要时，用 Effect API 放进「感知生命周期」的受控环境执行。\n" +
                                "本模块对照官方文章《Side-effects in Compose》逐个演示 8 种 API：\n" +
                                "· LaunchedEffect / rememberCoroutineScope —— 跑协程（组合内 / 回调里）；\n" +
                                "· rememberUpdatedState —— effect 不重启也能读到最新值；\n" +
                                "· DisposableEffect —— 需要清理的副作用（注册/注销）；\n" +
                                "· SideEffect —— 把状态发布给非 Compose 对象；\n" +
                                "· produceState —— 外部数据源 → Compose State；\n" +
                                "· derivedStateOf —— 派生出「变化更少」的状态，减少重组；\n" +
                                "· snapshotFlow —— Compose State → Flow。\n" +
                                "点击任意一项进入可交互演示。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    DemoRow(
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

    // 系统返回键先退回话题列表，而不是直接退出界面
    BackHandler { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { selected = null }) {
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
            EffectTopic.LaunchedEffect -> LaunchedEffectDemo(contentModifier)
            EffectTopic.RememberCoroutineScope -> RememberCoroutineScopeDemo(contentModifier)
            EffectTopic.RememberUpdatedState -> RememberUpdatedStateDemo(contentModifier)
            EffectTopic.DisposableEffect -> DisposableEffectDemo(contentModifier)
            EffectTopic.SideEffect -> SideEffectDemo(contentModifier)
            EffectTopic.ProduceState -> ProduceStateDemo(contentModifier)
            EffectTopic.DerivedStateOf -> DerivedStateOfDemo(contentModifier)
            EffectTopic.SnapshotFlow -> SnapshotFlowDemo(contentModifier)
        }
    }
}
