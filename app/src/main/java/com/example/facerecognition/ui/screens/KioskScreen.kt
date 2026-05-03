package com.example.facerecognition.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.facerecognition.viewmodel.AttendanceViewModel
import java.util.concurrent.Executors

/**
 * Kiosk screen — full-screen front-camera feed with a status overlay.
 *
 * Designed to run unattended on a tablet mounted at the entrance.
 * The overlay at the bottom shows real-time recognition status with
 * colour-coded feedback (teal = scanning, green = recognised, red = unknown).
 */
@Composable
fun KioskScreen(
    viewModel: AttendanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // ── Permissions (Camera + Location) ──────────────────────────────

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!hasCameraPermission) needed.add(Manifest.permission.CAMERA)
        if (!hasLocationPermission) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    val allPermissionsGranted = hasCameraPermission && hasLocationPermission

    // ── UI ─────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (allPermissionsGranted) {
            // Camera preview — only starts when BOTH permissions are granted
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(
                                androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        androidx.camera.core.resolutionselector.ResolutionStrategy(
                                            Size(640, 480),
                                            androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                        )
                                    ).build()
                            )
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(
                                    Executors.newSingleThreadExecutor()
                                ) { imageProxy ->
                                    viewModel.processFrame(imageProxy)
                                }
                            }

                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // ── Permission Denied State ─────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Lock icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Permissions Required",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Face Attendance requires both Camera and Location access to verify identity and enforce campus geofencing.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Permission status indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PermissionStatusChip(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        granted = hasCameraPermission
                    )
                    PermissionStatusChip(
                        icon = Icons.Default.LocationOn,
                        label = "Location",
                        granted = hasLocationPermission
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Retry button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF3B82F6), Color(0xFF3B82F6).copy(alpha = 0.7f))
                            )
                        )
                        .clickable {
                            val needed = mutableListOf<String>()
                            if (!hasCameraPermission) needed.add(Manifest.permission.CAMERA)
                            if (!hasLocationPermission) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                            if (needed.isNotEmpty()) {
                                permissionLauncher.launch(needed.toTypedArray())
                            }
                        }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Grant Permissions",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Status overlay ─────────────────────────────────────────

        val (statusText, statusColor) = when (uiState) {
            is AttendanceViewModel.UiState.Scanning ->
                "Scanning..." to Color(0xFF00BCD4) // Teal

            is AttendanceViewModel.UiState.Recognised ->
                (uiState as AttendanceViewModel.UiState.Recognised).message to Color(0xFF4CAF50) // Green

            is AttendanceViewModel.UiState.Unknown ->
                "Face not recognised" to Color(0xFFF44336) // Red

            is AttendanceViewModel.UiState.Error ->
                "Error: ${(uiState as AttendanceViewModel.UiState.Error).message}" to Color(0xFFFF9800) // Orange
        }

        val animatedColor by animateColorAsState(
            targetValue = statusColor,
            animationSpec = tween(durationMillis = 400),
            label = "statusColorAnim"
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.85f),
                            animatedColor.copy(alpha = 0.65f)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Clock / Title overlay at top ───────────────────────────

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Face Attendance",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionStatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    granted: Boolean
) {
    val chipColor = if (granted) Color(0xFF10B981) else Color(0xFFEF4444)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(chipColor.copy(alpha = 0.12f))
                .border(1.dp, chipColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = chipColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (granted) "Granted" else "Denied",
            color = chipColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
