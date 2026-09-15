package com.example.composeup.remember

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Remember 全家桶话题：状态持久化与生存期管理。
 */
private enum class RememberTopic(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Basic("remember & keys", "最基础的状态记忆，及其依赖键（Keys）触发的计算重写", Icons.Default.Memory),
    Saveable("rememberSaveable", "跨越配置变更（如旋屏）与进程重启的状态持久化", Icons.Default.Save),
    Serializable("Serializable & Saver", "如何通过自定义 Saver 记忆复杂的 Data Class 或序列化对象", Icons.Default.Archive),
    Retain("ViewModel & Retain", "超越 UI 树层级的状态保留：ViewModel 与长生命周期管理", Icons.Default.Restore),
    Observers("Observers", "RememberObserver 与 RetainObserver：生命周期感知与资源释放", Icons.Default.Visibility),
}

@Composable
fun RememberHub(modifier: Modifier = Modifier) {
    var selectedTopic by rememberSaveable { mutableStateOf<RememberTopic?>(null) }

    val contentModifier = modifier.fillMaxSize()

    if (selectedTopic == null) {
        RememberTopicList(onSelect = { selectedTopic = it }, modifier = contentModifier)
    } else {
        BackHandler { selectedTopic = null }
        when (selectedTopic) {
            RememberTopic.Basic -> RememberDemo(contentModifier)
            RememberTopic.Saveable -> RememberSaveableDemo(contentModifier)
            RememberTopic.Serializable -> RememberSerializableDemo(contentModifier)
            RememberTopic.Retain -> RetainDemo(contentModifier)
            RememberTopic.Observers -> ObserverDemo(contentModifier)
            null -> {}
        }
    }
}

@Composable
private fun RememberTopicList(
    onSelect: (RememberTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topics = RememberTopic.entries.toList()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Remember & State Persistence",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "在 Compose 中管理状态的深度与广度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(topics) { topic ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onSelect(topic) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = topic.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = topic.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = topic.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
