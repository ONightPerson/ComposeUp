package com.example.composeup.datastore.data.local.datastore

import androidx.datastore.core.Serializer
import com.example.composeup.datastore.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore的Serializer负责 对象 <-> 文件流之间的转换
 * kotlinx的Serializer负责   对象 <-> JSON字符串的转换
 * 外层：UserProfileSerializer : androidx.datastore.core.Serializer<UserProfile>
 * 内层：UserProfile.serializer() : kotlinx.serialization.KSerializer<UserProfile>
 * 它们名字相似，但一个管文件流，一个管 JSON 编解码。
 * androidx.datastore.core.Serializer 是 DataStore 框架要求的存储适配器。
 * kotlinx.serialization.KSerializer 是 Kotlin 序列化框架要求的格式编解码器。
 * DataStore<UserProfile>
 *     │
 *     │ 需要
 *     ▼
 * androidx.datastore.core.Serializer<UserProfile>   // UserProfileSerializer
 *     │
 *     │ 内部调用
 *     ▼
 * kotlinx.serialization.KSerializer<UserProfile>    // UserProfile.serializer()
 *     │
 *     │ 配合
 *     ▼
 * kotlinx.serialization.json.Json                   // 具体 JSON 格式
 */
object UserProfileSerializer : Serializer<UserProfile> {
    override val defaultValue: UserProfile = UserProfile()

    override suspend fun readFrom(input: InputStream): UserProfile {
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return defaultValue
            Json.decodeFromString<UserProfile>(bytes.decodeToString())
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserProfile, output: OutputStream) {
        val string = Json.encodeToString(UserProfile.serializer(), t)
        withContext(Dispatchers.IO) {
            output.write(string.encodeToByteArray())
        }
    }
}
