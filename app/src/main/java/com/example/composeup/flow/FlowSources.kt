package com.example.composeup.flow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

/**
 * 本模块各示例共享的「假数据源」。
 *
 * 讲 Flow 最怕空谈 API，所以这里把每个抽象概念都落到一个**贴近真实业务**的数据源上，
 * 各 Demo 直接复用它们，让「冷流 / 操作符 / 背压 / 生命周期」都有具体的业务语义：
 *
 *  - [articleFeed]      —— 模拟一次「网络分页拉取」，是典型的**冷流**（每次 collect 都重新请求）；
 *  - [searchArticles]   —— 模拟「按关键词检索」，检索有耗时，配合 flatMapLatest 演示**取消旧请求**；
 *  - [sensorStream]     —— 模拟「高频传感器 / 行情推送」，生产远快于消费，用来演示**背压**；
 *  - [ALL_ARTICLES]     —— 供检索命中用的静态语料。
 *
 * 这些都是纯 Kotlin 的 Flow 构造，不依赖 Android，方便单独理解；真正与生命周期 / Compose
 * 绑定的部分放在各自的 Demo 里。
 */

/** 检索用的静态语料。 */
internal val ALL_ARTICLES: List<String> = listOf(
    "Compose 布局基础",
    "Compose 动画进阶",
    "Flow 冷流与热流",
    "Flow 常用操作符",
    "StateFlow 管理 UI 状态",
    "SharedFlow 发送一次性事件",
    "背压：buffer 与 conflate",
    "生命周期安全收集 repeatOnLifecycle",
)

/**
 * 模拟一次网络分页拉取文章标题：**冷流**。
 *
 * 关键点：`flow { }` 里的代码在**每次被 collect 时**才从头执行一遍——这正是「冷流」的定义。
 * 中间用 [delay] 假装网络耗时，用 `emit` 逐条发出数据（而不是攒齐了一次性返回）。
 */
internal fun articleFeed(): Flow<String> = flow {
    ALL_ARTICLES.take(5).forEachIndexed { index, title ->
        delay(400.milliseconds)                 // 模拟逐条到达的网络延迟
        emit("第 ${index + 1} 条：$title")
    }
}

/**
 * 模拟「按关键词检索文章」：检索本身耗时 500ms。
 *
 * 它被设计成一个**冷流**，专门配合 `flatMapLatest` 使用——当用户连续输入、
 * 上一个检索还没返回时，flatMapLatest 会**取消**上一次检索，只对最新关键词保留结果，
 * 这就是搜索框「输入即搜、不闪烁、不堆积过期请求」的核心机制。
 */
internal fun searchArticles(query: String): Flow<List<String>> = flow {
    delay(500.milliseconds) // 模拟检索耗时；若期间来了新查询，flatMapLatest 会让这次执行被取消
    val result = if (query.isBlank()) {
        ALL_ARTICLES.take(4)
    } else {
        ALL_ARTICLES.filter { it.contains(query, ignoreCase = true) }
    }
    emit(result)
}

/**
 * 模拟高频数据源（传感器 / 行情 tick）：每 [intervalMs] 毫秒发出一个递增序号，共 [count] 个。
 *
 * 生产速度远快于「慢消费者」的处理速度，用它来制造背压场景，
 * 直观对比「默认挂起 / buffer 缓冲 / conflate 丢弃 / collectLatest 重启」四种策略的差异。
 */
internal fun sensorStream(intervalMs: Long = 50L, count: Int = 20): Flow<Int> = flow {
    repeat(count) { i ->
        delay(intervalMs.milliseconds)
        emit(i + 1)
    }
}