package org.cognisense.core.util

import kotlin.math.abs
import kotlin.math.floor

/**
 * Platform-independent helpers. The core must compile unchanged for the JVM (Android) and for
 * JavaScript (browser preview), so it uses no java.* APIs. These replace MessageDigest,
 * String.format and Charsets with pure Kotlin that behaves identically on both platforms.
 */
object Portable {
    /** Fixed-point decimal with [decimals] places, '.' separator, no locale, half rounded away from zero. */
    fun fixed(x: Double, decimals: Int): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val scaled = floor(abs(x) * scale + 0.5).toLong()
        val whole = scaled / scale
        val frac = (scaled % scale).toString().padStart(decimals, '0')
        val sign = if (x < 0 && scaled != 0L) "-" else ""
        return if (decimals == 0) "$sign$whole" else "$sign$whole.$frac"
    }

    fun hex2(b: Int): String = (b and 0xff).toString(16).padStart(2, '0')
    fun hex4(v: Int): String = (v and 0xffff).toString(16).padStart(4, '0')

    /** SHA-256 (FIPS 180-4) of [data], lowercase hex. */
    fun sha256Hex(data: ByteArray): String = sha256(data).joinToString("") { hex2(it.toInt()) }

    private val K = intArrayOf(
        0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(), 0x3956c25bu.toInt(), 0x59f111f1u.toInt(),
        0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(), 0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(),
        0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(), 0xe49b69c1u.toInt(), 0xefbe4786u.toInt(),
        0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(), 0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
        0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(), 0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(),
        0x06ca6351u.toInt(), 0x14292967u.toInt(), 0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(),
        0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(), 0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(),
        0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(), 0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
        0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(), 0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(),
        0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(), 0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(),
        0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt(),
    )

    fun sha256(data: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667u.toInt(), 0xbb67ae85u.toInt(), 0x3c6ef372u.toInt(), 0xa54ff53au.toInt(), 0x510e527fu.toInt(), 0x9b05688cu.toInt(), 0x1f83d9abu.toInt(), 0x5be0cd19u.toInt())
        val bitLen = data.size.toLong() * 8
        val padded = ByteArray(((data.size + 9 + 63) / 64) * 64)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in 0 until padded.size / 64) {
            for (i in 0 until 16) {
                val o = chunk * 64 + i * 4
                w[i] = ((padded[o].toInt() and 0xff) shl 24) or ((padded[o + 1].toInt() and 0xff) shl 16) or
                    ((padded[o + 2].toInt() and 0xff) shl 8) or (padded[o + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (j in 0 until 4) out[i * 4 + j] = (h[i] ushr (24 - 8 * j)).toByte()
        return out
    }
}
