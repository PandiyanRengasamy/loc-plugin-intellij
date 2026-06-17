@echo off
set JAVA_HOME=C:\Pandiyan\Sofware\jdk-22.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java: %JAVA_HOME%
java -version
echo.
echo ============================================================
echo  Launching sandbox IDE with plugin in DEBUG mode
echo  Sandbox IDE WILL PAUSE and wait for debugger on port 5005
echo  In your main IntelliJ: Run ^> Remote JVM Debug ^> port 5005
echo ============================================================
echo.
rem call gradlew.bat runIde --debug-jvm -PskipBuildSearchableOptions --no-build-cache
call gradlew.bat runIde --debug-jvm
