# New Implementation - Design Pattern Based Workflow System

## Overview

This package implements the automated workflow system as discussed in the ChatGPT conversation, following the recommended design patterns:

- **Strategy Pattern**: For different sources and destinations
- **Factory Pattern**: For dynamic object creation
- **Template Method Pattern**: For consistent processing workflows
- **Observer Pattern**: For event notifications (integrated with existing system)

## Architecture

```
Source (Gmail/Telegram) → Processor (LLM) → Destination (Gmail/Telegram)
```

### Core Components

#### 1. Message (Standardized Format)
```kotlin
data class Message(
    val id: String,
    val sender: String,
    val recipient: String,
    val content: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)
```

#### 2. Interfaces
- `MessageSource`: Fetch messages from sources
- `MessageDestination`: Send messages to destinations  
- `Processor`: Process messages with LLM
- `OutputFormatter`: Format messages for specific platforms

#### 3. Factories
- `SourceFactory`: Creates Gmail/Telegram sources
- `DestinationFactory`: Creates Gmail/Telegram destinations

#### 4. Implementations
- `GmailSource`: Integrates with existing GmailServiceAdapter
- `TelegramSource`: Integrates with existing TelegramSourceAdapter
- `GmailDestination`: Integrates with existing GmailService
- `TelegramDestination`: Integrates with existing TelegramService
- `LLMProcessor`: Integrates with existing LlmContentProcessor

## Usage Examples

### Basic Workflow
```kotlin
// Create source
val source = SourceFactory.create("gmail", mapOf(
    "context" to context,
    "searchQuery" to "is:unread"
))

// Create processor
val processor = LLMProcessor(context)

// Create destination
val destination = DestinationFactory.create("telegram", mapOf(
    "context" to context,
    "chatId" to "your_chat_id"
))

// Create and run workflow
val workflow = Workflow(source, processor, destination)
val success = workflow.run()
```

### Batch Processing (3 messages at a time)
```kotlin
val success = workflow.runBatch(batchSize = 3)
```

### Using the Bridge for Integration
```kotlin
val bridge = NewImplementationBridge(context)

// Create workflow matching existing pipeline
val workflow = bridge.createGmailToTelegramWorkflow(
    telegramBotToken = "your_token",
    telegramChatId = "your_chat_id",
    gmailSearchQuery = "is:unread"
)

// Execute with result handling
val result = bridge.executeWorkflow(workflow, useBatchMode = true)
```

## Testing

### Component Testing
```kotlin
val demo = WorkflowDemo(context)

// Test individual components
demo.testComponents { success, result ->
    Log.d("Test", result)
}
```

### Full Workflow Testing
```kotlin
// Test complete Gmail → Telegram workflow
demo.testGmailToTelegram(botToken, chatId) { success, result ->
    Log.d("Test", result)
}

// Test complete Telegram → Gmail workflow  
demo.testTelegramToGmail(botToken, recipients) { success, result ->
    Log.d("Test", result)
}
```

### Validation Against Existing System
```kotlin
val bridge = NewImplementationBridge(context)
bridge.validateAgainstExisting { validationResult ->
    Log.d("Validation", validationResult.details.joinToString("\n"))
}
```

## Configuration

### Gmail Source Configuration
```kotlin
val config = mapOf(
    "context" to context,
    "searchQuery" to "is:unread from:important@domain.com"
)
```

### Telegram Source Configuration
```kotlin
val config = mapOf(
    "botToken" to "your_bot_token"
)
```

### Gmail Destination Configuration
```kotlin
val config = mapOf(
    "context" to context,
    "recipients" to listOf("user1@example.com", "user2@example.com"),
    "senderEmail" to "sender@example.com"
)
```

### Telegram Destination Configuration
```kotlin
val config = mapOf(
    "context" to context,
    "chatId" to "your_chat_id",
    "botToken" to "your_bot_token"
)
```

## Integration with Existing System

This implementation is designed to work alongside the existing pipeline system:

1. **Gradual Migration**: Use `NewImplementationBridge` to gradually replace existing pipelines
2. **Backward Compatibility**: Results are formatted to match existing `PipelineResult` structure
3. **Shared Components**: Reuses existing adapters and services where possible
4. **Configuration Compatibility**: Accepts same configuration parameters as existing system

## Features

### Single Message Processing
- Fetch one message → Process with LLM → Send to destination
- Ideal for real-time processing

### Batch Processing  
- Fetch multiple messages → Combine and process with LLM → Send summary
- Ideal for periodic summaries and reducing LLM calls

### Error Handling
- Comprehensive error handling at each step
- Graceful fallbacks for network issues
- Detailed error reporting

### Formatting
- Platform-specific message formatting
- HTML email formatting for Gmail
- Markdown formatting for Telegram
- Batch summary formatting

## Design Pattern Benefits

1. **Strategy Pattern**: Easy to add new sources/destinations without changing existing code
2. **Factory Pattern**: Centralized object creation with configuration support
3. **Template Method**: Consistent workflow processing with customizable steps
4. **Dependency Injection**: All components are configurable and testable

## Future Extensions

The architecture supports easy extension:

- **New Sources**: Add WhatsApp, Slack, Discord sources
- **New Destinations**: Add any messaging platform
- **New Processors**: Add different LLM providers or processing strategies
- **New Formatters**: Add platform-specific formatting

## Files Structure

```
new_implementation/
├── Message.kt                    # Core message data class
├── Workflow.kt                   # Main workflow orchestrator
├── LLMProcessor.kt              # LLM processing implementation
├── interfaces/
│   ├── MessageSource.kt         # Source interface
│   ├── MessageDestination.kt    # Destination interface
│   ├── Processor.kt             # Processor interface
│   └── OutputFormatter.kt       # Formatter interface
├── factories/
│   ├── SourceFactory.kt         # Source factory
│   └── DestinationFactory.kt    # Destination factory
├── GmailSource.kt               # Gmail source implementation
├── TelegramSource.kt            # Telegram source implementation
├── GmailDestination.kt          # Gmail destination implementation
├── TelegramDestination.kt       # Telegram destination implementation
├── GmailFormatter.kt            # Gmail formatting
├── TelegramFormatter.kt         # Telegram formatting
├── demo/
│   ├── WorkflowDemo.kt          # Testing and demo class
│   └── NewImplementationTestActivity.kt # Test UI
└── integration/
    └── NewImplementationBridge.kt # Integration bridge
```

This implementation fulfills all requirements from the ChatGPT conversation and provides a solid foundation for the automated workflow system.
