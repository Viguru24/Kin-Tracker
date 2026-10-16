# Sovereign Multi-App Over-The-Air (OTA) Update Hub

The sync server in this repository (`server/src/routes/updates.js` and `server/src/server_multi_ota.js`) powers the sovereign Over-The-Air (OTA) update hub for all sovereign Android applications hosted on `https://api.cosmowhisper.com/updates`.

## Supported Applications
| App Name | Canonical Slug | Target Package / Repo |
| :--- | :--- | :--- |
| **Kin-Tracker** | `kin-tracker` | `com.viguru24.kintracker` |
| **COSMO Companion** | `cosmo-companion` | `com.cosmo.symphony.companion` |
| **Jinshin Jyutsu** | `jinshin-jyutsu` | `com.example.jinshin` |
| **Cosmo Flow** | `cosmo-flow` | `com.example.cosmoflow` |
| **Cosmo Compendium** | `cosmo-compendium` | `com.aistudio.heirloomrecipes.xkvp` |

## Server Endpoints
- **Status & Diagnostics:** `GET /updates/status` or `GET /updates/list` (Overview of all registered apps, versions, file sizes)
- **Version Check:** `GET /updates/:appId/version.json` or `GET /updates/version.json?app=:appId`
- **APK Download:** `GET /updates/:appId/app-latest.apk` or `GET /updates/app-latest.apk?app=:appId`
- **Publish Upload:** `POST /updates/:appId/upload` (Requires `x-admin-key: test_secret_4666`)

## Backward Compatibility
Legacy requests omitting `:appId` or `?app=` automatically default to `kin-tracker`, ensuring zero downtime or interruption to existing family tracking devices.

## Publishing Kin-Tracker Releases
- **Desktop Shortcut:** Double-click `Publish_Kin_Tracker.bat` in `C:\Users\louis\Desktop\OTA Update Publishers`.
- **PowerShell:**
  ```powershell
  cd C:\Users\louis\Documents\GitHub\kin-tracker
  .\publish-update.ps1 -Notes "Geofence accuracy, battery life and stability improvements"
  ```
