package com.example.composeup.keywords

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
 * 本包收录的「Compose 底层关键字」示例。
 *
 * 这几个关键字都不是 Compose 专有的，而是 Kotlin 语言特性——但正是它们撑起了
 * Compose 那套「看起来像声明、写起来像自然语言」的 DSL。按「解决什么问题」分为四组。
 */
private enum class KeywordTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    Operator(
        title = "① operator：让自定义类型用上 + - * [] < 等符号",
        summary = "运算符重载。Dp 的 16.dp + 8.dp、2 * size、EnterTransition 的 fadeIn() + slideIn() 全靠它。" +
            "本示例用真实 Dp 与一个自定义 Spacing 类型，演示 plus/times/compareTo/get/contains 等约定如何驱动 UI。",
        keywords = "operator fun plus · times · compareTo · get · contains · infix then",
    ),
    Infix(
        title = "② infix：把 a.foo(b) 写成 a foo b",
        summary = "中缀调用语法糖。Modifier 的 a then b、CompositionLocal 的 LocalX provides v、" +
            "动画的 fadeIn() togetherWith fadeOut() 全靠它。本示例演示三条硬性规则、自定义 infix、成员 vs 扩展 infix，并实时驱动 UI。",
        keywords = "infix fun then · provides · togetherWith · using · 中缀调用",
    ),
    Invoke(
        title = "③ invoke：让对象像函数一样被调用",
        summary = "operator fun invoke 约定。Compose 里每一个 content: @Composable () -> Unit 插槽被 content() 执行、" +
            "工厂对象被 Obj() 调用，走的都是它。本示例演示函数类型调用、自定义 invoke 重载、以及可调用的 Composable 对象。",
        keywords = "operator fun invoke · content() · @Composable 插槽 · 可调用对象",
    ),
    Inline(
        title = "④ inline：内联函数，Compose 的零开销抽象",
        summary = "为什么 Compose 满屏 lambda 却不卡？因为大量高阶函数是 inline 的：消除 lambda 对象分配、" +
            "支持 reified 泛型、允许非局部返回。Dp 运算、CompositionLocal.current、内部节点查找都是 inline。",
        keywords = "inline fun · reified · 非局部返回 · inline val · 零分配",
    ),
    LambdaModifiers(
        title = "⑤ crossinline / noinline：给 inline 的 lambda 参数加约束",
        summary = "inline 函数的两个 lambda 修饰符。crossinline 允许把 lambda 转发进嵌套上下文但禁止非局部返回；" +
            "noinline 保留 lambda 为真实对象，好把它存起来 / 延迟调用 / 传给别的函数。",
        keywords = "crossinline · noinline · 非局部返回 · 延迟执行 · 存储 lambda",
    ),
}

/**
 * 「Compose 底层关键字」示例的入口页。
 *
 * 外层是话题列表，点进去看具体示例；示例内部用系统返回键或左上角箭头回到列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordsHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<KeywordTopic?>(null) }
    val topics = remember { KeywordTopic.entries.toList() }

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
                            text = "Compose 底层 · 关键字",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "operator / infix / invoke / inline / crossinline / noinline 都是 Kotlin 语言特性，" +
                                "却是 Compose 优雅 API 的「隐形骨架」：\n" +
                                "· operator 让 Dp、EnterTransition 能用符号运算；\n" +
                                "· infix 让 a then b、LocalX provides v 像读句子；\n" +
                                "· invoke 让每个 content() 插槽和工厂对象可被直接调用；\n" +
                                "· inline 让满屏 lambda 零分配，并解锁 reified 与非局部返回；\n" +
                                "· crossinline / noinline 精调 inline 里 lambda 的行为。\n" +
                                "下面 5 个示例逐个拆解，点击任意一项进入。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    KeywordRow(
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
            KeywordTopic.Operator -> OperatorDemo(contentModifier)
            KeywordTopic.Infix -> InfixDemo(contentModifier)
            KeywordTopic.Invoke -> InvokeDemo(contentModifier)
            KeywordTopic.Inline -> InlineDemo(contentModifier)
            KeywordTopic.LambdaModifiers -> LambdaModifiersDemo(contentModifier)
        }
    }
}
