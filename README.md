# NUR Home — ESP32-S3 4-Channel Relay Home Automation App

<p align="center">
  <img src="public/assets/aistudio/NUR_Home_Smart_Living_Logo_32767e3b.png" width="320" alt="NUR Home Logo" />
</p>

A sleek, minimalist Android smart home automation application built with **Jetpack Compose**, **Kotlin**, and **Firebase Realtime Database** to control a 4-channel ESP32-S3 relay board with sub-second real-time synchronization.

---

## ⚡ System Architecture: How It Works

The architecture maintains continuous two-way synchronization between physical wall switches, the ESP32-S3 microcontroller, Firebase Realtime Database, and the NUR Home mobile app.

### Flow 1: Mobile App Control
```
[User App Tap]
      ↓
[Firebase Write: /devices/relayN = true/false]
      ↓
[Firebase Realtime Database WebSocket]
      ↓
[ESP32 Stream Listener receives event in <100ms]
      ↓
[ESP32 GPIO triggers Relay Coil Click]
      ↓
[Physical Light / Appliance Powers ON/OFF]
```

### Flow 2: Physical Wall Switch
```
[User flips physical Wall Switch on ESP32 board]
      ↓
[ESP32 firmware debounces input and triggers GPIO]
      ↓
[ESP32 writes updated boolean to Firebase /devices/relayN]
      ↓
[Firebase Realtime Database emits stream event]
      ↓
[NUR Home ValueEventListener receives update (~2s polling cycle)]
      ↓
[App UI switch & glowing card update automatically]
```

---

## 📂 Exact Firebase Realtime Database Schema

```json
{
  "devices": {
    "relay1": true,
    "relay2": false,
    "relay3": false,
    "relay4": false
  }
}
```

> **CRITICAL FIRMWARE RULE**:
> - `true` = Relay ON, `false` = Relay OFF.
> - **Every value is strictly boolean.**
> - Extra keys are never written to `/devices` to preserve ESP32 firmware compatibility. All custom relay names, rooms, icons, and activity history are stored securely on the device using Jetpack DataStore.

---

## 🛡️ Suggested Firebase Security Rules

Deploy the following rules in your Firebase Console under **Realtime Database > Rules**:

```json
{
  "rules": {
    "devices": {
      ".read": "auth != null",
      ".write": "auth != null",
      "relay1": { ".validate": "newData.isBoolean()" },
      "relay2": { ".validate": "newData.isBoolean()" },
      "relay3": { ".validate": "newData.isBoolean()" },
      "relay4": { ".validate": "newData.isBoolean()" }
    }
  }
}
```

---

## 🚀 Setup & Installation Instructions

### 1. Enable Anonymous Authentication in Firebase Console
1. Go to your [Firebase Console](https://console.firebase.google.com/).
2. Select your project (`abrob-home-automation-2b56b`).
3. In the left navigation bar, open **Build > Authentication**.
4. Click the **Sign-in method** tab.
5. Enable the **Anonymous** sign-in provider and click **Save**.

### 2. Configure `google-services.json`
The application already includes pre-configured project credentials in `app/google-services.json`:
- **Database URL**: `https://abrob-home-automation-2b56b-default-rtdb.firebaseio.com/`
- **Project ID**: `abrob-home-automation-2b56b`
- **API Key**: `AIzaSyAGqR2022Vwhk8Dp1XlQwrH2Sp48QMvo4I`

If you are using a new or custom Firebase project:
1. In Firebase Console, go to **Project Settings > General**.
2. Under **Your apps**, register an Android app with package name `com.aistudio.nurhome.esp32`.
3. Download `google-services.json` and replace `app/google-services.json`.

### 3. Build & Run the App
- In Android Studio or terminal:
  ```bash
  gradle :app:assembleDebug
  ```
- Or run local tests:
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## ✨ Features Implemented

1. **Brand Identity**: Styled with the official NUR Home electric blue (`#0D62FE`) and sleek midnight palette, custom adaptive launcher icon, and high-contrast glowing active states.
2. **Anonymous Authentication**: Automatic silent sign-in on first launch, session persistence, UID display, and manual re-authentication capability.
3. **4-Channel Dashboard**:
   - Relay 1 (`/devices/relay1`): Light 1 / Living Room
   - Relay 2 (`/devices/relay2`): Light 2 / Master Bedroom
   - Relay 3 (`/devices/relay3`): Light 3 / Kitchen
   - Relay 4 (`/devices/relay4`): Light 4 / Balcony
4. **Optimistic UI with Instant Feedback**: Switches toggle immediately, displaying a brief syncing spinner while the write is in flight, and gracefully reconciling or reverting if a network error occurs.
5. **Atomic Master Controls**:
   - **ALL ON** button writes `true` to all 4 relays atomically via `updateChildren`.
   - **ALL OFF** button writes `false` to all 4 relays atomically.
   - Quick scene presets: *Evening Lounge* and *Night Safety*.
6. **Live Connection Banner**:
   - Real-time indicator based on Firebase `.info/connected`.
   - Last updated timestamp and ESP32 polling sync cycle indicator.
7. **Local Customizations**:
   - Custom relay names, room tags, and icon selection (persisted via DataStore without mutating the Firebase schema).
   - Auto-off countdown timers (5m, 15m, 30m, 1h, 2h).
   - Activity history log tracking app taps vs physical wall switch toggles.
   - Dark / Light / System appearance mode toggle.
   - Interactive "System Architecture" visual guide modal in the top bar.
