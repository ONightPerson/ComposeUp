package com.example.composeup.datastore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.composeup.datastore.data.model.UserPreferences
import com.example.composeup.datastore.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(private val repository: PreferencesRepository) : ViewModel() {

    val uiState: StateFlow<UserPreferences> = repository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    fun updateUsername(name: String) {
        viewModelScope.launch { repository.updateUsername(name) }
    }

    fun toggleDarkMode() {
        viewModelScope.launch { repository.toggleDarkMode(uiState.value.isDarkMode) }
    }

    fun toggleNotification() {
        viewModelScope.launch { repository.toggleNotification(uiState.value.enableNotification) }
    }
}
