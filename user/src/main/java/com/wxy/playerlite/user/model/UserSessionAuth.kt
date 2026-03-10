package com.wxy.playerlite.user.model

fun UserSession.toAuthHeaders(): Map<String, String> {
    return buildMap {
        if (cookie.isNotBlank()) {
            put("Cookie", cookie)
        }
        csrfToken?.takeIf { it.isNotBlank() }?.let { put("X-CSRF-Token", it) }
    }
}
