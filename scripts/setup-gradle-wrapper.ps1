param([string]$Version = "9.9.1")
$ErrorActionPreference = "Stop"
Push-Location (Join-Path $PSScriptRoot "..")
try {
    gradle wrapper --gradle-version $Version --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed." }
} finally {
    Pop-Location
}
