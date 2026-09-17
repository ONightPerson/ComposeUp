package com.example.composeup.navigation3.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * 跨页面/跨流的结果存储分发中心。
 * 利用 Compose State 和 rememberSaveable，实现屏幕旋转及进程重启级别的数据保持。
 */
public class ResultStore {
    // 采用 String -> Any? 的结构，为了能在 rememberSaveable 中安全复原，存储的值应为基本类型或可序列化字符串
    public val resultMap: MutableMap<String, Any?> = mutableStateMapOf()

    /**
     * 获取指定类型的返回结果
     */
    @Suppress("UNCHECKED_CAST")
    public inline fun <reified T> getResult(key: String = T::class.java.name): T? {
        return resultMap[key] as? T
    }

    /**
     * 设置返回结果，触发订阅了该状态的页面进行重组
     */
    public inline fun <reified T> setResult(result: T, key: String = T::class.java.name) {
        resultMap[key] = result
    }

    /**
     * 消费或清除某个结果，避免二次消费
     */
    public inline fun <reified T> removeResult(key: String = T::class.java.name) {
        resultMap.remove(key)
    }
}

/**
 * 提供全局的 CompositionLocal 组合本地项容器
 */
public val LocalResultStore: ProvidableCompositionLocal<ResultStore> = compositionLocalOf {
    error("No ResultStore provided! Please wrap with CompositionLocalProvider.")
}

/**
 * 创建并记住一个支持状态持久化的 ResultStore
 */
@Composable
public fun rememberResultStore(): ResultStore {
    return rememberSaveable(saver = ResultStoreSaver()) {
        ResultStore()
    }
}

/**
 * 自定义 Saver 保证在配置变更或低内存被杀后能安全恢复内部的 resultMap
 */
private fun ResultStoreSaver(): Saver<ResultStore, Map<String, Any?>> = Saver(
    save = { it.resultMap.toMap() },
    restore = { restoredMap ->
        ResultStore().apply {
            resultMap.putAll(restoredMap)
        }
    }
)
