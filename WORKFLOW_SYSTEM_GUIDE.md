# Workflow Management System - User Guide

## Overview

The Workflow Management System provides a comprehensive solution for creating, managing, and executing automated pipelines between Gmail and Telegram. This system implements multiple design patterns to ensure scalability, maintainability, and extensibility.

## Architecture

### Design Patterns Implemented

1. **Repository Pattern** - `WorkflowRepository.kt`
   - Abstracts data persistence using SharedPreferences
   - Provides clean separation between data layer and business logic

2. **Factory Pattern** - `WorkflowFactory.kt`
   - Creates different pipeline types based on configuration
   - Encapsulates object creation complexity

3. **Strategy Pattern** - `WorkflowManager.kt`
   - Supports different execution strategies for workflows
   - Allows runtime selection of processing algorithms

4. **Template Method Pattern** - `WorkflowTemplate.kt`
   - Provides predefined workflow configurations
   - Defines common workflow structure with customizable steps

5. **Observer Pattern** - `WorkflowManager.kt`
   - Notifies listeners about workflow execution events
   - Supports real-time status updates

6. **Command Pattern** - `WorkflowExecution.kt`
   - Encapsulates workflow execution as objects
   - Supports undo/redo operations and execution queuing

## Key Components

### 1. Workflow Models (`WorkflowModels.kt`)

```kotlin
// Workflow types
enum class WorkflowType {
    GMAIL_TO_TELEGRAM,
    TELEGRAM_TO_GMAIL
}

// Configuration classes
sealed class WorkflowConfiguration : Parcelable {
    abstract fun validate(): ValidationResult
}

data class GmailToTelegramConfig(
    val gmailSearchQuery: String,
    val telegramBotToken: String,
    val telegramChatId: String,
    val llmPrompt: String,
    val emailLimit: Int
) : WorkflowConfiguration()

data class TelegramToGmailConfig(
    val telegramBotToken: String,
    val telegramChatId: String,
    val gmailRecipients: List<String>,
    val gmailSender: String,
    val llmPrompt: String,
    val messageLimit: Int,
    val emailTemplate: String
) : WorkflowConfiguration()
```

### 2. Workflow Manager (`WorkflowManager.kt`)

Central management component that coordinates all workflow operations:

```kotlin
class WorkflowManager(context: Context) {
    // Create workflows
    suspend fun createWorkflow(
        name: String,
        type: WorkflowType,
        configuration: WorkflowConfiguration
    ): Result<SavedWorkflow>
    
    // Execute workflows
    suspend fun executeWorkflow(workflowId: String): Result<WorkflowExecutionResult>
    
    // Manage workflows
    suspend fun getAllWorkflows(): Result<List<SavedWorkflow>>
    suspend fun updateWorkflow(id: String, name: String, configuration: WorkflowConfiguration): Result<SavedWorkflow>
    suspend fun deleteWorkflow(workflowId: String): Result<Unit>
}
```

### 3. User Interface

#### Home Screen Integration
- Added "Your Workflows" button in `HomeActivity`
- Direct access to workflow management from main menu

#### Workflow List (`WorkflowListActivity`)
- Displays all saved workflows
- Shows workflow type, status, and last execution time
- Provides actions: Run, Edit, Copy, Delete
- Empty state guidance for new users

#### Workflow Creation/Editing (`WorkflowCreateActivity`)
- Form-based workflow configuration
- Template selection for quick setup
- Real-time validation
- Test functionality before saving

## Usage Guide

### Creating a Workflow

1. **Access Workflow Management**
   - Open the app and tap "Your Workflows" from the home screen

2. **Create New Workflow**
   - Tap the "Create Workflow" button
   - Choose workflow type (Gmail → Telegram or Telegram → Gmail)
   - Optionally select a template for quick setup

3. **Configure Workflow**
   - **Gmail to Telegram:**
     - Gmail search query (e.g., "is:unread from:important@domain.com")
     - Telegram bot token
     - Telegram chat ID
     - LLM processing prompt
     - Email limit per execution
   
   - **Telegram to Gmail:**
     - Telegram bot token
     - Telegram chat ID
     - Gmail recipient emails
     - Gmail sender email
     - LLM processing prompt
     - Message limit per execution
     - Email template style

4. **Validate and Save**
   - Use "Test" button to validate configuration
   - Save the workflow for future use

### Managing Workflows

#### Running Workflows
- From workflow list: Tap "Run" button on any workflow
- Individual execution with real-time status updates

#### Editing Workflows
- Tap "Edit" button to modify existing workflows
- All configuration parameters can be updated
- Maintains workflow history and metadata

#### Copying Workflows
- Use "Copy" button to duplicate existing workflows
- Useful for creating variations of successful configurations

#### Deleting Workflows
- Tap "Delete" button to remove workflows
- Confirmation dialog prevents accidental deletion

## Templates

### Pre-defined Templates

1. **Urgent Email Monitor**
   - Type: Gmail → Telegram
   - Query: "is:unread label:urgent"
   - Purpose: Monitor urgent emails and send alerts

2. **Daily Email Summary**
   - Type: Gmail → Telegram
   - Query: "is:unread newer_than:1d"
   - Purpose: Daily digest of unread emails

3. **Team Chat Archive**
   - Type: Telegram → Gmail
   - Purpose: Archive important team discussions
   - Template: Professional email format

4. **Support Ticket Bridge**
   - Type: Telegram → Gmail
   - Purpose: Convert Telegram support requests to email tickets

### Custom Templates

Create your own templates by:
1. Setting up a workflow with desired configuration
2. Saving successful configurations as templates
3. Reusing templates for similar workflows

## Best Practices

### Configuration Guidelines

1. **Gmail Search Queries**
   - Use specific labels: `label:important`
   - Filter by sender: `from:alerts@service.com`
   - Combine conditions: `is:unread from:boss@company.com`

2. **Telegram Setup**
   - Create dedicated bots for different purposes
   - Use appropriate chat IDs (private chats vs groups)
   - Test bot permissions before deployment

3. **LLM Prompts**
   - Be specific about desired output format
   - Include context about the target audience
   - Test prompts with sample data

### Security Considerations

1. **Token Management**
   - Store tokens securely in app storage
   - Regularly rotate bot tokens
   - Use environment-specific tokens for testing

2. **Access Control**
   - Limit bot permissions to required scopes
   - Monitor bot usage for suspicious activity
   - Use private chats for sensitive information

## Troubleshooting

### Common Issues

1. **Workflow Execution Fails**
   - Check internet connectivity
   - Verify bot token validity
   - Confirm chat ID accessibility
   - Review Gmail API permissions

2. **Configuration Validation Errors**
   - Ensure all required fields are filled
   - Verify email address formats
   - Check numeric field ranges
   - Validate Telegram token format

3. **No Workflows Showing**
   - Check app permissions
   - Verify storage access
   - Try creating a new workflow
   - Check error logs

### Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Invalid bot token" | Malformed Telegram token | Verify token format from BotFather |
| "Gmail authentication failed" | Missing Google account | Sign in through home screen |
| "Configuration validation failed" | Invalid parameters | Check all required fields |
| "Workflow execution timeout" | Network issues | Check connectivity and retry |

## API Integration

### Existing Pipeline Integration

The workflow system integrates seamlessly with existing pipeline classes:

- `GmailToTelegramPipeline.kt` - Enhanced for workflow execution
- `TelegramToGmailPipeline.kt` - Updated with workflow support
- `PipelineTemplate.kt` - Maintains backward compatibility

### Future Extensions

The system is designed for easy extension:

1. **New Workflow Types**
   - Add enum values to `WorkflowType`
   - Implement corresponding configuration classes
   - Update factory and manager classes

2. **Additional Integrations**
   - Slack integration
   - Discord integration
   - Custom webhook endpoints

3. **Advanced Features**
   - Scheduled execution
   - Conditional workflows
   - Multi-step pipelines

## Performance Considerations

### Optimization Strategies

1. **Caching**
   - Message caching prevents duplicate processing
   - Template caching for faster workflow creation

2. **Async Operations**
   - All API calls use Kotlin Coroutines
   - Background execution prevents UI blocking

3. **Resource Management**
   - Configurable limits prevent excessive API usage
   - Efficient JSON serialization for storage

### Monitoring

Track workflow performance through:
- Execution time logging
- Success/failure rates
- Resource usage metrics
- User interaction analytics

## Support and Maintenance

### Logging

Comprehensive logging is implemented throughout:
- Debug logs for development
- Info logs for user actions
- Warning logs for recoverable errors
- Error logs for critical failures

### Updates and Migration

The system supports:
- Backward compatibility for existing workflows
- Schema migration for configuration updates
- Graceful handling of deprecated features

For technical support or feature requests, refer to the project documentation or contact the development team.
