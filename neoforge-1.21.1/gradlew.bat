@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=9.2.1"
set "EXPECTED_SHA256=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f"

rem Bootstrap pinned Gradle, verifying the downloaded archive before extraction.
for %%I in ("%~dp0.") do set "PROJECT_DIR=%%~fI"
set "CACHE_DIR=%PROJECT_DIR%\.gradle-bootstrap"
set "GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
if exist "%ZIP%" (
  call :verify "%ZIP%"
  if errorlevel 1 (
    echo Cached Gradle ZIP failed SHA-256 verification; removing it. 1>&2
    del /q "%ZIP%"
  )
)

if not exist "%ZIP%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ZIP%.download'"
  if errorlevel 1 exit /b 1
  call :verify "%ZIP%.download"
  if errorlevel 1 (
    echo Downloaded Gradle ZIP failed SHA-256 verification. 1>&2
    del /q "%ZIP%.download"
    exit /b 1
  )
  move /y "%ZIP%.download" "%ZIP%" >nul
  if errorlevel 1 exit /b 1
)

call :verify "%ZIP%"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%ZIP%' -DestinationPath '%CACHE_DIR%'"
if errorlevel 1 exit /b 1

:run
pushd "%PROJECT_DIR%"
call "%GRADLE_HOME%\bin\gradle.bat" %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%

:verify
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-FileHash -LiteralPath '%~1' -Algorithm SHA256).Hash.ToLowerInvariant() -ne '%EXPECTED_SHA256%') { exit 1 }"
exit /b %ERRORLEVEL%
