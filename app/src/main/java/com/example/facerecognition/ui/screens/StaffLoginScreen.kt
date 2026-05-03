package com.example.facerecognition.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facerecognition.ml.FaceDetectorHelper
import com.example.facerecognition.ml.FaceEmbedder
import com.example.facerecognition.viewmodel.StaffViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@Composable
fun StaffLoginScreen(
    viewModel: StaffViewModel,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val loginState by viewModel.loginState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val faceDetector = remember { FaceDetectorHelper() }
    val faceEmbedder = remember { FaceEmbedder(context) }

    var isScanning by remember { mutableStateOf(hasCameraPermission) }

    // If permission changes from false to true via the Launcher
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && loginState is StaffViewModel.LoginState.Idle) {
            isScanning = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            faceDetector.close()
            faceEmbedder.close()
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is StaffViewModel.LoginState.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1000) // Brief pause to show success message
            onLoginSuccess()
            viewModel.resetLoginState()
        }
    }

    // 5-second timeout, but only run if active scanning
    LaunchedEffect(isScanning) {
        if (isScanning && hasCameraPermission) {
            delay(5000L)
            if (isScanning) {
                isScanning = false
                viewModel.setLoginError("Face Not Recognized")
            }
        }
    }

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)) // Premium dark mode
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "← Back",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Staff Login",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Status message
            val statusColor = when {
                !hasCameraPermission -> Color(0xFFEF4444)
                loginState is StaffViewModel.LoginState.Success -> Color(0xFF10B981) // Green
                loginState is StaffViewModel.LoginState.Error -> Color(0xFFEF4444)   // Red
                else -> Color(0xFF00E5FF) // Blue HUD
            }
            val statusText = when {
                !hasCameraPermission -> "Camera Access Denied"
                loginState is StaffViewModel.LoginState.Success -> "Identity Verified: Welcome ${(loginState as StaffViewModel.LoginState.Success).staff.name}"
                loginState is StaffViewModel.LoginState.Error -> (loginState as StaffViewModel.LoginState.Error).message
                else -> "Scanning Identity..."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                    .padding(vertical = 12.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Central Area: Camera OR Permission Fallback OR Error
            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Access Required",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "FaceAttend requires the camera to securely verify staff identity via facial recognition.",
                            color = Color(0xFFA1A1AA),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E5FF))
                                .clickable {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "Grant Permission",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else if (isScanning || loginState is StaffViewModel.LoginState.Success) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 2.dp,
                            color = statusColor.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    if (isScanning) {
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
                                            ResolutionSelector.Builder()
                                                .setResolutionStrategy(
                                                    ResolutionStrategy(
                                                        Size(640, 480),
                                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                                    )
                                                ).build()
                                        )
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                                if (!isScanning) {
                                                    imageProxy.close()
                                                    return@setAnalyzer
                                                }

                                                scope.launch {
                                                    try {
                                                        val bitmap = withContext(Dispatchers.Default) {
                                                            imageProxyToBitmap(imageProxy)
                                                        }
                                                        if (bitmap != null) {
                                                            val faces = faceDetector.detectFaces(bitmap)
                                                            if (faces.isNotEmpty()) {
                                                                val embedding = withContext(Dispatchers.Default) {
                                                                    faceEmbedder.getEmbedding(faces[0])
                                                                }
                                                                val match = viewModel.findMatchingStaff(embedding)
                                                                if (match != null) {
                                                                    isScanning = false
                                                                    viewModel.setLoginSuccess(match)
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    } finally {
                                                        imageProxy.close()
                                                    }
                                                }
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
                    } else if (loginState is StaffViewModel.LoginState.Success) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        )
                    }

                    ScannerOverlay(color = statusColor)
                }
            } else if (loginState is StaffViewModel.LoginState.Error) {
                // Failure UI State Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Unrecognized Signature",
                            color = Color(0xFFA1A1AA),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEF4444))
                                .clickable {
                                    viewModel.resetLoginState()
                                    isScanning = true
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(text = "Retry scan", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val bracketLength = size.width * 0.15f
        val strokeWidth = 4.dp.toPx()
        val drawColor = color.copy(alpha = alpha)

        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val scaledWidth = size.width * scale * 0.8f
        val scaledHeight = size.height * scale * 0.8f
        val left = center.x - scaledWidth / 2
        val top = center.y - scaledHeight / 2
        val right = center.x + scaledWidth / 2
        val bottom = center.y + scaledHeight / 2

        drawLine(drawColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + bracketLength, top), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + bracketLength), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right - bracketLength, top), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + bracketLength), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left + bracketLength, bottom), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom - bracketLength), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right - bracketLength, bottom), strokeWidth)
        drawLine(drawColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - bracketLength), strokeWidth)
    }
}
