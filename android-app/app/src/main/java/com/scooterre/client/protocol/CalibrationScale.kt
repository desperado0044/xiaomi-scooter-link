package com.scooterre.client.protocol

import kotlin.math.abs

/**
 * Finds the scale factor between a raw on-wire FLOAT this app cannot yet interpret (a
 * [FamilyAProfiles] property, read already by the normal dashboard poll) and a reference value
 * the owner read off a source they already trust (Xiaomi Home, the scooter's own display).
 * Every scooter model confirmed so far in this app packs its FLOAT properties as value*100 (see
 * DashboardScreen's UNIT_SCALES and its RESEARCH_LOG reference) - 1 and 0.01 are therefore the
 * most likely candidates, with a few neighbours included in case a Family A model turns out to
 * follow a different convention. Never guesses outside this fixed candidate list: an unexplained
 * ratio is reported as "unclear" rather than silently assumed to be a new scale.
 */
object CalibrationScale {
    val CANDIDATES = listOf(1.0, 0.01, 0.1, 0.001, 10.0, 100.0, 1000.0)
    private const val TOLERANCE = 0.03

    data class Guess(val scale: Double?, val ratio: Double)

    /** [raw] is the value as the scooter sent it; [reference] is what the owner says their trusted
     * source shows for the same reading right now. Returns the best-matching candidate scale, or
     * null (with the raw ratio still reported) if nothing in [CANDIDATES] comes close. */
    fun guess(raw: Double, reference: Double): Guess {
        if (raw == 0.0 || !raw.isFinite() || !reference.isFinite()) return Guess(scale = null, ratio = Double.NaN)
        val ratio = reference / raw
        val best = CANDIDATES.minByOrNull { candidate -> abs(ratio - candidate) / candidate }
        val matches = best != null && abs(ratio - best) / best <= TOLERANCE
        return Guess(scale = if (matches) best else null, ratio = ratio)
    }

    /** Accepts "54,17", "54.17", "54.17 V", "~54" etc. - whatever a person types while copying a
     * number off another app's screen. Returns null if nothing digit-like could be parsed. */
    fun parseReference(text: String): Double? {
        val cleaned = text.trim().filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        return cleaned.replace(',', '.').toDoubleOrNull()
    }

    /** One line per calibrated property, for the issue report body. [entries] is (property name,
     * raw value already read, owner's typed reference text). */
    fun report(model: String?, entries: List<Triple<String, Float, String>>): String = buildString {
        appendLine("Scale calibration for model: ${model ?: "unknown"}")
        if (entries.isEmpty()) {
            appendLine("No reference value entered.")
        }
        for ((name, raw, referenceText) in entries) {
            val reference = parseReference(referenceText)
            if (reference == null) {
                appendLine("$name: raw=$raw reference=\"$referenceText\" (could not parse as a number)")
                continue
            }
            val result = guess(raw.toDouble(), reference)
            val scaleText = result.scale?.let { "x$it" } ?: "unclear"
            appendLine("$name: raw=$raw reference=$reference ratio=%.4f guessed scale=%s".format(java.util.Locale.US, result.ratio, scaleText))
        }
    }.trimEnd()
}
