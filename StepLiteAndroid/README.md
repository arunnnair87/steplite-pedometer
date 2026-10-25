# StepLite — Offline Multi-Year Android Pedometer (Kotlin + Jetpack Compose + Room)

A lightweight (< 2.1 MB APK), offline-first Android Pedometer engineered for maximum compatibility with basic/budget Android phones while storing **10+ years of daily, monthly, and yearly step history** locally using SQLite/Room.

## Key Architecture Highlights
1. **Universal Sensor Fallback (`StepSensorService.kt`)**:
   - Uses hardware `Sensor.TYPE_STEP_COUNTER` when available for near-zero battery usage.
   - Automatically falls back to `Sensor.TYPE_ACCELEROMETER` software peak detection (`sqrt(x² + y² + z²)` with low-pass smoothing, 250ms–2000ms cadence window, and false-shake rejection) on entry-level phones without a dedicated step counter chip.
2. **Multi-Year Local Database (`StepDatabase.kt`)**:
   - Stores one compact `DailyStepEntity` row per calendar day (`epochDay` primary key).
   - 10 years of daily records = only **3,650 rows (~140 KB on disk)**, indexed by `year` and `yearMonth` for instant Week, Month, Year, and Multi-Year bar chart queries.
3. **CSV Backup & Restore**:
   - Export or import years of step history as standard CSV without requiring cloud accounts or internet permissions.

## How to Open in Android Studio
1. Extract `StepLite_AndroidStudio_Project.zip`.
2. Open **Android Studio (Ladybug / Koala or newer)** -> **Open** -> select the `StepLiteAndroid` folder.
3. Let Gradle sync, then click **Run** on any physical Android device (API 24 / Android 7.0+).
