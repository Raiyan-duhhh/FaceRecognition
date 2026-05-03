package com.example.facerecognition.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facerecognition.data.entity.Staff
import com.example.facerecognition.viewmodel.AdminViewModel
import kotlinx.coroutines.launch

// ── Color Tokens (matching existing dark theme) ──────────────────────────────
private val SurfaceDark = Color(0xFF09090B)
private val CardBg = Color.White.copy(alpha = 0.05f)
private val CardBorder = Color.White.copy(alpha = 0.1f)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFA1A1AA)
private val AccentGreen = Color(0xFF10B981)
private val AccentRed = Color(0xFFEF4444)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStaffScreen(viewModel: AdminViewModel, onBack: () -> Unit) {

    // ── State ────────────────────────────────────────────────────────────────
    val pendingStaff by viewModel.pendingStaff.collectAsState()
    val activeStaff by viewModel.activeStaff.collectAsState()
    val latePunchTime by viewModel.latePunchTime.collectAsState()
    val halfDayTime by viewModel.halfDayTime.collectAsState()
    val rulesSaveStatus by viewModel.rulesSaveStatus.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pending", "Active", "Settings")

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Load data when screen appears
    LaunchedEffect(Unit) {
        viewModel.refreshStaffLists()
        viewModel.loadAttendanceRules()
    }

    // Observe rule save status for Snackbar feedback
    LaunchedEffect(rulesSaveStatus) {
        when (rulesSaveStatus) {
            is AdminViewModel.RulesSaveStatus.Success -> {
                snackbarHostState.showSnackbar("Rules saved successfully ✓")
                viewModel.resetRulesSaveStatus()
            }
            is AdminViewModel.RulesSaveStatus.Error -> {
                snackbarHostState.showSnackbar(
                    "Failed: ${(rulesSaveStatus as AdminViewModel.RulesSaveStatus.Error).message}"
                )
                viewModel.resetRulesSaveStatus()
            }
            else -> {}
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF27272A),
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        containerColor = SurfaceDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Manage Staff & Settings",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Approvals, rules & configuration",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // ── Tab Row ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF18181B))
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                            animationSpec = tween(250),
                            label = "tab_bg"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) TextPrimary else TextSecondary,
                            animationSpec = tween(250),
                            label = "tab_text"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .clickable { selectedTab = index }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = textColor,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tab Content ──────────────────────────────────────────────────
            when (selectedTab) {
                0 -> PendingApprovalsSection(
                    pendingStaff = pendingStaff,
                    onApprove = { viewModel.approveStaff(it) },
                    onReject = { viewModel.rejectStaff(it) }
                )
                1 -> ActiveStaffSection(
                    activeStaff = activeStaff,
                    onDelete = { viewModel.deleteStaff(it) },
                    onEdit = { id, name, empId -> viewModel.updateStaffDetails(id, name, empId) }
                )
                2 -> AttendanceRulesSection(
                    latePunchTime = latePunchTime,
                    halfDayTime = halfDayTime,
                    isSaving = rulesSaveStatus is AdminViewModel.RulesSaveStatus.Saving,
                    onSaveRules = { late, half ->
                        viewModel.updateAttendanceRules(late, half)
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Section 1: Pending Approvals
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PendingApprovalsSection(
    pendingStaff: List<Staff>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (pendingStaff.isEmpty()) {
        // ── Empty State ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.1f))
                        .border(1.dp, AccentGreen.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "All Caught Up!",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No pending staff approvals at the moment.\nNew registrations will appear here.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    } else {
        // ── Pending List ─────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Badge count header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "Awaiting Review",
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentAmber.copy(alpha = 0.15f))
                            .border(1.dp, AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${pendingStaff.size}",
                            color = AccentAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(pendingStaff, key = { it.id }) { staff ->
                PendingStaffApprovalCard(
                    staff = staff,
                    onApprove = { onApprove(staff.id) },
                    onReject = { onReject(staff.id) }
                )
            }
        }
    }
}

@Composable
private fun PendingStaffApprovalCard(
    staff: Staff,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    AccentBlue.copy(alpha = 0.3f),
                                    AccentGreen.copy(alpha = 0.15f)
                                )
                            )
                        )
                        .border(1.dp, AccentBlue.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = staff.name.firstOrNull()?.uppercase() ?: "?",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = staff.name,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = staff.employeeId.ifEmpty { "No ID" },
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Reject
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.12f))
                        .border(1.dp, AccentRed.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onReject),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reject",
                        tint = AccentRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Approve
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.12f))
                        .border(1.dp, AccentGreen.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onApprove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Approve",
                        tint = AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Section 2: Attendance Rules
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AttendanceRulesSection(
    latePunchTime: String,
    halfDayTime: String,
    isSaving: Boolean,
    onSaveRules: (String, String) -> Unit
) {
    var editLatePunch by remember(latePunchTime) { mutableStateOf(latePunchTime) }
    var editHalfDay by remember(halfDayTime) { mutableStateOf(halfDayTime) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Section Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dynamic Attendance Rules",
                    color = TextPrimary.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Info Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue.copy(alpha = 0.08f))
                    .border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "These rules define how the system categorizes staff attendance. Changes are synced to all devices in real-time via Firestore.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Late Punch Time Field
        item {
            RuleInputCard(
                label = "Late Punch Threshold",
                description = "Staff arriving after this time are marked 'LP' (Late Punch)",
                icon = Icons.Default.Schedule,
                iconTint = AccentAmber,
                value = editLatePunch,
                onValueChange = { editLatePunch = it },
                placeholder = "e.g. 09:35 AM"
            )
        }

        // Half Day Time Field
        item {
            RuleInputCard(
                label = "Half Day Threshold",
                description = "Staff arriving after this time are marked 'HD' (Half Day)",
                icon = Icons.Default.Timelapse,
                iconTint = AccentRed,
                value = editHalfDay,
                onValueChange = { editHalfDay = it },
                placeholder = "e.g. 01:40 PM"
            )
        }

        // Save Button
        item {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        // THE FIX: Wrap the solid color in SolidColor() so both branches return a Brush!
                        if (isSaving) SolidColor(AccentGreen.copy(alpha = 0.3f))
                        else Brush.horizontalGradient(
                            listOf(AccentGreen, AccentGreen.copy(alpha = 0.7f))
                        )
                    )
                    .then(
                        if (!isSaving) Modifier.clickable {
                            onSaveRules(editLatePunch, editHalfDay)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Saving...",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Save Rules to Cloud",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleInputCard(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f))
                        .border(1.dp, iconTint.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = label,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18181B))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = TextSecondary.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Section 2: Active Staff Directory
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveStaffSection(
    activeStaff: List<Staff>,
    onDelete: (String) -> Unit,
    onEdit: (String, String, String) -> Unit
) {
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }
    var staffToEdit by remember { mutableStateOf<Staff?>(null) }

    staffToDelete?.let { staff ->
        DeleteConfirmDialog(
            staffName = staff.name,
            onConfirm = { onDelete(staff.id); staffToDelete = null },
            onDismiss = { staffToDelete = null }
        )
    }

    staffToEdit?.let { staff ->
        EditStaffDialog(
            currentName = staff.name,
            currentEmployeeId = staff.employeeId,
            onSave = { n, e -> onEdit(staff.id, n, e); staffToEdit = null },
            onDismiss = { staffToEdit = null }
        )
    }

    if (activeStaff.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.1f))
                        .border(1.dp, AccentBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Group, null, tint = AccentBlue, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text("No Active Staff", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Approved staff members will appear here.\nRegister and approve staff to get started.",
                    color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Staff Directory", color = TextPrimary.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(AccentGreen.copy(alpha = 0.15f))
                            .border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("${activeStaff.size}", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(activeStaff, key = { it.id }) { staff ->
                ActiveStaffCard(
                    staff = staff,
                    onEdit = { staffToEdit = staff },
                    onDelete = { staffToDelete = staff }
                )
            }
        }
    }
}

@Composable
private fun ActiveStaffCard(staff: Staff, onEdit: () -> Unit, onDelete: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(CardBg).border(1.dp, CardBorder, RoundedCornerShape(16.dp)).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentGreen.copy(alpha = 0.3f), AccentBlue.copy(alpha = 0.15f))))
                        .border(1.dp, AccentGreen.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(staff.name.firstOrNull()?.uppercase() ?: "?", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(staff.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(staff.employeeId.ifEmpty { "No ID" }, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.12f))
                        .border(1.dp, AccentBlue.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Edit, "Edit", tint = AccentBlue, modifier = Modifier.size(18.dp)) }
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.12f))
                        .border(1.dp, AccentRed.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Delete, "Delete", tint = AccentRed, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Dialogs
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DeleteConfirmDialog(staffName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(AccentRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.DeleteForever, null, tint = AccentRed, modifier = Modifier.size(28.dp)) }
        },
        title = { Text("Delete Staff?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Are you sure you want to permanently remove \"$staffName\"? This action cannot be undone.",
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(AccentRed)
                    .clickable(onClick = onConfirm).padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        },
        dismissButton = {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
        }
    )
}

@Composable
private fun EditStaffDialog(
    currentName: String, currentEmployeeId: String,
    onSave: (String, String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var empId by remember { mutableStateOf(currentEmployeeId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        titleContentColor = TextPrimary,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Edit, null, tint = AccentBlue, modifier = Modifier.size(28.dp)) }
        },
        title = { Text("Edit Staff Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text("Name", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF09090B))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = name, onValueChange = { name = it },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                            singleLine = true, cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
                Column {
                    Text("Employee ID", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF09090B))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = empId, onValueChange = { empId = it },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                            singleLine = true, cursorBrush = SolidColor(AccentBlue),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(AccentBlue)
                    .clickable { onSave(name.trim(), empId.trim()) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        },
        dismissButton = {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
        }
    )
}

