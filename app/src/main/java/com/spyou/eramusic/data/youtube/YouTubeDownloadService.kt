package com.spyou.eramusic.data.youtube

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class YouTubeDownloadService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "YouTubeDownload"
    }

    suspend fun searchAndDownload(
        title: String,
        artist: String,
        targetDurationMs: Long,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val query = "$title $artist"
            Log.d(TAG, "Searching YouTube for: $query")
            val service = ServiceList.YouTube
            val searchQH = service.getSearchQHFactory()
                .fromQuery(query)
            val searchInfo = org.schabi.newpipe.extractor.search.SearchInfo
                .getInfo(service, searchQH)
            val results = searchInfo.relatedItems
            Log.d(TAG, "Search returned ${results.size} results")
            if (results.isEmpty()) return@withContext false

            val streamItems = results
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 0 }
            Log.d(TAG, "Stream items with duration: ${streamItems.size}")

            val bestItem = if (streamItems.isNotEmpty()) {
                streamItems.minByOrNull {
                    abs(it.duration * 1000L - targetDurationMs)
                } ?: streamItems.first()
            } else {
                results.first()
            }
            Log.d(TAG, "Selected: ${bestItem.name} (${bestItem.url})")

            val streamInfo = StreamInfo.getInfo(service, bestItem.url)
            Log.d(TAG, "Audio streams: ${streamInfo.audioStreams.size}, Video streams: ${streamInfo.videoStreams.size}, Video only: ${streamInfo.videoOnlyStreams.size}")

            val downloadUrl = pickBestStream(streamInfo)
            if (downloadUrl == null) {
                Log.w(TAG, "No downloadable stream found for: $query")
                return@withContext false
            }
            Log.d(TAG, "Downloading from: ${downloadUrl.take(80)}")

            outputFile.parentFile?.mkdirs()
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Download response code: ${response.code}")
            response.body?.byteStream()?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output, 8192)
                }
            } ?: return@withContext false

            val success = outputFile.exists() && outputFile.length() > 0
            Log.d(TAG, "Download ${if (success) "success" else "failed"}: ${outputFile.length()} bytes")
            success
        } catch (e: Exception) {
            Log.e(TAG, "searchAndDownload failed for: $title $artist", e)
            outputFile.delete()
            false
        }
    }

    private fun pickBestStream(streamInfo: StreamInfo): String? {
        val audioStream = streamInfo.audioStreams
            .filter { !it.content.isNullOrEmpty() }
            .maxByOrNull { it.averageBitrate }
        if (audioStream != null) {
            Log.d(TAG, "Using audio stream: bitrate=${audioStream.averageBitrate}, delivery=${audioStream.deliveryMethod}")
            return audioStream.content
        }

        val videoStream = streamInfo.videoStreams
            .filter { !it.content.isNullOrEmpty() }
            .minByOrNull { it.resolution?.let { r -> parseResolution(r) } ?: Int.MAX_VALUE }
        if (videoStream != null) {
            Log.d(TAG, "Falling back to video stream: resolution=${videoStream.resolution}, delivery=${videoStream.deliveryMethod}")
            return videoStream.content
        }

        return null
    }

    private fun parseResolution(res: String): Int {
        return res.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
    }
}
