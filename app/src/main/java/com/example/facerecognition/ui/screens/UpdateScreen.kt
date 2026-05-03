package com.example.facerecognition

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun UpdateScreen(updateUrl: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Update Required",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "A new version of FaceAttend is available. You must update to continue.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                // This opens the browser to download your new APK
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Download Update")
        }
    }
}