# Earbuds EQ

A root-powered system-wide equalizer for Android aimed at fixing the typical "cheap TWS
earbud" sound — recessed mids, harsh treble spike around 6–9kHz, boomy uncontrolled bass,
narrow soundstage.

## How it works

This app requires **root** (Magisk or otherwise). It attaches Android's `Equalizer`,
`BassBoost`, `Virtualizer`, and `LoudnessEnhancer` audio effects directly to audio session
0 — the system's global output mix — so the EQ applies to all app audio before it reaches
your Bluetooth earbuds, not just this app's own playback.

Why root: regular (non-privileged) apps can technically try to attach effects to session 0,
but there's no guarantee the device's audio HAL actually routes that into real hardware
output — results are inconsistent across OEMs. This app doesn't try to work around that;
it simply requires root and treats "no root" as unsupported.

**Known limitation:** whether session-0 effects reach Bluetooth output depends on the
device's audio HAL/OEM audio policy — reliable on many AOSP-close devices, inconsistent on
some heavily customized ones (not uncommon on budget/regional Chinese phone firmware). If
enabling the equalizer doesn't audibly change anything, that's this device-dependent
ceiling, not a bug to chase further in software.

## The UI

- **Equalizer card** — a curve graph showing your device's actual EQ bands (count and
  frequencies vary by device/chipset, typically 5). Drag any point to adjust that band by
  hand; the preset label switches to "Custom" automatically. Tap **Presets** to jump to a
  named curve instead.
- **Bass Boost / Loudness / Virtualizer dials** — drag around the ring to set 0–100%, each
  with its own on/off switch. Loudness maps to Android's real `LoudnessEnhancer` effect.
- **Volume slider** — controls the system media volume directly.
- **Master switch** (next to the small status dot) — turns the whole system-wide effect
  chain on/off. Disabled until root is detected.

## Presets

| Preset | What it does |
|---|---|
| Flat / Reference | No coloration |
| Bass Boost (V-Shape) | Punchy bass, scooped mids, crisp highs — EDM/hip-hop/pop |
| Warm | Smooths cheap-driver harshness |
| Bright / Detail | Pulls detail out of muddy mids |
| Vocal Forward | Pushes 1–4kHz so vocals/dialogue cut through |
| Podcast / Speech | Tight bass, boosted clarity, tamed sibilance |
| De-Harsh | Cuts the 6–9kHz spike common on sub-$20 drivers |

Presets are defined as a 5-point reference curve (60Hz/230Hz/910Hz/3.6kHz/14kHz) and
interpolated in log-frequency space onto whatever bands your phone's Equalizer effect
actually exposes (`BandMapper.kt`).

## Staying alive in the background

- Runs as a foreground service with a persistent notification (`START_STICKY`, doesn't
  stop itself when swiped from recents).
- **Boot auto-restart**: if the equalizer was on when you rebooted, a `BOOT_COMPLETED`
  receiver restarts it automatically — no user interaction needed, since this mode needs no
  consent screen.
- **Battery optimization exemption**: Chinese OEM skins (MIUI, ColorOS, EMUI, and similar)
  are notorious for killing background processes regardless of foreground-service status.
  One-tap button in the "Background & Reliability" card opens the system exemption prompt.

## Project structure

```
app/src/main/java/com/example/earbudseq/
  MainActivity.kt            — wires up the curve view, dials, volume slider, root/service lifecycle
  PrefsStore.kt               — persists preset/custom curve, per-effect dial state, volume
  ui/
    EqualizerCurveView.kt      — custom-drawn draggable EQ curve (gridlines, gradient fill, points)
    CircularDialView.kt        — custom-drawn arc gauge used for Bass Boost/Loudness/Virtualizer
  audio/
    EqEngine.kt                — wraps Equalizer/BassBoost/Virtualizer/LoudnessEnhancer for one session
    SoundSignatures.kt         — preset gain curve definitions
    BandMapper.kt              — maps 5-band reference curve onto device's real bands
    AudioSessionReceiver.kt    — optional hook for third-party apps that opt into system EQ control
  system/
    RootManager.kt             — root detection + generic `su` shell execution
    GlobalMixService.kt        — the system-wide EQ: effects attached to audio session 0
    BootReceiver.kt            — restarts the equalizer after reboot, if it was enabled
```

## Building it

Standard Gradle Android project — open the `EarbudsEQ/` folder in Android Studio, let
Gradle sync, run on a rooted device or emulator (`minSdk 24`). A GitHub Actions workflow
(`.github/workflows/build-apk.yml`) is also included if you want a cloud-built APK without
Android Studio — push to a repo and download the artifact from the Actions tab.

## Permissions

- `MODIFY_AUDIO_SETTINGS` — required to attach AudioEffects
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*` — keeps the EQ alive while backgrounded
- `RECEIVE_BOOT_COMPLETED` — auto-restarts the EQ after a reboot
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — for the one-tap exemption button
