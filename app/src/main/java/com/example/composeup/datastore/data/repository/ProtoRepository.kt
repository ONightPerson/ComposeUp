package com.example.composeup.datastore.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.example.composeup.datastore.data.local.datastore.UserProfileSerializer
import com.example.composeup.datastore.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

class ProtoRepository(context: Context) {
    private val userProfileDataStore: DataStore<UserProfile> = DataStoreFactory.create(
        serializer = UserProfileSerializer,
        produceFile = { context.filesDir.resolve("user_profile.json") }
    )

    val userProfileFlow: Flow<UserProfile> = userProfileDataStore.data

    suspend fun updateLevel(newLevel: String) {
        userProfileDataStore.updateData { it.copy(level = newLevel) }
    }

    suspend fun addTag(tag: String) {
        userProfileDataStore.updateData { 
            if (it.tags.contains(tag)) it else it.copy(tags = it.tags + tag)
        }
    }

    suspend fun resetProfile() {
        userProfileDataStore.updateData { UserProfile() }
    }
}
