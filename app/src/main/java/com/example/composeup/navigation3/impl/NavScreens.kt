package com.example.composeup.navigation3.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.composeup.navigation3.api.*

@Composable
fun HomeScreen(
    backStack: MutableList<androidx.navigation3.runtime.NavKey>,
    resultStore: ResultStore
) {
    val result = resultStore.resultMap[InputResult::class.java.name] as? InputResult

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🧭 Navigation 3 主控制中心",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (result != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "🎉 收到来自设置页的跨屏返回数据：", fontWeight = FontWeight.Bold)
                    Text(text = "内容: ${result.content}", modifier = Modifier.padding(top = 4.dp))
                    Text(text = "时间戳: ${result.timestamp}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Button(
            onClick = { backStack.add(DetailKey(userId = "9527")) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Text("进入用户详情页 (ID: 9527)")
        }

        Button(
            onClick = { backStack.add(SettingsKey) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Text("定制独立垂直过渡动画的设置页")
        }

        Button(
            onClick = { backStack.add(DialogKey) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Text("弹出内置原生的 Dialog 对话框")
        }
    }
}

@Composable
fun DetailScreen(
    key: DetailKey,
    backStack: MutableList<androidx.navigation3.runtime.NavKey>
) {
    val vm: DetailViewModel = viewModel()

    LaunchedEffect(key.userId) {
        vm.loadUserDetail(key.userId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "👤 用户核心详情信息",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = vm.detailText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp),
            color = MaterialTheme.colorScheme.secondary
        )

        Button(
            onClick = { backStack.removeLastOrNull() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("返回上一页")
        }
    }
}

@Composable
fun SettingsScreen(
    backStack: MutableList<androidx.navigation3.runtime.NavKey>,
    resultStore: ResultStore
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚙️ 独立垂直上升动画设置页",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("在此输入要回传到主页的昵称数据") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                if (textInput.isNotBlank()) {
                    resultStore.setResult(InputResult(content = textInput, timestamp = System.currentTimeMillis()))
                }
                backStack.removeLastOrNull()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("设置成功并携带数据返回")
        }
    }
}

@Composable
fun DialogContent(
    backStack: MutableList<androidx.navigation3.runtime.NavKey>
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🔔 提示", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = "这是一个由 Navigation 3 内置 DialogStrategy 渲染出来的原生对话框场景示例！", modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center)
            Button(onClick = { backStack.removeLastOrNull() }) {
                Text("关闭对话框")
            }
        }
    }
}
