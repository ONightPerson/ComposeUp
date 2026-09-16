package com.example.composeup.datastore.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.example.composeup.datastore.data.local.datastore.GsonUserProfileSerializer
import com.example.composeup.datastore.data.local.datastore.MoshiUserProfileSerializer
import com.example.composeup.datastore.data.local.datastore.ProtoSettingsSerializer
import com.example.composeup.datastore.data.local.datastore.UserProfileSerializer
import com.example.composeup.datastore.data.model.UserProfile
import com.example.composeup.datastore.data.model.UserSettingsProto
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class ProtoRepository(private val context: Context) {

    // 1. Kotlinx Serialization (Current)
    private val kotlinxDataStore: DataStore<UserProfile> = DataStoreFactory.create(
        serializer = UserProfileSerializer,
        produceFile = { context.filesDir.resolve("user_profile_kotlinx.json") }
    )

    // 2. Gson Implementation
    private val gsonDataStore: DataStore<UserProfile> = DataStoreFactory.create(
        serializer = GsonUserProfileSerializer(Gson()),
        produceFile = { context.filesDir.resolve("user_profile_gson.json") }
    )

    // 3. Moshi Implementation
    private val moshiDataStore: DataStore<UserProfile> = DataStoreFactory.create(
        serializer = MoshiUserProfileSerializer(
            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        ),
        produceFile = { context.filesDir.resolve("user_profile_moshi.json") }
    )

    // 4. True Protobuf Implementation
    private val trueProtoDataStore: DataStore<UserSettingsProto> = DataStoreFactory.create(
        serializer = ProtoSettingsSerializer,
        produceFile = { context.filesDir.resolve("user_settings.pb") }
    )

    // Exposed Flows
    val kotlinxProfileFlow: Flow<UserProfile> = kotlinxDataStore.data
    val gsonProfileFlow: Flow<UserProfile> = gsonDataStore.data
    val moshiProfileFlow: Flow<UserProfile> = moshiDataStore.data
    val trueProtoFlow: Flow<UserSettingsProto> = trueProtoDataStore.data

    // Update Methods for Kotlinx
    suspend fun updateKotlinxLevel(newLevel: String) {
        kotlinxDataStore.updateData { it.copy(level = newLevel) }
    }

    // Update Methods for Gson
    suspend fun updateGsonLevel(newLevel: String) {
        gsonDataStore.updateData { it.copy(level = newLevel) }
    }

    // Update Methods for Moshi
    suspend fun updateMoshiLevel(newLevel: String) {
        moshiDataStore.updateData { it.copy(level = newLevel) }
    }

    // Update Methods for True Proto
    suspend fun updateProtoUsername(name: String) {
        trueProtoDataStore.updateData { current ->
            current.toBuilder().setUsername(name).build()
        }
    }
}
