# 🧪 Manual Testing Guide - LOC Plugin with Copilot Chat

## Quick Start: Test in 15 Minutes

Follow these steps to quickly test the LOC plugin with Copilot Chat.

---

## ⏱️ 15-Minute Quick Test

### Step 1: Prepare (2 minutes)

```
1. Open IntelliJ IDEA
2. Create new Java project (File → New → Project)
3. Create new Java file (src/Main.java)
```

### Step 2: Open Copilot Chat (1 minute)

```
1. View → Tool Windows → GitHub Copilot Chat
   OR
   Press: Ctrl+Shift+A (Windows/Linux) / Cmd+Shift+A (Mac)

2. Chat window opens on right side
```

### Step 3: Generate Code (5 minutes)

**In Copilot Chat, type:**

```
Write a Java method to check if a string is a palindrome
```

**Copilot will suggest code like:**
```java
public static boolean isPalindrome(String str) {
    if (str == null || str.isEmpty()) {
        return false;
    }
    String cleaned = str.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    int left = 0, right = cleaned.length() - 1;
    while (left < right) {
        if (cleaned.charAt(left) != cleaned.charAt(right)) {
            return false;
        }
        left++;
        right--;
    }
    return true;
}
```

**Copy and paste into Main.java**

### Step 4: Verify Plugin Tracking (3 minutes)

**Check 1: Look at Bottom Right Corner**
- You should see plugin status
- Should show: `Sent=1 Failed=0 Pending=0`

**Check 2: Open Settings**
```
Settings → Tools → GenAI LOC Tracker
Verify:
- ✅ Enabled checkbox is checked
- ✅ Backend URL is configured
- ✅ Batch size is set (e.g., 10)
```

**Check 3: Check Logs**
```
Help → Show Log in Explorer
Search for "isPalindrome" or "CodeEventRequest"
Should see log entry with:
- fileName: Main.java
- linesAdded: 12
- genAiTool: GitHub Copilot
- genAiGenerated: true
```

**Check 4: Verify CSV (Offline Mode)**
```
Windows: %USERPROFILE%\.genai-loc\fallback-*.csv
Linux/Mac: ~/.genai-loc/fallback-*.csv

Should contain CSV with your event
```

### ✅ Test Complete!

You've successfully tested the LOC plugin with Copilot!

---

## 📊 Detailed Testing Steps (45 Minutes)

### Test 1: Single Code Generation (5 minutes)

**Objective**: Basic event tracking

**Steps**:
```
1. Open IntelliJ IDEA with test project
2. Open empty Main.java file
3. Open Copilot Chat
4. Request: "Create a factorial method"
5. Copy suggested code to Main.java
6. Wait 2 seconds for event to process
7. Check status bar shows event sent
```

**Expected**:
```
✅ Status shows "Sent=1"
✅ File name recorded: Main.java
✅ Lines counted correctly
✅ Confidence score: ~0.85-0.95
```

**Verification**:
```bash
# Check logs
Help → Show Log in Explorer
Search: "factorial" or "linesAdded"

# Check CSV (offline)
cat ~/.genai-loc/fallback-*.csv | grep Main.java
```

---

### Test 2: Multiple Code Blocks (10 minutes)

**Objective**: Batch event handling

**Steps**:
```
1. In Copilot Chat, request: "Create 3 utility methods for string handling"
2. Copilot generates multiple methods
3. Copy all suggested methods to Main.java
4. Wait for event processing
5. Check event count
```

**Expected**:
```
✅ Status shows "Sent=1" (batch sent together)
✅ All three methods tracked in single event
✅ Total lines = sum of all three methods
✅ Single event with all changes
```

**Verification**:
```bash
# View logs for batch operation
Help → Show Log in Explorer
Search: "Flushing 1 events" (batch of 3 lines)
```

---

### Test 3: Code Modification (5 minutes)

**Objective**: Track modifications

**Steps**:
```
1. Take the isPalindrome method from Test 1
2. In Copilot Chat: "Add comments to the isPalindrome method"
3. Copilot suggests commented version
4. Replace old method with new
5. Check event tracking
```

**Expected**:
```
✅ linesModified: ~10 (comments added)
✅ linesAdded: ~2 (if lines added)
✅ linesDeleted: ~0 (or number of deleted lines)
✅ genAiGenerated: true (modification from Copilot)
```

---

### Test 4: Manual Code vs Copilot Code (8 minutes)

**Objective**: Distinguish manual and GenAI code

**Steps**:
```
1. Add manual method to Main.java:
   public static void printHello() {
       System.out.println("Hello World");
   }

2. Add Copilot-generated method:
   (Ask Copilot for another method)

3. Check plugin differentiation
4. Verify both tracked but marked differently
```

**Expected**:
```
✅ Manual code: genAiGenerated = false, confidence = null
✅ Copilot code: genAiGenerated = true, confidence = 0.9+
✅ Both files/line counts tracked
✅ Events sent separately or batched
```

---

### Test 5: Testing Offline Mode (7 minutes)

**Objective**: CSV fallback when backend unavailable

**Setup**:
```
1. Find backend URL in Settings
   (Settings → Tools → GenAI LOC Tracker)
   
2. Simulate offline:
   - Option A: Stop backend service
   - Option B: Change URL to unreachable: http://localhost:9999
   - Option C: Block network (advanced)
```

**Test Steps**:
```
1. Offline configuration ready
2. Open Copilot Chat
3. Request: "Create a calculator class"
4. Accept suggested code
5. Event should be saved to CSV instead
6. Check CSV file created
```

**Expected**:
```
✅ CSV file created: ~/.genai-loc/fallback-YYYYMMDD.csv
✅ CSV contains event data
✅ File formatted correctly with headers
✅ Plugin shows "CsvFallback=yes" in status
```

**Verification**:
```bash
# Check CSV file exists
ls -la ~/.genai-loc/fallback-*.csv

# View CSV contents
cat ~/.genai-loc/fallback-*.csv

# Should show:
# developerId,developerName,...,Calculator,...
```

---

### Test 6: Performance Test (5 minutes)

**Objective**: Verify plugin doesn't slow IDE

**Steps**:
```
1. Request large code block from Copilot:
   "Create complete CRUD operations REST controller"
   (Should be 50+ lines)

2. Copy all code
3. Monitor IDE responsiveness
4. Check event processed without lag
5. Verify full code tracked
```

**Expected**:
```
✅ No IDE slowdown while copying
✅ No lag when saving file
✅ Event processed quickly
✅ Large line count handled (50+)
✅ No errors in logs
```

---

### Test 7: Multiple Files (8 minutes)

**Objective**: Track code across multiple files

**Setup**:
```
1. Create multiple Java files:
   - User.java
   - UserService.java
   - UserController.java
```

**Test Steps**:
```
1. In User.java, use Copilot:
   "Create POJO class for User with fields"
   
2. In UserService.java, use Copilot:
   "Create service methods for CRUD operations"
   
3. In UserController.java, use Copilot:
   "Create REST endpoints for User management"
   
4. Check plugin tracks all three files
```

**Expected**:
```
✅ File 1 - User.java: fileName tracked
✅ File 2 - UserService.java: fileName tracked
✅ File 3 - UserController.java: fileName tracked
✅ All file paths recorded correctly
✅ All events sent (or CSV contains all)
```

---

## 📋 Checklist During Testing

### While Testing - Monitor These:

```
IDE Status Bar (bottom right):
  [ ] Plugin status visible
  [ ] Event counts update
  [ ] Status changes from "Pending" to "Sent"

IntelliJ Logs:
  [ ] No error messages
  [ ] Debug logs show events
  [ ] File names appear in logs
  [ ] Line counts look correct

CSV File (if offline):
  [ ] File created in ~/.genai-loc/
  [ ] File name has date stamp
  [ ] CSV has correct headers
  [ ] Data rows present
  [ ] Data formatted correctly

Settings:
  [ ] Plugin enabled
  [ ] Backend URL configured
  [ ] Batch size reasonable (5-20)
  [ ] Flush interval reasonable (30-60 sec)
```

---

## 🔍 How to Verify Events

### Method 1: Status Bar
```
Look at bottom-right corner of IDE
Should show: "Sent=X Failed=Y Pending=Z CsvFallback=yes|no"

What it means:
- Sent=5     → 5 events successfully sent
- Failed=0   → No failed events
- Pending=0  → No events waiting in queue
- CsvFallback=no → Not using CSV (backend OK)
```

### Method 2: IntelliJ Logs
```
Help → Show Log in Explorer
Search for: "LOC" or "GenAi" or "CodeEventRequest"

Expected entries:
[DEBUG] CodeEventRequest created: file=Test.java dev=user1 tool=GitHub Copilot linesAdded=10 linesModified=2 genAiGenerated=true
[INFO] EventDispatcher: Flush OK: 1 events sent
```

### Method 3: CSV File
```bash
# View the file
cat ~/.genai-loc/fallback-20260402.csv

# Or search for specific file
grep "Main.java" ~/.genai-loc/fallback-*.csv

# Count entries
wc -l ~/.genai-loc/fallback-*.csv
```

### Method 4: Backend Logs (if running)
```bash
# Check what server received
curl http://localhost:8080/api/events

# Or tail backend logs
tail -f server.log | grep "POST /api/events"
```

---

## 🎯 Quick Scenario Templates

### Scenario A: Simple Test (5 minutes)
```
1. Write one method using Copilot
2. Check status shows "Sent=1"
3. Verify logs show event
✅ DONE
```

### Scenario B: Complete Test (15 minutes)
```
1. Create multiple methods (3-5)
2. Check batch sent
3. Verify logs
4. Check CSV file
5. Review settings
✅ DONE
```

### Scenario C: Comprehensive Test (45 minutes)
```
1. Test single generation
2. Test multiple files
3. Test offline mode
4. Test settings
5. Review all logs
6. Verify CSV
7. Create test report
✅ DONE
```

---

## 📊 Results Recording Template

**Test Date**: _________________

**Test Scenario**: _________________

**Result**: ✅ PASS / ❌ FAIL

**Details**:
```
- IDE Version: IntelliJ IDEA 2025.1
- Plugin Version: 1.0.0
- Copilot: ✅ Installed
- Backend: ✅ Running / ❌ Offline
- Test Duration: ___ minutes

Events Tracked:
- Total Events: ___
- Files Modified: ___
- Lines Added: ___
- Lines Modified: ___
- GenAI Events: ___
- Confidence Scores: ___

Issues Found:
1. ___
2. ___

Observations:
```

---

## 🚀 When to Test

### Test After
- [ ] Plugin installation
- [ ] Code changes to plugin
- [ ] Build/rebuild
- [ ] IDE update
- [ ] Copilot update

### Test When
- [ ] Verifying functionality
- [ ] Before releasing
- [ ] Before committing changes
- [ ] During debugging
- [ ] Adding new features

---

## ✨ Tips for Better Testing

1. **Use Real Copilot Code**
   - Don't manually type suggestions
   - Let Copilot generate naturally

2. **Test Different Complexity**
   - Simple methods (getters/setters)
   - Complex logic (algorithms)
   - Full classes

3. **Monitor While Testing**
   - Keep status bar visible
   - Watch for immediate feedback

4. **Document Everything**
   - Take screenshots
   - Record what worked
   - Note any issues

5. **Test Multiple Scenarios**
   - Don't just test success path
   - Test edge cases
   - Test offline mode
   - Test performance

---

## ❓ Common Questions

**Q: Where can I see if events were sent?**
A: Check status bar (bottom right) or view logs

**Q: How do I know if CSV was used?**
A: Check status bar shows "CsvFallback=yes" and file in ~/.genai-loc/

**Q: What if nothing is tracked?**
A: Check plugin is enabled in Settings → Tools → GenAI LOC Tracker

**Q: How do I see full event details?**
A: Check Help → Show Log in Explorer and search for filename

**Q: Can I test offline?**
A: Yes, change backend URL to unreachable address

---

## 📞 Help & Support

**Having issues?**

1. Check TESTING_WITH_COPILOT.md (detailed guide)
2. Check TESTING.md (troubleshooting)
3. Review logs (Help → Show Log)
4. Check CSV file if offline
5. Verify settings configured

---

**Last Updated**: April 2, 2026  
**Plugin Version**: 1.0.0  
**Estimated Testing Time**: 15 minutes (quick) to 45 minutes (detailed)

