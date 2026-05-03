package com.example.facerecognition.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facerecognition.ml.FaceDetectorHelper
import com.example.facerecognition.ml.FaceEmbedder
import com.example.facerecognition.viewmodel.AdminViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Staff registration screen with guided 3-photo face capture.
 *
 * Flow:
 *   1. Camera phase — captures 3 embeddings with pose prompts
 *      ("Look straight", "Look slightly left", "Look slightly right")
 *   2. Form phase — name & employee ID entry
 *   3. Save via AdminViewModel → success
 *
 * Each capture auto-triggers when a face is detected, with a 1.5s
 * cooldown between captures to let the user adjust their pose.
 */
@Composable
fun RegisterStaffScreen(
    viewModel: AdminViewModel,
    onRegistrationComplete: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val registrationState by viewModel.registrationState.collectAsState()

    // ── Capture state ──────────────────────────────────────────────

    val posePrompts = remember {
        listOf("Look straight ahead", "Look slightly left", "Look slightly right")
    }

    val embeddings = remember { mutableStateListOf<FloatArray>() }
    var captureIndex by remember { mutableIntStateOf(0) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureMessage by remember { mutableStateOf("") }
    var isCameraPhase by remember { mutableStateOf(false) }

    // ── Form state ─────────────────────────────────────────────────

    var staffName by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }

    // ── Permission state ───────────────────────────────────────────

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

    // ── ML helpers (scoped to this screen) ──────────────────────────

    val faceDetector = remember { FaceDetectorHelper() }
    val faceEmbedder = remember { FaceEmbedder(context) }

    // Cleanup when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            faceDetector.close()
            faceEmbedder.close()
        }
    }

    // Handle registration result
    LaunchedEffect(registrationState) {
        if (registrationState is AdminViewModel.RegistrationState.Success) {
            viewModel.resetRegistrationState()
            onRegistrationComplete()
        }
    }

    // ── Image conversion utility ───────────────────────────────────

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

    // ── UI ─────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF09090B),
                        Color(0xFF000000),
                        Color(0xFF000000)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ── Header ─────────────────────────────────────────────

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
                    text = "Register New Staff",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Crossfade(
                targetState = isCameraPhase,
                animationSpec = tween(600),
                label = "form_crossfade"
            ) { showCamera ->
                if (showCamera) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // ════════════════════════════════════════════════════
                        //  PHASE 1 — Camera capture
                        // ════════════════════════════════════════════════════

                // Progress Indicator
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = embeddings.size / 3f,
                    animationSpec = androidx.compose.animation.core.tween(500),
                    label = "progress"
                )

                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(80.dp),
                        color = Color(0xFF00FF87),
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeWidth = 6.dp,   
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Status Text
                val statusText = when {
                    !hasCameraPermission -> "Camera Access Denied"
                    registrationState is AdminViewModel.RegistrationState.Saving -> "Processing facial encodings..."
                    embeddings.size >= 3 -> "All required encodes captured. Saving..."
                    isCapturing -> "Face detected. Encoding facial metrics..."
                    else -> "Scanning environment..."
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, if (!hasCameraPermission) Color(0xFFEF4444).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(30.dp))
                        .padding(vertical = 12.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusText,
                        color = if (!hasCameraPermission) Color(0xFFEF4444) else if (isCapturing) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Camera preview
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = 2.dp,
                                color = if (isCapturing) Color(0xFF00FF87) else Color(0xFF00E5FF).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
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
                                            // Only process if we still need captures and aren't mid-capture
                                            if (embeddings.size >= 3 || isCapturing) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }

                                            isCapturing = true
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
                                                            embeddings.add(embedding)
                                                            android.util.Log.d("FaceDebug", "Captured embedding #${embeddings.size}, size=${embedding.size}")
                                                            captureMessage = "✓ Captured!"
                                                            captureIndex = embeddings.size

                                                            if (embeddings.size >= 3) {
                                                                if (registrationState !is AdminViewModel.RegistrationState.Saving && registrationState !is AdminViewModel.RegistrationState.Success) {
                                                                    viewModel.registerStaff(staffName, employeeId, embeddings.toList())
                                                                }
                                                            } else {
                                                                // Cooldown before next capture
                                                                kotlinx.coroutines.delay(1500L)
                                                                captureMessage = ""
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("CAMERA_CRASH", "Analyzer failed", e)
                                                } finally {
                                                    imageProxy.close()
                                                    isCapturing = false
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

                    ScannerOverlay()

                    // Capture flash overlay
                    
                    CaptureFlashOverlay(captureMessage = captureMessage)
                }
                } else {
                    // Fallback UI
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Position your face in the frame.\nCapture happens automatically when a face is detected.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // ════════════════════════════════════════════════════
                        //  PHASE 1 — Name & ID entry
                        // ════════════════════════════════════════════════════

                        val focusManager = LocalFocusManager.current

                Text(
                    text = "Staff Details",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Name field
                OutlinedTextField(
                    value = staffName,
                    onValueChange = { staffName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color(0xFF00E5FF)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Employee ID field
                OutlinedTextField(
                    value = employeeId,
                    onValueChange = { employeeId = it },
                    label = { Text("College/Employee ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color(0xFF00E5FF)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Error message
                val errorMsg = (registrationState as? AdminViewModel.RegistrationState.Error)?.message
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFF44336),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Continue button
                Button(
                    onClick = {
                        scope.launch {
                            val isEligible = viewModel.verifyEligibilityBeforeCapture(employeeId)
                            if (isEligible) {
                                isCameraPhase = true
                            }
                        }
                    },
                    enabled = staffName.isNotBlank() &&
                            employeeId.isNotBlank() &&
                            registrationState !is AdminViewModel.RegistrationState.Saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        disabledContainerColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
                        contentColor = Color.Black
                    )
                ) {
                    if (registrationState is AdminViewModel.RegistrationState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Continue to Face Capture",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureFlashOverlay(
    captureMessage: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = captureMessage.isNotEmpty(),
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF00FF87).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = captureMessage,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
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
        val color = Color(0xFF00E5FF).copy(alpha = alpha)

        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val scaledWidth = size.width * scale * 0.8f
        val scaledHeight = size.height * scale * 0.8f
        val left = center.x - scaledWidth / 2
        val top = center.y - scaledHeight / 2
        val right = center.x + scaledWidth / 2
        val bottom = center.y + scaledHeight / 2

        // Top-Left
        drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + bracketLength, top), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + bracketLength), strokeWidth)

        // Top-Right
        drawLine(color, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right - bracketLength, top), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + bracketLength), strokeWidth)

        // Bottom-Left
        drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left + bracketLength, bottom), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom - bracketLength), strokeWidth)

        // Bottom-Right
        drawLine(color, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right - bracketLength, bottom), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - bracketLength), strokeWidth)
    }
}
