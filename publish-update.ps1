# publish-update.ps1
# Automates compiling and publishing OTA beta builds to the Sovereign VPS update server.

param(
    [string]$Notes = ""
)

$AdminKey = "test_secret_4666"
$UploadUrl = "https://api.cosmowhisper.com/updates/upload"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " 🚀 Kin-Tracker Sovereign OTA Publisher" -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

Write-Host "🔨 Compiling latest APK build with Gradle..." -ForegroundColor Yellow
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Error "`n❌ Compilation failed! Build abandoned."
    exit $LASTEXITCODE
}

$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"
$BuildOutputsJson = "app\build\outputs\apk\debug\output-metadata.json"

if (-not (Test-Path $ApkPath)) {
    Write-Error "❌ Could not find compiled APK at: $ApkPath"
    exit 1
}

# Automatically extract version details directly from Android build output
$VersionCode = 14
$VersionName = "2.6.1"

if (Test-Path $BuildOutputsJson) {
    try {
        $Metadata = Get-Content $BuildOutputsJson | ConvertFrom-Json
        $VersionCode = $Metadata.elements[0].versionCode
        $VersionName = $Metadata.elements[0].versionName
    } catch {
        Write-Warning "Could not parse output-metadata.json, using defaults."
    }
}

if ([string]::IsNullOrWhiteSpace($Notes)) {
    $ReleaseNotes = Read-Host "Enter release notes for this build (or press Enter for default)"
    if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        $ReleaseNotes = "Bug fixes, school geofence stability, and performance improvements."
    }
} else {
    $ReleaseNotes = $Notes
}

Write-Host "`n📦 Build Ready:" -ForegroundColor Green
Write-Host "   Version: $VersionName (code: $VersionCode)"
Write-Host "   File:    $ApkPath"
Write-Host "   Notes:   $ReleaseNotes`n"

Write-Host "📡 Uploading to VPS ($UploadUrl)..." -ForegroundColor Yellow

$FormParameters = @{
    apk          = Get-Item $ApkPath
    versionCode  = "$VersionCode"
    versionName  = "$VersionName"
    releaseNotes = "$ReleaseNotes"
}

$Headers = @{
    "x-admin-key" = $AdminKey
}

try {
    $ServerResponse = Invoke-RestMethod -Uri $UploadUrl -Method Post -Form $FormParameters -Headers $Headers
    Write-Host "`n🎉 OTA DEPLOYMENT SUCCESSFUL!" -ForegroundColor Green
    Write-Host "   Message: $($ServerResponse.message)" -ForegroundColor Cyan
    Write-Host "   Family phones will automatically prompt to update on next launch.`n" -ForegroundColor White
} catch {
    Write-Warning "`nNote: If api.cosmowhisper.com has not yet restarted with the new /updates route,"
    Write-Warning "deploy the server update or run against local/staging host."
    Write-Error "❌ Server deployment upload failed: $_"
}
