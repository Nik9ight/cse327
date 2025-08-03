package com.example.llmapp.geofence

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.*
import android.app.AlertDialog
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.example.llmapp.R
import com.example.llmapp.TelegramService
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class GeofenceActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private val TAG = "GeofenceActivity"
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var currentLocation: Location? = null
    private val geofenceCircles = mutableMapOf<String, Circle>()
    private val geofenceMarkers = mutableMapOf<String, Marker>()
    
    // UI Components
    private lateinit var etGeofenceName: EditText
    private lateinit var etRadius: EditText
    private lateinit var etEnterMessage: EditText
    private lateinit var etExitMessage: EditText
    private lateinit var etChatId: EditText
    private lateinit var switchEnableExit: Switch
    private lateinit var btnAddGeofence: Button
    private lateinit var btnCurrentLocation: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var llGeofences: LinearLayout
    
    private var selectedLatLng: LatLng? = null
    
    // Permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Fine location permission granted
                enableMapLocation() // Enable map location display
                getCurrentLocation()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Request background location permission
                    requestBackgroundLocationPermission()
                }
            }
            else -> {
                Toast.makeText(this, "Location permission required for geofencing", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Background location permission recommended for reliable geofencing", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide the action bar to prevent overlap with custom title
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_geofence)
        
        // Initialize
        geofenceManager = GeofenceManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize UI
        initializeUI()
        
        // Setup map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        // Load existing geofences
        loadGeofences()
        
        // Request permissions
        requestLocationPermissions()
    }
    
    private fun initializeUI() {
        etGeofenceName = findViewById(R.id.etGeofenceName)
        etRadius = findViewById(R.id.etRadius)
        etEnterMessage = findViewById(R.id.etEnterMessage)
        etExitMessage = findViewById(R.id.etExitMessage)
        etChatId = findViewById(R.id.etChatId)
        switchEnableExit = findViewById(R.id.switchEnableExit)
        btnAddGeofence = findViewById(R.id.btnAddGeofence)
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation)
        btnSettings = findViewById(R.id.btnSettings)
        llGeofences = findViewById(R.id.lvGeofences)
        
        // Set default values
        etRadius.setText("100")
        etEnterMessage.setText("🎯 Entered location zone!")
        etExitMessage.setText("👋 Left location zone!")
        
        // Set up chat ID field to show default on focus
        setupChatIdHint()
        
        // Setup listeners
        btnAddGeofence.setOnClickListener { addGeofence() }
        btnCurrentLocation.setOnClickListener { getCurrentLocation() }
        btnSettings.setOnClickListener { showBotSettingsDialog() }
        
        switchEnableExit.setOnCheckedChangeListener { _, isChecked ->
            etExitMessage.isEnabled = isChecked
        }
    }
    
    private fun setupChatIdHint() {
        // Set initial hint
        etChatId.hint = "Telegram Chat ID (optional)"
        
        etChatId.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Show default chat ID when focused
                val telegramService = TelegramService(this)
                val defaultChatId = telegramService.getChatId()
                etChatId.hint = if (defaultChatId != null) "Default: $defaultChatId" else "Optional: Override default chat"
            } else {
                // Reset to generic hint when not focused
                etChatId.hint = "Telegram Chat ID (optional)"
            }
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d(TAG, "Map ready, setting up...")
        
        // Set default location (can be changed)
        val defaultLocation = LatLng(23.8103, 90.4125) // Dhaka, Bangladesh
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
        
        // Set map click listener
        map.setOnMapClickListener { latLng ->
            Log.d(TAG, "Map clicked at: ${latLng.latitude}, ${latLng.longitude}")
            selectedLatLng = latLng
            updateMapSelection()
        }
        
        // Enable location if permission granted
        if (geofenceManager.hasLocationPermissions()) {
            Log.d(TAG, "Location permissions already granted, enabling map location")
            enableMapLocation()
        } else {
            Log.d(TAG, "Location permissions not granted yet")
        }
        
        // Load existing geofences on map
        showGeofencesOnMap()
    }
    
    private fun requestLocationPermissions() {
        when {
            geofenceManager.hasLocationPermissions() -> {
                getCurrentLocation()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                }
            }
            else -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }
    
    private fun requestBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
            !geofenceManager.hasBackgroundLocationPermission()) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    
    private fun enableMapLocation() {
        if (!geofenceManager.hasLocationPermissions()) {
            Log.e(TAG, "Location permission not granted for map")
            return
        }
        
        try {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = true
            Log.d(TAG, "Map location enabled successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            Toast.makeText(this, "Location permission required to show your location", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getCurrentLocation() {
        if (!geofenceManager.hasLocationPermissions()) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Requesting current location...")
        
        try {
            // First try to get last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Got last known location: ${location.latitude}, ${location.longitude}")
                    currentLocation = location
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    selectedLatLng = latLng
                    updateMapSelection()
                    Toast.makeText(this, "Current location found", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Last known location is null, requesting fresh location")
                    // Request fresh location if last location is null
                    requestFreshLocation()
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get last location", exception)
                Toast.makeText(this, "Failed to get location: ${exception.message}", Toast.LENGTH_LONG).show()
                // Try requesting fresh location as fallback
                requestFreshLocation()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestFreshLocation() {
        if (!geofenceManager.hasLocationPermissions()) {
            return
        }
        
        Log.d(TAG, "Requesting fresh location...")
        
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(10000)
                .build()
            
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        Log.d(TAG, "Got fresh location: ${location.latitude}, ${location.longitude}")
                        currentLocation = location
                        val latLng = LatLng(location.latitude, location.longitude)
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        selectedLatLng = latLng
                        updateMapSelection()
                        Toast.makeText(this@GeofenceActivity, "Current location found", Toast.LENGTH_SHORT).show()
                        
                        // Stop location updates after getting one location
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
                
                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "Location not available")
                        Toast.makeText(this@GeofenceActivity, 
                            "Location not available. Please check:\n• GPS is enabled\n• Location services are on", 
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
            
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            
            // Set timeout to stop location requests
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                fusedLocationClient.removeLocationUpdates(locationCallback)
                if (currentLocation == null) {
                    Toast.makeText(this, "Unable to get current location. Please ensure:\n• GPS is enabled\n• Location services are on\n• You're not indoors", Toast.LENGTH_LONG).show()
                }
            }, 15000) // 15 seconds timeout
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting fresh location", e)
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateMapSelection() {
        selectedLatLng?.let { latLng ->
            googleMap?.clear()
            googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Selected Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            
            // Show existing geofences again
            showGeofencesOnMap()
        }
    }
    
    private fun addGeofence() {
        val name = etGeofenceName.text.toString().trim()
        val radiusText = etRadius.text.toString().trim()
        val enterMessage = etEnterMessage.text.toString().trim()
        val exitMessage = etExitMessage.text.toString().trim()
        val chatIdText = etChatId.text.toString().trim()
        val enableExit = switchEnableExit.isChecked
        
        // Validation
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter geofence name", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (radiusText.isEmpty()) {
            Toast.makeText(this, "Please enter radius", Toast.LENGTH_SHORT).show()
            return
        }
        
        val radius = radiusText.toFloatOrNull()
        if (radius == null || radius <= 0) {
            Toast.makeText(this, "Please enter valid radius", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (enterMessage.isEmpty()) {
            Toast.makeText(this, "Please enter enter message", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (enableExit && exitMessage.isEmpty()) {
            Toast.makeText(this, "Please enter exit message", Toast.LENGTH_SHORT).show()
            return
        }
        
        val latLng = selectedLatLng
        if (latLng == null) {
            Toast.makeText(this, "Please select location on map", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create geofence data
        val geofenceData = GeofenceData(
            id = "geofence_${System.currentTimeMillis()}",
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            radius = radius,
            enterMessage = enterMessage,
            exitMessage = if (enableExit) exitMessage else null,
            isEnabled = true,
            enableExit = enableExit,
            name = name,
            chatId = if (chatIdText.isNotEmpty()) chatIdText else null
        )
        
        // Add geofence
        geofenceManager.addGeofence(
            geofenceData = geofenceData,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Geofence added successfully", Toast.LENGTH_SHORT).show()
                    clearForm()
                    loadGeofences()
                    showGeofencesOnMap()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to add geofence: $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    
    private fun clearForm() {
        etGeofenceName.setText("")
        etRadius.setText("100")
        etEnterMessage.setText("🎯 Entered location zone!")
        etExitMessage.setText("👋 Left location zone!")
        etChatId.setText("")
        switchEnableExit.isChecked = false
        selectedLatLng = null
        googleMap?.clear()
        showGeofencesOnMap()
    }
    
    private fun loadGeofences() {
        val geofences = geofenceManager.getSavedGeofences()
        
        // Clear existing views
        llGeofences.removeAllViews()
        
        // Add each geofence as a view
        geofences.forEach { geofence ->
            val itemView = layoutInflater.inflate(R.layout.item_geofence, llGeofences, false)
            
            // Find views in the item
            val tvName = itemView.findViewById<TextView>(R.id.tvGeofenceName)
            val tvLocation = itemView.findViewById<TextView>(R.id.tvGeofenceLocation)
            val tvRadius = itemView.findViewById<TextView>(R.id.tvGeofenceRadius)
            val tvChatId = itemView.findViewById<TextView>(R.id.tvGeofenceChatId)
            val tvMessage = itemView.findViewById<TextView>(R.id.tvGeofenceMessage)
            val switchEnabled = itemView.findViewById<Switch>(R.id.switchGeofenceEnabled)
            val btnLocate = itemView.findViewById<Button>(R.id.btnLocateGeofence)
            val btnDelete = itemView.findViewById<Button>(R.id.btnDeleteGeofence)
            
            // Set data
            tvName.text = geofence.name
            tvLocation.text = String.format("%.6f, %.6f", geofence.latitude, geofence.longitude)
            tvRadius.text = "${geofence.radius.toInt()}m"
            tvChatId.text = if (geofence.chatId?.isNotEmpty() == true) {
                "Chat: ${geofence.chatId}"
            } else {
                "Chat: Default"
            }
            tvMessage.text = geofence.enterMessage
            switchEnabled.isChecked = geofence.isEnabled
            
            // Set listeners
            switchEnabled.setOnCheckedChangeListener { _, _ ->
                toggleGeofence(geofence)
            }
            
            btnLocate.setOnClickListener {
                locateGeofence(geofence)
            }
            
            btnDelete.setOnClickListener {
                deleteGeofence(geofence)
            }
            
            // Add the view to the container
            llGeofences.addView(itemView)
        }
    }
    
    private fun showGeofencesOnMap() {
        val geofences = geofenceManager.getSavedGeofences()
        
        // Clear existing geofence markers and circles
        geofenceCircles.values.forEach { it.remove() }
        geofenceMarkers.values.forEach { it.remove() }
        geofenceCircles.clear()
        geofenceMarkers.clear()
        
        // Add geofences to map
        geofences.forEach { geofence ->
            val latLng = LatLng(geofence.latitude, geofence.longitude)
            
            // Add marker
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(geofence.name)
                    .snippet("Radius: ${geofence.radius}m")
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        if (geofence.isEnabled) BitmapDescriptorFactory.HUE_GREEN 
                        else BitmapDescriptorFactory.HUE_RED
                    ))
            )
            
            // Add circle
            val circle = googleMap?.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(geofence.radius.toDouble())
                    .strokeColor(if (geofence.isEnabled) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
                    .fillColor(if (geofence.isEnabled) 0x3300FF00 else 0x33FF0000)
                    .strokeWidth(2f)
            )
            
            if (marker != null) geofenceMarkers[geofence.id] = marker
            if (circle != null) geofenceCircles[geofence.id] = circle
        }
    }
    
    private fun deleteGeofence(geofenceData: GeofenceData) {
        geofenceManager.removeGeofence(
            geofenceId = geofenceData.id,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Geofence deleted", Toast.LENGTH_SHORT).show()
                    loadGeofences()
                    showGeofencesOnMap()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to delete geofence: $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    
    private fun toggleGeofence(geofenceData: GeofenceData) {
        geofenceManager.updateGeofenceStatus(geofenceData.id, !geofenceData.isEnabled)
        loadGeofences()
        showGeofencesOnMap()
    }
    
    private fun locateGeofence(geofenceData: GeofenceData) {
        val latLng = LatLng(geofenceData.latitude, geofenceData.longitude)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
    }
    
    private fun showBotSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bot_settings, null)
        val etBotToken = dialogView.findViewById<EditText>(R.id.etBotToken)
        val etDefaultChatId = dialogView.findViewById<EditText>(R.id.etDefaultChatId)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        
        // Load current values
        val telegramService = TelegramService(this)
        etBotToken.setText(telegramService.getBotToken() ?: "")
        etDefaultChatId.setText(telegramService.getChatId() ?: "")
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_LLMAPP)
            .setView(dialogView)
            .setCancelable(true)
            .create()
            
        // Set dialog background
        dialog.window?.setBackgroundDrawableResource(R.drawable.app_background_charcoal)
        
        btnSave.setOnClickListener {
            val botToken = etBotToken.text.toString().trim()
            val chatId = etDefaultChatId.text.toString().trim()
            
            if (botToken.isEmpty()) {
                Toast.makeText(this, "Bot token is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (chatId.isEmpty()) {
                Toast.makeText(this, "Default chat ID is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save the configuration
            telegramService.saveBotToken(botToken)
            telegramService.setChatId(chatId)
            
            // Update the hint setup for the main form
            setupChatIdHint()
            
            Toast.makeText(this, "Bot configuration saved successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
