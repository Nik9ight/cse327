package com.example.llmapp.workflows.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Receipt analysis service for extracting receipt information from text
 */
class ReceiptAnalysisService(private val context: Context) {
    
    companion object {
        private const val TAG = "ReceiptAnalysisService"
        
        private val TOTAL_PATTERNS = listOf(
            // Patterns that look for "TOTAL AMOUNT" followed by amount (highest priority)
            Pattern.compile("(?i)total[\\s]*amount[\\s]*\\$?\\s*([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE),
            Pattern.compile("(?i)net[\\s]*taka[:\\s]*=?([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE), // Net Taka 
            Pattern.compile("(?i)total[:\\s]*\\$?\\s*([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE),
            Pattern.compile("(?i)grand[\\s]*total[:\\s]*\\$?\\s*([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE),
            Pattern.compile("(?i)sub[\\s]*total[:\\s]*\\$?\\s*([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE),
            Pattern.compile("(?i)amount[:\\s]*\\$?\\s*([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE), // Amount line (lower priority)
            Pattern.compile("(?i)sum[:\\s]*\\$?\\s*([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE),
            Pattern.compile("([0-9]{1,4}\\.[0-9]{1,2})", Pattern.MULTILINE) // Standalone decimal numbers (lowest priority)
        )
        
        private val MERCHANT_PATTERNS = listOf(
            // First line with shop name - specifically for "LAZZ PHARMA LTD."
            Pattern.compile("^([A-Z][A-Za-z\\s&\\.]{2,30})", Pattern.MULTILINE),
            // Specific patterns for common receipt formats
            Pattern.compile("(?:store|shop|market|restaurant)\\s*:?\\s*([A-Za-z\\s&]{2,30})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("welcome\\s+to\\s+([A-Za-z\\s&]{2,30})", Pattern.CASE_INSENSITIVE),
            // Pattern for "SHOP NAME" format and pharmacy names
            Pattern.compile("^([A-Z]{2,}(?:\\s+[A-Z]{2,})*(?:\\s+LTD\\.?)?)", Pattern.MULTILINE),
            // Generic merchant name at beginning of text
            Pattern.compile("^\\s*([A-Z][A-Za-z\\s\\.]{3,25})", Pattern.MULTILINE)
        )
        
        private val DATE_PATTERNS = listOf(
            Pattern.compile("([0-1]?[0-9]/[0-3]?[0-9]/[0-9]{2,4})"),
            Pattern.compile("([0-1]?[0-9]-[0-3]?[0-9]-[0-9]{2,4})"),
            Pattern.compile("([A-Za-z]{3}\\s+[0-3]?[0-9],?\\s+[0-9]{4})")
        )
    }
    
    private val imageProcessingService = ImageProcessingService(context)
    
    /**
     * Analyze receipt from image path
     */
    suspend fun analyzeReceipt(imagePath: String): ReceiptData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Analyzing receipt: $imagePath")
            
            // First check if it looks like a receipt
            if (!imageProcessingService.isReceiptImage(imagePath)) {
                Log.d(TAG, "Image doesn't appear to be a receipt")
                return@withContext null
            }
            
            // Extract text from image
            val text = imageProcessingService.extractTextFromImage(imagePath)
            
            if (text.isEmpty()) {
                Log.w(TAG, "No text extracted from image")
                return@withContext null
            }
            
            Log.d(TAG, "Extracted text: $text")
            
            // Parse receipt information
            val merchantName = extractMerchantName(text)
            val totalAmount = extractTotalAmount(text)
            val date = extractDate(text) ?: Date() // Default to current date
            val category = categorizeReceipt(text, merchantName)
            
            if (totalAmount == null || totalAmount <= 0.0) {
                Log.w(TAG, "Could not extract valid amount from receipt")
                return@withContext null
            }
            
            ReceiptData(
                imagePath = imagePath,
                merchantName = merchantName ?: "Unknown Merchant",
                totalAmount = totalAmount,
                date = date,
                category = category,
                extractedText = text,
                confidence = calculateConfidence(text, merchantName != null, totalAmount > 0)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing receipt", e)
            null
        }
    }
    
    private fun extractMerchantName(text: String): String? {
        MERCHANT_PATTERNS.forEach { pattern ->
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim()
                if (!merchant.isNullOrBlank() && merchant.length > 2) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        return null
    }
    
    private fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .take(4) // Limit to 4 words
            .joinToString(" ")
    }
    
    private fun extractTotalAmount(text: String): Double? {
        Log.d(TAG, "Attempting to extract amount from text")
        
        // Look for amount that appears BEFORE "TOTAL" or "TOTAL AMOUNT"
        val lines = text.split('\n').map { it.trim() }
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("TOTAL", ignoreCase = true)) {
                // Check if the total amount is on the same line
                val sameLinePattern = Pattern.compile("(?i)total[\\s]*\\$\\s*([0-9]{1,4}\\.?[0-9]{0,2})")
                val sameLineMatcher = sameLinePattern.matcher(line)
                if (sameLineMatcher.find()) {
                    val amountStr = sameLineMatcher.group(1)
                    Log.d(TAG, "Found TOTAL on same line: $amountStr")
                    val amount = parseAmount(amountStr)
                    if (amount != null) return amount
                }
                
                // Look backwards for a dollar amount (for "TOTAL AMOUNT" format)
                for (j in (i-4).coerceAtLeast(0) until i) {
                    val dollarPattern = Pattern.compile("\\$\\s*([0-9]{1,4}\\.?[0-9]{0,2})")
                    val dollarMatcher = dollarPattern.matcher(lines[j])
                    if (dollarMatcher.find()) {
                        val amountStr = dollarMatcher.group(1)
                        Log.d(TAG, "Found dollar amount BEFORE TOTAL: $amountStr")
                        val amount = parseAmount(amountStr)
                        if (amount != null) return amount
                    }
                }
                
                // Look forward for a dollar amount (for "TOTAL $56.95" format)
                for (j in i+1 until minOf(i+3, lines.size)) {
                    val dollarPattern = Pattern.compile("\\$\\s*([0-9]{1,4}\\.?[0-9]{0,2})")
                    val dollarMatcher = dollarPattern.matcher(lines[j])
                    if (dollarMatcher.find()) {
                        val amountStr = dollarMatcher.group(1)
                        Log.d(TAG, "Found dollar amount AFTER TOTAL: $amountStr")
                        val amount = parseAmount(amountStr)
                        if (amount != null) return amount
                    }
                }
            }
        }
        
        // Look for "Net Taka" pattern (for pharmacy receipts)
        val netTakaPattern = Pattern.compile("(?i)net[\\s]*taka[\\s]*=?([0-9]{1,4}\\.?[0-9]{0,2})", Pattern.MULTILINE)
        val netTakaMatcher = netTakaPattern.matcher(text)
        if (netTakaMatcher.find()) {
            val amountStr = netTakaMatcher.group(1)
            Log.d(TAG, "Found Net Taka amount: $amountStr")
            val amount = parseAmount(amountStr)
            if (amount != null) return amount
        }
        
        // Look for "= 482.00" pattern (amount with equals sign)
        val equalsAmountPattern = Pattern.compile("=\\s*([0-9]{1,4}\\.[0-9]{1,2})", Pattern.MULTILINE)
        val equalsAmountMatcher = equalsAmountPattern.matcher(text)
        val amounts = mutableListOf<Double>()
        while (equalsAmountMatcher.find()) {
            val amountStr = equalsAmountMatcher.group(1)
            val amount = parseAmount(amountStr)
            if (amount != null) {
                amounts.add(amount)
                Log.d(TAG, "Found equals amount: $amount")
            }
        }
        
        // Return the largest amount from equals patterns (likely the total)
        if (amounts.isNotEmpty()) {
            val maxAmount = amounts.maxOrNull()
            Log.d(TAG, "Returning largest equals amount: $maxAmount")
            return maxAmount
        }
        
        // Try pattern-based extraction as fallback
        val keywordPatterns = TOTAL_PATTERNS.drop(1) // Skip first pattern, use others
        keywordPatterns.forEach { pattern ->
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                Log.d(TAG, "Found amount with keyword pattern: $amountStr")
                val amount = parseAmount(amountStr)
                if (amount != null) return amount
            }
        }
        
        Log.w(TAG, "No valid amount found")
        return null
    }
    
    private fun parseAmount(amountStr: String?): Double? {
        if (amountStr.isNullOrBlank()) return null
        
        return try {
            val amount = amountStr.trim().toDoubleOrNull()
            if (amount != null && amount > 0 && amount < 10000) {
                amount
            } else null
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    private fun extractDate(text: String): Date? {
        DATE_PATTERNS.forEach { pattern ->
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val dateStr = matcher.group(1)
                try {
                    return parseDate(dateStr)
                } catch (e: Exception) {
                    return@forEach
                }
            }
        }
        return null
    }
    
    private fun parseDate(dateStr: String): Date? {
        val patterns = listOf(
            SimpleDateFormat("MM/dd/yyyy", Locale.US),
            SimpleDateFormat("M/d/yyyy", Locale.US),
            SimpleDateFormat("MM/dd/yy", Locale.US),
            SimpleDateFormat("M/d/yy", Locale.US),
            SimpleDateFormat("MM-dd-yyyy", Locale.US),
            SimpleDateFormat("MMM dd, yyyy", Locale.US),
            SimpleDateFormat("MMM d, yyyy", Locale.US)
        )
        
        patterns.forEach { formatter ->
            try {
                return formatter.parse(dateStr)
            } catch (e: Exception) {
                // Continue to next pattern
            }
        }
        return null
    }
    
    private fun categorizeReceipt(text: String, merchantName: String?): ReceiptCategory {
        val textLower = text.lowercase()
        val merchantLower = merchantName?.lowercase() ?: ""
        
        return when {
            // Gas stations
            textLower.contains("gas") || textLower.contains("fuel") || textLower.contains("gasoline") ||
            merchantLower.contains("shell") || merchantLower.contains("exxon") || merchantLower.contains("bp") ||
            merchantLower.contains("chevron") || merchantLower.contains("mobil") -> ReceiptCategory.GAS
            
            // Restaurants and food
            textLower.contains("restaurant") || textLower.contains("cafe") || textLower.contains("diner") ||
            textLower.contains("pizza") || textLower.contains("burger") || textLower.contains("food") ||
            merchantLower.contains("mcdonald") || merchantLower.contains("subway") || merchantLower.contains("starbucks") ||
            merchantLower.contains("kfc") || merchantLower.contains("pizza") -> ReceiptCategory.FOOD
            
            // Grocery stores
            textLower.contains("grocery") || textLower.contains("supermarket") || textLower.contains("market") ||
            merchantLower.contains("walmart") || merchantLower.contains("kroger") || merchantLower.contains("safeway") ||
            merchantLower.contains("whole foods") || merchantLower.contains("trader joe") -> ReceiptCategory.GROCERIES
            
            // Shopping/retail
            textLower.contains("store") || textLower.contains("retail") || textLower.contains("mall") ||
            merchantLower.contains("target") || merchantLower.contains("amazon") || merchantLower.contains("best buy") ||
            merchantLower.contains("home depot") || merchantLower.contains("lowes") -> ReceiptCategory.SHOPPING
            
            // Healthcare
            textLower.contains("pharmacy") || textLower.contains("medical") || textLower.contains("hospital") ||
            textLower.contains("doctor") || merchantLower.contains("cvs") || merchantLower.contains("walgreens") -> ReceiptCategory.HEALTHCARE
            
            // Transportation
            textLower.contains("uber") || textLower.contains("lyft") || textLower.contains("taxi") ||
            textLower.contains("bus") || textLower.contains("train") || textLower.contains("airport") -> ReceiptCategory.TRANSPORTATION
            
            else -> ReceiptCategory.OTHER
        }
    }
    
    private fun calculateConfidence(text: String, hasMerchant: Boolean, hasAmount: Boolean): Float {
        var confidence = 60f // Base confidence
        
        if (hasMerchant) confidence += 20f
        if (hasAmount) confidence += 20f
        
        // Check for receipt keywords
        val receiptKeywords = listOf("receipt", "thank you", "total", "tax", "subtotal")
        val keywordCount = receiptKeywords.count { text.lowercase().contains(it) }
        confidence += (keywordCount * 2f)
        
        return confidence.coerceAtMost(98f)
    }
}

/**
 * Data class for receipt information
 */
data class ReceiptData(
    val imagePath: String,
    val merchantName: String,
    val totalAmount: Double,
    val date: Date,
    val category: ReceiptCategory,
    val extractedText: String,
    val confidence: Float
)

/**
 * Receipt categories
 */
enum class ReceiptCategory(val displayName: String, val emoji: String) {
    FOOD("Food & Dining", "🍽️"),
    GROCERIES("Groceries", "🛒"),
    GAS("Gas & Fuel", "⛽"),
    SHOPPING("Shopping", "🛍️"),
    HEALTHCARE("Healthcare", "🏥"),
    TRANSPORTATION("Transportation", "🚗"),
    OTHER("Other", "📄")
}
