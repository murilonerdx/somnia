# Somnia Installer for Windows
# Installs Somnia Engine to ~/.somnia/bin and sets up file associations.

$ErrorActionPreference = "Stop"

$InstallDir = "$env:USERPROFILE\.somnia"
$BinDir = "$InstallDir\bin"
$EnginePath = "$BinDir\somnia.exe"
$SourceExe = "$PSScriptRoot\somnia_engine.exe"

Write-Host "Installing Somnia to $InstallDir..."

# 1. Create Directories
if (-not (Test-Path $BinDir)) {
    New-Item -ItemType Directory -Force -Path $BinDir | Out-Null
}

# 2. Copy Executable
if (Test-Path $SourceExe) {
    Copy-Item -Path $SourceExe -Destination $EnginePath -Force
    Write-Host "Engine copied."
} else {
    Write-Error "somnia_engine.exe not found in installer directory!"
}

# 3. Create 'somnia' shim
$ShimPath = "$BinDir\somnia.bat"
Set-Content -Path $ShimPath -Value "@echo off`r`n`"%~dp0somnia.exe`" %*"
Write-Host "Shim created."

# 4. Add to PATH
$CurrentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($CurrentPath -notlike "*$BinDir*") {
    $NewPath = "$CurrentPath;$BinDir"
    [Environment]::SetEnvironmentVariable("Path", $NewPath, "User")
    Write-Host "Added $BinDir to User PATH."
} else {
    Write-Host "Already in PATH."
}

# 5. File Associations (HKCU - No Admin required usually)
Function Set-Association {
    param ($Ext, $FileType, $Desc)
    $Classes = "HKCU:\Software\Classes"
    
    # Extension Key
    $ExtKey = "$Classes\$Ext"
    if (-not (Test-Path $ExtKey)) { New-Item $ExtKey -Force | Out-Null }
    Set-ItemProperty $ExtKey -Name "(default)" -Value $FileType

    # File Type Key
    $TypeKey = "$Classes\$FileType"
    if (-not (Test-Path $TypeKey)) { New-Item $TypeKey -Force | Out-Null }
    Set-ItemProperty $TypeKey -Name "(default)" -Value $Desc

    # Command
    $CmdKey = "$TypeKey\shell\open\command"
    if (-not (Test-Path $CmdKey)) { New-Item $CmdKey -Force | Out-Null }
    Set-ItemProperty $CmdKey -Name "(default)" -Value "`"$EnginePath`" run `"%1`""
}

Write-Host "Setting up file associations..."
try {
    Set-Association ".somnia" "Somnia.Script" "Somnia Script File"
    Set-Association ".som" "Somnia.Bundle" "Somnia Bundle"
    Write-Host "Associations registered. You can now double-click .somnia files!"
} catch {
    Write-Warning "Could not set file associations. (Registry access denied?)"
}

Write-Host "`nInstallation Complete! Please restart your terminal."
Write-Host "Type 'somnia version' to verify."

# Pause if running from explorer
if ($Host.Name -eq "ConsoleHost") {
    Read-Host "Press Enter to exit"
}
