# Earbuds EQ

A software equalizer for Android aimed at fixing the typical "cheap TWS earbud" sound —
recessed mids, harsh treble spike around 6–9kHz, boomy uncontrolled bass, narrow soundstage.

## How it works (important — read this first)

Cheap Chinese TWS earbuds almost never expose a Bluetooth control API for changing their
internal EQ. There's no universal "protocol" to hook into across brands/models. So this app
takes the approach that actually works regardless of earbud brand: it reshapes the audio
**on the phone**, before it's sent over Bluetooth, using Android's built-in `Equalizer`,
`BassBoost`, and `Virtualizer` audio effects.

Four paths are implemented:

1. **Built-in player (always works).** Load a local audio file in the app and it plays
   through a `MediaPlayer` with the EQ attached directly to that playback session. This is
   the reliable, guaranteed-to-work path — use it to A/B the presets.
2. **App-opt-in hook (some apps only).** The app listens for
   `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, a broadcast some third-party players
   (PowerAmp, some local/offline players) send when they start playback. Spotify/YouTube
   Music don't send this.
3. **System-wide capture (all apps, with caveats) — Shizuku or root.** Captures the
   device's whole audio mix, runs it through the equalizer, and re-outputs it.
4. **Global Mix EQ (root only).** Attaches the equalizer directly to audio session 0 —
   Android's global output mix — instead of capturing/re-outputting. Lower latency, no
   consent popup, and not affected by apps that block capture. See below.

### If your device is rooted

Root makes setup trivial and unlocks a better mode:

- **One-tap permission grant.** In the "Root Access" card, tap **Grant Capture Permission
  (Root)**. This runs `pm grant <package> android.permission.CAPTURE_AUDIO_OUTPUT` via
  `su` directly — no Shizuku app, no wireless debugging pairing needed. This unlocks the
  same System-Wide EQ (capture-based) mode described below, instantly.
- **Global Mix EQ toggle.** Flip "Use Global Mix EQ instead" to skip capture entirely —
  the equalizer attaches straight to the system's output mix (audio session 0). Advantages:
  no MediaProjection consent screen (ever), lower latency, and it isn't affected by apps
  that set `ALLOW_CAPTURE_BY_NONE` on their audio (that flag only blocks *capture*, not
  insert effects on the shared mix). The catch: whether session-0 effects actually reach
  Bluetooth output depends on the device's audio HAL — reliable on many AOSP-close builds,
  inconsistent on some heavily customized OEM skins (not uncommon on budget/regional
  Chinese phone firmware, somewhat ironically). If you flip it on and don't hear a
  difference, flip it back off and use System-Wide EQ (capture-based) instead — that one
  is capture-based and works reliably across HALs since it operates on raw PCM you already
  have in hand, at the cost of the tradeoffs below.
- These two modes are mutually exclusive (enabling one disables the other) since running
  both at once would double-process the audio.

### If your device isn't rooted (Shizuku path)

Android normally forbids one app from silently replacing another app's audio — without a
special permission, "capturing" another app's playback just plays a second copy on top
(echo), it doesn't redirect it. `CAPTURE_AUDIO_OUTPUT` is the permission that fixes this,
but it's locked to system apps. Shizuku gets around that by giving this app temporary
shell-level (adb) privilege — shell is specially allowed to grant that permission — without
needing full root.

**One-time setup, entirely on your phone:**

1. Install **Shizuku** from the Play Store (or F-Droid/GitHub if you prefer).
2. On your phone: **Settings → Developer options → Wireless debugging** → turn it on.
   (If Developer options isn't visible: **Settings → About phone** → tap "Build number" 7
   times.)
3. In Wireless debugging, tap **"Pair device with pairing code"** — it'll show a code and
   a port.
4. Open the **Shizuku** app → tap **"Start via Wireless debugging"** → it'll walk you
   through entering that pairing code once. After the first pairing, Shizuku can be
   restarted from its own app without repeating the pairing step (though it stops on
   reboot and needs a quick restart from the Shizuku app).
5. Open **Earbuds EQ** → in the "System-Wide EQ" section, tap **Set up**. It'll ask Shizuku
   to grant this app permission (approve the prompt), then request the actual audio-capture
   permission. When the status line says "Ready", tap **Enable System-Wide EQ**.
6. Android will show a screen/audio-capture consent dialog (the same one used for screen
   recording — audio capture reuses this API) — tap **Start now**. This confirmation is
   required by Android and will reappear each time you re-enable capture after a restart.

**Known limitations, to set expectations honestly:**

- **Some apps block capture.** Apps can mark their audio `ALLOW_CAPTURE_BY_NONE`, which
  excludes them from any capture, including this. Spotify has done this historically for
  licensing reasons — whether it currently does depends on their build. YouTube, browsers,
  games, and most local players typically don't block it.
- **Shizuku needs restarting after phone reboot.** It's not root — the elevated access
  doesn't persist across reboots the way root does. Reopen the Shizuku app and tap start
  again (no need to re-pair).
- **Small added latency.** Audio goes capture → process → re-output, which adds a few
  tens of milliseconds. Not noticeable for music; could be mildly noticeable for
  fast-paced gaming.
- If none of this appeals, path #1 (built-in player) has zero setup and zero caveats —
  it's the tradeoff of convenience vs. coverage.

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
  MainActivity.kt          — UI: preset chips, band sliders, file picker, playback controls
  PrefsStore.kt             — persists chosen preset + whether Global Mix should auto-resume
  audio/
    EqEngine.kt              — wraps Equalizer/BassBoost/Virtualizer for one audio session
    SoundSignatures.kt       — preset gain curve definitions
    BandMapper.kt            — maps 5-band reference curve onto device's real bands
    EqService.kt             — foreground service hosting the built-in MediaPlayer + EQ
    AudioSessionReceiver.kt  — optional hook for opted-in third-party apps
  system/
    ShizukuManager.kt         — connects to Shizuku, grants CAPTURE_AUDIO_OUTPUT via shell
    RootManager.kt            — runs su commands: grants the same permission, restarts audioserver
    SystemAudioProcessor.kt   — capture -> EQ -> re-output loop (AudioRecord/AudioTrack)
    SystemCaptureService.kt   — foreground service owning the MediaProjection + processor
    GlobalMixService.kt       — root-only: EQ attached directly to audio session 0
    BootReceiver.kt           — restarts Global Mix EQ after reboot, if it was enabled
```

## Building it

This is a standard Gradle Android project.

1. Open the `EarbudsEQ/` folder in Android Studio (Koala or newer recommended).
2. Let Gradle sync — it will pull `com.android.application` and Kotlin plugins.
3. Run on a device or emulator with `minSdk 24` (Android 7.0) or higher.
4. Connect Bluetooth earbuds, pick a local audio file in the app, hit Play, and switch presets.

No API keys, no earbud pairing/protocol setup needed — it works with whatever earbuds are
currently connected as the system's Bluetooth audio output, cheap or otherwise.

## Staying alive in the background

All three EQ modes (built-in player, System-Wide capture, Global Mix) run as foreground
services with a persistent notification, explicitly return `START_STICKY`, and don't stop
themselves when the app is swiped from recents. On stock/near-stock Android that's enough.

Two things that help further, both surfaced in the app's "Background & Reliability" card:

- **Battery optimization exemption.** Chinese OEM skins (MIUI, ColorOS, EMUI, and similar)
  are notorious for killing background processes regardless of foreground-service status.
  The app has a one-tap button to open the system's "ignore battery optimizations" prompt
  for itself — accept it there.
- **Boot auto-restart (root only).** If Global Mix EQ was on when you rebooted, a
  `BOOT_COMPLETED` receiver restarts it automatically — no user interaction needed, since
  that mode requires no consent screen. The Shizuku/capture-based mode can't do this: Android
  requires a fresh MediaProjection consent tap each time for security, so after a reboot
  you'll need to reopen the app once and tap Enable again if you use that mode instead.

## Permissions

- `MODIFY_AUDIO_SETTINGS` — required to attach AudioEffects
- `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` — to load local files to test with
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*` — keeps playback + EQ alive while backgrounded
- `RECORD_AUDIO`, `CAPTURE_AUDIO_OUTPUT` — needed for system-wide capture; the latter is
  granted only via Shizuku or root at runtime, not by a normal install-time prompt
- `RECEIVE_BOOT_COMPLETED` — lets Global Mix EQ (root mode) auto-restart after a reboot
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — for the one-tap exemption button

## Shizuku troubleshooting

`ShizukuManager.grantCaptureAudioOutputPermission()` calls Shizuku's `newProcess` via
reflection, since that method's exact location has moved between Shizuku library versions.
If it builds against a `dev.rikka.shizuku:api` version where that reflection call fails at
runtime, replace it with a `Shizuku.UserService` AIDL binding per the official docs:
https://github.com/RikkaApps/Shizuku-API — the shell command to run either way is just
`pm grant <your.package.name> android.permission.CAPTURE_AUDIO_OUTPUT`.
