package com.example.composeup.architecture.data

import com.example.composeup.architecture.domain.NewsArticle
import kotlinx.coroutines.delay

fun interface NewsSource {
    suspend fun fetchNews(): List<NewsArticle>
}

class DemoNewsSource : NewsSource {
    override suspend fun fetchNews(): List<NewsArticle> {
        delay(700)
        return listOf(
            NewsArticle(
                id = "architecture",
                title = "从页面到系统：让新闻应用拥有清晰的分层",
                summary = "UI 负责展示，领域层表达用户目标，数据层维护事实。一次小小的收藏，串起整个架构。",
                category = "架构观察",
                author = "ComposeUp 编辑部",
                publishedAt = "09:30 · 演示内容",
                paragraphs = listOf(
                    "当新闻首页和详情页都能收藏文章时，问题不再只是按钮如何变色，而是谁负责保存收藏状态。如果两个页面各自维护一份文章，返回时就可能看到不同的结果。",
                    "分层的第一步是明确职责：Compose 只渲染状态并上报操作；ViewModel 把操作交给 UseCase，并组合屏幕状态；Repository 管理唯一的数据源。依赖通过构造函数传入，而不是在页面里临时创建。",
                    "在这个示例中，首页和详情页共享一份按 ID 索引的文章表。用户在任何地方点击收藏，都只更新表中的一条记录，两处界面随后从同一个状态源得到新结果。",
                ),
            ),
            NewsArticle(
                id = "use-case",
                title = "UseCase：用用户目标命名一次操作",
                summary = "观察新闻、刷新新闻、切换收藏，把业务入口从页面点击回调中抽离出来。",
                category = "工程实践",
                author = "移动开发小组",
                publishedAt = "09:10 · 演示内容",
                paragraphs = listOf(
                    "UseCase 的名字应当描述用户想做什么，而不是屏幕上有什么控件。RefreshNewsUseCase 表达刷新新闻，即使触发入口由工具栏变成下拉手势，这个操作仍然成立。",
                    "当前例子的操作很简单，因此 UseCase 只委托给 Repository。保留这层是为了展示依赖边界，而不是声称每一个项目都必须增加只有一行代码的类。",
                    "当业务变复杂时，可以在领域层组合多个仓库的数据、落实业务规则，并用普通单元测试验证。UI 不应因此开始依赖数据库、网络客户端或它们的异常类型。",
                ),
            ),
            NewsArticle(
                id = "normalized-state",
                title = "一份实体，多种视图：状态归一化如何消除不同步",
                summary = "articlesById 保存实体，feedIds 保存顺序，selectedArticleId 保存选择。不要复制能够推导出的状态。",
                category = "状态管理",
                author = "ComposeUp 编辑部",
                publishedAt = "08:45 · 演示内容",
                paragraphs = listOf(
                    "把首页文章列表、收藏文章列表和详情文章分别保存，看上去很直接，却意味着每次更新都必须找到并修改所有副本。漏掉一个副本，就会产生状态不一致。",
                    "归一化后的状态只保存 articlesById 和 feedIds。首页通过 ID 取得实体，详情页也只持有文章 ID。收藏列表、收藏数量和当前详情都是读取时派生出来的投影，而不是另一份可写事实。",
                    "刷新请求进行时仍然允许收藏。响应写回仓库时，会读取最新实体的本地收藏字段再合并，因此请求开始后发生的收藏不会被旧响应覆盖。",
                ),
            ),
            NewsArticle(
                id = "unidirectional-flow",
                title = "单向数据流：把变化放进一条可以追踪的链路",
                summary = "事件向上，状态向下。让页面成为状态的投影，而不是业务执行现场。",
                category = "Compose 专栏",
                author = "移动开发小组",
                publishedAt = "08:20 · 演示内容",
                paragraphs = listOf(
                    "新闻卡片不知道 Repository 的存在。它接收文章数据，并在点击时上报一个包含文章 ID 的事件。ViewModel 是界面操作进入业务层的统一入口。",
                    "Repository 发布只读 StateFlow，ViewModel 组合新闻快照、筛选条件和当前选择，生成 NewsUiState。Compose 使用 collectAsStateWithLifecycle 订阅，界面不可见时不再持续收集。",
                    "详情导航在这里也是持久状态：当前选中的文章 ID。返回操作将 ID 清空，不需要通过一次性消息同步两页，也不依赖可能在后台丢失的导航事件。",
                ),
            ),
            NewsArticle(
                id = "refresh",
                title = "刷新不是清空：保留旧内容的加载体验",
                summary = "首次加载显示进度，后续刷新保留文章；失败可重试，取消不伪装成错误。",
                category = "体验设计",
                author = "产品技术联合组",
                publishedAt = "08:00 · 演示内容",
                paragraphs = listOf(
                    "首次进入新闻页时还没有内容，加载提示可以占据主体区域。已经有数据时再刷新，则应保留可阅读的文章，只在顶部显示刷新进度。",
                    "加载状态和文章快照一起由仓库发布，避免多个相互独立的布尔变量出现矛盾组合。失败不会清空已有文章，重试会再次发起加载。",
                    "示例使用本地数据和短暂延迟，不访问真实新闻服务。收藏保存在内存中，进程结束后会丢失；真实产品应使用数据库保存本地状态，并让网络数据先写入数据库。",
                ),
            ),
        )
    }
}
