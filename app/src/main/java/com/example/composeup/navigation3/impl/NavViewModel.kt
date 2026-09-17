package com.example.composeup.navigation3.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * 作用域绑定到 [androidx.navigation3.runtime.NavEntry] 的 ViewModel。
 * 当与之对应的 Key 被从回退栈中弹出（Pop）清除时，该 ViewModel 会自动触发 onCleared，释放内存与数据。
 */
class DetailViewModel : ViewModel() {
    // 页面内部的状态流/状态值
    var detailText: String by mutableStateOf("正在加载中...")
        private set

    fun loadUserDetail(userId: String) {
        // 模拟数据加载
        detailText = "成功加载用户 ID 为 $userId 的核心详情信息。本 ViewModel 完全独立作用于此 Entry 节点！"
    }

    override fun onCleared() {
        // 验证生命周期释放
        println("Nav3Demo: DetailViewModel for this NavEntry has been cleared successfully!")
    }
}
