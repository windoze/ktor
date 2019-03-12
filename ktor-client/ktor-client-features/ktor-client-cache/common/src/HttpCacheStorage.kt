package io.ktor.client.features.cache

import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*

@KtorExperimentalAPI
interface HttpCacheStorage {

    fun store(url: Url, value: HttpCacheEntry)

    fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry

    fun findByUrl(url: Url): List<HttpCacheEntry>

    companion object {
        val Empty: HttpCacheStorage = TODO()
        val Default: HttpCacheStorage = TODO()
    }
}

internal suspend fun HttpCacheStorage.store(url: Url, value: HttpResponse): HttpCacheEntry {
    val result = HttpCacheEntry(value)
    store(url, result)
    return result
}
