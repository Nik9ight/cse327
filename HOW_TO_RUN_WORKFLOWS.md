# How to Run Saved Workflows - Complete Guide

## 🔄 How Workflows Execute

Your image workflows can run in **three different ways**:

### 1. **🚀 Manual Execution (New Feature!)**

**Steps:**
1. Open **Image Workflows** from home screen
2. Find your saved workflow in the list
3. Tap the **▶️ Play button** next to the workflow name
4. Select "Choose Image" in the dialog
5. Pick an image from your gallery
6. Workflow processes the image immediately!

**What happens:**
- **IMAGE_FORWARD workflows**: Compares selected image with reference image for person matching
- **DOCUMENT_ANALYSIS workflows**: Analyzes selected document image and sends results

### 2. **🔄 Automatic Background Execution**

**How it works:**
- Service monitors your camera folder (`/DCIM/Camera/`)
- When you take a new photo, all active workflows automatically process it
- Runs in background without user interaction

**To enable:**
1. Open **Image Workflows**
2. Tap **"Start Service"** button
3. Grant permissions when prompted
4. Service runs persistently in background

**Service features:**
- ✅ Monitors camera folder 24/7
- ✅ Processes new images within seconds
- ✅ Persistent notification shows status
- ✅ Survives app restarts and reboots

### 3. **⏰ Scheduled Execution (Document Analysis)**

**For DOCUMENT_ANALYSIS workflows only:**
- Sends **daily summary reports** at scheduled time
- Summarizes all document analyses from that day
- Automatically sent via Gmail/Telegram based on workflow settings

## 🎯 Workflow Types & Execution

### **📸 IMAGE_FORWARD Workflows**

**Purpose**: Person detection and matching
**Execution**: 
- Takes any input image
- Detects if it contains a person
- Compares with your reference image
- Sends match/no-match notification via Gmail/Telegram

**Manual run**: Select any photo to test person matching
**Auto run**: Processes every new camera photo

### **📄 DOCUMENT_ANALYSIS Workflows**

**Purpose**: Document analysis and summarization
**Execution**:
- Analyzes document content using AI
- Extracts key information
- Stores analysis results
- Sends daily summaries at scheduled time

**Manual run**: Select document photo for immediate analysis
**Auto run**: Processes new document photos automatically

## 🎮 User Interface Guide

### **Workflow List Items**

Each workflow shows:
```
[Workflow Name]                    [⭐] [▶️] [🗑️]
[Type: Image Forward/Document Analysis]
[Destination: Gmail/Telegram info]
[Details: Reference/Schedule info]
```

**Buttons:**
- **⭐ Star**: Mark as favorite (starred workflows show first)
- **▶️ Play**: Run workflow manually with selected image
- **🗑️ Delete**: Remove workflow (with confirmation)

### **Service Control**

At bottom of workflow list:
```
Service Status: Running/Stopped
[Start Service] [Stop Service]
```

## 📱 Step-by-Step: Running Your First Workflow

### **Quick Manual Run:**
1. **Home** → **"Image Workflows"**
2. Find your workflow
3. Tap **▶️ Play button**
4. **"Choose Image"**
5. Select photo from gallery
6. Watch for success notification! ✅

### **Enable Background Processing:**
1. **Home** → **"Image Workflows"**
2. Scroll to bottom
3. Tap **"Start Service"**
4. Grant camera/storage permissions
5. Service now monitors camera folder! 📸

### **Check Daily Summaries:**
1. Create **DOCUMENT_ANALYSIS** workflow
2. Set schedule time (e.g., 6:00 PM)
3. Service automatically sends daily summaries
4. Check your Gmail/Telegram for reports! 📊

## ⚙️ Advanced Features

### **Testing Workflows**
- Manual run = Perfect for testing workflow setup
- Test with known images to verify behavior
- Check Gmail/Telegram to confirm delivery

### **Service Management**
- **Start Service**: Enable automatic image processing
- **Stop Service**: Disable background monitoring
- Service status shown in notification bar

### **Multiple Workflows**
- All active workflows process each new image
- Different workflows can have different destinations
- Mix IMAGE_FORWARD and DOCUMENT_ANALYSIS workflows

## 🔧 Troubleshooting

### **Manual Run Issues:**
- ❌ **"Error selecting image"**: Check gallery app permissions
- ❌ **"Workflow failed"**: Check Gmail/Telegram configuration
- ❌ **No notification**: Verify destination settings in workflow

### **Background Service Issues:**
- ❌ **Service won't start**: Check camera/storage permissions
- ❌ **Images not processing**: Ensure images are in `/DCIM/Camera/`
- ❌ **Service stops**: Check battery optimization settings

### **Daily Summary Issues:**
- ❌ **No summaries**: Ensure DOCUMENT_ANALYSIS workflow is active
- ❌ **Wrong time**: Check workflow schedule time setting
- ❌ **Not receiving**: Verify Gmail/Telegram configuration

## 🎉 Success Indicators

### **Manual Run Success:**
- ✅ **"Workflow [Name] executed successfully!"** toast message
- ✅ Gmail/Telegram notification received
- ✅ Results match expected workflow behavior

### **Background Service Success:**
- ✅ Persistent notification: **"Enhanced Image Workflow"**
- ✅ Notification shows active workflow count
- ✅ New photos trigger automatic processing

### **Daily Summary Success:**
- ✅ Daily summary email/message received
- ✅ Contains analysis of all processed documents
- ✅ Sent at scheduled time consistently

---

**🚀 Pro Tip**: Start with manual runs to test your workflows, then enable background service for automatic processing!
