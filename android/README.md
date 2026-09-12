# Upptagen Android App

A native Android companion app for **upptagen**, built with modern Android best practices: **Kotlin**, **Jetpack Compose (Material 3)**, and **Room (SQLite)**.

This app allows you to quickly log working hours on the go—starting and stopping live timers, tagging tasks, and viewing stats. When you're back at your computer, you can export your logs to a CSV file and import them directly into the desktop CLI app with automatic deduplication.

---

## Features

- **⏱️ Live Task Timer**:
  - Start timers with optional Project, Description, and Tags.
  - Persistent active timer state (continues running across app reboots/closures).
  - Real-time ticking counter formatted as `HH:MM:SS`.
  - Stop timer with options to update description or append tags.
- **📝 Retroactive Manual Logging**:
  - Log past work periods by specifying duration, project, description, and tags.
- **📋 Entries History & Management**:
  - Chronological list of recorded time entries.
  - Search by project, task description, or tags.
  - Filter by project and tag chips.
  - View total tracked time for any filtered selection.
  - Edit or delete individual entries.
- **🏷️ Tag Statistics**:
  - Detailed overview of time spent per tag.
  - Visual duration bars and entry counts.
  - Toggle sorting by duration or alphabetically.
- **📤 Export & 1-Tap Share**:
  - Exports to the exact CSV format expected by `upptagen import`.
  - Share sheet support: send CSV directly to your computer via **Quick Share / Nearby Share**, Google Drive, Email, or Messaging apps.
  - Save file directly to device storage via Android Storage Access Framework.
- **📥 Import Support**:
  - Import existing CSV logs into Android with automatic deduplication.

---

## Workflow: Consolidating Mobile Logs on Desktop

1. **Track on Mobile**:
   - Start and stop timers throughout your workday on your Android device.
2. **Export from Mobile**:
   - Go to the **Export & Sync** tab in the Android app.
   - Tap **Share CSV** and send the file to your computer (e.g. via Quick Share, Email, or Google Drive) or tap **Save File**.
3. **Import into CLI on Desktop**:
   - Run the following command on your computer:
     ```bash
     upptagen import --path /path/to/exported_file.csv
     ```
   - The CLI automatically imports new entries and skips any records that already exist in your local SQLite database based on `(project, description, start_time, end_time, tags)`.

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose with Material Design 3
- **Local Persistence**: Room Database (SQLite under the hood)
  - Tables: `entries`, `tags`, `entry_tags`, `active_timer` (mirroring the desktop SQLite datastore)
- **Architecture**: MVVM (Model-View-ViewModel) + StateFlow & Coroutines
- **Compatibility**: Android 8.0+ (API Level 26+)

---

## Project Structure

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/upptagen/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── UpptagenApplication.kt
│       │   │   ├── data/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── model/ (TimeEntryEntity, TagEntity, ActiveTimerEntity, ...)
│       │   │   │   ├── dao/   (TimeEntryDao, TagDao, ActiveTimerDao)
│       │   │   │   ├── repository/ (TimeTrackingRepository)
│       │   │   │   └── csv/   (CsvExporter, CsvImporter)
│       │   │   ├── ui/
│       │   │   │   ├── theme/ (Color, Theme, Type)
│       │   │   │   ├── components/ (ProjectBadge, TagChip, DurationBadge)
│       │   │   │   ├── viewmodel/ (TimerViewModel, EntriesViewModel, TagsViewModel)
│       │   │   │   ├── screens/ (TimerScreen, EntriesScreen, TagsScreen, ExportImportScreen)
│       │   │   │   └── UpptagenMainApp.kt
│       │   │   └── util/ (DateTimeUtils)
│       │   └── res/ (strings, colors, drawables, icons, file_paths)
│       └── test/ (Unit tests for CSV export, duration formatting, etc.)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Building and Running

### With Android Studio
1. Open Android Studio.
2. Select **Open** and choose the `android` directory in this repository.
3. Allow Gradle to sync dependencies.
4. Connect an Android device with USB debugging enabled or launch an Android Virtual Device (AVD).
5. Click **Run 'app'** (`Shift + F10`).

### From the Command Line
Ensure you have the Android SDK and Java 17 installed:

```bash
cd android

# Build debug APK
./gradlew assembleDebug

# The APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected device
./gradlew installDebug
```
