package com.example.composeup.datastore.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.composeup.datastore.data.model.UserProfile
import com.example.composeup.datastore.data.model.UserSettingsProto
import com.example.composeup.datastore.data.repository.ProtoRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProtoViewModel(private val repository: ProtoRepository) : ViewModel() {

    // 1. Kotlinx Serialization State
    val kotlinxState: StateFlow<UserProfile> = repository.kotlinxProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    // 2. Gson State
    val gsonState: StateFlow<UserProfile> = repository.gsonProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    // 3. Moshi State
    val moshiState: StateFlow<UserProfile> = repository.moshiProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    // 4. True Proto State
    val trueProtoState: StateFlow<UserSettingsProto> = repository.trueProtoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettingsProto.getDefaultInstance())

    fun updateKotlinxLevel(level: String) {
        viewModelScope.launch { repository.updateKotlinxLevel(level) }
    }

    fun updateGsonLevel(level: String) {
        viewModelScope.launch { repository.updateGsonLevel(level) }
    }

    fun updateMoshiLevel(level: String) {
        viewModelScope.launch { repository.updateMoshiLevel(level) }
    }

    fun updateProtoUsername(name: String) {
        viewModelScope.launch { repository.updateProtoUsername(name) }
    }
}
