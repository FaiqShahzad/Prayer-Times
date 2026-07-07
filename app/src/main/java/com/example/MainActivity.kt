package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.PrayerTimesHomeView
import com.example.ui.screens.QiblaCompassView
import com.example.ui.screens.SettingsAndTimetableView
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PrayerViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: PrayerViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Multiple permission launcher (Location + Notification)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val postNotificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.setLocationPermissionGranted(true)
            fetchLocation()
        } else {
            viewModel.setLocationPermissionGranted(false)
            Toast.makeText(this, "Location permission is required for GPS calculations.", Toast.LENGTH_LONG).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) {
            Toast.makeText(this, "Alert notifications are disabled. You can enable them in system settings.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[PrayerViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MyApplicationTheme {
                MainContentScreen()
            }
        }

        // Check and launch initial permissions
        checkAndRequestPermissions()
    }

    @Composable
    fun MainContentScreen() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        var currentTab by remember { mutableStateOf(0) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar"),
                    containerColor = Color(0xFF211F26)
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        label = { Text("Schedule") },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Prayer Schedule") },
                        modifier = Modifier.testTag("nav_schedule_tab")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        label = { Text("Qibla") },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Qibla Compass") },
                        modifier = Modifier.testTag("nav_qibla_tab")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        label = { Text("Settings") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings and Timetable") },
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        ) { innerPadding ->
            val context = LocalContext.current

            // Dynamically fetch GPS location when the Home/Schedule view is open and GPS mode is enabled
            LaunchedEffect(uiState.settings.useGps, currentTab) {
                if (uiState.settings.useGps) {
                    if (hasLocationPermission()) {
                        viewModel.setLocationPermissionGranted(true)
                        fetchLocation()
                    } else {
                        viewModel.setLocationPermissionGranted(false)
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentTab) {
                    0 -> PrayerTimesHomeView(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestLocationPermission = { checkAndRequestPermissions() }
                    )
                    1 -> QiblaCompassView(
                        userLatitude = uiState.settings.latitude,
                        userLongitude = uiState.settings.longitude
                    )
                    2 -> SettingsAndTimetableView(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            viewModel.setLocationPermissionGranted(true)
            fetchLocation()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun fetchLocation() {
        try {
            if (hasLocationPermission()) {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            reverseGeocode(location.latitude, location.longitude)
                        } else {
                            Log.w("MainActivity", "Last known location is null. Retrying...")
                            // Trigger refresh times using last saved coordinates
                            viewModel.refreshPrayerTimes()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Failed to retrieve location", e)
                        viewModel.refreshPrayerTimes()
                    }
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security Exception requesting location", e)
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double) {
        val context: Context = this
        // Reverse geocoding in background threads to avoid UI jank
        val thread = Thread {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: "GPS Location"
                    val country = address.countryName ?: ""
                    runOnUiThread {
                        viewModel.updateCoordinates(latitude, longitude, city, country)
                    }
                } else {
                    runOnUiThread {
                        viewModel.updateCoordinates(latitude, longitude, "GPS Location", "Coordinates")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Geocoder failed", e)
                runOnUiThread {
                    // Fallback to formatted coordinate strings if reverse geocoding fails offline
                    val city = String.format(Locale.US, "GPS (%.2f, %.2f)", latitude, longitude)
                    viewModel.updateCoordinates(latitude, longitude, city, "Auto Detected")
                }
            }
        }
        thread.start()
    }
}
