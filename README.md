# LLM APP

LLM APP is an Android application for building and running personal automation workflows across Gmail, Telegram, local LLM inference, geofencing, and image processing. It includes direct pipeline screens, saved workflow management, background services, and image/document workflows that can run manually or from a foreground service.

## Features

- Google sign-in with Gmail read, modify, and send scopes.
- Telegram Bot API setup for bot token validation, chat ID storage, and message delivery.
- Gmail -> Telegram and Telegram -> Gmail processing pipelines.
- Saved workflow management with reusable workflow configurations.
- Local LLM processing through MediaPipe LLM inference.
- Image workflows for person/reference matching and document or receipt analysis.
- Camera folder monitoring through a foreground service.
- Scheduled document summaries and background workflow execution.
- Google Maps based geofence management.
- Battery optimization guidance for long-running background services.

## Tech Stack

- Kotlin and Android Gradle Plugin.
- Min SDK 30, target SDK 35, compile SDK 35.
- Java 11 bytecode.
- AndroidX, Material, WorkManager, DataStore, CameraX, and Hilt.
- Google Sign-In, Gmail API, Google Maps, Firebase Analytics.
- Telegram Bot API through OkHttp.
- MediaPipe, TensorFlow Lite, and ML Kit for local AI/image features.

## Prerequisites

- Android Studio with Android SDK 35 installed.
- JDK 11.
- An Android device or emulator running API 30 or newer.
- A Google Cloud project configured for package name `com.example.llmapp`.
- Gmail API and Google Maps SDK enabled in Google Cloud.
- An Android OAuth client with the app SHA-1 fingerprint configured.
- A valid `google-services.json` for the Google/Firebase project.
- A Telegram bot token from BotFather.
- For local LLM flows, the model file expected by the app:

```text
/data/local/tmp/llm/gemma-3n-E2B-it-int4.task
```

## Setup

1. Open the repository in Android Studio and let Gradle sync.

2. Add or replace the Google services configuration for this app module. This project currently expects:

```text
app/src/google-services.json
```

3. Add the Maps API key to the root `local.properties` file:

```properties
GOOGLE_MAPS_API_KEY=your_google_maps_api_key
```

4. If you use local LLM features, push the model to the device:

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma-3n-E2B-it-int4.task /data/local/tmp/llm/
```

5. Create a Telegram bot with BotFather and keep the bot token ready. The app stores the token and chat ID locally through its Telegram setup screens.

6. Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

7. Install it on a connected device:

```bash
./gradlew :app:installDebug
```

## Running the App

After launch:

1. Use `SIGN IN WITH GOOGLE` on the home screen to connect a Google account.
2. Use the permissions button to grant storage, camera, location, notification, and background permissions.
3. Configure Telegram with a bot token and chat ID where required.
4. Disable battery optimization when prompted if workflows need to run in the background.

Main entry points from the home screen:

- `Pipeline`: run direct Gmail -> Telegram or Telegram -> Gmail flows.
- `Your Workflows`: manage saved Gmail/Telegram workflow configurations.
- `New Workflows`: access the newer strategy/factory based workflow system.
- `Image Workflows`: create and run image/document workflows.
- `Geofence`: manage location-triggered behavior with Google Maps.
- `LLM`: open local text or multimodal LLM demo screens.

## Tests

Run local unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run instrumentation tests on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Project Structure

```text
app/
├── build.gradle.kts                         # Android app module config
├── src/main/AndroidManifest.xml             # Activities, services, receivers, permissions
├── src/main/java/com/example/llmapp/
│   ├── HomeActivity.kt                      # Main launcher screen
│   ├── GmailService.kt                      # Gmail API integration
│   ├── TelegramService.kt                   # Telegram Bot API integration
│   ├── pipeline/                            # Message processing pipeline components
│   ├── workflow/                            # Saved workflow models, manager, repository
│   ├── new_implementation/                  # Pattern-based workflow implementation
│   ├── workflows/                           # Image/document workflow system
│   ├── geofence/                            # Geofence UI and receiver
│   ├── service/                             # Background workflow services
│   ├── receiver/                            # Boot, alarm, and keep-alive receivers
│   └── utils/                               # Permission, network, Telegram, and service helpers
└── src/main/res/                            # XML layouts, drawables, menus, themes, fonts
```

## Notes for Development

- Keep secrets out of source control. Do not commit real bot tokens, API keys, service account files, or model binaries.
- Gmail sign-in uses the package name `com.example.llmapp`; Google Cloud OAuth settings must match that package and the debug/release SHA-1 fingerprints.
- Background execution depends on foreground services, wake locks, boot receivers, exact alarms, and battery optimization settings. Test these flows on a physical Android device when possible.
- Image workflows monitor common camera directories such as `/DCIM/Camera/` and process `.jpg`, `.jpeg`, and `.png` files.
- The local LLM path is currently hardcoded in `llmchatmodel.kt`.

## Troubleshooting

- Google sign-in shows `DEVELOPER_ERROR`: verify package name, SHA-1 fingerprint, enabled APIs, OAuth client, and `google-services.json`.
- Gmail workflows cannot read or send mail: sign out and sign in again so the Gmail scopes can be requested.
- Telegram messages fail: verify the bot token, chat ID, network connection, and bot permissions in groups.
- Maps screen is blank: check `GOOGLE_MAPS_API_KEY`, enabled Maps SDK, and API key restrictions.
- LLM initialization fails: confirm the model exists at `/data/local/tmp/llm/gemma-3n-E2B-it-int4.task`.
- Background workflows stop unexpectedly: grant notification/location/storage permissions and disable battery optimization for the app.
