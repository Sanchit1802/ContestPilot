package com.sanchit.contestpilot.data.provider

import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * Turns a thrown exception into a [ProviderError] without surfacing stack traces or
 * request details to the user.
 */
object ProviderErrorMapper {

    fun classify(throwable: Throwable): ProviderError = when (throwable) {
        is SocketTimeoutException -> ProviderError.TIMEOUT
        is UnknownHostException -> ProviderError.NETWORK_UNAVAILABLE
        is HttpException -> ProviderError.SERVICE_UNAVAILABLE
        is JsonParseException -> ProviderError.MALFORMED_RESPONSE
        is IllegalStateException -> ProviderError.MALFORMED_RESPONSE
        is IOException -> ProviderError.NETWORK_UNAVAILABLE
        else -> ProviderError.UNKNOWN
    }
}
