#  SeismoSync

> **Real-time disaster detection and emergency coordination platform with native iOS & Android clients**

![Platform](https://img.shields.io/badge/Platform-iOS%20%7C%20Android%20%7C%20Web-blue)
![Swift](https://img.shields.io/badge/Swift-5.0-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple)
![Firebase](https://img.shields.io/badge/Firebase-Realtime%20DB-yellow)
![MQTT](https://img.shields.io/badge/Protocol-MQTT-green)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

##  Overview

SeismoSync is a cross-platform disaster detection and emergency response system that combines mobile sensing, embedded hardware, and real-time cloud infrastructure to detect seismic events and distress signals — and instantly alert rescue coordination centers.

The system features a native **iOS app (Swift)**, a native **Android app (Kotlin)**, an **ESP32 hardware node**, a **Node.js backend**, and a **live web dashboard** for rescue teams.

---

##  Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   iOS App       │     │  Android App    │     │   ESP32 Node    │
│   (Swift)       │     │  (Kotlin)       │     │   (Hardware)    │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                        │
         └───────────────────────┼────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │     MQTT Broker         │
                    │  (Simulated Satellite)  │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Firebase Realtime DB  │
                    │   + Node.js Backend     │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Web Dashboard         │
                    │   (Rescue Centers)      │
                    └─────────────────────────┘
```

---

##  Features

### Mobile (iOS & Android)
- Real-time **seismic event detection** using device accelerometer/gyroscope
- **Distress signal broadcasting** with one-tap SOS
- Background sensor monitoring with push notifications
- Offline-capable with local event logging
- Live map showing nearby distress signals

###  Hardware (ESP32)
- Environmental sensor readings (vibration, pressure)
- Autonomous distress detection without smartphone
- MQTT publish for remote/network-limited areas
- Battery-optimized deep sleep cycles

###  Web Dashboard (Rescue Centers)
- Real-time map of all active distress signals
- Incident timeline and event log
- Signal strength and device battery indicators
- Multi-center coordination view

###  Backend
- Firebase Realtime Database for low-latency sync
- MQTT broker simulating satellite relay
- REST API for dashboard and device communication
- Scalable Node.js server

---

##  Tech Stack

| Layer | Technology |
|---|---|
| iOS Client | Swift, SwiftUI, CoreMotion, MapKit |
| Android Client | Kotlin, Jetpack Compose, Sensor API |
| Hardware Node | ESP32, Arduino C, MQTT |
| Backend | Node.js, Express |
| Database | Firebase Realtime Database |
| Messaging | MQTT (Eclipse Mosquitto) |
| Dashboard | HTML, CSS, JavaScript, Leaflet.js |

---

##  Repository Structure

```
SeismoSync/
├── ios_app/          # Native iOS app (Swift + SwiftUI)
├── mobile_app/       # Native Android app (Kotlin)
├── server/           # Node.js backend + MQTT broker
├── dashboard/        # Web dashboard for rescue centers
└── package.json      # Root dependencies
```

---

## Getting Started

### Prerequisites
- Xcode 15+ (for iOS)
- Android Studio Hedgehog+ (for Android)
- Node.js 18+
- Firebase account
- MQTT Broker (local or cloud)

### iOS Setup
```bash
cd ios_app
open SeismoSync.xcodeproj
# Configure Firebase in GoogleService-Info.plist
# Build and run on simulator or device
```

### Android Setup
```bash
cd mobile_app
# Open in Android Studio
# Configure Firebase in google-services.json
# Build and run
```

### Backend Setup
```bash
cd server
npm install
cp .env.example .env
# Configure Firebase credentials and MQTT broker URL
npm start
```

### Dashboard Setup
```bash
cd dashboard
# Open index.html in browser
# Or serve with any static server
npx serve .
```

---

##  How It Works

1. **Detection** — Mobile app or ESP32 node continuously monitors sensor data for seismic patterns or manual SOS triggers
2. **Transmission** — Alert is published to MQTT broker (simulating satellite relay for remote areas)
3. **Sync** — Backend subscribes to MQTT, writes event to Firebase Realtime Database
4. **Notification** — All connected mobile clients receive push notification with event location
5. **Coordination** — Web dashboard at rescue centers updates in real-time showing event location, severity, and device details

---


##  Recognition

-  **Seismo Hack 1.0 (2025)** — 14th position out of 300+ teams, National Level Disaster Management Hackathon

---

##  License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

> *Built with the belief that technology should save lives, not just simplify them.*
