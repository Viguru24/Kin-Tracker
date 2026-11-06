<div align="center">

# 🛰️ Kin-Tracker

### Next-Generation, Private, 24/7 Family Safety Radar & GPS Transit Network
*The Privacy-First, Open-Source Life360 Alternative for Android.*

<br/>

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-00C853?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25%20No%20Data%20Brokers-00ff88?style=for-the-badge&logo=shield)](https://github.com/Viguru24/Kin-Tracker)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Latest Release](https://img.shields.io/badge/Release-v2.8.1_Latest-10B981?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Viguru24/Kin-Tracker/releases/latest)

<br/>

<p align="center">
  <a href="https://github.com/Viguru24/Kin-Tracker/releases/download/v2.8.1/KinTracker-v2.8.1.apk">
    <img src="https://img.shields.io/badge/📥_Download_APK-v2.8.1_Stable-00E676?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" height="42" />
  </a>
</p>

<p align="center">
  <a href="#-why-kin-tracker">🌟 <b>Why Kin-Tracker</b></a> •
  <a href="#-feature-comparison">📊 <b>Comparison</b></a> •
  <a href="#-key-features">🚀 <b>Features</b></a> •
  <a href="#-download--installation">📥 <b>Download & Install</b></a> •
  <a href="#-tech-stack--architecture">🛠️ <b>Tech Stack</b></a> •
  <a href="#-the-ecosystem">🌟 <b>Ecosystem</b></a>
</p>

</div>

---

## 🌟 Why Kin-Tracker?

Commercial family trackers (like Life360) have faced severe backlash for selling user location history to third-party data brokers while locking essential safety features behind costly monthly subscriptions ($15/mo+).

**Kin-Tracker provides a completely sovereign alternative:**
1. **🛡️ 100% Privacy-First:** No location data brokers, no user telemetry, and no accounts required — data is encrypted and shared strictly within your private family circle.
2. **⚡ Bulletproof Screen-Off Background GPS:** Intelligent hardware `WakeLock` cycling and foreground service prioritization ensure continuous 24/7 location streaming without being killed by Android Doze mode.
3. **💸 Free & Sovereign Forever:** No paywalled geofences, no driving reports subscription fees, and zero ads.

---

## 📊 Feature Comparison

| Feature | Life360 ($15+/mo) | Apple Find My | 🛰️ **Kin-Tracker** |
| :--- | :---: | :---: | :---: |
| **No Location Data Selling** | ❌ (Sells Data) | ✅ | **✅ 100% Private** |
| **Cross-Platform / Pure Android** | ⚠️ | ❌ (Apple Only) | **✅ Android Native** |
| **Unlimited Safe Zones (Geofences)** | ❌ (Paywalled) | ⚠️ (Limited) | **✅ Unlimited** |
| **Live Battery & Transit Speed HUD** | ⚠️ | ❌ | **✅ Real-Time HUD** |
| **Turn-by-Turn Route Trails** | ❌ (Requires Gold) | ❌ | **✅ Built-in** |
| **Ghost Mode Privacy Blur** | ⚠️ (Requires Gold) | ❌ | **✅ Built-in (1-Tap)** |
| **Shared Family Grocery & Task List** | ❌ | ❌ | **✅ Real-Time Synced** |

---

## 🚀 Key Features

| Feature | Description |
| :--- | :--- |
| **🛡️ 24/7 Background Radar** | Continuous GPS location streaming that never sleeps, even when phones are locked in pockets or deep sleep mode. |
| **🛤️ Turn-by-Turn Route Trails** | Live breadcrumb road pathing from Home to current location with automated bounding-box camera auto-framing. |
| **🚨 Emergency SOS System** | Instant one-tap SOS trigger with high-priority audio alarms, screen flash alerts, and direct location beacons. |
| **🔋 Battery & Speed Telemetry** | Live battery percentage, charging state indicators, transit speed (mph/km/h), and automatic low-battery warnings (<15%). |
| **🛒 Synced Family Shopping List** | Real-time shared grocery & task lists with instant cloud checkoffs and member attribution. |
| **👻 Ghost Mode Privacy** | Pause or blur location sharing for 1h, 2h, 8h, or custom durations when privacy is desired. |
| **📍 Smart Safe Zones & Geofences** | Instant arrival and departure activity logging for Home, School, Work, and custom-defined safe circles. |
| **🗣️ Voice Proximity Radar** | Text-to-speech voice announcements when family members arrive safely, depart, or move closer. |
| **🎨 Marker Collision Deconfliction** | Smart visual layout engine that prevents overlapping map pins when multiple family members are in the same spot. |

---

## 🛠️ Tech Stack & Architecture

- **Language & Framework:** 100% Kotlin with declarative **Jetpack Compose** & **Material 3**.
- **Architecture:** Clean MVVM (Model-View-ViewModel) + StateFlow reactive streams + Repository pattern.
- **Local Persistence:** **Room Database** (SQLite) with zero-loss fallback migration and persistent member caching.
- **Mapping & Geodesy:** **OSMDroid** with offline tile caching and OSRM road-routing APIs.
- **Background Engine:** Android Foreground Service with `PowerManager.PARTIAL_WAKE_LOCK`, `AlarmManager.RTC_WAKEUP`, and `LocationListener`.
- **Networking & Serialization:** OkHttp3 + Moshi JSON for peer-to-peer cloud state synchronization.

---

## 📥 Download & Installation

### Option 1: Direct APK Download (Android Phones)

Download the pre-compiled Android package directly to your phone:

<div align="center">

[![Download Kin-Tracker APK](https://img.shields.io/badge/Download-KinTracker_APK_(v2.8.1)-00E676?style=for-the-badge&logo=android&logoColor=black)](https://github.com/Viguru24/Kin-Tracker/releases/download/v2.8.1/KinTracker-v2.8.1.apk)
[![GitHub Releases](https://img.shields.io/badge/All_Releases-GitHub-27272a?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Viguru24/Kin-Tracker/releases)

</div>

1. Tap **Download APK** above on your Android phone (Android 8.0 Oreo or newer).
2. Open the downloaded `.apk` file and tap **Install** (allow *"Install unknown apps"* if prompted by your browser/file manager).
3. Open **Kin-Tracker**, select your name and avatar emoji, and either:
   - **Create a New Circle:** Generates your private invite code to share with family members.
   - **Join an Existing Circle:** Enter the 6-character circle code from your family.
4. *(Optional for Self-Hosters)* Tap the **Server URL** button on the setup screen or in **Settings** to route all traffic to your own private sovereign VPS.

---

### Option 2: Build From Source (Developers)

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 34+
- Java JDK 17+

```bash
# 1. Clone the repository
git clone https://github.com/Viguru24/Kin-Tracker.git
cd Kin-Tracker

# 2. Build the APK
./gradlew assembleDebug
```
The compiled APK will be output to: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🚀 Sovereign Over-The-Air (OTA) Update Hub
The Kin-Tracker backend server (`server/src/routes/updates.js`) hosts the central **Sovereign OTA Update Hub** for your entire Android application ecosystem:
- **Kin-Tracker** (`kin-tracker`)
- **COSMO Symphony Companion** (`cosmo-companion`)
- **Jinshin Jyutsu Android** (`jinshin-jyutsu`)
- **Cosmo Flow Personal Assistant** (`cosmo-flow`)
- **Cosmo Compendium Cooking App** (`cosmo-compendium`)

Check live system status anytime at [`https://api.cosmowhisper.com/updates/status`](https://api.cosmowhisper.com/updates/status).  
To publish updates, use `.\publish-update.ps1` or double-click `PUBLISH_UPDATE.bat`.  
See [OTA_UPDATE_GUIDE.md](OTA_UPDATE_GUIDE.md) for full endpoint specifications.

---

## 🌟 The Ecosystem

Discover other sovereign, high-performance tools by **[Viguru24](https://github.com/Viguru24)**:
- 🎬 **[Vixz YouTube Player (Android)](https://github.com/Viguru24/YouTube)** — Ad-free YouTube player with AI summaries and fluid gestures.
- 🌌 **[Cosmo Symphony (Windows)](https://github.com/Viguru24/Video)** — GPU-accelerated video & photo studio built with Rust & Tauri v2.
- 🎙️ **[CosmoWhisper (Mac & Windows)](https://github.com/Viguru24/CosmoWhisper-Native)** — 100% local AI speech dictation.

---

<div align="center">
  <sub>Distributed under the MIT License • Built with ❤️ by <a href="https://github.com/Viguru24">Viguru24</a></sub>
</div>
