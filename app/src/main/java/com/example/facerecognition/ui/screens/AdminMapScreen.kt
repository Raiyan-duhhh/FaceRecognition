package com.example.facerecognition.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.facerecognition.viewmodel.MapViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import android.graphics.Color as AndroidColor
import androidx.preference.PreferenceManager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMapScreen(
    onNavigateToApprovals: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToManageStaff: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val collegeId = ""
    val liveLocations by viewModel.getLiveLocations(collegeId).collectAsState(initial = emptyList())

    // BITS Narsampet Coordinates
    val campusLat = 17.937320
    val campusLng = 79.849330
    val campusPos = GeoPoint(campusLat, campusLng)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val inBoundsCount = liveLocations.count { it.status == "Safe" || it.status == "In Bounds" || it.status == "P" || it.status == "HD" }
    val outOfBoundsCount = liveLocations.size - inBoundsCount

    Scaffold(
        containerColor = Color(0xFF09090B),
        topBar = {
            TopAppBar(
                title = { Text("Live Tracking Dashboard", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF09090B))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Top 60% - OSM Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setHasTransientState(true)
                            isVerticalMapRepetitionEnabled = false
                            controller.setZoom(16.0)
                            controller.setCenter(campusPos)

                            // Geofence Circle
                            val circle = Polygon().apply {
                                points = Polygon.pointsAsCircle(campusPos, 234.23)
                                fillColor = AndroidColor.argb(50, 0, 229, 255)
                                strokeColor = AndroidColor.parseColor("#00E5FF")
                                strokeWidth = 2f
                            }
                            overlays.add(circle)

                            // My Location
                            // val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            // locationOverlay.enableMyLocation()
                            // overlays.add(locationOverlay)
                        }
                    },
                    update = { view ->
                        // Clear previous markers (keep polygon and location overlay)
                        view.overlays.removeAll { it is OsmMarker }
                        
                        if (liveLocations.isNotEmpty()) {
                            val firstLoc = liveLocations.first()
                            view.controller.setCenter(GeoPoint(firstLoc.lat, firstLoc.lng))
                        } else {
                            view.controller.setCenter(campusPos)
                        }
                        
                        liveLocations.forEach { loc ->
                            val marker = OsmMarker(view).apply {
                                position = GeoPoint(loc.lat, loc.lng)
                                // Fallback to staffId if staffName is empty
                                title = loc.staffName.ifEmpty { loc.staffId }
                                snippet = "Status: ${loc.status}"
                                // Ensure the info window pops up when tapped
                                setPanToView(true)
                                setInfoWindow(org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(
                                    org.osmdroid.library.R.layout.bonuspack_bubble, view
                                ))
                            }
                            view.overlays.add(marker)
                        }
                        view.invalidate()
                    }
                )
            }

            // Bottom 40% - Analytics Dashboard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .background(Color(0xFF09090B))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "God-View Statistics",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // In Bounds Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "In Bounds",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = inBoundsCount.toString(),
                                color = Color(0xFF00FF87), // Green
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Out of Bounds Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Out of Bounds",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = outOfBoundsCount.toString(),
                                color = Color(0xFFF44336), // Red
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom nav
        }

        // Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF18181B).copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Map Tab (Active)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Map",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Map",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Approvals Tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clickable { onNavigateToApprovals() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Approvals",
                        tint = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Approvals",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Reports Tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clickable { onNavigateToReports() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Reports",
                        tint = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reports",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Settings Tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clickable { onNavigateToManageStaff() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFA1A1AA)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Settings",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        }
    }
}
