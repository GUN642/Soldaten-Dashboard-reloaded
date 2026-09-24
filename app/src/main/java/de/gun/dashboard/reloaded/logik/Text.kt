package de.gun.dashboard.reloaded.logik

/** Termin-Beschreibungen aus Outlook kommen oft als HTML – in lesbaren Text wandeln. */
fun textAusHtml(roh: String): String {
    var t = roh
    if (t.isBlank()) return ""
    if (!Regex("<[a-zA-Z!/][^>]*>").containsMatchIn(t)) return t.trim()
    t = t.replace(Regex("<!--[\\s\\S]*?-->"), "")
    t = t.replace(Regex("(?is)<(script|style|head)\\b.*?</\\1>"), "")
    t = t.replace(Regex("(?i)<\\s*br\\s*/?\\s*>"), "\n")
    t = t.replace(Regex("(?i)<\\s*li\\b[^>]*>"), "• ")
    t = t.replace(Regex("(?i)<\\s*/\\s*(p|div|tr|li|h[1-6]|table)\\s*>"), "\n")
    t = t.replace(Regex("<[^>]+>"), "")
    val ent = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü",
        "szlig" to "ß", "euro" to "€", "ndash" to "–", "mdash" to "—", "hellip" to "…"
    )
    t = Regex("&([a-zA-Z]+);").replace(t) { ent[it.groupValues[1]] ?: it.value }
    t = Regex("&#(\\d+);").replace(t) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    t = Regex("&#x([0-9a-fA-F]+);").replace(t) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
    t = t.replace(Regex("[ \\t]+"), " ").replace(Regex(" *\\n *"), "\n").replace(Regex("\\n{3,}"), "\n\n")
    return t.trim()
}

/** HTML-Sonderzeichen schützen (für Druckansichten). */
fun schutz(t: String?): String =
    (t ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
