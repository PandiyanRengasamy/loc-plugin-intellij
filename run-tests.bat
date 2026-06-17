@echo off
REM LOC Plugin Test Runner Script for Windows
REM This script provides convenient commands to run various test configurations

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "GRADLE=gradlew.bat"
set "GRADLE_CMD=%GRADLE%"

:parse_args
if "%1"=="" (
    set "COMMAND=all"
) else if "%1"=="all" (
    set "COMMAND=all"
    shift
) else if "%1"=="unit" (
    set "COMMAND=unit"
    shift
) else if "%1"=="integration" (
    set "COMMAND=integration"
    shift
) else if "%1"=="model" (
    set "COMMAND=model"
    shift
) else if "%1"=="service" (
    set "COMMAND=service"
    shift
) else if "%1"=="util" (
    set "COMMAND=util"
    shift
) else if "%1"=="suite" (
    set "COMMAND=suite"
    shift
) else if "%1"=="coverage" (
    set "COMMAND=coverage"
    shift
) else if "%1"=="debug" (
    set "COMMAND=debug"
    shift
) else if "%1"=="clean" (
    set "COMMAND=clean"
    shift
) else if "%1"=="-v" (
    set "GRADLE_CMD=%GRADLE_CMD% --info"
    shift
    goto parse_args
) else if "%1"=="--verbose" (
    set "GRADLE_CMD=%GRADLE_CMD% --info"
    shift
    goto parse_args
) else if "%1"=="-q" (
    set "GRADLE_CMD=%GRADLE_CMD% -q"
    shift
    goto parse_args
) else if "%1"=="--quiet" (
    set "GRADLE_CMD=%GRADLE_CMD% -q"
    shift
    goto parse_args
) else if "%1"=="-p" (
    set "GRADLE_CMD=%GRADLE_CMD% --parallel --max-workers=4"
    shift
    goto parse_args
) else if "%1"=="--parallel" (
    set "GRADLE_CMD=%GRADLE_CMD% --parallel --max-workers=4"
    shift
    goto parse_args
) else if "%1"=="-h" (
    goto show_help
) else if "%1"=="--help" (
    goto show_help
) else if "%1"=="help" (
    goto show_help
) else (
    set "COMMAND=all"
)

:execute_command
echo.
echo ========================================
echo LOC Plugin Test Runner
echo ========================================
echo.

if "%COMMAND%"=="all" (
    echo Running All Tests...
    call %GRADLE_CMD% test
    echo.
    echo [OK] All tests completed!
    goto end
)

if "%COMMAND%"=="unit" (
    echo Running Unit Tests...
    call %GRADLE_CMD% test --tests "*Test"
    echo.
    echo [OK] Unit tests completed!
    goto end
)

if "%COMMAND%"=="integration" (
    echo Running Integration Tests...
    call %GRADLE_CMD% test --tests "*IntegrationTest"
    echo.
    echo [OK] Integration tests completed!
    goto end
)

if "%COMMAND%"=="model" (
    echo Running Model Tests...
    call %GRADLE_CMD% test --tests "*CodeEventRequestTest"
    echo.
    echo [OK] Model tests completed!
    goto end
)

if "%COMMAND%"=="service" (
    echo Running Service Tests...
    call %GRADLE_CMD% test --tests "*EventDispatcherTest"
    echo.
    echo [OK] Service tests completed!
    goto end
)

if "%COMMAND%"=="util" (
    echo Running Utility Tests...
    call %GRADLE_CMD% test --tests "*CsvFallbackStoreTest" --tests "*GenAiToolDetectorTest"
    echo.
    echo [OK] Utility tests completed!
    goto end
)

if "%COMMAND%"=="suite" (
    echo Running Test Suite...
    call %GRADLE_CMD% test --tests "*LocPluginTestSuite"
    echo.
    echo [OK] Test suite completed!
    goto end
)

if "%COMMAND%"=="coverage" (
    echo Running Tests with Coverage Report...
    call %GRADLE_CMD% test
    echo.
    echo [OK] Coverage report generated!
    echo View report at: build/reports/jacoco/test/html/index.html
    goto end
)

if "%COMMAND%"=="debug" (
    echo Running Tests with Debug Logging...
    call %GRADLE_CMD% test --debug
    echo.
    echo [OK] Debug tests completed!
    goto end
)

if "%COMMAND%"=="clean" (
    echo Cleaning Build Artifacts...
    call %GRADLE_CMD% clean
    echo.
    echo [OK] Build artifacts cleaned!
    goto end
)

goto end

:show_help
echo.
echo LOC Plugin Test Runner for Windows
echo.
echo Usage: run-tests.bat [COMMAND] [OPTIONS]
echo.
echo Commands:
echo   all              Run all tests
echo   unit             Run all unit tests
echo   integration      Run integration tests
echo   model            Run CodeEventRequest tests
echo   service          Run EventDispatcher tests
echo   util             Run utility tests (CSV, GenAiTool)
echo   suite            Run test suite
echo   coverage         Run tests with code coverage report
echo   debug            Run tests with debug logging
echo   clean            Clean build artifacts
echo   help             Show this help message
echo.
echo Options:
echo   -v, --verbose    Show detailed output
echo   -q, --quiet      Minimal output
echo   -p, --parallel   Run tests in parallel (4 workers)
echo   -h, --help       Show this help message
echo.
echo Examples:
echo   run-tests.bat all                    - Run all tests
echo   run-tests.bat unit -v                - Run unit tests with verbose output
echo   run-tests.bat model                  - Run model tests only
echo   run-tests.bat integration -p         - Run integration tests in parallel
echo   run-tests.bat coverage               - Generate coverage report
echo.

:end
endlocal
exit /b 0

