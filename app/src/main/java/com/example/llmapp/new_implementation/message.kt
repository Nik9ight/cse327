data class Message(
    val sender: String,
    val recipient: String,
    val content: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)
