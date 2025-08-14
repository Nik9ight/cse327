package com.example.llmapp.workflows.services

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for storing and retrieving document analyses
 * Follows Single Responsibility Principle - handles data persistence only
 */
class DocumentStorageService(private val context: Context) {
    
    companion object {
        private const val TAG = "DocumentStorageService"
        private const val STORAGE_DIR = "document_analyses"
        private const val ANALYSES_FILE = "analyses.json"
    }
    
    private val storageDir: File by lazy {
        File(context.filesDir, STORAGE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val analysesFile: File by lazy {
        File(storageDir, ANALYSES_FILE)
    }
    
    /**
     * Save document analysis result for a specific workflow
     */
    fun saveAnalysisForWorkflow(analysis: DocumentAnalysisResult, workflowId: String): Boolean {
        return try {
            Log.d(TAG, "Saving analysis for ${analysis.documentType} in workflow $workflowId")
            
            val analyses = loadAllAnalyses().toMutableList()
            val analysisWithWorkflow = analysis.copy(workflowId = workflowId)
            analyses.add(analysisWithWorkflow)
            
            val jsonArray = JSONArray()
            analyses.forEach { analysisResult ->
                val json = JSONObject().apply {
                    put("originalText", analysisResult.originalText)
                    put("analysis", analysisResult.analysis)
                    put("documentType", analysisResult.documentType)
                    put("imagePath", analysisResult.imagePath)
                    put("timestamp", analysisResult.timestamp)
                    put("date", analysisResult.date)
                    put("workflowId", analysisResult.workflowId)
                }
                jsonArray.put(json)
            }
            
            analysesFile.writeText(jsonArray.toString())
            
            Log.d(TAG, "Analysis saved successfully for workflow $workflowId. Total analyses: ${analyses.size}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving analysis for workflow $workflowId", e)
            false
        }
    }

    /**
     * Load analyses for a specific workflow and date
     */
    fun getAnalysesForWorkflowAndDate(workflowId: String, date: String): List<DocumentAnalysisResult> {
        return try {
            val allAnalyses = loadAllAnalyses()
            val filtered = allAnalyses.filter { it.workflowId == workflowId && it.date == date }
            Log.d(TAG, "Loaded ${filtered.size} analyses for workflow $workflowId on date $date")
            filtered
        } catch (e: Exception) {
            Log.e(TAG, "Error loading analyses for workflow $workflowId and date $date", e)
            emptyList()
        }
    }

    /**
     * Clear analyses for a specific workflow and date
     */
    fun clearAnalysesForWorkflowAndDate(workflowId: String, date: String): Boolean {
        return try {
            val allAnalyses = loadAllAnalyses().toMutableList()
            val initialSize = allAnalyses.size
            allAnalyses.removeAll { it.workflowId == workflowId && it.date == date }
            
            val jsonArray = JSONArray()
            allAnalyses.forEach { analysis ->
                val json = JSONObject().apply {
                    put("originalText", analysis.originalText)
                    put("analysis", analysis.analysis)
                    put("documentType", analysis.documentType)
                    put("imagePath", analysis.imagePath)
                    put("timestamp", analysis.timestamp)
                    put("date", analysis.date)
                    put("workflowId", analysis.workflowId)
                }
                jsonArray.put(json)
            }
            
            analysesFile.writeText(jsonArray.toString())
            
            val clearedCount = initialSize - allAnalyses.size
            Log.d(TAG, "Cleared $clearedCount analyses for workflow $workflowId on date $date")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing analyses for workflow $workflowId and date $date", e)
            false
        }
    }

    /**
     * Save document analysis result
     */
    fun saveAnalysis(analysis: DocumentAnalysisResult): Boolean {
        return try {
            Log.d(TAG, "Saving analysis for ${analysis.documentType}")
            
            val analyses = loadAllAnalyses().toMutableList()
            analyses.add(analysis)
            
            val jsonArray = JSONArray()
            analyses.forEach { analysisResult ->
                val json = JSONObject().apply {
                    put("originalText", analysisResult.originalText)
                    put("analysis", analysisResult.analysis)
                    put("documentType", analysisResult.documentType)
                    put("imagePath", analysisResult.imagePath)
                    put("timestamp", analysisResult.timestamp)
                    put("date", analysisResult.date)
                    put("workflowId", analysisResult.workflowId)
                }
                jsonArray.put(json)
            }
            
            analysesFile.writeText(jsonArray.toString())
            
            Log.d(TAG, "Analysis saved successfully. Total analyses: ${analyses.size}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving analysis", e)
            false
        }
    }
    
    /**
     * Load analyses for a specific date
     */
    fun getAnalysesForDate(date: String): List<DocumentAnalysisResult> {
        return try {
            val allAnalyses = loadAllAnalyses()
            val filtered = allAnalyses.filter { it.date == date }
            Log.d(TAG, "Loaded ${filtered.size} analyses for date $date")
            filtered
        } catch (e: Exception) {
            Log.e(TAG, "Error loading analyses for date $date", e)
            emptyList()
        }
    }
    
    /**
     * Load analyses for today
     */
    fun getTodaysAnalyses(): List<DocumentAnalysisResult> {
        val today = getCurrentDateString()
        return getAnalysesForDate(today)
    }
    
    /**
     * Load all analyses
     */
    fun loadAllAnalyses(): List<DocumentAnalysisResult> {
        return try {
            if (!analysesFile.exists()) {
                Log.d(TAG, "No analyses file found, returning empty list")
                return emptyList()
            }
            
            val jsonText = analysesFile.readText()
            val jsonArray = JSONArray(jsonText)
            
            val analyses = mutableListOf<DocumentAnalysisResult>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val analysis = DocumentAnalysisResult(
                    originalText = json.getString("originalText"),
                    analysis = json.getString("analysis"),
                    documentType = json.getString("documentType"),
                    imagePath = json.getString("imagePath"),
                    timestamp = json.getLong("timestamp"),
                    date = json.getString("date"),
                    workflowId = json.optString("workflowId", "") // Handle both old and new format
                )
                analyses.add(analysis)
            }
            
            Log.d(TAG, "Loaded ${analyses.size} total analyses")
            analyses
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading analyses", e)
            emptyList()
        }
    }
    
    /**
     * Get analyses for date range
     */
    fun getAnalysesForDateRange(startDate: String, endDate: String): List<DocumentAnalysisResult> {
        return try {
            val allAnalyses = loadAllAnalyses()
            val filtered = allAnalyses.filter { analysis ->
                analysis.date >= startDate && analysis.date <= endDate
            }
            Log.d(TAG, "Loaded ${filtered.size} analyses for date range $startDate to $endDate")
            filtered
        } catch (e: Exception) {
            Log.e(TAG, "Error loading analyses for date range", e)
            emptyList()
        }
    }
    
    /**
     * Clear analyses for a specific date
     */
    fun clearAnalysesForDate(date: String): Boolean {
        return try {
            val allAnalyses = loadAllAnalyses().toMutableList()
            val initialSize = allAnalyses.size
            allAnalyses.removeAll { it.date == date }
            
            val jsonArray = JSONArray()
            allAnalyses.forEach { analysis ->
                val json = JSONObject().apply {
                    put("originalText", analysis.originalText)
                    put("analysis", analysis.analysis)
                    put("documentType", analysis.documentType)
                    put("imagePath", analysis.imagePath)
                    put("timestamp", analysis.timestamp)
                    put("date", analysis.date)
                    put("workflowId", analysis.workflowId)
                }
                jsonArray.put(json)
            }
            
            analysesFile.writeText(jsonArray.toString())
            
            val clearedCount = initialSize - allAnalyses.size
            Log.d(TAG, "Cleared $clearedCount analyses for date $date")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing analyses for date $date", e)
            false
        }
    }
    
    /**
     * Get document count by type for today
     */
    fun getTodaysDocumentCounts(): Map<String, Int> {
        return try {
            val todaysAnalyses = getTodaysAnalyses()
            val counts = mutableMapOf<String, Int>()
            
            todaysAnalyses.forEach { analysis ->
                val type = analysis.documentType
                counts[type] = counts.getOrDefault(type, 0) + 1
            }
            
            Log.d(TAG, "Today's document counts: $counts")
            counts
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's document counts", e)
            emptyMap()
        }
    }
    
    private fun getCurrentDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date())
    }
}
