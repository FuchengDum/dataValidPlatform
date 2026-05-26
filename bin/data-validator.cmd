@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "APP_HOME=%%~fI"

if defined DATA_VALIDATOR_JAR (
  set "JAR_PATH=%DATA_VALIDATOR_JAR%"
) else (
  set "JAR_PATH=%APP_HOME%\backend\target\data-validator-0.1.0.jar"
)

if not exist "%JAR_PATH%" (
  >&2 echo data-validator jar not found: %JAR_PATH%
  >&2 echo Build it first: cd backend ^&^& mvn package -DskipTests
  exit /b 1
)

java %JAVA_OPTS% -jar "%JAR_PATH%" %*
exit /b %ERRORLEVEL%
