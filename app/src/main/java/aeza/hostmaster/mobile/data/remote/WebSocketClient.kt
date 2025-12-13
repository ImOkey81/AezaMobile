package aeza.hostmaster.mobile.data.remote

import android.util.Log
import aeza.hostmaster.mobile.BuildConfig
import aeza.hostmaster.mobile.data.model.CheckResponseDto
import com.google.gson.Gson
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader

private const val TAG = "WebSocketClient"

class WebSocketClient @Inject constructor(
    private val gson: Gson,
) {

    fun subscribeToJob(jobId: String): Flow<CheckResponseDto> = callbackFlow {
        val urls = buildWebSocketUrls()
        var client: StompClient? = null
        var topicSubscription: Disposable? = null
        var lifecycleSubscription: Disposable? = null
        var currentIndex = 0

        fun disconnect() {
            topicSubscription?.dispose()
            lifecycleSubscription?.dispose()
            client?.disconnect()
        }

        fun connect(url: String) {
            disconnect()
            client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url).apply {
                lifecycleSubscription = lifecycle().subscribe { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> subscribeToTopic(this)
                        LifecycleEvent.Type.ERROR -> {
                            Log.w(TAG, "WebSocket error on $url", event.exception)
                            if (currentIndex < urls.lastIndex) {
                                currentIndex++
                                connect(urls[currentIndex])
                            }
                        }

                        LifecycleEvent.Type.CLOSED -> {
                            Log.d(TAG, "WebSocket closed for $url")
                        }

                        else -> Unit
                    }
                }
                connect(emptyList())
            }
        }

        fun subscribeToTopic(client: StompClient) {
            topicSubscription?.dispose()
            topicSubscription = client.topic(
                "/topics/jobs/$jobId",
                listOf(StompHeader("id", jobId))
            ).subscribe({ message ->
                runCatching {
                    gson.fromJson(message.payload, CheckResponseDto::class.java)
                }.onSuccess { dto ->
                    trySend(dto).isSuccess
                }.onFailure { error ->
                    Log.w(TAG, "Failed to parse message: ${'$'}{message.payload}", error)
                }
            }, { error ->
                Log.w(TAG, "Topic subscription error", error)
            })
        }

        connect(urls[currentIndex])

        awaitClose {
            disconnect()
        }
    }

    private fun buildWebSocketUrls(): List<String> {
        val base = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        val wsBase = when {
            base.startsWith("wss://", ignoreCase = true) || base.startsWith("ws://", ignoreCase = true) -> base
            base.startsWith("https://", ignoreCase = true) -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://", ignoreCase = true) -> "ws://" + base.removePrefix("http://")
            else -> "ws://${'$'}base"
        }.trimEnd('/')

        val direct = "${'$'}wsBase/ws"
        val sockJs = "${'$'}wsBase/ws-sockjs/websocket"

        return listOf(direct, sockJs).distinct()
    }
}
