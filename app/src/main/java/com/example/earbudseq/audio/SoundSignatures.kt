package com.example.earbudseq.audio

/**
 * Preset gain curves, expressed in dB across a normalized 5-band layout
 * (roughly: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz). At apply time these are
 * mapped onto whatever bands the device's real Equalizer effect exposes.
 *
 * These curves are tuned specifically to counter the typical "cheap TWS"
 * sound: recessed mids, harsh/sibilant treble spike around 6-9kHz, boomy
 * uncontrolled bass, narrow soundstage.
 */
enum class SoundSignature(
    val displayName: String,
    val description: String,
    val bandGainsDb: FloatArray,
    val bassBoost: Short,   // 0-1000
    val virtualizer: Short  // 0-1000
) {
    FLAT(
        "Flat / Reference",
        "No coloration — closest to what the recording engineer heard.",
        floatArrayOf(0f, 0f, 0f, 0f, 0f),
        0, 0
    ),
    BASS_HEAD(
        "Bass Boost (V-Shape)",
        "Punchy low end, scooped mids, crisp highs. EDM, hip-hop, pop.",
        floatArrayOf(6f, 3f, -2f, 1f, 3f),
        500, 200
    ),
    WARM(
        "Warm",
        "Smooths out cheap-driver harshness. Rounder, more relaxed treble.",
        floatArrayOf(3f, 2f, 0f, -2f, -4f),
        200, 100
    ),
    BRIGHT(
        "Bright / Detail",
        "Pulls out detail buried by muddy cheap-earbud mids.",
        floatArrayOf(-2f, -1f, 2f, 4f, 5f),
        0, 300
    ),
    VOCAL(
        "Vocal Forward",
        "Pushes the 1-4kHz range so vocals and dialogue cut through.",
        floatArrayOf(-1f, 0f, 4f, 3f, 0f),
        100, 0
    ),
    PODCAST(
        "Podcast / Speech",
        "Tight low end, boosted speech clarity, tamed sibilance.",
        floatArrayOf(-4f, -1f, 5f, 3f, -3f),
        0, 0
    ),
    ANTI_SIBILANCE(
        "De-Harsh",
        "Cuts the 6-9kHz spike common on sub-$20 dynamic drivers.",
        floatArrayOf(1f, 1f, 0f, -5f, -3f),
        100, 100
    );

    companion object {
        /** Reference center frequencies (Hz) this preset table is designed around. */
        val referenceFrequencies = intArrayOf(60, 230, 910, 3600, 14000)
    }
}
