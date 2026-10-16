package com.example.composeup.navigation3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.BackStackMatchResult
import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.withBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.composeup.ui.theme.ComposeUpTheme
import kotlinx.serialization.serializer

/**
 * Navigation 3 的**真实 deep link 入口 Activity**。
 *
 * 「⑥ Deeplinks」示例里我们把匹配做成了可交互输入框方便随时试；而真实的深链匹配发生在
 * Activity 的 onCreate/onNewIntent：系统按 AndroidManifest 里的 `<intent-filter>` 把 Intent 投递过来，
 * 我们用一组 [DeepLinkMatcher] 匹配、取最优、生成初始返回栈，再交给 [NavDisplay]。
 *
 * 用 adb 触发（对应 manifest 里 scheme=app / host=www.composeup.com）：
 * ```
 * adb shell am start -a android.intent.action.VIEW -d "app://www.composeup.com/users/42"
 * adb shell am start -a android.intent.action.VIEW -d "app://www.composeup.com/detail/7"
 * ```
 * 从 users/42 进来会合成返回栈 [Home, UserProfile(42)]，按返回键先回到 Home。
 */
class Nav3Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 各 matcher（真实项目里由各功能模块贡献，用 DI 多绑定汇总）。
        val matchers: List<DeepLinkMatcher<*, *>> = listOf(
            UriDeepLinkMatcher(DeepLinkUri("app://www.composeup.com/home"), serializer<HomeKey>()),
            UriDeepLinkMatcher(DeepLinkUri("app://www.composeup.com/detail/{id}"), serializer<DetailKey>())
                .withBackStack { matchResult -> listOf(HomeKey, matchResult.key) },
            UriDeepLinkMatcher(DeepLinkUri("app://www.composeup.com/users/{id}"), serializer<UserProfileKey>())
                .withBackStack { matchResult -> listOf(HomeKey, matchResult.key) },
        )

        // 2. 从进来的 Intent 造 DeepLinkRequest（这里只取 data URI）。
        val uri = intent?.data
        val request = uri?.let { DeepLinkRequest(uri = it) }

        // 3. 用所有 matcher 匹配，取排名最高的结果，生成初始返回栈。
        val matchResult = request?.let { req -> matchers.mapNotNull { it.match(req) }.maxOrNull() }
        @Suppress("UNCHECKED_CAST")
        val initialKeys: List<NavKey> = when (matchResult) {
            null -> listOf(HomeKey)
            is BackStackMatchResult<*, *> -> matchResult.backStack as List<NavKey>
            else -> listOf(matchResult.key as NavKey)
        }

        setContent {
            ComposeUpTheme {
                // 4. 用合成/匹配出的 key 初始化返回栈（rememberNavBackStack 支持跨进程死亡存活）。
                val backStack = rememberNavBackStack(*initialKeys.toTypedArray())
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = entryProvider {
                        entry<HomeKey> {
                            Pane("Home（深链入口）", color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("从深链进来时，这里通常是合成返回栈的栈底。", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        entry<DetailKey> { key ->
                            Pane("Detail #${key.id}", color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text("由 app://www.composeup.com/detail/{id} 匹配得到", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        entry<UserProfileKey> { key ->
                            Pane("UserProfile #${key.id}", color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("由 app://www.composeup.com/users/{id} 匹配得到", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                )
            }
        }
    }
}
