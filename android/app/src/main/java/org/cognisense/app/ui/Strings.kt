package org.cognisense.app.ui

import org.cognisense.core.config.JObj
import org.cognisense.core.config.Json

enum class Lang(val code: String) { UR("ur"), EN("en") }

/**
 * Interface text, loaded from config/strings.json: shared with the browser preview so reviewers
 * see exactly the text the app shows. English is the source text.
 *
 * EVERY URDU STRING IS AN UNVALIDATED DRAFT requiring forward translation, independent back
 * translation, reconciliation and a child-comprehension check before any field use.
 */
object Strings {
    private var table: Map<String, Pair<String, String>> = emptyMap()

    /** Parses config/strings.json; throws (with the key path) if any entry lacks "en" or "ur". */
    fun load(bytes: ByteArray) {
        @Suppress("UNCHECKED_CAST")
        val root = JObj(Json.parse(bytes.decodeToString()) as? Map<String, Any?>
            ?: throw IllegalArgumentException("strings: root must be an object"))
        require(root.str("schema") == "cognisense.strings") { "strings: unexpected schema" }
        val s = root.obj("strings")
        table = s.keys().associateWith { k -> s.obj(k).let { it.str("en") to it.str("ur") } }
    }

    val loaded: Boolean get() = table.isNotEmpty()

    fun get(key: String, lang: Lang): String =
        table[key]?.let { if (lang == Lang.UR) it.second else it.first } ?: "⟨$key⟩"

    val keys: Set<String> get() = table.keys
}
