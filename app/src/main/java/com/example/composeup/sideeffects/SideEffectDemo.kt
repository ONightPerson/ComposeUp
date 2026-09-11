package com.example.composeup.sideeffects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 模拟一个用户模型：userType 会变化，需要同步给分析库。 */
private data class User(val userType: String)

/**
 * 示例⑤：`SideEffect` —— 把 Compose 状态发布给「非 Compose 管理」的代码。
 *
 * 当你需要把 Compose 里的状态同步给一个**外部对象**（分析 SDK、埋点库、第三方管理器等）时用 `SideEffect`。
 * 它保证在**每次成功重组之后**执行——这一点很关键：直接把副作用写在 composable 函数体里是**错误**的，
 * 因为重组可能被丢弃、顺序不可预测，副作用可能在「重组其实没成功」时就跑了。
 *
 * 官方例子：把当前用户类型同步给 FirebaseAnalytics，好让后续埋点都带上这个属性。
 * ```
 * @Composable
 * fun rememberFirebaseAnalytics(user: User): FirebaseAnalytics {
 *     val analytics = remember { FirebaseAnalytics() }
 *     SideEffect {                                   // 每次成功重组后更新
 *         analytics.setUserProperty("userType", user.userType)
 *     }
 *     return analytics
 * }
 * ```
 *
 * 本示例用共享的 [FakeAnalytics] 扮演这个「非 Compose 对象」：切换用户类型 → 重组 → SideEffect 把
 * 最新 userType 写进 FakeAnalytics，界面下方实时展示它的 userProperties。
 */
@Composable
fun SideEffectDemo(modifier: Modifier = Modifier) {
    var userType by rememberSaveable { mutableStateOf("免费用户") }
    val user = remember(userType) { User(userType) }

    // 复刻官方 rememberFirebaseAnalytics：把 user.userType 发布给外部分析对象。
    val analytics = rememberFakeAnalytics(user)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "切换用户类型会触发重组；SideEffect 在【重组成功之后】把最新 userType 同步给 FakeAnalytics。" +
                "下方展示的是这个「非 Compose 对象」当前持有的属性。",
        )
        OptionChips(
            options = listOf("免费用户" to "免费", "付费用户" to "付费", "试用用户" to "试用"),
            selected = userType,
            onSelect = { userType = it },
        )

        SectionTitle("FakeAnalytics（非 Compose 对象）当前状态")
        Readout("当前 user = $user")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (analytics.userProperties.isEmpty()) {
                Text("（暂无属性）", style = MaterialTheme.typography.bodySmall)
            }
            analytics.userProperties.forEach { (k, v) ->
                Text("· $k = $v", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionTitle("官方写法")
        CodeBlock(
            """
            @Composable
            fun rememberFirebaseAnalytics(user: User): FirebaseAnalytics {
                val analytics = remember { FirebaseAnalytics() }
                SideEffect {   // 保证在每次成功重组后执行
                    analytics.setUserProperty("userType", user.userType)
                }
                return analytics
            }
            """.trimIndent(),
        )

        SectionTitle("SideEffect vs 直接写在函数体里")
        Text(
            text = "• 直接写：`analytics.setUserProperty(...)` 放在 composable 体内 → 重组可能被丢弃/跳过，副作用时机不可控（错误）。\n" +
                "• SideEffect：只在【成功重组后】执行，时机确定，是把 Compose 状态发布给外部对象的正确方式。\n" +
                "• SideEffect 无 key、无清理；每次成功重组都跑一次，所以里面的操作要足够轻量。\n" +
                "• 若需要「按 key 变化才跑」或「需要清理」，应改用 LaunchedEffect / DisposableEffect。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** 复刻官方 rememberFirebaseAnalytics：用 SideEffect 把 user 的类型发布给外部分析对象。 */
@Composable
private fun rememberFakeAnalytics(user: User): FakeAnalytics {
    // FakeAnalytics 是「不受 Compose 管理」的外部单例，这里 remember 只是强调它是被记住的对象。
    val analytics = remember { FakeAnalytics }
    // 每次成功重组后，把最新 userType 同步给它，保证后续埋点都带上该属性。
    SideEffect {
        analytics.setUserProperty("userType", user.userType)
    }
    return analytics
}
