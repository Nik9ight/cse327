package com.example.llmapp.pipeline

import android.content.Context
import com.example.llmapp.GmailService
import com.example.llmapp.Model
import com.example.llmapp.TelegramService

/**
 * Strategy Pattern for service creation in GmailToTelegramPipeline
 * Allows different service creation strategies without modifying the pipeline
 */
interface ServiceCreationStrategy {
    fun createEmailFetchService(context: Context, gmailService: GmailService?): EmailFetchService
    fun createContentProcessor(context: Context, model: Model?): ContentProcessor
    fun createDeliveryService(context: Context, telegramService: TelegramService?): MessageDeliveryService
}

/**
 * Default strategy for creating Gmail to Telegram services
 */
class DefaultServiceStrategy : ServiceCreationStrategy {
    override fun createEmailFetchService(context: Context, gmailService: GmailService?): EmailFetchService {
        return GmailFetchService(context, gmailService)
    }
    
    override fun createContentProcessor(context: Context, model: Model?): ContentProcessor {
        val actualModel = model ?: com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        return LlmContentProcessor(context, actualModel, DefaultPromptStrategy())
    }
    
    override fun createDeliveryService(context: Context, telegramService: TelegramService?): MessageDeliveryService {
        return TelegramDeliveryService(context, telegramService)
    }
}

/**
 * Strategy for creating services with custom prompt strategy
 */
class CustomPromptServiceStrategy(
    private val promptStrategy: PromptStrategy
) : ServiceCreationStrategy {
    override fun createEmailFetchService(context: Context, gmailService: GmailService?): EmailFetchService {
        return GmailFetchService(context, gmailService)
    }
    
    override fun createContentProcessor(context: Context, model: Model?): ContentProcessor {
        val actualModel = model ?: com.example.llmapp.MODEL_TEXT_CLASSIFICATION_MOBILEBERT.copy(llmSupportImage = false)
        return LlmContentProcessor(context, actualModel, promptStrategy)
    }
    
    override fun createDeliveryService(context: Context, telegramService: TelegramService?): MessageDeliveryService {
        return TelegramDeliveryService(context, telegramService)
    }
}

/**
 * Mock strategy for testing purposes
 */
class MockServiceStrategy : ServiceCreationStrategy {
    override fun createEmailFetchService(context: Context, gmailService: GmailService?): EmailFetchService {
        return MockEmailFetchService(context)
    }
    
    override fun createContentProcessor(context: Context, model: Model?): ContentProcessor {
        return MockContentProcessor()
    }
    
    override fun createDeliveryService(context: Context, telegramService: TelegramService?): MessageDeliveryService {
        return MockDeliveryService(context)
    }
}
