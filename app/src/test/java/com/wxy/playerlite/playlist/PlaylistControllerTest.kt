package com.wxy.playerlite.playlist

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
    fun restore_shouldDropInvalidEntriesAndClampActiveIndex() {
        storage.write(
            PlaylistController.STORAGE_KEY,
            """
            {
              "version":1,
              "activeIndex":9,
              "items":[
                {"id":"valid","uri":"uri://ok","displayName":"ok"},
                {"id":"invalid","uri":"uri://drop","displayName":"drop"}
              ]
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
              "version":2,
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

    private fun createController(): PlaylistController {
        return PlaylistController(
            storage = storage,
            scope = scope,
            ioDispatcher = Dispatchers.Unconfined,
            persistDebounceMs = 0L
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
