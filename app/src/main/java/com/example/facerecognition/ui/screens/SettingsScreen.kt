package com.example.facerecognition.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.facerecognition.viewmodel.SettingsViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val latePunchTime by viewModel.latePunchTime.collectAsState()
    val halfDayTime by viewModel.halfDayTime.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val showSuccess by viewModel.showSuccess.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            snackbarHostState.showSnackbar("Settings saved successfully")
            viewModel.resetSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TimePickerField(
                label = "Late Punch Threshold",
                currentTime = latePunchTime,
                onTimeSelected = { viewModel.updateLatePunch(it) }
            )

            TimePickerField(
                label = "Half Day Threshold",
                currentTime = halfDayTime,
                onTimeSelected = { viewModel.updateHalfDay(it) }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Settings")
                }
            }
        }
    }
}

@Composable
fun TimePickerField(
    label: String,
    currentTime: String,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val amPm = if (hourOfDay < 12) "AM" else "PM"
            val hour = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
            val formattedTime = String.format("%02d:%02d %s", hour, minute, amPm)
            onTimeSelected(formattedTime)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        OutlinedCard(
            onClick = { timePickerDialog.show() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = currentTime, style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.AccessTime, contentDescription = "Select Time")
            }
        }
    }
}
