package com.example.llmapp.workflows.services

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.*

/**
 * Service for face matching and comparison
 * Follows Single Responsibility Principle - only handles face matching logic
 */
class FaceMatchingService(private val context: Context) {
    
    companion object {
        private const val TAG = "FaceMatchingService"
        private const val SIMILARITY_THRESHOLD = 0.5f // Lower threshold for better differentiation
        private const val MAX_IMAGE_SIZE = 1024
    }
    
    // Lazy initialization to avoid ML Kit context issues in background services
    private val faceDetector by lazy { 
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f)
                .enableTracking()
                .build()
        )
    }
    
    /**
     * Extract face features from reference image
     */
    suspend fun extractReferenceFaceFeatures(imagePath: String): FaceFeatures? {
        return try {
            // Check if file exists first
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Reference image file does not exist: $imagePath")
                return null
            }
            
            val bitmap = loadAndOrientBitmap(imagePath) ?: return null
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val faces = faceDetector.process(image).await()
            
            if (faces.isEmpty()) {
                Log.w(TAG, "No faces found in reference image")
                return null
            }
            
            // Use the largest face as reference
            val referenceFace = faces.maxByOrNull { 
                it.boundingBox.width() * it.boundingBox.height() 
            } ?: return null
            
            extractFeaturesFromFace(referenceFace, bitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting face features from: $imagePath", e)
            null
        }
    }
    
    /**
     * Check if specific person is present in image based on reference features
     */
    suspend fun isPersonInImage(imagePath: String, referenceFeatures: FaceFeatures): Boolean {
        return try {
            // Check if file exists first
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file does not exist: $imagePath")
                return false
            }
            
            val bitmap = loadAndOrientBitmap(imagePath) ?: return false
            val image = InputImage.fromBitmap(bitmap, 0)
            
            val faces = faceDetector.process(image).await()
            
            for (face in faces) {
                val features = extractFeaturesFromFace(face, bitmap)
                if (features != null) {
                    val similarity = compareFeatures(features, referenceFeatures)
                    Log.d(TAG, "Face comparison - Similarity: $similarity, Threshold: $SIMILARITY_THRESHOLD")
                    
                    if (similarity > SIMILARITY_THRESHOLD) {
                        Log.d(TAG, "✅ Face match found! Similarity: $similarity")
                        return true
                    } else {
                        Log.d(TAG, "❌ Face doesn't match. Similarity: $similarity")
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if person is in image: $imagePath", e)
            false
        }
    }
    
    /**
     * Check if reference person is present in the given image
     */
    suspend fun isReferencePresentInImage(imagePath: String, referenceFeatures: FaceFeatures): Boolean {
        return isPersonInImage(imagePath, referenceFeatures)
    }
    
    /**
     * Compare two face features and return similarity score
     */
    private fun compareFeatures(candidate: FaceFeatures, reference: FaceFeatures): Float {
        val similarity = FaceMatcher.calculateSimilarity(reference, candidate)
        Log.d(TAG, "Face similarity calculated: $similarity (threshold: $SIMILARITY_THRESHOLD)")
        return similarity
    }
    
    private fun extractFeaturesFromFace(face: Face, bitmap: Bitmap): FaceFeatures? {
        return try {
            val boundingBox = face.boundingBox
            
            // Validate bounding box
            if (boundingBox.width() <= 0 || boundingBox.height() <= 0) return null
            
            // Extract safe face region
            val faceRegion = extractSafeFaceRegion(bitmap, boundingBox) ?: return null
            
            FaceFeatures(
                boundingBoxRatio = boundingBox.width().toFloat() / boundingBox.height().toFloat(),
                faceSize = boundingBox.width() * boundingBox.height(),
                leftEyeOpenProbability = face.leftEyeOpenProbability ?: 0.5f,
                rightEyeOpenProbability = face.rightEyeOpenProbability ?: 0.5f,
                smilingProbability = face.smilingProbability ?: 0.5f,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ,
                landmarks = face.allLandmarks.associate { 
                    it.landmarkType to Pair(it.position.x, it.position.y) 
                },
                faceHistogram = ColorHistogramExtractor.extract(faceRegion)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting face features", e)
            null
        }
    }
    
    private fun extractSafeFaceRegion(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
        val left = maxOf(0, boundingBox.left)
        val top = maxOf(0, boundingBox.top)
        val right = minOf(bitmap.width, boundingBox.right)
        val bottom = minOf(bitmap.height, boundingBox.bottom)
        
        val width = right - left
        val height = bottom - top
        
        if (width <= 0 || height <= 0) return null
        
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
    
    private fun loadAndOrientBitmap(imagePath: String): Bitmap? {
        return try {
            // Load with size constraints
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            val sampleSize = calculateSampleSize(
                options.outWidth, 
                options.outHeight, 
                MAX_IMAGE_SIZE, 
                MAX_IMAGE_SIZE
            )
            
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            var bitmap = BitmapFactory.decodeFile(imagePath, loadOptions) ?: return null
            
            // Handle orientation
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, 
                ExifInterface.ORIENTATION_NORMAL
            )
            
            bitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
            
            bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: $imagePath", e)
            null
        }
    }
    
    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / sampleSize) >= reqHeight && 
                   (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/**
 * Data class representing face features
 */
data class FaceFeatures(
    val boundingBoxRatio: Float,
    val faceSize: Int,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float,
    val smilingProbability: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val landmarks: Map<Int, Pair<Float, Float>>,
    val faceHistogram: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as FaceFeatures
        
        return boundingBoxRatio == other.boundingBoxRatio &&
                faceSize == other.faceSize &&
                leftEyeOpenProbability == other.leftEyeOpenProbability &&
                rightEyeOpenProbability == other.rightEyeOpenProbability &&
                smilingProbability == other.smilingProbability &&
                headEulerAngleY == other.headEulerAngleY &&
                headEulerAngleZ == other.headEulerAngleZ &&
                landmarks == other.landmarks &&
                faceHistogram.contentEquals(other.faceHistogram)
    }
    
    override fun hashCode(): Int {
        var result = boundingBoxRatio.hashCode()
        result = 31 * result + faceSize
        result = 31 * result + leftEyeOpenProbability.hashCode()
        result = 31 * result + rightEyeOpenProbability.hashCode()
        result = 31 * result + smilingProbability.hashCode()
        result = 31 * result + headEulerAngleY.hashCode()
        result = 31 * result + headEulerAngleZ.hashCode()
        result = 31 * result + landmarks.hashCode()
        result = 31 * result + faceHistogram.contentHashCode()
        return result
    }
}

/**
 * Utility for face matching (Open/Closed Principle - can be extended)
 */
object FaceMatcher {
    
    fun matches(reference: FaceFeatures, candidate: FaceFeatures, threshold: Float): Boolean {
        val similarity = calculateSimilarity(reference, candidate)
        Log.d("FaceMatcher", "Face similarity: $similarity (threshold: $threshold)")
        return similarity >= threshold
    }
    
    fun calculateSimilarity(reference: FaceFeatures, candidate: FaceFeatures): Float {
        var totalSimilarity = 0f
        var totalWeight = 0f
        
        // Face shape similarity (less weight since it can vary with pose)
        val shapeSimilarity = calculateShapeSimilarity(reference, candidate)
        totalSimilarity += shapeSimilarity * 0.15f
        totalWeight += 0.15f
        
        // Landmark similarity (most discriminative)
        val landmarkSimilarity = calculateLandmarkSimilarity(reference, candidate)
        totalSimilarity += landmarkSimilarity * 0.45f
        totalWeight += 0.45f
        
        // Color histogram similarity
        val colorSimilarity = calculateColorSimilarity(reference, candidate)
        totalSimilarity += colorSimilarity * 0.3f
        totalWeight += 0.3f
        
        // Head pose similarity (less important but useful)
        val poseSimilarity = calculatePoseSimilarity(reference, candidate)
        totalSimilarity += poseSimilarity * 0.1f
        totalWeight += 0.1f
        
        val finalSimilarity = if (totalWeight > 0) totalSimilarity / totalWeight else 0f
        Log.d("FaceMatcher", "Similarity breakdown - Shape: $shapeSimilarity, Landmarks: $landmarkSimilarity, Color: $colorSimilarity, Pose: $poseSimilarity, Final: $finalSimilarity")
        
        return finalSimilarity
    }
    
    private fun calculateShapeSimilarity(reference: FaceFeatures, candidate: FaceFeatures): Float {
        return 1f - abs(reference.boundingBoxRatio - candidate.boundingBoxRatio) / 2f
    }
    
    private fun calculateLandmarkSimilarity(reference: FaceFeatures, candidate: FaceFeatures): Float {
        if (reference.landmarks.isEmpty() || candidate.landmarks.isEmpty()) {
            Log.d("FaceMatcher", "No landmarks available for comparison")
            return 0.5f // Neutral score when no landmarks
        }
        
        var totalDistance = 0f
        var landmarkCount = 0
        
        // Compare common landmarks
        for ((landmarkType, refPos) in reference.landmarks) {
            candidate.landmarks[landmarkType]?.let { candPos ->
                val distance = sqrt((refPos.first - candPos.first).pow(2) + (refPos.second - candPos.second).pow(2))
                totalDistance += distance
                landmarkCount++
            }
        }
        
        if (landmarkCount == 0) return 0.5f
        
        val averageDistance = totalDistance / landmarkCount
        // Convert distance to similarity (smaller distance = higher similarity)
        // Normalize by assuming max meaningful distance is ~100 pixels
        val similarity = maxOf(0f, 1f - (averageDistance / 100f))
        
        Log.d("FaceMatcher", "Landmark similarity: $similarity (avg distance: $averageDistance)")
        return similarity
    }
    
    private fun calculatePoseSimilarity(reference: FaceFeatures, candidate: FaceFeatures): Float {
        val yAngleDiff = abs(reference.headEulerAngleY - candidate.headEulerAngleY)
        val zAngleDiff = abs(reference.headEulerAngleZ - candidate.headEulerAngleZ)
        
        // Normalize angles (assume max meaningful difference is 45 degrees)
        val yAngleSim = maxOf(0f, 1f - (yAngleDiff / 45f))
        val zAngleSim = maxOf(0f, 1f - (zAngleDiff / 45f))
        
        return (yAngleSim + zAngleSim) / 2f
    }
    
    private fun calculateExpressionSimilarity(reference: FaceFeatures, candidate: FaceFeatures): Float {
        val eyeSimilarity = 1f - (abs(reference.leftEyeOpenProbability - candidate.leftEyeOpenProbability) +
                abs(reference.rightEyeOpenProbability - candidate.rightEyeOpenProbability)) / 2f
        val smileSimilarity = 1f - abs(reference.smilingProbability - candidate.smilingProbability)
        return (eyeSimilarity + smileSimilarity) / 2f
    }
    
    private fun calculateColorSimilarity(reference: FaceFeatures, candidate: FaceFeatures): Float {
        if (reference.faceHistogram.size != candidate.faceHistogram.size) return 0f
        
        // Use Bhattacharyya coefficient for better discrimination
        var correlation = 0f
        for (i in reference.faceHistogram.indices) {
            correlation += sqrt(reference.faceHistogram[i] * candidate.faceHistogram[i])
        }
        
        // Also calculate histogram intersection for additional validation
        var intersection = 0f
        for (i in reference.faceHistogram.indices) {
            intersection += minOf(reference.faceHistogram[i], candidate.faceHistogram[i])
        }
        
        // Combine both measures (Bhattacharyya is more discriminative)
        val finalSimilarity = (correlation * 0.7f + intersection * 0.3f)
        
        Log.d("FaceMatcher", "Color similarity - Bhattacharyya: $correlation, Intersection: $intersection, Final: $finalSimilarity")
        return finalSimilarity
    }
}

/**
 * Utility for color histogram extraction (Single Responsibility Principle)
 */
object ColorHistogramExtractor {
    
    private const val HISTOGRAM_SIZE = 64
    private const val FACE_RESIZE = 32
    
    fun extract(faceBitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, FACE_RESIZE, FACE_RESIZE, false)
        val pixels = IntArray(FACE_RESIZE * FACE_RESIZE)
        resizedBitmap.getPixels(pixels, 0, FACE_RESIZE, 0, 0, FACE_RESIZE, FACE_RESIZE)
        
        val histogram = FloatArray(HISTOGRAM_SIZE)
        
        pixels.forEach { pixel ->
            val r = (Color.red(pixel) / 64).coerceIn(0, 3)
            val g = (Color.green(pixel) / 64).coerceIn(0, 3)
            val b = (Color.blue(pixel) / 64).coerceIn(0, 3)
            val bin = r * 16 + g * 4 + b
            histogram[bin] += 1f
        }
        
        // Normalize histogram
        val sum = histogram.sum()
        if (sum > 0) {
            for (i in histogram.indices) {
                histogram[i] /= sum
            }
        }
        
        return histogram
    }
}
