package com.wxy.playerlite.network.core

fun interface AuthHeaderProvider {
    fun currentAuthHeaders(): Map<String, String>
}
