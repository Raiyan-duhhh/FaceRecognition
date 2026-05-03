package com.example.facerecognition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.facerecognition.utils.CsvExportUtils
import com.example.facerecognition.viewmodel.ReportViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportBottomSheet(
    onDismiss: () -> Unit,
    viewModel: ReportViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var violationsOnly by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    // Dummy Date Range: Last 7 days to Today
    val endDate = remember { Calendar.getInstance().timeInMillis }
    val startDate = remember { 
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis 
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B), // Zinc900
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Export Attendance Report",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Date Range: Last 7 Days", // Placeholder for actual Date Picker UI
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Export Violations Only (Absent/Late)",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Switch(
                    checked = violationsOnly,
                    onCheckedChange = { violationsOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF00FF87), // AccentGreen
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isGenerating = true
                    scope.launch {
                        val logs = viewModel.generateReport(
                            collegeId = "tenant_bits_01",
                            startDate = startDate,
                            endDate = endDate,
                            violationsOnly = violationsOnly
                        )
                        isGenerating = false
                        CsvExportUtils.createAndShareCsv(context, logs)
                        onDismiss()
                    }
                },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF), // AccentBlue
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "Generate & Share CSV",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
