package com.example.llmapp
// Hardcoded model data class with only essential fields
data class Model(
    val name: String,
    val version: String,
    val downloadFileName: String,
    val url: String,
    val sizeInBytes: Long,
    val info: String = "",
    val learnMoreUrl: String = "",
    val isZip: Boolean = false,
    val unzipDir: String = "",
    val llmSupportImage: Boolean = true,
    val llmSupportAudio: Boolean = false,
    var instance: Any? = null
)

// Hardcoded models
val MODEL_TEXT_CLASSIFICATION_MOBILEBERT = Model(
    name = "MobileBert",
    version = "1.0",
    downloadFileName = "bert_classifier.tflite",
    url = "https://storage.googleapis.com/mediapipe-models/text_classifier/bert_classifier/float32/latest/bert_classifier.tflite",
    sizeInBytes = 25707538L,
    info = "Model is trained on movie reviews dataset. Type a movie review below and see the scores of positive or negative sentiment.",
    learnMoreUrl = "https://ai.google.dev/edge/mediapipe/solutions/text/text_classifier"
)

val MODEL_TEXT_CLASSIFICATION_AVERAGE_WORD_EMBEDDING = Model(
    name = "Average word embedding",
    version = "1.0",
    downloadFileName = "average_word_classifier.tflite",
    url = "https://storage.googleapis.com/mediapipe-models/text_classifier/average_word_classifier/float32/latest/average_word_classifier.tflite",
    sizeInBytes = 775708L,
    info = "Model is trained on movie reviews dataset. Type a movie review below and see the scores of positive or negative sentiment."
)

val MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1 = Model(
    name = "Mobilenet V1",
    version = "1.0",
    downloadFileName = "mobilenet_v1.tflite",
    url = "https://storage.googleapis.com/tfweb/app_gallery_models/mobilenet_v1.tflite",
    sizeInBytes = 16900760L,
    info = "",
    learnMoreUrl = "https://ai.google.dev/edge/litert/android"
)

val MODEL_IMAGE_CLASSIFICATION_MOBILENET_V2 = Model(
    name = "Mobilenet V2",
    version = "1.0",
    downloadFileName = "mobilenet_v2.tflite",
    url = "https://storage.googleapis.com/tfweb/app_gallery_models/mobilenet_v2.tflite",
    sizeInBytes = 13978596L,
    info = ""
)

val MODEL_IMAGE_GENERATION_STABLE_DIFFUSION = Model(
    name = "Stable diffusion",
    version = "1.0",
    downloadFileName = "sd15.zip",
    url = "https://storage.googleapis.com/tfweb/app_gallery_models/sd15.zip",
    sizeInBytes = 1906219565L,
    isZip = true,
    unzipDir = "sd15",
    info = "Powered by [MediaPipe Image Generation API](https://ai.google.dev/edge/mediapipe/solutions/vision/image_generator/android)",
    learnMoreUrl = "https://huggingface.co/litert-community"
)

val EMPTY_MODEL = Model(
    name = "empty",
    version = "1.0",
    downloadFileName = "empty.tflite",
    url = "",
    sizeInBytes = 0L
)

// Hardcoded model collections
val MODELS_TEXT_CLASSIFICATION = listOf(
    MODEL_TEXT_CLASSIFICATION_MOBILEBERT,
    MODEL_TEXT_CLASSIFICATION_AVERAGE_WORD_EMBEDDING
)

val MODELS_IMAGE_CLASSIFICATION = listOf(
    MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1,
    MODEL_IMAGE_CLASSIFICATION_MOBILENET_V2
)

val MODELS_IMAGE_GENERATION = listOf(
    MODEL_IMAGE_GENERATION_STABLE_DIFFUSION
)