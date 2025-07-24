package com.example.llmapp.pipeline

import android.content.Context
import android.util.Log
import com.example.llmapp.interfaces.MessageSourceService

/**
 * Plugin Architecture for extending pipeline functionality
 * Allows adding new features without modifying existing code
 */

interface PipelinePlugin {
    fun getName(): String
    fun getVersion(): String
    fun initialize(context: Context, config: Map<String, Any>)
    fun onMessageReceived(message: MessageSourceService.SourceMessage)
    fun onMessageProcessed(message: MessageSourceService.SourceMessage, processedContent: String)
    fun onMessageDelivered(message: MessageSourceService.SourceMessage, success: Boolean)
    fun onError(error: String, context: String)
    fun cleanup()
}

/**
 * Abstract base plugin class with default implementations
 */
abstract class BasePipelinePlugin : PipelinePlugin {
    protected var isInitialized = false
    protected var context: Context? = null
    protected var config: Map<String, Any> = emptyMap()
    
    override fun initialize(context: Context, config: Map<String, Any>) {
        this.context = context
        this.config = config
        this.isInitialized = true
        Log.d(getName(), "Plugin initialized")
    }
    
    override fun onMessageReceived(message: MessageSourceService.SourceMessage) {
        // Default: no action
    }
    
    override fun onMessageProcessed(message: MessageSourceService.SourceMessage, processedContent: String) {
        // Default: no action
    }
    
    override fun onMessageDelivered(message: MessageSourceService.SourceMessage, success: Boolean) {
        // Default: no action
    }
    
    override fun onError(error: String, context: String) {
        Log.w(getName(), "Error in $context: $error")
    }
    
    override fun cleanup() {
        isInitialized = false
        context = null
        config = emptyMap()
        Log.d(getName(), "Plugin cleaned up")
    }
}

/**
 * Plugin manager to handle plugin lifecycle and notifications
 */
class PluginManager {
    private val plugins = mutableListOf<PipelinePlugin>()
    private val tag = "PluginManager"
    
    fun registerPlugin(plugin: PipelinePlugin) {
        if (!plugins.contains(plugin)) {
            plugins.add(plugin)
            Log.i(tag, "Registered plugin: ${plugin.getName()} v${plugin.getVersion()}")
        }
    }
    
    fun unregisterPlugin(plugin: PipelinePlugin) {
        if (plugins.remove(plugin)) {
            plugin.cleanup()
            Log.i(tag, "Unregistered plugin: ${plugin.getName()}")
        }
    }
    
    fun initializePlugins(context: Context, globalConfig: Map<String, Any> = emptyMap()) {
        plugins.forEach { plugin ->
            try {
                val pluginConfig = globalConfig[plugin.getName()] as? Map<String, Any> ?: emptyMap()
                plugin.initialize(context, pluginConfig)
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize plugin: ${plugin.getName()}", e)
            }
        }
    }
    
    fun notifyMessageReceived(message: MessageSourceService.SourceMessage) {
        plugins.forEach { plugin ->
            try {
                plugin.onMessageReceived(message)
            } catch (e: Exception) {
                Log.e(tag, "Plugin ${plugin.getName()} failed on message received", e)
            }
        }
    }
    
    fun notifyMessageProcessed(message: MessageSourceService.SourceMessage, processedContent: String) {
        plugins.forEach { plugin ->
            try {
                plugin.onMessageProcessed(message, processedContent)
            } catch (e: Exception) {
                Log.e(tag, "Plugin ${plugin.getName()} failed on message processed", e)
            }
        }
    }
    
    fun notifyMessageDelivered(message: MessageSourceService.SourceMessage, success: Boolean) {
        plugins.forEach { plugin ->
            try {
                plugin.onMessageDelivered(message, success)
            } catch (e: Exception) {
                Log.e(tag, "Plugin ${plugin.getName()} failed on message delivered", e)
            }
        }
    }
    
    fun notifyError(error: String, context: String) {
        plugins.forEach { plugin ->
            try {
                plugin.onError(error, context)
            } catch (e: Exception) {
                Log.e(tag, "Plugin ${plugin.getName()} failed on error notification", e)
            }
        }
    }
    
    fun cleanup() {
        plugins.forEach { plugin ->
            try {
                plugin.cleanup()
            } catch (e: Exception) {
                Log.e(tag, "Failed to cleanup plugin: ${plugin.getName()}", e)
            }
        }
        plugins.clear()
    }
    
    fun getPlugins(): List<PipelinePlugin> = plugins.toList()
}

/**
 * Example plugins demonstrating the plugin architecture
 */

/**
 * Statistics plugin - tracks processing metrics
 */
class StatisticsPlugin : BasePipelinePlugin() {
    private var messagesReceived = 0
    private var messagesProcessed = 0
    private var messagesDelivered = 0
    private var errors = 0
    
    override fun getName(): String = "StatisticsPlugin"
    override fun getVersion(): String = "1.0.0"
    
    override fun onMessageReceived(message: MessageSourceService.SourceMessage) {
        messagesReceived++
        Log.d(getName(), "Messages received: $messagesReceived")
    }
    
    override fun onMessageProcessed(message: MessageSourceService.SourceMessage, processedContent: String) {
        messagesProcessed++
        Log.d(getName(), "Messages processed: $messagesProcessed")
    }
    
    override fun onMessageDelivered(message: MessageSourceService.SourceMessage, success: Boolean) {
        if (success) {
            messagesDelivered++
            Log.d(getName(), "Messages delivered: $messagesDelivered")
        }
    }
    
    override fun onError(error: String, context: String) {
        errors++
        Log.w(getName(), "Errors: $errors")
        super.onError(error, context)
    }
    
    fun getStats(): Map<String, Int> {
        return mapOf(
            "received" to messagesReceived,
            "processed" to messagesProcessed,
            "delivered" to messagesDelivered,
            "errors" to errors
        )
    }
}

/**
 * Content filter plugin - filters messages based on content
 */
class ContentFilterPlugin(
    private val blockedWords: List<String> = emptyList(),
    private val blockedSenders: List<String> = emptyList()
) : BasePipelinePlugin() {
    
    override fun getName(): String = "ContentFilterPlugin"
    override fun getVersion(): String = "1.0.0"
    
    override fun onMessageReceived(message: MessageSourceService.SourceMessage) {
        val shouldBlock = checkIfBlocked(message)
        if (shouldBlock) {
            Log.w(getName(), "Blocked message: ${message.subject}")
            // In a real implementation, you might throw an exception or set a flag
        }
    }
    
    private fun checkIfBlocked(message: MessageSourceService.SourceMessage): Boolean {
        val sender = message.metadata["from"] ?: ""
        
        // Check blocked senders
        if (blockedSenders.any { it.equals(sender, ignoreCase = true) }) {
            return true
        }
        
        // Check blocked words in subject and content
        val textToCheck = "${message.subject} ${message.content}".lowercase()
        return blockedWords.any { word ->
            textToCheck.contains(word.lowercase())
        }
    }
}

/**
 * Notification plugin - sends notifications about pipeline events
 */
class NotificationPlugin : BasePipelinePlugin() {
    
    override fun getName(): String = "NotificationPlugin"
    override fun getVersion(): String = "1.0.0"
    
    override fun onMessageProcessed(message: MessageSourceService.SourceMessage, processedContent: String) {
        // In a real implementation, this might send Android notifications
        Log.i(getName(), "Notification: Message processed - ${message.subject}")
    }
    
    override fun onError(error: String, context: String) {
        // In a real implementation, this might send error notifications
        Log.e(getName(), "Error notification: $error in $context")
        super.onError(error, context)
    }
}
