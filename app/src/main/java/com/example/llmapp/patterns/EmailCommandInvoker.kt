package com.example.llmapp.patterns

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Command Invoker for processing email commands sequentially
 * Implements the Command pattern to execute commands one at a time
 */
class EmailCommandInvoker {
    private val commandQueue = ConcurrentLinkedQueue<EmailCommand>()
    private val isProcessing = AtomicBoolean(false)
    private val TAG = "EmailCommandInvoker"
    
    /**
     * Add a command to the queue and start processing if not already started
     */
    fun addCommand(command: EmailCommand) {
        commandQueue.add(command)
        Log.d(TAG, "Command added to queue, queue size: ${commandQueue.size}")
        
        // Start processing if not already processing
        if (isProcessing.compareAndSet(false, true)) {
            processNextCommand()
        }
    }
    
    /**
     * Clear all pending commands
     */
    fun clearCommands() {
        commandQueue.clear()
        Log.d(TAG, "Command queue cleared")
    }
    
    /**
     * Process the next command in the queue
     */
    private fun processNextCommand() {
        val command = commandQueue.poll()
        
        if (command == null) {
            // No more commands, mark as not processing
            isProcessing.set(false)
            Log.d(TAG, "Command queue empty, processing complete")
            return
        }
        
        // Execute the command in a separate thread
        Thread {
            try {
                Log.d(TAG, "Executing command")
                command.execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
            } finally {
                // Move to the next command
                processNextCommand()
            }
        }.start()
    }
    
    /**
     * Check if commands are currently being processed
     */
    fun isProcessing(): Boolean {
        return isProcessing.get()
    }
    
    /**
     * Get the current size of the command queue
     */
    fun queueSize(): Int {
        return commandQueue.size
    }
}
