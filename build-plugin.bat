@echo off
rem ── Set this to your JDK 22 installation path ────────────────────────────
set JAVA_HOME=C:\Pandiyan\Sofware\jdk-22.0.2
rem ─────────────────────────────────────────────────────────────────────────
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java: %JAVA_HOME%
java -version
echo.
echo Building plugin...
call gradlew.bat clean buildPlugin
echo.
if %ERRORLEVEL%==0 (
    echo BUILD SUCCESS
    echo Plugin ZIP: build\distributions\genai-loc-tracker-1.0.0.zip
) else (
    echo BUILD FAILED - check errors above
)
