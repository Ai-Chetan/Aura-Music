package com.aura.music.data.extraction

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class OkHttpDownloader(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        // GET/HEAD must not carry a body — passing an empty body breaks
        // NewPipe's player-response requests on some YouTube endpoints.
        if (dataToSend != null) {
            requestBuilder.method(httpMethod, dataToSend.toRequestBody())
        } else {
            when (httpMethod) {
                "GET" -> requestBuilder.get()
                "HEAD" -> requestBuilder.head()
                else -> requestBuilder.method(httpMethod, null)
            }
        }

        headers.forEach { (headerName, headerValues) ->
            headerValues.forEach { headerValue ->
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        val response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw IOException("Failed to execute request: ${e.message}", e)
        }

        response.use {
            val responseCode = it.code
            val responseBody = it.body?.string() ?: ""
            val responseMessage = it.message
            val responseHeaders = mutableMapOf<String, List<String>>()

            it.headers.forEach { (name, value) ->
                val existing = responseHeaders.getOrPut(name) { emptyList() }
                responseHeaders[name] = existing + value
            }

            if (responseCode == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            return Response(
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                url
            )
        }
    }

    companion object {
        // Modern Chrome UA — YouTube throttles/blocks stale agents.
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}