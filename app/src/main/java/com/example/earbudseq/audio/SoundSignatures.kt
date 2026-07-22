package com.example.earbudseq.audio

/**
 * Preset gain curves, expressed in dB across a normalized 5-band layout
 * (roughly: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz). At apply time these are
 * mapped onto whatever bands the device's real Equalizer effect exposes.
 *
 * Each preset also carries bassBoost (0-1000), virtualizer (0-1000), and
 * loudness (0-100 percent) so the dials and effect switches auto-configure
 * when a genre preset is selected.
 *
 * Curves are tuned for typical cheap TWS earbuds: recessed mids, harsh
 * treble spike around 6-9kHz, boomy uncontrolled bass, narrow soundstage.
 */
enum class SoundSignature(
    val displayName: String,
    val description: String,
    val bandGainsDb: FloatArray,
    val bassBoost: Short,    // 0-1000
    val virtualizer: Short,   // 0-1000
    val loudness: Int,        // 0-100 percent
    val bassEnabled: Boolean = true,
    val virtualizerEnabled: Boolean = true,
    val loudnessEnabled: Boolean = false
) {
    FLAT(
        "Flat / Reference",
        "No coloration — closest to what the recording engineer heard.",
        floatArrayOf(0f, 0f, 0f, 0f, 0f),
        0, 0, 0,
        bassEnabled = false, virtualizerEnabled = false, loudnessEnabled = false
    ),

    // ── Genre presets ────────────────────────────────────────────────

    ROCK(
        "Rock",
        "Classic rock curve — punchy bass, forward mids for guitars, smooth highs.",
        floatArrayOf(5f, 1f, 1f, 2f, 3f),
        400, 300, 20
    ),
    POP(
        "Pop",
        "Bright, vocal-forward, tight bass. Built for chart hits and radio mixes.",
        floatArrayOf(3f, 0f, 2f, 3f, 2f),
        300, 250, 15
    ),
    HIP_HOP(
        "Hip-Hop / R&B",
        "Deep sub-bass, warm mids for vocals, controlled treble.",
        floatArrayOf(7f, 2f, -1f, 1f, 2f),
        600, 200, 25
    ),
    EDM(
        "Electronic / EDM",
        "Heavy sub-bass, scooped mids, sparkling highs for festival drops.",
        floatArrayOf(7f, 3f, -2f, 1f, 4f),
        700, 400, 30
    ),
    JAZZ(
        "Jazz",
        "Warm, natural — upright bass, brushed snares, horn warmth without harshness.",
        floatArrayOf(3f, 2f, 1f, 0f, -1f),
        200, 300, 10,
        virtualizerEnabled = true
    ),
    CLASSICAL(
        "Classical",
        "Flat-ish with slight warmth. Wide soundstage, no artificial bass boost.",
        floatArrayOf(2f, 1f, 0f, 1f, 2f),
        100, 500, 5,
        bassEnabled = false
    ),
    METAL(
        "Metal",
        "Tight low end, aggressive mids for rhythm guitars, present highs for cymbals.",
        floatArrayOf(4f, 0f, 2f, 3f, 4f),
        300, 350, 15
    ),
    ACOUSTIC(
        "Acoustic",
        "Natural, detailed — guitar body, vocal intimacy, minimal coloration.",
        floatArrayOf(2f, 2f, 1f, 2f, 1f),
        150, 350, 10
    ),
    LATIN(
        "Latin / Reggaeton",
        "Punchy bass for dembow and reggaeton grooves, bright percussion.",
        floatArrayOf(6f, 2f, 0f, 2f, 3f),
        500, 300, 25
    ),
    COUNTRY(
        "Country",
        "Warm mids for vocals and fiddle, present highs for steel guitar.",
        floatArrayOf(3f, 2f, 2f, 1f, 2f),
        250, 250, 10
    ),
    FOLK(
        "Folk / Indie",
        "Intimate, natural — vocal and acoustic instrument clarity over bass weight.",
        floatArrayOf(2f, 2f, 2f, 1f, 0f),
        150, 300, 5
    ),
    AMBIENT(
        "Ambient / Chill",
        "Relaxed, wide, warm. Subtle bass, gentle highs, immersive soundstage.",
        floatArrayOf(3f, 1f, 0f, -1f, -1f),
        200, 600, 15
    ),
    TALK(
        "Podcast / Speech",
        "Tight low end, boosted speech clarity, tamed sibilance.",
        floatArrayOf(-4f, -1f, 5f, 3f, -3f),
        0, 0, 0,
        bassEnabled = false, virtualizerEnabled = false, loudnessEnabled = false
    ),

    // ── Tonal correction presets ─────────────────────────────────────

    BASS_HEAD(
        "Bass Boost (V-Shape)",
        "Maximum punchy low end, scooped mids, crisp highs.",
        floatArrayOf(6f, 3f, -2f, 1f, 3f),
        800, 200, 30
    ),
    WARM(
        "Warm",
        "Smooths out cheap-driver harshness. Rounder, more relaxed treble.",
        floatArrayOf(3f, 2f, 0f, -2f, -4f),
        200, 100, 10
    ),
    BRIGHT(
        "Bright / Detail",
        "Pulls out detail buried by muddy cheap-earbud mids.",
        floatArrayOf(-2f, -1f, 2f, 4f, 5f),
        0, 300, 0,
        bassEnabled = false
    ),
    VOCAL(
        "Vocal Forward",
        "Pushes the 1-4kHz range so vocals and dialogue cut through.",
        floatArrayOf(-1f, 0f, 4f, 3f, 0f),
        100, 0, 0,
        virtualizerEnabled = false
    ),
    ANTI_SIBILANCE(
        "De-Harsh",
        "Cuts the 6-9kHz spike common on sub-$20 dynamic drivers.",
        floatArrayOf(1f, 1f, 0f, -5f, -3f),
        100, 100, 0
    );

    companion object {
        /** Reference center frequencies (Hz) this preset table is designed around. */
        val referenceFrequencies = intArrayOf(60, 230, 910, 3600, 14000)
    }
}
