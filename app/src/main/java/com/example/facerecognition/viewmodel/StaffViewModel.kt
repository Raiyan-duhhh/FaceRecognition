package com.example.facerecognition.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.entity.AttendanceLog
import com.example.facerecognition.data.entity.Staff
import com.example.facerecognition.data.repository.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class StaffViewModel(application: Application) : AndroidViewModel(application) {

    private val firestoreRepository = FirestoreRepository()

    sealed interface LoginState {
        data object Idle : LoginState
        data object Loading : LoginState
        data class Success(val staff: Staff) : LoginState
        data class Error(val message: String) : LoginState
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    data class StaffStats(
        val totalDaysPresent: Int = 0,
        val lateCheckIns: Int = 0,
        val history: List<AttendanceLog> = emptyList()
    )

    private val _staffStats = MutableStateFlow(StaffStats())
    val staffStats: StateFlow<StaffStats> = _staffStats

    suspend fun findMatchingStaff(liveEmbedding: FloatArray): Staff? = withContext(Dispatchers.IO) {
        val approvedStaff = firestoreRepository.getAllApprovedStaff()
        if (approvedStaff.isEmpty()) return@withContext null

        var bestDistance = Float.MAX_VALUE
        var bestMatch: Staff? = null

        for (staff in approvedStaff) {
            for (cloudEmbedding in staff.embeddings) {
                val savedFloatArray = cloudEmbedding.vector.toFloatArray()
                val dist = calculateEuclideanDistance(liveEmbedding, savedFloatArray)

                if (dist < bestDistance) {
                    bestDistance = dist
                    bestMatch = staff
                }
            }
        }

        if (bestDistance < 0.8f) bestMatch else null
    }

    private fun calculateEuclideanDistance(enc1: FloatArray, enc2: FloatArray): Float {
        var sum = 0f
        for (i in enc1.indices) {
            val diff = enc1[i] - enc2[i]
            sum += diff * diff
        }
        return sqrt(sum.toDouble()).toFloat()
    }

    // Bug 3 Fix: Evaluate time-based status instead of hardcoding "Present"
    fun setLoginSuccess(staff: Staff) {
        _loginState.value = LoginState.Success(staff)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTime = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                val status = when {
                    currentTime.isBefore(java.time.LocalTime.of(9, 35))  -> "P_FN"
                    currentTime.isBefore(java.time.LocalTime.of(10, 30)) -> "LP_FN"
                    currentTime.isBefore(java.time.LocalTime.of(13, 40)) -> "HD"
                    currentTime.isBefore(java.time.LocalTime.of(16, 10)) -> "EP_AN"
                    else -> "P_AN"
                }

                val log = AttendanceLog(
                    staffId = staff.id,
                    staffName = staff.name,
                    timestamp = System.currentTimeMillis(),
                    status = status
                )
                firestoreRepository.markAttendance(log)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        loadStaffStats(staff.id)
    }

    fun setLoginError(message: String) {
        _loginState.value = LoginState.Error(message)
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
        _staffStats.value = StaffStats()
    }

    private fun loadStaffStats(staffId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val calendar = java.util.Calendar.getInstance()
                val currentYear = calendar.get(java.util.Calendar.YEAR)
                val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1

                val allLogs = firestoreRepository.getAttendanceForMonth(currentYear, currentMonth)
                val myLogs = allLogs.filter { it.staffId == staffId }.sortedByDescending { it.timestamp }

                val uniqueDays = myLogs.map { log ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp }
                    "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
                }.toSet().size

                var lateCount = 0
                for (log in myLogs) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp }
                    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val minute = cal.get(java.util.Calendar.MINUTE)
                    if (hour > 9 || (hour == 9 && minute > 30)) {
                        lateCount++
                    }
                }

                _staffStats.value = StaffStats(
                    totalDaysPresent = uniqueDays,
                    lateCheckIns = lateCount,
                    history = myLogs
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}