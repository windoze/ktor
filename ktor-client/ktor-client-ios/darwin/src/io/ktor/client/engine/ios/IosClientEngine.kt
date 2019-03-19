package io.ktor.client.engine.ios

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.writer
import kotlinx.io.core.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

internal class IosClientEngine(
    override val config: IosClientEngineConfig
) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall = suspendCancellableCoroutine { continuation ->
        val callContext = coroutineContext + CompletableDeferred<Unit>()
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()

        val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {
            val chunks = Channel<ByteArray>(Channel.UNLIMITED)

            override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
                val content = didReceiveData.toByteArray()
                if (!chunks.offer(content)) throw IosHttpRequestException()
            }

            override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
                chunks.close()

                if (didCompleteWithError != null) {
                    continuation.resumeWithException(IosHttpRequestException(didCompleteWithError))
                    return
                }

                val response = task.response as NSHTTPURLResponse

                @Suppress("UNCHECKED_CAST")
                val headersDict = response.allHeaderFields as Map<String, String>

                val status = HttpStatusCode.fromValue(response.statusCode.toInt())
                val headers = buildHeaders {
                    headersDict.mapKeys { (key, value) -> append(key, value) }
                }

                val responseContext = writer(coroutineContext, autoFlush = true) {
                    while (!chunks.isClosedForReceive) {
                        val chunk = chunks.receive()
                        channel.writeFully(chunk)
                    }
                }

                val result = IosHttpResponse(
                    call, status, headers, requestTime,
                    responseContext.channel, callContext
                )

                continuation.resume(HttpEngineCall(request, result))
            }
        }

        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate, delegateQueue = NSOperationQueue.mainQueue()
        )

        val url = URLBuilder().takeFrom(request.url).buildString()
        val nativeRequest = NSMutableURLRequest.requestWithURL(NSURL(string = url))

        mergeHeaders(request.headers, request.content) { key, value ->
            nativeRequest.setValue(value, key)
        }

        nativeRequest.setCachePolicy(NSURLRequestReloadIgnoringCacheData)
        nativeRequest.setHTTPMethod(request.method.value)

        launch(callContext) {
            val content = request.content
            val body = readOutgoingContent(content)?.toNSData()

            body?.let { nativeRequest.setHTTPBody(it) }
            session.dataTaskWithRequest(nativeRequest).resume()
        }
    }

    override fun close() {
        coroutineContext.cancel()
    }
}

private suspend fun readOutgoingContent(content: OutgoingContent): ByteArray? = when (content) {
    is OutgoingContent.ByteArrayContent -> content.bytes()
    is OutgoingContent.WriteChannelContent -> {
        val channel = ByteChannel()
        GlobalScope.launch(Dispatchers.Unconfined) {
            content.writeTo(channel)
        }

        channel.readRemaining().readBytes()
    }
    is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(content)
}
