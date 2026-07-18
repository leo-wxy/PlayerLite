package com.wxy.playerlite.user.model

fun UserSession.toAuthHeaders(): Map<String, String> {
    return buildMap {
        sanitizeCookieHeader(cookie)?.let { sanitizedCookie ->
            put("Cookie", sanitizedCookie)
        }
        csrfToken?.takeIf { it.isNotBlank() }?.let { put("X-CSRF-Token", it) }
    }
}

private fun sanitizeCookieHeader(rawCookie: String): String? {
    if (rawCookie.isBlank()) {
        return null
    }
    val sanitizedPairs = linkedMapOf<String, String>()
    rawCookie.split(';')
        .map { it.trim() }
        .forEach { segment ->
            if (segment.isBlank() || '=' !in segment) {
                return@forEach
            }
            val separatorIndex = segment.indexOf('=')
            val name = segment.substring(0, separatorIndex).trim()
            val value = segment.substring(separatorIndex + 1).trim()
            if (name.isBlank() || value.isBlank()) {
                return@forEach
            }
            if (COOKIE_ATTRIBUTE_NAMES.any { attribute ->
                attribute.equals(name, ignoreCase = true)
            }) {
                return@forEach
            }
            sanitizedPairs.putIfAbsent(name, value)
        }
    return sanitizedPairs.takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (name, value) -> "$name=$value" }
}

private val COOKIE_ATTRIBUTE_NAMES = setOf(
    "Max-Age",
    "Expires",
    "Path",
    "Domain",
    "Secure",
    "HttpOnly",
    "SameSite",
    "Comment",
    "Version"
)
