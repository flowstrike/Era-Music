package com.spyou.eramusic.data.quotes

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Quote(
    val text: String,
    val author: String,
)

class QuotesService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    companion object {
        private const val TAG = "QuotesService"
        private val FALLBACK_QUOTES = listOf(
            Quote("I have found the one whom my soul loves.", "Song of Solomon 3:4"),
            Quote("You are my today and all of my tomorrows.", "Leo Christopher"),
            Quote("In all the world, there is no heart for me like yours.", "Maya Angelou"),
            Quote("I love you not because of who you are, but because of who I am when I am with you.", "Roy Croft"),
            Quote("Every love story is beautiful, but ours is my favorite.", "Unknown"),
            Quote("I fell in love the way you fall asleep: slowly, and then all at once.", "John Green"),
            Quote("You are my sun, my moon, and all my stars.", "E.E. Cummings"),
            Quote("If I had a flower for every time I thought of you, I could walk through my garden forever.", "Alfred Tennyson"),
            Quote("I choose you. And I'll choose you over and over. Without pause, without doubt, in a heartbeat. I'll keep choosing you.", "Unknown"),
            Quote("You know you're in love when you can't fall asleep because reality is finally better than your dreams.", "Dr. Seuss"),
            Quote("My night has become a sunny dawn because of you.", "Ibn Arabi"),
            Quote("You're not just my love, you're my best friend, my partner in crime, and my soulmate.", "Unknown"),
            Quote("I would rather share one lifetime with you than face all the ages of this world alone.", "J.R.R. Tolkien"),
            Quote("If I know what love is, it is because of you.", "Hermann Hesse"),
            Quote("You are the finest, loveliest, tenderest, and most beautiful person I have ever known — and even that is an understatement.", "F. Scott Fitzgerald"),
            Quote("I love you to the moon and back.", "Diane Love"),
            Quote("Whatever our souls are made of, his and mine are the same.", "Emily Brontë"),
            Quote("You are the reason behind my smile, the reason behind my laughter, and the reason behind my happiness.", "Unknown"),
            Quote("I want all of my lasts to be with you.", "Unknown"),
            Quote("You don't love someone for their looks, or their clothes, or for their fancy car, but because they sing a song only you can hear.", "Oscar Wilde"),
        )
    }

    suspend fun fetchQuote(): Quote = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.quotable.io/quotes/random?tags=love|romantic|flirting")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return@withContext randomFallback()
                val quotes = gson.fromJson(json, Array<QuotableResponse>::class.java)
                if (!quotes.isNullOrEmpty()) {
                    return@withContext Quote(
                        text = quotes[0].content,
                        author = quotes[0].author,
                    )
                }
            }
            Log.w(TAG, "Quotable API failed (${response.code}), using fallback")
        } catch (e: Exception) {
            Log.w(TAG, "Quotable API error: ${e.message}")
        }

        try {
            val request = Request.Builder()
                .url("https://dummyjson.com/quotes/random")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return@withContext randomFallback()
                val q = gson.fromJson(json, DummyJsonResponse::class.java)
                return@withContext Quote(text = q.quote, author = q.author)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DummyJSON API error: ${e.message}")
        }

        randomFallback()
    }

    private fun randomFallback(): Quote {
        return FALLBACK_QUOTES.random()
    }
}

private data class QuotableResponse(
    val content: String,
    val author: String,
)

private data class DummyJsonResponse(
    val quote: String,
    val author: String,
)
