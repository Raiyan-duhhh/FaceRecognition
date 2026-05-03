package com.example.facerecognition.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.example.facerecognition.R

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GeofenceReceiver", "Error: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            handleExit(context)
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("Geofence", "User entered campus!")
            sendNotification(context, "Welcome Back", "You have entered the campus boundaries.")
        }
    }

    private fun handleExit(context: Context) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Lunch Exception: Ignore exit between 13:00 and 14:00 (1:00 PM - 2:00 PM)
        if (hour in 13..14) {
            Log.d("Geofence", "User left campus during lunch hours. Ignoring.")
            return
        }

        Log.d("Geofence", "User left campus outside lunch hours!")

        // 3-Strikes Logic using SharedPreferences
        val prefs = context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        val lastStrikeDate = prefs.getString("strike_date", "")
        var strikes = if (lastStrikeDate == todayStr) prefs.getInt("strike_count", 0) else 0
        
        strikes++
        
        prefs.edit()
            .putString("strike_date", todayStr)
            .putInt("strike_count", strikes)
            .apply()

        when (strikes) {
            1 -> {
                sendNotification(context, "Warning: Campus Exit (Strike 1)", "You have exited the campus. Please return to avoid being marked absent.")
            }
            2 -> {
                sendNotification(context, "Final Warning: Campus Exit (Strike 2)", "You have exited the campus again. One more strike and you will be marked absent.")
            }
            else -> {
                sendNotification(context, "Absent: Campus Exit (Strike 3)", "You have exceeded the allowed campus exits. You have been marked absent for today.")
                markUserAbsentInFirestore()
            }
        }
    }

    private fun markUserAbsentInFirestore() {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: Replace with the actual logged-in user's staffId (e.g. from DataStore/SharedPreferences)
                val staffId = "mock_staff_id" 
                val db = FirebaseFirestore.getInstance()
                
                // Query today's attendance log for this staff member
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val todayEnd = todayStart + 86400000L // + 1 day
                
                val logs = db.collection("attendanceLogs")
                    .whereEqualTo("staffId", staffId)
                    .whereGreaterThanOrEqualTo("timestamp", todayStart)
                    .whereLessThan("timestamp", todayEnd)
                    .get()
                    .await()
                    
                for (document in logs.documents) {
                    db.collection("attendanceLogs").document(document.id)
                        .update("status", "Absent")
                        .await()
                    Log.d("Geofence", "Updated log ${document.id} to Absent for staff $staffId")
                }
            } catch (e: Exception) {
                Log.e("Geofence", "Failed to update Firestore status to Absent", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "geofence_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Campus Boundaries",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using system icon for now
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)
    }
}
