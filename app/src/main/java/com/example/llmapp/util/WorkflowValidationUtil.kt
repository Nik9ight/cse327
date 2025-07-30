package com.example.llmapp.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.Login
import com.example.llmapp.TelegramService
import com.example.llmapp.workflow.*

/**
 * Utility class for validating workflow prerequisites and providing user guidance
 */
object WorkflowValidationUtil {
    private const val TAG = "WorkflowValidation"
    
    /**
     * Create an authentication callback that can handle Gmail sign-in from an Activity
     */
    fun createGmailAuthenticationCallback(activity: Activity): GmailToTelegramWorkflowPipeline.AuthenticationCallback {
        return object : GmailToTelegramWorkflowPipeline.AuthenticationCallback {
            override fun onAuthenticationRequired(authType: GmailToTelegramWorkflowPipeline.AuthType, callback: (Boolean) -> Unit) {
                when (authType) {
                    GmailToTelegramWorkflowPipeline.AuthType.GMAIL_SIGNIN -> {
                        Log.d(TAG, "Gmail authentication requested, starting sign-in flow")
                        handleGmailSignIn(activity, callback)
                    }
                    GmailToTelegramWorkflowPipeline.AuthType.TELEGRAM_CONFIG -> {
                        Log.d(TAG, "Telegram configuration requested")
                        // For now, just return false as this needs UI configuration
                        callback(false)
                    }
                }
            }
        }
    }
    
    /**
     * Handle Gmail sign-in using the Login class
     * First checks if user is already properly authenticated to avoid unnecessary sign-in
     */
    private fun handleGmailSignIn(activity: Activity, callback: (Boolean) -> Unit) {
        try {
            Log.d(TAG, "Checking current Gmail authentication status...")
            
            val login = Login(activity)
            
            // First check if user is already signed in with proper scopes
            if (login.isSignedIn() && login.hasGmailScopes()) {
                Log.d(TAG, "User is already signed in with Gmail scopes, no need to sign in again")
                
                // Verify that GmailService is also properly initialized
                val gmailService = GmailService(activity)
                if (gmailService.isSignedIn()) {
                    Log.d(TAG, "Gmail service is also ready, authentication complete")
                    activity.runOnUiThread {
                        callback(true)
                    }
                    return
                } else {
                    Log.d(TAG, "Gmail service needs initialization, attempting to initialize...")
                    // Try to initialize the Gmail service with existing credentials
                    gmailService.initGoogleSignIn(activity)
                    
                    // Check again after initialization
                    if (gmailService.isSignedIn()) {
                        Log.d(TAG, "Gmail service initialized successfully")
                        activity.runOnUiThread {
                            callback(true)
                        }
                        return
                    } else {
                        Log.w(TAG, "Gmail service initialization failed, proceeding with sign-in")
                    }
                }
            } else {
                Log.d(TAG, "User is not signed in or missing Gmail scopes, starting sign-in process")
            }
            
            // If we reach here, we need to perform sign-in
            Log.d(TAG, "Starting Gmail sign-in process")
            
            val gmailService = GmailService(activity)
            gmailService.signIn(activity) { success ->
                Log.d(TAG, "Gmail sign-in completed: $success")
                activity.runOnUiThread {
                    callback(success)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during Gmail sign-in", e)
            activity.runOnUiThread {
                callback(false)
            }
        }
    }
    
    /**
     * Quick check if Gmail authentication is available
     * Uses Login.kt for more accurate authentication and scope checking
     */
    fun isGmailAuthenticated(context: Context): Boolean {
        return try {
            // If context is Activity, use Login helper for more accurate checking
            if (context is Activity) {
                val login = Login(context)
                login.isSignedIn() && login.hasGmailScopes()
            } else {
                // Fallback to GmailService check
                val gmailService = GmailService(context)
                gmailService.isSignedIn()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Gmail authentication", e)
            false
        }
    }
    
    /**
     * Quick check if Telegram is configured for a given bot token
     */
    fun isTelegramConfigured(context: Context, botToken: String): Boolean {
        return try {
            if (botToken.isBlank()) return false
            val telegramService = TelegramService(context)
            // This is a basic check - we can't validate the token without making a network call
            botToken.startsWith("bot") || botToken.contains(":")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Telegram configuration", e)
            false
        }
    }
    
    /**
     * Get user-friendly validation message for Gmail to Telegram workflow
     */
    fun getGmailToTelegramValidationMessage(context: Context, config: GmailToTelegramConfig): ValidationMessage {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check Gmail authentication
        if (!isGmailAuthenticated(context)) {
            issues.add("Gmail Sign-In Required: Please authenticate your Google account in the app settings")
        }
        
        // Check Telegram configuration
        if (config.telegramBotToken.isBlank()) {
            issues.add("Telegram Bot Token Missing: Please enter your Telegram bot token in workflow settings")
        } else if (!isTelegramConfigured(context, config.telegramBotToken)) {
            warnings.add("Telegram Bot Token Format: Please verify your bot token format (should contain ':')")
        }
        
        if (config.telegramChatId.isBlank()) {
            issues.add("Telegram Chat ID Missing: Please enter the target chat ID in workflow settings")
        }
        
        // Check search query
        if (config.gmailSearchQuery.isBlank()) {
            warnings.add("Using default search query 'is:unread' - you can customize this in settings")
        }
        
        return ValidationMessage(
            canExecute = issues.isEmpty(),
            criticalIssues = issues,
            warnings = warnings,
            actionGuidance = getActionGuidance(issues, warnings)
        )
    }
    
    /**
     * Get user-friendly validation message for Telegram to Gmail workflow  
     */
    fun getTelegramToGmailValidationMessage(context: Context, config: TelegramToGmailConfig): ValidationMessage {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check Telegram configuration
        if (config.telegramBotToken.isBlank()) {
            issues.add("Telegram Bot Token Missing: Please enter your Telegram bot token in workflow settings")
        } else if (!isTelegramConfigured(context, config.telegramBotToken)) {
            warnings.add("Telegram Bot Token Format: Please verify your bot token format")
        }
        
        if (config.telegramChatId.isBlank()) {
            issues.add("Telegram Chat ID Missing: Please enter the source chat ID in workflow settings")
        }
        
        // Check Gmail authentication
        if (!isGmailAuthenticated(context)) {
            issues.add("Gmail Sign-In Required: Please authenticate your Google account in the app settings")
        }
        
        // Check Gmail recipients
        if (config.gmailRecipients.isEmpty()) {
            issues.add("Gmail Recipients Missing: Please add at least one email recipient in workflow settings")
        }
        
        return ValidationMessage(
            canExecute = issues.isEmpty(),
            criticalIssues = issues,
            warnings = warnings,
            actionGuidance = getActionGuidance(issues, warnings)
        )
    }
    
    /**
     * Generate specific action guidance based on the issues found
     */
    private fun getActionGuidance(issues: List<String>, warnings: List<String>): String {
        if (issues.isEmpty() && warnings.isEmpty()) {
            return "✅ All requirements satisfied. Workflow is ready to execute."
        }
        
        val guidance = StringBuilder()
        
        if (issues.isNotEmpty()) {
            guidance.append("⚠️ Required Actions:\n")
            issues.forEachIndexed { index, issue ->
                guidance.append("${index + 1}. $issue\n")
            }
        }
        
        if (warnings.isNotEmpty()) {
            if (guidance.isNotEmpty()) guidance.append("\n")
            guidance.append("💡 Recommendations:\n")
            warnings.forEachIndexed { index, warning ->
                guidance.append("${index + 1}. $warning\n")
            }
        }
        
        // Add specific steps for common issues
        if (issues.any { it.contains("Gmail Sign-In", ignoreCase = true) }) {
            guidance.append("\n📧 To sign in to Gmail:\n")
            guidance.append("• Go to Home screen\n")
            guidance.append("• Tap 'Sign In' button\n")
            guidance.append("• Complete Google authentication\n")
        }
        
        if (issues.any { it.contains("Telegram Bot Token", ignoreCase = true) }) {
            guidance.append("\n🤖 To get Telegram Bot Token:\n")
            guidance.append("• Message @BotFather on Telegram\n")
            guidance.append("• Use /newbot command to create a bot\n")
            guidance.append("• Copy the token to workflow settings\n")
        }
        
        if (issues.any { it.contains("Chat ID", ignoreCase = true) }) {
            guidance.append("\n💬 To get Telegram Chat ID:\n")
            guidance.append("• Add your bot to the target chat\n")
            guidance.append("• Send a message in the chat\n")
            guidance.append("• Use bot API to get chat updates\n")
        }
        
        return guidance.toString().trim()
    }
    
    /**
     * Data class for validation results with user-friendly messaging
     */
    data class ValidationMessage(
        val canExecute: Boolean,
        val criticalIssues: List<String>,
        val warnings: List<String>,
        val actionGuidance: String
    ) {
        fun hasGmailAuthIssue(): Boolean {
            return criticalIssues.any { it.contains("Gmail Sign-In", ignoreCase = true) }
        }
        
        fun hasTelegramConfigIssue(): Boolean {
            return criticalIssues.any { it.contains("Telegram", ignoreCase = true) }
        }
        
        fun getSummary(): String {
            return when {
                canExecute -> "Ready to execute"
                criticalIssues.size == 1 -> criticalIssues.first()
                criticalIssues.size > 1 -> "${criticalIssues.size} issues need to be resolved"
                else -> "Configuration validation failed"
            }
        }
    }
}
