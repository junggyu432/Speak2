package com.example.data.api

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GoogleSheetsSyncService {
    private const val TAG = "GoogleSheetsSyncService"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Disable automatic redirect handling so we can manually follow redirects (preserving POST method!)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Sends local words and chat logs to Google Sheets Apps Script Web App, receives the merged cloud data lists.
     */
    fun performSync(
        webAppUrl: String,
        localWords: List<SheetWordItem>,
        localLogs: List<SheetChatLogItem>
    ): SheetSyncResponse {
        val adapter = moshi.adapter(SheetSyncRequest::class.java)
        val requestJson = adapter.toJson(
            SheetSyncRequest(
                action = "sync",
                words = localWords,
                chatLogs = localLogs
            )
        )
        
        Log.d(TAG, "Requesting sync to URL: $webAppUrl")
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(webAppUrl)
            .post(requestBody)
            .build()
            
        var response = client.newCall(request).execute()
        Log.d(TAG, "Initial response code: ${response.code}")
        
        // Google Apps Script redirects with 302/301/303. Manual redirection preserves POST body!
        if (response.code in 301..308) {
            val redirectUrl = response.header("Location")
            if (!redirectUrl.isNullOrEmpty()) {
                Log.d(TAG, "Following manual redirection to: $redirectUrl")
                val redirectRequest = Request.Builder()
                    .url(redirectUrl)
                    .post(requestBody)
                    .build()
                response = client.newCall(redirectRequest).execute()
                Log.d(TAG, "Redirected response code: ${response.code}")
            }
        }
        
        val responseBody = response.body?.string() ?: throw Exception("Empty response from Google Sheets")
        if (!response.isSuccessful && response.code !in 200..299) {
            throw Exception("HTTP ${response.code}: $responseBody")
        }
        
        Log.d(TAG, "Sync Response: $responseBody")
        val responseAdapter = moshi.adapter(SheetSyncResponse::class.java)
        return responseAdapter.fromJson(responseBody) ?: throw Exception("JSON conversion failed")
    }
}
