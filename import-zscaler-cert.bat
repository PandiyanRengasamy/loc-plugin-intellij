@echo off
set KT=C:\Pandiyan\Sofware\jdk-22.0.2\bin\keytool.exe
set KS=C:\Pandiyan\Sofware\jdk-22.0.2\lib\security\cacerts

echo Step 1: Downloading Zscaler Root CA from live server...
%KT% -printcert -sslserver cache-redirector.jetbrains.com:443 -rfc > "%TEMP%\zscaler-chain.pem" 2>&1
type "%TEMP%\zscaler-chain.pem"

echo.
echo Step 2: Importing Zscaler Root CA into JDK 22 truststore...
%KT% -importcert -trustcacerts -noprompt -alias zscaler-root-ca -file "%TEMP%\zscaler-chain.pem" -keystore %KS% -storepass changeit

if %ERRORLEVEL%==0 (
    echo.
    echo SUCCESS: Zscaler Root CA imported into JDK 22!
) else (
    echo.
    echo FAILED - may already exist or need Administrator rights
    echo Please RIGHT-CLICK this bat file and choose "Run as Administrator"
)

echo.
pause

