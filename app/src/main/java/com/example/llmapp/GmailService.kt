package com.example.llmapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.ModifyMessageRequest
import java.io.IOException
import java.util.*
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*

private const val TAG = "GmailService"

class GmailService(private val context: Context, private val serviceAccount: String? = null) {
    private var gmailClient: Gmail? = null
    private var login: Login? = null
    
    init {
        // If a service account is provided, initialize the client with it
        if (serviceAccount != null) {
            setupGmailClientWithServiceAccount(serviceAccount)
        }
    }
    
    // Search for emails with a specific query
    suspend fun searchEmails(query: String, maxResults: Int = 10): List<Message> = withContext(Dispatchers.IO) {
        try {
            val user = "me"
            val request = gmailClient?.users()?.messages()?.list(user)?.setQ(query)?.setMaxResults(maxResults.toLong())
            val response = request?.execute()
            
            val messages = response?.messages?.mapNotNull { message ->
                gmailClient?.users()?.messages()?.get(user, message.id)?.execute()
            } ?: listOf()
            
            messages
        } catch (e: Exception) {
            Log.e(TAG, "Error searching emails: ${e.message}")
            throw e
        }
    }
    
    // Initialize Gmail API client with user's Google account
    fun initGoogleSignIn(activity: Activity) {
        login = Login(activity)
        login?.initGoogleSignIn(requestGmailAccess = true)
        
        // Log the initialization
        Log.d(TAG, "Google Sign-In initialized with Gmail scopes")
        debugState()
    }
    
    // Start sign-in process from an activity
    fun signIn(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (login == null) {
            initGoogleSignIn(activity)
        }
        
        // Check if Google Sign-In is properly configured by examining google-services.json
        try {
            // Check for empty oauth_client array in google-services.json
            val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resourceId == 0) {
                // OAuth client ID is missing, this indicates a configuration issue
                Log.e(TAG, "OAuth client configuration error: Web client ID not found in resources")
                Toast.makeText(
                    context,
                    "Google Sign-In is not configured properly. Please check your Google Cloud Console setup.",
                    Toast.LENGTH_LONG
                ).show()
                
                // Show more detailed error with instructions
                Log.e(TAG, "OAuth client configuration error. Please follow these steps to fix:\n" +
                    "1. Check google-services.json file\n" +
                    "2. Ensure the oauth_client array contains entries\n" +
                    "3. Go to the Google Cloud Console > APIs & Services > Credentials\n" +
                    "4. Create an OAuth client ID for Android\n" +
                    "5. Add your app's SHA-1 fingerprint and package name\n" +
                    "6. Download the updated google-services.json file\n" +
                    "7. Place it in your app/ directory")
                
                onComplete(false)
                return
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking OAuth configuration: ${e.message}")
            // Continue with sign-in attempt even if we couldn't check config
        }
        
        // Check if already signed in
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            // Check if we have the required scopes
            if (GoogleSignIn.hasPermissions(
                account,
                Scope(GmailScopes.GMAIL_READONLY),
                Scope(GmailScopes.GMAIL_MODIFY),
                Scope(GmailScopes.GMAIL_SEND)
            )) {
                Log.d(TAG, "User already signed in with required scopes")
                setupGmailClient(account)
                onComplete(true)
                return
            } else {
                Log.d(TAG, "User signed in but missing required scopes, requesting additional scopes")
            }
        }
        
        // Get the request code first, ensuring it's not null
        val requestCode = login?.getRequestCode()
        if (requestCode == null) {
            Log.e(TAG, "Failed to get request code for Google Sign-In")
            onComplete(false)
            return
        }
        
        // Display a Toast with instructions
        Toast.makeText(
            context, 
            "Please sign in with your Google account and grant Gmail access permissions", 
            Toast.LENGTH_LONG
        ).show()
        
        // Store the callback to invoke from the activity's onActivityResult
        val callbackMap = CallbackStorage.getCallbackMap()
        callbackMap[requestCode] = { data ->
            login?.handleSignInResult(
                data = data,
                onSuccess = { account ->
                    setupGmailClient(account)
                    onComplete(true)
                },
                onError = { error ->
                    Log.e(TAG, "Sign-in error: $error")
                    // Show error message to user
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
            )
        }
        
        // Start the sign-in process
        login?.signIn()
        Log.d(TAG, "Google Sign-In process started with requestCode: $requestCode")
    }
    
    // Call this from the activity's onActivityResult
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Log detailed information about the activity result
        Log.d(TAG, "Activity result received: requestCode=$requestCode, resultCode=$resultCode, data=${data != null}")
        
        val callback = CallbackStorage.getCallbackMap()[requestCode]
        if (callback != null) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Handling successful activity result for requestCode=$requestCode")
                callback.invoke(data)
            } else {
                Log.d(TAG, "Activity result was not OK: requestCode=$requestCode, resultCode=$resultCode")
                // Show a user-friendly message for canceled sign-in
                if (resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(context, "Google Sign-In was canceled", Toast.LENGTH_SHORT).show()
                }
                // Even if result is not OK, we need to notify the callback
                callback.invoke(null)
            }
            // Clean up the callback regardless of result
            CallbackStorage.getCallbackMap().remove(requestCode)
        } else {
            Log.d(TAG, "No callback found for activity result: requestCode=$requestCode, resultCode=$resultCode")
        }
        
        // Debug the state after activity result
        debugState()
    }
    
    // Set up Gmail client with the signed-in account
    private fun setupGmailClient(account: GoogleSignInAccount) {
        try {
            // If a service account was provided in the constructor, use that instead
            if (serviceAccount != null) {
                setupGmailClientWithServiceAccount(serviceAccount)
                return
            }
            
            // Otherwise use the user's account
            // Check if account.account is null (can happen in some cases)
            if (account.account == null) {
                Log.e(TAG, "Account.account is null, cannot set up Gmail client")
                Toast.makeText(
                    context, 
                    "Error: Could not access Google account details. Please try signing in again.", 
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            
            // Log account details for debugging
            Log.d(TAG, "Setting up Gmail client with account: ${account.email}, id: ${account.id}")
            
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_MODIFY, GmailScopes.GMAIL_SEND)
            )
            credential.selectedAccount = account.account
            
            // Use NetHttpTransport with the Google API Client
            val transport = NetHttpTransport()
            
            gmailClient = Gmail.Builder(
                transport,
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("LLMAPP Gmail Integration")
                .build()
            
            Log.d(TAG, "Gmail client successfully set up")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Gmail client", e)
            
            // Check if the error is related to OAuth client configuration
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("oauth2") || errorMsg.contains("OAuth") || 
                errorMsg.contains("credential") || errorMsg.contains("authentication")) {
                Toast.makeText(
                    context,
                    "Authentication error: Please check that Google Sign-In is properly configured in your Google Cloud Console.",
                    Toast.LENGTH_LONG
                ).show()
                // Log detailed instructions for fixing this error
                Log.e(TAG, "OAuth client configuration error. Please follow these steps to fix:\n" +
                        "1. Go to the Google Cloud Console: https://console.cloud.google.com\n" +
                        "2. Select your project\n" +
                        "3. Go to APIs & Services > Credentials\n" +
                        "4. Check if you have an OAuth client ID for Android\n" +
                        "5. Verify the SHA-1 fingerprint and package name are correct\n" +
                        "6. Make sure to download the updated google-services.json and place it in your app folder")
            } else {
                Toast.makeText(
                    context,
                    "Failed to set up Gmail: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Sets up the Gmail client using a service account
     * @param serviceAccountEmail The service account email to use
     */
    private fun setupGmailClientWithServiceAccount(serviceAccountEmail: String) {
        try {
            Log.d(TAG, "Setting up Gmail client with service account: $serviceAccountEmail")
            
            // Use NetHttpTransport with the Google API Client
            val transport = NetHttpTransport()
            
            // In a real implementation, you would load the service account credentials from a JSON file
            // For this example, we'll just create a Gmail client with the service account information
            
            // Placeholder for service account credential setup
            // In a real app, you would use GoogleCredential.fromStream() with a service account JSON file
            
            gmailClient = Gmail.Builder(
                transport,
                GsonFactory.getDefaultInstance(),
                null // Replace with actual service account credential
            )
                .setApplicationName("LLMAPP Gmail Integration")
                .build()
            
            Log.d(TAG, "Gmail client successfully set up with service account")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Gmail client with service account", e)
            Toast.makeText(
                context,
                "Error connecting to Gmail with service account: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Check if signed in and Gmail client is initialized
    fun isSignedIn(): Boolean {
        // If using service account, we only need to check if gmailClient is initialized
        if (serviceAccount != null) {
            return gmailClient != null
        }
        // Otherwise check for user account sign-in
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && gmailClient != null
    }
    
    // Log debug information about the current state
    fun debugState() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        
        Log.d(TAG, "====== GmailService Debug State ======")
        if (account == null) {
            Log.d(TAG, "Google Account: Not signed in")
        } else {
            Log.d(TAG, "Google Account: ${account.email}")
            Log.d(TAG, "Account ID: ${account.id}")
            Log.d(TAG, "Display Name: ${account.displayName}")
            Log.d(TAG, "ID Token: ${account.idToken?.take(10)}...")
            Log.d(TAG, "Server Auth Code: ${account.serverAuthCode?.take(10) ?: "null"}...")
            
            // Check for Gmail permissions
            val hasReadScope = GoogleSignIn.hasPermissions(
                account, 
                Scope(GmailScopes.GMAIL_READONLY)
            )
            
            val hasModifyScope = GoogleSignIn.hasPermissions(
                account,
                Scope(GmailScopes.GMAIL_MODIFY)
            )
            
            Log.d(TAG, "Gmail Read Permission: $hasReadScope")
            Log.d(TAG, "Gmail Modify Permission: $hasModifyScope")
            
            // Log the granted scopes
            val grantedScopes = account.grantedScopes
            Log.d(TAG, "All granted scopes: ${grantedScopes?.joinToString(", ") ?: "none"}")
        }
        
        Log.d(TAG, "Gmail Client: ${if (gmailClient != null) "Initialized" else "Not Initialized"}")
        Log.d(TAG, "Login Helper: ${if (login != null) "Initialized" else "Not Initialized"}")
        
        // Check for web client ID in google-services.json
        try {
            val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resourceId != 0) {
                val webClientId = context.getString(resourceId)
                Log.d(TAG, "Web Client ID found: ${webClientId.take(10)}...")
            } else {
                Log.d(TAG, "Web Client ID not found in resources")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking for Web Client ID: ${e.message}")
        }
        
        Log.d(TAG, "================================")
    }
    
    // Get unread emails from Gmail, sorted by most recent first
    suspend fun getUnreadEmails(maxResults: Int = 10): List<EmailMessage> = withContext(Dispatchers.IO) {
        if (!isSignedIn()) {
            throw IllegalStateException("Not signed in to Gmail")
        }
        
        try {
            val userId = "me"
            val listResponse: ListMessagesResponse = gmailClient!!.users().messages()
                .list(userId)
                .setMaxResults(maxResults.toLong())
                .setQ("is:unread")  // Only unread emails
                .execute()
            
            // Process and map messages
            var messages = listResponse.messages?.mapNotNull { messageRef ->
                try {
                    val message = gmailClient!!.users().messages().get(userId, messageRef.id).execute()
                    val headers = message.payload.headers
                    
                    val from = headers.find { it.name == "From" }?.value ?: ""
                    val subject = headers.find { it.name == "Subject" }?.value ?: ""
                    val content = extractEmailContent(message)
                    val date = headers.find { it.name == "Date" }?.value ?: ""
                    
                    // Use internal date (timestamp) for sorting
                    val internalDate = message.internalDate ?: 0L
                    
                    Log.d(TAG, "Processing email: '$subject' from '$from' dated '$date'")
                    
                    EmailMessage(
                        id = message.id,
                        from = from,
                        subject = subject,
                        content = content,
                        date = date,
                        timestamp = internalDate
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing message ${messageRef.id}", e)
                    null
                }
            } ?: listOf()
            
            // Sort messages by timestamp (newest first)
            messages = messages.sortedByDescending { it.timestamp }
            
            // Log the number of emails fetched
            Log.d(TAG, "Fetched ${messages.size} unread emails, sorted by newest first")
            
            messages
        } catch (e: IOException) {
            Log.e(TAG, "Error fetching emails", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            throw e
        }
    }
    
    // Mark an email as read (remove UNREAD label)
    suspend fun markAsRead(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn()) {
            throw IllegalStateException("Not signed in to Gmail")
        }
        
        try {
            val modifyMessageRequest = ModifyMessageRequest()
            modifyMessageRequest.removeLabelIds = listOf("UNREAD")
            gmailClient!!.users().messages()
                .modify("me", messageId, modifyMessageRequest)
                .execute()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error marking email as read", e)
            throw e
        }
    }
    
    // Send an email through Gmail
    suspend fun sendEmail(
        to: List<String>,
        subject: String,
        body: String,
        isHtml: Boolean = true,
        from: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn()) {
            throw IllegalStateException("Not signed in to Gmail")
        }
        
        try {
            Log.d(TAG, "Sending email to: ${to.joinToString(", ")}")
            Log.d(TAG, "Subject: $subject")
            
            // Build email content
            val fromAddress = from ?: GoogleSignIn.getLastSignedInAccount(context)?.email ?: "me"
            val rawEmail = buildEmailMessage(to, fromAddress, subject, body, isHtml)
            
            // Create Gmail message
            val message = com.google.api.services.gmail.model.Message()
            message.raw = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawEmail.toByteArray())
            
            // Send the email
            val result = gmailClient!!.users().messages()
                .send("me", message)
                .execute()
            
            Log.d(TAG, "Email sent successfully with ID: ${result.id}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email", e)
            throw e
        }
    }

    // Send an email with image attachment through Gmail
    suspend fun sendEmailWithImage(
        to: List<String>,
        subject: String,
        body: String,
        imagePath: String,
        isHtml: Boolean = true,
        from: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn()) {
            throw IllegalStateException("Not signed in to Gmail")
        }
        
        try {
            Log.d(TAG, "Sending email with image attachment to: ${to.joinToString(", ")}")
            Log.d(TAG, "Subject: $subject")
            Log.d(TAG, "Image path: $imagePath")
            
            // Check if image file exists
            val imageFile = java.io.File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file does not exist: $imagePath")
                throw IllegalArgumentException("Image file does not exist: $imagePath")
            }
            
            // Build email content with image attachment
            val fromAddress = from ?: GoogleSignIn.getLastSignedInAccount(context)?.email ?: "me"
            val rawEmail = buildEmailMessageWithImageAttachment(to, fromAddress, subject, body, imagePath, isHtml)
            
            // Create Gmail message
            val message = com.google.api.services.gmail.model.Message()
            message.raw = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawEmail.toByteArray())
            
            // Send the email
            val result = gmailClient!!.users().messages()
                .send("me", message)
                .execute()
            
            Log.d(TAG, "Email with image attachment sent successfully with ID: ${result.id}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email with image attachment", e)
            throw e
        }
    }
    
    // Helper method to build RFC 2822 compliant email message
    private fun buildEmailMessage(
        to: List<String>,
        from: String,
        subject: String,
        body: String,
        isHtml: Boolean
    ): String {
        val contentType = if (isHtml) "text/html; charset=UTF-8" else "text/plain; charset=UTF-8"
        
        return buildString {
            appendLine("From: $from")
            appendLine("To: ${to.joinToString(", ")}")
            appendLine("Subject: $subject")
            appendLine("Content-Type: $contentType")
            appendLine("MIME-Version: 1.0")
            appendLine()
            append(body)
        }
    }

    // Helper method to build RFC 2822 compliant email message with image attachment
    private fun buildEmailMessageWithImageAttachment(
        to: List<String>,
        from: String,
        subject: String,
        body: String,
        imagePath: String,
        isHtml: Boolean
    ): String {
        val boundary = "----=_Part_${System.currentTimeMillis()}_${Math.random()}"
        val contentType = if (isHtml) "text/html; charset=UTF-8" else "text/plain; charset=UTF-8"
        
        // Read image file and encode to base64
        val imageFile = java.io.File(imagePath)
        val imageBytes = imageFile.readBytes()
        val imageBase64 = java.util.Base64.getEncoder().encodeToString(imageBytes)
        val imageName = imageFile.name
        
        // Determine MIME type based on file extension
        val mimeType = when (imagePath.lowercase().substringAfterLast('.')) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg" // default
        }
        
        return buildString {
            // Headers
            appendLine("From: $from")
            appendLine("To: ${to.joinToString(", ")}")
            appendLine("Subject: $subject")
            appendLine("MIME-Version: 1.0")
            appendLine("Content-Type: multipart/mixed; boundary=\"$boundary\"")
            appendLine()
            
            // Body part
            appendLine("--$boundary")
            appendLine("Content-Type: $contentType")
            appendLine("Content-Transfer-Encoding: 7bit")
            appendLine()
            appendLine(body)
            appendLine()
            
            // Image attachment part
            appendLine("--$boundary")
            appendLine("Content-Type: $mimeType; name=\"$imageName\"")
            appendLine("Content-Transfer-Encoding: base64")
            appendLine("Content-Disposition: attachment; filename=\"$imageName\"")
            appendLine()
            
            // Split base64 into 76-character lines as per RFC 2045
            var index = 0
            while (index < imageBase64.length) {
                val endIndex = minOf(index + 76, imageBase64.length)
                appendLine(imageBase64.substring(index, endIndex))
                index = endIndex
            }
            
            appendLine()
            appendLine("--$boundary--")
        }
    }
    
    // Extract content from Gmail message
    private fun extractEmailContent(message: Message): String {
        // Log message structure for debugging
        Log.d(TAG, "Extracting content from message ID: ${message.id}")
        
        // Simple implementation - handle text parts only
        if (message.payload.body.data != null) {
            try {
                val decodedBytes = Base64.getUrlDecoder().decode(message.payload.body.data)
                val content = String(decodedBytes)
                Log.d(TAG, "Found content in message body: ${content.take(50)}...")
                return content
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding message body: ${e.message}")
            }
        }
        
        // Try to get content from parts recursively
        val content = extractContentFromParts(message.payload.parts) ?: "[No readable content]"
        if (content == "[No readable content]") {
            Log.w(TAG, "No readable content found for message ID: ${message.id}")
        } else {
            Log.d(TAG, "Extracted content from parts: ${content.take(50)}...")
        }
        return content
    }
    
    // Recursively extract text content from message parts
    private fun extractContentFromParts(parts: List<MessagePart>?): String? {
        if (parts == null) {
            Log.d(TAG, "No parts found to extract content from")
            return null
        }
        
        val stringBuilder = StringBuilder()
        
        for (part in parts) {
            Log.d(TAG, "Processing part with mimeType: ${part.mimeType}")
            
            // Try text/plain first for cleaner content
            if (part.mimeType == "text/plain" && part.body.data != null) {
                try {
                    val decodedBytes = Base64.getUrlDecoder().decode(part.body.data)
                    val content = String(decodedBytes)
                    Log.d(TAG, "Found text/plain content: ${content.take(50)}...")
                    stringBuilder.append(content)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding text/plain part: ${e.message}")
                }
            } 
            // Also try HTML content if plain text is not available
            else if (part.mimeType == "text/html" && stringBuilder.isEmpty() && part.body.data != null) {
                try {
                    val decodedBytes = Base64.getUrlDecoder().decode(part.body.data)
                    val htmlContent = String(decodedBytes)
                    // Strip HTML tags for cleaner content
                    val plainContent = htmlContent.replace("<[^>]*>".toRegex(), " ")
                        .replace("&nbsp;", " ")
                        .replace("\\s+".toRegex(), " ")
                        .trim()
                    Log.d(TAG, "Found text/html content (stripped): ${plainContent.take(50)}...")
                    stringBuilder.append(plainContent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding text/html part: ${e.message}")
                }
            } 
            // Check for nested parts
            else if (part.parts != null) {
                Log.d(TAG, "Checking nested parts...")
                val nestedContent = extractContentFromParts(part.parts)
                if (nestedContent != null) {
                    stringBuilder.append(nestedContent)
                }
            }
        }
        
        return if (stringBuilder.isEmpty()) {
            Log.w(TAG, "No content extracted from parts")
            null
        } else {
            stringBuilder.toString()
        }
    }
    
    // Sign out
    fun signOut() {
        login?.signOut {
            gmailClient = null
            Log.d(TAG, "Signed out of Google account")
            debugState()
        }
    }
    
    // Clean up
    fun cleanup() {
        // No resources to clean up with this implementation
    }
    
    // Data class for email messages
    data class EmailMessage(
        val id: String,
        val from: String,
        val subject: String,
        val content: String,
        val date: String = "",  // Date field added with default value for backward compatibility
        val timestamp: Long = 0  // Internal timestamp used for sorting, newest first
    )
    
    // Utility class to store callbacks across activity results
    object CallbackStorage {
        private val callbackMap = mutableMapOf<Int, (Intent?) -> Unit>()
        
        fun getCallbackMap(): MutableMap<Int, (Intent?) -> Unit> {
            return callbackMap
        }
    }
}
