<div align="center">

# 🛰️ Kin-Tracker (Pulse Tracker)
### *Next-Generation, Private, 24/7 Family Safety Radar & GPS Transit Network*

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![OSMDroid](https://img.shields.io/badge/Maps-OpenStreetMap-7EBC6F?style=for-the-badge&logo=openstreetmap&logoColor=white)](https://osmdroid.github.io/osmdroid/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

<br/>

> **Never lose track of your loved ones.**  
> Kin-Tracker is a battery-efficient, privacy-focused, real-time family location tracker built for Android with continuous screen-off GPS streaming, smart geofencing, route breadcrumbs, and instant peer-to-peer cloud sync.

[**Download Latest APK**](https://github.com/Viguru24/kin-tracker/releases) • [**Explore Features**](#-key-features) • [**Tech Stack**](#-tech-stack--architecture) • [**Getting Started**](#-quick-start--build-guide)

---

</div>

## 🌟 Why Kin-Tracker?

Most family tracking apps (like Life360 or Find My) suffer from three critical flaws: aggressive battery drain, invasive subscription paywalls, and location freezing when the phone screen is turned off in a pocket.

**Kin-Tracker solves all three:**
1. **Bulletproof Screen-Off Background GPS:** Uses intelligent hardware `WakeLock` cycling and foreground service prioritization to keep location updating 24/7 without being put to sleep by Android Doze mode.
2. **100% Free & Open Ecosystem:** No monthly subscriptions, no paywalled safety features, and zero intrusive ads.
3. **Privacy First:** Instant group joining via private 4-digit PIN codes with optional Ghost Mode location pausing.

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

## 📱 User Interface Highlights

- **Dynamic Interactive Radar Map:** Powered by OpenStreetMap (OSMDroid) with custom dark/light tiles, transit velocity rings, and smooth camera animations.
- **Glassmorphism Status Cards:** Real-time arrival timers (*"here for 45m"*, *"Live"*), battery gauges, and direct SMS/Call shortcuts.
- **Quick-Action Bottom Bar:** Floating controls for Circle Switching, Safe Zones, Shopping List, Route Trails, and Emergency SOS.

---

## 🛠️ Tech Stack & Architecture

- **Language & Framework:** 100% Kotlin with declarative **Jetpack Compose** & **Material 3**.
- **Architecture:** Clean MVVM (Model-View-ViewModel) + StateFlow reactive streams + Repository pattern.
- **Local Persistence:** **Room Database** (SQLite) with zero-loss fallback migration and persistent member caching.
- **Mapping & Geodesy:** **OSMDroid** with offline tile caching and OSRM road-routing APIs.
- **Background Engine:** Android Foreground Service with `PowerManager.PARTIAL_WAKE_LOCK`, `AlarmManager.RTC_WAKEUP`, and `LocationListener`.
- **Networking & Serialization:** OkHttp3 + Moshi JSON for peer-to-peer cloud state synchronization.

---

## 📥 Quick Start & Build Guide

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 34+
- Java JDK 17+

### 1. Clone the Repository
```bash
git clone https://github.com/Viguru24/kin-tracker.git
cd kin-tracker
```

### 2. Build the Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install to Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Permissions & Privacy

Kin-Tracker requires standard location and foreground permissions solely for family safety tracking:
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: Precise GPS positioning.
- `ACCESS_BACKGROUND_LOCATION`: Continuous screen-off updates.
- `FOREGROUND_SERVICE_LOCATION`: Ongoing background sync notification.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Prevents Android/Samsung battery managers from suspending tracking in deep sleep.

---

## 🤝 Contributing & Community

Contributions, feature suggestions, and bug reports are warmly welcomed!
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See [LICENSE](LICENSE) for more information.

<div align="center">

**Built with ❤️ for keeping families connected and safe everywhere.**

</div>
