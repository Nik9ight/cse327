package com.example.llmapp.workflows.services

import android.content.Context
import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.TelegramService
import com.example.llmapp.Model
import com.example.llmapp.llmChatView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enhanced image processing service that coordinates the entire pipeline
 * Follows Open/Closed Principle - extensible for new image processing types
 */
class EnhancedImageProcessingService(
    private val context: Context,
    private val telegramService: TelegramService,
    private val gmailService: GmailService,
    private val llmChatView: llmChatView
) {
    companion object {
        private const val TAG = "EnhancedImageProcessingService"
    }
    
    private val imageClassificationService = ImageClassificationService(context)
    private val documentAnalysisService = DocumentAnalysisService(context, llmChatView)
    private val documentStorageService = DocumentStorageService(context)
    private val faceMatchingService = FaceMatchingService(context)
    private val referenceImageStorage = SharedPreferencesReferenceStorage(context)
    private val referenceImageManager = ReferenceImageManager(faceMatchingService, referenceImageStorage)
    
    /**
     * Ensure Gmail service is properly initialized for background operation
     */
    private fun ensureGmailServiceInitialized(): Boolean {
        return try {
            Log.d(TAG, "Checking Gmail service initialization status")
            
            // Check if already signed in and initialized
            if (gmailService.isSignedIn()) {
                Log.d(TAG, "Gmail service already initialized")
                return true
            }
            
            // Check if user has signed in via the main app
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                Log.d(TAG, "Found signed-in Google account: ${account.email}")
                
                // Check if account has required Gmail permissions
                val hasGmailScopes = com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(
                    account,
                    com.google.android.gms.common.api.Scope(com.google.api.services.gmail.GmailScopes.GMAIL_READONLY),
                    com.google.android.gms.common.api.Scope(com.google.api.services.gmail.GmailScopes.GMAIL_MODIFY),
                    com.google.android.gms.common.api.Scope(com.google.api.services.gmail.GmailScopes.GMAIL_SEND)
                )
                
                if (hasGmailScopes) {
                    Log.d(TAG, "Account has required Gmail scopes, initializing service")
                    // Initialize the Gmail client with the existing account using reflection
                    // This is needed because we can't call activity-dependent methods in background service
                    try {
                        val setupMethod = gmailService::class.java.getDeclaredMethod("setupGmailClient", com.google.android.gms.auth.api.signin.GoogleSignInAccount::class.java)
                        setupMethod.isAccessible = true
                        setupMethod.invoke(gmailService, account)
                        
                        Log.d(TAG, "Gmail service initialized successfully in background service")
                        return gmailService.isSignedIn()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize Gmail service via reflection: ${e.message}")
                        return false
                    }
                } else {
                    Log.w(TAG, "Account does not have required Gmail scopes")
                    return false
                }
            } else {
                Log.w(TAG, "No signed-in Google account found")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Gmail service initialization", e)
            false
        }
    }
    
    /**
     * Process a new image through the complete pipeline based on workflow type
     */
    suspend fun processImage(imagePath: String, workflowConfig: ImageWorkflowConfig) {
        try {
            Log.d(TAG, "Processing image: $imagePath")
            Log.d(TAG, "Workflow type: ${workflowConfig.workflowType}, name: ${workflowConfig.workflowName}")
            
            when (workflowConfig.workflowType) {
                WorkflowType.IMAGE_FORWARD -> {
                    // For image forward: check if image has person, then check similarity
                    val classification = imageClassificationService.classifyImage(imagePath)
                    if (classification == ImageClassification.PERSON) {
                        processPersonForForwarding(imagePath, workflowConfig)
                    } else {
                        Log.d(TAG, "Image is not a person, skipping for IMAGE_FORWARD workflow")
                    }
                }
                
                WorkflowType.DOCUMENT_ANALYSIS -> {
                    // For document analysis: check if image is document, then analyze
                    val classification = imageClassificationService.classifyImage(imagePath)
                    if (classification == ImageClassification.DOCUMENT) {
                        processDocumentForAnalysis(imagePath, workflowConfig)
                    } else {
                        Log.d(TAG, "Image is not a document, skipping for DOCUMENT_ANALYSIS workflow")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: $imagePath", e)
        }
    }
    
    /**
     * Process person image for forwarding workflow - check similarity with reference and forward if match
     */
    private suspend fun processPersonForForwarding(imagePath: String, config: ImageWorkflowConfig) {
        try {
            Log.d(TAG, "Processing person image for forwarding: $imagePath")
            
            // Check if we have a reference image for this workflow
            if (config.referenceImagePath.isEmpty()) {
                Log.w(TAG, "No reference image set for workflow ${config.workflowId}")
                return
            }
            
            // Check if reference image file exists before trying to use it
            val referenceFile = java.io.File(config.referenceImagePath)
            if (!referenceFile.exists()) {
                Log.e(TAG, "Reference image file does not exist: ${config.referenceImagePath}")
                Log.e(TAG, "Skipping person detection for workflow ${config.workflowId}")
                return
            }
            
            // Check if reference image is already set and valid
            val hasValidReference = referenceImageManager.hasReferenceImage(config.workflowId)
            
            if (!hasValidReference) {
                // Set the reference image for comparison (user uploaded during workflow creation)
                val referenceSet = referenceImageManager.setReferenceImage(config.workflowId, config.referenceImagePath)
                if (!referenceSet) {
                    Log.e(TAG, "Failed to set reference image for workflow ${config.workflowId}")
                    return
                }
            }
            
            // Check similarity with reference image
            val isMatch = referenceImageManager.isReferencePresentInImage(config.workflowId, imagePath)
            
            if (isMatch) {
                Log.d(TAG, "Person matches reference image, forwarding")
                forwardPersonImage(imagePath, config)
            } else {
                Log.d(TAG, "Person does not match reference image, not forwarding")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing person image for forwarding", e)
        }
    }
    
    /**
     * Process document for analysis workflow - analyze with LLM using user-specified type and store result
     */
    private suspend fun processDocumentForAnalysis(imagePath: String, config: ImageWorkflowConfig) {
        try {
            Log.d(TAG, "Processing document for analysis: $imagePath")
            
            // Create a custom prompt using user-specified document type
            val customPrompt = "Make a very concise report on this picture only if it is a '${config.documentType}'"
            
            // Analyze document directly with LLM using image and custom prompt
            val analysisResult = documentAnalysisService.analyzeDocumentImage(
                imagePath = imagePath,
                documentType = config.documentType,
                customPrompt = customPrompt,
                workflowId = config.workflowId
            )
            
            // Store the analysis result for later daily summary
            val saved = documentStorageService.saveAnalysisForWorkflow(analysisResult, config.workflowId)
            if (saved) {
                Log.d(TAG, "Document analysis saved successfully for workflow ${config.workflowId}")
            } else {
                Log.e(TAG, "Failed to save document analysis for workflow ${config.workflowId}")
            }
            
            Log.d(TAG, "Document analysis completed and stored, will be included in daily summary at ${config.scheduledTime}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing document for analysis", e)
        }
    }
    
    /**
     * Forward person image to configured destination when similarity match is found
     */
    private fun forwardPersonImage(imagePath: String, config: ImageWorkflowConfig) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val caption = "✅ Person Match Detected\n📷 Workflow: ${config.workflowName}\n🕐 ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
                
                when (config.destinationType) {
                    DestinationType.TELEGRAM -> {
                        if (telegramService.isLoggedIn() && config.telegramChatId.isNotEmpty()) {
                            telegramService.sendPhoto(
                                imagePath = imagePath,
                                caption = caption,
                                onSuccess = {
                                    Log.d(TAG, "Person image sent via Telegram successfully")
                                },
                                onError = { error ->
                                    Log.e(TAG, "Failed to send person image via Telegram: $error")
                                }
                            )
                        } else {
                            Log.w(TAG, "Telegram not configured or not logged in")
                        }
                    }
                    
                    DestinationType.GMAIL -> {
                        if (config.gmailRecipient.isNotEmpty()) {
                            // Ensure Gmail service is initialized for background operation
                            val isGmailReady = ensureGmailServiceInitialized()
                            
                            if (isGmailReady && gmailService.isSignedIn()) {
                                gmailService.sendEmailWithImage(
                                    to = listOf(config.gmailRecipient),
                                    subject = "Person Match Detected - ${config.workflowName}",
                                    body = """
                                        <h3>Person Match Detected</h3>
                                        <p><strong>Workflow:</strong> ${config.workflowName}</p>
                                        <p><strong>Time:</strong> ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}</p>
                                        <p><strong>Status:</strong> Person matches your reference image</p>
                                        <p><strong>Detected Image:</strong> See attached image</p>
                                        <br>
                                        <p><em>This is an automated notification from LLMAPP Image Workflow</em></p>
                                    """.trimIndent(),
                                    imagePath = imagePath,
                                    isHtml = true
                                )
                                Log.d(TAG, "Person image and notification sent via Gmail successfully to ${config.gmailRecipient}")
                            } else {
                                Log.e(TAG, "Gmail service not ready - User needs to sign in via the main app first")
                                Log.e(TAG, "Gmail configuration: isSignedIn=${gmailService.isSignedIn()}, hasRecipient=${config.gmailRecipient.isNotEmpty()}")
                            }
                        } else {
                            Log.w(TAG, "Gmail recipient not configured")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding person image", e)
            }
        }
    }
    
//    private fun getCurrentDateString(): String {
//        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
//        return formatter.format(java.util.Date())
//    }
    
    /**
     * Generate and send daily summary report for a specific workflow
     */
    suspend fun generateDailySummaryForWorkflow(config: ImageWorkflowConfig, date: String = getCurrentDateString()) {
        try {
            Log.d(TAG, "Generating daily summary report for workflow ${config.workflowName} on $date")
            Log.d(TAG, "Workflow config - ID: ${config.workflowId}, Type: ${config.workflowType}")
            Log.d(TAG, "Destination config - Type: ${config.destinationType}, Gmail: '${config.gmailRecipient}', Telegram: '${config.telegramChatId}'")
            
            // Only process document analysis workflows
            if (config.workflowType != WorkflowType.DOCUMENT_ANALYSIS) {
                Log.d(TAG, "Workflow ${config.workflowName} is not a document analysis workflow, skipping daily summary")
                return
            }
            
            val analyses = documentStorageService.getAnalysesForWorkflowAndDate(config.workflowId, date)
            if (analyses.isEmpty()) {
                Log.d(TAG, "No analyses found for workflow ${config.workflowName} on $date, skipping daily summary")
                return
            }
            
            val summary = documentAnalysisService.generateDailySummary(analyses, date)
            
            val subject = "Daily ${config.documentType} Summary - ${config.workflowName} - $date"
            val content = """
                <h2>Daily Document Summary Report</h2>
                <p><strong>Workflow:</strong> ${config.workflowName}</p>
                <p><strong>Document Type:</strong> ${config.documentType}</p>
                <p><strong>Date:</strong> $date</p>
                <p><strong>Documents Processed:</strong> ${analyses.size}</p>
                <hr>
                <h3>AI Generated Summary</h3>
                <p>${summary.replace("\n", "<br>")}</p>
                <hr>
                <h3>Individual Document Analyses</h3>
                ${analyses.mapIndexed { index, analysis ->
                    """
                    <h4>${index + 1}. ${analysis.documentType} Analysis</h4>
                    <p><strong>Time:</strong> ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(analysis.timestamp))}</p>
                    <p>${analysis.analysis.replace("\n", "<br>")}</p>
                    """.trimIndent()
                }.joinToString("<hr>")}
            """.trimIndent()
            
            // Send to configured destination
            when (config.destinationType) {
                DestinationType.TELEGRAM -> {
                    if (telegramService.isLoggedIn() && config.telegramChatId.isNotEmpty()) {
                        val telegramMessage = """
                            📊 *Daily ${config.documentType} Summary*
                            📋 Workflow: ${config.workflowName}
                            📅 Date: $date
                            � Documents: ${analyses.size}
                            
                            $summary
                        """.trimIndent()
                        
                        telegramService.sendMessage(
                            text = telegramMessage,
                            onSuccess = {
                                Log.d(TAG, "Daily summary sent via Telegram successfully")
                            },
                            onError = { error ->
                                Log.e(TAG, "Failed to send daily summary via Telegram: $error")
                            }
                        )
                    }
                }
                
                DestinationType.GMAIL -> {
                    Log.d(TAG, "Gmail destination selected. Recipient: '${config.gmailRecipient}', isEmpty: ${config.gmailRecipient.isEmpty()}")
                    
                    if (config.gmailRecipient.isNotEmpty()) {
                        // Validate email format before attempting to send
                        if (!isValidEmailAddress(config.gmailRecipient)) {
                            Log.e(TAG, "Invalid email address format: '${config.gmailRecipient}' - looks like Telegram chat ID")
                            Log.e(TAG, "Skipping Gmail delivery due to invalid recipient address")
                            return
                        }
                        
                        // Ensure Gmail service is initialized for background operation
                        val isGmailReady = ensureGmailServiceInitialized()
                        
                        if (isGmailReady && gmailService.isSignedIn()) {
                            gmailService.sendEmail(
                                to = listOf(config.gmailRecipient),
                                subject = subject,
                                body = content,
                                isHtml = true
                            )
                            Log.d(TAG, "Daily summary sent via Gmail successfully to ${config.gmailRecipient}")
                        } else {
                            Log.e(TAG, "Gmail service not ready for daily summary - User needs to sign in via the main app first")
                        }
                    } else {
                        Log.w(TAG, "Gmail recipient not configured for daily summary")
                    }
                }
            }
            
            // Clear processed analyses after sending summary
            documentStorageService.clearAnalysesForWorkflowAndDate(config.workflowId, date)
            Log.d(TAG, "Analyses cleared for workflow ${config.workflowName} on $date after sending summary")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily summary report for workflow ${config.workflowName}", e)
        }
    }
    
    private fun getCurrentDateString(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return formatter.format(java.util.Date())
    }
    
    /**
     * Validate email address format to prevent Gmail API errors
     */
    private fun isValidEmailAddress(email: String): Boolean {
        // Basic email validation - check for @ symbol and basic structure
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        val regex = Regex(emailPattern)
        
        // Also check if it looks like a Telegram chat ID (starts with - and is all digits)
        val isTelegramChatId = email.startsWith("-") && email.substring(1).all { it.isDigit() }
        
        return regex.matches(email) && !isTelegramChatId
    }
}

/**
 * Configuration for image workflow - simplified for user requirements
 */
data class ImageWorkflowConfig(
    // Workflow metadata
    val workflowName: String = "Image Workflow",
    val workflowId: String = java.util.UUID.randomUUID().toString(),
    val workflowType: WorkflowType = WorkflowType.IMAGE_FORWARD,
    val isStarred: Boolean = false,
    
    // Image Forward settings (when workflowType == IMAGE_FORWARD)
    val referenceImagePath: String = "", // Single reference image uploaded by user
    
    // Document Analysis settings (when workflowType == DOCUMENT_ANALYSIS)
    val documentType: String = "document", // User-specified type (receipts, prescriptions, id, etc.)
    val scheduledTime: String = "18:00", // User-selected time for daily summary
    
    // Destination settings (applies to both types)
    val destinationType: DestinationType = DestinationType.GMAIL,
    val gmailRecipient: String = "", // For Gmail destination
    val telegramBotToken: String = "", // For Telegram destination - bot token
    val telegramChatId: String = "" // For Telegram destination - chat ID
)

/**
 * Workflow types as per user requirements
 */
enum class WorkflowType {
    IMAGE_FORWARD, // Forward person images if they match reference
    DOCUMENT_ANALYSIS // Analyze documents and send daily summary
}

/**
 * Destination types for sending results
 */
enum class DestinationType {
    GMAIL,
    TELEGRAM
}
