package io.github.holtdroid.webview.model

import java.net.URLEncoder

fun expandSize(values: Map<String, String>): Map<String, String> {
    val size = values["size"]?.let(::parseSize) ?: return values
    return values - "size" + mapOf("width" to size.first.toString(), "height" to size.second.toString())
}

fun normalizeUrl(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return "about:blank"
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text) && !Regex("^[^/]+:\\d+").matches(text.substringBefore('/'))) return text
    if (!text.contains(' ') && (text.contains('.') || text.startsWith("localhost") || text.contains(':'))) {
        val local = text.startsWith("localhost") || text.startsWith("127.") || text.startsWith("10.") || text.startsWith("192.168.")
        return (if (local) "http://" else "https://") + text
    }
    return "https://www.google.com/search?q=" + URLEncoder.encode(text, Charsets.UTF_8.name())
}
