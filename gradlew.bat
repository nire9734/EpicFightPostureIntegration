@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=8.8"

rem Resolve the project directory without a trailing backslash.
for %%I in ("%~dp0.") do set "PROJECT_DIR=%%~fI"
set "CACHE_DIR=%PROJECT_DIR%\.gradle-bootstrap"
set "GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
if not exist "%ZIP%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ZIP%'"
  if errorlevel 1 exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%ZIP%' -DestinationPath '%CACHE_DIR%'"
if errorlevel 1 exit /b 1

:run
pushd "%PROJECT_DIR%"
call "%GRADLE_HOME%\bin\gradle.bat" %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
