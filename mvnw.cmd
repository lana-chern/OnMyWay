@echo off
setlocal
set "MAVEN_VERSION=3.9.11"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_EXE=%MAVEN_HOME%\bin\mvn.cmd"

if exist "%MAVEN_EXE%" goto run_maven

set "TMP_DIR=%TEMP%\maven-wrapper-%RANDOM%%RANDOM%"
mkdir "%TMP_DIR%" >nul 2>&1
set "ARCHIVE=%TMP_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"
set "URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

echo Downloading Maven %MAVEN_VERSION% from %URL%
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ARCHIVE%'"
if errorlevel 1 goto download_error

powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%TMP_DIR%' -Force"
if errorlevel 1 goto extract_error

if not exist "%MAVEN_USER_HOME%\wrapper\dists" mkdir "%MAVEN_USER_HOME%\wrapper\dists"
if exist "%MAVEN_HOME%" rmdir /s /q "%MAVEN_HOME%"
move "%TMP_DIR%\apache-maven-%MAVEN_VERSION%" "%MAVEN_USER_HOME%\wrapper\dists\" >nul
if errorlevel 1 goto install_error

rmdir /s /q "%TMP_DIR%" >nul 2>&1

goto run_maven

:download_error
echo Failed to download Maven %MAVEN_VERSION%. 1>&2
rmdir /s /q "%TMP_DIR%" >nul 2>&1
exit /b 1

:extract_error
echo Failed to extract Maven %MAVEN_VERSION%. 1>&2
rmdir /s /q "%TMP_DIR%" >nul 2>&1
exit /b 1

:install_error
echo Failed to install Maven %MAVEN_VERSION%. 1>&2
rmdir /s /q "%TMP_DIR%" >nul 2>&1
exit /b 1

:run_maven
call "%MAVEN_EXE%" %*
exit /b %ERRORLEVEL%
