package com.example.llmapp.workflows.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Simple image processing service using ML Kit
 */
class ImageProcessingService(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageProcessingService"
    }
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f) // Detect smaller faces (10% of image)
            .enableTracking()
            .build()
    )
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    
    /**
     * Extract text from image
     */
    suspend fun extractTextFromImage(imagePath: String): String = suspendCancellableCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                continuation.resume("")
                return@suspendCancellableCoroutine
            }
            
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                continuation.resume("")
                return@suspendCancellableCoroutine
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                    continuation.resume("")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from image", e)
            continuation.resume("")
        }
    }
    
    /**
     * Detect faces in image
     */
    suspend fun detectFacesInImage(imagePath: String): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "Image file does not exist: $imagePath")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            Log.d(TAG, "Loading image for face detection: $imagePath")
            
            // Try to load image with proper orientation handling
            val bitmap = loadBitmapWithOrientation(imagePath)
            if (bitmap == null) {
                Log.w(TAG, "Could not load bitmap from: $imagePath")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            Log.d(TAG, "Image loaded successfully. Size: ${bitmap.width}x${bitmap.height}")
            
            val image = InputImage.fromBitmap(bitmap, 0)
            
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    Log.d(TAG, "Face detection completed. Found ${faces.size} faces")
                    
                    faces.forEachIndexed { index, face ->
                        Log.d(TAG, "Face $index: bounds=${face.boundingBox}, " +
                                "smilingProbability=${face.smilingProbability}, " +
                                "leftEyeOpenProbability=${face.leftEyeOpenProbability}, " +
                                "rightEyeOpenProbability=${face.rightEyeOpenProbability}")
                    }
                    
                    continuation.resume(faces.isNotEmpty())
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed for: $imagePath", e)
                    continuation.resume(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting faces in image: $imagePath", e)
            continuation.resume(false)
        }
    }
    
    private fun loadBitmapWithOrientation(imagePath: String): Bitmap? {
        return try {
            // First, get the image dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            // Calculate sample size for large images
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1024, 1024)
            
            // Load the bitmap
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }
            
            val bitmap = BitmapFactory.decodeFile(imagePath, loadOptions) ?: return null
            
            // Handle EXIF orientation
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap with orientation", e)
            null
        }
    }
    
    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Label objects in image
     */
    suspend fun labelImage(imagePath: String): List<String> = suspendCancellableCoroutine { continuation ->
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            
            val image = InputImage.fromBitmap(bitmap, 0)
            
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val labelTexts = labels.map { it.text }
                    continuation.resume(labelTexts)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Image labeling failed", e)
                    continuation.resume(emptyList())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error labeling image", e)
            continuation.resume(emptyList())
        }
    }
    
    /**
     * Check if image appears to be a receipt based on text content
     */
    suspend fun isReceiptImage(imagePath: String): Boolean {
        val text = extractTextFromImage(imagePath).lowercase()
        
        val receiptKeywords = listOf(
            "receipt", "total", "tax", "subtotal", "amount", "paid", "visa", "mastercard",
            "cash", "change", "thank you", "store", "shop", "$", "price", "qty", "item"
        )
        
        val foundKeywords = receiptKeywords.count { keyword ->
            text.contains(keyword)
        }
        
        // If we find 3 or more receipt-related keywords, likely a receipt
        return foundKeywords >= 3
    }
}
