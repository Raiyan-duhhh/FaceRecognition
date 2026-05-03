package com.example.facerecognition

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.facerecognition.ui.navigation.FaceAttendanceNavGraph
import com.example.facerecognition.ui.theme.FaceRecognitionTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.facerecognition.BuildConfig

class MainActivity : ComponentActivity() {

    companion object {
        // Simulate the current installed version for testing.
        // Set this to a low number so the "Update Available" dialog triggers.
        // In production, read from BuildConfig.VERSION_CODE or PackageInfo.
        val CURRENT_VERSION_CODE = BuildConfig.VERSION_CODE
        private const val TAG = "OTA_Update"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // ── OTA State ────────────────────────────────────────────────
            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateUrl by remember { mutableStateOf("") }
            var isCheckingUpdate by remember { mutableStateOf(true) }

            // Keep the native splash screen visible while the OTA check runs
            splashScreen.setKeepOnScreenCondition { isCheckingUpdate }

            // ── Firestore OTA Check (runs once on launch) ────────────────
            LaunchedEffect(Unit) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val snapshot = db.collection("app_settings")
                        .document("config")
                        .get()
                        .await()

                    if (snapshot.exists()) {
                        val latestVersionCode = snapshot.getLong("latest_version_code") ?: 0L
                        val downloadUrl = snapshot.getString("download_url") ?: ""

                        Log.d(TAG, "Cloud version=$latestVersionCode, Local version=$CURRENT_VERSION_CODE")
                        Log.d(TAG, "Download URL=$downloadUrl")

                        if (latestVersionCode > CURRENT_VERSION_CODE) {
                            updateUrl = downloadUrl
                            showUpdateDialog = true
                            Log.d(TAG, "Update required! Showing blocking dialog.")
                        } else {
                            Log.d(TAG, "App is up to date.")
                        }
                    } else {
                        Log.w(TAG, "app_settings/config document does not exist in Firestore.")
                    }
                } catch (e: Exception) {
                    // If Firestore is unreachable, let the user in gracefully
                    Log.e(TAG, "OTA check failed: ${e.message}", e)
                } finally {
                    isCheckingUpdate = false
                }
            }

            // ── UI Router ────────────────────────────────────────────────
            // Splash screen stays visible until isCheckingUpdate → false,
            // so we can always render the content below.
            FaceRecognitionTheme {
                FaceAttendanceNavGraph()
            }

            // Blocking, non-dismissible update dialog overlaid on top
            if (showUpdateDialog) {
                OtaUpdateDialog(updateUrl = updateUrl)
            }
        }
    }
}

/**
 * A blocking, non-dismissible AlertDialog that forces the user
 * to update the app before proceeding.
 */
@Composable
private fun OtaUpdateDialog(updateUrl: String) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* Intentionally empty — dialog is non-dismissible */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Text(
                text = "🔄",
                style = MaterialTheme.typography.displaySmall
            )
        },
        title = {
            Text(
                text = "Update Available",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "A new version of FaceAttend is available.\nYou must update to continue using this app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Update Now",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    )
}