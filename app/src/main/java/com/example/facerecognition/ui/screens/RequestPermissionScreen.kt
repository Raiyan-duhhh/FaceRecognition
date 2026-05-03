package com.example.facerecognition.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.facerecognition.data.entity.PermissionRequest
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPermissionScreen(
    onBack: () -> Unit
) {
    var selectedType by remember { mutableStateOf("Late Arrival") }
    var date by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    val permissionTypes = listOf("Late Arrival", "Early Leave", "WFH", "On-Duty (OD)", "Medical")
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color(0xFF09090B), // Zinc950
        topBar = {
            TopAppBar(
                title = { Text("Request Permission", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Exception Matrix",
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            
            Text(
                text = "Apply for a tracking shield or leave exception.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Permission Type Dropdown
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Permission Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    )
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFF18181B))
                ) {
                    permissionTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type, color = Color.White) },
                            onClick = {
                                selectedType = type
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Date Field
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = Color(0xFF00E5FF),
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Start Time Field
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Start Time") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    )
                )

                // End Time Field
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("End Time") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    )
                )
            }

            // Reason Field
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = Color(0xFF00E5FF),
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            Button(
                onClick = {
                    // Create the Request Object
                    val request = PermissionRequest(
                        id = UUID.randomUUID().toString(), // Will be ignored by Firestore DocumentId
                        collegeId = "tenant_bits_01", // Dummy SaaS tenant
                        departmentId = "dept_cse_01", // Dummy Dept
                        facultyId = "EMP-999", // Dummy faculty ID
                        facultyName = "John Doe",
                        permissionType = selectedType,
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        reason = reason,
                        status = "Pending" // Defaults to Pending
                    )
                    
                    Log.d("PermissionUI", "Submitting Request: $request")
                    // TODO: Pass to ViewModel (e.g., staffViewModel.submitPermissionRequest(request))
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "Submit Request",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
