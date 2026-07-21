# Earbuds EQ

A software equalizer for Android aimed at fixing the typical "cheap TWS earbud" sound —
recessed mids, harsh treble spike around 6–9kHz, boomy uncontrolled bass, narrow soundstage.

## How it works (important — read this first)

Cheap Chinese TWS earbuds almost never expose a Bluetooth control API for changing their
internal EQ. There's no universal "protocol" to hook into across brands/models. So this app
takes the approach that actually works regardless of earbud brand: it reshapes the audio
**on the phone**, before it's sent over Bluetooth, using Android's built-in `Equalizer`,
`BassBoost`, and `Virtualizer` audio effects.

Two paths are implemented:

1. **App-opt-in hook (some apps only).** The app listens for
   `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, a broadcast some third-party players
   (PowerAmp, some local/offline players) send when they start playback. Spotify/YouTube
   Music don't send this.
2. **Global Mix EQ (root only).** Attaches the equalizer directly to audio session 0 —
   Android's global output mix — reshaping everything the phone outputs regardless of which
   app is playing it. Lower latency, no consent popup, and not affected by apps that block
   capture. See below.

### If your device is rooted

Root makes setup trivial and unlocks a better mode:

- **One-tap permission grant.** In the "Root Access" card, tap **Grant Capture Permission
  (Root)**. This runs `pm grant <package> android.permission.CAPTURE_AUDIO_OUTPUT` via
  `su` directly.
- **Global Mix EQ toggle.** Flip "Enable Global Mix EQ" to attach the equalizer straight to
  the system's output mix (audio session 0). Advantages: no consent screen, lower latency,
  and it isn't affected by apps that set `ALLOW_CAPTURE_BY_NONE` on their audio (that flag
  only blocks *capture*, not insert effects on the shared mix). The catch: whether session-0
  effects actually reach Bluetooth output depends on the device's audio HAL — reliable on
  many AOSP-close builds, inconsistent on some heavily customized OEM skins (not uncommon on
  budget/regional Chinese phone firmware, somewhat ironically).

### If your device isn't rooted

Without root, this app can only shape audio from apps that opt into the
`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast (path #1 above). Most streaming apps
(Spotify, YouTube Music) don't send this broadcast and can only be equalized with their own
in-app EQ, if they have one.

## Presets included

| Preset | What it does |
|---|---|
| Flat / Reference | No coloration |
| Bass Boost (V-Shape) | Punchy bass, scooped mids, crisp highs — EDM/hip-hop/pop |
| Warm | Smooths cheap-driver harshness |
| Bright / Detail | Pulls detail out of muddy mids |
| Vocal Forward | Pushes 1–4kHz so vocals/dialogue cut through |
| Podcast / Speech | Tight bass, boosted clarity, tamed sibilance |
| De-Harsh | Cuts the 6–9kHz spike common on sub-$20 drivers |

All presets are defined as a 5-point reference curve (60Hz / 230Hz / 910Hz / 3.6kHz / 14kHz)
and interpolated in log-frequency space onto whatever bands your specific phone's Equalizer
effect actually exposes (`EqEngine.kt` / `BandMapper.kt`) — band count and frequencies vary
by device/chipset.

You can also hand-tune any preset with the per-band sliders, which write directly to the
live `Equalizer`.

## Project structure

```
app/src/main/java/com/example/earbudseq/
  MainActivity.kt          — UI: preset chips, band sliders, root controls
  PrefsStore.kt             — persists chosen preset + whether Global Mix should auto-resume
  audio/
    EqEngine.kt              — wraps Equalizer/BassBoost/Virtualizer for one audio session
    SoundSignatures.kt       — preset gain curve definitions
    BandMapper.kt            — maps 5-band reference curve onto device's real bands
    AudioSessionReceiver.kt  — optional hook for opted-in third-party apps
  system/
    RootManager.kt            — runs su commands: grants CAPTURE_AUDIO_OUTPUT, restarts audioserver
    GlobalMixService.kt       — root-only: EQ attached directly to audio session 0
    BootReceiver.kt           — restarts Global Mix EQ after reboot, if it was enabled
```

## Building it

This is a standard Gradle Android project.

1. Open the `EarbudsEQ/` folder in Android Studio (Koala or newer recommended).
2. Let Gradle sync — it will pull `com.android.application` and Kotlin plugins.
3. Run on a device or emulator with `minSdk 24` (Android 7.0) or higher.
4. Connect Bluetooth earbuds, pick a preset, and (on a rooted device) enable Global Mix EQ.

No API keys, no earbud pairing/protocol setup needed — it works with whatever earbuds are
currently connected as the system's Bluetooth audio output, cheap or otherwise.

## Staying alive in the background

Global Mix EQ runs as a foreground service with a persistent notification, explicitly
returns `START_STICKY`, and doesn't stop itself when the app is swiped from recents. On
stock/near-stock Android that's enough.

One thing that helps further, surfaced in the app's "Background & Reliability" card:

- **Battery optimization exemption.** Chinese OEM skins (MIUI, ColorOS, EMUI, and similar)
  are notorious for killing background processes regardless of foreground-service status.
  The app has a one-tap button to open the system's "ignore battery optimizations" prompt
  for itself — accept it there.

Global Mix EQ also auto-restarts after a reboot if it was enabled, via a `BOOT_COMPLETED`
receiver — no user interaction needed, since that mode requires no consent screen.

## Permissions

- `MODIFY_AUDIO_SETTINGS` — required to attach AudioEffects
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*` — keeps the EQ alive while backgrounded
- `CAPTURE_AUDIO_OUTPUT` — needed for Global Mix EQ to fully replace (not just add to) the
  system output; granted only via root at runtime, not by a normal install-time prompt
- `RECEIVE_BOOT_COMPLETED` — lets Global Mix EQ (root mode) auto-restart after a reboot
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — for the one-tap exemption button
