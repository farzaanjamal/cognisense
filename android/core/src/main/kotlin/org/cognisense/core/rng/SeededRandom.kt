package org.cognisense.core.rng

/**
 * Deterministic pseudo-random generator: SplitMix64 (Steele, Lea & Flood, 2014 [VERIFY]).
 *
 * STATUS: IMPLEMENTED (tested against an independent Python implementation).
 *
 * Why not java.util.Random or kotlin.random.Random: this generator is twelve lines,
 * fully specified here, and produces identical output on every JVM and Android
 * version, so a logged session seed reconstructs every trial sequence exactly.
 * Not for cryptographic use (participant IDs use SecureRandom in the app layer).
 */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform integer in [0, bound), by rejection sampling (no modulo bias). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        val m = (bound - 1).toLong()
        var u = nextLong() ushr 1
        var r = u % bound
        while (u - r + m < 0) { // overflow means u fell in the incomplete final chunk
            u = nextLong() ushr 1
            r = u % bound
        }
        return r.toInt()
    }

    /** Uniform integer in [from, until). */
    fun nextInt(from: Int, until: Int): Int {
        require(until > from) { "empty range" }
        return from + nextInt(until - from)
    }

    /** Uniform double in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** Fisher–Yates shuffle in place. */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
    }

    companion object {
        private val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        private val MIX1 = 0xBF58476D1CE4E5B9uL.toLong()
        private val MIX2 = 0x94D049BB133111EBuL.toLong()

        /** FNV-1a 64-bit hash of the UTF-8 bytes of [text]. Stable across platforms. */
        fun fnv1a64(text: String): Long {
            var h = 0xcbf29ce484222325uL.toLong()
            for (b in text.encodeToByteArray()) {
                h = h xor (b.toLong() and 0xff)
                h *= 0x100000001b3L
            }
            return h
        }

        /**
         * An independent, reproducible stream for one purpose, e.g. "GNG:0.1:SCORED:1".
         * Same session seed + same label => same stream, on any device.
         */
        fun derive(sessionSeed: Long, label: String): SeededRandom =
            SeededRandom(SeededRandom(sessionSeed xor fnv1a64(label)).nextLong())

        /**
         * Shuffle [items] until [ok] holds, deterministically. Throws if the constraint
         * is not satisfied within [maxAttempts] — a sign the constraint is infeasible.
         */
        fun <T> constrainedShuffle(
            items: List<T>, rng: SeededRandom, maxAttempts: Int = 100_000, ok: (List<T>) -> Boolean,
        ): List<T> {
            val work = items.toMutableList()
            repeat(maxAttempts) {
                rng.shuffle(work)
                if (ok(work)) return work.toList()
            }
            error("Sequence constraint not satisfied after $maxAttempts attempts")
        }

        /** Longest run of consecutive elements satisfying [pred]. */
        fun <T> longestRun(xs: List<T>, pred: (T) -> Boolean): Int {
            var best = 0; var cur = 0
            for (x in xs) { if (pred(x)) { cur++; if (cur > best) best = cur } else cur = 0 }
            return best
        }
    }
}
