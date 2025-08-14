package com.example.llmapp.workflows.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.llmapp.Model
import com.example.llmapp.llmChatView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service for analyzing documents with LLM
 * Follows Single Responsibility Principle - handles document analysis only
 */
class DocumentAnalysisService(
    private val context: Context,
    private val llmChatView: llmChatView
) {
    companion object {
        private const val TAG = "DocumentAnalysisService"
    }
    
    // Queue system for managing LLM requests to prevent concurrent access
    private val llmMutex = Mutex()
    private val analysisQueue = ConcurrentLinkedQueue<AnalysisTask>()
    private var isProcessingQueue = false
    
    /**
     * Data class representing a queued analysis task
     */
    private data class AnalysisTask(
        val type: TaskType,
        val imagePath: String = "",
        val documentType: String = "",
        val customPrompt: String = "",
        val workflowId: String = "",
        val documentText: String = "",
        val analyses: List<DocumentAnalysisResult> = emptyList(),
        val date: String = "",
        val result: CompletableDeferred<Any>
    )
    
    private enum class TaskType {
        IMAGE_ANALYSIS,
        TEXT_ANALYSIS, 
        DAILY_SUMMARY
    }
    
    /**
     * Analyze document image directly with LLM using custom prompt (user requirements)
     * Uses queue system to prevent concurrent LLM access
     */
    suspend fun analyzeDocumentImage(
        imagePath: String,
        documentType: String,
        customPrompt: String,
        workflowId: String
    ): DocumentAnalysisResult {
        Log.d(TAG, "Queuing document image analysis: $imagePath")
        Log.d(TAG, "Document type: $documentType, Workflow: $workflowId")
        
        // Create a task and add to queue
        val result = CompletableDeferred<Any>()
        val task = AnalysisTask(
            type = TaskType.IMAGE_ANALYSIS,
            imagePath = imagePath,
            documentType = documentType,
            customPrompt = customPrompt,
            workflowId = workflowId,
            result = result
        )
        
        analysisQueue.offer(task)
        Log.d(TAG, "Task queued. Queue size: ${analysisQueue.size}")
        
        // Start processing if not already running
        processQueueIfNeeded()
        
        // Wait for result
        return result.await() as DocumentAnalysisResult
    }
    
    /**
     * Process queue if not currently processing
     */
    private suspend fun processQueueIfNeeded() {
        if (isProcessingQueue) {
            Log.d(TAG, "Queue processing already in progress")
            return
        }
        
        llmMutex.withLock {
            if (isProcessingQueue) return@withLock
            
            isProcessingQueue = true
            Log.d(TAG, "Starting queue processing")
            
            try {
                while (analysisQueue.isNotEmpty()) {
                    val task = analysisQueue.poll()
                    if (task != null) {
                        Log.d(TAG, "Processing task type: ${task.type}, Queue remaining: ${analysisQueue.size}")
                        
                        when (task.type) {
                            TaskType.IMAGE_ANALYSIS -> {
                                val result = performImageAnalysis(
                                    task.imagePath,
                                    task.documentType,
                                    task.customPrompt,
                                    task.workflowId
                                )
                                task.result.complete(result)
                            }
                            TaskType.TEXT_ANALYSIS -> {
                                val result = performTextAnalysis(
                                    task.documentText,
                                    task.documentType,
                                    task.imagePath
                                )
                                task.result.complete(result)
                            }
                            TaskType.DAILY_SUMMARY -> {
                                val result = performDailySummary(task.analyses, task.date)
                                task.result.complete(result)
                            }
                        }
                    }
                }
            } finally {
                isProcessingQueue = false
                Log.d(TAG, "Queue processing completed")
            }
        }
    }
    
    /**
     * Perform the actual image analysis without queue management
     */
    private suspend fun performImageAnalysis(
        imagePath: String,
        documentType: String,
        customPrompt: String,
        workflowId: String
    ): DocumentAnalysisResult {
        return try {
            Log.d(TAG, "Performing document image analysis: $imagePath")
            
            // Load the image from file path
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file does not exist: $imagePath")
                return DocumentAnalysisResult(
                    originalText = "",
                    analysis = "Error: Image file not found at $imagePath",
                    documentType = documentType,
                    imagePath = imagePath,
                    timestamp = System.currentTimeMillis(),
                    date = getCurrentDateString(),
                    workflowId = workflowId
                )
            }
            
            // Load bitmap from file
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode image from: $imagePath")
                return DocumentAnalysisResult(
                    originalText = "",
                    analysis = "Error: Failed to decode image file",
                    documentType = documentType,
                    imagePath = imagePath,
                    timestamp = System.currentTimeMillis(),
                    date = getCurrentDateString(),
                    workflowId = workflowId
                )
            }
            
            // Ensure bitmap is ARGB_8888 format as required by MediaPipe
            val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            
            // Convert bitmap to MPImage for LLM
            val images = listOf(BitmapImageBuilder(safeBitmap).build())
            
            val analysisResult = CompletableDeferred<String>()
            var fullResponse = StringBuilder()
            
            // Use the LLM to analyze the image directly with custom prompt and image
            llmChatView.generateResponse(
                context = context,
                input = customPrompt,
                images = images, // Pass the actual image to LLM
                onResult = { result, isDone ->
                    fullResponse.append(result)
                    if (isDone) {
                        analysisResult.complete(fullResponse.toString())
                    }
                },
                onError = { error ->
                    Log.e(TAG, "LLM image analysis error: $error")
                    analysisResult.complete("Error analyzing document image: $error")
                }
            )
            
            // Wait for analysis to complete naturally (no timeout)
            val analysis = analysisResult.await()
            
            Log.d(TAG, "Document image analysis completed. Length: ${analysis.length}")
            
            DocumentAnalysisResult(
                originalText = "", // No text extraction for direct image analysis
                analysis = analysis,
                documentType = documentType,
                imagePath = imagePath,
                timestamp = System.currentTimeMillis(),
                date = getCurrentDateString(),
                workflowId = workflowId
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing document image", e)
            DocumentAnalysisResult(
                originalText = "",
                analysis = "Error analyzing document image: ${e.message}",
                documentType = documentType,
                imagePath = imagePath,
                timestamp = System.currentTimeMillis(),
                date = getCurrentDateString(),
                workflowId = workflowId
            )
        }
    }

    /**
     * Analyze document text and generate concise report
     * Uses queue system to prevent concurrent LLM access
     */
    suspend fun analyzeDocument(
        documentText: String, 
        documentType: String,
        imagePath: String
    ): DocumentAnalysisResult {
        Log.d(TAG, "Queuing document text analysis")
        Log.d(TAG, "Document type: $documentType, Text length: ${documentText.length}")
        
        // Create a task and add to queue
        val result = CompletableDeferred<Any>()
        val task = AnalysisTask(
            type = TaskType.TEXT_ANALYSIS,
            documentText = documentText,
            documentType = documentType,
            imagePath = imagePath,
            result = result
        )
        
        analysisQueue.offer(task)
        Log.d(TAG, "Task queued. Queue size: ${analysisQueue.size}")
        
        // Start processing if not already running
        processQueueIfNeeded()
        
        // Wait for result
        return result.await() as DocumentAnalysisResult
    }
    
    /**
     * Perform the actual text analysis without queue management
     */
    private suspend fun performTextAnalysis(
        documentText: String,
        documentType: String,
        imagePath: String
    ): DocumentAnalysisResult {
        return try {
            Log.d(TAG, "Performing document text analysis")
            
            val prompt = createAnalysisPrompt(documentText, documentType)
            
            val analysisResult = CompletableDeferred<String>()
            var fullResponse = StringBuilder()
            
            // Use the LLM to analyze the document
            llmChatView.generateResponse(
                context = context,
                input = prompt,
                onResult = { result, isDone ->
                    fullResponse.append(result)
                    if (isDone) {
                        analysisResult.complete(fullResponse.toString())
                    }
                },
                onError = { error ->
                    Log.e(TAG, "LLM analysis error: $error")
                    analysisResult.complete("Error analyzing document: $error")
                }
            )
            
            // Wait for analysis to complete naturally (no timeout)
            val analysis = analysisResult.await()
            
            Log.d(TAG, "Document analysis completed. Length: ${analysis.length}")
            
            DocumentAnalysisResult(
                originalText = documentText,
                analysis = analysis,
                documentType = documentType,
                imagePath = imagePath,
                timestamp = System.currentTimeMillis(),
                date = getCurrentDateString()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing document", e)
            DocumentAnalysisResult(
                originalText = documentText,
                analysis = "Error analyzing document: ${e.message}",
                documentType = documentType,
                imagePath = imagePath,
                timestamp = System.currentTimeMillis(),
                date = getCurrentDateString()
            )
        }
    }
    
    /**
     * Generate daily summary from multiple document analyses
     * Uses queue system to prevent concurrent LLM access
     */
    suspend fun generateDailySummary(
        analyses: List<DocumentAnalysisResult>,
        date: String
    ): String {
        if (analyses.isEmpty()) {
            return "No documents were analyzed on $date."
        }
        
        Log.d(TAG, "Queuing daily summary generation for ${analyses.size} documents on $date")
        
        // Create a task and add to queue
        val result = CompletableDeferred<Any>()
        val task = AnalysisTask(
            type = TaskType.DAILY_SUMMARY,
            analyses = analyses,
            date = date,
            result = result
        )
        
        analysisQueue.offer(task)
        Log.d(TAG, "Daily summary task queued. Queue size: ${analysisQueue.size}")
        
        // Start processing if not already running
        processQueueIfNeeded()
        
        // Wait for result
        return result.await() as String
    }
    
    /**
     * Perform the actual daily summary generation without queue management
     */
    private suspend fun performDailySummary(
        analyses: List<DocumentAnalysisResult>,
        date: String
    ): String {
        return try {
                Log.d(
                    TAG,
                    "Performing daily summary generation for ${analyses.size} documents on $date"
                )

                val summaryPrompt = createDailySummaryPrompt(analyses, date)
                Log.d(TAG, "Summary prompt length: ${summaryPrompt.length} characters")

                val summaryResult = CompletableDeferred<String>()
                var fullResponse = StringBuilder()

                Log.d(TAG, "Starting LLM summary generation... (waiting until completion)")
                llmChatView.generateResponse(
                    context = context,
                    input = summaryPrompt,
                    onResult = { result, isDone ->
                        fullResponse.append(result)
                        Log.d(
                            TAG,
                            "Summary generation progress: ${fullResponse.length} chars, done: $isDone"
                        )
                        if (isDone) {
                            Log.d(TAG, "LLM summary generation completed naturally")
                            summaryResult.complete(fullResponse.toString())
                        }
                    },
                    onError = { error ->
                        Log.e(TAG, "Daily summary error: $error")
                        summaryResult.complete("Error generating daily summary: $error")
                    }
                )

                // Wait indefinitely for the LLM to complete (no timeout)
                val summary = summaryResult.await()

            Log.d(TAG, "Daily summary generated. Length: ${summary.length}")
            
            summary
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily summary", e)
            "Error generating daily summary: ${e.message}"
        }
    }
    
    private fun createAnalysisPrompt(documentText: String, documentType: String): String {
            return """
        Analyze this image and provide a very concise report only if it is a ${documentType.lowercase()}. Focus on key information only.
        
        Document Content:
        $documentText
        
        Please provide a brief analysis covering:
        1. Document type and purpose
        2. Key information (amounts, dates, names, etc.)
        3. Important details relevant to ${documentType.lowercase()}
        4. no need to add item list in report
        
        Keep the analysis concise (8-10 sentences maximum) and focus only on the most important information. if it is not a ${documentType.lowercase()}, only reply "not a ${documentType.lowercase()},nothing else"
                """.trimIndent()
    }
    
    private fun createDailySummaryPrompt(
        analyses: List<DocumentAnalysisResult>,
        date: String
    ): String {
        val analysesText = analyses.mapIndexed { index, analysis ->
            "${index + 1}. ${analysis.documentType}: ${analysis.analysis}"
        }.joinToString("\n")

        return """
        Write a 2-3 sentence summary of these ${analyses.size} documents only for from $date:
        
        $analysesText
        
        Focus only on key highlights and total spending.
                """.trimIndent()
    }
    
    /**
     * Create a simple fallback summary when LLM generation fails or times out
     */
    private fun createFallbackSummary(
        analyses: List<DocumentAnalysisResult>,
        date: String
    ): String {
        val documentTypes = analyses.map { it.documentType }.distinct()
        val documentCounts = analyses.groupBy { it.documentType }.mapValues { it.value.size }

        return buildString {
            appendLine("📊 Daily Summary for $date")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📋 Documents Processed: ${analyses.size}")
            appendLine("📄 Document Types: ${documentTypes.joinToString(", ")}")
            appendLine()
            documentCounts.forEach { (type, count) ->
                appendLine("• $type: $count documents")
            }
            appendLine()
            appendLine("⚠️ AI summary generation was unavailable.")
            appendLine("Individual analyses are available in the detailed report above.")
        }
    }
    
    private fun getCurrentDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date())
    }
}

/**
 * Data class for document analysis results
 */
data class DocumentAnalysisResult(
    val originalText: String,
    val analysis: String,
    val documentType: String,
    val imagePath: String,
    val timestamp: Long,
    val date: String,
    val workflowId: String = "" // Added for workflow-specific storage
)