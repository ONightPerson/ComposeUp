package com.example.composeup.remember

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.composeup.flow.ButtonRow
import com.example.composeup.flow.CodeBlock
import com.example.composeup.flow.DemoButton
import com.example.composeup.flow.Note
import com.example.composeup.flow.SectionTitle
import com.example.composeup.flow.Stage
import kotlinx.serialization.Serializable

/**
 * 示例③：rememberSerializable —— 基于 Serialization 的持久化。
 *
 * 相比于 `rememberSaveable` 需要手动编写 `Saver` (如 listSaver/mapSaver)，
 * `rememberSerializable` 允许我们直接记忆被 `@Serializable` 注解的类。
 *
 * 核心优势：
 * 1. **零样板代码**：无需手动定义如何保存和恢复每个字段。
 * 2. **类型安全**：直接利用 Kotlinx Serialization 的能力处理复杂嵌套结构。
 * 3. **跨配置变更存活**：与 rememberSaveable 一样，支持旋屏和进程重启。
 */
@Composable
fun RememberSerializableDemo(modifier: Modifier = Modifier) {
    // 演示：直接记忆 Serializable 对象，无需 Saver
    var user by rememberSerializable {
        mutableStateOf(User("路人甲", 18))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note("User 类使用了 @Serializable 注解，可以直接被 rememberSerializable 记忆。")

        Stage {
            Column {
                Text("用户信息（Serializable）：", style = MaterialTheme.typography.titleMedium)
                Text("姓名：${user.name}")
                Text("年龄：${user.age}")
            }
        }

        ButtonRow {
            DemoButton("随机修改") {
                user = User(
                    name = listOf("张三", "李四", "王五").random(),
                    age = (1..100).random()
                )
            }
        }

        SectionTitle("rememberSerializable 用法")
        Note("只需给类加上 @Serializable 注解，即可通过 rememberSerializable 自动持久化。")
        CodeBlock("""
            @Serializable
            data class User(val name: String, val age: Int)

            // 使用时（会自动推导 Serializer）：
            var user by rememberSerializable { 
                mutableStateOf(User("路人甲", 18)) 
            }
        """.trimIndent())
        
        SectionTitle("与 rememberSaveable 的关系")
        Note("rememberSerializable 是对 rememberSaveable 的高级封装，专门为 kotlinx.serialization 优化。")
    }
}

/** 演示用的数据类，标记为 Serializable */
@Serializable
private data class User(val name: String, val age: Int)
