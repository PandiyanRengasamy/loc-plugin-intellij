@echo off
set KT=C:\Pandiyan\Sofware\jdk-22.0.2\bin\keytool.exe
set KS=C:\Pandiyan\Sofware\jdk-22.0.2\lib\security\cacerts
set CF=C:\Users\752004\Downloads\_.cloudfront.net.crt

echo Importing cloudfront certificate into JDK 22 truststore...
%KT% -importcert -trustcacerts -noprompt -alias cloudfront-jb -file %CF% -keystore %KS% -storepass changeit

if %ERRORLEVEL%==0 (
    echo SUCCESS: Certificate imported!
) else (
    echo FAILED or already exists. Trying -cacerts shortcut...
    %KT% -importcert -cacerts -trustcacerts -noprompt -alias cloudfront-jb2 -file %CF% -storepass changeit
)

echo.
echo Done. Now run build-plugin.bat
pause
