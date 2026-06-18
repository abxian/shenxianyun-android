param(
  [ValidateSet("release", "debug")]
  [string]$Mode = "release",
  [switch]$Clean
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Missing command: $Name"
  }
}

Set-Location $PSScriptRoot

Require-Command java

if ($Clean) {
  .\gradlew.bat clean
}

if ($Mode -eq "debug") {
  .\gradlew.bat :app:assembleMetaDebug --rerun-tasks
  $Output = "app\build\outputs\apk\meta\debug"
} else {
  .\gradlew.bat :app:assembleMetaRelease --rerun-tasks
  $Output = "app\build\outputs\apk\meta\release"
}

Write-Host ""
Write-Host "Build finished."
Write-Host "APK output:"
Get-ChildItem -Path $Output -Filter "*.apk" -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending |
  Select-Object FullName, LastWriteTime

if (Test-Path $Output) {
  Invoke-Item $Output
}
