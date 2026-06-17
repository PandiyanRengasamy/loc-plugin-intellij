# Configuration Guide — GenAI LOC Tracker

This document explains every configuration property available in `genai-loc.properties`.

---

## File Location

Place `genai-loc.properties` at the **project root** (same folder as `build.gradle.kts`):

```
intellij-plugin-claude-ij/
├── build.gradle.kts
├── genai-loc.properties    ← here
└── src/
```

> The plugin reads this file at startup using `System.getProperty("user.dir")`.  
> If the file is missing, all values fall back to built-in defaults.

---

## Full Property Reference

```properties
# ════════════════════════════════════════════════════
#  GenAI LOC Tracker — genai-loc.properties
# ════════════════════════════════════════════════════

# ── Backend API ──────────────────────────────────────
# URL of the REST endpoint that receives LOC events.
# Single event : POST {backend.url}
# Batch events : POST {backend.url}/batch  (auto-selected when queue > 1)
backend.url=http://localhost:8080/api/v1/genai-loc/events

# ── Developer Identity ───────────────────────────────
# developer.id   : Leave blank → auto-detected from Windows login (user.name)
#                  or Git config (user.email) if Git is available.
# developer.name : Display name. Leave blank → auto-detected from Git (user.name).
developer.id=
developer.name=

# ── Project / Sprint ─────────────────────────────────
# project.id : Leave blank → uses the IntelliJ project name.
# sprint.id  : Optional. Leave blank to omit from payloads.
project.id=
sprint.id=

# ── GenAI Tool ───────────────────────────────────────
# Leave blank → auto-detected from installed plugins at startup.
# Supported values:
#   COPILOT | CLAUDE | CHATGPT | GEMINI | CODEWHISPERER | TABNINE | CODEIUM | OTHER
default.genai.tool=

# ── Plugin Behaviour ─────────────────────────────────
# Set to false to completely disable all event tracking.
plugin.enabled=true

# Number of events to buffer in memory before flushing to backend.
# Lower value = more frequent HTTP calls.
# Higher value = fewer calls but more data lost if IDE crashes.
batch.size=10

# How often (in seconds) the background scheduler flushes buffered events.
# This is independent of batch.size — whichever triggers first wins.
flush.interval.seconds=30

# Minimum number of inserted newlines in a single document change
# to classify that change as GenAI-assisted.
# Increase this if manual multi-line pastes are being mis-classified.
genai.line.threshold=3
```

---

## Developer Identity Resolution Order

```
1. genai-loc.properties → developer.id (if set)
2. Git config           → git config user.email  (if Git is available)
3. OS login             → System.getProperty("user.name")  e.g. "752004"
```

---

## GenAI Tool Detection Order

```
1. genai-loc.properties → default.genai.tool (if set)
2. Auto-detect          → scans installed & enabled plugins at startup
3. Fallback             → "OTHER"
```

---

## Runtime Settings Override

All properties can be overridden at runtime without editing the file:

1. Open **File → Settings → Tools → GenAI LOC Tracker**
2. Edit any field
3. Click **Apply** or **OK**

Runtime changes are persisted to:
```
Windows : %APPDATA%\JetBrains\<product>\options\genai-loc.xml
macOS   : ~/Library/Application Support/JetBrains/<product>/options/genai-loc.xml
Linux   : ~/.config/JetBrains/<product>/options/genai-loc.xml
```

> **Priority:** Settings UI values always take precedence over `genai-loc.properties`.

---

## CSV Fallback Store Location

When the backend is unreachable, events are saved here:

```
~/.genai-loc/fallback-YYYYMMDD.csv
```

| OS | Full path |
|---|---|
| Windows | `C:\Users\<username>\.genai-loc\fallback-20260402.csv` |
| macOS | `/Users/<username>/.genai-loc/fallback-20260402.csv` |
| Linux | `/home/<username>/.genai-loc/fallback-20260402.csv` |

