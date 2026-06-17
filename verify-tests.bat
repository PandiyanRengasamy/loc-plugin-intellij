@echo off
REM Test Program Verification Checklist for Windows
REM Run this script to verify all test components are in place

setlocal enabledelayedexpansion

echo.
echo ========================================
echo LOC Plugin Test Program - Verification Checklist
echo ========================================
echo.

set SUCCESS=0
set FAILED=0

:check_files
echo Checking Test Files...
echo ----------------------------------------

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\model\CodeEventRequestTest.java" (
    echo [OK] CodeEventRequest unit tests
    set /a SUCCESS+=1
) else (
    echo [FAIL] CodeEventRequest unit tests
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\service\EventDispatcherTest.java" (
    echo [OK] EventDispatcher unit tests
    set /a SUCCESS+=1
) else (
    echo [FAIL] EventDispatcher unit tests
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\util\CsvFallbackStoreTest.java" (
    echo [OK] CsvFallbackStore unit tests
    set /a SUCCESS+=1
) else (
    echo [FAIL] CsvFallbackStore unit tests
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\util\GenAiToolDetectorTest.java" (
    echo [OK] GenAiToolDetector unit tests
    set /a SUCCESS+=1
) else (
    echo [FAIL] GenAiToolDetector unit tests
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\integration\LocPluginIntegrationTest.java" (
    echo [OK] Integration tests
    set /a SUCCESS+=1
) else (
    echo [FAIL] Integration tests
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\LocPluginTestSuite.java" (
    echo [OK] Test suite
    set /a SUCCESS+=1
) else (
    echo [FAIL] Test suite
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\testutil\TestEventBuilder.java" (
    echo [OK] Test utility builder
    set /a SUCCESS+=1
) else (
    echo [FAIL] Test utility builder
    set /a FAILED+=1
)

echo.
echo Checking Documentation Files...
echo ----------------------------------------

if exist "TEST_PROGRAM.md" (
    echo [OK] Comprehensive test documentation
    set /a SUCCESS+=1
) else (
    echo [FAIL] Comprehensive test documentation
    set /a FAILED+=1
)

if exist "TESTING.md" (
    echo [OK] Quick start guide
    set /a SUCCESS+=1
) else (
    echo [FAIL] Quick start guide
    set /a FAILED+=1
)

if exist "TEST_PROGRAM_SUMMARY.md" (
    echo [OK] Test program summary
    set /a SUCCESS+=1
) else (
    echo [FAIL] Test program summary
    set /a FAILED+=1
)

echo.
echo Checking Runner Scripts...
echo ----------------------------------------

if exist "run-tests.sh" (
    echo [OK] Linux/Mac test runner
    set /a SUCCESS+=1
) else (
    echo [FAIL] Linux/Mac test runner
    set /a FAILED+=1
)

if exist "run-tests.bat" (
    echo [OK] Windows test runner
    set /a SUCCESS+=1
) else (
    echo [FAIL] Windows test runner
    set /a FAILED+=1
)

echo.
echo Checking Directory Structure...
echo ----------------------------------------

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\model" (
    echo [OK] Model test directory
    set /a SUCCESS+=1
) else (
    echo [FAIL] Model test directory
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\service" (
    echo [OK] Service test directory
    set /a SUCCESS+=1
) else (
    echo [FAIL] Service test directory
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\util" (
    echo [OK] Util test directory
    set /a SUCCESS+=1
) else (
    echo [FAIL] Util test directory
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\integration" (
    echo [OK] Integration test directory
    set /a SUCCESS+=1
) else (
    echo [FAIL] Integration test directory
    set /a FAILED+=1
)

if exist "src\test\kotlin\com\cts\plugin\intellij\loc\testutil" (
    echo [OK] Test utility directory
    set /a SUCCESS+=1
) else (
    echo [FAIL] Test utility directory
    set /a FAILED+=1
)

echo.
echo Checking Build Configuration...
echo ----------------------------------------

findstr /M "junit-jupiter" build.gradle.kts >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] JUnit Jupiter
    set /a SUCCESS+=1
) else (
    echo [WARN] JUnit Jupiter not found
    set /a FAILED+=1
)

findstr /M "junit-platform-suite" build.gradle.kts >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] JUnit Platform Suite
    set /a SUCCESS+=1
) else (
    echo [WARN] JUnit Platform Suite not found
    set /a FAILED+=1
)

findstr /M "mockito-core" build.gradle.kts >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Mockito
    set /a SUCCESS+=1
) else (
    echo [WARN] Mockito not found
    set /a FAILED+=1
)

findstr /M "assertj-core" build.gradle.kts >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] AssertJ
    set /a SUCCESS+=1
) else (
    echo [WARN] AssertJ not found
    set /a FAILED+=1
)

findstr /M "useJUnitPlatform" build.gradle.kts >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] JUnit Platform configuration
    set /a SUCCESS+=1
) else (
    echo [WARN] JUnit Platform configuration not found
    set /a FAILED+=1
)

echo.
echo ========================================
echo Verification Results
echo ========================================
echo Passed: %SUCCESS%
echo Failed: %FAILED%
echo.

if %FAILED% EQU 0 (
    echo [SUCCESS] All checks passed!
    echo.
    echo You can now run tests with:
    echo   Windows: .\run-tests.bat all
    echo   Linux/Mac: bash run-tests.sh all
    echo   Gradle: ./gradlew test
    echo.
    exit /b 0
) else (
    echo [ERROR] Some checks failed!
    echo.
    echo Please ensure:
    echo   1. All test files are in the correct directories
    echo   2. build.gradle.kts is updated with test dependencies
    echo   3. Runner scripts exist and are executable
    echo.
    exit /b 1
)

