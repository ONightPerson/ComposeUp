package com.example.composeup.navigation3

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.example.composeup.navigation3.api.DetailKey
import com.example.composeup.navigation3.api.HomeKey
import com.example.composeup.navigation3.impl.CustomListDetailStrategy
import com.example.composeup.navigation3.impl.appNav3EntryProvider
import com.example.composeup.navigation3.impl.rememberResultStore

/**
 * Navigation 3 演示控制中心主 Activity。
 */
class Nav3Activity : ComponentActivity() {

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 深层链接 (Deep Links) 解析与合成虚拟历史栈 (Synthetic BackStack) 还原
        val initialKeys = parseDeepLink(intent?.data)

        setContent {
            val resultStore = rememberResultStore()

            // 2. 保存和管理导航状态：使用官方规定的 rememberNavBackStack vararg 传参
            val backStack = rememberNavBackStack(*initialKeys.toTypedArray())

            val isWideScreen = false
            val listDetailStrategy = remember(isWideScreen) { CustomListDetailStrategy<NavKey>(isWideScreen) }

            Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                // 3. 全局主导航容器 NavDisplay 展示回退栈
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.padding(paddingValues),
                    onBack = { backStack.removeLastOrNull() },
                    // 配置自定义分栏布局场景策略
                    sceneStrategies = listOf(listDetailStrategy),
                    // 配置系统状态保护及生命周期作用域隔离装饰器
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    // 提供所有的路由节点，并做安全类型强转适配 NavDisplay 签名
                    entryProvider = appNav3EntryProvider(backStack, resultStore) as (NavKey) -> androidx.navigation3.runtime.NavEntry<NavKey>,
                    // 5. 不同 destinations 之间切换使用全景水平动画
                    transitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(400)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(400)
                        )
                    },
                    popTransitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(400)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(400)
                        )
                    },
                    predictivePopTransitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(400)
                        ) togetherWith slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(400)
                        )
                    }
                )
            }
        }
    }

    /**
     * 将解析到的 Uri 还原为合法的导航基础序列。
     * 例如匹配：app://www.composeup.com/detail/{userId}
     */
    private fun parseDeepLink(uri: Uri?): List<NavKey> {
        if (uri == null) return listOf(HomeKey)

        val pathSegments = uri.pathSegments
        if (pathSegments.size == 2 && pathSegments[0] == "detail") {
            val userId = pathSegments[1]
            // 返回合成回退栈：底栈为主页，顶栈为详情页。这样用户点击返回键会自动回退到主页！
            return listOf(HomeKey, DetailKey(userId = userId))
        }

        return listOf(HomeKey)
    }
}
