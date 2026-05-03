package com.example.facerecognition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.facerecognition.data.entity.PermissionRequest
import com.example.facerecognition.viewmodel.PermissionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPermissionScreen(
    onBack: () -> Unit,
    viewModel: PermissionViewModel = viewModel()
) {
    // Dummy SaaS tenant. In a real app, you'd get this from the Admin's auth context.
    val collegeId = "tenant_bits_01" 
    val pendingRequests by viewModel.getPendingRequests(collegeId).collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Color(0xFF09090B), // Zinc950
        topBar = {
            TopAppBar(
                title = { Text("Pending Permissions", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        if (pendingRequests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pending requests found.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pendingRequests) { request ->
                    PermissionRequestCard(
                        request = request,
                        onApprove = { viewModel.updatePermissionStatus(request.id, "Approved") },
                        onReject = { viewModel.updatePermissionStatus(request.id, "Rejected") }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestCard(
    request: PermissionRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF18181B) // Zinc900
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.facultyName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = request.permissionType,
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0xFF00E5FF).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Text(
                text = "Date: ${request.date} | ${request.startTime} - ${request.endTime}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Text(
                text = "Reason: ${request.reason}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF87), // AccentGreen
                        contentColor = Color.Black
                    )
                ) {
                    Text("Approve", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336), // Red
                        contentColor = Color.White
                    )
                ) {
                    Text("Reject", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
