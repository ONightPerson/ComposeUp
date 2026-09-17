package com.example.composeup.compositionlocal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 1. 动态多语言切换示例 */

data class AppStrings(
    val title: String,
    val description: String,
    val switchLang: String,
    val currentLang: String
)

private val EnStrings = AppStrings(
    title = "CompositionLocal Demo",
    description = "Change language without restarting Activity.",
    switchLang = "Switch Language",
    currentLang = "Current: English"
)

private val ZhStrings = AppStrings(
    title = "CompositionLocal 演示",
    description = "不重启 Activity 即可平滑切换语言。",
    switchLang = "切换语言",
    currentLang = "当前：简体中文"
)

// 创建 staticCompositionLocalOf，因为语言通常不频繁变化
val LocalAppStrings = staticCompositionLocalOf { EnStrings }

@Composable
fun LocalizationDemo(modifier: Modifier = Modifier) {
    var isChinese by remember { mutableStateOf(false) }
    val strings = if (isChinese) ZhStrings else EnStrings

    // 提供 CompositionLocal
    CompositionLocalProvider(LocalAppStrings provides strings) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Note("本例展示如何通过 CompositionLocal 实现应用内即时翻译，避开 stringResource 依赖 Context 配置的局限性。")
            
            Stage {
                DeepNestedLocalizationUI()
            }

            ButtonRow {
                DemoButton(text = LocalAppStrings.current.switchLang) {
                    isChinese = !isChinese
                }
            }
        }
    }
}

@Composable
private fun DeepNestedLocalizationUI() {
    // 自动从当前 Context 环境中获取翻译
    val strings = LocalAppStrings.current
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = strings.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = strings.description, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = strings.currentLang,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
