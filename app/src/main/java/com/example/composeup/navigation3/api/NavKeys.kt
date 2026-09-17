package com.example.composeup.navigation3.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 导航模块对外公开的 API 键接口。
 * 所有用于 Navigation 3 导航回退栈（Back Stack）的键都必须实现 [NavKey]，并且带有 [@Serializable] 注解。
 */
interface AppNavKey : NavKey

/** 主页键 */
@Serializable
data object HomeKey : AppNavKey

/** 详情页键（带参数传递演示） */
@Serializable
data class DetailKey(val userId: String) : AppNavKey

/** 设置页键（用来演示 Metadata 独立过渡动画及返回结果） */
@Serializable
data object SettingsKey : AppNavKey

/** 对话框弹窗键（演示内建场景 Dialog 渲染） */
@Serializable
data object DialogKey : AppNavKey

/**
 * 页面间返回数据的实体模型。
 */
@Serializable
data class InputResult(
    val content: String,
    val timestamp: Long
)