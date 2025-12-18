# SteamDeck Mobile - Java Environment Setup (PowerShell)
# Sets JAVA_HOME and PATH for current user (no admin rights required)

$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"

Write-Host "Setting up Java environment variables for current user..." -ForegroundColor Cyan

# Set JAVA_HOME
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
Write-Host "✓ JAVA_HOME set to: $javaHome" -ForegroundColor Green

# Get current user PATH
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "User")

# Add JAVA_HOME\bin to PATH if not already present
$javaBinPath = "$javaHome\bin"
if ($currentPath -notlike "*$javaBinPath*") {
    $newPath = "$currentPath;$javaBinPath"
    [System.Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    Write-Host "✓ Added $javaBinPath to PATH" -ForegroundColor Green
} else {
    Write-Host "✓ $javaBinPath already in PATH" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Java environment setup complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "IMPORTANT: Please restart your terminal/IDE for changes to take effect." -ForegroundColor Yellow
Write-Host ""
Write-Host "To verify, run in a NEW terminal:" -ForegroundColor White
Write-Host "  java -version" -ForegroundColor Gray
Write-Host "  echo `$env:JAVA_HOME" -ForegroundColor Gray
Write-Host ""

Read-Host "Press Enter to exit"
