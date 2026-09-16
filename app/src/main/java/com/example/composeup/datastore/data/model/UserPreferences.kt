package com.example.composeup.datastore.data.model

/** 用户基础配置模型 (Preferences DataStore) */
data class UserPreferences(
    val username: String = "开发者",
    val isDarkMode: Boolean = false,
    val enableNotification: Boolean = true,
)
