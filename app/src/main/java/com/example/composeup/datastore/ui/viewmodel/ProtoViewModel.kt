package com.example.composeup.datastore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.composeup.datastore.data.model.UserProfile
import com.example.composeup.datastore.data.repository.ProtoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProtoViewModel(private val repository: ProtoRepository) : ViewModel() {

    val uiState: StateFlow<UserProfile> = repository.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    fun updateLevel(newLevel: String) {
        viewModelScope.launch { repository.updateLevel(newLevel) }
    }

    fun addTag(tag: String) {
        viewModelScope.launch { repository.addTag(tag) }
    }

    fun resetProfile() {
        viewModelScope.launch { repository.resetProfile() }
    }
}
