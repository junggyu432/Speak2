package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// --- Models for prompt 1 output (Moshi direct deserialization of the generated words) ---

@JsonClass(generateAdapter = true)
data class GeneratedWordsContainer(
    @Json(name = "generated_words") val generatedWords: List<GeneratedWordItem>
)

@JsonClass(generateAdapter = true)
data class GeneratedWordItem(
    @Json(name = "item_type") val itemType: String, // 'VERB' or 'CHUNK'
    @Json(name = "target_english") val targetEnglish: String,
    @Json(name = "target_meaning") val targetMeaning: String,
    @Json(name = "context_kr") val contextKr: String,
    @Json(name = "target_hint") val targetHint: String,
    @Json(name = "native_example") val nativeExample: String,
    @Json(name = "native_example_kr") val nativeExampleKr: String
)
