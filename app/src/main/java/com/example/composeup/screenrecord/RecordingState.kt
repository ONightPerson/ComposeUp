package com.example.composeup.screenrecord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RecordingState {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }
}
