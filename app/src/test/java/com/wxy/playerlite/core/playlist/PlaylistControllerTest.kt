package com.wxy.playerlite.core.playlist

import com.wxy.playerlite.playback.model.PlaybackMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaylistControllerTest {
    private lateinit var storage: FakeStorage
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        storage = FakeStorage()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun addMoveRemove_shouldKeepActiveIndexConsistent() {
        val controller = createController()
        val a = item("a", "uri://a")
        val b = item("b", "uri://b")
        val c = item("c", "uri://c")

        controller.addItem(a, makeActive = true)
        controller.addItem(b, makeActive = false)
        controller.addItem(c, makeActive = false)

        assertEquals(listOf("a", "b", "c"), controller.state.items.map { it.id })
        assertEquals(0, controller.state.activeIndex)

        controller.moveItem(fromIndex = 0, toIndex = 2)
        assertEquals(listOf("b", "c", "a"), controller.state.items.map { it.id })
        assertEquals(2, controller.state.activeIndex)

        controller.removeAt(1)
        assertEquals(listOf("b", "a"), controller.state.items.map { it.id })
        assertEquals(1, controller.state.activeIndex)
    }

    @Test
    fun nextPreviousBoundary_andSingleItemCompatibility_shouldWork() {
        val controller = createController()
        val single = item("single", "uri://single")
        controller.replaceWithSingle(single)

        val beforePrev = controller.state
        controller.moveToPrevious()
        assertEquals(beforePrev, controller.state)

        val beforeNext = controller.state
        controller.moveToNext()
        assertEquals(beforeNext, controller.state)

        controller.addItem(item("next", "uri://next"), makeActive = false)
        assertEquals(0, controller.state.activeIndex)

        controller.moveToNext()
        assertEquals(1, controller.state.activeIndex)

        val onTail = controller.state
        controller.moveToNext()
        assertEquals(onTail, controller.state)

        controller.moveToPrevious()
        assertEquals(0, controller.state.activeIndex)
    }

    @Test
    fun replaceAll_shouldUseRequestedActiveIndexAndPreservePlaybackMode() {
        val controller = createController(orderShuffler = { ids -> ids.reversed() })
        controller.addItem(item("old-a", "uri://old-a"), makeActive = true)
        controller.addItem(item("old-b", "uri://old-b"), makeActive = false)
        controller.setPlaybackMode(PlaybackMode.SHUFFLE)

        controller.replaceAll(
            items = listOf(
                item("new-a", "uri://new-a"),
                item("new-b", "uri://new-b"),
                item("new-c", "uri://new-c")
            ),
            activeIndex = 2
        )

        assertEquals(PlaybackMode.SHUFFLE, controller.state.playbackMode)
        assertEquals(listOf("new-a", "new-b", "new-c"), controller.state.originalItems.map { it.id })
        assertEquals("new-c", controller.state.activeItemId)
        assertEquals(listOf("new-c", "new-b", "new-a"), controller.state.shuffledOrderIds)
    }

    @Test
    fun flushAndRestore_shouldPersistState() {
        val controller = createController()
        controller.addItem(item("a", "uri://a"), makeActive = true)
        controller.addItem(item("b", "uri://b"), makeActive = true)
        controller.flush()

        val raw = storage.read(PlaylistController.STORAGE_KEY)
        assertNotNull(raw)

        val restored = createController().restore { true }
        assertEquals(listOf("a", "b"), restored.items.map { it.id })
        assertEquals(1, restored.activeIndex)
    }

    @Test
    fun flushAndRestore_shouldPreserveOptionalSongId() {
        val controller = createController()
        controller.addItem(
            PlaylistItem(
                id = "song-1",
                uri = "https://example.com/song.mp3",
                displayName = "在线歌曲",
                songId = "1973665667"
            ),
            makeActive = true
        )
        controller.flush()

        val restored = createController().restore { true }

        assertEquals("1973665667", restored.items.single().songId)
    }

    @Test
    fun flushAndRestore_shouldPreserveOnlineMetadataWithoutDirectPlaybackUri() {
        val controller = createController()
        controller.addItem(
            PlaylistItem(
                id = "queue-online-1",
                uri = "",
                displayName = "夜曲",
                songId = "1973665667",
                title = "夜曲",
                artistText = "周杰伦 / 杨瑞代",
                albumTitle = "十一月的萧邦",
                coverUrl = "https://example.com/night.jpg",
                durationMs = 213_000L,
                itemType = PlaylistItemType.ONLINE,
                contextType = "playlist",
                contextId = "24381616",
                contextTitle = "深夜单曲循环"
            ),
            makeActive = true
        )
        controller.flush()

        val restored = createController().restore { true }
        val item = restored.items.single()

        assertEquals(PlaylistItemType.ONLINE, item.itemType)
        assertEquals("1973665667", item.songId)
        assertEquals("夜曲", item.title)
        assertEquals("周杰伦 / 杨瑞代", item.artistText)
        assertEquals("十一月的萧邦", item.albumTitle)
        assertEquals("https://example.com/night.jpg", item.coverUrl)
        assertEquals(213_000L, item.durationMs)
        assertEquals("playlist", item.contextType)
        assertEquals("24381616", item.contextId)
        assertEquals("深夜单曲循环", item.contextTitle)
    }

    @Test
    fun restore_shouldDropInvalidEntriesAndClampActiveIndex() {
        storage.write(
            PlaylistController.STORAGE_KEY,
            """
            {
              "version":2,
              "activeIndex":9,
              "originalItems":[
                {"id":"valid","uri":"uri://ok","displayName":"ok"},
                {"id":"invalid","uri":"uri://drop","displayName":"drop"}
              ],
              "shuffledOrderIds":["invalid","valid"],
              "activeItemId":"invalid",
              "playbackMode":"shuffle",
              "showOriginalOrderInShuffle":true
            }
            """.trimIndent()
        )

        val restored = createController().restore { it.uri != "uri://drop" }
        assertEquals(1, restored.items.size)
        assertEquals("valid", restored.items.first().id)
        assertEquals(0, restored.activeIndex)

        val rewritten = storage.read(PlaylistController.STORAGE_KEY)
        assertNotNull(rewritten)
        assertFalse(rewritten!!.contains("uri://drop"))
    }

    @Test
    fun restore_shouldFallbackToEmptyWhenDataBrokenOrVersionMismatch() {
        storage.write(PlaylistController.STORAGE_KEY, "not-a-json")
        val broken = createController().restore { true }
        assertTrue(broken.items.isEmpty())
        assertEquals(-1, broken.activeIndex)
        assertNull(storage.read(PlaylistController.STORAGE_KEY))

        storage.write(
            PlaylistController.STORAGE_KEY,
            """
            {
              "version":999,
              "activeIndex":0,
              "items":[{"id":"a","uri":"uri://a","displayName":"A"}]
            }
            """.trimIndent()
        )

        val mismatch = createController().restore { true }
        assertTrue(mismatch.items.isEmpty())
        assertEquals(-1, mismatch.activeIndex)
        assertNull(storage.read(PlaylistController.STORAGE_KEY))
    }

    @Test
    fun restore_shouldMigrateLegacyStateToDualOrderModel() {
        storage.write(
            PlaylistController.STORAGE_KEY,
            """
            {
              "version":1,
              "activeIndex":1,
              "items":[
                {"id":"a","uri":"uri://a","displayName":"A"},
                {"id":"b","uri":"uri://b","displayName":"B"}
              ]
            }
            """.trimIndent()
        )

        val restored = createController().restore { true }

        assertEquals(listOf("a", "b"), restored.originalItems.map { it.id })
        assertEquals(listOf("a", "b"), restored.shuffledOrderIds)
        assertEquals("b", restored.activeItemId)
        assertEquals(PlaybackMode.LIST_LOOP, restored.playbackMode)
        assertFalse(restored.showOriginalOrderInShuffle)
    }

    @Test
    fun restore_shouldMigrateV2HttpSongEntriesIntoOnlineItems() {
        storage.write(
            PlaylistController.STORAGE_KEY,
            """
            {
              "version":2,
              "activeItemId":"song-1",
              "originalItems":[
                {
                  "id":"song-1",
                  "uri":"https://example.com/night.mp3",
                  "displayName":"夜曲",
                  "songId":"1973665667"
                }
              ],
              "shuffledOrderIds":["song-1"],
              "playbackMode":"list_loop",
              "showOriginalOrderInShuffle":false
            }
            """.trimIndent()
        )

        val restored = createController().restore { true }
        val item = restored.items.single()

        assertEquals(PlaylistItemType.ONLINE, item.itemType)
        assertEquals("1973665667", item.songId)
        assertEquals("https://example.com/night.mp3", item.uri)
        assertEquals("夜曲", item.title)
    }

    @Test
    fun setPlaybackMode_shuffle_shouldPreserveActiveItemAndPersistDisplayPreference() {
        val controller = createController(orderShuffler = { ids -> ids.reversed() })
        controller.addItem(item("a", "uri://a"), makeActive = true)
        controller.addItem(item("b", "uri://b"), makeActive = true)
        controller.addItem(item("c", "uri://c"), makeActive = false)

        controller.setPlaybackMode(PlaybackMode.SHUFFLE)
        controller.setShowOriginalOrderInShuffle(true)
        controller.flush()

        assertEquals(PlaybackMode.SHUFFLE, controller.state.playbackMode)
        assertEquals("b", controller.state.activeItemId)
        assertEquals(listOf("c", "b", "a"), controller.state.shuffledOrderIds)
        assertTrue(controller.state.showOriginalOrderInShuffle)

        val restored = createController(orderShuffler = { ids -> ids }).restore { true }
        assertEquals(PlaybackMode.SHUFFLE, restored.playbackMode)
        assertEquals("b", restored.activeItemId)
        assertEquals(listOf("c", "b", "a"), restored.shuffledOrderIds)
        assertTrue(restored.showOriginalOrderInShuffle)
    }

    @Test
    fun shuffleMode_shouldSwitchDisplayOrderWithoutChangingPlaybackOrder() {
        val controller = createController(orderShuffler = { ids -> ids.reversed() })
        controller.addItem(item("a", "uri://a"), makeActive = false)
        controller.addItem(item("b", "uri://b"), makeActive = true)
        controller.addItem(item("c", "uri://c"), makeActive = false)

        controller.setPlaybackMode(PlaybackMode.SHUFFLE)

        assertEquals(listOf("c", "b", "a"), controller.state.displayItems.map { it.id })
        assertEquals(1, controller.state.displayActiveIndex)
        assertFalse(controller.state.canReorderDisplayItems)

        controller.setShowOriginalOrderInShuffle(true)

        assertEquals(listOf("a", "b", "c"), controller.state.displayItems.map { it.id })
        assertEquals(1, controller.state.displayActiveIndex)
        assertTrue(controller.state.canReorderDisplayItems)
        assertEquals(listOf("c", "b", "a"), controller.state.playbackItems.map { it.id })
    }

    @Test
    fun selectingItemInShuffle_shouldKeepExistingShuffleOrder() {
        val controller = createController(orderShuffler = { ids -> ids.reversed() })
        controller.addItem(item("a", "uri://a"), makeActive = true)
        controller.addItem(item("b", "uri://b"), makeActive = false)
        controller.addItem(item("c", "uri://c"), makeActive = false)
        controller.setPlaybackMode(PlaybackMode.SHUFFLE)

        val previousOrder = controller.state.shuffledOrderIds

        controller.setActiveItemId("a")

        assertEquals(previousOrder, controller.state.shuffledOrderIds)
        assertEquals("a", controller.state.activeItemId)
    }

    private fun createController(
        orderShuffler: (List<String>) -> List<String> = { ids -> ids.shuffled() }
    ): PlaylistController {
        return PlaylistController(
            storage = storage,
            scope = scope,
            ioDispatcher = Dispatchers.Unconfined,
            persistDebounceMs = 0L,
            orderShuffler = orderShuffler
        )
    }

    private fun item(id: String, uri: String): PlaylistItem {
        return PlaylistItem(
            id = id,
            uri = uri,
            displayName = id
        )
    }

    private class FakeStorage : PlaylistStorage {
        private val valueMap = mutableMapOf<String, String>()

        override fun read(key: String): String? {
            return valueMap[key]
        }

        override fun write(key: String, value: String) {
            valueMap[key] = value
        }

        override fun remove(key: String) {
            valueMap.remove(key)
        }
    }
}
