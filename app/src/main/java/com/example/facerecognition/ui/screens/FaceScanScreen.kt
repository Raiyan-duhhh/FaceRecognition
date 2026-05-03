package com.example.facerecognition.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
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
import com.example.facerecognition.viewmodel.FaceScanViewModel
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val TAG = "FaceScanScreen"

// ════════════════════════════════════════════════════════════════════
//  Base64 Conversion Utility
// ════════════════════════════════════════════════════════════════════

/**
 * Converts an [ImageProxy] from [ImageCapture] into a Base64-encoded
 * JPEG data-URI string suitable for the V2 backend API.
 *
 * Pipeline:
 *   1. `ImageProxy.toBitmap()` — handles JPEG / YUV conversion internally.
 *   2. Rotation correction via EXIF `rotationDegrees`.
 *   3. JPEG compression at 90 % quality.
 *   4. `Base64.NO_WRAP` encoding.
 *   5. Prefix with `data:image/jpeg;base64,`.
 *
 * @return The complete data-URI string ready for the API payload.
 */
fun convertImageProxyToBase64Uri(imageProxy: ImageProxy): String {
    // Step 1 — Obtain a Bitmap (works for both JPEG and YUV backing)
    val rawBitmap = imageProxy.toBitmap()

    // Step 2 — Apply rotation from camera sensor metadata
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val bitmap = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(
            rawBitmap, 0, 0,
            rawBitmap.width, rawBitmap.height,
            matrix, true
        )
    } else {
        rawBitmap
    }

    // Step 3 — Compress to JPEG bytes
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    val jpegBytes = outputStream.toByteArray()

    // Step 4 — Base64 encode (NO_WRAP avoids line breaks in the string)
    val base64String = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

    // Step 5 — Return the full data-URI
    return "data:image/jpeg;base64,$base64String"
}

// ════════════════════════════════════════════════════════════════════
//  Main Screen
// ════════════════════════════════════════════════════════════════════

/**
 * "Space Cutting" image-capture screen for the V2 Face Recognition pipeline.
 *
 * Opens the **front camera** in a full-bleed preview, overlays animated scan
 * brackets, and presents a single capture button.  On press the captured
 * frame is JPEG-compressed, Base64-encoded with the required
 * `data:image/jpeg;base64,…` prefix, and submitted to the backend via
 * [FaceScanViewModel].
 *
 * @param faceScanViewModel  ViewModel handling Retrofit submission + GPS.
 * @param onImageCaptured    Optional callback receiving the raw Base64 string
 *                           (fired *in addition* to the ViewModel submission).
 * @param onBack             Navigation callback for the back button.
 */
@Composable
fun FaceScanScreen(
    faceScanViewModel: FaceScanViewModel = viewModel(),
    onImageCaptured: (String) -> Unit = {},
    onBack: () -> Unit
) {
    // ── Observe ViewModel state ────────────────────────────────────
    val attendanceState by faceScanViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Single-thread executor for ImageCapture callbacks
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // ── State ──────────────────────────────────────────────────────

    var isProcessing by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ImageCapture use-case reference — set once camera binds
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // ── Permission ─────────────────────────────────────────────────

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // ── Side-effects ───────────────────────────────────────────────

    // Auto-dismiss the capture flash after a short duration
    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(400L)
            showFlash = false
        }
    }

    // Clean up the executor when the composable leaves composition
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // ── UI ──────────────────────────────────────────────────────────

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
                    text = "Space Cutting",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Status pill ────────────────────────────────────────

            val statusText = when {
                !hasCameraPermission -> "Camera Access Denied"
                isProcessing         -> "Encoding image…"
                errorMessage != null -> errorMessage!!
                else                 -> "Position face and tap capture"
            }

            val statusBorder = if (!hasCameraPermission || errorMessage != null)
                Color(0xFFEF4444).copy(alpha = 0.5f)
            else
                Color.White.copy(alpha = 0.1f)

            val statusTextColor = when {
                !hasCameraPermission || errorMessage != null -> Color(0xFFEF4444)
                isProcessing -> Color(0xFF00E5FF)
                else         -> Color.White.copy(alpha = 0.7f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, statusBorder, RoundedCornerShape(30.dp))
                    .padding(vertical = 12.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    color = statusTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Camera preview / Permission fallback ───────────────

            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 2.dp,
                            color = if (isProcessing) Color(0xFF00FF87)
                            else Color(0xFF00E5FF).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    // CameraX preview via AndroidView
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val cameraProviderFuture =
                                ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(
                                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                    )
                                    .build()

                                imageCapture = capture

                                val cameraSelector = CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                    .build()

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        capture
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "CameraX bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Animated scanner bracket overlay
                    FaceScanOverlay()

                    // Green flash on successful capture
                    // NOTE: Fully-qualified to avoid ColumnScope.AnimatedVisibility
                    // resolution — this Box sits inside a Column child, so the
                    // compiler would otherwise pick the wrong overload.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFlash,
                        enter = fadeIn(tween(100)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF00FF87).copy(alpha = 0.25f))
                        )
                    }
                }
            } else {
                // ── Permission-denied fallback UI ──────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            Color(0xFFEF4444).copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp)
                        )
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
                            text = "FaceAttend requires camera access to capture " +
                                    "your face for identity verification.",
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

            Spacer(modifier = Modifier.height(20.dp))

            // ── Capture button ─────────────────────────────────────

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SpaceCuttingCaptureButton(
                    enabled = hasCameraPermission && !isProcessing,
                    isProcessing = isProcessing,
                    onClick = {
                        val capture = imageCapture ?: return@SpaceCuttingCaptureButton

                        isProcessing = true
                        errorMessage = null

                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {

                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        showFlash = true
                                        val base64Uri =
                                            convertImageProxyToBase64Uri(image)
                                        Log.d(
                                            TAG,
                                            "Capture OK — Base64 length: " +
                                                    "${base64Uri.length}"
                                        )

                                        // Fire ViewModel submission → Retrofit
                                        faceScanViewModel.submitAttendance(base64Uri)

                                        // Also call the raw callback (for chaining)
                                        onImageCaptured(base64Uri)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Conversion failed", e)
                                        errorMessage = "Image conversion failed"
                                    } finally {
                                        image.close()
                                        isProcessing = false
                                    }
                                }

                                override fun onError(
                                    exception: ImageCaptureException
                                ) {
                                    Log.e(TAG, "Capture failed", exception)
                                    errorMessage =
                                        "Capture failed: ${exception.message}"
                                    isProcessing = false
                                }
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Position your face within the frame\n" +
                        "and tap the button to capture.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Attendance Result Overlay ───────────────────────────────
        // Overlaid on top of the full-screen Box so it floats above
        // the camera preview and capture button.

        when (val state = attendanceState) {
            is FaceScanViewModel.AttendanceUiState.Loading -> {
                // Dimmed overlay with spinner
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Submitting to server…",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            is FaceScanViewModel.AttendanceUiState.Success -> {
                AttendanceResultOverlay(
                    title = "Attendance Marked ✓",
                    message = "${state.staffName} — ${state.message}",
                    accentColor = Color(0xFF00FF87),
                    onDismiss = { faceScanViewModel.resetState() }
                )
            }

            is FaceScanViewModel.AttendanceUiState.OutOfBounds -> {
                AttendanceResultOverlay(
                    title = "Out of Bounds",
                    message = state.message,
                    accentColor = Color(0xFFFF9800),
                    onDismiss = { faceScanViewModel.resetState() }
                )
            }

            is FaceScanViewModel.AttendanceUiState.Error -> {
                AttendanceResultOverlay(
                    title = "Error",
                    message = state.message,
                    accentColor = Color(0xFFEF4444),
                    onDismiss = { faceScanViewModel.resetState() }
                )
            }

            is FaceScanViewModel.AttendanceUiState.Idle -> { /* no overlay */ }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Attendance Result Overlay
// ════════════════════════════════════════════════════════════════════

/**
 * Full-screen dimmed overlay with a glassmorphic card showing the
 * attendance result.  Tapping "Dismiss" calls [onDismiss] which
 * resets the ViewModel state back to Idle.
 */
@Composable
private fun AttendanceResultOverlay(
    title: String,
    message: String,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor)
                    .clickable { onDismiss() }
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dismiss",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Capture Button
// ════════════════════════════════════════════════════════════════════

/**
 * Pulsing cyan-ringed capture button with a white inner disc.
 * Shows a progress spinner while [isProcessing] is true.
 */
@Composable
private fun SpaceCuttingCaptureButton(
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "capturePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val buttonAlpha = if (enabled) 1f else 0.4f

    // Outer glow ring
    Box(contentAlignment = Alignment.Center) {
        // Glow halo (only when idle and enabled)
        if (enabled && !isProcessing) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF00E5FF).copy(alpha = glowAlpha * 0.15f)
                    )
            )
        }

        // Main button
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(
                    width = 4.dp,
                    color = Color(0xFF00E5FF).copy(alpha = buttonAlpha),
                    shape = CircleShape
                )
                .clickable(enabled = enabled) { onClick() }
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (isProcessing) Color.Transparent
                        else Color.White.copy(alpha = buttonAlpha * 0.9f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        tint = Color(0xFF09090B),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Scanner Bracket Overlay
// ════════════════════════════════════════════════════════════════════

/**
 * Animated corner brackets that pulse and breathe over the camera
 * preview to guide face positioning.
 */
@Composable
private fun FaceScanOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanOverlay")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bracketScale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bracketAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val bracketLength = size.width * 0.15f
        val strokeWidth = 4.dp.toPx()
        val color = Color(0xFF00E5FF).copy(alpha = alpha)

        val center = Offset(size.width / 2, size.height / 2)
        val scaledWidth = size.width * scale * 0.8f
        val scaledHeight = size.height * scale * 0.8f
        val left = center.x - scaledWidth / 2
        val top = center.y - scaledHeight / 2
        val right = center.x + scaledWidth / 2
        val bottom = center.y + scaledHeight / 2

        // Top-Left corner
        drawLine(color, Offset(left, top), Offset(left + bracketLength, top), strokeWidth)
        drawLine(color, Offset(left, top), Offset(left, top + bracketLength), strokeWidth)

        // Top-Right corner
        drawLine(color, Offset(right, top), Offset(right - bracketLength, top), strokeWidth)
        drawLine(color, Offset(right, top), Offset(right, top + bracketLength), strokeWidth)

        // Bottom-Left corner
        drawLine(color, Offset(left, bottom), Offset(left + bracketLength, bottom), strokeWidth)
        drawLine(color, Offset(left, bottom), Offset(left, bottom - bracketLength), strokeWidth)

        // Bottom-Right corner
        drawLine(color, Offset(right, bottom), Offset(right - bracketLength, bottom), strokeWidth)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - bracketLength), strokeWidth)
    }
}
