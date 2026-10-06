package org.cognisense.core.config

import kotlin.math.abs
import kotlin.math.floor

/**
 * Minimal strict JSON parser (RFC 8259), so the core stays free of third-party dependencies.
 * Objects keep key order; duplicate keys are rejected; numbers become Double.
 * STATUS: IMPLEMENTED (tested in CoreTests).
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (!p.end()) p.fail("trailing characters")
        return v
    }

    private class Parser(private val s: String) {
        var i = 0
        fun end() = i >= s.length
        fun fail(msg: String): Nothing = throw IllegalArgumentException("JSON error at character $i: $msg")
        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\r' || s[i] == '\n')) i++ }
        private fun expect(c: Char) { if (end() || s[i] != c) fail("expected '$c'"); i++ }

        fun value(): Any? {
            if (end()) fail("unexpected end of input")
            val c = s[i]
            return when {
                c == '{' -> obj()
                c == '[' -> arr()
                c == '"' -> str()
                c == 't' -> lit("true", true)
                c == 'f' -> lit("false", false)
                c == 'n' -> lit("null", null)
                c == '-' || c in '0'..'9' -> num()
                else -> fail("unexpected '$c'")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            expect('{'); ws()
            if (!end() && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (end() || s[i] != '"') fail("expected a string key")
                val k = str()
                ws(); expect(':'); ws()
                if (m.containsKey(k)) fail("duplicate key '$k'")
                m[k] = value()
                ws()
                if (end()) fail("unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> { i--; fail("expected ',' or '}'") }
                }
            }
        }

        private fun arr(): List<Any?> {
            val a = ArrayList<Any?>()
            expect('['); ws()
            if (!end() && s[i] == ']') { i++; return a }
            while (true) {
                ws(); a += value(); ws()
                if (end()) fail("unterminated array")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return a
                    else -> { i--; fail("expected ',' or ']'") }
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (end()) fail("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (end()) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("short \\u escape")
                                val hex = s.substring(i, i + 4)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("bad \\u escape")
                                sb.append(hex.toInt(16).toChar()); i += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> fail("unescaped control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            if (s[i] == '-') i++
            if (end()) fail("bad number")
            when {
                s[i] == '0' -> i++
                s[i] in '1'..'9' -> while (i < s.length && s[i] in '0'..'9') i++
                else -> fail("bad number")
            }
            if (i < s.length && s[i] == '.') {
                i++; val d = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == d) fail("bad fraction")
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val d = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == d) fail("bad exponent")
            }
            return s.substring(start, i).toDouble()
        }
    }
}

/**
 * Typed, path-aware view of a JSON object. Every missing or mistyped value fails with the full
 * path (e.g. "tasks.GNG.blocks"), so a bad config is caught at load time, never mid-session.
 */
class JObj(val map: Map<String, Any?>, val path: String = "$") {
    fun has(k: String) = map.containsKey(k)
    fun keys(): List<String> = map.keys.toList()

    private fun raw(k: String): Any? =
        if (map.containsKey(k)) map[k] else throw IllegalArgumentException("config: missing '$path.$k'")

    private fun bad(k: String, type: String): Nothing =
        throw IllegalArgumentException("config: '$path.$k' must be $type")

    fun str(k: String): String = raw(k) as? String ?: bad(k, "a string")
    fun num(k: String): Double = raw(k) as? Double ?: bad(k, "a number")
    fun bool(k: String): Boolean = raw(k) as? Boolean ?: bad(k, "true or false")

    fun int(k: String): Int {
        val d = num(k)
        if (d != floor(d) || abs(d) > Int.MAX_VALUE) bad(k, "an integer")
        return d.toInt()
    }

    fun long(k: String): Long {
        val d = num(k)
        if (d != floor(d) || abs(d) > 9.0e15) bad(k, "an integer")
        return d.toLong()
    }

    @Suppress("UNCHECKED_CAST")
    fun obj(k: String): JObj = JObj(raw(k) as? Map<String, Any?> ?: bad(k, "an object"), "$path.$k")

    fun arr(k: String): List<Any?> = raw(k) as? List<Any?> ?: bad(k, "an array")

    fun intList(k: String): List<Int> = arr(k).map { v ->
        val d = v as? Double ?: bad(k, "an array of integers")
        if (d != floor(d)) bad(k, "an array of integers")
        d.toInt()
    }

    fun numList(k: String): List<Double> = arr(k).map { it as? Double ?: bad(k, "an array of numbers") }
    fun strList(k: String): List<String> = arr(k).map { it as? String ?: bad(k, "an array of strings") }

    @Suppress("UNCHECKED_CAST")
    fun objList(k: String): List<JObj> = arr(k).mapIndexed { idx, v ->
        JObj(v as? Map<String, Any?> ?: bad(k, "an array of objects"), "$path.$k[$idx]")
    }

    fun intLists(k: String): List<List<Int>> = arr(k).map { v ->
        (v as? List<*>)?.map { x -> (x as? Double)?.takeIf { it == floor(it) }?.toInt() ?: bad(k, "an array of integer arrays") }
            ?: bad(k, "an array of integer arrays")
    }

    /** Shallow merge: keys in [override] replace keys here (used for Expert Review variants). */
    fun merged(override: JObj): JObj = JObj(LinkedHashMap(map).apply { putAll(override.map) }, path)
}
