package org.cognisense.core.scoring

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Timing-robustness class of a metric (docs/task_specifications.md §3):
 *  A — timing-independent (counts, proportions, accuracy)
 *  B — robust to a constant device offset; sensitive to jitter (RT differences, SD, durations)
 *  C — sensitive to the constant offset (absolute RT, CV, SSRT, thresholds on absolute RT)
 */
enum class TimingClass { A, B, C }

data class Metric(
    val name: String,
    /** null when not computable (e.g. too few trials); never imputed. */
    val value: Double?,
    val unit: String,
    val timingClass: TimingClass,
    /** Number of trials the value is based on. */
    val n: Int,
    val note: String = "",
)

object Stats {
    fun mean(xs: List<Double>): Double? = if (xs.isEmpty()) null else xs.sum() / xs.size

    fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted(); val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /** Sample standard deviation (n - 1). Null for fewer than 2 values. */
    fun sd(xs: List<Double>): Double? {
        if (xs.size < 2) return null
        val m = xs.sum() / xs.size
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }

    /**
     * Inverse standard normal CDF (Acklam's rational approximation, relative error ~1e-9).
     * Tested against Python's statistics.NormalDist.
     */
    fun probit(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "p must be in (0,1)" }
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
            3.754408661907416e+00)
        val pLow = 0.02425
        return when {
            p < pLow -> {
                val q = sqrt(-2 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
            p <= 1 - pLow -> {
                val q = p - 0.5; val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
            else -> {
                val q = sqrt(-2 * ln(1 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
        }
    }

    /**
     * d' with the log-linear correction (Hautus, 1995 [VERIFY]):
     * H = (hits + 0.5) / (nSignal + 1), FA = (falseAlarms + 0.5) / (nNoise + 1).
     */
    fun dPrime(hits: Int, nSignal: Int, falseAlarms: Int, nNoise: Int): Double? {
        if (nSignal <= 0 || nNoise <= 0) return null
        val h = (hits + 0.5) / (nSignal + 1)
        val fa = (falseAlarms + 0.5) / (nNoise + 1)
        return probit(h) - probit(fa)
    }

    fun ratio(num: Int, den: Int): Double? = if (den <= 0) null else num.toDouble() / den

    fun diff(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a - b
}
