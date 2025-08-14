# Enhanced Image Workflow Pipeline

## Overview
This enhanced image workflow pipeline provides comprehensive monitoring and processing of camera images with intelligent classification, person detection, document analysis, and automated reporting.

## Architecture (SOLID Principles)

### 1. Single Responsibility Principle
- **ImageClassificationService**: Only handles image classification (person vs document)
- **DocumentAnalysisService**: Only handles LLM-based document analysis
- **DocumentStorageService**: Only handles data persistence
- **EnhancedImageProcessingService**: Coordinates the complete pipeline
- **WorkflowConfigManager**: Only manages workflow configurations
- **DailySummaryScheduler**: Only handles scheduling

### 2. Open/Closed Principle
- Services are open for extension (new document types, new analysis methods)
- Closed for modification (core functionality remains stable)

### 3. Liskov Substitution Principle
- Service interfaces can be replaced with implementations
- Consistent behavior across different service implementations

### 4. Interface Segregation Principle
- Each service has focused, specific interfaces
- No unnecessary dependencies between services

### 5. Dependency Inversion Principle
- High-level modules depend on abstractions
- Services are injected rather than hard-coded

## Key Features

### 📷 Person Detection & Forwarding
- **Face Matching**: Compare detected persons with reference images
- **Similarity Analysis**: Advanced algorithms with landmark comparison
- **Selective Forwarding**: Only forward matching persons or all persons
- **Multiple Channels**: Send via Telegram and/or Gmail

### 📄 Document Analysis
- **Text Recognition**: ML Kit OCR for text extraction
- **LLM Analysis**: Local LLM generates concise reports
- **Document Types**: Receipts, prescriptions, ID cards, contracts, etc.
- **Immediate or Batched**: Send analysis immediately or wait for daily summary

### 📊 Daily Summaries
- **Automated Scheduling**: Configurable time for daily reports
- **Comprehensive Reports**: LLM generates summary of all daily documents
- **Multiple Formats**: Telegram messages and HTML emails

### ⚙️ Workflow Management
- **Multiple Workflows**: Create different workflows for different needs
- **Flexible Configuration**: Mix and match features per workflow
- **Easy Management**: Edit, delete, and monitor workflows

## How It Works

### 1. Image Monitoring
```
Camera Folder → File Observer → New Image Detected → Processing Queue
```

### 2. Image Processing Pipeline
```
New Image → Classification (Person/Document) → Branch Processing

Person Branch:
└── Face Detection → Reference Comparison → Similarity Check → Forward if Match

Document Branch:
└── Text Extraction → LLM Analysis → Storage → Optional Immediate Send
```

### 3. Daily Summary Process
```
Scheduler → Collect Day's Analyses → LLM Summary Generation → Send Report → Clear Data
```

## Usage Instructions

### Setup Process

1. **Sign In to Services**
   - Use "SIGN IN WITH GOOGLE" button on home screen for Gmail
   - Configure Telegram bot token and chat ID for Telegram

2. **Create Workflows**
   - Open "Enhanced Image Workflows" from home screen
   - Click "Create New Workflow"
   - Configure features as needed

3. **Start Monitoring**
   - Use "Start Service" button in workflow list
   - Service runs continuously in background

### Workflow Configuration Options

#### Person Forwarding
- **Enable Person Forwarding**: Detect and forward person images
- **Reference Image**: Set via existing ImageWorkflowActivity
- **Non-matching Notifications**: Also notify for different persons

#### Document Analysis
- **Document Type**: Specify what type of documents to expect
- **Immediate Analysis**: Send analysis right away
- **LLM Processing**: Uses local Gemma model for analysis

#### Daily Summary
- **Summary Time**: When to send daily reports (HH:MM format)
- **Clear After Summary**: Remove processed data after sending
- **Automatic Scheduling**: Runs every day at specified time

#### Communication
- **Telegram**: Send via configured Telegram bot
- **Gmail**: Send to specified email address
- **Both**: Use multiple channels simultaneously

### Example Workflows

#### 1. Receipt Tracking Workflow
```
Name: "Daily Receipts"
Document Analysis: ✓ (Type: receipts)
Daily Summary: ✓ (Time: 20:00)
Communication: Gmail to personal@email.com
```

#### 2. Security Monitoring
```
Name: "Home Security"
Person Forwarding: ✓ (Reference image set)
Non-matching Persons: ✓
Communication: Telegram (immediate alerts)
```

#### 3. Document Processing Office
```
Name: "Office Documents" 
Document Analysis: ✓ (Type: contracts)
Immediate Analysis: ✓
Daily Summary: ✓ (Time: 17:00)
Communication: Both Telegram and Gmail
```

## Technical Details

### Dependencies
- **ML Kit**: Face detection, text recognition, image labeling
- **Local LLM**: Gemma 3n model for document analysis
- **Services**: Telegram Bot API, Gmail API
- **Storage**: SharedPreferences, JSON files
- **Scheduling**: Coroutines with Timer-based scheduling

### Performance Considerations
- **Background Processing**: All analysis runs on background threads
- **Memory Management**: Images loaded with appropriate sampling
- **Error Handling**: Comprehensive error handling and logging
- **Resource Cleanup**: Proper service lifecycle management

### Privacy & Security
- **Local Processing**: LLM runs entirely on device
- **No Cloud Uploads**: Images processed locally only
- **Secure Storage**: Configurations stored in app private directory
- **Permission Management**: Only required permissions requested

## Troubleshooting

### Service Not Starting
- Check permissions (storage, camera access)
- Verify battery optimization disabled
- Check service status in workflow list

### Analysis Not Working
- Verify LLM model is properly loaded
- Check log output for initialization errors
- Ensure sufficient device storage and RAM

### Communications Failing
- Verify Telegram bot token and chat ID
- Check Gmail sign-in status
- Test connections using workflow test feature

### Daily Summary Not Sending
- Verify scheduler is running
- Check configured time format (HH:MM)
- Ensure workflows have document analysis enabled

## Advanced Features

### Custom Document Types
- Modify `DocumentAnalysisService` to add new document categories
- Update spinner in workflow creation UI

### Enhanced Face Matching
- Adjust similarity thresholds in `FaceMatcher`
- Add additional facial features for comparison

### Custom Scheduling
- Extend `DailySummaryScheduler` for weekly/monthly reports
- Add custom time intervals

### Multiple Reference Images
- Extend `ReferenceImageManager` for multiple reference persons
- Add person identification by name

## File Structure
```
workflows/
├── services/
│   ├── ImageClassificationService.kt          # ML Kit classification
│   ├── DocumentAnalysisService.kt             # LLM document analysis
│   ├── DocumentStorageService.kt              # Data persistence
│   ├── EnhancedImageProcessingService.kt      # Main pipeline coordinator
│   └── ImageWorkflowBackgroundService.kt      # Background monitoring
├── managers/
│   └── WorkflowConfigManager.kt               # Configuration management
├── schedulers/
│   └── DailySummaryScheduler.kt               # Daily report scheduling
└── ui/
    ├── EnhancedWorkflowCreateActivity.kt      # Workflow creation
    └── EnhancedWorkflowListActivity.kt        # Workflow management
```

This implementation provides a robust, scalable, and maintainable image processing pipeline that follows SOLID principles while delivering powerful document analysis and person detection capabilities.
