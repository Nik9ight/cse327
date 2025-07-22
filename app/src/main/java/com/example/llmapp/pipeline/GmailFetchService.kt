package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import com.example.llmapp.GmailService
import com.example.llmapp.adapters.GmailServiceAdapter
import com.example.llmapp.interfaces.MessageSourceService
import com.example.llmapp.util.ServiceAccountUtil

/**
 * Implementation of EmailFetchService using Gmail
 */
class GmailFetchService(
    private val context: Context,
    private val gmailService: GmailService? = null
) : EmailFetchService {
    private val TAG = "GmailFetchService"
    private val messageSourceAdapter: GmailServiceAdapter
    private var searchQuery: String? = null
    
    init {
        // Initialize Gmail service adapter
        messageSourceAdapter = if (gmailService != null) {
            GmailServiceAdapter(context, gmailService)
        } else {
            // Try to create one using service account
            val serviceAccount = ServiceAccountUtil.getServiceAccount(context)
            if (serviceAccount != null) {
                // Explicitly cast serviceAccount to String to match the constructor parameter type
                val newGmailService = GmailService(context, serviceAccount as String)
                GmailServiceAdapter(context, newGmailService)
            } else {
                // Create a default one - will need to be signed in later
                GmailServiceAdapter(context, GmailService(context))
            }
        }
        
        Log.d(TAG, "GmailFetchService initialized, configured: ${isConfigured()}")
    }
    
    override fun fetchUnreadMessages(
        count: Int, 
        onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isConfigured()) {
            onError("Gmail service is not configured")
            return
        }
        
        val safeCount = when {
            count <= 0 -> {
                Log.w(TAG, "Invalid count parameter: $count, using default of 3")
                3
            }
            count > 10 -> {
                Log.w(TAG, "Count parameter too large: $count, capping at 10")
                10
            }
            else -> count
        }
        
        Log.d(TAG, "Fetching up to $safeCount emails")
        
        messageSourceAdapter.getUnreadMessages(
            safeCount,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    override fun markMessageAsProcessed(
        messageId: String,
        callback: MessageSourceService.OperationCallback
    ) {
        if (!isConfigured()) {
            callback.onError("Gmail service is not configured")
            return
        }
        
        messageSourceAdapter.markMessageAsProcessed(messageId, callback)
    }
    
    override fun isConfigured(): Boolean {
        return messageSourceAdapter.isConfigured()
    }
    
    override fun fetchMessagesByQuery(
        query: String,
        count: Int,
        onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isConfigured()) {
            onError("Gmail service is not configured")
            return
        }
        
        val safeCount = when {
            count <= 0 -> 3
            count > 10 -> 10
            else -> count
        }
        
        Log.d(TAG, "Fetching up to $safeCount emails with query: $query")
        
        messageSourceAdapter.searchMessages(
            query,
            safeCount,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    override fun setSearchQuery(query: String?) {
        this.searchQuery = query
    }
    
    override fun getSearchQuery(): String? {
        return searchQuery
    }
    
    override fun cancelPendingOperations() {
        Log.d(TAG, "Cancelling pending Gmail operations")
        // Currently no async operations to cancel in the adapter
        // The GmailService operations are already synchronous through coroutines
    }
}

//newly added

/**
 * Search and fetch messages matching a query
 * @param query The Gmail search query
 * @param count Maximum number of results to return
 */
