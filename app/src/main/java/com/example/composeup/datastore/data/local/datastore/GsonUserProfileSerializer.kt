package com.example.composeup.datastore.data.local.datastore

import androidx.datastore.core.Serializer
import com.example.composeup.datastore.data.model.UserProfile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class GsonUserProfileSerializer(private val gson: Gson) : Serializer<UserProfile> {
    override val defaultValue: UserProfile = UserProfile()

    override suspend fun readFrom(input: InputStream): UserProfile {
        return try {
            gson.fromJson(input.readBytes().decodeToString(), UserProfile::class.java)
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserProfile, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(gson.toJson(t).encodeToByteArray())
        }
    }
}
