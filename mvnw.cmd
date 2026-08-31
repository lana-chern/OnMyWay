@ECHO OFF
SETLOCAL

SET "MAVEN_PROJECTBASEDIR=%~dp0"
SET "MAVEN_WRAPPER_VERSION=3.3.4"
SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
SET "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/%MAVEN_WRAPPER_VERSION%/maven-wrapper-%MAVEN_WRAPPER_VERSION%.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
  IF NOT EXIST "%MAVEN_PROJECTBASEDIR%.mvn\wrapper" MKDIR "%MAVEN_PROJECTBASEDIR%.mvn\wrapper"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
  IF ERRORLEVEL 1 EXIT /B 1
)

java -jar "%WRAPPER_JAR%" %*
ENDLOCAL
