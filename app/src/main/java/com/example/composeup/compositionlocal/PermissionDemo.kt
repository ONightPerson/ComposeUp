package com.example.composeup.compositionlocal

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 2. 全局权限请求控制器示例 */

interface PermissionHandler {
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit)
}

val LocalPermissionHandler = compositionLocalOf<PermissionHandler> {
    error("No PermissionHandler provided")
}

@Composable
fun PermissionDemo(modifier: Modifier = Modifier) {
    var resultText by remember { mutableStateOf("等待权限申请...") }
    var onPermissionResult: ((Boolean) -> Unit)? by remember { mutableStateOf(null) }

    // 统一的 Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult?.invoke(isGranted)
    }

    val handler = remember {
        object : PermissionHandler {
            override fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
                onPermissionResult = onResult
                launcher.launch(permission)
            }
        }
    }

    CompositionLocalProvider(LocalPermissionHandler provides handler) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Note("通过 CompositionLocal 提供统一的权限请求接口，深层 UI 不需要关心 ActivityResultLauncher 的位置。")
            
            Stage {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = resultText)
                    DeepNestedPermissionButton(
                        onStarted = { resultText = "申请中..." },
                        onFinished = { resultText = if (it) "✅ 权限已授予" else "❌ 权限被拒绝" }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeepNestedPermissionButton(onStarted: () -> Unit, onFinished: (Boolean) -> Unit) {
    val permissionHandler = LocalPermissionHandler.current
    
    ButtonRow {
        DemoButton(text = "请求相机权限") {
            onStarted()
            permissionHandler.requestPermission(Manifest.permission.CAMERA) { isGranted ->
                onFinished(isGranted)
            }
        }
    }
}
