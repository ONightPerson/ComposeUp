package com.example.composeup.datastore.data.local.datastore

import androidx.datastore.core.Serializer
import com.example.composeup.datastore.data.model.UserProfile
import com.squareup.moshi.Moshi
import java.io.InputStream
import java.io.OutputStream

class MoshiUserProfileSerializer(private val moshi: Moshi) : Serializer<UserProfile> {
    private val adapter = moshi.adapter(UserProfile::class.java)

    override val defaultValue: UserProfile = UserProfile()

    override suspend fun readFrom(input: InputStream): UserProfile {
        return try {
            adapter.fromJson(input.readBytes().decodeToString()) ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserProfile, output: OutputStream) {
        output.write(adapter.toJson(t).encodeToByteArray())
    }
}
