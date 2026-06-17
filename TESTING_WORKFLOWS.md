# 🧪 LOC Plugin Testing Workflow Diagrams & Checklists

## Visual Testing Flow

### Quick Test Workflow (15 minutes)

```
START
  ↓
[OPEN IDE & CREATE PROJECT] (2 min)
  ↓
[OPEN COPILOT CHAT] (1 min)
  ↓
[REQUEST CODE: "Write palindrome method"] (2 min)
  ↓
[COPY/PASTE SUGGESTED CODE] (2 min)
  ↓
[CHECK STATUS BAR] → "Sent=1" ✅
  ↓
[VIEW LOGS] → Find CodeEventRequest entry ✅
  ↓
[CHECK CSV] (if offline) → File exists ✅
  ↓
SUCCESS ✅
```

---

## Complete Testing Workflow (45 minutes)

```
START
  ↓
┌─────────────────────────────────┐
│ TEST 1: SINGLE CODE GENERATION  │ (5 min)
└─────────────────────────────────┘
  ↓ Verify: Event tracked
  ↓
┌─────────────────────────────────┐
│ TEST 2: MULTIPLE METHODS        │ (10 min)
└─────────────────────────────────┘
  ↓ Verify: Batch processing
  ↓
┌─────────────────────────────────┐
│ TEST 3: CODE MODIFICATION       │ (5 min)
└─────────────────────────────────┘
  ↓ Verify: Modifications tracked
  ↓
┌─────────────────────────────────┐
│ TEST 4: OFFLINE MODE            │ (7 min)
└─────────────────────────────────┘
  ↓ Verify: CSV creation
  ↓
┌─────────────────────────────────┐
│ TEST 5: MULTIPLE FILES          │ (8 min)
└─────────────────────────────────┘
  ↓ Verify: All files tracked
  ↓
┌─────────────────────────────────┐
│ TEST 6: PERFORMANCE             │ (5 min)
└─────────────────────────────────┘
  ↓ Verify: No slowdown
  ↓
┌─────────────────────────────────┐
│ TEST 7: SETTINGS                │ (5 min)
└─────────────────────────────────┘
  ↓ Verify: Config respected
  ↓
SUCCESS ✅ → CREATE REPORT
```

---

## Event Tracking Flow

```
USER GENERATES CODE
    ↓
COPILOT SUGGESTS CODE
    ↓
USER ACCEPTS/COPIES CODE
    ↓
┌──────────────────────────────────────┐
│ CodeEventRequest CREATED             │
├──────────────────────────────────────┤
│ developerId: <windows-user>          │
│ projectId: <project-name>            │
│ filePath: <file-path>                │
│ fileName: <file-name>                │
│ linesAdded: <count>                  │
│ linesModified: <count>               │
│ linesDeleted: <count>                │
│ genAiTool: GitHub Copilot            │
│ genAiGenerated: true                 │
│ genAiConfidenceScore: 0.85-0.95      │
│ eventTimestamp: 2026-04-02T...       │
│ sessionId: <uuid>                    │
└──────────────────────────────────────┘
    ↓
EVENT ENQUEUED
    ↓
┌─ DECISION ─────────────────────────┐
│ Queue size >= Batch size (10)?      │
├─────────────────────────────────┬───┤
│ YES                 │       NO       │
└────────┬───────────────────┬────────┘
         ↓                   ↓
    FLUSH NOW          WAIT FOR TIMER
         ↓                   ↓
    BATCH CREATED    (30 sec elapsed?)
         ↓                   ↓
         └─────────┬─────────┘
                   ↓
        CONVERT TO JSON
                   ↓
    ┌─ BACKEND CHECK ──┐
    │ Reachable?       │
    ├──────┬───────────┤
    │ YES  │    NO     │
    └──┬───┴───┬───────┘
       ↓       ↓
     POST   SAVE CSV
      ↓        ↓
   SUCCESS   OFFLINE
      ↓        ↓
   CLEAR   RETRY LATER
    QUEUE       ↓
      ↓     BACKEND UP?
      ↓        ↓
   STATUS  YES→ AUTO-REPLAY
   UPDATED      ↓
               CLEAR CSV
               ↓
            EVENTS SENT
                 ↓
              STATUS UPDATE
                 ↓
              SUCCESS ✅
```

---

## Status Bar Indicator Guide

```
┌─────────────────────────────────────────────────────┐
│ STATUS BAR (Bottom Right of IDE)                    │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Sent=5 Failed=0 Pending=0 CsvFallback=no          │
│   │     │      │        │         │                 │
│   │     │      │        │         └─ CSV in use?   │
│   │     │      │        │            yes = offline  │
│   │     │      │        │            no  = online   │
│   │     │      │        │                           │
│   │     │      │        └─ Events waiting to send   │
│   │     │      │           0 = all sent             │
│   │     │      │           >0 = batch pending       │
│   │     │      │                                    │
│   │     │      └─ Failed events (retry attempts)    │
│   │     │         0 = all OK                        │
│   │     │         >0 = some failed                  │
│   │     │                                           │
│   │     └─ Total successfully sent                  │
│   │        increases after flush                    │
│   │                                                 │
│   └─ This updates in real-time as you code        │
│                                                      │
│  HOVER OVER INDICATOR to see full statistics        │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

## Decision Tree: Is Plugin Working?

```
                    START
                      ↓
        ┌─ Is plugin enabled? ──┐
        │ (Settings→Tools→LOC)  │
        └──┬─────────────┬──────┘
       YES │             │ NO
          ↓             ↓
      CONTINUE      ❌ FAIL
          │         Enable it
          │         then retry
          ↓
  ┌─ Did you add code? ──┐
  │ (from Copilot)       │
  └──┬──────────┬────────┘
 YES │          │ NO
     ↓          ↓
 CONTINUE   ❌ FAIL
     │      Request code
     │      from Copilot
     ↓
  ┌─ Check Status Bar ──┐
  │ Shows "Sent=X"?    │
  └──┬──────────┬──────┘
 YES │          │ NO
     ↓          ↓
  ✅ OK       Check Logs
     │        (Help→Log)
     │        ↓
     │    ┌─ See event? ──┐
     │    │ Search: file  │
     │    │ name or code  │
     │    └──┬──────┬─────┘
     │   YES │      │ NO
     │       ↓      ↓
     │     ✅ OK   ❌ Restart IDE
     │    Plugin   and retry
     │    working
     │       ↓
     ↓       ↓
  OFFLINE CHECK
     │
  ┌─ Backend running? ──┐
  │ (if applicable)      │
  └──┬──────────┬───────┘
 YES │          │ NO
     ↓          ↓
 ✅ ONLINE   ✅ OFFLINE
 Events sent CSV created
     │          │
     └────┬─────┘
          ↓
       SUCCESS ✅
```

---

## Checklist: Before Each Test

```
PRE-TEST CHECKLIST
═══════════════════════════════════════════════

□ IDE Launched
  - IntelliJ IDEA 2025.1+
  - Java project open
  - No errors in IDE

□ Copilot Ready
  - Copilot plugin installed
  - Copilot Chat enabled
  - Can send messages in chat

□ LOC Plugin Ready
  - Plugin built
  - Settings accessible
  - (Settings→Tools→GenAI LOC Tracker)

□ Environment
  - Backend running OR
  - Network blocked for offline test
  - CSV directory writable

□ Test File Ready
  - New Java file created (Test.java)
  - File is empty
  - File is in project source

□ Status Bar Visible
  - Can see bottom-right corner
  - Plugin status indicators visible

□ Logs Ready
  - Know how to open logs
  - (Help → Show Log in Explorer)

□ Documentation Ready
  - Have MANUAL_TESTING_GUIDE.md open
  - Have test checklist

═══════════════════════════════════════════════
Ready to test? → START TEST
```

---

## During Test: Real-Time Monitoring

```
WHILE TESTING
═══════════════════════════════════════════════

WATCH THESE 3 THINGS:

1. STATUS BAR (Bottom Right)
   ┌─────────────────────────┐
   │ Before Code: Sent=0     │
   │     ↓                   │
   │ After Code: Sent=1 ✅   │
   └─────────────────────────┘

2. LOGS (Help → Show Log)
   ┌─────────────────────────┐
   │ Search: filename        │
   │ Should appear in DEBUG  │
   │ logs immediately        │
   └─────────────────────────┘

3. CSV FILE (if offline)
   ┌─────────────────────────┐
   │ ~/.genai-loc/           │
   │ fallback-20260402.csv   │
   │ Should exist and grow    │
   └─────────────────────────┘

═══════════════════════════════════════════════
All 3 show activity? → TEST PASSING ✅
```

---

## Verification Matrix

```
TEST VERIFICATION MATRIX
════════════════════════════════════════════════════════════

What to Test          How to Verify            Expected Result
──────────────────────────────────────────────────────────────

Event Created        Check Logs              ✅ DEBUG message
                     Search: "CodeEventRequest"

File Tracked         Check Logs              ✅ file=Test.java
                     Search: "fileName"

Lines Counted        Check Logs              ✅ linesAdded=N
                     Search: "linesAdded"

Copilot Detected     Check Logs              ✅ tool=GitHub Copilot
                     Search: "genAiTool"

Event Sent           Check Status Bar        ✅ Sent=1
                     Look at bottom-right    (increases by 1)

Batch Process        Check Logs              ✅ "Flushing N events"
                     Search: "Flush"         N = number of events

Offline Handling     Check CSV Created       ✅ ~/.genai-loc/
                                             fallback-*.csv exists

Settings Respected   Check Settings          ✅ Plugin disabled
                     Disable plugin          = no events tracked
                     Test that no events
                     are tracked

Session ID           Check Logs              ✅ Same sessionId
                     Search: "session"       for all events in
                     on multiple events      one IDE session

Confidence Score     Check CSV or Logs       ✅ Score between
                     Check genAiConfidence   0.0 and 1.0
                                             for GenAI code

════════════════════════════════════════════════════════════
```

---

## Testing Execution Timeline

```
TIME    ACTION                          VERIFICATION
────────────────────────────────────────────────────────────

0:00    Create project & open IDE       IDE loads OK

1:00    Open Copilot Chat               Chat window visible

2:00    Request code from Copilot       Suggestion appears

3:00    Copy suggested code to file     Code in file, saved

3:05    CHECK IMMEDIATELY
        ├─ Status bar                   Shows "Sent=N"
        ├─ Look for refresh             Counts increase
        └─ Wait 1 second for async      (May need small delay)

3:15    VIEW LOGS
        ├─ Open logs                    Logs open
        ├─ Search filename              Entry appears
        ├─ Check linesAdded             Number visible
        ├─ Check genAiTool              "GitHub Copilot"
        ├─ Check genAiGenerated         "true"
        └─ Check confidence             "0.xx" value

4:00    CHECK CSV (if offline)
        ├─ Navigate to ~/.genai-loc/    Directory visible
        ├─ Check file exists            fallback-*.csv
        ├─ View contents                Data rows present
        └─ Verify headers               All 16 columns

5:00    TEST COMPLETE ✅

────────────────────────────────────────────────────────────
```

---

## Troubleshooting Quick Reference

```
PROBLEM                    QUICK FIX
─────────────────────────────────────────────────────

Nothing tracked            □ Enable plugin (Settings)
                          □ Restart IDE
                          □ Check Copilot generating code

Status bar not visible    □ View → Tool Windows → Status
                          □ Check bottom-right corner

No logs appear            □ Generate code again
                          □ Wait 2 seconds
                          □ Refresh logs

CSV not created           □ Backend might be up (no offline)
                          □ Check directory permissions
                          □ Try blocking network

Event count wrong          □ Check multiple files open
                          □ Review what code was added
                          □ Check logs for deleted lines

Confidence score missing   □ Check if genAiGenerated=false
                          □ Copilot code should have score
                          □ Manual code = no score

Plugin disabled            □ Settings → Tools → GenAI LOC
                          □ Check "Enabled" checkbox
                          □ Restart IDE

Backend unreachable        □ Check URL in settings
                          □ Verify backend running
                          □ Check network connectivity

─────────────────────────────────────────────────────
```

---

## Sign-Off Sheet

After completing all tests, fill this out:

```
╔════════════════════════════════════════════════╗
║         LOC PLUGIN TEST SIGN-OFF SHEET         ║
╚════════════════════════════════════════════════╝

Date: ____________________
Tester: __________________

TEST RESULTS
────────────────────────────────────────────

[  ] Test 1: Single Code Generation       PASS/FAIL
[  ] Test 2: Multiple Methods             PASS/FAIL
[  ] Test 3: Code Modification            PASS/FAIL
[  ] Test 4: Offline Mode                 PASS/FAIL
[  ] Test 5: Multiple Files               PASS/FAIL
[  ] Test 6: Performance                  PASS/FAIL
[  ] Test 7: Settings Configuration       PASS/FAIL

VERIFICATION
────────────────────────────────────────────

[  ] Status bar updates correctly
[  ] Logs show event details
[  ] CSV file created when offline
[  ] File names recorded correctly
[  ] Line counts accurate
[  ] Confidence scores present
[  ] Backend receives events
[  ] No IDE slowdown
[  ] Settings respected
[  ] Session IDs consistent

SUMMARY
────────────────────────────────────────────

Total Tests Passed: ______ / 7
Total Tests Failed: ______ / 7

Issues Found:
1. ___________________________
2. ___________________________
3. ___________________________

Recommendation:
[ ] READY FOR PRODUCTION
[ ] NEEDS FIXES
[ ] INVESTIGATE FURTHER

Tester Signature: _____________________
Date: _____________________
```

---

**Testing Guide Created**: April 2, 2026  
**Plugin Version**: 1.0.0  
**Copilot Integration**: GitHub Copilot Chat

