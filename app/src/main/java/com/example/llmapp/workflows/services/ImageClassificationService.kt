package com.example.llmapp.workflows.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Service for classifying images as either person photos or documents
 * Follows Single Responsibility Principle - only handles image classification
 */
class ImageClassificationService(private val context: Context) {
    
    // Lazy initialization to avoid ML Kit context issues in background services
    private val textRecognizer by lazy { 
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val imageLabeler by lazy { 
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }
    
    @Volatile
    private var mlKitInitialized: Boolean = false
    
    // Ensure ML Kit is initialized
    private fun ensureMLKitInitialized() {
        try {
            if (!mlKitInitialized) {
                MlKit.initialize(context)
                mlKitInitialized = true
                Log.d(TAG, "ML Kit initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit", e)
        }
    }
    
    companion object {
        private const val TAG = "ImageClassificationService"
        private const val TEXT_DENSITY_THRESHOLD = 0.15f // 15% of image should contain text for document classification
        private const val CONFIDENCE_THRESHOLD = 0.7f
    }
    
    /**
     * Classify image type using ML Kit with callback-based approach
     */
    suspend fun classifyImage(imagePath: String): ImageClassification {
        try {
            Log.d(TAG, "Classifying image with ML Kit: $imagePath")
            
            // Ensure ML Kit is initialized on main thread
            withContext(Dispatchers.Main) {
                ensureMLKitInitialized()
            }
            
            val bitmap = loadBitmap(imagePath)
            if (bitmap == null) {
                return ImageClassification.UNKNOWN
            }
            
            // Create InputImage and process on main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation {
                        // Cleanup resources if needed when cancelled
                    }
                    
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        var textResult: com.google.mlkit.vision.text.Text? = null
                        var labelResult: List<com.google.mlkit.vision.label.ImageLabel>? = null
                        var completedTasks = 0
                        
                        // Process text recognition
                        textRecognizer.process(image)
                            .addOnSuccessListener { text ->
                                textResult = text
                                completedTasks++
                                if (completedTasks == 2) {
                                    processResults(textResult, labelResult, bitmap, continuation)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Text recognition failed", e)
                                continuation.resume(ImageClassification.UNKNOWN)
                            }
                        
                        // Process image labeling
                        imageLabeler.process(image)
                            .addOnSuccessListener { labels ->
                                labelResult = labels
                                completedTasks++
                                if (completedTasks == 2) {
                                    processResults(textResult, labelResult, bitmap, continuation)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Image labeling failed", e)
                                continuation.resume(ImageClassification.UNKNOWN)
                            }
                            
                    } catch (e: Exception) {
                        Log.e(TAG, "ML Kit processing failed", e)
                        continuation.resume(ImageClassification.UNKNOWN)
                    }
                }
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit classification failed - this is required for proper image classification", e)
            return ImageClassification.UNKNOWN
        }
    }
    
    private fun processResults(
        textResult: com.google.mlkit.vision.text.Text?,
        labelResult: List<com.google.mlkit.vision.label.ImageLabel>?,
        bitmap: Bitmap,
        continuation: kotlinx.coroutines.CancellableContinuation<ImageClassification>
    ) {
        try {
            if (textResult == null || labelResult == null) {
                continuation.resume(ImageClassification.UNKNOWN)
                return
            }
            
            // Log all detected labels for debugging
            Log.d(TAG, "Detected ${labelResult.size} labels:")
            labelResult.forEach { label ->
                Log.d(TAG, "Label: '${label.text}' with confidence: ${label.confidence}")
            }
            
            // Analyze text density
            val textDensity = calculateTextDensity(textResult.text, bitmap.width * bitmap.height)
            Log.d(TAG, "Extracted text: '${textResult.text.take(100)}...' (${textResult.text.length} chars)")
            
            // Analyze image labels for person detection with lower confidence threshold
            val hasPersonLabel = labelResult.any { label ->
                val labelText = label.text.lowercase()
                val isPersonRelated = (labelText.contains("person") || 
                                     labelText.contains("face") ||
                                     labelText.contains("human") ||
                                     labelText.contains("people") ||
                                     labelText.contains("man") ||
                                     labelText.contains("woman") ||
                                     labelText.contains("child") ||
                                     labelText.contains("selfie") ||
                                     labelText.contains("portrait") ||
                                     // Body parts that indicate a person
                                     labelText.contains("ear") ||
                                     labelText.contains("eye") ||
                                     labelText.contains("nose") ||
                                     labelText.contains("mouth") ||
                                     labelText.contains("hair") ||
                                     labelText.contains("hand") ||
                                     labelText.contains("finger") ||
                                     labelText.contains("arm") ||
                                     labelText.contains("leg") ||
                                     labelText.contains("foot") ||
                                     labelText.contains("head") ||
                                     labelText.contains("neck") ||
                                     labelText.contains("skin") ||
                                     labelText.contains("flesh") ||
                                     labelText.contains("eyelash") ||
                                     labelText.contains("eyebrow") ||
                                     labelText.contains("lip") ||
                                     labelText.contains("cheek") ||
                                     labelText.contains("forehead"))
                
                if (isPersonRelated) {
                    Log.d(TAG, "Found person-related label: '${label.text}' with confidence: ${label.confidence}")
                }
                
                isPersonRelated && label.confidence > 0.5f // Lower threshold for person detection
            }
            
            // Analyze labels for document indicators with lower confidence threshold
            val hasDocumentLabel = labelResult.any { label ->
                val labelText = label.text.lowercase()
                val isDocumentRelated = (labelText.contains("text") || 
                                        labelText.contains("document") ||
                                        labelText.contains("paper") ||
                                        labelText.contains("receipt") ||
                                        labelText.contains("card") ||
                                        labelText.contains("book") ||
                                        labelText.contains("page") ||
                                        labelText.contains("writing") ||
                                        labelText.contains("letter") ||
                                        labelText.contains("note") ||
                                        labelText.contains("form") ||
                                        labelText.contains("invoice") ||
                                        labelText.contains("contract") ||
                                        labelText.contains("magazine") ||
                                        labelText.contains("newspaper"))
                
                if (isDocumentRelated) {
                    Log.d(TAG, "Found document-related label: '${label.text}' with confidence: ${label.confidence}")
                }
                
                isDocumentRelated && label.confidence > 0.5f // Lower threshold for documents too
            }
            
            val classification = when {
                // If we have substantial text (>50 characters) or high text density, it's likely a document
                (textResult.text.length > 50 || textDensity > TEXT_DENSITY_THRESHOLD) && !hasPersonLabel -> ImageClassification.DOCUMENT
                // If we explicitly detected document labels
                hasDocumentLabel && !hasPersonLabel -> ImageClassification.DOCUMENT
                // If we have person-related labels and low text density, it's a person
                hasPersonLabel && textDensity < TEXT_DENSITY_THRESHOLD -> ImageClassification.PERSON
                // If we have person labels regardless of text (selfies might have some text)
                hasPersonLabel -> ImageClassification.PERSON
                // If substantial text content but no clear person indicators
                textResult.text.length > 100 -> ImageClassification.DOCUMENT
                else -> ImageClassification.UNKNOWN
            }
            
            Log.d(TAG, "ML Kit classification result: $classification")
            Log.d(TAG, "Text density: $textDensity, Person label: $hasPersonLabel, Document label: $hasDocumentLabel")
            
            continuation.resume(classification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ML Kit results", e)
            continuation.resume(ImageClassification.UNKNOWN)
        }
    }
    
    /**
     * Extract text from document image using ML Kit with callback-based approach
     */
    suspend fun extractTextFromDocument(imagePath: String): String? {
        try {
            Log.d(TAG, "Extracting text from document with ML Kit: $imagePath")
            
            // Ensure ML Kit is initialized on main thread
            withContext(Dispatchers.Main) {
                ensureMLKitInitialized()
            }
            
            val bitmap = loadBitmap(imagePath)
            if (bitmap == null) {
                return null
            }
            
            // Create InputImage and process on main thread
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation {
                        // Cleanup resources if needed when cancelled
                    }
                    
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        
                        textRecognizer.process(image)
                            .addOnSuccessListener { result ->
                                val extractedText = result.text
                                Log.d(TAG, "Extracted ${extractedText.length} characters from document")
                                continuation.resume(if (extractedText.isBlank()) null else extractedText)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "ML Kit text extraction failed", e)
                                continuation.resume(null)
                            }
                            
                    } catch (e: Exception) {
                        Log.e(TAG, "ML Kit text extraction processing failed", e)
                        continuation.resume(null)
                    }
                }
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from document", e)
            return null
        }
    }
    
    private fun loadBitmap(imagePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                // Load with reasonable size to avoid memory issues
                inSampleSize = 2
                inJustDecodeBounds = false
            }
            BitmapFactory.decodeFile(imagePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap: $imagePath", e)
            null
        }
    }
    
    private fun calculateTextDensity(text: String, imageArea: Int): Float {
        if (text.isBlank() || imageArea <= 0) return 0f
        
        // Rough estimation: assume each character takes about 100 pixels
        val textArea = text.length * 100
        return (textArea.toFloat() / imageArea).coerceAtMost(1f)
    }
}

/**
 * Image classification results
 */
enum class ImageClassification {
    PERSON,    // Image contains primarily a person/people
    DOCUMENT,  // Image contains text/document content
    UNKNOWN    // Unable to classify or mixed content
}
