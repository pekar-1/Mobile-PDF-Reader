package com.example.pdf_reader.ui.pdf
internal object ExternalLinks {
    private val allowedSchemes = setOf("http", "https", "mailto", "tel")

    // RFC 3986 scheme followed by ':'.
    private val schemePrefix = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

    // a link without a scheme gets https:// and any other scheme (e.g. file:, javascript:) is rejected
    fun normalize(raw: String): String? {
        val link = raw.trim()
        if (link.isEmpty()) return null
        val match = schemePrefix.find(link)
        if (match != null && !isHostAndPort(link, match)) {
            return link.takeIf { match.groupValues[1].lowercase() in allowedSchemes }
        }
        return "https://$link"
    }

    // check if is a host
    private fun isHostAndPort(link: String, match: MatchResult): Boolean =
        link.getOrNull(match.range.last + 1)?.isDigit() == true &&
            match.groupValues[1].lowercase() !in allowedSchemes
}
