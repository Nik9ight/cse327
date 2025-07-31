interface OutputFormatter {
    fun formatOutput(message: Message): String
}
// This interface defines a method to format the output of a message.
// The `formatOutput` method takes a `Message` object and returns a formatted string representation of that message.