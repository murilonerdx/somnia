@echo off
REM ============================================================================
REM Somnia CLI Installer for Windows
REM Installs Somnia CLI and adds to PATH
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ========================================
echo    Somnia Language Installer v1.0.0
echo ========================================
echo.

REM Check for admin rights
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Warning: Running without admin rights.
    echo PATH will be set for current user only.
    set "ADMIN_MODE=0"
) else (
    set "ADMIN_MODE=1"
)

REM Set install directory
set "INSTALL_DIR=%USERPROFILE%\.somnia"
set "BIN_DIR=%INSTALL_DIR%\bin"
set "LIB_DIR=%INSTALL_DIR%\lib"
set "CORE_DIR=%INSTALL_DIR%\core"
set "EXTENSIONS_DIR=%INSTALL_DIR%\extensions"

echo Install directory: %INSTALL_DIR%
echo.

REM Create directories
echo Creating directories...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"
if not exist "%LIB_DIR%" mkdir "%LIB_DIR%"
if not exist "%CORE_DIR%" mkdir "%CORE_DIR%"
if not exist "%EXTENSIONS_DIR%" mkdir "%EXTENSIONS_DIR%"

REM Get source directory (where install.bat is located)
set "SOURCE_DIR=%~dp0"

REM Copy files
echo Copying files...

REM Copy launcher
copy /Y "%SOURCE_DIR%bin\somnia.bat" "%BIN_DIR%\" >nul

REM Copy VM jar (from somnia-vm build)
set "JAR_SOURCE=%SOURCE_DIR%..\somnia-vm\build\libs\somnia.jar"
if exist "%JAR_SOURCE%" (
    copy /Y "%JAR_SOURCE%" "%LIB_DIR%\" >nul
    echo - Copied somnia.jar
) else (
    echo Warning: somnia.jar not found at %JAR_SOURCE%
    echo Please build somnia-vm first: gradlew :somnia-vm:jar
)

REM Copy core library
if exist "%SOURCE_DIR%..\somnia-core" (
    xcopy /E /I /Y "%SOURCE_DIR%..\somnia-core" "%CORE_DIR%\somnia-core" >nul
    echo - Copied somnia-core
)

REM Copy extensions
if exist "%SOURCE_DIR%..\somnia-json" (
    xcopy /E /I /Y "%SOURCE_DIR%..\somnia-json" "%EXTENSIONS_DIR%\somnia-json" >nul
    echo - Copied somnia-json extension
)

REM Add to PATH
echo.
echo Configuring PATH...

REM Check if already in PATH
echo %PATH% | findstr /I /C:"%BIN_DIR%" >nul
if %errorlevel% equ 0 (
    echo PATH already contains Somnia bin directory.
) else (
    if "%ADMIN_MODE%"=="1" (
        REM System-wide PATH (requires admin)
        setx /M PATH "%PATH%;%BIN_DIR%" >nul 2>&1
        echo Added to system PATH.
    ) else (
        REM User PATH
        setx PATH "%PATH%;%BIN_DIR%" >nul 2>&1
        echo Added to user PATH.
    )
)

REM Create environment variable
setx SOMNIA_HOME "%INSTALL_DIR%" >nul 2>&1
echo Set SOMNIA_HOME=%INSTALL_DIR%

echo.
echo ========================================
echo    Installation Complete!
echo ========================================
echo.
echo Please restart your terminal/command prompt
echo for the changes to take effect.
echo.
echo Quick start:
echo   somnia version           # Check installation
echo   somnia init my-project   # Create new project
echo   somnia run file.somnia   # Run a file
echo.
echo Documentation: https://github.com/murilonerdx/somnia
echo.

pause
exit /b 0
