package com.example.composeup.sideeffects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** 图片加载结果：Loading / Success / Error 三态。 */
private sealed interface ImageResult {
    data object Loading : ImageResult
    data class Success(val image: String) : ImageResult
    data object Error : ImageResult
}

/** 模拟图片仓库：load 是 suspend；含 "bad" 的 url 返回 null（失败）。 */
private class ImageRepository {
    suspend fun load(url: String): String? {
        delay(1200.milliseconds) // 假装网络耗时
        return if (url.contains("bad")) null else "🖼️ 已加载：$url"
    }
}

/**
 * 示例⑥：`produceState` —— 把「非 Compose 状态」转成 Compose 的 `State`。
 *
 * `produceState` 启动一个**绑定组合生命周期**的协程，可以把外部数据（Flow、LiveData、RxJava、
 * 回调、suspend 调用）的结果不断写进它返回的 `State`（通过 `value =`）。
 * 进入组合时启动、离开组合时取消；key 变化时取消旧 producer、用新 key 重启。返回的 State 是 **conflated** 的
 * （写入相同值不会触发重组）。
 *
 * 官方例子：从网络加载图片。
 * ```
 * @Composable
 * fun loadNetworkImage(url: String, repo: ImageRepository): State<Result<Image>> {
 *     return produceState(initialValue = Result.Loading, url, repo) {   // url/repo 是 key
 *         val image = repo.load(url)          // 协程里可调用 suspend
 *         value = if (image == null) Result.Error else Result.Success(image)
 *     }
 * }
 * ```
 * 关键点（官方 Key Point）：`produceState` 底层就是 `remember { mutableStateOf(initial) }` + `LaunchedEffect`，
 * 每次给 `value` 赋值就更新那个 state。**你完全可以基于现有 API 组合出自己的 effect。**
 *
 * 对「非挂起」的数据源（回调/监听器），用 producer 作用域里的 `awaitDispose { }` 在离开时注销订阅。
 */
@Composable
fun ProduceStateDemo(modifier: Modifier = Modifier) {
    val repo = remember { ImageRepository() }
    var url by rememberSaveable { mutableStateOf("photo_1") }

    // url 变化 → producer 取消并重启（initialValue 回到 Loading）。
    val imageResult by loadNetworkImage(url, repo)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "切换 url 会重启 producer：先回到 Loading，1.2 秒后出结果。含 “bad” 的 url 会失败（Error）。" +
                "这段加载逻辑封装在一个返回 State 的 composable 里，调用方只需 `by loadNetworkImage(...)`。",
        )
        OptionChips(
            options = listOf(
                "photo_1" to "photo_1",
                "photo_2" to "photo_2",
                "bad_url" to "bad_url（失败）",
            ),
            selected = url,
            onSelect = { url = it },
        )

        Stage(height = 160.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (val r = imageResult) {
                    ImageResult.Loading -> {
                        CircularProgressIndicator()
                        Text("Loading…（initialValue）", style = MaterialTheme.typography.bodySmall)
                    }
                    is ImageResult.Success -> Text(
                        text = r.image,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ImageResult.Error -> Text(
                        text = "❌ 加载失败（Error）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Readout("当前 url = $url · 状态 = ${imageResult::class.simpleName}")

        SectionTitle("官方写法")
        CodeBlock(
            """
            @Composable
            fun loadNetworkImage(url: String, repo: ImageRepository): State<ImageResult> =
                produceState(initialValue = ImageResult.Loading, url, repo) {  // key: url, repo
                    val image = repo.load(url)             // producer 是 suspend 作用域
                    value = if (image == null) ImageResult.Error
                            else ImageResult.Success(image)
                }

            // 非挂起数据源（回调/监听器）：用 awaitDispose 在离开时注销
            val ticks by produceState(0) {
                val listener = object : OnTick { override fun onTick(v: Int) { value = v } }
                Ticker.register(listener)
                awaitDispose { Ticker.unregister(listener) }   // 清理订阅
            }
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• produceState 把「外部世界」桥接进 Compose：进入启动、离开取消、key 变重启，返回 conflated 的 State。\n" +
                "• producer 作用域（ProduceStateScope）里可调用 suspend，用 `value =` 写结果；`awaitDispose { }` 做非挂起源的清理。\n" +
                "• 有返回值的 composable 按普通 Kotlin 函数命名（小写开头），如 loadNetworkImage / rememberXxx。\n" +
                "• 底层就是 remember + LaunchedEffect + mutableStateOf——理解这点后你能自造 effect。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** 复刻官方 loadNetworkImage：返回一个 State<ImageResult>，供其它 composable 消费。 */
@Composable
private fun loadNetworkImage(url: String, repo: ImageRepository): State<ImageResult> {
    return produceState<ImageResult>(initialValue = ImageResult.Loading, url, repo) {
        val image = repo.load(url)
        value = if (image == null) ImageResult.Error else ImageResult.Success(image)
    }
}
