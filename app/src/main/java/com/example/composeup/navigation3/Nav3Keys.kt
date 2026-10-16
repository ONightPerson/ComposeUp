package com.example.composeup.navigation3

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 本模块共享的导航 key 定义。
 *
 * Navigation 3 的返回栈装的是 **key（内容的引用）**，不是内容本身。要用
 * `rememberNavBackStack` 让栈在配置变更 / 进程死亡后存活，key 必须：
 *  1. 实现标记接口 [NavKey]；
 *  2. 标注 `@Serializable`（库据此把整个栈序列化进 SavedState）。
 *
 * 这些 key 被多个示例与 [Nav3Activity] 复用，故集中定义在这里。
 */

/** 主页。 */
@Serializable
data object HomeKey : NavKey

/** 详情页，带一个 id 参数——演示「同一个目的地类的不同实例」。 */
@Serializable
data class DetailKey(val id: String) : NavKey

/** 设置页。 */
@Serializable
data object SettingsKey : NavKey

/** 用户资料页，用于 deeplink 从 URI 抽取 {id} 参数。 */
@Serializable
data class UserProfileKey(val id: String) : NavKey

/** 拨号页，用于自定义 matcher 处理 tel: 这类 opaque URI。 */
@Serializable
data class DialerKey(val phoneNumber: String) : NavKey
