package com.example.facerecognition.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facerecognition.viewmodel.AdminViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReportsScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // UI State
    var selectedYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var selectedMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }
    var csvData by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var rawCsvString by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // File Download Launcher
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                            writer.write(rawCsvString)
                        }
                    }
                    Toast.makeText(context, "Report Saved Successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to save report: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (rawCsvString.isNotEmpty()) {
                        IconButton(onClick = {
                            downloadLauncher.launch("Attendance_Report_${selectedMonth}_${selectedYear}.csv")
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download CSV")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // ── Selection UI ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Month Dropdown (Simplified for this example)
                        MonthPicker(
                            selectedMonth = selectedMonth,
                            onMonthSelected = { selectedMonth = it },
                            modifier = Modifier.weight(1f)
                        )

                        // Year Picker (Simplified)
                        YearPicker(
                            selectedYear = selectedYear,
                            onYearSelected = { selectedYear = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                rawCsvString = viewModel.generateMonthlyReport(selectedYear, selectedMonth)
                                csvData = parseCsv(rawCsvString)
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Report")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Table UI ──────────────────────────────────────────────
            if (csvData.isNotEmpty()) {
                Text(
                    text = "Report Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    AttendanceTable(data = csvData)
                }
            } else if (!isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Select a period and click generate to view attendance.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AttendanceTable(data: List<List<String>>) {
    val horizontalScrollState = rememberScrollState()

    Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
        // Headers (First Row)
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
            data.firstOrNull()?.forEach { header ->
                TableCell(text = header, isHeader = true)
            }
        }

        // Data Rows
        LazyColumn(modifier = Modifier.fillMaxHeight()) {
            items(data.drop(1)) { row ->
                Row(modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    row.forEach { cell ->
                        TableCell(text = cell, isHeader = false)
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String, isHeader: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .width(100.dp)
            .padding(8.dp),
        style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        color = if (isHeader) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPicker(selectedMonth: Int, onMonthSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val months = Month.values()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = Month.of(selectedMonth).name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Month") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            months.forEach { month ->
                DropdownMenuItem(
                    text = { Text(month.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onMonthSelected(month.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearPicker(selectedYear: Int, onYearSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = LocalDate.now().year
    val years = (currentYear - 2..currentYear + 1).toList()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Year") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            textStyle = MaterialTheme.typography.bodyMedium
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onYearSelected(year)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun parseCsv(csvString: String): List<List<String>> {
    return csvString.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            line.split(",").map { it.trim() }
        }
}
