# Class Jam 🎵📱

> **Local-Network Synchronized Audio & Party Games for Android**  
> Connect a group of friends' phones to one admin phone with zero internet, zero cloud servers, and zero accounts. Play music in microsecond sync across all phone speakers, pass DJ privileges around, and play interactive party games!

---

## 📋 Table of Contents
1. [Overview & Core Architecture](#overview--core-architecture)
2. [Tech Stack](#tech-stack)
3. [Network Modes](#network-modes)
4. [Clock Sync & Audio Engine](#clock-sync--audio-engine)
5. [Music Distribution & HTTP Range Streaming](#music-distribution--http-range-streaming)
6. [Party Games Engine](#party-games-engine)
7. [Testing Guides](#testing-guides)
   - [Testing on 2 Phones](#testing-on-2-phones)
   - [Testing on 5 Phones](#testing-on-5-phones)
   - [Testing on 10 Phones (Stress Test)](#testing-on-10-phones-stress-test)
8. [Unit Test Suite](#unit-test-suite)
9. [Known Limitations & Android Platform Notes](#known-limitations--android-platform-notes)

---

## 🌟 Overview & Core Architecture

Class Jam turns one Android phone into an entire local backend:
- **No Internet Required:** Runs 100% locally on direct Wi-Fi hotspots or a local classroom router.
- **One-Scan QR Join:** Clients scan the host's QR code; the app programmatically joins the Wi-Fi using `WifiNetworkSpecifier`, binds the process, establishes a WebSocket, and completes the handshake without typing passwords or IPs.
- **Microsecond Synchronization:** Uses monotonic nanosecond time (`SystemClock.elapsedRealtimeNanos`), top-5 lowest RTT clock offset filtering, and future-timestamped playback (`PLAY_AT +2.0s`).
- **Autonomous Drift Correction:** A continuous feedback loop monitors drift every 2 seconds. Small drifts (20–200ms) are corrected using smooth speed nudges ($1.0 \pm 0.02$); severe desyncs (>200ms) trigger hard seeks.
- **Acoustic Calibration:** Procedurally generated metronome click track allows users to calibrate Bluetooth speakers and slow hardware audio paths by ear down to millisecond precision.
- **Party Games:** Musical Chairs, Hot Potato, and Buzzer games with host-corrected timestamp decision logic to protect against Wi-Fi packet jitter.

---

## 🛠 Tech Stack

| Component | Technology | Role |
|---|---|---|
| **Language** | Kotlin 1.9.24 / 2.0 | Type-safe asynchronous coroutines |
| **Minimum SDK** | `minSdk 29` (Android 10) | Support for modern `WifiNetworkSpecifier` & scoped storage |
| **Target SDK** | `targetSdk 35` (Android 15) | Latest platform foreground service & audio policies |
| **UI Framework** | Jetpack Compose + Material 3 | Dark party theme with large accessible touch targets |
| **Audio Engine** | AndroidX Media3 ExoPlayer 1.3.1 | Pre-buffering, precision seek, and variable speed playback |
| **Local Server** | Ktor Server 2.3.11 (CIO Engine) | Self-contained HTTP Range audio server & WebSocket hub |
| **Local Client** | Ktor Client 2.3.11 (CIO Engine) | Resumable chunked downloader & WebSocket connection |
| **Serialization** | `kotlinx.serialization.json` | Type-safe protocol envelopes |
| **QR Engine** | ZXing Core 3.5.3 (Generate) + ML Kit / CameraX (Scan) | High-contrast QR rendering & instant one-shot camera scanning |
| **Preferences** | Jetpack DataStore Preferences | Persistent device ID, settings, and calibrated offsets |
| **Background Locks** | `WifiLock` (HIGH_PERF) + `WakeLock` (PARTIAL) | Prevents sleep/throttling during screen-off playback |

---

## 🌐 Network Modes

The host chooses one of three networking modes in settings:

### Mode A: In-App Hotspot (`LocalOnlyHotspot`)
- The host phone programmatically calls `WifiManager.startLocalOnlyHotspot()`.
- The generated SSID and WPA2 passphrase are read programmatically and embedded into the QR payload.
- Clients scan the QR and connect automatically using `WifiNetworkSpecifier` and `bindProcessToNetwork()`.

### Mode B: Manual Hotspot or Wi-Fi Router
- The host configures their personal hotspot or classroom router SSID and password once in Settings.
- The QR payload carries the credentials for auto-join.
- Ideal for large classes (10–30 phones) connected to a dedicated Wi-Fi router.

### Mode C: Same Wi-Fi Already
- If all devices are already on the same school or home Wi-Fi network, the QR carries only the host's LAN IP, port, token, and room code.
- LAN IP is detected dynamically by inspecting active interfaces (`wlan0`, `ap0`, `softap0`), avoiding loopback and never hardcoding `192.168.43.1`.

---

## ⏱ Clock Sync & Audio Engine

```
[Phone B (Client)]                                   [Phone A (Host)]
        |                                                   |
        |--- PING (t0: clientSendNs) ---------------------->| (Records t1: serverReceiveNs)
        |<-- PONG (t0, t1, serverTransmitNs) ---------------| (t2: clientReceiveNs)
        |                                                   |
        | RTT = t2 - t0                                     |
        | Offset = t1 - (t0 + t2)/2                         |
        | (Collects 20 samples; takes median of best 5)     |
        |                                                   |
        |                                                   |
        |<-- PLAY_AT (trackId, hostStartTimeNs: now + 2s)---|
        |                                                   |
        | Pre-buffers ExoPlayer                             | Pre-buffers ExoPlayer
        | Calculates localTargetNs = hostStartTime - Offset | Calculates localTargetNs = hostStartTime
        | Spin-waits last 10ms                              | Spin-waits last 10ms
        |                                                   |
        |== [PLAY AT EXACT SAME MICROSECOND] ===============|== [PLAY AT EXACT SAME MICROSECOND]
```

### Autonomous Drift Controller
Every 2 seconds during playback, the client calculates:
$$\text{Drift} = \text{Actual Position} - \text{Expected Host Position}$$

| Drift Range | Action Taken | Rationale |
|---|---|---|
| $|\text{Drift}| < 20\text{ ms}$ | **In-Sync (Ignore)** | Human ear cannot detect $<20\text{ms}$ delay. Prevents audible pitch artifacts. |
| $20\text{ ms} \le |\text{Drift}| \le 200\text{ ms}$ | **Speed Nudge ($1.0 \pm 0.02$)** | Smoothly speeds up to `1.02f` or slows down to `0.98f` until drift returns to $<20\text{ms}$. |
| $|\text{Drift}| > 200\text{ ms}$ | **Hard Seek** | Instantly seeks player to `expectedPosition` and restores `1.0f` speed. |

---

## 🎶 Music Distribution & HTTP Range Streaming

1. **Host Indexing:** The host indexes audio from Android `MediaStore`. A background thread calculates SHA-256 hashes in streaming buffers.
2. **HTTP Range Server:** The host exposes `GET /track/{id}` with `Accept-Ranges: bytes` and `206 Partial Content`.
3. **Resumable Chunked Download:** Clients download into `cacheDir/tracks/{id}.tmp`. If interrupted, downloads resume using `Range: bytes={existing}-`.
4. **Integrity Check:** Completed files are validated against the host's SHA-256 hash before renaming to `{id}.audio`.
5. **Queue Prefetch:** Clients prefetch the currently active song plus the next 2 queued songs (maximum 3 concurrent downloads per client via `Semaphore(3)`).
6. **Playback Gating:** `PLAY_AT` fires only when all phones in the active output group report `READY`. The host can toggle **"Start with ready phones only"** to bypass slow devices.
7. **Cache Lifecycle:** On leaving, cached songs are automatically deleted unless the user enabled **"Keep downloaded songs"** in Settings.

---

## 🎮 Party Games Engine

All party games implement the modular `Game` interface (`onStart`, `onMessage`, `onTick`, `onEnd`):

### 1. Musical Chairs
- Music plays across all phones. The host randomly cuts the music after 8–20 seconds.
- Players tap **SIT!**.
- **Timestamp Skew Protection:** Each tap is tagged with the client's host-corrected monotonic timestamp:
  $$t_{\text{corrected}} = t_{\text{local}} + \text{offset}$$
  Even if network packet delivery is delayed by Wi-Fi congestion, the player with the earliest physical tap wins (+10 pts); the slowest loses (-5 pts).

### 2. Hot Potato
- Host starts a hidden countdown (12–25 seconds).
- One phone receives the fiery potato card and must tap another player's name to pass it.
- When the timer explodes: **BOOM!** The current holder loses -10 pts; all other players gain +5 pts.

### 3. Buzzer / Guess the Song
- The DJ plays a song snippet. Players buzz in on a giant gold buzzer.
- The first host-corrected tap locks out the room and awards guessing rights.

### 4. Free DJ
- Casual mode: pass music control from friend to friend without timers or elimination.

---

## 🧪 Testing Guides

### Testing on 2 Phones (Host + 1 Client)
1. **Phone A (Host):** Launch Class Jam $\to$ Select **In-App Hotspot** $\to$ Tap **Start Session as Admin**.
2. **Phone B (Client):** Tap **Join Session (Scan QR)** $\to$ Point camera at Phone A's screen.
3. Confirm Phone B auto-connects to Phone A's hotspot and displays `ROLE: PLAYER`.
4. On Phone A, open **Music Library** $\to$ Select a song $\to$ Tap **Play** in Now Playing.
5. Listen closely to both phones. Audio will begin simultaneously at the 2-second mark with zero echo.
6. On Phone A, tap Phone B's tile in "Friends & Roles" and tap **Grant DJ**. Confirm Phone B's screen announces `"YOU ARE THE DJ!"` with music control access.

### Testing on 5 Phones (Host + DJ + 3 Players)
1. Set up Phone A as Host.
2. Join Phones B, C, D, and E via QR scan.
3. On Phone A, promote Phone B to `DJ`.
4. On Phone B (DJ), browse the host's library and queue 3 songs.
5. Verify on Phone A's lobby that all phones show `Downloading (progress %)` then `READY`.
6. Start playback. Walk around the room—all 5 speakers will play as one unified acoustic system.
7. Open **Party Games Hub** on Phone A $\to$ Tap **Start Musical Chairs**.
8. Verify that all 4 players see the game screen and compete for the fastest tap when music stops.

### Testing on 10 Phones (Stress Test)
1. **Network Recommendation:** Use **Mode B (Manual Hotspot / External Wi-Fi Router)** for maximum Wi-Fi bandwidth.
2. Connect 10 phones to the room.
3. In Host **Settings**, verify `Max Connected Clients` is set to 10. Attempting to connect an 11th phone should display the error: `"Room is at full capacity (10 clients max)"`.
4. Queue a song. Notice that each client downloads at up to 3 concurrent streams, respecting bandwidth.
5. If one phone has a slow connection, test the Admin override: toggle ON **"Start with ready phones only"** in the Queue screen.

---

## 🔬 Unit Test Suite

The app includes 7 comprehensive JUnit test suites:

| Test File | Verification Scope |
|---|---|
| [ClockSyncEngineTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/sync/ClockSyncEngineTest.kt) | 20-sample burst offset calculation, median of best 5 lowest RTTs, outlier/lag spike rejection, and negative offsets. |
| [DriftControllerTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/sync/DriftControllerTest.kt) | Deadband $<20\text{ms}$ (InSync), speed nudge $1.0 \pm 0.02$ for $20\text{--}200\text{ms}$, and hard seeks for $>200\text{ms}$. |
| [ProtocolSerializationTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/network/protocol/ProtocolSerializationTest.kt) | Full serialization of `QrPayload`, `Envelope`, `JoinPayload`, `WelcomePayload`, `DeviceListPayload`, and `Ping/Pong`. |
| [RolePermissionTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/core/RolePermissionTest.kt) | Permissions matrix for `ADMIN`, `DJ`, and `PLAYER`. |
| [RoleManagementTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/network/server/RoleManagementTest.kt) | Single DJ invariant (granting new DJ demotes previous), DJ revocation, remote mute, output group toggle, and kick. |
| [TrackDistributionLogicTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/network/server/TrackDistributionLogicTest.kt) | Ready state gating, output group filtering, and "Start with ready phones only" override. |
| [GameLogicDecisionTest.kt](file:///d:/CODING%20REALTED/CLG%20RELATED%20CONTENT/M'Sync/app/src/test/java/com/classjam/games/GameLogicDecisionTest.kt) | Musical chairs winner decision with skewed packet arrivals (faster physical tap wins regardless of network delay). |

To run the unit tests:
```bash
./gradlew testDebugUnitTest
```

---

## ⚠️ Known Limitations & Android Platform Notes

1. **Android 10+ Wi-Fi Restrictions:** Starting in Android 10, apps cannot toggle device Wi-Fi state silently. Class Jam uses the sanctioned `WifiNetworkSpecifier` API, which shows a system prompt on the client phone to confirm connecting to the host's hotspot.
2. **Bluetooth Latency:** Bluetooth A2DP audio connections typically introduce 100ms–250ms of audio buffer latency. Always use **Calibration Mode** to dial in a manual offset (e.g. `+140ms`) when using Bluetooth speakers.
3. **Aggressive Battery Savers:** Certain vendor ROMs (MIUI, OneUI) throttle network sockets when the screen is locked. Use the in-app **"Enable Screen-Off Sync"** button on the home screen to exempt Class Jam from battery optimization.
4. **DRM & Streaming:** Class Jam is strictly designed for DRM-free local audio files (MP3, AAC, FLAC, WAV, M4A) stored on the host phone. Cloud streaming services (Spotify, Apple Music) are DRM-protected and out of scope.
