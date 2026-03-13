package com.wxy.playerlite.feature.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.feature.search.searchRouteIntent

internal const val DefaultUnsupportedContentMessage = "当前内容暂不支持打开"
internal const val DefaultOpenLinkFailedMessage = "当前内容暂时无法打开"
internal const val UnsupportedColumnDetailMessage = "当前版本暂不支持打开专栏详情"

internal sealed interface ContentEntryAction {
    data class OpenDetail(
        val target: SearchRouteTarget
    ) : ContentEntryAction

    data class OpenUri(
        val uri: String,
        val fallbackMessage: String = DefaultOpenLinkFailedMessage
    ) : ContentEntryAction

    data class Unsupported(
        val message: String = DefaultUnsupportedContentMessage
    ) : ContentEntryAction
}

internal data class ContentEntryLaunch(
    val intent: Intent? = null,
    val failureMessage: String? = null
)

internal fun resolveContentEntryLaunch(
    context: Context,
    action: ContentEntryAction
): ContentEntryLaunch {
    return when (action) {
        is ContentEntryAction.OpenDetail -> {
            val intent = searchRouteIntent(context, action.target)
            if (intent != null) {
                ContentEntryLaunch(intent = intent)
            } else {
                ContentEntryLaunch(failureMessage = DefaultUnsupportedContentMessage)
            }
        }

        is ContentEntryAction.OpenUri -> {
            val parsedUri = action.uri.toLaunchableUri()
            if (parsedUri == null) {
                ContentEntryLaunch(failureMessage = action.fallbackMessage)
            } else {
                ContentEntryLaunch(
                    intent = Intent(Intent.ACTION_VIEW, parsedUri),
                    failureMessage = action.fallbackMessage
                )
            }
        }

        is ContentEntryAction.Unsupported -> ContentEntryLaunch(
            failureMessage = action.message
        )
    }
}

private fun String.toLaunchableUri(): Uri? {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    return uri.takeIf { !it.scheme.isNullOrBlank() }
}
