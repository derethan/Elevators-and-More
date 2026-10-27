# PowerShell Version Bump Helper for Elevators and More
param(
    [ValidateSet("patch", "minor", "major")]
    [string]$Type = "patch"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

Set-Location -Path $projectDir

Write-Host "Running Version Bump ($Type)..." -ForegroundColor Cyan

switch ($Type) {
    "minor" { .\gradlew.bat bumpMinor }
    "major" { .\gradlew.bat bumpMajor }
    default { .\gradlew.bat bumpPatch }
}
