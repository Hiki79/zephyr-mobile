package dev.zephyr.mobile.core

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer

/** Keep cancellation attached until the response body has been consumed. */
internal suspend fun <T> Call.readResponse(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use(read)
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

internal fun ResponseBody.boundedText(maxBytes: Long): String {
    val buffer = Buffer()
    val source = source()
    while (true) {
        val count = source.read(buffer, minOf(8192L, maxBytes + 1 - buffer.size))
        if (count == -1L) break
        require(buffer.size <= maxBytes) { "下载内容超过 ${maxBytes / (1024 * 1024)} MiB 限制" }
    }
    return buffer.readString(contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}
