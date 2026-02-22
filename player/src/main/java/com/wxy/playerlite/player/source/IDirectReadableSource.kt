package com.wxy.playerlite.player.source

import java.nio.ByteBuffer

interface IDirectReadableSource {
    fun readDirect(buffer: ByteBuffer, size: Int): Int
}
