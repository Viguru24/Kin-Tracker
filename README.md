<div align="center">

# 🛰️ Kin-Tracker

### Next-Generation, Private, 24/7 Family Safety Radar & GPS Transit Network
*The Privacy-First, Open-Source Life360 Alternative for Android.*

<br/>

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy First](https://img.shields.io/badge/Privacy-100%25%20No%20Data%20Brokers-00ff88?style=for-the-badge&logo=shield)](https://github.com/Viguru24/Kin-Tracker)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<p align="center">
  <a href="#-why-kin-tracker">🌟 <b>Why Kin-Tracker</b></a> •
  <a href="#-feature-comparison">📊 <b>Comparison</b></a> •
  <a href="#-key-features">🚀 <b>Features</b></a> •
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

## 📥 Build & Installation

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

## 🌟 The Ecosystem

Discover other sovereign, high-performance tools by **[Viguru24](https://github.com/Viguru24)**:
- 🎬 **[Vixz YouTube Player (Android)](https://github.com/Viguru24/YouTube)** — Ad-free YouTube player with AI summaries and fluid gestures.
- 🌌 **[Cosmo Symphony (Windows)](https://github.com/Viguru24/Video)** — GPU-accelerated video & photo studio built with Rust & Tauri v2.
- 🎙️ **[CosmoWhisper (Mac & Windows)](https://github.com/Viguru24/CosmoWhisper-Native)** — 100% local AI speech dictation.

---

<div align="center">
  <sub>Distributed under the MIT License • Built with ❤️ by <a href="https://github.com/Viguru24">Viguru24</a></sub>
</div>
