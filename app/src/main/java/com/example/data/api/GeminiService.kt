package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.database.WordWithLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService {

    private val apiService = GeminiClient.apiService
    var customApiKey: String? = null

    private fun getApiKey(): String {
        val apiKeyFromPrefs = customApiKey
        if (!apiKeyFromPrefs.isNullOrBlank()) {
            return apiKeyFromPrefs
        }
        val key = BuildConfig.GEMINI_API_KEY
        return if (key == "MY_GEMINI_API_KEY" || key.isBlank()) {
            ""
        } else {
            key
        }
    }

    /**
     * Checks if a valid API key exists.
     */
    fun hasValidApiKey(): Boolean {
        return getApiKey().isNotEmpty()
    }

    /**
     * Prompts ①: Extracts and refines up to 5 target English words/chunks from raw input text.
     */
    suspend fun extractLearningMaterials(rawText: String): List<GeneratedWordItem> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("API_KEY_MISSING")
        }

        val systemInstruction = """
            Role: 기초 영어 회화 탈출을 위한 맞춤형 콘텐츠 가공 프로듀서
            Context: 유저는 문법 지식은 약간 있으나 실전 발화가 불가능한 기초 회화 학습자임. 
            Task: 유저가 제공한 영어 텍스트 원문을 분석하여, 원어민들이 일상생활이나 여행에서 가장 자주 쓰는 필수 동사 및 핵심 청크(Chunk)를 최대 5개 엄선하고 이를 로컬 DB에 즉시 삽입할 수 있는 구조화된 JSON 포맷으로 생성하라.

            [콘텐츠 생성 규칙]
            1. 너무 어렵고 학술적인 단어는 제외하고, 'get', 'take', 'run out of' 같이 원어민이 일상에서 돌려쓰는 고효율 기본 동사 및 구동사 위주로 선별할 것.
            2. context_kr(한국어 가이드)은 유저가 영어 단어를 직접 뱉을 수 있도록 유도하되, 기초 수준에 맞게 직관적이고 친절한 한국어 상황 맥락으로 제시할 것.
            3. target_hint(힌트 포맷)는 첫 글자를 제외하고 나머지는 영자 수만큼 언더바로 치환하되, 여러 단어인 경우 공백을 유지하라. 예: 'run out of' -> 'r__ ___ __', 'get along' -> 'g__ a____'.
            4. 반드시 지정된 JSON 포맷으로만 출력하고, 다른 부연 설명은 일절 배제할 것.

            [출력 JSON 스키마 가이드]
            {
              "generated_words": [
                {
                  "item_type": "VERB 또는 CHUNK",
                  "target_english": "영어 단어/청크",
                  "target_meaning": "한국어 뜻",
                  "context_kr": "인출 유도용 상황 맥락 한국어 문장",
                  "target_hint": "힌트 포맷(첫글자 제외 언더바 처리)",
                  "native_example": "원어민 표준 예문",
                  "native_example_kr": "원어민 표준 예문 해석"
                }
              ]
            }
        """.trimIndent()

        val prompt = "아래 텍스트 원문에서 회화 표현 5개를 가공해 줘:\n\n$rawText"

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2f),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val resultText = GeminiClient.extractText(response) ?: ""
            Log.d("GeminiService", "Extracted JSON text: $resultText")
            GeminiClient.parseGeneratedWords(resultText)
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to extract learning materials", e)
            throw e
        }
    }

    /**
     * Topic Research: Searches and prepares up to 5 super practical, native expressions (VERB or CHUNK) based on any keyword or topic.
     */
    suspend fun researchTopicExpressions(topic: String): List<GeneratedWordItem> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("API_KEY_MISSING")
        }

        val systemInstruction = """
            Role: 일상/비즈니스 실전 비대면 회화 1:1 러닝 디자이너
            Task: 사용자가 학습하고 싶어하는 영어 회화 주제(상황, 장소, 목적 등)인 '$topic'을 정밀 타겟팅하여, 원어민들이 해당 상황에서 입에 달고 사는 핵심 구동사(VERB) 및 실용적 패턴 청크(CHUNK)를 최대 5개 선정 및 가공하라.
            
            [콘텐츠 가이드라인]
            1. 사용자가 단회성 통암기가 아닌 필수 일상 단어 조합으로 유연하게 발화할 수 있도록 설계할 것.
            2. context_kr(한국어 상황 맥락)은 해당 단어/패턴이 정확히 사용되는 상황극이나 감정 상태를 쉬운 한국어로 제시할 것.
            3. target_hint(힌트 포맷)는 첫 글자 외에는 공백 여부를 보존한 채 언더바(_)로 마스킹할 것. 예: 'get away with' -> 'g__ ____ ____'.
            4. 반드시 지정된 JSON 포맷으로 출력하고 다른 부연 설명은 일절 배제할 것.

            [출력 JSON 스키마 가이드]
            {
              "generated_words": [
                {
                  "item_type": "VERB 또는 CHUNK",
                  "target_english": "영어 표현",
                  "target_meaning": "명확한 한국어 뜻",
                  "context_kr": "말을 뱉게 환경을 잡아주는 한글 상황 문장",
                  "target_hint": "각 영어 단어별 첫자리 제외 마스킹 힌트",
                  "native_example": "대표 예시 문장",
                  "native_example_kr": "예시 영어 문장 한글 번역"
                }
              ]
            }
        """.trimIndent()

        val prompt = "주제: $topic\n이 상황에 대비하기 위해 꼭 입 밖으로 뱉어야 하는 고빈도 단어/청크 5개와 훈련 스키마를 도출해 줘."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.3f),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val resultText = GeminiClient.extractText(response) ?: ""
            Log.d("GeminiService", "Researched topic JSON text: $resultText")
            GeminiClient.parseGeneratedWords(resultText)
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to research topic materials", e)
            throw e
        }
    }

    /**
     * Prompts ②: Created Daily report based on today's logs.
     */
    suspend fun generateDailyReport(completedLessons: List<WordWithLogs>): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("API_KEY_MISSING")
        }

        if (completedLessons.isEmpty()) {
            return@withContext "오늘 학습한 정리가 존재하지 않습니다."
        }

        val systemInstruction = """
            Role: 기초 회화 전문 친절한 1:1 홈튜터 및 회화 가이드 설계자
            Task: 오늘 사용자가 학습한 10개의 표현과 실전 작문 로그를 정밀 분석하여, [PART 1: 작문 뉘앙스 피드백]과 [PART 2: 구글 Gemini 공식 앱용 실전 보이스 회화 프롬프트]가 결합된 통합 일일 보고서를 마크다운으로 작성하라.

            [PART 1. 오늘의 10개 작문 정밀 피드백 작성 규칙]
            - 기초 회화 학습자임을 고려하여 지나치게 격식 차린 표현보다는 '친구끼리 쓰는 자연스러운 표현'과 '해외 여행/상황별 필수 표현' 2가지 대안(Rephrasing)을 표 형태로 제시할 것.
            - 틀린 문법이 있다면 기죽지 않게 친절하게 짚어주고, "한국식으로 생각하면 이렇게 나오지만, 원어민들은 보통 이렇게 말해요"라는 뉘앙스 해설을 쉽게 한글로 달아줄 것.

            [PART 2. Gemini Live 실전 보이스 회화 개시 프로토콜 작성 규칙]
            - 사용자가 이 영역을 통째로 복사해서 공식 Gemini 앱에 넣고 바로 보이스 회화(Gemini Live)를 시작할 수 있도록 완벽한 '영어 명령문(Prompt)' 형태로 작성할 것. 마크다운 기호는 배제할 것.
            - 보이스 룰 지침:
              1. 너는 미국 표준 발음을 쓰는 세상에서 가장 친절한 1:1 영어 회화 친구(Roleplay Partner)다.
              2. 학습자가 오늘 배운 10개의 핵심 단어([여기에 오늘 유저가 공부한 단어 10개 자동 나열])를 실제 대화 속에서 자연스럽게 입 밖으로 꺼내도록 쉬운 질문을 던져라.
              3. 오늘 학습자가 실수했던 문장 맥락을 대화 주제로 삼아, 학습자가 피드백 받은 올바른 표현을 사용해 다시 말해볼 수 있도록 유도하라.
              4. 한 번에 한 문장씩만 짧게 질문하고, 대화를 주도하되 학습자가 말할 타이밍을 충분히 보장하라.
        """.trimIndent()

        // Construct input structured content for today's efforts representatively
        val sb = StringBuilder()
        sb.append("오늘의 학습 리시트 및 유저 영작 데이터입니다:\n\n")
        completedLessons.forEachIndexed { index, item ->
            val latestSentence = item.logs.firstOrNull()?.userTypedSentence ?: "(입력하지 않음)"
            sb.append("${index + 1}. [단어/청크]: ${item.word.targetEnglish} (${item.word.targetMeaning})\n")
            sb.append("   - 상황맥락 가이드: ${item.word.contextKr}\n")
            sb.append("   - 표준 예문: ${item.word.nativeExample} (${item.word.nativeExampleKr})\n")
            sb.append("   - 유저 작문 문장: \"$latestSentence\"\n\n")
        }

        val prompt = sb.toString()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5f),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            GeminiClient.extractText(response) ?: "보고서 생성에 실패했습니다."
        } catch (e: Exception) {
            Log.e("GeminiService", "Failed to generate daily report", e)
            throw e
        }
    }
}
