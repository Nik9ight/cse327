package com.example.llmapp.workflows.receipt

import android.content.Context
import android.util.Log
import com.example.llmapp.TelegramService
import com.example.llmapp.workflows.services.ReceiptAnalysisService
import com.example.llmapp.workflows.services.ReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Receipt Detection Workflow - detects and processes receipts in images
 */
class ReceiptDetectionWorkflow(
    private val context: Context,
    private val telegramChatId: String,
    private val summaryTime: String = "20:00" // Default 8 PM
) {
    
    companion object {
        private const val TAG = "ReceiptDetectionWorkflow"
        
        // Shared storage for daily receipts across all instances
        private val sharedDailyReceipts = mutableListOf<ReceiptData>()
    }
    
    private val receiptAnalysisService = ReceiptAnalysisService(context)
    private val telegramService = TelegramService(context)
    
    // Reference to shared storage
    private val dailyReceipts get() = sharedDailyReceipts
    
    /**
     * Process image for receipt detection
     */
    suspend fun processImageForReceipt(imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing image for receipt detection: $imagePath")
            
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "Image file does not exist: $imagePath")
                return@withContext false
            }
            
            // Analyze the image for receipt data
            val receiptData = receiptAnalysisService.analyzeReceipt(imagePath)
            
            if (receiptData != null) {
                Log.d(TAG, "Receipt detected: ${receiptData.merchantName}, $${receiptData.totalAmount}")
                
                // Store the receipt data
                addReceiptToDaily(receiptData)
                
                // Send immediate notification for high-value purchases
                if (receiptData.totalAmount > 100.0) {
                    sendHighValueReceiptNotification(receiptData)
                }
                
                return@withContext true
            } else {
                Log.d(TAG, "No receipt detected in image: $imagePath")
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image for receipt detection", e)
            false
        }
    }
    
    /**
     * Add receipt to daily collection
     */
    private fun addReceiptToDaily(receiptData: ReceiptData) {
        synchronized(dailyReceipts) {
            // Check if we already have this receipt (avoid duplicates)
            val existingReceipt = dailyReceipts.find { 
                it.imagePath == receiptData.imagePath || 
                (it.merchantName == receiptData.merchantName && 
                 Math.abs(it.totalAmount - receiptData.totalAmount) < 0.01 &&
                 Math.abs(it.date.time - receiptData.date.time) < 60000) // Within 1 minute
            }
            
            if (existingReceipt == null) {
                dailyReceipts.add(receiptData)
                Log.d(TAG, "Added receipt to daily collection: ${receiptData.merchantName}")
                Log.d(TAG, "Receipt date: ${receiptData.date}")
                Log.d(TAG, "Total receipts in collection: ${dailyReceipts.size}")
            } else {
                Log.d(TAG, "Receipt already exists, skipping duplicate")
            }
        }
    }
    
    /**
     * Send notification for high-value receipts
     */
    private suspend fun sendHighValueReceiptNotification(receiptData: ReceiptData) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            
            val message = buildString {
                appendLine("💰 HIGH-VALUE PURCHASE DETECTED!")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                appendLine("🏪 Merchant: ${receiptData.merchantName}")
                appendLine("💵 Amount: $${String.format("%.2f", receiptData.totalAmount)}")
                appendLine("📅 Date: ${dateFormatter.format(receiptData.date)}")
                appendLine("🏷️ Category: ${receiptData.category.emoji} ${receiptData.category.displayName}")
                appendLine("🎯 Confidence: ${String.format("%.1f", receiptData.confidence)}%")
                appendLine()
                appendLine("⚠️ Immediate alert for purchase over $100")
                appendLine("🤖 Auto-detected by LLMAPP")
            }
            
            // Store original chat ID and switch to workflow chat
            val originalChatId = telegramService.getChatId()
            telegramService.setChatId(telegramChatId)
            
            telegramService.sendMessage(
                text = message,
                onSuccess = {
                    Log.d(TAG, "High-value receipt notification sent")
                    telegramService.setChatId(originalChatId ?: "")
                    continuation.resume(Unit)
                },
                onError = { error ->
                    Log.e(TAG, "Failed to send high-value receipt notification: $error")
                    telegramService.setChatId(originalChatId ?: "")
                    continuation.resume(Unit)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending high-value receipt notification", e)
            continuation.resume(Unit)
        }
    }
    
    /**
     * Send daily expense summary
     */
    suspend fun sendDailyExpenseSummary(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating daily expense summary")
            
            val todayReceipts = getTodayReceipts()
            
            if (todayReceipts.isEmpty()) {
                Log.d(TAG, "No receipts to summarize today")
                return@withContext false
            }
            
            val success = sendExpenseSummary(todayReceipts)
            
            if (success) {
                // Clear today's receipts after sending summary
                clearTodayReceipts()
            }
            
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending daily expense summary", e)
            false
        }
    }
    
    /**
     * Get today's receipts
     */
    private fun getTodayReceipts(): List<ReceiptData> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.time
        
        Log.d(TAG, "Looking for receipts since: $todayStart")
        Log.d(TAG, "Total receipts in collection: ${dailyReceipts.size}")
        
        synchronized(dailyReceipts) {
            dailyReceipts.forEachIndexed { index, receipt ->
                Log.d(TAG, "Receipt $index: ${receipt.merchantName} - ${receipt.date} - Amount: ${receipt.totalAmount}")
            }
        }
        
        val todayReceipts = synchronized(dailyReceipts) {
            dailyReceipts.filter { receipt -> 
                val isFromToday = receipt.date.after(todayStart) || receipt.date == todayStart
                Log.d(TAG, "Receipt ${receipt.merchantName} from ${receipt.date} is from today: $isFromToday")
                isFromToday
            }
        }
        
        Log.d(TAG, "Found ${todayReceipts.size} receipts from today")
        return todayReceipts
    }
    
    /**
     * Clear today's receipts
     */
    private fun clearTodayReceipts() {
        val todayReceipts = getTodayReceipts()
        synchronized(dailyReceipts) {
            dailyReceipts.removeAll(todayReceipts)
        }
    }
    
    /**
     * Send expense summary
     */
    private suspend fun sendExpenseSummary(receipts: List<ReceiptData>): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val dateFormatter = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            val totalAmount = receipts.sumOf { it.totalAmount }
            val categoryTotals = receipts.groupBy { it.category }
                .mapValues { (_, receipts) -> receipts.sumOf { it.totalAmount } }
            
            val message = buildString {
                appendLine("📊 DAILY EXPENSE SUMMARY")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                appendLine("📅 Date: ${dateFormatter.format(Date())}")
                appendLine("🧾 Total Receipts: ${receipts.size}")
                appendLine("💰 Total Spent: $${String.format("%.2f", totalAmount)}")
                
                appendLine()
                appendLine("📈 Breakdown by Category:")
                categoryTotals.toList().sortedByDescending { it.second }.forEach { (category, amount) ->
                    val percentage = (amount / totalAmount * 100).toInt()
                    appendLine("${category.emoji} ${category.displayName}: $${String.format("%.2f", amount)} (${percentage}%)")
                }
                
                appendLine()
                appendLine("🧾 Transaction Details:")
                receipts.sortedByDescending { it.date }.take(10).forEach { receipt ->
                    appendLine("• ${timeFormatter.format(receipt.date)} - ${receipt.merchantName}")
                    appendLine("  $${String.format("%.2f", receipt.totalAmount)} | ${receipt.category.emoji} ${receipt.category.displayName}")
                }
                
                if (receipts.size > 10) {
                    appendLine("• ... and ${receipts.size - 10} more transactions")
                }
                
                appendLine()
                appendLine("⚙️ Generated at: ${timeFormatter.format(Date())}")
                appendLine("🤖 Daily summary by LLMAPP")
            }
            
            // Store original chat ID and switch to workflow chat
            val originalChatId = telegramService.getChatId()
            telegramService.setChatId(telegramChatId)
            
            telegramService.sendMessage(
                text = message,
                onSuccess = {
                    Log.d(TAG, "Daily expense summary sent successfully")
                    telegramService.setChatId(originalChatId ?: "")
                    continuation.resume(true)
                },
                onError = { error ->
                    Log.e(TAG, "Failed to send expense summary: $error")
                    telegramService.setChatId(originalChatId ?: "")
                    continuation.resume(false)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending expense summary", e)
            continuation.resume(false)
        }
    }
    
    /**
     * Get current receipt count for today
     */
    fun getTodayReceiptCount(): Int {
        return getTodayReceipts().size
    }
    
    /**
     * Get today's total spending
     */
    fun getTodayTotalSpent(): Double {
        return getTodayReceipts().sumOf { it.totalAmount }
    }
}
