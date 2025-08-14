package com.example.llmapp.workflows.debug

import android.content.Context
import android.util.Log
import com.example.llmapp.workflows.services.FaceMatchingService
import kotlinx.coroutines.*

/**
 * Debug utility for testing face matching between two images
 * This can be used to test and tune the face matching algorithm
 */
class FaceMatchingDebugger(private val context: Context) {
    
    companion object {
        private const val TAG = "FaceMatchingDebugger"
    }
    
    private val faceMatchingService = FaceMatchingService(context)
    
    /**
     * Compare two images and return detailed similarity information
     */
    suspend fun compareTwoImages(imagePath1: String, imagePath2: String): FaceComparisonResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Comparing images:")
                Log.d(TAG, "   Reference: $imagePath1")
                Log.d(TAG, "   Candidate: $imagePath2")
                
                // Extract features from both images
                val features1 = faceMatchingService.extractReferenceFaceFeatures(imagePath1)
                val features2 = faceMatchingService.extractReferenceFaceFeatures(imagePath2)
                
                if (features1 == null) {
                    Log.w(TAG, "❌ No face found in reference image: $imagePath1")
                    return@withContext FaceComparisonResult(
                        success = false,
                        errorMessage = "No face found in reference image",
                        similarity = 0f
                    )
                }
                
                if (features2 == null) {
                    Log.w(TAG, "❌ No face found in candidate image: $imagePath2")
                    return@withContext FaceComparisonResult(
                        success = false,
                        errorMessage = "No face found in candidate image", 
                        similarity = 0f
                    )
                }
                
                // Check if they match using the face matching service
                val isMatch = faceMatchingService.isPersonInImage(imagePath2, features1)
                
                Log.d(TAG, "📊 Face Comparison Results:")
                Log.d(TAG, "   Same Person: ${if (isMatch) "✅ YES" else "❌ NO"}")
                
                return@withContext FaceComparisonResult(
                    success = true,
                    isMatch = isMatch,
                    similarity = 0f, // The detailed similarity is logged in FaceMatcher
                    referenceFeatures = features1,
                    candidateFeatures = features2
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error comparing images", e)
                return@withContext FaceComparisonResult(
                    success = false,
                    errorMessage = e.message ?: "Unknown error",
                    similarity = 0f
                )
            }
        }
    }
    
    /**
     * Test the face matching system with multiple candidate images against one reference
     */
    suspend fun testMultipleCandidates(
        referenceImagePath: String, 
        candidateImagePaths: List<String>
    ): List<FaceComparisonResult> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "🧪 Testing multiple candidates against reference: $referenceImagePath")
            
            val results = mutableListOf<FaceComparisonResult>()
            
            for ((index, candidatePath) in candidateImagePaths.withIndex()) {
                Log.d(TAG, "Testing candidate ${index + 1}/${candidateImagePaths.size}: $candidatePath")
                val result = compareTwoImages(referenceImagePath, candidatePath)
                results.add(result)
            }
            
            // Summary
            val matches = results.count { it.isMatch }
            Log.d(TAG, "📈 Test Summary: ${matches}/${results.size} candidates matched the reference")
            
            return@withContext results
        }
    }
}

/**
 * Result of face comparison
 */
data class FaceComparisonResult(
    val success: Boolean,
    val isMatch: Boolean = false,
    val similarity: Float,
    val errorMessage: String? = null,
    val referenceFeatures: com.example.llmapp.workflows.services.FaceFeatures? = null,
    val candidateFeatures: com.example.llmapp.workflows.services.FaceFeatures? = null
)
