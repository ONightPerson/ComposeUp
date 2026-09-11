package com.example.composeup.keywords

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 示例⑤：crossinline / noinline —— 给 inline 函数的 lambda 参数「加约束」。
 *
 * 它们只出现在 inline 函数的 lambda 形参前，用来微调「这个 lambda 到底怎么被处理」：
 *
 *  - **普通 inline lambda**：就地展开，**允许非局部返回**；但因此不能被存起来 / 异步 / 转发。
 *  - **crossinline**：仍然就地展开，但 lambda 是在**另一个嵌套上下文**里被调用的
 *    （比如被包进另一个 lambda / 对象里）。此时必须**禁止非局部返回**，否则会破坏那层嵌套的语义。
 *  - **noinline**：**不展开**，把 lambda 保留成一个**真实的函数对象**，
 *    于是可以把它存进字段/集合、传给别的函数、稍后或异步再调用；代价是失去 inline 的性能收益。
 *
 * 一句话选型：
 *  > 只是就地执行、想用非局部返回 → 普通 inline；
 *  > 要把 lambda 转发进另一层 lambda 里执行 → crossinline；
 *  > 要把 lambda 存起来 / 延迟 / 异步调用 → noinline。
 *
 *  * crossinline 演示：body 不是在 runWrapped 的函数体里「直接」调用的，
 *  * 而是被包进一个 Runnable（另一层 lambda）后再 run —— 这就是「嵌套上下文」。
 *  *
 *  * 如果 body 不加 crossinline：编译器无法保证安全（非局部返回会跳出 Runnable 语义），直接报错。
 *  * 加了 crossinline：允许内联进那层 Runnable，但**禁止**在 body 里写 `return`（返回外层函数）。
 */
private inline fun runWrapped(crossinline body: () -> Unit) {
    val runnable = Runnable { body() } // body 在这层 Runnable lambda 内被调用
    runnable.run()
}

/**
 * noinline 演示：enqueue 里 log 是普通 inline lambda（就地展开、零分配），
 * 而 task 需要被存进队列、稍后才执行，所以必须 noinline 保留成真实函数对象。
 * 「同一函数里部分 lambda inline、部分 noinline」正是 noinline 的典型用法。
 */
private inline fun enqueue(
    queue: MutableList<() -> String>,
    log: (String) -> Unit,        // 普通 inline lambda：就地展开
    noinline task: () -> String,  // 需要存储 → 必须 noinline
) {
    queue += task // ← 存储 lambda：若 task 不加 noinline，这行编译不过
    log("已入队，当前队列长度 = ${queue.size}")
}

/** 稍后再逐个执行队列里的 noinline lambda（延迟执行）。 */
private fun drainQueue(queue: MutableList<() -> String>): List<String> {
    val results = queue.map { it() } // 此刻才真正 invoke 之前存下的 lambda
    queue.clear()
    return results
}

private fun crossInlineLog(): List<String> {
    val out = mutableListOf<String>()
    runWrapped {
        out += "  body 在 Runnable 内部被执行（嵌套上下文）"
        // 注意：这里写 `return` 返回 crossInlineLog 是**不允许**的 —— crossinline 禁止非局部返回
    }
    out += "runWrapped 已返回"
    return out
}

@Composable
fun LambdaModifiersDemo(modifier: Modifier = Modifier) {
    val queue = remember { mutableListOf<() -> String>() }
    var seq by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf("（点上面按钮试试：先入队，再执行）")) }
    val crossLines = remember { crossInlineLog() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("① 三种 lambda 处理方式对比")
        LogBox(
            lines = listOf(
                "普通 inline lambda ：就地展开 · 可非局部 return · 不能存储/异步",
                "crossinline        ：展开进「嵌套上下文」· 禁止非局部 return · 不能存储",
                "noinline           ：不展开，保留真实对象 · 可存储/传递/异步 · 失去 inline 收益",
            ),
        )

        SectionTitle("② crossinline：把 lambda 转发进另一层上下文")
        CodeBlock(
            """
            inline fun runWrapped(crossinline body: () -> Unit) {
                val runnable = Runnable { body() }   // body 在 Runnable 这层 lambda 内被调用
                runnable.run()
            }
            // 若 body 不加 crossinline：编译报错（无法内联，且不能允许非局部返回）
            // 加了 crossinline：可内联进 Runnable，但 body 里禁止写 return 跳出外层函数
            """.trimIndent(),
        )
        LogBox(lines = crossLines)

        SectionTitle("③ noinline：把 lambda 存起来，稍后再调用")
        Note(
            "enqueue 同时收两个 lambda：log 是普通 inline（就地展开），task 是 noinline（存进 MutableList，" +
                "稍后再逐个执行）。点「入队」只把 task 存起来（不执行），点「执行」才真正 invoke —— " +
                "这正是「存储 / 延迟调用」场景，普通 inline 做不到，而 noinline 常这样与 inline 参数并存。",
        )
        CodeBlock(
            """
            inline fun enqueue(
                queue: MutableList<() -> String>,
                log: (String) -> Unit,        // 普通 inline lambda：就地展开
                noinline task: () -> String,  // 需要存储 → 必须 noinline
            ) {
                queue += task                 // 没有 noinline 就编译不过
                log("已入队")                  // log 是 inline，就地展开
            }
            fun drainQueue(queue: MutableList<() -> String>) = queue.map { it() }  // 稍后执行
            """.trimIndent(),
        )
        ButtonRow {
            DemoButton(text = "入队一个 noinline 任务") {
                seq += 1
                val id = seq
                enqueue(
                    queue = queue,
                    log = { msg -> results = listOf("任务#$id：$msg", "（task 仅入队，尚未执行）") },
                    task = { "任务#$id 此刻才真正执行" },
                )
            }
            DemoButton(text = "执行并清空队列") {
                results = if (queue.isEmpty()) {
                    listOf("队列为空，先点「入队」")
                } else {
                    drainQueue(queue) + "（队列已清空）"
                }
            }
        }
        LogBox(lines = results)

        SectionTitle("④ Compose 里的取舍")
        Note(
            "· Compose 大量使用 inline 让 content/onClick 等 lambda 零分配（见示例③）；\n" +
                "· 但当一个 lambda 需要被「记住并稍后重组时再调用」（如某些工厂、延迟构建的内容），\n" +
                "  就不能简单 inline —— 要么 noinline 保留为对象，要么改变 API 形状；\n" +
                "· crossinline 常见于「把用户 lambda 包进另一层作用域再执行」的封装里（如带 receiver 的构建器）。\n" +
                "记住：inline 是性能与语法的利器，noinline/crossinline 是它在「存储 / 嵌套」场景下的安全阀。",
        )
    }
}
