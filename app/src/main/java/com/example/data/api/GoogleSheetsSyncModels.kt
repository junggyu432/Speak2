package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SheetSyncRequest(
    @Json(name = "action") val action: String,
    @Json(name = "words") val words: List<SheetWordItem>,
    @Json(name = "chatLogs") val chatLogs: List<SheetChatLogItem>
)

@JsonClass(generateAdapter = true)
data class SheetSyncResponse(
    @Json(name = "status") val status: String,
    @Json(name = "message") val message: String? = null,
    @Json(name = "words") val words: List<SheetWordItem>? = null,
    @Json(name = "chatLogs") val chatLogs: List<SheetChatLogItem>? = null
)

@JsonClass(generateAdapter = true)
data class SheetWordItem(
    @Json(name = "itemType") val itemType: String,
    @Json(name = "targetEnglish") val targetEnglish: String,
    @Json(name = "targetMeaning") val targetMeaning: String,
    @Json(name = "contextKr") val contextKr: String,
    @Json(name = "targetHint") val targetHint: String,
    @Json(name = "nativeExample") val nativeExample: String,
    @Json(name = "nativeExampleKr") val nativeExampleKr: String,
    @Json(name = "status") val status: String,
    @Json(name = "createdAt") val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class SheetChatLogItem(
    @Json(name = "targetEnglish") val targetEnglish: String,
    @Json(name = "userTypedSentence") val userTypedSentence: String,
    @Json(name = "createdAt") val createdAt: Long
)
