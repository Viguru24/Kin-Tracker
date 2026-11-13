param(
    [string]$AppId = "kin-tracker",
    [string]$VersionCode = "",
    [string]$BackupId = "",
    [switch]$ListOnly,
    [string]$ServerUrl = "https://api.cosmowhisper.com",
    [string]$AdminKey = "test_secret_4666"
)

$ErrorActionPreference = "Stop"

$headers = @{ "x-admin-key" = $AdminKey }
$backupsUrl = "$ServerUrl/updates/$AppId/backups"

Write-Host "Checking OTA backups on $backupsUrl ..." -ForegroundColor Cyan
$status = Invoke-RestMethod -Uri $backupsUrl -Method GET -Headers $headers

if ($status.current) {
    Write-Host ("Current live: v{0} (versionCode {1})" -f $status.current.versionName, $status.current.versionCode) -ForegroundColor Green
}

if (-not $status.backups -or $status.backups.Count -eq 0) {
    Write-Warning "No backups archived on server yet."
    exit 0
}

Write-Host "Archived backups (newest first):" -ForegroundColor Cyan
$status.backups | ForEach-Object {
    $mb = [math]::Round(($_.apkSize / 1MB), 2)
    Write-Host ("  - {0}  v{1} (code {2})  [{3} MB]  {4}" -f $_.backupId, $_.versionName, $_.versionCode, $mb, $_.releaseNotes)
}

if ($ListOnly) {
    exit 0
}

$payload = @{}
if ($BackupId) { $payload.backupId = $BackupId }
elseif ($VersionCode) { $payload.versionCode = [int]$VersionCode }

$jsonBody = ($payload | ConvertTo-Json -Compress)
$rollbackUrl = "$ServerUrl/updates/$AppId/rollback"

Write-Host "Triggering OTA rollback via $rollbackUrl ..." -ForegroundColor Yellow
$res = Invoke-RestMethod -Uri $rollbackUrl -Method POST -Headers $headers -ContentType "application/json" -Body $jsonBody
Write-Host $res.message -ForegroundColor Green
Write-Host "Verify on live version.json -> v$($res.metadata.versionName) (code $($res.metadata.versionCode))" -ForegroundColor Cyan
