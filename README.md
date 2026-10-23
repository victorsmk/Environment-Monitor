# Todo App - Basic Screen

A modern, interactive Android Todo application frontend built using **Jetpack Compose** and **Material 3**.

> **Note**: This project was completed for **Lab Assignment 1**.

---

## Features

- **Progress Summary**: Header card displaying task completion count and a progress bar.
- **Task Management**:
  - Toggle completion with animated strikethrough styling.
  - Delete items from the list.
  - Add new tasks with title, category, and priority level via a Floating Action Button dialog.
- **Priority & Categories**: Color-coded badges for priority levels (**Low**, **Medium**, **High**) and custom category tags.
- **Search & Filtering**: Live search bar and filter chips (**All**, **Active**, **Completed**).
- **In-Memory State Management**: Backed by `ViewModel` and `StateFlow` to maintain UI state across screen reconfigurations.

---

## Architecture & File Structure

```
app/src/main/java/com/example/basicscreen/
├── model/
│   └── TodoItem.kt         # Data model, Priority enum, and TodoFilter enum
├── ui/
│   ├── TodoViewModel.kt    # StateFlow-based UI state management and sample data
│   └── TodoScreen.kt       # Jetpack Compose UI components & previews
└── MainActivity.kt         # Main Activity entry point
```

---

## How to Run

### Requirements
- Android Studio Ladybug or newer
- JDK 11+
- Android Emulator or physical device running Android 7.0 (API 24) or higher

### Building & Running
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd BasicScreen
   ```
2. Build and run via Android Studio or using the Gradle wrapper:
   ```bash
   # On macOS / Linux:
   ./gradlew assembleDebug

   # On Windows:
   gradlew.bat assembleDebug
   ```
3. Deploy to a connected device or emulator using the **Run** button in Android Studio.
