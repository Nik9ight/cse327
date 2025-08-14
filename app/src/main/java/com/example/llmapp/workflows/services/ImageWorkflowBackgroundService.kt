package com.example.llmapp.workflows.services

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.llmapp.R
import com.example.llmapp.TelegramService
import com.example.llmapp.GmailService
import com.example.llmapp.MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1
import com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT
import com.example.llmapp.Model
import com.example.llmapp.llmChatView
import com.example.llmapp.workflows.services.ImageWorkflowConfig
import com.example.llmapp.workflows.services.WorkflowType
import com.example.llmapp.workflows.services.WorkflowConfigManager
import com.example.llmapp.workflows.schedulers.DailySummaryScheduler
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced background service to monitor camera folder for new images
 * Integrates with the complete image processing pipeline following SOLID principles
 */
class ImageWorkflowBackgroundService : Service() {
    
    companion object {
        private const val TAG = "ImageWorkflowService"
        private const val NOTIFICATION_CHANNEL_ID = "image_workflow_channel"
        private const val NOTIFICATION_ID = 2001
        private val MONITORED_EXTENSIONS = listOf(".jpg", ".jpeg", ".png")
        private const val PROCESSING_DELAY = 3000L // 3 seconds delay to ensure file is fully written
    }
    
    private var fileObserver: FileObserver? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processingQueue = mutableSetOf<String>() // To avoid duplicate processing
    
    // Enhanced services
    private lateinit var workflowConfigManager: WorkflowConfigManager
    private lateinit var enhancedImageProcessingService: EnhancedImageProcessingService
    private lateinit var dailySummaryScheduler: DailySummaryScheduler
    private lateinit var telegramService: TelegramService
    private lateinit var gmailService: GmailService
    private lateinit var llmChatView: llmChatView
    
    // LLM Model
    private val model = MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = true)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Enhanced Image Workflow Background Service created")
        
        // Initialize services
        initializeServices()
        
        createNotificationChannel()
        startImageMonitoring()
        startDailySummaryScheduler()
    }
    
    private fun initializeServices() {
        try {
            workflowConfigManager = WorkflowConfigManager(this)
            telegramService = TelegramService(this)
            gmailService = GmailService(this)
            llmChatView = llmChatView(model)
            
            // Initialize LLM with proper callback handling
            llmChatView.initialize(this) { error ->
                try {
                    if (error.isNotEmpty()) {
                        Log.e(TAG, "LLM initialization error: $error")
                        // Continue with service initialization even if LLM fails
                    } else {
                        Log.d(TAG, "LLM initialized successfully")
                    }
                    
                    // Initialize daily summary scheduler immediately after LLM is ready
                    // This ensures daily summaries work even without image processing
                    initializeDailySummaryScheduler()
                    
                    // Note: EnhancedImageProcessingService will be initialized lazily when first image is processed
                    // This avoids ML Kit context initialization issues in background service startup
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in LLM initialization callback", e)
                }
            }
            
            Log.d(TAG, "Basic services initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing services", e)
        }
    }
    
    /**
     * Initialize the daily summary scheduler early so it can run independently
     */
    private fun initializeDailySummaryScheduler() {
        try {
            if (!::dailySummaryScheduler.isInitialized) {
                Log.d(TAG, "Initializing daily summary scheduler")
                
                // Create a temporary enhanced image processing service just for daily summaries
                // This allows the scheduler to work even without image processing
                val tempEnhancedService = EnhancedImageProcessingService(
                    context = this,
                    telegramService = telegramService,
                    gmailService = gmailService,
                    llmChatView = llmChatView
                )
                
                dailySummaryScheduler = DailySummaryScheduler(
                    context = this,
                    imageProcessingService = tempEnhancedService,
                    workflowConfigManager = workflowConfigManager
                )
                
                startDailySummaryScheduler()
                Log.d(TAG, "Daily summary scheduler initialized and started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing daily summary scheduler", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Enhanced Image Workflow Service started")
        
        // Show persistent notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Enhanced Image Workflow Service destroyed")
        fileObserver?.stopWatching()
        if (::dailySummaryScheduler.isInitialized) {
            dailySummaryScheduler.stopScheduler()
        }
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Image Workflow Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors images for person detection"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val activeConfigs = workflowConfigManager.getAllWorkflows()
        
        val workflowText = if (activeConfigs.isEmpty()) {
            "No active workflows"
        } else {
            val imageForwardCount = activeConfigs.count { it.workflowType == WorkflowType.IMAGE_FORWARD }
            val documentAnalysisCount = activeConfigs.count { it.workflowType == WorkflowType.DOCUMENT_ANALYSIS }
            
            buildString {
                if (imageForwardCount > 0) append("Image Forward: $imageForwardCount ")
                if (documentAnalysisCount > 0) append("Document Analysis: $documentAnalysisCount")
            }.trim().ifEmpty { "Monitoring active" }
        }
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Enhanced Image Workflow")
            .setContentText(workflowText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startImageMonitoring() {
        try {
            // Check permissions first
            if (!hasRequiredPermissions()) {
                Log.e(TAG, "Missing required permissions for image monitoring")
                return
            }
            
            // Monitor the DCIM/Camera directory where most camera apps save photos
            val cameraDir = File(Environment.getExternalStorageDirectory(), "DCIM/Camera")
            
            if (!cameraDir.exists()) {
                Log.w(TAG, "Camera directory does not exist: ${cameraDir.absolutePath}")
                // Fallback to Pictures directory
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (picturesDir.exists()) {
                    monitorDirectory(picturesDir.absolutePath)
                } else {
                    Log.e(TAG, "No suitable directory found for monitoring")
                }
                return
            }
            
            monitorDirectory(cameraDir.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting image monitoring", e)
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Check for READ_MEDIA_IMAGES
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 and below: Check for READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun monitorDirectory(directoryPath: String) {
        Log.d(TAG, "Starting to monitor directory: $directoryPath")
        
        fileObserver = object : FileObserver(directoryPath, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                
                val fullPath = "$directoryPath/$path"
                
                // Check if it's an image file
                if (MONITORED_EXTENSIONS.any { path.lowercase().endsWith(it) }) {
                    Log.d(TAG, "New image detected: $fullPath")
                    
                    // Avoid processing the same file multiple times
                    synchronized(processingQueue) {
                        if (processingQueue.contains(fullPath)) {
                            return
                        }
                        processingQueue.add(fullPath)
                    }
                    
                    // Process with delay to ensure file is fully written
                    serviceScope.launch {
                        delay(PROCESSING_DELAY)
                        processNewImage(fullPath)
                        
                        // Remove from processing queue
                        synchronized(processingQueue) {
                            processingQueue.remove(fullPath)
                        }
                    }
                }
            }
        }
        
        fileObserver?.startWatching()
        Log.d(TAG, "File observer started for directory: $directoryPath")
    }
    
    /**
     * Process a new image through the enhanced pipeline
     */
    private suspend fun processNewImage(imagePath: String) {
        try {
            Log.d(TAG, "Processing new image: $imagePath")
            
            // Initialize services lazily when first image is processed to avoid ML Kit context issues
            if (!::enhancedImageProcessingService.isInitialized) {
                Log.d(TAG, "Initializing enhanced image processing service for first image")
                try {
                    enhancedImageProcessingService = EnhancedImageProcessingService(
                        context = this,
                        telegramService = telegramService,
                        gmailService = gmailService,
                        llmChatView = llmChatView
                    )
                    
                    // Daily summary scheduler should already be initialized, just ensure it's running
                    if (!::dailySummaryScheduler.isInitialized) {
                        Log.w(TAG, "Daily summary scheduler not initialized during service startup, initializing now")
                        initializeDailySummaryScheduler()
                    }
                    
                    Log.d(TAG, "Enhanced image processing service initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize enhanced image processing service", e)
                    return
                }
            }
            
            // Get all workflow configurations
            val activeConfigs = workflowConfigManager.getAllWorkflows()
            
            if (activeConfigs.isEmpty()) {
                Log.d(TAG, "No active workflows configured, skipping image processing")
                return
            }
            
            // Process image with each active workflow configuration
            activeConfigs.forEach { config ->
                try {
                    Log.d(TAG, "Processing image with workflow: ${config.workflowName}")
                    enhancedImageProcessingService.processImage(imagePath, config)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing image with workflow ${config.workflowName}", e)
                }
            }
            
            // Update notification to show processing activity
            updateNotificationWithActivity()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing new image: $imagePath", e)
        }
    }
    
    /**
     * Start the daily summary scheduler
     */
    private fun startDailySummaryScheduler() {
        try {
            if (::dailySummaryScheduler.isInitialized) {
                dailySummaryScheduler.startScheduler()
                Log.d(TAG, "Daily summary scheduler started")
            } else {
                Log.d(TAG, "Daily summary scheduler not yet initialized, will start when ready")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting daily summary scheduler", e)
        }
    }
    
    /**
     * Update notification to show recent activity
     */
    private fun updateNotificationWithActivity() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = createNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
}

// Extension properties for FileObserver constants (for older Android versions compatibility)
private const val CREATE = 0x100
private const val MOVED_TO = 0x80