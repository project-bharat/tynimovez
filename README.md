# Baby Movement Counter

A Marathi-first Android app for recording baby movements during a two-hour session. Built with Kotlin, Jetpack Compose Material 3, Room, and offline-first local storage. Minimum Android 7.0 (API 24).

## Features
- Manual baby movement counter with undo.
- Two-hour movement sessions with resume support.
- Optional offline voice counting in Marathi and English using Sherpa-ONNX models.
- Automatic voice-model download and local installation; recognition runs offline after installation.
- Session strength notes, daily history, monthly report, and weekly totals.
- Review, confirm, or dismiss automatic movement detections.
- Reminders, JSON backup/restore, CSV export, home-screen widget, and Baby-only quick-start shortcut.

## Voice models
- Sherpa-ONNX Marathi and English models are the only downloadable voice models.
- Models are installed in app-private storage; audio is processed on-device and is not saved as recordings.
- Microphone access is used only when the user enables voice counting.

## Safety
This app is a personal movement diary, not a medical device, fetal monitor, or fetal-wellbeing assessment. It does not define a universal movement target. If the baby's usual movement pattern changes or movements reduce, contact your maternity-care professional promptly; do not wait for an app timer or target.

## Build
Use JDK 17 and Android SDK 35. The GitHub Actions workflow runs unit tests and builds the debug APK:
```sh
./gradlew testDebugUnitTest assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Download from GitHub Actions
1. Open the repository's **Actions** tab.
2. Open the latest successful **Android Debug APK** run.
3. Under **Artifacts**, download `counterapp-debug-apk`.
4. Extract the ZIP to get `app-debug.apk`.

Pushes to `main` and `baby-movement-only` run the build workflow.

## Backup and Google Drive setup

Manual device backup uses Android's file picker and works without Google setup.
It covers the five Room tables and imports the previous version-1 counter JSON.
The pregnancy timeline and reminder preferences are managed separately.

To enable Pro Google Drive backup in a Play test build:

1. Enable the Google Drive API in Google Cloud and configure an OAuth consent
   screen for `https://www.googleapis.com/auth/drive.appdata`. Add test users
   if the consent screen is in testing.
2. Create an Android OAuth client for `com.projectbharat.counterapp` and the
   signing certificate SHA-1 used by the installed build. Register the debug
   SHA-1 for local testing and the Play App Signing SHA-1 for Play installs.
3. Create a Web OAuth client in the same project. Paste its public client ID
   into `default_web_client_id` in `app/src/main/res/values/strings.xml`.
   Keep the OAuth client secret out of the Android project.
4. Activate a Google Play subscription with an eligible base plan. Match its
   product ID to `pro_subscription_product_id` in the same resource file.
   Install from a Play test track and add the account as a license tester.
5. Open **Backup & sync**, subscribe with the test account, connect Google
   Drive, and select **Sync now** or enable the 12-hour **Auto-sync** job.

Drive backups are whole-database snapshots. Automatic restore requires a known
updated Drive revision and an unchanged local snapshot. A fresh installation
with an existing cloud backup uses **Restore from cloud** after confirmation.
Divergent snapshots require an explicit choice; export a local copy before
restoring. This is snapshot sync and has no per-record merge. Use one active
writer at a time. The client rechecks the Drive revision before upload and
sends an HTTP precondition on responses that provide an ETag. The JSON file
is stored in the Google account's app-specific folder without app-level
encryption.
