package com.wxy.playerlite.network.core

class NetworkRequestException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)
