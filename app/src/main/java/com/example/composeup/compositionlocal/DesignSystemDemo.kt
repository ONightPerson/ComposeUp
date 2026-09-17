package com.example.composeup.compositionlocal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 3. 自定义设计系统扩展示例 */

data class AppSpacing(
    val small: Dp = 4.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 24.dp
)

val LocalSpacing = staticCompositionLocalOf { AppSpacing() }

/** 专家级写法：通过扩展属性简化访问 */
object AppTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}

@Composable
fun DesignSystemDemo(modifier: Modifier = Modifier) {
    // 可以在这里根据不同屏幕尺寸提供不同的 Spacing 实现
    val customSpacing = AppSpacing(medium = 16.dp, large = 32.dp)

    CompositionLocalProvider(LocalSpacing provides customSpacing) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Note("本例展示如何扩展 MaterialTheme，提供自定义的间距、阴影等设计维度。")
            
            Stage(height = 200.dp) {
                ContentWithCustomSpacing()
            }
        }
    }
}

@Composable
private fun ContentWithCustomSpacing() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("上方文字")
        
        // 使用简化后的访问方式
        Spacer(Modifier.height(AppTheme.spacing.large))
        
        Box(
            Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
        )
        
        Spacer(Modifier.height(AppTheme.spacing.medium))
        
        Text("下方文字")
    }
}
