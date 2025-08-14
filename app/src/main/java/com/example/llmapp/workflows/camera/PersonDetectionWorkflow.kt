package com.example.llmapp.workflows.camera

import android.content.Context
import android.util.Log
import com.example.llmapp.TelegramService
import com.example.llmapp.workflows.services.ImageProcessingService
import com.example.llmapp.workflows.services.ReferenceImageManager
import com.example.llmapp.workflows.services.FaceMatchingService
import com.example.llmapp.workflows.services.SharedPreferencesReferenceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Person Detection Workflow - detects people in images and forwards to Telegram
 * Now supports reference-based face matching for specific person detection
 */
class PersonDetectionWorkflow(
    private val context: Context,
    private val telegramChatId: String,
    private val targetSubject: String? = null,
    private val workflowId: String = "person_detection"
) {
    
    companion object {
        private const val TAG = "PersonDetectionWorkflow"
    }
    
    private val imageProcessingService = ImageProcessingService(context)
    private val telegramService = TelegramService(context)
    private val faceMatchingService = FaceMatchingService(context)
    private val referenceImageManager = ReferenceImageManager(
        faceMatchingService,
        SharedPreferencesReferenceStorage(context)
    )
    
    /**
     * Set reference image for specific person detection
     */
    suspend fun setReferenceImage(imagePath: String): Boolean {
        return referenceImageManager.setReferenceImage(workflowId, imagePath)
    }
    
    /**
     * Check if reference image is set
     */
    fun hasReferenceImage(): Boolean {
        return referenceImageManager.hasReferenceImage(workflowId)
    }
    
    /**
     * Get reference image path
     */
    fun getReferenceImagePath(): String? {
        return referenceImageManager.getReferenceImagePath(workflowId)
    }
    
    /**
     * Remove reference image
     */
    fun removeReferenceImage() {
        referenceImageManager.removeReferenceImage(workflowId)
    }
    
    /**
     * Process image for person detection
     * If reference image is set, only forwards if specific person is detected
     */
    suspend fun processImageForPerson(imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing image for person detection: $imagePath")
            
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "Image file does not exist: $imagePath")
                return@withContext false
            }
            
            // Check if reference image is set for specific person detection
            val hasReference = hasReferenceImage()
            
            if (hasReference) {
                Log.d(TAG, "Reference-based person detection enabled")
                
                // Check if reference person is present in the image
                val isReferencePresent = referenceImageManager.isReferencePresentInImage(workflowId, imagePath)
                
                if (isReferencePresent) {
                    Log.d(TAG, "Reference person detected in image: $imagePath")
                    
                    // Get additional context from image labeling
                    val imageLabels = imageProcessingService.labelImage(imagePath)
                    
                    // Send the image with notification
                    val success = sendPersonDetectionNotification(imagePath, imageLabels, isSpecificPerson = true)
                    
                    return@withContext success
                } else {
                    Log.d(TAG, "Reference person not found in image: $imagePath")
                    return@withContext false
                }
            } else {
                Log.d(TAG, "General person detection mode (no reference)")
                
                // Detect any faces/people in the image
                val hasFaces = imageProcessingService.detectFacesInImage(imagePath)
                
                if (hasFaces) {
                    Log.d(TAG, "Person detected in image: $imagePath")
                    
                    // Get additional context from image labeling
                    val imageLabels = imageProcessingService.labelImage(imagePath)
                    
                    // Send the image with notification
                    val success = sendPersonDetectionNotification(imagePath, imageLabels, isSpecificPerson = false)
                    
                    return@withContext success
                } else {
                    Log.d(TAG, "No person detected in image: $imagePath")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image for person detection", e)
            false
        }
    }
    
    /**
     * Send person detection notification with image
     */
    private suspend fun sendPersonDetectionNotification(
        imagePath: String, 
        imageLabels: List<String>,
        isSpecificPerson: Boolean = false
    ): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val file = File(imagePath)
            val fileName = file.name
            val fileSize = file.length() / 1024 // Size in KB
            val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            
            val caption = buildString {
                if (isSpecificPerson) {
                    appendLine("🎯 SPECIFIC PERSON DETECTED!")
                } else {
                    appendLine("👤 PERSON DETECTED!")
                }
                appendLine("━━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                appendLine("📸 Image: $fileName")
                appendLine("📅 Time: ${dateFormatter.format(Date())}")
                
                if (isSpecificPerson) {
                    appendLine("✅ Reference Match: Confirmed")
                }
                
                if (targetSubject != null) {
                    appendLine("🎯 Target: $targetSubject")
                }
                
                if (imageLabels.isNotEmpty()) {
                    appendLine("🏷️ Context: ${imageLabels.take(3).joinToString(", ")}")
                }
                
                appendLine("📏 Size: ${fileSize}KB")
                appendLine()
                appendLine("🤖 Auto-detected by LLMAPP")
            }
            
            // Store original chat ID and switch to workflow chat
            val originalChatId = telegramService.getChatId()
            telegramService.setChatId(telegramChatId)
            
            // Send the actual image with caption
            telegramService.sendPhoto(
                imagePath = imagePath,
                caption = caption,
                onSuccess = {
                    Log.d(TAG, "Person detection image sent successfully")
                    telegramService.setChatId(originalChatId ?: "")
                    continuation.resume(true)
                },
                onError = { error ->
                    Log.e(TAG, "Failed to send person detection image: $error")
                    
                    // Fallback: try sending just the text notification
                    telegramService.sendMessage(
                        text = caption + "\n\n⚠️ *Image could not be sent: $error*",
                        onSuccess = {
                            Log.d(TAG, "Person detection text sent as fallback")
                            telegramService.setChatId(originalChatId ?: "")
                            continuation.resume(true)
                        },
                        onError = { fallbackError ->
                            Log.e(TAG, "Failed to send fallback notification: $fallbackError")
                            telegramService.setChatId(originalChatId ?: "")
                            continuation.resume(false)
                        }
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending person detection notification", e)
            continuation.resume(false)
        }
    }
}
