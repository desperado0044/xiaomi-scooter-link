package com.scooterre.client.protocol

/**
 * Plausibility check backing ScooterViewModel.checkLayout(): whether a basic reading's value is
 * one this app's table would actually expect, not just "the read succeeded". A model this app
 * does not know (activeModel == null - ModelSupport.supportOf treats that as FULL by design, e.g.
 * a scooter added from an export code) can still answer every GET: the generic MIoT "Device
 * Information" service (siid=1) exists on every model, just with different fields there (strings
 * such as the manufacturer or model name) - decoded as this app's UINT8/FLOAT types, such a string
 * can come back as "ok" with a small-looking number that is really just its first byte(s), not a
 * real battery level or odometer reading. The ranges below are deliberately generous - wide enough
 * to never reject a real scooter, only to catch a reinterpreted-garbage value that status codes
 * alone would have waved through as "the read succeeded".
 */
object LayoutCheck {
    enum class Outcome { PLAUSIBLE, IMPLAUSIBLE, INCONCLUSIVE }

    /** A timeout (status -1) says nothing about the property table (a bad moment on the radio) -
     * only an explicit status or a value outside the expected range counts against it. */
    private fun evaluate(result: SpecReadResult?, isPlausible: (Any?) -> Boolean): Outcome = when {
        result == null -> Outcome.INCONCLUSIVE
        !result.ok && result.status == -1 -> Outcome.INCONCLUSIVE
        !result.ok -> Outcome.IMPLAUSIBLE
        isPlausible(result.value) -> Outcome.PLAUSIBLE
        else -> Outcome.IMPLAUSIBLE
    }

    fun batteryLevel(result: SpecReadResult?): Outcome = evaluate(result) { (it as? Long)?.let { v -> v in 0..100 } == true }

    fun ridingMode(result: SpecReadResult?): Outcome = evaluate(result) { (it as? Long)?.let { v -> v in 1..3 } == true }

    /** TOTAL_MILEAGE is FLOAT on the wire, packed as value*100 (see DashboardScreen's UNIT_SCALES) -
     * checked unscaled, so the bound here (20,000,000) is a generous 200,000 km. */
    fun totalMileage(result: SpecReadResult?): Outcome =
        evaluate(result) { (it as? Float)?.let { v -> v.isFinite() && v in 0f..20_000_000f } == true }
}
