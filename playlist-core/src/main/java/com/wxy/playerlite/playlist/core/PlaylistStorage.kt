package com.wxy.playerlite.playlist.core

interface PlaylistStorage {
    fun read(key: String): String?

    fun write(key: String, value: String)

    fun remove(key: String)
}
