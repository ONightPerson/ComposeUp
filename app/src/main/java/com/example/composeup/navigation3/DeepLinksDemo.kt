package com.example.composeup.navigation3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.BackStackMatchResult
import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.withBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.serializer

/**
 * ⑥ 支持 deeplinks：匹配与自定义 matchers。
 *
 * 1.2.0 起，nav3 用 `DeepLinkRequest`（一次传入的链接）+ `DeepLinkMatcher`（把请求映射成 key）
 * 支持深链。三步：**定义 matcher → 从 Intent/URI 造 request → 用所有 matcher 匹配取最优 → 生成返回栈**。
 *
 * 库内置三种 matcher：
 *  - [UriDeepLinkMatcher]：按 URI 模式匹配并抽取 `{id}` 这类参数（靠 kotlinx.serialization 反序列化进 key）；
 *  - StaticKeyDeepLinkMatcher：匹配固定链接 / 特定 action（无参数）；
 *  - BackStackMatcher：用 `withBackStack { }` 把匹配结果包装成一条**合成返回栈**（例如 [Home, Detail]，
 *    这样从深链进来后按返回键会先回到首页，而不是直接退出）。
 *
 * 内置 matcher 不够用时，可继承 [DeepLinkMatcher] 写自定义 matcher——本示例的 [TelDeepLinkMatcher]
 * 就处理了 `tel:` 这类 opaque URI（UriDeepLinkMatcher 不支持）。多个 matcher 命中同一请求时，
 * 靠 `MatchResult` 的 `Comparable` 排序，`maxOrNull()` 取最优。
 *
 * 真实项目里匹配发生在 Activity 的 onCreate/onNewIntent（见本包 [Nav3Activity]）；
 * 这里做成可交互输入框，方便你随时试不同 URI。
 */
@Composable
internal fun DeepLinksDemo(modifier: Modifier = Modifier, onExit: () -> Unit) {
    val backStack = rememberNavBackStack(HomeKey)
    val log = rememberDemoLog()

    var uri by rememberSaveable { mutableStateOf("https://www.example.com/users/42") }

    // 一组 matcher（真实项目里各功能模块分别贡献，用 DI 多绑定汇总）。
    val matchers: List<DeepLinkMatcher<*, *>> = remember {
        listOf(
            // 固定 URI → 首页
            UriDeepLinkMatcher(DeepLinkUri("www.example.com/home"), serializer<HomeKey>())
                .withBackStack { listOf(HomeKey) },
            // 带 {id} 参数 → 合成返回栈 [Home, UserProfile(id)]
            UriDeepLinkMatcher(DeepLinkUri("www.example.com/users/{id}"), serializer<UserProfileKey>())
                .withBackStack { matchResult -> listOf(HomeKey, matchResult.key) },
            // 自定义 matcher：处理 tel: opaque URI
            TelDeepLinkMatcher(),
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenArea(height = 220.dp) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onExit() },
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        Pane("Home 主页", color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("深链匹配后会在这里合成返回栈。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    entry<UserProfileKey> { key ->
                        Pane("UserProfile 用户资料", color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("从 URI 抽取的 id = ${key.id}", style = MaterialTheme.typography.bodyMedium)
                            ButtonRow { DemoOutlinedButton("返回") { if (backStack.size > 1) backStack.removeLastOrNull() } }
                        }
                    }
                    entry<DialerKey> { key ->
                        Pane("Dialer 拨号", color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("自定义 matcher 解析出的号码 = ${key.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
                            ButtonRow { DemoOutlinedButton("返回") { if (backStack.size > 1) backStack.removeLastOrNull() } }
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
            SectionTitle("试一试：输入一个 URI")
            OutlinedTextField(
                value = uri,
                onValueChange = { uri = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("Deep link URI") },
                singleLine = true,
            )
            ButtonRow {
                DemoOutlinedButton("home") { uri = "https://www.example.com/home" }
                DemoOutlinedButton("users/42") { uri = "https://www.example.com/users/42" }
                DemoOutlinedButton("tel:") { uri = "tel:5550100" }
            }
            ButtonRow {
                DemoButton("匹配并导航") {
                    val request = DeepLinkRequest(uri = uri)
                    // 用所有 matcher 匹配，取排名最高的结果
                    val matchResult = matchers
                        .mapNotNull { it.match(request) }
                        .maxOrNull()
                    // 从匹配结果生成返回栈（找不到就回退到默认首页）
                    @Suppress("UNCHECKED_CAST")
                    val newStack: List<NavKey> = when (matchResult) {
                        null -> listOf(HomeKey)
                        is BackStackMatchResult<*, *> -> matchResult.backStack as List<NavKey>
                        else -> listOf(matchResult.key as NavKey)
                    }
                    backStack.clear()
                    backStack.addAll(newStack)
                    log(
                        if (matchResult == null) {
                            "❌ 无匹配：$uri → 默认 [HomeKey]"
                        } else {
                            "✅ 命中 ${matchResult::class.simpleName}：$uri → ${newStack.joinToString()}"
                        },
                    )
                }
            }

            SectionTitle("自定义 matcher")
            CodeBlock(
                """
                // 内置：URI 模式 + 合成返回栈
                UriDeepLinkMatcher(
                    DeepLinkUri("www.example.com/users/{id}"),
                    serializer<UserProfileKey>(),
                ).withBackStack { listOf(HomeKey, it.key) }

                // 内置：固定链接 / 特定 action（无参数）
                StaticKeyDeepLinkMatcher(
                    HomeKey, listOf(DeepLinkMatcher.actionFilter(Intent.ACTION_VIEW)),
                )

                // 自定义：处理 tel: 这类 opaque URI
                class TelDeepLinkMatcher :
                    DeepLinkMatcher<DialerKey, DeepLinkMatcher.MatchResult<DialerKey>>() {
                    override fun matchRequest(request: DeepLinkRequest): MatchResult<DialerKey>? {
                        val uri = request.uri ?: return null
                        if (uri.scheme != "tel") return null
                        val phone = uri.schemeSpecificPart ?: return null
                        return MatchResult(DialerKey(phone))
                    }
                }

                // 汇总匹配，取最优（MatchResult 实现了 Comparable）
                val result = matchers.mapNotNull { it.match(request) }.maxOrNull()
                """.trimIndent(),
            )
            SectionTitle("匹配日志（最新在上）")
            LogBox(lines = log)
        }
    }
}

/**
 * 自定义 DeepLinkMatcher：匹配 `tel:` 这类 opaque URI。
 *
 * 因为 tel URI 没有层级结构（不是 //host/path），UriDeepLinkMatcher 处理不了，
 * 所以直接继承 DeepLinkMatcher，实现 matchRequest 即可。第二个类型参数用默认的
 * `MatchResult<DialerKey>`（不需要自定义排名时）。
 */
private class TelDeepLinkMatcher :
    DeepLinkMatcher<DialerKey, DeepLinkMatcher.MatchResult<DialerKey>>() {

    override fun matchRequest(request: DeepLinkRequest): MatchResult<DialerKey>? {
        val uri = request.uri ?: return null
        if (uri.scheme != "tel") return null
        // schemeSpecificPart 仅在 Android 上可用：tel:5550100 → "5550100"
        val phoneNumber = uri.schemeSpecificPart ?: return null
        return MatchResult(DialerKey(phoneNumber = phoneNumber))
    }
}
