package com.example.llmapp.pipeline

import com.example.llmapp.interfaces.MessageSourceService

/**
 * Interface for email fetching services
 */
interface EmailFetchService : CancellableService {
    /**
     * Fetches unread messages up to the specified count
     */
    fun fetchUnreadMessages(
        count: Int,
        onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    )
    
    /**
     * Fetches messages matching a search query
     */
    fun fetchMessagesByQuery(
        query: String,
        count: Int,
        onSuccess: (List<MessageSourceService.SourceMessage>) -> Unit,
        onError: (String) -> Unit
    )
    
    /**
     * Sets the search query for filtering messages
     */
    fun setSearchQuery(query: String?)
    
    /**
     * Gets the current search query
     */
    fun getSearchQuery(): String?
    
    /**
     * Marks a message as processed
     */
    fun markMessageAsProcessed(
        messageId: String,
        callback: MessageSourceService.OperationCallback
    )
    
    /**
     * Checks if the service is properly configured
     */
    fun isConfigured(): Boolean
}
