package io.ktor.client.features.cache

import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*

@KtorExperimentalAPI
class HttpCacheEntry(
    val expires: GMTDate,
    val varyKeys: Map<String, String>,
    response: HttpResponse
) {
    val requestHeaders: Headers = TODO()

    val responseHeaders: Headers = TODO()

    constructor(response: HttpResponse) : this(response.cacheExpires(), response.varyKeys(), response)

    fun produceResponse(): HttpResponse = TODO()
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
    
     val smaxAge = cacheControl.firstOrNull { it.value.startsWith("s-max-age") }
        ?.value?.split("=")
        ?.get(1)?.toInt()


    val maxAge = cacheControl.firstOrNull { it.value.startsWith("max-age") }
        ?.value?.split("=")
        ?.get(1)?.toInt()


}

@KtorExperimentalAPI
internal fun HttpCacheEntry.isValid(): Boolean {
    TODO()
}
