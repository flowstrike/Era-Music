package com.spyou.eramusic.data.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class YouTubeDownloadService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun searchAndDownload(
        title: String,
        artist: String,
        targetDurationMs: Long,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val query = "$title $artist"
            val service = ServiceList.YouTube
            val searchQH = service.getSearchQHFactory()
                .fromQuery(query)
            val searchInfo = org.schabi.newpipe.extractor.search.SearchInfo
                .getInfo(service, searchQH)
            val results = searchInfo.relatedItems
            if (results.isEmpty()) return@withContext false

            val streamItems = results
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0 }

            val bestItem = if (streamItems.isNotEmpty()) {
                streamItems.minByOrNull {
                    abs(it.duration * 1000L - targetDurationMs)
                } ?: streamItems.first()
            } else {
                results.first()
            }

            val streamInfo = StreamInfo.getInfo(service, bestItem.url)
            val audioStream = pickAudioStream(streamInfo.audioStreams)
                ?: return@withContext false

            outputFile.parentFile?.mkdirs()
            val request = Request.Builder().url(audioStream.content).build()
            val response = httpClient.newCall(request).execute()
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output, 8192)
                }
            } ?: return@withContext false

            outputFile.exists() && outputFile.length() > 0
        } catch (_: Exception) {
            outputFile.delete()
            false
        }
    }

    private fun pickAudioStream(streams: List<AudioStream>): AudioStream? {
        return streams
            .filter { it.content != null && it.content.isNotEmpty() }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
    }
}
