package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d(TAG, "Request URL: ${request.url}")
            chain.proceed(request)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Parse the text response inside the JSON container.
     */
    fun extractText(response: GenerateContentResponse): String? {
        return response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
    }

    /**
     * Clean markdown wrappers around JSON (e.g. ```json ... ```) to extract raw JSON string.
     */
    fun cleanJsonMarkdown(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring("```json".length)
        } else if (clean.startsWith("```")) {
            clean = clean.substring("```".length)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - "```".length)
        }
        return clean.trim()
    }

    /**
     * Deserializes the words from the raw JSON output.
     */
    fun parseGeneratedWords(jsonText: String): List<GeneratedWordItem> {
        val cleanJson = cleanJsonMarkdown(jsonText)
        val adapter = moshi.adapter(GeneratedWordsContainer::class.java)
        return try {
            val container = adapter.fromJson(cleanJson)
            container?.generatedWords ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse generated words: $jsonText", e)
            emptyList()
        }
    }
}
