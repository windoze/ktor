package io.ktor.client.features.cache

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*

internal suspend fun HttpCacheEntry(response: HttpResponse): HttpCacheEntry {
    val body = response.content.readRemaining().readBytes()
    return HttpCacheEntry(response.cacheExpires(), response.varyKeys(), response, body)
}

@KtorExperimentalAPI
class HttpCacheEntry internal constructor(
    internal val expires: GMTDate,
    internal val varyKeys: Map<String, String>,
    internal val response: HttpResponse,
    internal val body: ByteArray
) {
    internal val requestHeaders: Headers = response.call.request.headers

    internal val responseHeaders: Headers = Headers.build {
        appendAll(response.headers)
    }

    internal fun produceResponse(): HttpResponse {
        val call = HttpCacheCall(response.call.client)
        call.response = HttpCacheResponse(call, body, response)
        call.request = HttpCacheRequest(call, response.call.request)

        return call.response
    }
}


internal fun HttpResponse.varyKeys(): Map<String, String> {
    val validationKeys = vary() ?: return emptyMap()

    val result = mutableMapOf<String, String>()
    val requestHeaders = call.request.headers

    for (key in validationKeys) {
        result[key] = requestHeaders[key] ?: ""
    }

    return result
}

internal fun HttpResponse.cacheExpires(): GMTDate {
    val cacheControl = cacheControl()

    val isPrivate = CacheControl.PRIVATE in cacheControl

    val maxAgeKey = if (isPrivate) "s-max-age" else "max-age"

    val maxAge = cacheControl.firstOrNull { it.value.startsWith(maxAgeKey) }
        ?.value?.split("=")
        ?.get(1)?.toInt()

    if (maxAge != null) {
        return call.response.requestTime + maxAge * 1000L
    }

    headers[HttpHeaders.Expires]?.fromHttpToGmtDate()?.let { return it }
    return GMTDate()
}

@KtorExperimentalAPI
internal fun HttpCacheEntry.isValid(): Boolean {
    val cacheControl = responseHeaders[HttpHeaders.CacheControl]?.let { parseHeaderValue(it) } ?: emptyList()
    return expires >= GMTDate() && !(CacheControl.MUST_REVALIDATE in cacheControl)
}

private class HttpCacheCall(client: HttpClient) : HttpClientCall(client)

private class HttpCacheRequest(
    override val call: HttpCacheCall, origin: HttpRequest
) : HttpRequest by origin

private class HttpCacheResponse(
    override val call: HttpCacheCall, body: ByteArray, origin: HttpResponse
) : HttpResponse by origin {
    override val content: ByteReadChannel = ByteReadChannel(body)

    override fun close() {}
}
