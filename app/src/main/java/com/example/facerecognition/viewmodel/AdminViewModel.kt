package com.example.facerecognition.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.entity.Staff
import com.example.facerecognition.data.entity.FaceVector
import com.example.facerecognition.data.repository.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val firestoreRepository = FirestoreRepository()



    data class DashboardStats(
        val totalStaff: Int = 0,
        val todayCheckIns: Int = 0
    )

    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats

    // Bug 6 Fix: Full staff directory for ManageStaffScreen (not just pending)
    private val _allStaffDirectory = MutableStateFlow<List<Staff>>(emptyList())
    val allStaffDirectory: StateFlow<List<Staff>> = _allStaffDirectory

    // ── Pending Staff (manual refresh) ────────────────────────────────
    private val _pendingStaff = MutableStateFlow<List<Staff>>(emptyList())
    val pendingStaff: StateFlow<List<Staff>> = _pendingStaff

    // ── Active Staff (approved, manual refresh) ──────────────────────
    private val _activeStaff = MutableStateFlow<List<Staff>>(emptyList())
    val activeStaff: StateFlow<List<Staff>> = _activeStaff

    // ── Attendance Rules State ────────────────────────────────────────
    private val _latePunchTime = MutableStateFlow("09:35 AM")
    val latePunchTime: StateFlow<String> = _latePunchTime

    private val _halfDayTime = MutableStateFlow("01:40 PM")
    val halfDayTime: StateFlow<String> = _halfDayTime

    sealed interface RulesSaveStatus {
        data object Idle : RulesSaveStatus
        data object Saving : RulesSaveStatus
        data object Success : RulesSaveStatus
        data class Error(val message: String) : RulesSaveStatus
    }

    private val _rulesSaveStatus = MutableStateFlow<RulesSaveStatus>(RulesSaveStatus.Idle)
    val rulesSaveStatus: StateFlow<RulesSaveStatus> = _rulesSaveStatus

    sealed interface RegistrationState {
        data object Idle : RegistrationState
        data object Saving : RegistrationState
        data object Success : RegistrationState
        data class Error(val message: String) : RegistrationState
    }

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState



    fun deleteStaffMember(staffId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepository.deleteStaff(staffId)
        }
    }

    fun updateStaffProfile(staffId: String, name: String, employeeId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestoreRepository.updateStaffProfile(staffId, name, employeeId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Delete a staff member and refresh both lists */
    fun deleteStaff(staffId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestoreRepository.deleteStaff(staffId)
                refreshStaffLists()
                loadDashboardStats()
            } catch (e: Exception) {
                Log.e("AdminVM", "Failed to delete staff", e)
            }
        }
    }

    /** Update staff name/employeeId and refresh both lists */
    fun updateStaffDetails(staffId: String, newName: String, newEmployeeId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestoreRepository.updateStaffProfile(staffId, newName, newEmployeeId)
                refreshStaffLists()
                loadDashboardStats()
            } catch (e: Exception) {
                Log.e("AdminVM", "Failed to update staff details", e)
            }
        }
    }
    fun loadDashboardStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch all approved staff and update the directory + count
                val staffList = firestoreRepository.getAllApprovedStaff()
                _allStaffDirectory.value = staffList

                // Count today's check-ins from this month's attendance logs
                val calendar = java.util.Calendar.getInstance()
                val year = calendar.get(java.util.Calendar.YEAR)
                val month = calendar.get(java.util.Calendar.MONTH) + 1
                val todayDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)

                val allLogs = firestoreRepository.getAttendanceForMonth(year, month)
                val todayCheckIns = allLogs.count { log ->
                    val logCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = log.timestamp
                    }
                    logCal.get(java.util.Calendar.DAY_OF_YEAR) == todayDayOfYear &&
                        logCal.get(java.util.Calendar.YEAR) == year
                }

                _dashboardStats.value = DashboardStats(
                    totalStaff = staffList.size,
                    todayCheckIns = todayCheckIns
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun verifyEligibilityBeforeCapture(employeeId: String): Boolean {
        if (employeeId.isBlank()) {
            _registrationState.value = RegistrationState.Error("College ID is required to register.")
            return false
        }

        _registrationState.value = RegistrationState.Saving
        return try {
            val existing = firestoreRepository.findStaffByEmployeeId(employeeId.trim())
            if (existing != null) {
                if (existing.isApproved) {
                    _registrationState.value = RegistrationState.Error(
                        "You are already an approved staff member. No need to register!"
                    )
                } else {
                    _registrationState.value = RegistrationState.Error(
                        "Your registration is already pending. Please wait for Admin approval."
                    )
                }
                false
            } else {
                _registrationState.value = RegistrationState.Idle
                true
            }
        } catch (e: Exception) {
            _registrationState.value = RegistrationState.Error("Verification failed: ${e.message}")
            false
        }
    }

    fun registerStaff(name: String, employeeId: String, embeddings: List<FloatArray>) {
        if (name.isBlank()) {
            _registrationState.value = RegistrationState.Error("Name cannot be empty")
            return
        }
        if (employeeId.isBlank()) {
            _registrationState.value = RegistrationState.Error("College ID is required to register")
            return
        }
        if (embeddings.isEmpty()) {
            _registrationState.value = RegistrationState.Error("No face encodings captured")
            return
        }

        _registrationState.value = RegistrationState.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolvedEmpId = employeeId.trim()

                // ── Proceed with registration ────────────────────────────
                val firestoreEmbeddings = embeddings.map { floatArray -> 
                    FaceVector(vector = floatArray.toList()) 
                }

                android.util.Log.d("FaceDebug", "Registering staff '$name' with ${firestoreEmbeddings.size} embeddings, " +
                        "vector sizes: ${firestoreEmbeddings.map { it.vector.size }}")

                val staff = Staff(
                    name = name.trim(),
                    employeeId = resolvedEmpId,
                    embeddings = firestoreEmbeddings
                )

                firestoreRepository.registerStaff(staff)
                _registrationState.value = RegistrationState.Success
            } catch (e: Exception) {
                _registrationState.value = RegistrationState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun resetRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    val pendingApprovals: StateFlow<List<Staff>> = firestoreRepository.getPendingApprovals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun approveStaff(staffId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepository.approveStaff(staffId)
            refreshStaffLists()
            loadDashboardStats()
        }
    }

    fun rejectStaff(staffId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepository.rejectStaff(staffId)
            refreshStaffLists()
            loadDashboardStats()
        }
    }

    /** Refresh both pending and active staff lists from Firestore in one call */
    fun refreshStaffLists() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allStaff = firestoreRepository.getAllStaff()
                _pendingStaff.value = allStaff.filter { !it.isApproved }
                _activeStaff.value = allStaff.filter { it.isApproved }
                _allStaffDirectory.value = allStaff.filter { it.isApproved }
            } catch (e: Exception) {
                Log.e("AdminVM", "Failed to refresh staff lists", e)
            }
        }
    }

    // ── Attendance Rules CRUD ──────────────────────────────────────────

    /** Load saved rules from Firestore settings/attendance_rules */
    fun loadAttendanceRules() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rules = firestoreRepository.getAttendanceRules()
                if (rules != null) {
                    _latePunchTime.value = rules["latePunchTime"] as? String ?: "09:35 AM"
                    _halfDayTime.value = rules["halfDayTime"] as? String ?: "01:40 PM"
                }
            } catch (e: Exception) {
                Log.e("AdminVM", "Failed to load rules", e)
            }
        }
    }

    /** Persist updated attendance rules to Firestore settings/attendance_rules */
    fun updateAttendanceRules(latePunchTime: String, halfDayTime: String) {
        _rulesSaveStatus.value = RulesSaveStatus.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rules = mapOf(
                    "latePunchTime" to latePunchTime,
                    "halfDayTime" to halfDayTime,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestoreRepository.updateAttendanceRules(rules)
                _latePunchTime.value = latePunchTime
                _halfDayTime.value = halfDayTime
                _rulesSaveStatus.value = RulesSaveStatus.Success
            } catch (e: Exception) {
                _rulesSaveStatus.value = RulesSaveStatus.Error(e.message ?: "Save failed")
                Log.e("AdminVM", "Failed to save rules", e)
            }
        }
    }

    fun resetRulesSaveStatus() {
        _rulesSaveStatus.value = RulesSaveStatus.Idle
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    suspend fun generateMonthlyReport(year: Int, month: Int): String = withContext(Dispatchers.IO) {
        val yearMonth = java.time.YearMonth.of(year, month)
        val daysInMonth = yearMonth.lengthOfMonth()
        val zoneId = java.time.ZoneId.of("Asia/Kolkata")

        val staffList = firestoreRepository.getAllApprovedStaff()
        val allLogs = firestoreRepository.getAttendanceForMonth(year, month)
        
        val sb = java.lang.StringBuilder()
        sb.append("Staff Name,Employee ID,")
        for (day in 1..daysInMonth) {
            sb.append("${day},")
        }
        sb.append("Total P,Total A,Total LP,Total EP,Total HD,Total MC,Total H,Final Presence Score\n")

        fun evaluateDailyStatus(dayLogs: List<com.example.facerecognition.data.entity.AttendanceLog>): String {
            if (dayLogs.any { it.status == "MC" }) return "MC"

            val validWindows = dayLogs.mapNotNull { log ->
                val time = java.time.Instant.ofEpochMilli(log.timestamp)
                    .atZone(zoneId)
                    .toLocalTime()

                when {
                    time.isBefore(java.time.LocalTime.of(9, 0)) -> null
                    time.isBefore(java.time.LocalTime.of(9, 35)) -> "P_FN"
                    time.isBefore(java.time.LocalTime.of(10, 30)) -> "LP_FN"
                    time.isBefore(java.time.LocalTime.of(12, 40)) -> null
                    time.isBefore(java.time.LocalTime.of(13, 40)) -> "HD"
                    time.isBefore(java.time.LocalTime.of(15, 10)) -> null
                    time.isBefore(java.time.LocalTime.of(16, 10)) -> "EP_AN"
                    else -> "P_AN"
                }
            }

            if (validWindows.isEmpty()) return "A"

            val first = validWindows.first()
            val last = validWindows.last()

            if (first == last && validWindows.distinct().size == 1) {
                if (first == "HD") return "HD"
                return "A"
            }

            val hasFN = first == "P_FN" || first == "LP_FN"
            val hasAN = last == "P_AN" || last == "EP_AN"

            if (hasFN && hasAN) {
                val isLate = first == "LP_FN"
                val isEarly = last == "EP_AN"
                return when {
                    isLate && isEarly -> "LP/EP"
                    isLate -> "LP"
                    isEarly -> "EP"
                    else -> "P"
                }
            }

            if (first == "HD" || last == "HD") {
                return "HD"
            }

            return "A"
        }

        for (staff in staffList) {
            val staffLogs = allLogs.filter { it.staffId == staff.id }

            sb.append("${staff.name},${staff.employeeId},")

            var totalP = 0
            var totalA = 0
            var totalLP = 0
            var totalEP = 0
            var totalLPEP = 0
            var totalHD = 0
            var totalMC = 0
            var totalH = 0

            for (day in 1..daysInMonth) {
                val currentDate = java.time.LocalDate.of(year, month, day)
                val dayOfWeek = currentDate.dayOfWeek

                val startOfDay = currentDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = currentDate.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()

                val logsForDay = staffLogs.filter { it.timestamp in startOfDay..endOfDay }

                if (logsForDay.isNotEmpty()) {
                    val logStr = evaluateDailyStatus(logsForDay)
                    sb.append("${logStr},")

                    when (logStr) {
                        "P" -> totalP++
                        "LP" -> totalLP++
                        "EP" -> totalEP++
                        "LP/EP" -> {
                            totalLP++
                            totalEP++
                            totalLPEP++
                        }
                        "HD" -> totalHD++
                        "MC" -> totalMC++
                        "A" -> totalA++
                    }
                } else {
                    if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                        sb.append("H,")
                        totalH++
                    } else {
                        sb.append("A,")
                        totalA++
                    }
                }
            }

            val finalScore = (totalP + totalLP + totalEP - totalLPEP + totalMC) + (totalHD * 0.5)
            sb.append("${totalP},${totalA},${totalLP},${totalEP},${totalHD},${totalMC},${totalH},${finalScore}\n")
        }

        return@withContext sb.toString()
    }
}