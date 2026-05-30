package com.spyou.eramusic.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor() : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value ->
                builder.addHeader(name, value)
            }
        }

        val body = request.dataToSend()?.toRequestBody()
        builder.method(request.httpMethod(), body)

        val call = client.newCall(builder.build())
        val response = call.execute()

        if (response.code == 429) {
            throw ReCaptchaException("Rate limited", request.url())
        }

        val responseBody = response.body?.string().orEmpty()
        val headers = response.headers.toMultimap()

        return Response(
            response.code,
            response.message,
            headers,
            responseBody,
            request.url()
        )
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader() }
    }
}
