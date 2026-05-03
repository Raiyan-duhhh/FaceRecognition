package com.example.facerecognition.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class CampusTrackingService : Service() {

    private lateinit var geofencingClient: GeofencingClient
    private val GEOFENCE_ID = "BITS_NARSAMPET"

    companion object {
        private const val CHANNEL_ID = "CampusTrackingServiceChannel"
        private const val NOTIFICATION_ID = 1001
        
        // Campus Coordinates
        private const val CAMPUS_LAT = 17.937086
        private const val CAMPUS_LNG = 79.848835
        // Strict BITS Narsampet Campus Radius
        private const val CAMPUS_RADIUS = 234.23f
    }

    override fun onCreate() {
        super.onCreate()
        geofencingClient = LocationServices.getGeofencingClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FaceAttend")
            .setContentText("Campus tracking active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        setupGeofence()

        return START_STICKY
    }

    private fun setupGeofence() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CampusTrackingService", "Missing BACKGROUND_LOCATION permission. Geofence not added.")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(CAMPUS_LAT, CAMPUS_LNG, CAMPUS_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent()

        try {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)?.run {
                addOnSuccessListener {
                    Log.d("CampusTrackingService", "Geofence added successfully.")
                }
                addOnFailureListener { e ->
                    Log.e("CampusTrackingService", "Failed to add geofence", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e("CampusTrackingService", "SecurityException: Missing required permissions", e)
        }
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(this, 0, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Campus Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding
    }
}
