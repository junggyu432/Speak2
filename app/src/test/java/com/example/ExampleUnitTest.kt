package com.example

import com.example.data.api.GeminiService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
    @Test
    fun debugGeminiApiCalls() = runBlocking {
        val apiKey = System.getenv("GEMINI_API_KEY") ?: System.getProperty("GEMINI_API_KEY") ?: ""
        println("=== DEBUGMING GEMINI API CALLS ===")
        println("Resolved API Key length: ${apiKey.length}")
        
        val service = GeminiService()
        if (apiKey.isNotEmpty()) {
            service.customApiKey = apiKey
        } else {
            println("Skipping real call because GEMINI_API_KEY is not defined in the JVM environment.")
            return@runBlocking
        }

        println("\nTesting 1: extractLearningMaterials")
        try {
            val result = service.extractLearningMaterials("Hello, can you help me buy coffee?")
            println("Success! Extraction count: ${result.size}")
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            println("HTTP Extraction Failed: Class=${e.javaClass.simpleName}, Code=${e.code()}, Message=${e.message()}")
            println("Error Body: $errorBody")
        } catch (e: Exception) {
            println("General Extraction Failed: $e")
            e.printStackTrace()
        }

        println("\nTesting 2: researchTopicExpressions")
        try {
            val result = service.researchTopicExpressions("공항 입국심사")
            println("Success! Research count: ${result.size}")
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            println("HTTP Research Failed: Class=${e.javaClass.simpleName}, Code=${e.code()}, Message=${e.message()}")
            println("Error Body: $errorBody")
        } catch (e: Exception) {
            println("General Research Failed: $e")
            e.printStackTrace()
        }
    }
}
