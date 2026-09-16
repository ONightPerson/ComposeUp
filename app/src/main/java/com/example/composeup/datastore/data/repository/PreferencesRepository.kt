package com.example.composeup.datastore.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.composeup.datastore.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ENABLE_NOTIFICATION = booleanPreferencesKey("enable_notification")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                username = prefs[Keys.USERNAME] ?: "开发者",
                isDarkMode = prefs[Keys.DARK_MODE] ?: false,
                enableNotification = prefs[Keys.ENABLE_NOTIFICATION] ?: true
            )
        }

    suspend fun updateUsername(name: String) {
        context.userPrefsDataStore.edit { it[Keys.USERNAME] = name }
    }

    suspend fun toggleDarkMode(current: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.DARK_MODE] = !current }
    }

    suspend fun toggleNotification(current: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.ENABLE_NOTIFICATION] = !current }
    }
}
