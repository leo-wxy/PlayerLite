package com.wxy.playerlite.playback.process.source

internal data class ParsedContentRange(
    val start: Long,
    val endInclusive: Long,
    val totalLength: Long?
)

internal fun parseContentRange(value: String?): ParsedContentRange? {
    val normalized = value?.trim().orEmpty()
    if (!normalized.startsWith("bytes ", ignoreCase = true)) {
        return null
    }
    val rangeAndTotal = normalized.substringAfter(' ').trim()
    val slashIndex = rangeAndTotal.indexOf('/')
    if (slashIndex <= 0 || slashIndex >= rangeAndTotal.lastIndex) {
        return null
    }
    val rangePart = rangeAndTotal.substring(0, slashIndex).trim()
    val totalPart = rangeAndTotal.substring(slashIndex + 1).trim()
    val dashIndex = rangePart.indexOf('-')
    if (dashIndex <= 0 || dashIndex >= rangePart.lastIndex) {
        return null
    }
    val start = rangePart.substring(0, dashIndex).trim().toLongOrNull() ?: return null
    val endInclusive = rangePart.substring(dashIndex + 1).trim().toLongOrNull() ?: return null
    val totalLength = if (totalPart == "*") null else totalPart.toLongOrNull() ?: return null
    if (start < 0L || endInclusive < start || (totalLength != null && endInclusive >= totalLength)) {
        return null
    }
    return ParsedContentRange(
        start = start,
        endInclusive = endInclusive,
        totalLength = totalLength
    )
}

internal fun isValidRangeResponse(
    requestOffset: Long,
    responseCode: Int,
    contentRange: String?
): Boolean {
    if (requestOffset < 0L) {
        return false
    }
    if (responseCode == 200) {
        return requestOffset == 0L
    }
    if (responseCode != 206) {
        return false
    }
    return parseContentRange(contentRange)?.start == requestOffset
}

internal class HttpResourceIdentityGuard {
    private var etag: String? = null
    private var lastModified: String? = null

    @Synchronized
    fun accept(responseEtag: String?, responseLastModified: String?): Boolean {
        val normalizedEtag = responseEtag?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedLastModified = responseLastModified?.trim()?.takeIf { it.isNotEmpty() }
        if (etag != null && normalizedEtag != null && etag != normalizedEtag) {
            return false
        }
        if (lastModified != null && normalizedLastModified != null &&
            lastModified != normalizedLastModified
        ) {
            return false
        }
        if (etag == null) {
            etag = normalizedEtag
        }
        if (lastModified == null) {
            lastModified = normalizedLastModified
        }
        return true
    }

    @Synchronized
    fun ifRangeValue(): String? {
        val strongEtag = etag?.takeUnless { it.startsWith("W/", ignoreCase = true) }
        return strongEtag ?: lastModified
    }
}
