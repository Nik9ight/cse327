package com.example.llmapp.commands

/**
 * Base Command interface for the Command pattern
 * Provides basic execute and undo operations
 */
interface Command {
    /**
     * Execute the command
     * @return true if successful, false otherwise
     */
    fun execute(): Boolean
    
    /**
     * Undo the command operation
     * @return true if undo was successful, false otherwise
     */
    fun undo(): Boolean
}
