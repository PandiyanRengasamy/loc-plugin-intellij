# Developer Guide — GenAI LOC Tracker

This document is for developers who want to build, modify, or extend the plugin.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Build Tool | Gradle 8.x (Kotlin DSL) |
| IDE Platform | IntelliJ Platform SDK 2025.1 |
| Gradle Plugin | `org.jetbrains.intellij.platform` v2.5.0 |
| Serialisation | Gson 2.11.0 |
| HTTP Client | Java 11+ `java.net.http.HttpClient` |

---

## Development Setup

### 1. Clone / Open Project
Open the folder in IntelliJ IDEA:
```
C:\Pandiyan\Workspace\GenAI\claude_plugins\intellij-plugin-claude-ij
```

### 2. Sync Gradle
Click **"Load Gradle Changes"** in the notification bar, or run:
```powershell
.\gradlew.bat dependencies
```

### 3. Run in Sandbox IDE
```powershell
.\gradlew.bat runIde
```
This downloads IntelliJ IDEA 2025.1 (first time) and opens a sandboxed instance with your plugin loaded.

---

## Build Commands

| Command | Description |
|---|---|
| `.\gradlew.bat clean` | Delete all build output |
| `.\gradlew.bat compileJava` | Compile Java sources only |
| `.\gradlew.bat buildPlugin` | Build the distributable ZIP |
| `.\gradlew.bat runIde` | Launch sandboxed IDE for testing |
| `.\gradlew.bat verifyPlugin` | Run IntelliJ plugin verifier |
| `.\gradlew.bat publishPlugin` | Publish to JetBrains Marketplace (needs token) |

---

## Class Responsibilities

### `GenAiDocumentListener`
- **Package:** `listeners`
- **Role:** Attaches a `DocumentListener` to every opened editor
- **Triggers on:** Every document change (keystroke, paste, AI completion)
- **Key logic:** Counts newlines in inserted fragment → determines if GenAI-assisted

### `CodeEventRequest`
- **Package:** `model`
- **Role:** Data model for a single LOC event
- **Serialised to:** JSON by Gson for the REST API POST

### `EventDispatcher`
- **Package:** `service`
- **Role:** Thread-safe event queue with batch flush and HTTP POST
- **Fallback:** Writes to CSV on failure, replays on recovery
- **Threading:** Uses `ScheduledExecutorService` (single background thread)

### `GenAiLocProjectService`
- **Package:** `service`
- **Role:** Project-scoped service — one instance per open IntelliJ project
- **Lifecycle:** Created when project opens, disposed when project closes

### `GenAiLocSettings`
- **Package:** `settings`
- **Role:** Application-level persistent settings (singleton)
- **Persisted to:** `genai-loc.xml` via IntelliJ `PersistentStateComponent`
- **Defaults from:** `genai-loc.properties` at project root

### `GenAiLocConfigurable`
- **Package:** `settings`
- **Role:** Settings UI panel shown under `Settings → Tools → GenAI LOC Tracker`

### `GenAiToolDetector`
- **Package:** `util`
- **Role:** Scans installed IntelliJ plugins to auto-detect active AI assistants

### `CsvFallbackStore`
- **Package:** `util`
- **Role:** Reads/writes events to a local CSV file when backend is offline

---

## Adding a New GenAI Tool

1. Open `GenAiToolDetector.java`
2. Add a new entry to the `KNOWN_TOOLS` array:
```java
{ "MY_TOOL", "com.example.my-ai-plugin" },
```
3. That's it — detection is automatic at startup

---

## Changing the Event Schema

1. Add/remove fields in `CodeEventRequest.java`
2. Update `CsvFallbackStore.toCsvRow()` and `fromCsvRow()` to match
3. Update `HEADER` constant in `CsvFallbackStore.java`
4. Update the backend API accordingly

---

## Packaging & Distribution

### Build ZIP
```powershell
.\gradlew.bat clean buildPlugin
```
Output: `build/distributions/genai-loc-tracker-1.0.0.zip`

### Sign Plugin (for Marketplace)
Place certificate files in `certificate/` folder:
```
certificate/
├── chain.crt
└── private.pem
```
Set environment variable:
```powershell
$env:PRIVATE_KEY_PASSWORD = "your-password"
.\gradlew.bat signPlugin
```

### Publish to Marketplace
```powershell
$env:PUBLISH_TOKEN = "your-jetbrains-token"
.\gradlew.bat publishPlugin
```

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `Unresolved reference: intellijPlatform` | Run `.\gradlew.bat dependencies` to sync the Gradle plugin |
| `Dependency requires JVM 11+` | Set `JAVA_HOME` to JDK 21 |
| Plugin not visible after install | Restart IDE after installation |
| Events not reaching backend | Check `idea.log` for `EventDispatcher` WARN/ERROR entries |
| CSV not being replayed | Check `~/.genai-loc/` folder and `idea.log` for CsvFallbackStore logs |
| Settings not saved | Ensure `GenAiLocSettings` is registered as `<applicationService>` in `plugin.xml` |

---

## Folder Structure Reference

```
intellij-plugin-claude-ij/
├── build.gradle.kts          # Build script — dependencies, plugin metadata
├── gradle.properties         # JVM args, caching flags
├── settings.gradle.kts       # Root project name
├── genai-loc.properties      # Runtime config (project root)
├── README.md                 # User-facing overview
├── CONFIGURATION.md          # Detailed property reference
├── DEVELOPER.md              # This file
└── src/main/
    ├── kotlin/               # Java source files (under kotlin/ folder)
    │   └── com/cts/plugin/intelij/loc/
    │       ├── listeners/    # Editor event listeners
    │       ├── model/        # Data models
    │       ├── service/      # Core business logic
    │       ├── settings/     # Configuration & UI
    │       └── util/         # Utility classes
    └── resources/
        └── META-INF/
            └── plugin.xml    # Plugin registration
```

