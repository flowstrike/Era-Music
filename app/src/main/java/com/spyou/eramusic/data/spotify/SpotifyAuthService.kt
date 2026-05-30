package com.spyou.eramusic.data.spotify

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpotifyAuthService(
    private val clientId: String,
    private val clientSecret: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiry: Long = 0

    suspend fun getAccessToken(): String {
        cachedToken?.let { token ->
            if (System.currentTimeMillis() < tokenExpiry) return token
        }
        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", Credentials.basic(clientId, clientSecret))
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: error("Empty token response")
            val parsed = gson.fromJson(json, TokenResponse::class.java)
            cachedToken = parsed.accessToken
            tokenExpiry = System.currentTimeMillis() + (parsed.expiresIn * 1000L) - 60_000L
            cachedToken!!
        }
    }

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresIn: Int,
    )
}
