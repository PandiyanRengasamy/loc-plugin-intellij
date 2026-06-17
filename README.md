# GenAI LOC Tracker — IntelliJ IDEA Plugin

> Automatically tracks **Lines of Code (LOC)** generated with AI coding assistants inside IntelliJ IDEA and reports them to a backend REST API.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Supported GenAI Tools](#supported-genai-tools)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Build the Plugin](#build-the-plugin)
- [Run / Test the Plugin](#run--test-the-plugin)
- [Install the Plugin](#install-the-plugin)
- [Settings UI](#settings-ui)
- [CSV Fallback (Offline Mode)](#csv-fallback-offline-mode)
- [Logging](#logging)
- [API Contract](#api-contract)
- [Changelog](#changelog)

---

## Overview

**GenAI LOC Tracker** is an IntelliJ IDEA plugin that listens to every document change in the editor and classifies each change as either:

- ✅ **GenAI-generated** — a bulk insertion that exceeds the configured line threshold (e.g. Copilot/Claude suggestion accepted)
- 🖊️ **Manual** — regular keystroke-by-keystroke developer code

Each event is enriched with developer identity, project/sprint metadata, and confidence score, then posted to a configurable backend REST API.

---

## Features

| Feature | Description |
|---|---|
| 📊 **LOC Tracking** | Counts lines added, modified, and deleted per file change |
| 🤖 **GenAI Detection** | Detects bulk multi-line insertions as AI-assisted code |
| 🔍 **Plugin Auto-Detection** | Automatically detects installed AI plugins (Copilot, Claude, Gemini, etc.) |
| 👤 **Developer Identity** | Auto-resolves from Windows login, Git config, or manual setting |
| 🌱 **Dev Mode Detection** | Classifies files as `GREENFIELD` or `BROWNFIELD` |
| 📡 **REST API Integration** | Posts events to a configurable backend endpoint |
| 💾 **CSV Offline Fallback** | Saves events to CSV when backend is down |
| 🔄 **Auto-Replay** | Replays CSV events automatically when backend recovers |
| ⚙️ **Settings UI** | Built-in settings page under `Settings → Tools → GenAI LOC Tracker` |
| 🔒 **Singleton Service** | Thread-safe application-level service with access tracking |

---

## Supported GenAI Tools

The plugin auto-detects the following installed plugins:

| Tool | Plugin ID |
|---|---|
| **GitHub Copilot** | `com.github.copilot` |
| **Claude / Amazon Q** | `com.anthropic.claude` / `amazon.q` |
| **JetBrains AI Assistant** | `com.intellij.ml.llm` |
| **ChatGPT** | `com.openai.chatgpt` |
| **Gemini Code Assist** | `com.google.ide-plugin` |
| **Amazon CodeWhisperer** | `com.amazonaws.codewhisperer` |
| **Tabnine** | `com.tabnine.TabNine` |
| **Codeium** | `com.codeium.intellij` |

> If none are detected, defaults to `OTHER`.  
> You can override this in `genai-loc.properties` or via the Settings UI.

---

## Project Structure

```
intellij-plugin-claude-ij/
├── build.gradle.kts                  # Gradle build script
├── gradle.properties                 # Gradle JVM & cache settings
├── genai-loc.properties              # Plugin runtime configuration (project root)
├── settings.gradle.kts               # Project name
├── README.md                         # This file
│
└── src/main/
    ├── kotlin/com/cts/plugin/intelij/loc/
    │   ├── listeners/
    │   │   └── GenAiDocumentListener.java    # Listens to editor document changes
    │   ├── model/
    │   │   └── CodeEventRequest.java         # Event data model (REST payload)
    │   ├── service/
    │   │   ├── EventDispatcher.java          # Batches & POSTs events to backend
    │   │   └── GenAiLocProjectService.java   # Project-scoped service (one per project)
    │   ├── settings/
    │   │   ├── GenAiLocSettings.java         # Persistent settings (singleton)
    │   │   └── GenAiLocConfigurable.java     # Settings UI panel
    │   └── util/
    │       ├── GenAiToolDetector.java         # Detects installed AI plugins
    │       └── CsvFallbackStore.java          # CSV read/write for offline fallback
    │
    └── resources/
        └── META-INF/
            └── plugin.xml                    # Plugin registration
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| **Java JDK** | 21+ |
| **Gradle** | 8.x (via wrapper) |
| **IntelliJ IDEA** | 2025.1+ (Community or Ultimate) |
| **OS** | Windows / macOS / Linux |

---

## Configuration

All settings are in `genai-loc.properties` at the **project root**:

```properties
# Backend REST API endpoint
backend.url=http://localhost:8080/api/v1/genai-loc/events

# Developer identity (leave blank to auto-detect from Windows login or Git)
developer.id=
developer.name=

# Project and Sprint (leave blank to use IntelliJ project name)
project.id=
sprint.id=

# GenAI tool (leave blank to auto-detect from installed plugins)
# Values: COPILOT | CLAUDE | CHATGPT | GEMINI | CODEWHISPERER | TABNINE | CODEIUM | OTHER
default.genai.tool=

# Plugin on/off switch
plugin.enabled=true

# Events to collect before flushing to backend
batch.size=10

# How often (seconds) the background thread flushes events
flush.interval.seconds=30

# Min inserted lines to classify a change as GenAI-assisted
genai.line.threshold=3
```

> Settings can also be changed at runtime via **Settings → Tools → GenAI LOC Tracker**.  
> Runtime changes are persisted to `~/.config/JetBrains/<product>/options/genai-loc.xml`.

---

## Build the Plugin

### Clean Build
```powershell
cd "C:\Pandiyan\Workspace\GenAI\claude_plugins\intellij-plugin-claude-ij"
.\gradlew.bat clean buildPlugin
```

### Output
```
build/distributions/genai-loc-tracker-1.0.0.zip   ✅
```

### Contents of the ZIP
```
genai-loc-tracker-1.0.0.zip
└── genai-loc-tracker/
    ├── lib/
    │   ├── genai-loc-tracker-1.0.0.jar   # Plugin classes
    │   └── gson-2.11.0.jar               # Bundled dependency
    └── META-INF/
        └── plugin.xml
```

---

## Run / Test the Plugin

Launch a **sandboxed IntelliJ IDEA** instance with the plugin pre-loaded:

```powershell
.\gradlew.bat runIde
```

This downloads IntelliJ IDEA 2025.1 (first time only) and opens a new IDE window where you can test the plugin live.

---

## Install the Plugin

### Method 1 — Install from ZIP (Recommended)
1. Open **IntelliJ IDEA**
2. Go to **File → Settings → Plugins**
3. Click **⚙️ gear icon** → **Install Plugin from Disk...**
4. Select `build/distributions/genai-loc-tracker-1.0.0.zip`
5. Click **OK** → **Restart IDE**

### Method 2 — Install from Gradle (Dev mode)
```powershell
.\gradlew.bat runIde
```

---

## Settings UI

After installation, configure the plugin via:

> **File → Settings → Tools → GenAI LOC Tracker**

| Field | Description |
|---|---|
| **Backend URL** | REST API endpoint for event delivery |
| **Developer ID** | Your user ID (auto-filled from Windows login) |
| **Developer Name** | Your display name |
| **Project ID** | Project identifier (auto-filled from IntelliJ project name) |
| **Sprint ID** | Current sprint/iteration ID |
| **GenAI Tool** | Override auto-detected tool |
| **Plugin Enabled** | Toggle tracking on/off |
| **Batch Size** | Number of events to buffer before flushing |
| **Flush Interval** | Background flush interval in seconds |
| **GenAI Line Threshold** | Min lines inserted to classify as AI-generated |

---

## CSV Fallback (Offline Mode)

When the backend REST API is **unreachable**, events are automatically saved to:

```
~/.genai-loc/fallback-YYYYMMDD.csv
```

### Columns
```
developerId, developerName, projectId, sprintId, filePath, fileName,
ideType, genAiTool, developmentMode,
linesAdded, linesModified, linesDeleted,
genAiGenerated, genAiConfidenceScore, eventTimestamp, sessionId
```

### Recovery Flow
```
Backend DOWN                       Backend UP
─────────────────────────────      ──────────────────────────────
flush() fails                      flush() succeeds (HTTP 2xx)
  → write to CSV                     → replayFallback() triggered
                                       → POST all CSV rows
                                       → delete CSV file ✅
```

> If replay also fails, the CSV is **retained** and retried on the next successful flush.

---

## Logging

All plugin classes use IntelliJ's `Logger` (`com.intellij.openapi.diagnostic.Logger`).

### View Logs at Runtime
> **Help → Show Log in Explorer** → open `idea.log`

### Enable DEBUG Logs
> **Help → Diagnostic Tools → Debug Log Settings**

Add any of these:
```
com.cts.plugin.intelij.loc.listeners.GenAiDocumentListener
com.cts.plugin.intelij.loc.service.EventDispatcher
com.cts.plugin.intelij.loc.service.GenAiLocProjectService
com.cts.plugin.intelij.loc.settings.GenAiLocSettings
com.cts.plugin.intelij.loc.settings.GenAiLocConfigurable
com.cts.plugin.intelij.loc.util.GenAiToolDetector
com.cts.plugin.intelij.loc.util.CsvFallbackStore
```

### Log Levels Used

| Level | When |
|---|---|
| `DEBUG` | Per-event detail, field values, plugin checks |
| `INFO` | Lifecycle events — init, flush, apply settings, replay |
| `WARN` | Recoverable issues — backend down, Git unavailable |
| `ERROR` | Unrecoverable — events lost after retry failure |

---

## API Contract

### Single Event — `POST /api/v1/genai-loc/events`
```json
{
  "developerId":          "752004",
  "developerName":        "Pandiyan",
  "projectId":            "TMG-ENROLL",
  "sprintId":             "SPRINT-12",
  "filePath":             "/src/main/java/com/cts/Service.java",
  "fileName":             "Service.java",
  "ideType":              "INTELLIJ",
  "genAiTool":            "COPILOT",
  "developmentMode":      "BROWNFIELD",
  "linesAdded":           15,
  "linesModified":        2,
  "linesDeleted":         0,
  "genAiGenerated":       true,
  "genAiConfidenceScore": 0.87,
  "eventTimestamp":       "2026-04-02T10:30:00",
  "sessionId":            "a1b2c3d4-e5f6-..."
}
```

### Batch Events — `POST /api/v1/genai-loc/events/batch`
```json
{
  "events": [ { ...event1 }, { ...event2 } ]
}
```

### Expected Response
```
HTTP 200 OK  or  HTTP 201 Created
```

---

## Changelog

### 1.0.0 — 2026-04-02
- 🎉 Initial release
- LOC tracking per file change
- GenAI tool auto-detection from installed plugins
- Developer identity auto-resolution (Windows login / Git config)
- GREENFIELD vs BROWNFIELD detection
- Batch event flush with configurable size and interval
- CSV offline fallback with auto-replay on backend recovery
- Settings UI under `Tools → GenAI LOC Tracker`
- Full logging across all classes

---

## License

Internal CTS use only. Not for public distribution.

