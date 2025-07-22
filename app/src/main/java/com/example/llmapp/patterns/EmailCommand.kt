package com.example.llmapp.patterns

/**
 * Command interface for email processing operations
 * Implements the Command pattern to encapsulate email processing operations
 */
interface EmailCommand {
    /**
     * Execute the command
     */
    fun execute()
    
    /**
     * Cancel the command execution
     */
    fun cancel()
}
