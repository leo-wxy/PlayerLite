package com.wxy.playerlite.feature.main

import android.app.Application
import android.content.Intent
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.search.SearchRouteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ContentEntryActionNavigationTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun resolveContentEntryLaunch_shouldCreateDetailIntentForSupportedTarget() {
        val launch = resolveContentEntryLaunch(
            context = context,
            action = ContentEntryAction.OpenDetail(
                SearchRouteTarget.Album(albumId = "32311")
            )
        )

        requireNotNull(launch.intent)
        assertEquals(AlbumDetailActivity::class.java.name, launch.intent?.component?.className)
        assertEquals("32311", launch.intent?.getStringExtra("album_id"))
        assertNull(launch.failureMessage)
    }

    @Test
    fun resolveContentEntryLaunch_shouldCreateViewIntentForUriAction() {
        val launch = resolveContentEntryLaunch(
            context = context,
            action = ContentEntryAction.OpenUri(
                uri = "https://music.163.com/topic?id=1"
            )
        )

        requireNotNull(launch.intent)
        assertEquals(Intent.ACTION_VIEW, launch.intent?.action)
        assertEquals("https://music.163.com/topic?id=1", launch.intent?.dataString)
        assertEquals(DefaultOpenLinkFailedMessage, launch.failureMessage)
    }

    @Test
    fun resolveContentEntryLaunch_shouldExposeMessageForUnsupportedAction() {
        val launch = resolveContentEntryLaunch(
            context = context,
            action = ContentEntryAction.Unsupported("当前版本暂不支持打开专栏详情")
        )

        assertNull(launch.intent)
        assertEquals("当前版本暂不支持打开专栏详情", launch.failureMessage)
    }
}
