package com.example.llmapp.workflows.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * Interface for reference image storage (Dependency Inversion Principle)
 */
interface ReferenceImageStorage {
    fun saveReferenceImagePath(workflowId: String, imagePath: String)
    fun getReferenceImagePath(workflowId: String): String?
    fun removeReferenceImage(workflowId: String)
    fun hasReferenceImage(workflowId: String): Boolean
}

/**
 * Implementation for storing reference image paths using SharedPreferences
 */
class SharedPreferencesReferenceStorage(context: Context) : ReferenceImageStorage {
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        "reference_images", Context.MODE_PRIVATE
    )
    
    override fun saveReferenceImagePath(workflowId: String, imagePath: String) {
        sharedPrefs.edit().putString(workflowId, imagePath).apply()
    }
    
    override fun getReferenceImagePath(workflowId: String): String? {
        return sharedPrefs.getString(workflowId, null)
    }
    
    override fun removeReferenceImage(workflowId: String) {
        sharedPrefs.edit().remove(workflowId).apply()
    }
    
    override fun hasReferenceImage(workflowId: String): Boolean {
        val path = getReferenceImagePath(workflowId)
        return !path.isNullOrEmpty() && File(path).exists()
    }
}

/**
 * Manager for reference images (Single Responsibility Principle)
 * Follows Open/Closed Principle - can be extended without modification
 */
class ReferenceImageManager(
    private val faceMatchingService: FaceMatchingService,
    private val storage: ReferenceImageStorage
) {
    
    companion object {
        private const val TAG = "ReferenceImageManager"
    }
    
    private val referenceFeaturesCache = mutableMapOf<String, FaceFeatures>()
    
    /**
     * Set reference image for a workflow
     */
    suspend fun setReferenceImage(workflowId: String, imagePath: String): Boolean {
        return try {
            Log.d(TAG, "Setting reference image for workflow: $workflowId")
            
            val features = faceMatchingService.extractReferenceFaceFeatures(imagePath)
            if (features != null) {
                storage.saveReferenceImagePath(workflowId, imagePath)
                referenceFeaturesCache[workflowId] = features
                Log.d(TAG, "Reference image set successfully for workflow: $workflowId")
                true
            } else {
                Log.w(TAG, "Failed to extract face features from reference image")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting reference image", e)
            false
        }
    }
    
    /**
     * Check if reference person is in the given image
     */
    suspend fun isReferencePresentInImage(workflowId: String, imagePath: String): Boolean {
        val features = getReferenceFeatures(workflowId) ?: return false
        return faceMatchingService.isReferencePresentInImage(imagePath, features)
    }
    
    /**
     * Get reference image path for workflow
     */
    fun getReferenceImagePath(workflowId: String): String? {
        return storage.getReferenceImagePath(workflowId)
    }
    
    /**
     * Remove reference image for workflow
     */
    fun removeReferenceImage(workflowId: String) {
        storage.removeReferenceImage(workflowId)
        referenceFeaturesCache.remove(workflowId)
    }
    
    /**
     * Check if workflow has reference image
     */
    fun hasReferenceImage(workflowId: String): Boolean {
        return storage.hasReferenceImage(workflowId)
    }
    
    private suspend fun getReferenceFeatures(workflowId: String): FaceFeatures? {
        // Check cache first
        referenceFeaturesCache[workflowId]?.let { return it }
        
        // Load from storage if not in cache
        val imagePath = storage.getReferenceImagePath(workflowId) ?: return null
        if (!File(imagePath).exists()) return null
        
        val features = faceMatchingService.extractReferenceFaceFeatures(imagePath)
        if (features != null) {
            referenceFeaturesCache[workflowId] = features
        }
        
        return features
    }
}
