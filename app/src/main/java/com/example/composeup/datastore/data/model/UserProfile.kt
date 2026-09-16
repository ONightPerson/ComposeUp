package com.example.composeup.datastore.data.model

import kotlinx.serialization.Serializable
import com.squareup.moshi.JsonClass

/** 强类型用户档案模型 (Proto DataStore) */
@Serializable
@JsonClass(generateAdapter = true)
data class UserProfile(
    val lastLoginIp: String = "192.168.1.1",
    val level: String = "REGULAR",
    val tags: List<String> = listOf("Android", "Compose"),
)
