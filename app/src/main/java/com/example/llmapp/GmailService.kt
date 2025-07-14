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

private const val TAG = "GmailService"

class GmailService(private val context: Context) {
    private var gmailClient: Gmail? = null
    private var login: Login? = null
    
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
        
        // Get the request code first, ensuring it's not null
        val requestCode = login?.getRequestCode()
        if (requestCode == null) {
            Log.e(TAG, "Failed to get request code for Google Sign-In")
            onComplete(false)
            return
        }
        
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
        val callback = CallbackStorage.getCallbackMap()[requestCode]
        if (callback != null) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Handling successful activity result for requestCode=$requestCode")
                callback.invoke(data)
            } else {
                Log.d(TAG, "Activity result was not OK: requestCode=$requestCode, resultCode=$resultCode")
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
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_MODIFY)
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
            Toast.makeText(
                context,
                "Failed to set up Gmail: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Check if signed in and Gmail client is initialized
    fun isSignedIn(): Boolean {
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
        }
        
        Log.d(TAG, "Gmail Client: ${if (gmailClient != null) "Initialized" else "Not Initialized"}")
        Log.d(TAG, "Login Helper: ${if (login != null) "Initialized" else "Not Initialized"}")
        Log.d(TAG, "================================")
    }
    
    // Get unread emails from Gmail, sorted by most recent first
    suspend fun getUnreadEmails(maxResults: Int = 10): List<EmailMessage> = suspendCoroutine { continuation ->
        if (!isSignedIn()) {
            continuation.resumeWithException(IllegalStateException("Not signed in to Gmail"))
            return@suspendCoroutine
        }
        
        Thread {
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
                
                continuation.resume(messages)
            } catch (e: IOException) {
                Log.e(TAG, "Error fetching emails", e)
                continuation.resumeWithException(e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                continuation.resumeWithException(e)
            }
        }.start()
    }
    
    // Mark an email as read (remove UNREAD label)
    suspend fun markAsRead(messageId: String): Boolean = suspendCoroutine { continuation ->
        if (!isSignedIn()) {
            continuation.resumeWithException(IllegalStateException("Not signed in to Gmail"))
            return@suspendCoroutine
        }
        
        Thread {
            try {
                val modifyMessageRequest = ModifyMessageRequest()
                modifyMessageRequest.removeLabelIds = listOf("UNREAD")
                gmailClient!!.users().messages()
                    .modify("me", messageId, modifyMessageRequest)
                    .execute()
                continuation.resume(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking email as read", e)
                continuation.resumeWithException(e)
            }
        }.start()
    }
    
    // Extract content from Gmail message
    private fun extractEmailContent(message: Message): String {
        // Simple implementation - handle text parts only
        if (message.payload.body.data != null) {
            val decodedBytes = Base64.getUrlDecoder().decode(message.payload.body.data)
            return String(decodedBytes)
        }
        
        // Try to get content from parts recursively
        return extractContentFromParts(message.payload.parts) ?: "[No readable content]"
    }
    
    // Recursively extract text content from message parts
    private fun extractContentFromParts(parts: List<MessagePart>?): String? {
        if (parts == null) return null
        
        val stringBuilder = StringBuilder()
        
        for (part in parts) {
            if (part.mimeType == "text/plain" && part.body.data != null) {
                val decodedBytes = Base64.getUrlDecoder().decode(part.body.data)
                stringBuilder.append(String(decodedBytes))
            } else if (part.parts != null) {
                val nestedContent = extractContentFromParts(part.parts)
                if (nestedContent != null) {
                    stringBuilder.append(nestedContent)
                }
            }
        }
        
        return if (stringBuilder.isEmpty()) null else stringBuilder.toString()
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
