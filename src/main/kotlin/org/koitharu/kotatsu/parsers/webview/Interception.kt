package org.koitharu.kotatsu.parsers.webview

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

public class InterceptionConfig(
    @JvmField public val timeoutMs: Long,
    @JvmField public val urlPattern: Regex,
    @JvmField public val pageScript: String,
    @JvmField public val maxRequests: Int,
)

public class InterceptedRequest(
    @JvmField public val url: String,
    @JvmField public val headers: Map<String, String> = emptyMap(),
) {
    public fun getQueryParameter(name: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
    }
}
