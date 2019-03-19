
import cocoapods.jetfire.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.Foundation.*
import kotlin.coroutines.*

class JetfireWebSocketEngine : WebSocketEngine {
    private val completionHandler: CompletableDeferred<Unit> = CompletableDeferred()
    override val coroutineContext: CoroutineContext = completionHandler + Dispatchers.Unconfined

    override suspend fun execute(request: HttpRequest): WebSocketResponse {
        val requestTime = GMTDate()
        val callContext = CompletableDeferred<Unit>() + coroutineContext

        val session = JetfireWebSocketSession(callContext, request)

        return WebSocketResponse(callContext, requestTime, session)
    }
}

class JetfireWebSocketSession(
    override val coroutineContext: CoroutineContext,
    request: HttpRequest
) : WebSocketSession {

    init {
        val url = NSURL(string = request.url.toString())

        val subProtocols = request.headers[HttpHeaders.SecWebSocketProtocol]
            ?.split(",")?.map { it.trim() } ?: emptyList()

        val websocket = JFRWebSocket(url, subProtocols)

    }

    override val incoming: ReceiveChannel<Frame>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override val outgoing: SendChannel<Frame>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override suspend fun flush() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun terminate() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun close(cause: Throwable?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
