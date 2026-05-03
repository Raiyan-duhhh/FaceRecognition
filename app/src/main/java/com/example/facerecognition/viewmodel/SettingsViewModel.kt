package com.example.facerecognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.repository.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val repository = FirestoreRepository()

    private val _latePunchTime = MutableStateFlow("09:15 AM")
    val latePunchTime: StateFlow<String> = _latePunchTime

    private val _halfDayTime = MutableStateFlow("01:00 PM")
    val halfDayTime: StateFlow<String> = _halfDayTime

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _showSuccess = MutableStateFlow(false)
    val showSuccess: StateFlow<Boolean> = _showSuccess

    init {
        fetchSettings()
    }

    private fun fetchSettings() {
        viewModelScope.launch {
            repository.getAttendanceRules()?.let { data ->
                _latePunchTime.value = data["late_punch_time"] as? String ?: "09:15 AM"
                _halfDayTime.value = data["half_day_time"] as? String ?: "01:00 PM"
            }
        }
    }

    fun updateLatePunch(time: String) { _latePunchTime.value = time }
    fun updateHalfDay(time: String) { _halfDayTime.value = time }

    fun saveSettings() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val rules = mapOf(
                    "late_punch_time" to _latePunchTime.value,
                    "half_day_time" to _halfDayTime.value
                )
                repository.updateAttendanceRules(rules)
                _showSuccess.value = true
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun resetSuccess() { _showSuccess.value = false }
}
