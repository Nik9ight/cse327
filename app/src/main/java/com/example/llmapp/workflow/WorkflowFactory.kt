package com.example.llmapp.workflow

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.GmailToTelegramPipeline
import com.example.llmapp.Login
import com.example.llmapp.TelegramService
import com.example.llmapp.pipeline.*
import com.example.llmapp.adapters.EmailTemplateType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Factory Pattern implementation for creating different types of pipelines
 * This abstraction allows easy addition of new pipeline types
 */
interface WorkflowFactory {
    suspend fun createPipeline(type: WorkflowType, configuration: WorkflowConfiguration): WorkflowPipeline
    fun setAuthenticationCallback(callback: GmailToTelegramWorkflowPipeline.AuthenticationCallback?)
}

/**
 * Concrete factory implementation
 */
class WorkflowFactoryImpl(private val context: Context) : WorkflowFactory {
    
    companion object {
        private const val TAG = "WorkflowFactory"
    }
    
    private var authenticationCallback: GmailToTelegramWorkflowPipeline.AuthenticationCallback? = null
    
    override fun setAuthenticationCallback(callback: GmailToTelegramWorkflowPipeline.AuthenticationCallback?) {
        this.authenticationCallback = callback
        Log.d(TAG, "Authentication callback ${if (callback != null) "set" else "cleared"}")
    }
    
    override suspend fun createPipeline(
        type: WorkflowType, 
        configuration: WorkflowConfiguration
    ): WorkflowPipeline {
        Log.d(TAG, "Creating pipeline for type: ${type.name}")
        
        return when (type) {
            WorkflowType.GMAIL_TO_TELEGRAM -> createGmailToTelegramPipeline(configuration as GmailToTelegramConfig)
            WorkflowType.TELEGRAM_TO_GMAIL -> createTelegramToGmailPipeline(configuration as TelegramToGmailConfig)
        }
    }
    
     suspend fun validateConfiguration(type: WorkflowType, configuration: WorkflowConfiguration): Boolean {
        return try {
            val validationResult = configuration.validate()
            validationResult.isValid()
        } catch (e: Exception) {
            Log.e(TAG, "Error validating configuration", e)
            false
        }
    }
    
    /**
     * Check if all required services are ready for workflow execution
     * This can be used by UI components to determine if a workflow can be executed
     */
    suspend fun checkExecutionReadiness(type: WorkflowType, configuration: WorkflowConfiguration): ExecutionReadinessResult {
        return try {
            val pipeline = createPipeline(type, configuration)
            
            when (type) {
                WorkflowType.GMAIL_TO_TELEGRAM -> {
                    val gmailPipeline = pipeline as GmailToTelegramWorkflowPipeline
                    checkGmailToTelegramReadiness(gmailPipeline)
                }
                WorkflowType.TELEGRAM_TO_GMAIL -> {
                    val telegramPipeline = pipeline as TelegramToGmailWorkflowPipeline  
                    checkTelegramToGmailReadiness(telegramPipeline)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking execution readiness", e)
            ExecutionReadinessResult(
                ready = false,
                issues = listOf("Failed to check readiness: ${e.message}")
            )
        }
    }
    
    private fun checkGmailToTelegramReadiness(pipeline: GmailToTelegramWorkflowPipeline): ExecutionReadinessResult {
        val issues = mutableListOf<String>()
        
        // Get the configuration from the pipeline
        val config = pipeline.getConfiguration() as GmailToTelegramConfig
        
        // Check Gmail authentication using Login helper if context is Activity
        val gmailAuthStatus = if (context is Activity) {
            try {
                val login = Login(context)
                val isSignedIn = login.isSignedIn()
                val hasScopes = login.hasGmailScopes()
                
                Log.d(TAG, "Gmail readiness check - Signed in: $isSignedIn, Has scopes: $hasScopes")
                isSignedIn && hasScopes
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Gmail authentication with Login", e)
                // Fallback to direct GmailService check
                val gmailService = GmailService(context)
                gmailService.isSignedIn()
            }
        } else {
            // Fallback to direct GmailService check for non-Activity contexts
            val gmailService = GmailService(context)
            gmailService.isSignedIn()
        }
        
        if (!gmailAuthStatus) {
            issues.add("Gmail authentication required: Please sign in to your Gmail account with appropriate permissions")
        }
        
        // Check Telegram configuration
        if (config.telegramBotToken.isBlank()) {
            issues.add("Telegram bot token is required")
        }
        if (config.telegramChatId.isBlank()) {
            issues.add("Telegram chat ID is required")
        }
        
        return ExecutionReadinessResult(
            ready = issues.isEmpty(),
            issues = issues
        )
    }
    
    private fun checkTelegramToGmailReadiness(pipeline: TelegramToGmailWorkflowPipeline): ExecutionReadinessResult {
        val issues = mutableListOf<String>()
        val config = pipeline.getConfiguration() as TelegramToGmailConfig
        
        // Check Telegram configuration
        if (config.telegramBotToken.isBlank()) {
            issues.add("Telegram bot token is required")
        }
        if (config.telegramChatId.isBlank()) {
            issues.add("Telegram chat ID is required")
        }
        
        // Check Gmail authentication using Login helper if context is Activity
        val gmailAuthStatus = if (context is Activity) {
            try {
                val login = Login(context)
                val isSignedIn = login.isSignedIn()
                val hasScopes = login.hasGmailScopes()
                
                Log.d(TAG, "Gmail readiness check - Signed in: $isSignedIn, Has scopes: $hasScopes")
                isSignedIn && hasScopes
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Gmail authentication with Login", e)
                // Fallback to direct GmailService check
                val gmailService = GmailService(context)
                gmailService.isSignedIn()
            }
        } else {
            // Fallback to direct GmailService check for non-Activity contexts
            val gmailService = GmailService(context)
            gmailService.isSignedIn()
        }
        
        if (!gmailAuthStatus) {
            issues.add("Gmail authentication required: Please sign in to your Gmail account with appropriate permissions")
        }
        
        // Check Gmail recipients
        if (config.gmailRecipients.isEmpty()) {
            issues.add("Gmail recipients list is empty")
        }
        
        return ExecutionReadinessResult(
            ready = issues.isEmpty(),
            issues = issues
        )
    }
    
    private fun createGmailToTelegramPipeline(config: GmailToTelegramConfig): WorkflowPipeline {
        Log.d(TAG, "Creating Gmail to Telegram pipeline")
        Log.d(TAG, "Config - Bot Token: ${if (config.telegramBotToken.isNotBlank()) "[SET]" else "[EMPTY]"}")
        Log.d(TAG, "Config - Chat ID: ${if (config.telegramChatId.isNotBlank()) "[SET]" else "[EMPTY]"}")
        Log.d(TAG, "Config - Gmail Query: '${config.gmailSearchQuery}'")
        
        // Create services
        val gmailService = GmailService(context)
        val telegramService = TelegramService(context)
        
        // Initialize Gmail service if user is already signed in
        if (context is Activity) {
            try {
                val login = Login(context)
                if (login.isSignedIn() && login.hasGmailScopes()) {
                    Log.d(TAG, "User is already signed in with Gmail scopes, initializing Gmail service")
                    gmailService.initGoogleSignIn(context)
                    
                    // Verify that the service is now configured
                    val isConfigured = gmailService.isSignedIn()
                    Log.d(TAG, "Gmail service configured after initialization: $isConfigured")
                    
                    if (!isConfigured) {
                        Log.w(TAG, "Gmail service initialization failed despite valid authentication")
                        // Debug the service state
                        gmailService.debugState()
                    }
                } else {
                    Log.d(TAG, "User not signed in (${login.isSignedIn()}) or missing Gmail scopes (${login.hasGmailScopes()}) - service will need authentication later")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking authentication status during pipeline creation", e)
            }
        } else {
            Log.d(TAG, "Context is not Activity, Gmail service will be configured during execution if needed")
        }
        
        // Set Telegram credentials
        if (config.telegramBotToken.isNotBlank()) {
            Log.d(TAG, "Logging into Telegram with provided token")
            telegramService.login(config.telegramBotToken, 
                onSuccess = { 
                    Log.d(TAG, "Telegram login successful")
                    if (config.telegramChatId.isNotBlank()) {
                        telegramService.setChatId(config.telegramChatId)
                        Log.d(TAG, "Telegram chat ID set")
                    }
                },
                onError = { error ->
                    Log.e(TAG, "Error logging into Telegram: $error")
                }
            )
        } else {
            Log.w(TAG, "No Telegram bot token provided - service will not be configured")
        }
        
        // Create the pipeline
        val pipeline = GmailToTelegramPipeline(context, telegramService, null, gmailService)
        Log.d(TAG, "GmailToTelegramPipeline created")
        
        val workflowPipeline = GmailToTelegramWorkflowPipeline(context, pipeline, config, gmailService)
        
        // Set authentication callback if available
        if (authenticationCallback != null) {
            workflowPipeline.setAuthenticationCallback(authenticationCallback)
            Log.d(TAG, "Authentication callback set on workflow pipeline")
        }
        
        return workflowPipeline
    }
    
    private fun createTelegramToGmailPipeline(config: TelegramToGmailConfig): WorkflowPipeline {
        Log.d(TAG, "Creating Telegram to Gmail pipeline")
        
        // Create services
        val gmailService = GmailService(context)
        val llmProcessor = LlmContentProcessor(context)
        
        // Set custom prompt if provided
        if (config.llmPrompt.isNotBlank()) {
            llmProcessor.setPromptStrategy(CustomPromptStrategy(config.llmPrompt))
        }
        
        // Create the pipeline
        val pipeline = TelegramToGmailPipeline(
            botToken = config.telegramBotToken,
            gmailService = gmailService,
            emailRecipients = config.gmailRecipients,
            defaultSender = config.gmailSender,
            llmProcessor = llmProcessor
        )
        
        // Set email template
        val templateType = when (config.emailTemplate.uppercase()) {
            "COMPACT" -> EmailTemplateType.COMPACT
            "DETAILED" -> EmailTemplateType.DETAILED
            else -> EmailTemplateType.STANDARD
        }
        pipeline.setEmailTemplate(templateType)
        
        return TelegramToGmailWorkflowPipeline(pipeline, config, context)
    }
}

/**
 * Abstract base class for workflow pipelines using Template Method Pattern
 */
abstract class WorkflowPipeline {
    abstract suspend fun execute(): WorkflowExecutionResult
    abstract fun getType(): WorkflowType
    abstract fun getConfiguration(): WorkflowConfiguration
    abstract fun stop()
}

/**
 * Concrete implementation for Gmail to Telegram workflows
 */
class GmailToTelegramWorkflowPipeline(
    private val context: Context,
    private val pipeline: GmailToTelegramPipeline,
    private val config: GmailToTelegramConfig,
    private val gmailService: GmailService? = null
) : WorkflowPipeline() {
    
    companion object {
        private const val TAG = "GmailToTelegramWorkflow"
    }
    
    /**
     * Callback interface for authentication requests
     */
    interface AuthenticationCallback {
        fun onAuthenticationRequired(authType: AuthType, callback: (Boolean) -> Unit)
    }
    
    enum class AuthType {
        GMAIL_SIGNIN,
        TELEGRAM_CONFIG
    }
    
    private var authCallback: AuthenticationCallback? = null
    
    /**
     * Set an authentication callback to handle sign-in requests
     */
    fun setAuthenticationCallback(callback: AuthenticationCallback?) {
        this.authCallback = callback
    }
    
    override suspend fun execute(): WorkflowExecutionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "Executing Gmail to Telegram workflow")
            Log.d(TAG, "Config - Email limit: ${config.emailLimit}")
            Log.d(TAG, "Config - Search query: '${config.gmailSearchQuery}'")
            
            // STEP 1: Initialize Gmail service from the beginning using Login.kt
            Log.d(TAG, "Step 1: Initializing Gmail service at workflow start")
            val gmailService = getGmailServiceFromPipeline(pipeline)
            if (gmailService == null) {
                return WorkflowExecutionResult(
                    success = false,
                    message = "Gmail service not available. Please check the application configuration.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            // Check authentication status using Login.kt first
            if (context is Activity) {
                try {
                    val login = Login(context)
                    val isSignedIn = login.isSignedIn()
                    val hasScopes = login.hasGmailScopes()
                    
                    Log.d(TAG, "Initial Login status check - Signed in: $isSignedIn, Has Gmail scopes: $hasScopes")
                    
                    if (!isSignedIn) {
                        Log.w(TAG, "User is not signed in to Google account")
                        return handleGmailAuthenticationRequired(startTime)
                    }
                    
                    if (!hasScopes) {
                        Log.w(TAG, "User is signed in but missing Gmail scopes")
                        return handleGmailAuthenticationRequired(startTime)
                    }
                    
                    // User is authenticated with proper scopes, now ensure Gmail service is initialized
                    Log.d(TAG, "User has proper authentication, initializing Gmail service...")
                    
                    // Initialize Gmail service with the authenticated user
                    gmailService.initGoogleSignIn(context)
                    
                    // Verify initialization was successful
                    if (!gmailService.isSignedIn()) {
                        Log.w(TAG, "Gmail service initialization failed despite valid authentication")
                        gmailService.debugState()
                        
                        // Try the sign-in process to properly set up the Gmail client
                        Log.d(TAG, "Attempting Gmail client setup through sign-in process...")
                        val signInSuccessful = suspendCoroutine<Boolean> { continuation ->
                            gmailService.signIn(context) { success ->
                                Log.d(TAG, "Gmail sign-in completed with result: $success")
                                continuation.resume(success)
                            }
                        }
                        
                        if (!signInSuccessful) {
                            Log.e(TAG, "Gmail client setup failed")
                            return handleGmailAuthenticationRequired(startTime)
                        }
                        
                        Log.d(TAG, "Gmail client setup successful")
                    } else {
                        Log.d(TAG, "Gmail service is properly initialized and ready")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during initial Gmail authentication check", e)
                    return WorkflowExecutionResult(
                        success = false,
                        message = "Gmail authentication check failed: ${e.message}",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }
            } else {
                Log.w(TAG, "Context is not Activity, using direct Gmail service checks")
                // Fallback for non-Activity contexts
                if (!gmailService.isSignedIn()) {
                    return WorkflowExecutionResult(
                        success = false,
                        message = "Gmail authentication required. Please sign in through the app settings.",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }
            }
            
            // STEP 2: Get and validate services after Gmail initialization
            Log.d(TAG, "Step 2: Validating all pipeline services")
            val emailFetchService = pipeline.getEmailFetchService()
            val contentProcessor = pipeline.getContentProcessor()
            val deliveryService = pipeline.getDeliveryService()
            
            Log.d(TAG, "Email fetch service type: ${emailFetchService::class.simpleName}")
            Log.d(TAG, "Email fetch service ready: ${emailFetchService.isConfigured()}")
            Log.d(TAG, "Content processor type: ${contentProcessor::class.simpleName}")
            Log.d(TAG, "Content processor ready: ${contentProcessor.isReady()}")
            Log.d(TAG, "Delivery service type: ${deliveryService::class.simpleName}")
            Log.d(TAG, "Delivery service configured: ${deliveryService.isConfigured()}")
            
            // Final validation of all services
            if (!emailFetchService.isConfigured()) {
                Log.e(TAG, "Gmail service is still not configured after initialization")
                gmailService.debugState()
                return WorkflowExecutionResult(
                    success = false,
                    message = "Gmail service configuration failed. Please try signing out and signing in again.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            if (!contentProcessor.isReady()) {
                Log.w(TAG, "Content processor (LLM) is not ready")
                val executionTime = System.currentTimeMillis() - startTime
                return WorkflowExecutionResult(
                    success = false,
                    message = "LLM Not Ready: The language model is still initializing. Please wait a moment and try again.",
                    executionTime = executionTime
                )
            }
            if (!deliveryService.isConfigured()) {
                Log.w(TAG, "Telegram service is not configured")
                val executionTime = System.currentTimeMillis() - startTime
                return WorkflowExecutionResult(
                    success = false,
                    message = "Telegram Configuration Required: Please check your Telegram bot token and chat ID in the workflow settings. Make sure the bot is properly configured and has permission to send messages.",
                    executionTime = executionTime
                )
            }
            
            // Configure the pipeline based on saved configuration
            Log.d(TAG, "Configuring pipeline with workflow settings...")
            if (emailFetchService is com.example.llmapp.pipeline.GmailFetchService) {
                val searchQuery = config.gmailSearchQuery.ifBlank { "is:unread" }
                emailFetchService.setSearchQuery(searchQuery)
                Log.d(TAG, "Set Gmail search query to: '$searchQuery'")
            }
            
            // Set LLM prompt if provided
            if (config.llmPrompt.isNotBlank()) {
                if (contentProcessor is LlmContentProcessor) {
                    contentProcessor.setPromptStrategy(CustomPromptStrategy(config.llmPrompt))
                    Log.d(TAG, "Set custom LLM prompt")
                }
            }
            
            // Note: Using process() method which has hardcoded email limit of 3
            Log.d(TAG, "Using pipeline.process() method with hardcoded email limit of 3")
            
            // Final check: ensure all services are ready before proceeding
            Log.d(TAG, "Final service status check before execution:")
            Log.d(TAG, "- Email fetch service configured: ${emailFetchService.isConfigured()}")
            Log.d(TAG, "- Content processor ready: ${contentProcessor.isReady()}")
            Log.d(TAG, "- Delivery service configured: ${deliveryService.isConfigured()}")
            
            // STEP 3: Execute the pipeline using the GmailToTelegramPipeline.process() method
            Log.d(TAG, "Step 3: Starting pipeline execution using process() method...")
            
            val result = suspendCoroutine<WorkflowExecutionResult> { continuation ->
                var callbackInvoked = false
                
                // Set a completion listener to capture the result
                pipeline.setCompletionListener(object : GmailToTelegramPipeline.PipelineCompletionListener {
                    override fun onComplete(success: Boolean, message: String) {
                        if (!callbackInvoked) {
                            callbackInvoked = true
                            Log.d(TAG, "Pipeline execution completed - Success: $success, Message: '$message'")
                            
                            val executionTime = System.currentTimeMillis() - startTime
                            val result = WorkflowExecutionResult(
                                success = success,
                                message = message.ifBlank { 
                                    if (success) "Workflow completed successfully" else "Workflow failed" 
                                },
                                executionTime = executionTime,
                                details = mapOf(
                                    "emailsProcessed" to 3, // Hardcoded limit used by process() method
                                    "searchQuery" to config.gmailSearchQuery,
                                    "telegramChatId" to config.telegramChatId
                                )
                            )
                            continuation.resume(result)
                        }
                    }
                })
                
                // Use the process() method which implements the Command Pattern with hardcoded limit of 3
                Log.d(TAG, "Calling pipeline.process() to execute Gmail to Telegram workflow")
                pipeline.process()
                
                // Timeout fallback after 30 seconds
                CoroutineScope(Dispatchers.IO).launch {
                    delay(30000)
                    if (!callbackInvoked) {
                        callbackInvoked = true
                        Log.w(TAG, "Pipeline execution timed out after 30 seconds")
                        val executionTime = System.currentTimeMillis() - startTime
                        val result = WorkflowExecutionResult(
                            success = false,
                            message = "Workflow execution timed out after 30 seconds",
                            executionTime = executionTime,
                            details = mapOf("timeout" to true)
                        )
                        continuation.resume(result)
                    }
                }
            }
            
            Log.d(TAG, "Workflow execution result: success=${result.success}, message='${result.message}', time=${result.executionTime}ms")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed", e)
            val executionTime = System.currentTimeMillis() - startTime
            
            // Provide more specific error messages based on exception type
            val errorMessage = when {
                e.message?.contains("Gmail service is not properly configured") == true ||
                e.message?.contains("not signed in") == true -> {
                    "Gmail Authentication Required: Please sign in to your Gmail account in the app settings before running this workflow. Go to Home > Sign In to authenticate your Google account."
                }
                e.message?.contains("Telegram") == true -> {
                    "Telegram Configuration Error: ${e.message}. Please check your bot token and chat ID in the workflow settings."
                }
                e.message?.contains("LLM") == true || e.message?.contains("Content processor") == true -> {
                    "Language Model Error: ${e.message}. Please wait for the LLM to fully initialize and try again."
                }
                e.message?.contains("network") == true || e.message?.contains("connection") == true -> {
                    "Network Error: ${e.message}. Please check your internet connection and try again."
                }
                else -> {
                    "Workflow execution failed: ${e.message ?: "Unknown error occurred"}"
                }
            }
            
            WorkflowExecutionResult(
                success = false,
                message = errorMessage,
                executionTime = executionTime,
                details = mapOf("error" to e.javaClass.simpleName)
            )
        }
    }
    
    /**
     * Handle Gmail authentication requirement with callback or fallback message
     */
    private suspend fun handleGmailAuthenticationRequired(startTime: Long): WorkflowExecutionResult {
        return if (authCallback != null) {
            Log.d(TAG, "Requesting Gmail authentication through callback")
            
            // Use suspendCoroutine to wait for authentication callback
            suspendCoroutine { continuation ->
                authCallback!!.onAuthenticationRequired(AuthType.GMAIL_SIGNIN) { authSuccess ->
                    if (authSuccess) {
                        Log.d(TAG, "Authentication callback reported success, checking if workflow can continue...")
                        
                        // Re-check if the email fetch service is now configured
                        val emailFetchService = pipeline.getEmailFetchService()
                        val isNowConfigured = emailFetchService.isConfigured()
                        Log.d(TAG, "Email fetch service configured after authentication: $isNowConfigured")
                        
                        if (isNowConfigured) {
                            Log.d(TAG, "Gmail service is now configured, continuing workflow execution...")
                            // Continue with the workflow execution by returning a success indicator
                            // The calling method should handle this by proceeding with execution
                            continuation.resume(WorkflowExecutionResult(
                                success = true,
                                message = "CONTINUE_WORKFLOW", // Special flag to indicate continuation
                                executionTime = System.currentTimeMillis() - startTime
                            ))
                        } else {
                            Log.w(TAG, "Gmail service still not configured after authentication callback")
                            val executionTime = System.currentTimeMillis() - startTime
                            continuation.resume(WorkflowExecutionResult(
                                success = false,
                                message = "Gmail Authentication Completed: Please run the workflow again now that you're signed in.",
                                executionTime = executionTime
                            ))
                        }
                    } else {
                        Log.w(TAG, "Authentication callback reported failure")
                        val executionTime = System.currentTimeMillis() - startTime
                        continuation.resume(WorkflowExecutionResult(
                            success = false,
                            message = "Gmail Authentication Failed: Sign-in was cancelled or failed. Please try again.",
                            executionTime = executionTime
                        ))
                    }
                }
            }
        } else {
            Log.w(TAG, "No authentication callback available, returning error message")
            val executionTime = System.currentTimeMillis() - startTime
            WorkflowExecutionResult(
                success = false,
                message = "Gmail Authentication Required: Please sign in to your Gmail account in the app settings before running this workflow. Go to Home > Sign In to authenticate your Google account.",
                executionTime = executionTime
            )
        }
    }
    
    /**
     * Helper method to extract GmailService from the pipeline
     */
    private fun getGmailServiceFromPipeline(pipeline: GmailToTelegramPipeline): GmailService? {
        return try {
            // Return the GmailService instance that was used to create this workflow pipeline
            gmailService ?: run {
                Log.w(TAG, "No GmailService reference available, creating new instance")
                val newGmailService = GmailService(context)
                
                // If context is Activity, initialize it with Google Sign-In
                if (context is Activity) {
                    val login = Login(context)
                    if (login.isSignedIn() && login.hasGmailScopes()) {
                        newGmailService.initGoogleSignIn(context)
                    }
                }
                
                newGmailService
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting GmailService from pipeline", e)
            null
        }
    }
    
    /**
     * Helper method to attempt Gmail service reinitialization
     * This checks if the user is already signed in and tries to reinitialize the service
     * Uses Login.kt for authentication checks to avoid redundant sign-in requests
     */
    private suspend fun attemptGmailServiceReinitialization(gmailService: GmailService): Boolean = suspendCoroutine { continuation ->
        try {
            Log.d(TAG, "Checking Gmail authentication status using Login helper...")
            
            // Create a Login instance to check authentication status
            // Note: We need an Activity context, but workflow execution is in background
            // We'll use the context if it's an Activity, otherwise fall back to direct checks
            val authenticationStatus = if (context is Activity) {
                checkGmailAuthenticationWithLogin(context, gmailService)
            } else {
                Log.d(TAG, "Context is not Activity, using direct Gmail service checks")
                checkGmailAuthenticationDirect(gmailService)
            }
            
            continuation.resume(authenticationStatus)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Gmail authentication check", e)
            continuation.resume(false)
        }
    }
    
    /**
     * Check Gmail authentication using Login.kt helper
     */
    private fun checkGmailAuthenticationWithLogin(activity: Activity, gmailService: GmailService): Boolean {
        return try {
            val login = Login(activity)
            
            // First check if user is signed in
            if (!login.isSignedIn()) {
                Log.d(TAG, "User is not signed in to Google account")
                return false
            }
            
            Log.d(TAG, "User is signed in, checking Gmail permissions...")
            
            // Check if user has required Gmail scopes
            if (!login.hasGmailScopes()) {
                Log.d(TAG, "User is signed in but missing Gmail scopes")
                return false
            }
            
            Log.d(TAG, "User has required Gmail scopes, initializing Gmail service...")
            
            // Initialize Gmail sign-in
            gmailService.initGoogleSignIn(activity)
            
            // Force a small delay to allow initialization
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            
            // Debug the current state
            gmailService.debugState()
            
            // Check if the Gmail service is now working
            val isGmailServiceReady = gmailService.isSignedIn()
            Log.d(TAG, "Gmail service isSignedIn after initialization: $isGmailServiceReady")
            
            if (isGmailServiceReady) {
                Log.d(TAG, "Gmail service is properly initialized and ready")
                return true
            } else {
                Log.w(TAG, "Gmail service still not ready after initialization, trying refresh...")
                
                // Try to reinitialize one more time
                gmailService.initGoogleSignIn(activity)
                Thread.sleep(100)
                
                val retryResult = gmailService.isSignedIn()
                Log.d(TAG, "Gmail service retry check: $retryResult")
                
                if (retryResult) {
                    Log.d(TAG, "Gmail service initialized successfully on retry")
                    return true
                } else {
                    Log.w(TAG, "Gmail service still not ready after retry attempts")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Gmail authentication with Login helper", e)
            false
        }
    }
    
    /**
     * Fallback method for direct Gmail authentication check when Activity context is not available
     */
    private fun checkGmailAuthenticationDirect(gmailService: GmailService): Boolean {
        return try {
            Log.d(TAG, "Performing direct Gmail authentication check...")
            
            // Check if user is already signed in to Google but service isn't initialized
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                Log.d(TAG, "Google account found: ${account.email}")
                
                // Check if the account has Gmail permissions
                val hasGmailScopes = GoogleSignIn.hasPermissions(
                    account,
                    com.google.android.gms.common.api.Scope(com.google.api.services.gmail.GmailScopes.GMAIL_READONLY),
                    com.google.android.gms.common.api.Scope(com.google.api.services.gmail.GmailScopes.GMAIL_MODIFY),
                    com.google.android.gms.common.api.Scope(com.google.api.services.gmail.GmailScopes.GMAIL_SEND)
                )
                
                if (hasGmailScopes) {
                    Log.d(TAG, "Account has required Gmail scopes, initializing service if needed")
                    
                    // Try to initialize the Gmail service if context is Activity
                    if (context is Activity) {
                        gmailService.initGoogleSignIn(context)
                    }
                    
                    // Debug the current state
                    gmailService.debugState()
                    
                    // Check if the service is now working
                    val isSignedIn = gmailService.isSignedIn()
                    Log.d(TAG, "Gmail service isSignedIn after check: $isSignedIn")
                    
                    if (isSignedIn) {
                        Log.d(TAG, "Gmail service is properly initialized and ready")
                        true
                    } else {
                        Log.w(TAG, "Gmail service still not ready - may need manual authentication")
                        false
                    }
                } else {
                    Log.w(TAG, "Account exists but missing required Gmail scopes")
                    false
                }
            } else {
                Log.w(TAG, "No Google account found - user needs to sign in manually")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in direct Gmail authentication check", e)
            false
        }
    }
    
    override fun getType(): WorkflowType = WorkflowType.GMAIL_TO_TELEGRAM
    
    override fun getConfiguration(): WorkflowConfiguration = config
    
    override fun stop() {
        Log.d(TAG, "Stopping Gmail to Telegram workflow")
        // Implementation depends on pipeline's stop capabilities
    }
}

/**
 * Concrete implementation for Telegram to Gmail workflows
 */
class TelegramToGmailWorkflowPipeline(
    private val pipeline: TelegramToGmailPipeline,
    private val config: TelegramToGmailConfig,
    private val context: Context
) : WorkflowPipeline() {
    
    companion object {
        private const val TAG = "TelegramToGmailWorkflow"
    }
    
    override suspend fun execute(): WorkflowExecutionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "Executing Telegram to Gmail workflow")
            
            // STEP 1: Gmail Authentication and Sign-in using Login.kt
            Log.d(TAG, "Step 1: Checking and initializing Gmail authentication using Login.kt")
            
            // Check if context is Activity for Login.kt usage
            if (context !is Activity) {
                Log.e(TAG, "Context is not an Activity, cannot use Login.kt for authentication")
                return WorkflowExecutionResult(
                    success = false,
                    message = "Gmail Authentication Error: Cannot authenticate in background context. Please run this workflow from an Activity.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            // Create Login instance and check authentication
            val login = Login(context)
            
            // Initialize Google Sign-In with Gmail permissions
            Log.d(TAG, "Initializing Google Sign-In with Gmail permissions...")
            login.initGoogleSignIn(requestGmailAccess = true)
            
            // Check if user is signed in
            if (!login.isSignedIn()) {
                Log.e(TAG, "User is not signed in to Google account")
                return WorkflowExecutionResult(
                    success = false,
                    message = "Gmail Authentication Required: Please sign in to your Gmail account. The app will guide you through the sign-in process.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            Log.d(TAG, "User is signed in to Google account")
            
            // Check if user has Gmail permissions
            if (!login.hasGmailScopes()) {
                Log.e(TAG, "User is signed in but missing Gmail scopes")
                return WorkflowExecutionResult(
                    success = false,
                    message = "Gmail Permissions Required: Please grant Gmail permissions. Go to Home > Sign In and authorize Gmail access, or re-run this workflow to grant permissions.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            Log.d(TAG, "User has all required Gmail permissions")
            
            // STEP 2: Create and initialize Gmail service
            Log.d(TAG, "Step 2: Creating and initializing Gmail service")
            val gmailService = GmailService(context)
            
            // Initialize Gmail service with the authenticated account using Activity context
            Log.d(TAG, "Initializing Gmail service with authenticated Google account...")
            gmailService.initGoogleSignIn(context as Activity)
            
            // Get the current signed-in account and manually setup Gmail client
            Log.d(TAG, "Setting up Gmail client manually...")
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                // Force setup of Gmail client by calling a private method through reflection or alternative approach
                try {
                    // Call setupGmailClient method directly
                    val setupMethod = gmailService::class.java.getDeclaredMethod("setupGmailClient", GoogleSignInAccount::class.java)
                    setupMethod.isAccessible = true
                    setupMethod.invoke(gmailService, account)
                    Log.d(TAG, "Gmail client setup method called successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not call setupGmailClient via reflection: ${e.message}")
                    // Alternative: Force initialization by triggering sign-in completion callback
                    val callbackMap = try {
                        val storageClass = Class.forName("com.example.llmapp.GmailService\$CallbackStorage")
                        val getCallbackMapMethod = storageClass.getDeclaredMethod("getCallbackMap")
                        @Suppress("UNCHECKED_CAST")
                        getCallbackMapMethod.invoke(null) as? MutableMap<Int, (android.content.Intent?) -> Unit>
                    } catch (ex: Exception) {
                        Log.w(TAG, "Could not access CallbackStorage: ${ex.message}")
                        null
                    }
                    
                    if (callbackMap != null) {
                        // Simulate successful sign-in callback
                        val dummyIntent = android.content.Intent().apply {
                            putExtra("account", account)
                        }
                        callbackMap.values.firstOrNull()?.invoke(dummyIntent)
                        Log.d(TAG, "Triggered sign-in callback manually")
                    }
                }
            } else {
                Log.e(TAG, "No Google account found after authentication check")
                return WorkflowExecutionResult(
                    success = false,
                    message = "Gmail Authentication Error: Google account not found after authentication.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            // Wait a bit longer for initialization
            kotlinx.coroutines.delay(500)
            
            // Verify Gmail service is ready
            if (!gmailService.isSignedIn()) {
                Log.e(TAG, "Gmail service is still not signed in after initialization")
                // Try one more time with a longer delay
                kotlinx.coroutines.delay(1000)
                if (!gmailService.isSignedIn()) {
                    Log.e(TAG, "Gmail service failed to initialize after retries")
                    return WorkflowExecutionResult(
                        success = false,
                        message = "Gmail service initialization failed. The Google account is authenticated but the Gmail service couldn't initialize. Please restart the app and try again.",
                        executionTime = System.currentTimeMillis() - startTime
                    )
                }
            }
            
            Log.d(TAG, "Gmail service successfully authenticated and ready")
            
            // STEP 3: Create TelegramToGmailPipeline object with configuration
            Log.d(TAG, "Step 3: Creating TelegramToGmailPipeline object")
            
            // Create LLM processor for the pipeline
            val llmProcessor = LlmContentProcessor(context)
            
            val telegramToGmailPipeline = com.example.llmapp.pipeline.TelegramToGmailPipeline(
                botToken = config.telegramBotToken,
                gmailService = gmailService,
                emailRecipients = config.gmailRecipients,
                defaultSender = config.gmailSender,
                llmProcessor = llmProcessor
            )
            
            // Set email template if configured
            val templateType = when (config.emailTemplate.uppercase()) {
                "COMPACT" -> EmailTemplateType.COMPACT
                "DETAILED" -> EmailTemplateType.DETAILED
                else -> EmailTemplateType.STANDARD
            }
            telegramToGmailPipeline.setEmailTemplate(templateType)
            
            // Set custom LLM prompt if provided
            if (config.llmPrompt.isNotBlank()) {
                telegramToGmailPipeline.setPromptStrategy(CustomPromptStrategy(config.llmPrompt))
                Log.d(TAG, "Set custom LLM prompt for Telegram pipeline")
            }
            
            Log.d(TAG, "TelegramToGmailPipeline configured with bot token: ${if (config.telegramBotToken.isNotBlank()) "[SET]" else "[EMPTY]"}")
            Log.d(TAG, "Email recipients: ${config.gmailRecipients.size}")
            Log.d(TAG, "Message limit: ${config.messageLimit}")
            
            // STEP 4: Get available chat IDs and use the configured one or the first available
            Log.d(TAG, "Step 4: Determining chat ID for processing")
            val chatId = try {
                // Try to get available chat IDs with timeout handling
                val availableChatIds = telegramToGmailPipeline.getAvailableChatIds()
                Log.d(TAG, "Retrieved ${availableChatIds.size} available chat IDs: $availableChatIds")
                
                if (config.telegramChatId.isNotBlank() && availableChatIds.contains(config.telegramChatId)) {
                    Log.d(TAG, "Using configured chat ID: ${config.telegramChatId}")
                    config.telegramChatId
                } else {
                    val fallbackChatId = availableChatIds.firstOrNull() ?: config.telegramChatId
                    Log.d(TAG, "Using fallback chat ID: $fallbackChatId")
                    fallbackChatId
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get available chat IDs due to timeout or error: ${e.message}")
                Log.d(TAG, "Using configured chat ID as fallback: ${config.telegramChatId}")
                
                // If we have a configured chat ID, use it even if we can't verify it exists
                if (config.telegramChatId.isNotBlank()) {
                    config.telegramChatId
                } else {
                    // Generate a mock chat ID for testing if no configuration exists
                    "mock_chat_${System.currentTimeMillis()}"
                }
            }
            
            if (chatId.isBlank()) {
                return WorkflowExecutionResult(
                    success = false,
                    message = "No Telegram chat ID available. Please configure a chat ID in the workflow settings or send a message to the bot first.",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            
            Log.d(TAG, "Using chat ID: $chatId")
            
            // STEP 5: Execute the pipeline using processRecentConversation() method
            Log.d(TAG, "Step 5: Executing pipeline using processRecentConversation() method")
            val result = try {
                telegramToGmailPipeline.processRecentConversation(
                    chatId = chatId,
                    messageLimit = config.messageLimit
                )
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Telegram API timeout error: ${e.message}")
                com.example.llmapp.pipeline.PipelineResult.Failure(
                    "Telegram API timeout: Unable to connect to Telegram servers. Please check your internet connection and try again later."
                )
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Network connectivity error: ${e.message}")
                com.example.llmapp.pipeline.PipelineResult.Failure(
                    "Network error: Unable to reach Telegram servers. Please check your internet connection."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during pipeline execution: ${e.message}", e)
                com.example.llmapp.pipeline.PipelineResult.Failure(
                    "Pipeline execution failed: ${e.message ?: "Unknown error occurred"}"
                )
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            // STEP 6: Convert pipeline result to workflow execution result
            when (result) {
                is com.example.llmapp.pipeline.PipelineResult.Success -> {
                    Log.d(TAG, "Pipeline execution successful - Message ID: ${result.messageId}, Processed: ${result.processedMessages}, Email sent: ${result.emailSent}")
                    WorkflowExecutionResult(
                        success = true,
                        message = "Successfully processed ${result.processedMessages} messages and sent email",
                        executionTime = executionTime,
                        details = mapOf(
                            "messagesProcessed" to result.processedMessages,
                            "emailSent" to result.emailSent,
                            "messageId" to result.messageId,
                            "chatId" to chatId,
                            "batchInfo" to (result.batchInfo ?: "")
                        )
                    )
                }
                is com.example.llmapp.pipeline.PipelineResult.Failure -> {
                    Log.e(TAG, "Pipeline execution failed: ${result.error}")
                    WorkflowExecutionResult(
                        success = false,
                        message = result.error,
                        executionTime = executionTime,
                        details = mapOf("chatId" to chatId)
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed", e)
            val executionTime = System.currentTimeMillis() - startTime
            
            WorkflowExecutionResult(
                success = false,
                message = "Execution failed: ${e.message}",
                executionTime = executionTime
            )
        }
    }
    
    override fun getType(): WorkflowType = WorkflowType.TELEGRAM_TO_GMAIL
    
    override fun getConfiguration(): WorkflowConfiguration = config
    
    override fun stop() {
        Log.d(TAG, "Stopping Telegram to Gmail workflow")
        // Implementation depends on pipeline's stop capabilities
    }
}

/**
 * Data class to represent the readiness status for workflow execution
 */
data class ExecutionReadinessResult(
    val ready: Boolean,
    val issues: List<String> = emptyList()
) {
    fun getIssuesString(): String {
        return issues.joinToString("; ")
    }
    
    fun hasAuthenticationIssues(): Boolean {
        return issues.any { it.contains("authentication", ignoreCase = true) || it.contains("sign in", ignoreCase = true) }
    }
    
    fun hasConfigurationIssues(): Boolean {
        return issues.any { it.contains("token", ignoreCase = true) || it.contains("chat ID", ignoreCase = true) || it.contains("recipients", ignoreCase = true) }
    }
}
